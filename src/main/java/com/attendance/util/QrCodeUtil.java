package com.attendance.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Utility for Cryptographically Server-Signed Dynamic QR Code Tokens.
 * Features:
 * - HMAC-SHA256 token signing with server-side secret key.
 * - 7-second dynamic QR code rotation.
 * - Leeway validity window (10 seconds: 7s display + 3s network transmission leeway).
 * - Strict constant-time cryptographic verification preventing timing attacks.
 * - Typed fallback code validation supporting the exact same signed token.
 */
public class QrCodeUtil {

    private static final String HMAC_ALGO = "HmacSHA256";
    public static final int MIN_SECRET_BYTES = 32;
    private static volatile String testSecretOverride = null;

    /**
     * Sets an in-memory HMAC secret override for testing purposes.
     * Never commit or log secret values.
     */
    public static synchronized void setSecretKeyForTesting(String secret) {
        testSecretOverride = secret;
    }

    /**
     * Clears the in-memory test secret override, restoring environment resolution.
     */
    public static synchronized void clearSecretKeyForTesting() {
        testSecretOverride = null;
    }

    /**
     * Resolves the server-side HMAC secret key from the secure environment.
     * Enforces minimum entropy (at least 32 bytes of cryptographic entropy).
     * Fails closed if the secret is missing, blank, or insufficient.
     */
    public static String resolveSecret() {
        if (testSecretOverride != null) {
            if (testSecretOverride.trim().isEmpty()) {
                throw new IllegalStateException("HMAC secret configuration error: QR_HMAC_SECRET environment variable is missing or blank. Dynamic attendance token operations cannot proceed.");
            }
            validateSecret(testSecretOverride.trim());
            return testSecretOverride.trim();
        }
        String secret = System.getenv("QR_HMAC_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getenv("HMAC_SECRET");
        }
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getProperty("QR_HMAC_SECRET");
        }
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getProperty("qr.hmac.secret");
        }
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getProperty("HMAC_SECRET");
        }
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("HMAC secret configuration error: QR_HMAC_SECRET environment variable is missing or blank. Dynamic attendance token operations cannot proceed.");
        }
        validateSecret(secret.trim());
        return secret.trim();
    }

    /**
     * Decodes the configured secret string into raw secret bytes.
     * Supports Hex (>= 64 chars), Base64 (>= 43 chars), or raw UTF-8 string bytes.
     */
    public static byte[] decodeSecretBytes(String secret) {
        if (secret == null) {
            return new byte[0];
        }
        String trimmed = secret.trim();
        // 1. Check if 64+ hex characters (representing >= 32 raw bytes)
        if (trimmed.length() >= 64 && trimmed.matches("^[0-9a-fA-F]+$") && (trimmed.length() % 2 == 0)) {
            byte[] bytes = new byte[trimmed.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(trimmed.substring(i * 2, i * 2 + 2), 16);
            }
            if (bytes.length >= MIN_SECRET_BYTES) {
                return bytes;
            }
        }
        // 2. Check if Base64 encoded (length >= 43 chars for >= 32 bytes)
        if (trimmed.length() >= 43 && trimmed.matches("^[A-Za-z0-9+/=_-]+$")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed);
                if (decoded.length >= MIN_SECRET_BYTES) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                try {
                    byte[] decodedUrl = Base64.getUrlDecoder().decode(trimmed);
                    if (decodedUrl.length >= MIN_SECRET_BYTES) {
                        return decodedUrl;
                    }
                } catch (IllegalArgumentException ignored2) {}
            }
        }
        // 3. Raw UTF-8 bytes
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    private static void validateSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("HMAC secret configuration error: QR_HMAC_SECRET environment variable is missing or blank. Dynamic attendance token operations cannot proceed.");
        }
        byte[] bytes = decodeSecretBytes(secret);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("HMAC secret configuration error: QR_HMAC_SECRET must provide at least 32 characters / 32 bytes of cryptographic entropy (found " + bytes.length + " bytes).");
        }
        // Entropy check: verify byte diversity and Shannon entropy to prevent low-entropy/predictable keys
        int distinct = 0;
        int[] freq = new int[256];
        for (byte b : bytes) {
            int idx = b & 0xFF;
            if (freq[idx] == 0) distinct++;
            freq[idx]++;
        }
        if (distinct < 10) {
            throw new IllegalStateException("HMAC secret configuration error: QR_HMAC_SECRET lacks sufficient cryptographic entropy (insufficient character/byte diversity).");
        }
        double entropy = 0.0;
        int len = bytes.length;
        for (int count : freq) {
            if (count > 0) {
                double p = (double) count / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        if (entropy < 2.5) {
            throw new IllegalStateException("HMAC secret configuration error: QR_HMAC_SECRET lacks sufficient cryptographic entropy (Shannon entropy too low: " + String.format(Locale.ROOT, "%.2f", entropy) + ").");
        }
    }

    // Dynamic rotation interval on screen: 7 seconds
    public static final int ROTATION_INTERVAL_SECONDS = 7;
    public static final long ROTATION_INTERVAL_MS = ROTATION_INTERVAL_SECONDS * 1000L;

    // Validity window: 10 seconds (7 seconds display + 3 seconds network transmission leeway)
    public static final long VALIDITY_WINDOW_MS = 10000L;

    /**
     * Calculates the deterministic time-step for a given millisecond timestamp.
     */
    public static long getCurrentTimeStep(long timestampMs) {
        return timestampMs / ROTATION_INTERVAL_MS;
    }

    /**
     * Calculates the exact epoch millisecond of the next rotation boundary.
     */
    public static long getNextRotationBoundary(long timestampMs) {
        return (getCurrentTimeStep(timestampMs) + 1) * ROTATION_INTERVAL_MS;
    }

    /**
     * Calculates the remaining milliseconds in the active time-step.
     */
    public static long getRemainingMsInStep(long timestampMs) {
        return Math.max(0L, getNextRotationBoundary(timestampMs) - timestampMs);
    }

    /**
     * Generates a cryptographically server-signed token derived for an explicit time-step.
     * Guarantees that repeated calls within the same time-step yield the exact same token.
     */
    public static String generateSignedTokenForStep(int sessionId, int courseId, long step) {
        long stepStartMs = step * ROTATION_INTERVAL_MS;
        String nonce = computeHmac(sessionId + ":" + courseId + ":nonce:" + step).substring(0, 8);
        String payload = sessionId + ":" + courseId + ":" + stepStartMs + ":" + nonce;
        String signature = computeHmac(payload);
        return sessionId + "." + courseId + "." + stepStartMs + "." + nonce + "." + signature;
    }

    /**
     * Generates a cryptographically server-signed, short-lived token for an active QR session.
     * Format: sessionId.courseId.timestampMs.nonce.hmacSigHex
     */
    public static String generateSignedToken(int sessionId, int courseId) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String payload = sessionId + ":" + courseId + ":" + timestamp + ":" + nonce;
        String signature = computeHmac(payload);
        return sessionId + "." + courseId + "." + timestamp + "." + nonce + "." + signature;
    }

    /**
     * Derives a short 6-digit numeric fallback code from (sessionId, courseId, timestamp).
     * Rotates synchronously with the dynamic QR code every 7 seconds.
     */
    public static String computeNumericCode(int sessionId, int courseId, long timestamp) {
        long step = timestamp / ROTATION_INTERVAL_MS;
        String data = sessionId + ":" + courseId + ":" + step;
        String hmac = computeHmac(data);
        // Dynamic truncation: take 4 bytes modulo 1,000,000
        int offset = Integer.parseInt(hmac.substring(hmac.length() - 1), 16);
        String part = hmac.substring(offset * 2, offset * 2 + 8);
        long binary = Long.parseLong(part, 16) & 0x7fffffffL;
        int otp = (int) (binary % 1000000L);
        return String.format("%06d", otp);
    }

    public static String generateNumericFallback(int sessionId, int courseId) {
        return computeNumericCode(sessionId, courseId, System.currentTimeMillis());
    }

    /**
     * Internal/testable generator accepting an explicit timestamp.
     */
    public static String generateSignedTokenWithTimestamp(int sessionId, int courseId, long timestamp) {
        String nonce = computeHmac(sessionId + ":" + courseId + ":nonce:" + timestamp).substring(0, 8);
        String payload = sessionId + ":" + courseId + ":" + timestamp + ":" + nonce;
        String signature = computeHmac(payload);
        return sessionId + "." + courseId + "." + timestamp + "." + nonce + "." + signature;
    }

    /**
     * Generates an intentionally expired signed token for automated testing.
     */
    public static String generateExpiredToken(int sessionId, int courseId) {
        // Timestamp 20 seconds in the past (beyond 10s validity window)
        return generateSignedTokenWithTimestamp(sessionId, courseId, System.currentTimeMillis() - 20000L);
    }

    /**
     * Generates an intentionally tampered token for automated testing.
     */
    public static String generateTamperedToken(int sessionId, int courseId) {
        String token = generateSignedToken(sessionId, courseId);
        // Invert the last character of the signature
        char lastChar = token.charAt(token.length() - 1);
        char replacedChar = (lastChar == 'a') ? 'b' : 'a';
        return token.substring(0, token.length() - 1) + replacedChar;
    }

    /**
     * Computes HMAC-SHA256 signature in hexadecimal.
     */
    public static String computeHmac(String data) {
        try {
            byte[] keyBytes = decodeSecretBytes(resolveSecret());
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * Builds scan payload URL for QR code generation.
     */
    public static String buildScanPayload(String baseUrl, String token) {
        return baseUrl + "/qr/scan?token=" + token;
    }

    /**
     * Token verification status enumeration.
     */
    public enum TokenStatus {
        VALID,
        EXPIRED,
        INVALID
    }

    /**
     * Data carrier for parsed and verified token attributes.
     */
    public static class TokenValidation {
        private final TokenStatus status;
        private final int sessionId;
        private final int courseId;
        private final long timestamp;
        private final String nonce;
        private final String message;

        public TokenValidation(TokenStatus status, int sessionId, int courseId, long timestamp, String nonce, String message) {
            this.status = status;
            this.sessionId = sessionId;
            this.courseId = courseId;
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.message = message;
        }

        public TokenStatus getStatus() { return status; }
        public int getSessionId() { return sessionId; }
        public int getCourseId() { return courseId; }
        public long getTimestamp() { return timestamp; }
        public String getNonce() { return nonce; }
        public String getMessage() { return message; }
        public boolean isValid() { return status == TokenStatus.VALID; }
        public boolean isExpired() { return status == TokenStatus.EXPIRED; }
    }

    /**
     * Validates signature and lifetime window on the server.
     */
    public static TokenValidation validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return new TokenValidation(TokenStatus.INVALID, 0, 0, 0, null, "Session token is missing");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 5) {
            return new TokenValidation(TokenStatus.INVALID, 0, 0, 0, null, "Malformed or invalid token format");
        }

        try {
            int sessionId = Integer.parseInt(parts[0]);
            int courseId = Integer.parseInt(parts[1]);
            long timestamp = Long.parseLong(parts[2]);
            String nonce = parts[3];
            String receivedSig = parts[4];

            // 1. Verify HMAC signature using constant-time comparison
            String payload = sessionId + ":" + courseId + ":" + timestamp + ":" + nonce;
            String expectedSig = computeHmac(payload);
            if (!MessageDigest.isEqual(receivedSig.getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                return new TokenValidation(TokenStatus.INVALID, sessionId, courseId, timestamp, nonce, "Invalid token cryptographic signature");
            }

            // 2. Verify expiration window
            long now = System.currentTimeMillis();
            long age = now - timestamp;

            // Reject tokens generated in the future beyond normal 2s clock skew
            if (age < -2000L) {
                return new TokenValidation(TokenStatus.INVALID, sessionId, courseId, timestamp, nonce, "Token timestamp is in the future");
            }

            // Reject expired tokens beyond the 10s validity window
            if (age > VALIDITY_WINDOW_MS) {
                return new TokenValidation(TokenStatus.EXPIRED, sessionId, courseId, timestamp, nonce, "QR code / token has expired");
            }

            return new TokenValidation(TokenStatus.VALID, sessionId, courseId, timestamp, nonce, "Token is valid and within active rotation window");
        } catch (IllegalStateException e) {
            return new TokenValidation(TokenStatus.INVALID, 0, 0, 0, null, e.getMessage());
        } catch (NumberFormatException e) {
            return new TokenValidation(TokenStatus.INVALID, 0, 0, 0, null, "Malformed token numeric fields");
        }
    }
}

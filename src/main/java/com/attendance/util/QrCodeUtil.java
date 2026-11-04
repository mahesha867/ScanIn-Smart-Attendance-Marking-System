package com.attendance.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private static final String SECRET_KEY = "ScanIn#PurpleCollegiateAttendanceHMAC2026";

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
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
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
        } catch (NumberFormatException e) {
            return new TokenValidation(TokenStatus.INVALID, 0, 0, 0, null, "Malformed token numeric fields");
        }
    }
}

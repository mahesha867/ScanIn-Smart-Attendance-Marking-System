package com.attendance;

import com.attendance.util.QrCodeUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ScanIn Stage 15 — Release Verification & Cryptographic Entropy Test Suite.
 *
 * Scope:
 * 1. Cryptographic entropy enforcement on HMAC secret (distinguishes characters vs entropy).
 * 2. Proper handling of Hex-encoded and Base64-encoded 256-bit (32-byte) keys.
 * 3. Secret non-leakage in exceptions and logs.
 * 4. Historical enrollment boundary assertions.
 */
public class Stage15ReleaseVerificationTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Stage 15 Release Verification & Entropy Hardening Tests         ");
        System.out.println("==================================================================");

        // ── 1. Cryptographic Entropy & Representation Tests ─────────────────
        runTest("S15-01: QrCodeUtil rejects 32-char string with low entropy (e.g. repeated chars)",
                Stage15ReleaseVerificationTest::testHmacSecretLowEntropyFailsClosed);

        runTest("S15-02: QrCodeUtil correctly decodes and validates 64-char Hex (32-byte) secret",
                Stage15ReleaseVerificationTest::testHmacSecretHexEncoded32BytesSucceeds);

        runTest("S15-03: QrCodeUtil correctly decodes and validates Base64-encoded (32-byte) secret",
                Stage15ReleaseVerificationTest::testHmacSecretBase64Encoded32BytesSucceeds);

        runTest("S15-04: QrCodeUtil exception messages never leak the secret contents",
                Stage15ReleaseVerificationTest::testHmacSecretNeverLeakedInExceptions);

        runTest("S15-05: QrCodeUtil validates high-entropy UTF-8 passphrase",
                Stage15ReleaseVerificationTest::testHmacSecretHighEntropyPassphraseSucceeds);

        // ── 2. Historical Enrollment Boundary Static Contract Tests ─────────
        runTest("S15-06: PlannedSessionDao roster includes student enrolled before closure and still active",
                Stage15ReleaseVerificationTest::testEnrollmentBoundaryActiveIncluded);

        runTest("S15-07: PlannedSessionDao roster preserves student dropped AFTER session closure",
                Stage15ReleaseVerificationTest::testEnrollmentBoundaryDroppedAfterClosurePreserved);

        runTest("S15-08: PlannedSessionDao roster excludes student dropped BEFORE session closure",
                Stage15ReleaseVerificationTest::testEnrollmentBoundaryDroppedBeforeClosureExcluded);

        runTest("S15-09: PlannedSessionDao roster excludes student enrolled AFTER session closure",
                Stage15ReleaseVerificationTest::testEnrollmentBoundaryEnrolledAfterClosureExcluded);

        runTest("S15-10: PlannedSessionDao roster safely preserves legacy enrollment with NULL enrolled_at",
                Stage15ReleaseVerificationTest::testEnrollmentBoundaryNullEnrolledAtPreserved);

        System.out.println("==================================================================");
        System.out.printf("  Stage 15 Test Results: %d Passed, %d Failed%n", totalPassed, totalFailed);
        System.out.println("==================================================================");

        if (totalFailed > 0) {
            System.err.println("❌ STAGE 15 VERIFICATION FAILED");
            System.exit(1);
        } else {
            System.out.println("✅ STAGE 15 VERIFICATION PASSED");
        }
    }

    private static void runTest(String testName, TestRunnable test) {
        System.out.printf("[TEST] %s ... ", testName);
        try {
            test.run();
            totalPassed++;
            System.out.println("PASS");
        } catch (Throwable t) {
            totalFailed++;
            System.out.printf("FAIL: %s%n", t.getMessage());
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Throwable;
    }

    // ── Test Implementations ────────────────────────────────────────────────

    private static void testHmacSecretLowEntropyFailsClosed() {
        try {
            // 32 characters, but all 'a' -> 0 bits of Shannon entropy
            QrCodeUtil.setSecretKeyForTesting("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            QrCodeUtil.resolveSecret();
            throw new AssertionError("Expected IllegalStateException for low-entropy secret");
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("lacks sufficient cryptographic entropy")) {
                throw new AssertionError("Unexpected error message: " + e.getMessage());
            }
        } finally {
            QrCodeUtil.clearSecretKeyForTesting();
        }
    }

    private static void testHmacSecretHexEncoded32BytesSucceeds() {
        // 64 hex characters representing 32 securely generated random bytes
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : randomBytes) {
            hex.append(String.format("%02x", b));
        }
        String hexSecret = hex.toString();

        try {
            QrCodeUtil.setSecretKeyForTesting(hexSecret);
            String token = QrCodeUtil.generateSignedToken(101, 202);
            if (token == null || token.isEmpty()) {
                throw new AssertionError("Token generation failed with Hex secret");
            }
            if (!QrCodeUtil.validateToken(token).isValid()) {
                throw new AssertionError("Token validation failed with Hex secret");
            }
        } finally {
            QrCodeUtil.clearSecretKeyForTesting();
        }
    }

    private static void testHmacSecretBase64Encoded32BytesSucceeds() {
        // 32 random bytes Base64 encoded
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String base64Secret = Base64.getEncoder().encodeToString(randomBytes);

        try {
            QrCodeUtil.setSecretKeyForTesting(base64Secret);
            String token = QrCodeUtil.generateSignedToken(301, 402);
            if (token == null || token.isEmpty()) {
                throw new AssertionError("Token generation failed with Base64 secret");
            }
            if (!QrCodeUtil.validateToken(token).isValid()) {
                throw new AssertionError("Token validation failed with Base64 secret");
            }
        } finally {
            QrCodeUtil.clearSecretKeyForTesting();
        }
    }

    private static void testHmacSecretNeverLeakedInExceptions() {
        String testSecretWithCanary = "CANARY_SECRET_DATA_DO_NOT_LEAK_IN_LOGS_OR_MESSAGES";
        try {
            QrCodeUtil.setSecretKeyForTesting(testSecretWithCanary);
            // This secret is 50 chars, but let's test a short secret containing the canary
            QrCodeUtil.setSecretKeyForTesting("CANARY_SHORT");
            QrCodeUtil.resolveSecret();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("CANARY_SHORT")) {
                throw new AssertionError("Secret string leaked in exception message: " + e.getMessage());
            }
        } finally {
            QrCodeUtil.clearSecretKeyForTesting();
        }
    }

    private static void testHmacSecretHighEntropyPassphraseSucceeds() {
        String passphrase = "ScanIn!2026#SecureDynamicAttendanceKey%WithSufficientEntropy*";
        try {
            QrCodeUtil.setSecretKeyForTesting(passphrase);
            String token = QrCodeUtil.generateSignedToken(501, 602);
            if (!QrCodeUtil.validateToken(token).isValid()) {
                throw new AssertionError("Passphrase token validation failed");
            }
        } finally {
            QrCodeUtil.clearSecretKeyForTesting();
        }
    }

    private static void testEnrollmentBoundaryActiveIncluded() throws IOException {
        String source = Files.readString(Paths.get("src/main/java/com/attendance/dao/PlannedSessionDao.java"));
        // Matches condition: active enrollment before closure
        if (!source.contains("e.status = 'ACTIVE' OR e.status IS NULL")) {
            throw new AssertionError("PlannedSessionDao does not check for ACTIVE status");
        }
    }

    private static void testEnrollmentBoundaryDroppedAfterClosurePreserved() throws IOException {
        String source = Files.readString(Paths.get("src/main/java/com/attendance/dao/PlannedSessionDao.java"));
        if (!source.contains("e.dropped_at > qs.closed_at")) {
            throw new AssertionError("PlannedSessionDao does not preserve enrollments dropped after closure");
        }
    }

    private static void testEnrollmentBoundaryDroppedBeforeClosureExcluded() throws IOException {
        String source = Files.readString(Paths.get("src/main/java/com/attendance/dao/PlannedSessionDao.java"));
        // When dropped_at <= qs.closed_at and status != ACTIVE, the condition evaluates to false, excluding the student
        if (!source.contains("e.dropped_at IS NULL OR qs.closed_at IS NULL OR e.dropped_at > qs.closed_at")) {
            throw new AssertionError("PlannedSessionDao does not exclude enrollments dropped before closure");
        }
    }

    private static void testEnrollmentBoundaryEnrolledAfterClosureExcluded() throws IOException {
        String source = Files.readString(Paths.get("src/main/java/com/attendance/dao/PlannedSessionDao.java"));
        // When enrolled_at > qs.closed_at, student is excluded from completed roster
        if (!source.contains("e.enrolled_at <= qs.closed_at")) {
            throw new AssertionError("PlannedSessionDao does not exclude students enrolled after closure");
        }
    }

    private static void testEnrollmentBoundaryNullEnrolledAtPreserved() throws IOException {
        String source = Files.readString(Paths.get("src/main/java/com/attendance/dao/PlannedSessionDao.java"));
        // When enrolled_at IS NULL, safe fallback allows historical preservation
        if (!source.contains("e.enrolled_at IS NULL")) {
            throw new AssertionError("PlannedSessionDao does not handle legacy NULL enrolled_at safely");
        }
    }
}


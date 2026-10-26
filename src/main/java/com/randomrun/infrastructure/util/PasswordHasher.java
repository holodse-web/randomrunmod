package com.randomrun.infrastructure.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class PasswordHasher {
    private static final String VERSION_V1 = "v1";
    private static final String VERSION_V2 = "v2";
    private static final String CURRENT_VERSION = VERSION_V2;

    private static final String LEGACY_PEPPER = "rr_pepper_2026";

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 32;
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    private static final ThreadLocal<MessageDigest> digestCache = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            CompactLogger.authError("SHA-256 unavailable");
            return null;
        }
    });

    private static final SecureRandom random = new SecureRandom();

    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            CompactLogger.authWarn("Empty password");
            return null;
        }

        try {
            byte[] salt = generateSaltBytes();
            byte[] hash = hashPBKDF2(password, salt);

            String saltB64 = Base64.getEncoder().encodeToString(salt);
            String hashB64 = Base64.getEncoder().encodeToString(hash);

            return String.format("%s:%d:%s:%s", VERSION_V2, PBKDF2_ITERATIONS, saltB64, hashB64);
        } catch (Exception e) {
            CompactLogger.authError("Hash failed: {}", e.getMessage());
            return null;
        }
    }

    private static String hashV1(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }

        String pepper = getPepper();
        String hash = hashSHA256(password + pepper);
        return VERSION_V1 + ":" + hash;
    }

    private static String hashV1WithSalt(String password, String salt) {
        if (password == null || password.isEmpty()) {
            return null;
        }

        String pepper = getPepper();
        String hash = hashSHA256(password + salt + pepper);
        return VERSION_V1 + ":" + hash;
    }

    private static byte[] hashPBKDF2(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        return factory.generateSecret(spec).getEncoded();
    }

    private static String hashSHA256(String data) {
        try {
            MessageDigest digest = digestCache.get();
            if (digest == null) {
                throw new RuntimeException("SHA-256 unavailable");
            }

            digest.reset();
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            CompactLogger.authError("SHA-256 failed: {}", e.getMessage());
            throw new RuntimeException("Hash error", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String getPepper() {
        return LEGACY_PEPPER;
    }

    public static boolean verify(String password, String hashedPassword) {
        if (password == null || hashedPassword == null) {
            return password == null && hashedPassword == null;
        }

        try {
            String version = detectVersion(hashedPassword);

            if (VERSION_V2.equals(version)) {
                return verifyV2(password, hashedPassword);
            } else if (VERSION_V1.equals(version)) {
                return verifyV1(password, hashedPassword);
            } else {
                return verifyLegacy(password, hashedPassword);
            }
        } catch (Exception e) {
            CompactLogger.authError("Verify failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean verifyV2(String password, String hashedPassword) {
        try {
            String[] parts = hashedPassword.split(":");
            if (parts.length != 4) return false;

            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] actualHash = factory.generateSecret(spec).getEncoded();

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            CompactLogger.authError("V2 verify failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean verifyV1(String password, String hashedPassword) {
        try {
            String[] parts = hashedPassword.split(":", 2);
            if (parts.length != 2) return false;

            String expectedHash = parts[1];
            String pepper = getPepper();
            String actualHash = hashSHA256(password + pepper);

            return timingSafeEquals(expectedHash, actualHash);
        } catch (Exception e) {
            CompactLogger.authError("V1 verify failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean verifyLegacy(String password, String hashedPassword) {
        try {
            String pepper = getPepper();
            String actualHash = hashSHA256(password + pepper);
            return timingSafeEquals(hashedPassword, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.isEmpty()) return false;
        if (password.length() < MIN_PASSWORD_LENGTH) return false;
        if (password.length() > MAX_PASSWORD_LENGTH) return false;
        return true;
    }

    public static String generatePassword(int length) {
        if (length < MIN_PASSWORD_LENGTH) length = MIN_PASSWORD_LENGTH;
        if (length > MAX_PASSWORD_LENGTH) length = MAX_PASSWORD_LENGTH;

        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(PASSWORD_CHARS.length());
            password.append(PASSWORD_CHARS.charAt(index));
        }
        return password.toString();
    }

    public static String generateSalt() {
        return generatePassword(16);
    }

    private static byte[] generateSaltBytes() {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    public static String detectVersion(String hashedPassword) {
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            return null;
        }

        if (hashedPassword.startsWith(VERSION_V2 + ":")) {
            return VERSION_V2;
        } else if (hashedPassword.startsWith(VERSION_V1 + ":")) {
            return VERSION_V1;
        } else {
            return "legacy";
        }
    }

    public static boolean needsUpgrade(String hashedPassword) {
        String version = detectVersion(hashedPassword);
        return !CURRENT_VERSION.equals(version);
    }

    public static boolean isLegacyHash(String hashedPassword) {
        String version = detectVersion(hashedPassword);
        return VERSION_V1.equals(version) || "legacy".equals(version);
    }

    public static int getMinPasswordLength() {
        return MIN_PASSWORD_LENGTH;
    }

    public static int getMaxPasswordLength() {
        return MAX_PASSWORD_LENGTH;
    }

    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }
}

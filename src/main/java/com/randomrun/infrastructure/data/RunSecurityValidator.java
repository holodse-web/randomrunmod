package com.randomrun.infrastructure.data;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RunSecurityValidator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ThreadLocal<MessageDigest> DIGEST_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    });

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "give", "tp", "teleport", "gamemode", "gm",
        "time", "weather", "difficulty",
        "effect", "enchant", "xp", "experience",
        "kill", "summon",
        "setblock", "fill", "clone",
        "clear", "replaceitem", "item", "loot",
        "locate", "spreadplayers", "spawnpoint", "setworldspawn",
        "seed",
        "execute", "function",
        "data",
        "attribute",
        "worldborder",
        "forceload",
        "recipe",
        "advancement",
        "tag", "trigger",
        "scoreboard",
        "bossbar", "title",
        "playsound", "stopsound",
        "particle",
        "debug",
        "spectate", "ride", "damage", "return", "schedule"
    );

    private static final Map<String, Integer> commandAttempts = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_MS = 1000;
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;

    public static String generateSecureHash(String data) {
        try {
            byte[] saltBytes = new byte[32];
            SECURE_RANDOM.nextBytes(saltBytes);
            String salt = Base64.getEncoder().encodeToString(saltBytes);

            MessageDigest md = DIGEST_CACHE.get();
            md.reset();
            byte[] hash = md.digest((data + ":" + salt).getBytes(StandardCharsets.UTF_8));
            return salt + ":" + bytesToHex(hash);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[Security] Hash generation failed", e);
            throw new SecurityException("Hash generation failed", e);
        }
    }

    public static boolean verifyHashSecure(String data, String hash) {
        if (data == null || hash == null) return false;
        try {
            int sep = hash.indexOf(':');
            if (sep < 0) return false;
            String salt = hash.substring(0, sep);
            String expectedHex = hash.substring(sep + 1);

            MessageDigest md = DIGEST_CACHE.get();
            md.reset();
            byte[] computed = md.digest((data + ":" + salt).getBytes(StandardCharsets.UTF_8));
            return constantTimeEquals(bytesToHex(computed), expectedHex);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public static boolean isCommandBlocked(String command) {
        if (command == null || command.isEmpty()) return false;

        String cmd = command.trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        int spaceIndex = cmd.indexOf(' ');
        String baseCommand = (spaceIndex > 0 ? cmd.substring(0, spaceIndex) : cmd).toLowerCase();

        boolean blocked = BLOCKED_COMMANDS.contains(baseCommand);

        if (blocked) {
            logBlockedCommand(baseCommand);
        }

        return blocked;
    }

    private static void logBlockedCommand(String command) {
        String key = command + ":" + (System.currentTimeMillis() / 60000);

        commandAttempts.compute(key, (k, count) -> {
            int newCount = (count == null ? 0 : count) + 1;
            if (newCount <= MAX_ATTEMPTS_PER_MINUTE) {
                CompactLogger.warn("[Security] Blocked command: /{}", command);
            }
            return newCount;
        });

        cleanupOldAttempts();
    }

    private static void cleanupOldAttempts() {
        long currentMinute = System.currentTimeMillis() / 60000;
        commandAttempts.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            try {
                long minute = Long.parseLong(key.substring(key.lastIndexOf(':') + 1));
                return currentMinute - minute > 5;
            } catch (Exception e) {
                return true;
            }
        });
    }

    public static Set<String> getBlockedCommands() {
        return Collections.unmodifiableSet(BLOCKED_COMMANDS);
    }

    public static Map<String, Integer> getRecentBlockedAttempts() {
        Map<String, Integer> summary = new HashMap<>();
        long currentMinute = System.currentTimeMillis() / 60000;

        commandAttempts.forEach((key, count) -> {
            String command = key.substring(0, key.lastIndexOf(':'));
            try {
                long minute = Long.parseLong(key.substring(key.lastIndexOf(':') + 1));

                if (currentMinute - minute <= 1) {
                    summary.merge(command, count, Integer::sum);
                }
            } catch (Exception e) {
            }
        });

        return summary;
    }

    public static String sanitizeCommand(String command) {
        if (command == null) return "";
        return command.trim().replaceAll("[^a-zA-Z0-9/_\\- ]", "");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

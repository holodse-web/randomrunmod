package com.randomrun.infrastructure.util;

import com.randomrun.infrastructure.RandomRunMod;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class CompactLogger {
    public enum LogLevel {
        MINIMAL(0),
        NORMAL(1),
        VERBOSE(2),
        DEBUG(3);

        private final int level;

        LogLevel(int level) {
            this.level = level;
        }

        public boolean allows(LogLevel other) {
            return this.level >= other.level;
        }

        public static LogLevel fromString(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (Exception e) {
                return NORMAL;
            }
        }
    }

    private static volatile boolean verboseCache = false;
    private static volatile LogLevel logLevelCache = LogLevel.NORMAL;
    private static long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 5000;

    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final AtomicInteger warnCount = new AtomicInteger(0);
    private static final Set<String> enabledCategories = new HashSet<>();

    static {
        enabledCategories.add("Core");
        enabledCategories.add("Sec");
        enabledCategories.add("Battle");
        enabledCategories.add("Auth");
        enabledCategories.add("Net");
        enabledCategories.add("FB");
        enabledCategories.add("Debug");
    }

    private static void updateCache() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_TTL_MS) {
            try {
                verboseCache = RandomRunMod.getInstance().getConfig().isVerboseLogging();
                logLevelCache = LogLevel.fromString(RandomRunMod.getInstance().getConfig().getLogLevel());
                lastCacheUpdate = now;
            } catch (Exception e) {
                verboseCache = false;
                logLevelCache = LogLevel.NORMAL;
            }
        }
    }

    private static boolean isVerbose() {
        updateCache();
        return verboseCache;
    }

    private static LogLevel getLogLevel() {
        updateCache();
        return logLevelCache;
    }

    private static boolean shouldLog(String category, LogLevel requiredLevel) {
        if (!enabledCategories.isEmpty() && !enabledCategories.contains(category)) {
            return false;
        }
        return getLogLevel().allows(requiredLevel);
    }

    public static void core(String message, Object... args) {
        RandomRunMod.LOGGER.info("[Core] " + message, args);
    }

    public static void network(String message, Object... args) {
        if (shouldLog("Net", LogLevel.VERBOSE)) {
            RandomRunMod.LOGGER.info("[Net] " + message, args);
        }
    }

    public static void networkError(String message, Object... args) {
        errorCount.incrementAndGet();
        RandomRunMod.LOGGER.error("[Net] " + message, args);
    }

    public static void firebase(String message, Object... args) {
        if (shouldLog("FB", LogLevel.VERBOSE)) {
            RandomRunMod.LOGGER.info("[FB] " + message, args);
        }
    }

    public static void firebaseError(String message, Object... args) {
        errorCount.incrementAndGet();
        RandomRunMod.LOGGER.error("[FB] " + message, args);
    }

    public static void security(String message, Object... args) {
        RandomRunMod.LOGGER.info("[Sec] " + message, args);
    }

    public static void battle(String message, Object... args) {
        RandomRunMod.LOGGER.info("[Battle] " + message, args);
    }

    public static void auth(String message, Object... args) {
        RandomRunMod.LOGGER.info("[Auth] " + message, args);
    }

    public static void authError(String message, Object... args) {
        errorCount.incrementAndGet();
        RandomRunMod.LOGGER.error("[Auth] " + message, args);
    }

    public static void authWarn(String message, Object... args) {
        warnCount.incrementAndGet();
        RandomRunMod.LOGGER.warn("[Auth] " + message, args);
    }

    public static void error(String message, Object... args) {
        errorCount.incrementAndGet();
        RandomRunMod.LOGGER.error(message, args);
    }

    public static void warn(String message, Object... args) {
        warnCount.incrementAndGet();
        RandomRunMod.LOGGER.warn(message, args);
    }

    public static void debug(String message, Object... args) {
        if (shouldLog("Debug", LogLevel.DEBUG)) {
            RandomRunMod.LOGGER.info("[Debug] " + message, args);
        }
    }

    public static void success(String message, Object... args) {
        if (shouldLog("Success", LogLevel.NORMAL)) {
            RandomRunMod.LOGGER.info("✓ " + message, args);
        }
    }

    public static void failure(String message, Object... args) {
        errorCount.incrementAndGet();
        RandomRunMod.LOGGER.error("✗ " + message, args);
    }

    public static void operation(String category, String operation, String status) {
        if (shouldLog(category, LogLevel.NORMAL)) {
            RandomRunMod.LOGGER.info("[{}] {} - {}", category, operation, status);
        }
    }

    public static void timed(String category, String operation, long durationMs) {
        if (shouldLog(category, LogLevel.VERBOSE)) {
            RandomRunMod.LOGGER.info("[{}] {} - {}ms", category, operation, durationMs);
        }
    }

    public static void group(String category, String... messages) {
        if (shouldLog(category, LogLevel.VERBOSE)) {
            RandomRunMod.LOGGER.info("[{}] ===", category);
            for (String msg : messages) {
                RandomRunMod.LOGGER.info("[{}]   {}", category, msg);
            }
        }
    }

    public static void logIf(boolean condition, String message, Object... args) {
        if (condition) {
            RandomRunMod.LOGGER.info(message, args);
        }
    }

    public static void enableCategory(String category) {
        enabledCategories.add(category);
    }

    public static void disableCategory(String category) {
        enabledCategories.remove(category);
    }

    public static void enableAllCategories() {
        enabledCategories.clear();
    }

    public static boolean isCategoryEnabled(String category) {
        return enabledCategories.isEmpty() || enabledCategories.contains(category);
    }

    public static int getErrorCount() {
        return errorCount.get();
    }

    public static int getWarnCount() {
        return warnCount.get();
    }

    public static void resetCounters() {
        errorCount.set(0);
        warnCount.set(0);
    }

    public static void invalidateCache() {
        lastCacheUpdate = 0;
    }
}

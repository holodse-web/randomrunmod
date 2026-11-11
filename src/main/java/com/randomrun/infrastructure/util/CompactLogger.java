package com.randomrun.infrastructure.util;

import com.randomrun.infrastructure.RandomRunMod;

public final class CompactLogger {
    private CompactLogger() {}

    public static void core(String message, Object... args) {
        RandomRunMod.LOGGER.info(message, args);
    }

    public static void security(String message, Object... args) {
        RandomRunMod.LOGGER.info("[Sec] " + message, args);
    }

    public static void authError(String message, Object... args) {
        RandomRunMod.LOGGER.error("[Auth] " + message, args);
    }

    public static void error(String message, Object... args) {
        RandomRunMod.LOGGER.error(message, args);
    }

    public static void warn(String message, Object... args) {
        RandomRunMod.LOGGER.warn(message, args);
    }
}

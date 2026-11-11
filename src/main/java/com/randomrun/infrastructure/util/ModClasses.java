package com.randomrun.infrastructure.util;

import java.security.CodeSource;

public final class ModClasses {
    private static final String OWN = location(ModClasses.class);

    private static final ClassValue<Boolean> OWNED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith("com.randomrun.") || (OWN != null && OWN.equals(location(type)));
        }
    };

    private ModClasses() {}

    private static String location(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            return source == null || source.getLocation() == null ? null : source.getLocation().toString();
        } catch (Exception error) {
            return null;
        }
    }

    public static boolean isOwn(Object value) {
        if (value == null) return false;
        return OWNED.get(value.getClass());
    }
}

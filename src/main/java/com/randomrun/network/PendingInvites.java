package com.randomrun.network;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PendingInvites {
    private static final Set<String> NAMES = new LinkedHashSet<>();

    private PendingInvites() {}

    public static synchronized void add(String name) {
        NAMES.add(name);
    }

    public static synchronized Set<String> drain() {
        Set<String> copy = new LinkedHashSet<>(NAMES);
        NAMES.clear();
        return copy;
    }

    public static synchronized void clear() {
        NAMES.clear();
    }
}

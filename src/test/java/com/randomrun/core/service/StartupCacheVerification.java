package com.randomrun.core.service;

import com.randomrun.core.history.Run;
import com.randomrun.infrastructure.util.PlayerSkinHelper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StartupCacheVerification {
    private StartupCacheVerification() {}

    public static int verify() {
        int checks = 0;
        for (String name : List.of("Player", "player", "Игрок", "Гравець")) {
            UUID expected = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            UUID first = PlayerSkinHelper.getOfflineUUID(name);
            require(first.equals(expected), "offline UUID algorithm remains unchanged"); checks++;
            require(first == PlayerSkinHelper.getOfflineUUID(name), "HUD reuses the UUID object instead of hashing every frame"); checks++;
        }

        List<Run> existing = new ArrayList<>(), local = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) existing.add(new CountingRun(i % 2 == 0));
        local.addAll(existing.subList(0, 500));
        for (int i = 0; i < 1_000; i++) local.add(new CountingRun(i % 3 == 0));
        List<Run> expected = new ArrayList<>(existing);
        for (Run record : local) if (record.isUpgrader() && !expected.contains(record)) expected.add(record);
        CountingRun.hashCalls = 0;
        DataSyncService.appendLocalUpgraderHistory(existing, local);
        require(existing.equals(expected), "history merge preserves order, duplicates, and classic/upgrader filtering"); checks++;
        require(CountingRun.hashCalls <= 2_000 + local.size(), "history merge performs a linear number of hash lookups"); checks++;
        return checks;
    }

    private static final class CountingRun extends Run {
        static int hashCalls;
        final boolean upgrader;
        CountingRun(boolean upgrader) { this.upgrader = upgrader; }
        @Override public boolean isUpgrader() { return upgrader; }
        @Override public boolean equals(Object other) { return this == other; }
        @Override public int hashCode() { hashCalls++; return System.identityHashCode(this); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

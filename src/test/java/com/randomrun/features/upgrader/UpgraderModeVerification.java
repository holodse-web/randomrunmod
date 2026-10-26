package com.randomrun.features.upgrader;

import com.google.gson.Gson;
import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunHistory;
import com.randomrun.core.history.RunResult;

import java.util.List;

public final class UpgraderModeVerification {
    private UpgraderModeVerification() {}

    public static int verify() {
        Run classic = record(false, 30_000, 1);
        Run upgraded = record(true, 500, 2);
        check(classic.isEligibleForLeaderboard(), "classic eligibility preserved");
        check(!upgraded.isEligibleForLeaderboard(), "Upgrader excluded from classic leaderboard");

        Gson gson = new Gson();
        String saved = gson.toJson(upgraded);
        check(saved.contains("\"is_upgrader\":true"), "Upgrader metadata serialized");
        Run loaded = gson.fromJson(saved, Run.class);
        check(loaded.isUpgrader(), "Upgrader metadata survives JSON round trip");
        check(!loaded.isEligibleForLeaderboard(), "loaded Upgrader record remains ineligible");

        Run legacy = gson.fromJson("{\"target_id\":\"minecraft:diamond\",\"duration\":40000,"
            + "\"result\":\"VICTORY\",\"is_hardcore\":true}", Run.class);
        check(!legacy.isUpgrader(), "legacy records retain classic mode");
        check(legacy.isEligibleForLeaderboard(), "legacy records retain eligibility");

        RunHistory history = RunHistory.getInstance();
        List<Run> before = history.getAllRecords();
        try {
            history.setRecords(List.of(classic, upgraded));
            check(history.getBestTime("minecraft:diamond").orElse(-1L) == 30_000,
                "faster Upgrader victory never overwrites classic best");
            check(history.getAttemptCount("minecraft:diamond") == 1, "classic attempts isolated");
            check(history.getAttemptCount("minecraft:diamond", true) == 1, "Upgrader attempts isolated");
            history.setRecords(List.of(upgraded));
            check(history.getBestRecord("minecraft:diamond").isEmpty(), "Upgrader-only history has no classic best");
            check(history.getAllRecords().size() == 1, "Upgrader history remains available locally");
        } finally {
            history.setRecords(before);
        }
        return 12;
    }

    private static Run record(boolean upgrader, long duration, long timestamp) {
        return new Run.Builder().targetId("minecraft:diamond").result(RunResult.VICTORY)
            .duration(duration).timestamp(timestamp).isHardcore(true).isUpgrader(upgrader).build();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

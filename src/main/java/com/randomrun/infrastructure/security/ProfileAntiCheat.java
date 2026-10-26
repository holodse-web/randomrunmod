package com.randomrun.infrastructure.security;

import com.randomrun.core.history.RunHistory;
import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

public class ProfileAntiCheat {
    private static final int MIN_TRUST_LEADERBOARD = 10;
    private static final int MIN_TRUST_BATTLE = 10;
    private static final int MAX_SUSPICIOUS_FLAGS_LEADERBOARD = 50;
    private static final int TRUST_PENALTY_CHEAT = 8;

    private static final double SUSPICIOUS_WINRATE = 0.95;
    private static final int MIN_RUNS_FOR_WINRATE_CHECK = 50;
    private static final long SUSPICIOUS_AVG_TIME_MS = 1500;
    private static final long SUSPICIOUS_BEST_TIME_MS = 800;
    private static final int MIN_VICTORIES_FOR_AVG_CHECK = 3;

    private static final long LOG_RATE_LIMIT_MS = 1000;

    private static final ConcurrentHashMap<String, Long> lastLogTime = new ConcurrentHashMap<>();

    public static void checkProfile(PlayerProfile profile) {
        if (profile == null) return;

        CompactLogger.security("[AntiCheat] Checking profile: {}", profile.getPlayerName());

        if (!profile.getModVersion().equals(RandomRunMod.MOD_VERSION)) {
            CompactLogger.security("[AntiCheat] Version update: {} -> {}",
                profile.getModVersion(), RandomRunMod.MOD_VERSION);
            profile.setModVersion(RandomRunMod.MOD_VERSION);
        }

        checkHwidWithCooldown(profile);
        CompletableFuture.runAsync(() -> checkStatistics(profile));

        CompactLogger.security("[AntiCheat] Check done, Trust: {}/200", profile.getTrustScore());
    }

    private static void checkHwidWithCooldown(PlayerProfile profile) {
        String currentHwid = HardwareIdGenerator.generateHWID();
        String savedHwid = profile.getHwidHash();

        if (savedHwid == null || savedHwid.isEmpty()) {
            profile.setHwidHash(currentHwid);
            PlayerProfile.save();
        } else if (!savedHwid.equals(currentHwid)) {
            logSecurityEvent(profile.getPlayerName(), "HWID_MISMATCH",
                "Saved: " + savedHwid.substring(0, 8) + "... | Current: " + currentHwid.substring(0, 8) + "...");
            profile.addSuspiciousFlag();
            PlayerProfile.save();
        }
    }

    private static void checkStatistics(PlayerProfile profile) {
        RunHistory history = RunHistory.getInstance();

        var classicRecords = history.getAllRecords().stream().filter(record -> !record.isUpgrader()).toList();
        int totalRuns = classicRecords.size();
        if (totalRuns > MIN_RUNS_FOR_WINRATE_CHECK) {
            int victories = (int) classicRecords.stream().filter(com.randomrun.core.history.Run::isVictory).count();
            double winRate = (double) victories / totalRuns;

            if (winRate > SUSPICIOUS_WINRATE) {
                CompactLogger.warn("[AntiCheat] Suspicious win rate: {}%", (int)(winRate * 100));
                profile.addSuspiciousFlag();
                logSecurityEvent(profile.getPlayerName(), "HIGH_WINRATE",
                    "WinRate: " + (int)(winRate * 100) + "% (" + victories + "/" + totalRuns + ")");
            }
        }

        var victories = classicRecords.stream().filter(com.randomrun.core.history.Run::isVictory).toList();
        if (victories.size() > MIN_VICTORIES_FOR_AVG_CHECK) {
            VictoryStats stats = calculateVictoryStats(victories);

            if (stats.avgTime > 0 && stats.avgTime < SUSPICIOUS_AVG_TIME_MS) {
                CompactLogger.warn("[AntiCheat] Suspicious avg time: {}ms", stats.avgTime);
                profile.addSuspiciousFlag();
                logSecurityEvent(profile.getPlayerName(), "SUSPICIOUS_AVG_TIME",
                    "Average: " + stats.avgTime + "ms");
            }

            if (stats.fastestTime < SUSPICIOUS_BEST_TIME_MS && stats.fastestTime != Long.MAX_VALUE) {
                CompactLogger.warn("[AntiCheat] Suspicious best time: {}ms", stats.fastestTime);
                profile.addSuspiciousFlag();
                logSecurityEvent(profile.getPlayerName(), "SUSPICIOUS_BEST_TIME",
                    "Best: " + stats.fastestTime + "ms");
            }
        }
    }

    private static VictoryStats calculateVictoryStats(java.util.List<com.randomrun.core.history.Run> victories) {
        long sum = 0;
        long min = Long.MAX_VALUE;
        int count = 0;

        for (var record : victories) {
            long duration = record.getDuration();
            sum += duration;
            if (duration < min) min = duration;
            count++;
        }

        return new VictoryStats(count > 0 ? sum / count : 0, min);
    }

    private static class VictoryStats {
        final long avgTime;
        final long fastestTime;

        VictoryStats(long avgTime, long fastestTime) {
            this.avgTime = avgTime;
            this.fastestTime = fastestTime;
        }
    }

    public static void reportCheat(PlayerProfile profile, String cheatType) {
        if (profile == null) return;

        CompactLogger.error("[AntiCheat] Cheat detected: {} | Player: {}",
            cheatType, profile.getPlayerName());

        profile.setLastCheatDetection(System.currentTimeMillis());
        profile.addSuspiciousFlag();
        profile.setTrustScore(Math.max(0, profile.getTrustScore() - TRUST_PENALTY_CHEAT));

        logSecurityEvent(profile.getPlayerName(), "CHEAT_DETECTED",
            "Type: " + cheatType + " | Trust: " + profile.getTrustScore());

        PlayerProfile.save();
    }

    private static void kickPlayer(String reason) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.level != null) {
                client.disconnectWithProgressScreen();
            }
            client.gui.setScreen(new com.randomrun.ui.screen.info.BanScreen(reason));
        });
    }

    public static boolean canSubmitToLeaderboard(PlayerProfile profile) {
        return canPerformAction(profile, MIN_TRUST_LEADERBOARD, MAX_SUSPICIOUS_FLAGS_LEADERBOARD);
    }

    public static boolean canJoinBattle(PlayerProfile profile) {
        return canPerformAction(profile, MIN_TRUST_BATTLE, -1);
    }

    private static boolean canPerformAction(PlayerProfile profile, int minTrust, int maxFlags) {
        if (profile == null) return false;
        if (profile.getTrustScore() < minTrust) return false;
        if (maxFlags > 0 && profile.getSuspiciousFlags() > maxFlags) return false;
        return true;
    }

    private static void logSecurityEvent(String playerName, String eventType, String details) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) return;

        long now = System.currentTimeMillis();
        String key = playerName + ":" + eventType;
        Long lastLog = lastLogTime.get(key);

        if (lastLog != null && (now - lastLog) < LOG_RATE_LIMIT_MS) return;
        lastLogTime.put(key, now);

        CompletableFuture.runAsync(() -> {
            try {
                com.google.gson.JsonObject event = new com.google.gson.JsonObject();
                event.addProperty("player", playerName);
                event.addProperty("type", eventType);
                event.addProperty("details", details);
                event.addProperty("timestamp", now);
                event.addProperty("modVersion", RandomRunMod.MOD_VERSION);

                String eventId = now + "_" + UUID.randomUUID().toString().substring(0, 8);
                String path = "/security_logs/" + playerName + "/" + eventId;

                com.randomrun.network.HttpClient.getInstance()
                    .put(path, event)
                    .exceptionally(e -> {
                        CompactLogger.warn("[AntiCheat] Logging error: {}", e.getMessage());
                        return null;
                    });
            } catch (Exception e) {
                CompactLogger.error("[AntiCheat] Logging error", e);
            }
        });
    }
}

package com.randomrun.infrastructure.security;

import com.randomrun.core.history.Run;
import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public class RunIntegrityChecker {
    private static final long IMPOSSIBLE_TIME_MS = 100;
    private static final long SUSPICIOUS_TIME_MS = 300;
    private static final long VERY_FAST_TIME_MS = 500;

    private static final int MIN_RUNS_FOR_STATS = 50;
    private static final double SUSPICIOUS_CONSISTENCY = 0.02;

    private static final Map<String, CheckResult> recentChecks = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60000;
    private static final long ACTIVITY_TTL_MS = 7_200_000L;

    private static final Map<String, SuspiciousActivity> playerActivity = new ConcurrentHashMap<>();

    public static IntegrityReport checkRunIntegrity(Run record, PlayerProfile profile) {
        IntegrityReport report = new IntegrityReport(record != null ? record.getTargetId() : "unknown");

        if (record != null && record.isUpgrader()) return report;

        if (record == null || profile == null) {
            report.addViolation(ViolationType.INVALID_DATA, "Run record or profile is null", Severity.CRITICAL);
            return report;
        }

        checkTime(record, report);
        checkPlayerStats(profile, report);
        checkRunSequence(record, profile, report);
        checkGameConditions(record, report);
        checkActiveEffects(report);
        checkDataConsistency(record, report);
        checkWorldIntegrity(report);

        if (!report.isClean()) {
            applyPenalties(profile, report);
        }

        cacheResult(record.getTargetId(), report);
        return report;
    }

    private static void checkTime(Run record, IntegrityReport report) {
        long duration = record.getDuration();

        if (duration < IMPOSSIBLE_TIME_MS) {
            report.addViolation(ViolationType.IMPOSSIBLE_TIME,
                String.format("Duration %dms is physically impossible", duration), Severity.CRITICAL);
        } else if (duration < SUSPICIOUS_TIME_MS) {
            report.addViolation(ViolationType.SUSPICIOUS_TIME,
                String.format("Duration %dms is suspiciously fast", duration), Severity.HIGH);
        } else if (duration < VERY_FAST_TIME_MS) {
            report.addViolation(ViolationType.VERY_FAST_TIME,
                String.format("Duration %dms is very fast", duration), Severity.MEDIUM);
        }

        if (duration <= 0) {
            report.addViolation(ViolationType.INVALID_TIME, "Duration must be positive", Severity.CRITICAL);
        }
    }

    private static void checkPlayerStats(PlayerProfile profile, IntegrityReport report) {
        int totalRuns = profile.getTotalRuns();
        if (totalRuns < MIN_RUNS_FOR_STATS) return;

        if (profile.getTrustScore() < PlayerProfile.LEADERBOARD_THRESHOLD) {
            report.addViolation(ViolationType.LOW_TRUST_SCORE,
                String.format("Trust Score too low: %d/%d", profile.getTrustScore(), PlayerProfile.LEADERBOARD_THRESHOLD),
                Severity.HIGH);
        }

        if (profile.getSuspiciousFlags() > 5) {
            report.addViolation(ViolationType.HIGH_SUSPICIOUS_FLAGS,
                String.format("Too many suspicious flags: %d", profile.getSuspiciousFlags()),
                Severity.MEDIUM);
        }

        if (profile.getLastCheatDetection() > 0) {
            long daysSinceCheat = (System.currentTimeMillis() - profile.getLastCheatDetection()) / (1000 * 60 * 60 * 24);
            if (daysSinceCheat < 7) {
                report.addViolation(ViolationType.RECENT_CHEAT_DETECTION,
                    String.format("Cheat detected %d days ago", daysSinceCheat),
                    Severity.HIGH);
            }
        }
    }

    private static void checkRunSequence(Run record, PlayerProfile profile, IntegrityReport report) {
        SuspiciousActivity activity = playerActivity.computeIfAbsent(
            profile.getPlayerName(),
            k -> new SuspiciousActivity()
        );

        activity.addRun(record);

        if (activity.getRunsInLastMinute() > 10) {
            report.addViolation(ViolationType.TOO_FREQUENT_RUNS,
                String.format("Too many runs per minute: %d", activity.getRunsInLastMinute()),
                Severity.MEDIUM);
        }

        if (activity.getRunCount() >= 5) {
            double consistency = activity.getTimeConsistency();
            if (consistency < SUSPICIOUS_CONSISTENCY) {
                report.addViolation(ViolationType.SUSPICIOUS_CONSISTENCY,
                    String.format("Suspicious time consistency: %.2f%%", consistency * 100),
                    Severity.MEDIUM);
            }
        }
    }

    private static void checkGameConditions(Run record, IntegrityReport report) {
        if (!record.isBattle() && record.getCustomSeed() != null && !record.getCustomSeed().isEmpty()) {
            report.addViolation(ViolationType.CUSTOM_SEED_USED, "Custom seed was used", Severity.LOW);
        }

        if (record.getModVersion() == null || !record.getModVersion().equals(RandomRunMod.MOD_VERSION)) {
            report.addViolation(ViolationType.VERSION_MISMATCH,
                String.format("Mod version mismatch: %s vs %s", record.getModVersion(), RandomRunMod.MOD_VERSION),
                Severity.LOW);
        }
    }

    private static void checkActiveEffects(IntegrityReport report) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        List<String> suspiciousEffects = new ArrayList<>();

        for (Holder<MobEffect> effectEntry : client.player.getActiveEffectsMap().keySet()) {
            MobEffect effect = effectEntry.value();
            var instance = client.player.getEffect(effectEntry);
            if (instance == null) continue;
            int amp = instance.getAmplifier();

            if (effect == MobEffects.SPEED.value() && amp > 5) {
                suspiciousEffects.add("Speed " + (amp + 1));
            } else if (effect == MobEffects.HASTE.value() && amp > 5) {
                suspiciousEffects.add("Haste " + (amp + 1));
            } else if (effect == MobEffects.LUCK.value() && amp > 3) {
                suspiciousEffects.add("Luck " + (amp + 1));
            }
        }

        if (!suspiciousEffects.isEmpty()) {
            report.addViolation(ViolationType.SUSPICIOUS_EFFECTS,
                "Suspicious effects: " + String.join(", ", suspiciousEffects),
                Severity.MEDIUM);
        }
    }

    private static void checkWorldIntegrity(IntegrityReport report) {
        try {
            com.randomrun.core.session.RunSession session =
                com.randomrun.core.session.RunSessionManager.getInstance().getCurrentSession();

            if (session == null) return;

            if (!session.validateWorldIntegrity()) {
                report.addViolation(ViolationType.WORLD_MANIPULATION,
                    "World dimension files modified during run (DIM-1/DIM1)",
                    Severity.CRITICAL);
            }
        } catch (Exception e) {
            CompactLogger.warn("[Integrity] World integrity check failed: {}", e.getMessage());
        }
    }

    private static void checkDataConsistency(Run record, IntegrityReport report) {
        long now = System.currentTimeMillis();
        long timestamp = record.getTimestamp();

        if (timestamp > now) {
            report.addViolation(ViolationType.FUTURE_TIMESTAMP, "Timestamp is in the future", Severity.HIGH);
        }

        if (timestamp < now - (365L * 24 * 60 * 60 * 1000)) {
            report.addViolation(ViolationType.OLD_TIMESTAMP, "Timestamp is too old", Severity.MEDIUM);
        }

        if (record.getTargetId() == null || record.getTargetId().isEmpty()) {
            report.addViolation(ViolationType.INVALID_TARGET, "Missing targetId", Severity.CRITICAL);
        }
    }

    private static void applyPenalties(PlayerProfile profile, IntegrityReport report) {
        int totalPenalty = 0;
        List<String> penaltyReasons = new ArrayList<>();

        for (Violation violation : report.getViolations()) {
            int penalty = switch (violation.severity) {
                case CRITICAL -> -8;
                case HIGH -> -5;
                case MEDIUM -> -2;
                case LOW -> 0;
            };

            if (penalty < 0) {
                totalPenalty += penalty;
                penaltyReasons.add(String.format("%s (%d)", violation.type, penalty));
            }
        }

        if (totalPenalty < 0) {
            profile.applyTrustPenalty(totalPenalty,
                String.format("Run integrity violations (%d)", report.getViolations().size()));
            PlayerProfile.save();
            CompactLogger.warn("[Integrity] Penalty {} for {} violations: {}",
                totalPenalty, report.getViolations().size(), String.join(", ", penaltyReasons));
        } else if (!report.isClean()) {
            CompactLogger.security("[Integrity] LOW violations (no penalty): {} violations",
                report.getViolations().size());
        }
    }

    private static void cacheResult(String targetId, IntegrityReport report) {
        long now = System.currentTimeMillis();
        recentChecks.put(targetId, new CheckResult(report, now));
        recentChecks.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_TTL_MS);
        playerActivity.entrySet().removeIf(entry -> now - entry.getValue().lastAccessTime > ACTIVITY_TTL_MS);
    }

    public static IntegrityReport getCachedResult(String targetId) {
        CheckResult result = recentChecks.get(targetId);
        if (result != null && System.currentTimeMillis() - result.timestamp < CACHE_TTL_MS) {
            return result.report;
        }
        return null;
    }

    public static void clearCache() {
        recentChecks.clear();
        playerActivity.clear();
    }

    private static class CheckResult {
        final IntegrityReport report;
        final long timestamp;

        CheckResult(IntegrityReport report, long timestamp) {
            this.report = report;
            this.timestamp = timestamp;
        }
    }

    private static class SuspiciousActivity {
        private final List<Long> runTimestamps = new ArrayList<>();
        private final List<Long> runDurations = new ArrayList<>();
        long lastAccessTime = System.currentTimeMillis();

        void addRun(Run record) {
            lastAccessTime = System.currentTimeMillis();
            runTimestamps.add(record.getTimestamp());
            runDurations.add(record.getDuration());

            if (runTimestamps.size() > 100) {
                runTimestamps.remove(0);
                runDurations.remove(0);
            }
        }

        int getRunsInLastMinute() {
            long now = System.currentTimeMillis();
            return (int) runTimestamps.stream()
                .filter(ts -> now - ts < 60000)
                .count();
        }

        int getRunCount() {
            return runTimestamps.size();
        }

        double getTimeConsistency() {
            if (runDurations.size() < 2) return 1.0;

            double avg = runDurations.stream().mapToLong(Long::longValue).average().orElse(0);
            if (avg == 0) return 1.0;

            double variance = runDurations.stream()
                .mapToDouble(d -> Math.pow(d - avg, 2))
                .average()
                .orElse(0);

            double stdDev = Math.sqrt(variance);
            return stdDev / avg;
        }
    }

    public static class IntegrityReport {
        private final String targetId;
        private final List<Violation> violations = new ArrayList<>();
        private Severity maxSeverity = Severity.LOW;

        public IntegrityReport(String targetId) {
            this.targetId = targetId;
        }

        void addViolation(ViolationType type, String description, Severity severity) {
            violations.add(new Violation(type, description, severity));
            if (severity.ordinal() > maxSeverity.ordinal()) {
                maxSeverity = severity;
            }
        }

        void setSeverity(Severity severity) {
            if (severity.ordinal() > maxSeverity.ordinal()) {
                maxSeverity = severity;
            }
        }

        public boolean isClean() {
            return violations.isEmpty();
        }

        public List<Violation> getViolations() {
            return Collections.unmodifiableList(violations);
        }

        public Severity getMaxSeverity() {
            return maxSeverity;
        }

        public String getTargetId() {
            return targetId;
        }

        public boolean isCritical() {
            return maxSeverity == Severity.CRITICAL;
        }

        @Override
        public String toString() {
            if (isClean()) {
                return "IntegrityReport{clean=true}";
            }
            return String.format("IntegrityReport{violations=%d, severity=%s}",
                violations.size(), maxSeverity);
        }
    }

    public static class Violation {
        final ViolationType type;
        final String description;
        final Severity severity;

        Violation(ViolationType type, String description, Severity severity) {
            this.type = type;
            this.description = description;
            this.severity = severity;
        }

        public ViolationType getType() { return type; }
        public String getDescription() { return description; }
        public Severity getSeverity() { return severity; }
    }

    public enum ViolationType {
        IMPOSSIBLE_TIME,
        SUSPICIOUS_TIME,
        VERY_FAST_TIME,
        INVALID_TIME,
        LOW_TRUST_SCORE,
        HIGH_SUSPICIOUS_FLAGS,
        RECENT_CHEAT_DETECTION,
        TOO_FREQUENT_RUNS,
        SUSPICIOUS_CONSISTENCY,
        CUSTOM_SEED_USED,
        VERSION_MISMATCH,
        INVALID_TARGET,
        SUSPICIOUS_EFFECTS,
        FUTURE_TIMESTAMP,
        OLD_TIMESTAMP,
        INVALID_DATA,
        WORLD_MANIPULATION
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}

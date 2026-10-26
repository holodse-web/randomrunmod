package com.randomrun.core.service;

import com.randomrun.core.history.RunHistory;
import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunResult;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.core.session.RunSession;
import com.randomrun.core.session.RunSessionManager;
import com.randomrun.core.storage.CloudStorage;
import com.randomrun.core.storage.LocalStorage;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.data.GlobalStatsManager;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RunCompletionService {
    private static final String LOG_TAG = "[Run]";
    private static final int SAVE_TIMEOUT_SECONDS = 10;
    private static final int MAX_SAVE_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final RunSessionManager sessionManager;
    private final RunHistory runHistory;
    private final LocalStorage localStorage;
    private final CloudStorage cloudStorage;
    private final LeaderboardService leaderboardService;

    private final Queue<Run> saveQueue = new ConcurrentLinkedQueue<>();

    private RunCompletionService() {
        this.sessionManager = RunSessionManager.getInstance();
        this.runHistory = RunHistory.getInstance();
        this.localStorage = LocalStorage.INSTANCE;
        this.cloudStorage = CloudStorage.INSTANCE;
        this.leaderboardService = LeaderboardService.getInstance();
    }

    private static class Holder {
        private static final RunCompletionService INSTANCE = new RunCompletionService();
    }

    public static RunCompletionService getInstance() {
        return Holder.INSTANCE;
    }

    public CompletableFuture<Run> completeRun(RunResult result) {
        if (result == null) {
            CompactLogger.error("{} Result cannot be null", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        RunSession session = sessionManager.getCurrentSession();
        if (session == null) {
            CompactLogger.error("{} Cannot complete - no active session", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        com.randomrun.infrastructure.security.ModScannerService scanner =
            com.randomrun.infrastructure.security.ModScannerService.getInstance();
        boolean isUntrusted = !scanner.isTrusted();

        if (isUntrusted) {
            CompactLogger.warn("{} Untrusted mode - result will not be submitted to leaderboard", LOG_TAG);
        }

        return CompletableFuture.supplyAsync(() -> {
            Run savedRecord = null;

            try {
                RunSession currentSession = session;

                if (result == RunResult.VICTORY) {
                    currentSession.complete();
                } else {
                    currentSession.fail();
                }

                String playerName = getPlayerName();
                int attemptNumber = runHistory.getAttemptCount(currentSession.getTargetId(), currentSession.isUpgrader()) + 1;
                boolean onlineMode = isOnlineMode();

                Run record = new Run.Builder()
                    .targetId(currentSession.getTargetId())
                    .timestamp(System.currentTimeMillis())
                    .duration(currentSession.getElapsedTime())
                    .result(result)
                    .worldSeed(currentSession.getWorldSeed())
                    .customSeed(currentSession.getCustomSeed())
                    .isBattle(currentSession.isBattle())
                    .battleRoomId(currentSession.getBattleRoomId())
                    .isHardcore(currentSession.isHardcore())
                    .isRandomDrop(currentSession.isRandomDrop())
                    .isUpgrader(currentSession.isUpgrader())
                    .modVersion(currentSession.getModVersion())
                    .attemptNumber(attemptNumber)
                    .targetType(currentSession.getTargetType())
                    .mobVariant(currentSession.getMobVariant())
                    .build();

                com.randomrun.infrastructure.security.RunIntegrityChecker.IntegrityReport integrityReport =
                    com.randomrun.infrastructure.security.RunIntegrityChecker.checkRunIntegrity(
                        record,
                        com.randomrun.core.profile.PlayerProfile.get()
                    );

                if (!integrityReport.isClean()) {
                    CompactLogger.warn("{} Integrity violations detected: {}", LOG_TAG, integrityReport);

                    if (integrityReport.isCritical()) {
                        CompactLogger.error("{} CRITICAL integrity violation - run will not be submitted to leaderboard", LOG_TAG);
                        record = new Run.Builder()
                            .targetId(record.getTargetId())
                            .timestamp(record.getTimestamp())
                            .duration(record.getDuration())
                            .result(record.getResult())
                            .worldSeed(record.getWorldSeed())
                            .customSeed(record.getCustomSeed())
                            .isBattle(record.isBattle())
                            .battleRoomId(record.getBattleRoomId())
                            .isHardcore(record.isHardcore())
                            .isRandomDrop(record.isRandomDrop())
                            .isUpgrader(record.isUpgrader())
                            .modVersion(record.getModVersion())
                            .attemptNumber(record.getAttemptNumber())
                            .targetType(record.getTargetType())
                            .mobVariant(record.getMobVariant())
                            .build();
                    }
                }

                runHistory.addRecord(record);

                runHistory.markAsStale();

                com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
                if (profile != null && !record.isUpgrader()) {
                    profile.incrementTotalRuns();
                }

                updateTrustScore(result, record, isUntrusted);

                checkBanStatus();

                boolean saveSuccess = false;
                if (onlineMode && !record.isUpgrader()) {
                    saveSuccess = saveToCloudWithRetry(playerName, record);

                    if (saveSuccess && !saveQueue.isEmpty()) {
                        CompletableFuture.runAsync(() -> processFailedSaves(playerName));
                    }
                } else {
                    CompletableFuture<Boolean> localSave = localStorage.saveRecord(playerName, record);
                    saveSuccess = record.isUpgrader()
                        ? localSave.get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS) : true;
                }

                if (onlineMode && record.isEligibleForLeaderboard()) {
                    leaderboardService.submitRecord(record, playerName).thenAccept(success -> {
                    }).exceptionally(ex -> {
                        CompactLogger.error("{} Leaderboard error: {}", LOG_TAG, ex.getMessage());
                        return null;
                    });
                }

                if (onlineMode && !record.isUpgrader()) {
                    GlobalStatsManager.incrementRun();
                }

                if (sessionManager.getCurrentSession() == currentSession) {
                    sessionManager.cancelRun();
                }
                savedRecord = record;

                com.randomrun.ui.screen.main.MainModScreen.resetLeaderboardTimer();

                return record;
            } catch (Exception e) {
                CompactLogger.error("{} Completion error: {}", LOG_TAG, e.getMessage());

                if (savedRecord != null) {
                    CompactLogger.warn("{} Attempting rollback", LOG_TAG);
                    runHistory.getAllRecords().remove(savedRecord);
                }

                return null;
            }
        });
    }

    private boolean saveToCloudWithRetry(String playerName, Run record) {
        for (int attempt = 0; attempt < MAX_SAVE_RETRIES; attempt++) {
            try {
                CompletableFuture<Boolean> saveFuture = cloudStorage.saveRecord(playerName, record);
                boolean success = saveFuture.get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (success) {
                    return true;
                }

                CompactLogger.warn("{} Attempt {} failed", LOG_TAG, attempt + 1);
            } catch (TimeoutException e) {
                CompactLogger.warn("{} Timeout (attempt {})", LOG_TAG, attempt + 1);
            } catch (Exception e) {
                CompactLogger.error("{} Error (attempt {}): {}", LOG_TAG, attempt + 1, e.getMessage());
            }

            if (attempt < MAX_SAVE_RETRIES - 1) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        CompactLogger.warn("{} All {} attempts failed - falling back to local storage", LOG_TAG, MAX_SAVE_RETRIES);
        localStorage.saveRecord(playerName, record);
        saveQueue.offer(record);
        return false;
    }

    private void processFailedSaves(String playerName) {
        if (saveQueue.isEmpty()) return;

        int processed = 0;
        int failed = 0;

        while (!saveQueue.isEmpty() && processed < 10) {
            Run record = saveQueue.poll();
            if (record != null) {
                try {
                    boolean success = cloudStorage.saveRecord(playerName, record)
                        .get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (success) {
                        processed++;
                    } else {
                        saveQueue.offer(record);
                        failed++;
                        break;
                    }
                } catch (Exception e) {
                    saveQueue.offer(record);
                    failed++;
                    break;
                }
            }
        }

        if (failed > 0) {
            CompactLogger.warn("{} Failed to process {} records", LOG_TAG, failed);
        }
    }

    public CompletableFuture<Run> completeWithVictory() {
        return completeRun(RunResult.VICTORY);
    }

    public CompletableFuture<Run> completeWithDeath() {
        return completeRun(RunResult.DEATH);
    }

    public CompletableFuture<Run> completeWithGaveUp() {
        return completeRun(RunResult.GAVE_UP);
    }

    public CompletableFuture<Run> completeWithTimeout() {
        return completeRun(RunResult.TIMEOUT);
    }

    public int getSaveQueueSize() {
        return saveQueue.size();
    }

    private void updateTrustScore(RunResult result, Run record, boolean isUntrusted) {
        try {
            if (isUntrusted || record.isUpgrader()) {
                return;
            }

            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            if (profile == null) return;

            profile.applyTrustBonus(com.randomrun.core.profile.PlayerProfile.BONUS_CLEAN_RUN, "Clean run");

            if (result == RunResult.VICTORY) {
                if (record.isBattle()) {
                    profile.applyTrustBonus(com.randomrun.core.profile.PlayerProfile.BONUS_BATTLE_VICTORY, "Online battle victory");
                } else {
                    profile.applyTrustBonus(com.randomrun.core.profile.PlayerProfile.BONUS_VICTORY, "Victory");
                }
            }

            com.randomrun.core.profile.PlayerProfile.save();
        } catch (Exception e) {
            CompactLogger.error("{} Trust Score update error: {}", LOG_TAG, e.getMessage());
        }
    }

    private String getPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        } else if (client.getUser() != null) {
            return client.getUser().getName();
        }
        return "Unknown";
    }

    private boolean isOnlineMode() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode();
    }

    public CompletableFuture<Run> handleMobKill(net.minecraft.world.entity.player.Player player,
                                                net.minecraft.world.entity.EntityType<?> mobType,
                                                String mobVariant) {
        if (!sessionManager.hasActiveRun() || sessionManager.getTargetType() != com.randomrun.core.session.TargetType.MOB) {
            CompactLogger.warn("{} No active mob run", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        net.minecraft.world.entity.EntityType<?> targetMob = sessionManager.getTargetMob();
        String targetVariant = sessionManager.getMobVariant();

        if (mobType != targetMob) {
            CompactLogger.warn("{} Wrong mob killed", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        if (targetVariant != null && !targetVariant.equals(mobVariant)) {
            CompactLogger.warn("{} Wrong mob variant killed", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        return completeRun(RunResult.VICTORY);
    }

    private void checkBanStatus() {
    }
}

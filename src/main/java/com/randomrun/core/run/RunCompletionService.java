package com.randomrun.core.run;

import com.randomrun.core.data.CloudStorage;
import com.randomrun.core.data.LocalStorage;
import com.randomrun.core.run.Run.RunResult;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;

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

    private static volatile CompletableFuture<Run> lastVictory = CompletableFuture.completedFuture(null);

    public static CompletableFuture<Run> lastVictory() {
        return lastVictory;
    }

    public CompletableFuture<Run> completeRun(RunResult result) {
        return completeRun(result, false);
    }

    private CompletableFuture<Run> completeRun(RunResult result, boolean survivor) {
        if (result == null) {
            CompactLogger.error("{} Missing run result", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        RunSession session = sessionManager.getCurrentSession();
        if (session == null) {
            CompactLogger.error("{} No active session to complete", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }

        long finishedAt = System.currentTimeMillis();
        if (!(result == RunResult.VICTORY ? session.complete() : session.fail())) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Run> completion = completeSession(session, result, survivor, finishedAt);
        if (result == RunResult.VICTORY) lastVictory = completion;
        return completion;
    }

    private CompletableFuture<Run> completeSession(RunSession session, RunResult result, boolean survivor, long finishedAt) {

        com.randomrun.infrastructure.security.ModScannerService scanner =
            com.randomrun.infrastructure.security.ModScannerService.getInstance();
        scanner.refreshPacks();
        boolean isUntrusted = !scanner.isTrusted();

        if (isUntrusted) {
            CompactLogger.warn("{} Untrusted run detected", LOG_TAG);
        }

        var owner = com.randomrun.network.BackendSession.current();
        String ownerName = getPlayerName();
        boolean online = isOnlineMode();
        return CompletableFuture.supplyAsync(() -> {
            if (online && owner != com.randomrun.network.BackendSession.current()) return null;
            Run savedRecord = null;

            try {
                RunSession currentSession = session;


                String playerName = ownerName;
                int attemptNumber = runHistory.getAttemptCount(currentSession.getTargetId(), currentSession.isUpgrader()) + 1;
                boolean onlineMode = online && owner != null;

                Run record = new Run.Builder()
                    .targetId(currentSession.getTargetId())
                    .timestamp(finishedAt)
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
                    .build();

                com.randomrun.infrastructure.security.RunIntegrityChecker.IntegrityReport integrityReport =
                    com.randomrun.infrastructure.security.RunIntegrityChecker.checkRunIntegrity(
                        record,
                        com.randomrun.core.data.PlayerProfile.get()
                    );

                boolean excluded = isUntrusted || integrityReport.isCritical();
                if (!integrityReport.isClean()) {
                    CompactLogger.warn("{} Integrity violations: {}", LOG_TAG, integrityReport);
                }
                if (!currentSession.isBattle()) {
                    var proof = com.randomrun.network.RunVerifier.finished(record.historyKey().target(), currentSession.getRtaTime(), result == RunResult.VICTORY && !excluded);
                    if (!excluded) record.setProof(proof);
                }

                if (survivor) record.markSurvivor();
                synchronized (com.randomrun.network.BackendSession.class) {
                    if (online && owner != com.randomrun.network.BackendSession.current()) return null;
                    runHistory.addRecord(record);
                    runHistory.markAsStale();
                    com.randomrun.core.data.PlayerProfile profile = com.randomrun.core.data.PlayerProfile.get();
                    if (profile != null && !record.isUpgrader()) profile.incrementTotalRuns();
                    if (!onlineMode) updateTrustScore(result, record, excluded);
                }

                boolean voiceRun = com.randomrun.features.voice.control.VoiceController.isVoiceRun(currentSession);
                boolean cloudable = online && !excluded && !record.isUpgrader() && !voiceRun;
                boolean cloudSaved = cloudable && onlineMode && saveToCloudWithRetry(playerName, record, owner);
                if (cloudable && !cloudSaved) cloudStorage.queuePending(playerName, record);
                if (!cloudSaved) {
                    var localSave = localStorage.saveRecord(playerName, record).thenAccept(saved -> {
                        if (!saved) return;
                        synchronized (com.randomrun.network.BackendSession.class) {
                            if (!online || owner == com.randomrun.network.BackendSession.current()) runHistory.markAsStale();
                        }
                    });
                    try {
                        localSave.get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (TimeoutException ignored) {
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (cloudSaved && owner == com.randomrun.network.BackendSession.current()) {
                    leaderboardService.clearCache();
                    com.randomrun.core.data.PlayerProfile.load(playerName);
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

    private boolean saveToCloudWithRetry(String playerName, Run record, com.randomrun.network.BackendSession.Session owner) {
        for (int attempt = 0; attempt < MAX_SAVE_RETRIES; attempt++) {
            if (owner != com.randomrun.network.BackendSession.current()) break;
            try {
                if (cloudStorage.submitRecord(playerName, record).get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS) != null) {
                    return true;
                }
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

        CompactLogger.warn("{} Cloud save unavailable; saving locally", LOG_TAG);
        return false;
    }

    public CompletableFuture<Run> completeWithVictory() {
        return completeRun(RunResult.VICTORY);
    }

    public CompletableFuture<Run> completeWithSurvivorVictory() {
        return completeRun(RunResult.VICTORY, true);
    }

    public CompletableFuture<Run> completeWithDeath() {
        return completeRun(RunResult.DEATH);
    }

    public CompletableFuture<Run> completeWithLoss() {
        return completeRun(RunResult.LOST);
    }

    public CompletableFuture<Run> completeWithGaveUp() {
        return completeRun(RunResult.GAVE_UP);
    }

    public CompletableFuture<Run> completeWithTimeout() {
        return completeRun(RunResult.TIMEOUT);
    }

    private void updateTrustScore(RunResult result, Run record, boolean isUntrusted) {
        try {
            if (isUntrusted || record.isUpgrader()) {
                return;
            }

            com.randomrun.core.data.PlayerProfile profile = com.randomrun.core.data.PlayerProfile.get();
            if (profile == null) return;

            profile.applyTrustBonus(com.randomrun.core.data.PlayerProfile.BONUS_CLEAN_RUN, "Clean run");

            if (result == RunResult.VICTORY) {
                if (record.isBattle()) {
                    profile.applyTrustBonus(com.randomrun.core.data.PlayerProfile.BONUS_BATTLE_VICTORY, "Online battle victory");
                } else {
                    profile.applyTrustBonus(com.randomrun.core.data.PlayerProfile.BONUS_VICTORY, "Victory");
                }
            }

            com.randomrun.core.data.PlayerProfile.save();
        } catch (Exception e) {
            CompactLogger.error("{} Trust score update failed: {}", LOG_TAG, e.getMessage());
        }
    }

    private String getPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        } else if (client.getUser() != null) {
            return com.randomrun.network.BackendSession.playerName();
        }
        return "Unknown";
    }

    private boolean isOnlineMode() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode();
    }

}

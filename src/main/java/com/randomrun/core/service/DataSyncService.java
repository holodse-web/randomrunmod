package com.randomrun.core.service;

import com.randomrun.core.history.RunHistory;
import com.randomrun.core.history.Run;
import com.randomrun.core.storage.CloudStorage;
import com.randomrun.core.storage.LocalStorage;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

public class DataSyncService {
    private static final String LOG_TAG = "[Sync]";
    private static final int LOAD_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final RunHistory runHistory;
    private final LocalStorage localStorage;
    private final CloudStorage cloudStorage;

    private final AtomicBoolean isLoading = new AtomicBoolean(false);

    private volatile String lastLoadedPlayer = null;
    private volatile boolean dataLoaded = false;

    private DataSyncService() {
        this.runHistory = RunHistory.getInstance();
        this.localStorage = LocalStorage.INSTANCE;
        this.cloudStorage = CloudStorage.INSTANCE;
    }

    private static class Holder {
        private static final DataSyncService INSTANCE = new DataSyncService();
    }

    public static DataSyncService getInstance() {
        return Holder.INSTANCE;
    }

    public CompletableFuture<Boolean> loadPlayerData(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            CompactLogger.error("{} Player name empty", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }

        if (dataLoaded && playerName.equals(lastLoadedPlayer)) {
            return CompletableFuture.completedFuture(true);
        }

        if (isLoading.get()) {
            CompactLogger.warn("{} Load in progress", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }

        return loadWithRetry(playerName, 0);
    }

    private CompletableFuture<Boolean> loadWithRetry(String playerName, int attemptNumber) {
        isLoading.set(true);

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Run> allRecords = new ArrayList<>();

                if (isOnlineMode()) {
                    try {
                        CompletableFuture<List<Run>> cloudFuture = cloudStorage.loadHistory(playerName);
                        List<Run> cloudRecords = cloudFuture.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                        if (cloudRecords != null && !cloudRecords.isEmpty()) {
                            allRecords.addAll(cloudRecords);
                        }
                    } catch (Exception e) {
                        CompactLogger.warn("{} Cloud load failed: {}", LOG_TAG, e.getMessage());
                    }
                } else {
                    List<Run> localRecords = localStorage.loadHistory(playerName).join();
                    if (localRecords != null && !localRecords.isEmpty()) {
                        allRecords.addAll(localRecords);
                    }
                }

                mergeLocalUpgraderHistory(playerName, allRecords);
                runHistory.setRecords(allRecords);
                lastLoadedPlayer = playerName;
                dataLoaded = true;

                return true;
            } catch (Exception e) {
                CompactLogger.error("{} Init failed (attempt {}): {}", LOG_TAG, attemptNumber + 1, e.getMessage());

                if (attemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    isLoading.set(false);
                    return loadWithRetry(playerName, attemptNumber + 1).join();
                }

                return false;
            } finally {
                isLoading.set(false);
            }
        });
    }

    public CompletableFuture<Boolean> loadHistoryOnDemand() {
        String playerName = lastLoadedPlayer != null ? lastLoadedPlayer : getCurrentPlayerName();

        if (playerName == null || playerName.isEmpty()) {
            CompactLogger.warn("{} Player name empty", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }

        if (runHistory.isLoaded() && !runHistory.isStale() && runHistory.getTotalRecords() > 0) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Run> allRecords = new ArrayList<>();

                if (isOnlineMode()) {
                    try {
                        CompletableFuture<List<Run>> cloudFuture = cloudStorage.loadHistory(playerName);
                        List<Run> cloudRecords = cloudFuture.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                        if (cloudRecords != null && !cloudRecords.isEmpty()) {
                            allRecords.addAll(cloudRecords);
                        } else {
                            CompactLogger.warn("{} Cloud returned empty result", LOG_TAG);
                        }
                    } catch (Exception e) {
                        CompactLogger.warn("{} Cloud history unavailable; retaining local Upgrader records: {}", LOG_TAG, e.getMessage());
                    }
                } else {
                    List<Run> localRecords = localStorage.loadHistory(playerName).join();
                    if (localRecords != null && !localRecords.isEmpty()) {
                        allRecords.addAll(localRecords);
                    }
                }

                mergeLocalUpgraderHistory(playerName, allRecords);
                runHistory.setRecords(allRecords);
                return true;
            } catch (Exception e) {
                CompactLogger.error("{} History load failed: {}", LOG_TAG, e.getMessage(), e);
                return false;
            }
        });
    }

    public String getCurrentPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        } else if (client.getUser() != null) {
            return client.getUser().getName();
        }
        return "Unknown";
    }

    private void mergeLocalUpgraderHistory(String playerName, List<Run> records) {
        if (!isOnlineMode()) return;
        appendLocalUpgraderHistory(records, localStorage.loadHistory(playerName).join());
    }

    static void appendLocalUpgraderHistory(List<Run> records, List<Run> localRecords) {
        Set<Run> seen = new HashSet<>(records);
        for (Run local : localRecords) {
            if (local.isUpgrader() && seen.add(local)) records.add(local);
        }
    }

    private boolean isOnlineMode() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode();
    }
}

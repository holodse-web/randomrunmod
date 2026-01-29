package com.randomrun.main.data;

import com.google.gson.annotations.SerializedName;
import com.randomrun.main.RandomRunMod;
import com.randomrun.leaderboard.LeaderboardManager;
import com.randomrun.leaderboard.LeaderboardEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;

public class RunDataManager {
    
    public enum TargetType {
        ITEM,
        ADVANCEMENT
    }
    
    private TargetType targetType = TargetType.ITEM;
    private net.minecraft.util.Identifier targetAdvancementId;
    private String targetAdvancementName; // Cached achievement name
    private Item targetAdvancementIcon; // Cached achievement icon item
    private Item targetItem;
    private RunStatus status = RunStatus.INACTIVE;
    private long startTime;
    private long elapsedTime;
    private long timeLimit; 
    private Map<String, RunResult> results = new HashMap<>();
    
    private boolean paused = false;
    private long pauseStartTime = 0;
    private long totalPausedTime = 0;
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "randomrun");

    public enum RunStatus {
        INACTIVE,
        FROZEN,
        RUNNING,
        COMPLETED,
        FAILED
    }
    
    public static class RunResult {
        @SerializedName("itemId")
        public String itemId;
        @SerializedName("bestTime")
        public long bestTime;
        @SerializedName("attempts")
        public int attempts;
        @SerializedName("lastAttempt")
        public long lastAttempt;
        @SerializedName("hash")
        public String hash;
        
        public RunResult() {
        }
        
        public RunResult(String itemId, long bestTime) {
            this.itemId = itemId;
            this.bestTime = bestTime;
            this.attempts = 1;
            this.lastAttempt = System.currentTimeMillis();
            this.hash = generateHash(itemId, bestTime);
        }
        
        public static String generateHash(String itemId, long time) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                String data = itemId + time + "randomrun_secret_salt_2024";
                byte[] hash = md.digest(data.getBytes());
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString().substring(0, 16);
            } catch (Exception e) {
                return "invalid";
            }
        }
        
        public boolean verifyIntegrity() {
            return hash.equals(generateHash(itemId, bestTime));
        }
    }
    
    public RunDataManager() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        loadLocalResults();
        refreshResultsFromLeaderboard();
    }
    
    public void refreshResultsFromLeaderboard() {
        LeaderboardManager.getInstance().getPlayerRecords(LeaderboardManager.getInstance().getPlayerName())
            .thenAccept(entries -> {
                synchronized (results) {
                    boolean changed = false;
                    for (LeaderboardEntry entry : entries) {
                        RunResult current = results.get(entry.targetId);
                        // Merge: If we don't have it, or leaderboard time is better (lower)
                        if (current == null || entry.completionTime < current.bestTime) {
                            RunResult result = new RunResult();
                            result.itemId = entry.targetId;
                            result.bestTime = entry.completionTime;
                            result.lastAttempt = entry.timestamp;
                            result.attempts = current != null ? current.attempts : 1;
                            result.hash = RunResult.generateHash(result.itemId, result.bestTime);
                            results.put(result.itemId, result);
                            changed = true;
                        }
                    }
                    if (changed) {
                        saveLocalResults();
                    }
                }
                RandomRunMod.LOGGER.info("Synced results from Leaderboard. Total entries: " + results.size());
            });
    }

    private File getPlayerHistoryFile() {
        String playerName = "unknown";
        try {
            if (MinecraftClient.getInstance().getSession() != null) {
                playerName = MinecraftClient.getInstance().getSession().getUsername();
            }
        } catch (Exception e) {
            // Ignore
        }
        return new File(CONFIG_DIR, "run_history_" + playerName + ".json");
    }

    private void loadLocalResults() {
        File file = getPlayerHistoryFile();
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            Map<String, RunResult> loaded = gson.fromJson(reader, new TypeToken<Map<String, RunResult>>(){}.getType());
            if (loaded != null) {
                synchronized (results) {
                    results.putAll(loaded);
                }
                RandomRunMod.LOGGER.info("Loaded " + loaded.size() + " local results from " + file.getName());
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to load local run history", e);
        }
    }

    private void saveLocalResults() {
        try (Writer writer = new FileWriter(getPlayerHistoryFile())) {
            synchronized (results) {
                gson.toJson(results, writer);
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to save local run history", e);
        }
    }
    
    public void startNewRun(Item item, long timeLimitMs) {
        this.targetType = TargetType.ITEM;
        this.targetItem = item;
        this.targetAdvancementId = null;
        this.status = RunStatus.FROZEN;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.timeLimit = timeLimitMs;
        RandomRunMod.LOGGER.info("New run started for item: " + Registries.ITEM.getId(item));
    }
    
    public void startNewRun(net.minecraft.util.Identifier advancementId, long timeLimitMs) {
        this.targetType = TargetType.ADVANCEMENT;
        this.targetItem = null;
        this.targetAdvancementId = advancementId;
        this.targetAdvancementName = null;
        this.targetAdvancementIcon = null;
        this.status = RunStatus.FROZEN;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.timeLimit = timeLimitMs;
        RandomRunMod.LOGGER.info("New run started for advancement: " + advancementId);
    }
    
    public void setAdvancementDisplayInfo(String name, Item icon) {
        this.targetAdvancementName = name;
        this.targetAdvancementIcon = icon;
    }
    
    public String getTargetAdvancementName() {
        return targetAdvancementName;
    }
    
    public Item getTargetAdvancementIcon() {
        return targetAdvancementIcon;
    }
    
    public void unfreezeRun() {
        if (status == RunStatus.FROZEN) {
            status = RunStatus.RUNNING;
            startTime = System.currentTimeMillis();
            totalPausedTime = 0;
            paused = false;
            RandomRunMod.LOGGER.info("Run started!");
        }
    }
    
    public void pauseRun() {
        if (status == RunStatus.RUNNING && !paused) {
            paused = true;
            pauseStartTime = System.currentTimeMillis();
            RandomRunMod.LOGGER.info("Run paused");
        }
    }
    
    public void resumeRun() {
        if (status == RunStatus.RUNNING && paused) {
            totalPausedTime += System.currentTimeMillis() - pauseStartTime;
            paused = false;
            pauseStartTime = 0;
            RandomRunMod.LOGGER.info("Run resumed");
        }
    }
    
    public boolean isPaused() {
        return paused;
    }
    
    public void completeRun() {
        if (status == RunStatus.RUNNING) {
            elapsedTime = System.currentTimeMillis() - startTime - totalPausedTime;
            status = RunStatus.COMPLETED;
            saveResult();
            RandomRunMod.LOGGER.info("Run completed! Time: " + formatTime(elapsedTime));
        }
    }
    
    public void failRun() {
        if (status == RunStatus.RUNNING || status == RunStatus.FROZEN) {
            if (status == RunStatus.RUNNING) {
                elapsedTime = System.currentTimeMillis() - startTime;
            }
            status = RunStatus.FAILED;
            incrementAttempts();
            RandomRunMod.LOGGER.info("Run failed!");
        }
    }
    
    public void cancelRun() {
        status = RunStatus.INACTIVE;
        targetItem = null;
        targetAdvancementId = null;
        startTime = 0;
        elapsedTime = 0;
        timeLimit = 0;
    }
    
    public boolean checkItemPickup(Item item) {
        if (status == RunStatus.RUNNING && targetType == TargetType.ITEM && targetItem != null) {
            return item == targetItem;
        }
        return false;
    }
    
    public boolean checkAdvancementCompleted(net.minecraft.util.Identifier advancementId) {
        if (status == RunStatus.RUNNING && targetType == TargetType.ADVANCEMENT && targetAdvancementId != null) {
            return advancementId.equals(targetAdvancementId);
        }
        return false;
    }
    
    public boolean isTimeLimitExceeded() {
        if (status == RunStatus.RUNNING && timeLimit > 0) {
            return getCurrentTime() >= timeLimit;
        }
        return false;
    }
    
    public long getCurrentTime() {
        if (status == RunStatus.RUNNING) {
            long currentPauseTime = paused ? (System.currentTimeMillis() - pauseStartTime) : 0;
            return System.currentTimeMillis() - startTime - totalPausedTime - currentPauseTime;
        }
        return elapsedTime;
    }
    
    public long getRemainingTime() {
        if (timeLimit > 0) {
            return Math.max(0, timeLimit - getCurrentTime());
        }
        return -1;
    }
    
    private void saveResult() {
        String itemId;
        String tType;
        String difficulty = "Normal";
        
        if (targetType == TargetType.ITEM) {
            if (targetItem == null) return;
            itemId = Registries.ITEM.getId(targetItem).toString();
            tType = "ITEM";
        } else {
            if (targetAdvancementId == null) return;
            itemId = targetAdvancementId.toString();
            tType = "ADVANCEMENT";
            difficulty = "Achievement";
        }
        
        // Update local cache first for immediate UI feedback
        synchronized (results) {
            RunResult existing = results.get(itemId);
            if (existing == null) {
                results.put(itemId, new RunResult(itemId, elapsedTime));
            } else {
                existing.attempts++; // Increment local attempts
                existing.lastAttempt = System.currentTimeMillis();
                if (elapsedTime < existing.bestTime) {
                    existing.bestTime = elapsedTime;
                    existing.hash = RunResult.generateHash(itemId, elapsedTime);
                }
            }
            saveLocalResults();
        }
        
        // Note: Leaderboard submission is handled by VictoryHandler to prevent duplicates.
    }
    
    private void incrementAttempts() {
        // Just update local cache, no DB save for attempts on failure
        String itemId;
        if (targetType == TargetType.ITEM) {
            if (targetItem == null) return;
            itemId = Registries.ITEM.getId(targetItem).toString();
        } else {
            if (targetAdvancementId == null) return;
            itemId = targetAdvancementId.toString();
        }
        
        synchronized (results) {
            RunResult existing = results.get(itemId);
            if (existing != null) {
                existing.attempts++;
                existing.lastAttempt = System.currentTimeMillis();
            }
        }
    }
    
    public static String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    public TargetType getTargetType() {
        return targetType;
    }

    public Item getTargetItem() {
        return targetItem;
    }
    
    public net.minecraft.util.Identifier getTargetAdvancementId() {
        return targetAdvancementId;
    }
    
    public RunStatus getStatus() {
        return status;
    }
    
    public long getElapsedTime() {
        return elapsedTime;
    }
    
    public long getTimeLimit() {
        return timeLimit;
    }
    
    public Map<String, RunResult> getResults() {
        synchronized (results) {
            return new HashMap<>(results);
        }
    }
    
    public RunResult getResultForItem(String itemId) {
        synchronized (results) {
            return results.get(itemId);
        }
    }
    
    public List<RunResult> getAllResultsSorted() {
        synchronized (results) {
            List<RunResult> sorted = new ArrayList<>(results.values());
            sorted.sort(Comparator.comparingLong(r -> r.bestTime));
            return sorted;
        }
    }
    
    public void setTargetItem(Item item) {
        this.targetItem = item;
    }
}

package com.randomrun.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.randomrun.RandomRunMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

public class RunDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH = FabricLoader.getInstance().getConfigDir().resolve("randomrun_data.json");
    
    private Item targetItem;
    private RunStatus status = RunStatus.INACTIVE;
    private long startTime;
    private long elapsedTime;
    private long timeLimit; 
    private Map<String, RunResult> results = new HashMap<>();
    
    
    private boolean paused = false;
    private long pauseStartTime = 0;
    private long totalPausedTime = 0;
    
    public enum RunStatus {
        INACTIVE,
        FROZEN,
        RUNNING,
        COMPLETED,
        FAILED
    }
    
    public static class RunResult {
        public String itemId;
        public long bestTime;
        public int attempts;
        public long lastAttempt;
        public String hash;
        
        public RunResult(String itemId, long bestTime) {
            this.itemId = itemId;
            this.bestTime = bestTime;
            this.attempts = 1;
            this.lastAttempt = System.currentTimeMillis();
            this.hash = generateHash(itemId, bestTime);
        }
        
        private static String generateHash(String itemId, long time) {
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
        loadResults();
    }
    
    public void startNewRun(Item item, long timeLimitMs) {
        this.targetItem = item;
        this.status = RunStatus.FROZEN;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.timeLimit = timeLimitMs;
        RandomRunMod.LOGGER.info("New run started for item: " + Registries.ITEM.getId(item));
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
            elapsedTime = System.currentTimeMillis() - startTime;
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
        startTime = 0;
        elapsedTime = 0;
        timeLimit = 0;
    }
    
    public boolean checkItemPickup(Item item) {
        if (status == RunStatus.RUNNING && targetItem != null) {
            return item == targetItem;
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
        if (targetItem == null) return;
        
        String itemId = Registries.ITEM.getId(targetItem).toString();
        RunResult existing = results.get(itemId);
        
        if (existing == null) {
            results.put(itemId, new RunResult(itemId, elapsedTime));
        } else {
            existing.attempts++;
            existing.lastAttempt = System.currentTimeMillis();
            if (elapsedTime < existing.bestTime) {
                existing.bestTime = elapsedTime;
                existing.hash = RunResult.generateHash(itemId, elapsedTime);
            }
        }
        
        saveResults();
    }
    
    private void incrementAttempts() {
        if (targetItem == null) return;
        
        String itemId = Registries.ITEM.getId(targetItem).toString();
        RunResult existing = results.get(itemId);
        
        if (existing != null) {
            existing.attempts++;
            existing.lastAttempt = System.currentTimeMillis();
            saveResults();
        }
    }
    
    private void loadResults() {
        if (Files.exists(DATA_PATH)) {
            try (Reader reader = Files.newBufferedReader(DATA_PATH)) {
                Type type = new TypeToken<Map<String, RunResult>>(){}.getType();
                Map<String, RunResult> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    
                    for (Map.Entry<String, RunResult> entry : loaded.entrySet()) {
                        if (entry.getValue().verifyIntegrity()) {
                            results.put(entry.getKey(), entry.getValue());
                        } else {
                            RandomRunMod.LOGGER.warn("Invalid result detected for: " + entry.getKey());
                        }
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to load run data", e);
            }
        }
    }
    
    private void saveResults() {
        try {
            Files.createDirectories(DATA_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(DATA_PATH)) {
                GSON.toJson(results, writer);
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to save run data", e);
        }
    }
    
    public static String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    
    public Item getTargetItem() {
        return targetItem;
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
        return Collections.unmodifiableMap(results);
    }
    
    public RunResult getResultForItem(String itemId) {
        return results.get(itemId);
    }
    
    public List<RunResult> getAllResultsSorted() {
        List<RunResult> sorted = new ArrayList<>(results.values());
        sorted.sort(Comparator.comparingLong(r -> r.bestTime));
        return sorted;
    }
    
    public void setTargetItem(Item item) {
        this.targetItem = item;
    }
}

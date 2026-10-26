package com.randomrun.infrastructure.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.IdCompressor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RunDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "randomrun");
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "RandomRun-HistoryIO");
        thread.setDaemon(true);
        return thread;
    });
    private static final long SAVE_DEBOUNCE_MS = 1000;
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final String RESULT_VICTORY = "VICTORY";
    private static final String RESULT_DEATH = "DEATH";
    private static final String RESULT_FAILED = "FAILED";
    private static final String SEED_UNKNOWN = "unknown";
    
    private final AtomicBoolean isDirty = new AtomicBoolean(false);
    private final Object resultsLock = new Object();
    private final Map<String, RunResult> results = new HashMap<>();
    private final Map<String, RunResult> localResults = new HashMap<>();
    
    private Item targetItem;
    private String runWorldName;
    private RunStatus status = RunStatus.INACTIVE;
    private long startTime;
    private long elapsedTime;
    private long lastSaveTime = 0;
    
    private boolean paused = false;
    private long pauseStartTime = 0;
    private long totalPausedTime = 0;

    public enum RunStatus {
        INACTIVE, FROZEN, RUNNING, COMPLETED, FAILED
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
        @SerializedName("attempts_history")
        public List<AttemptRecord> attemptsHistory = new ArrayList<>();
        
        public static class AttemptRecord {
            public long timestamp;
            public String seed;
            public long duration;
            public String result;
            public boolean isOnline;
            public boolean isHardcore;
            
            public AttemptRecord(long timestamp, String seed, long duration, String result, boolean isOnline, boolean isHardcore) {
                this.timestamp = timestamp;
                this.seed = seed;
                this.duration = duration;
                this.result = result;
                this.isOnline = isOnline;
                this.isHardcore = isHardcore;
            }
            
            public AttemptRecord(long timestamp, String seed, long duration, String result, boolean isOnline) {
                this(timestamp, seed, duration, result, isOnline, true);
            }
        }
        
        public RunResult() {}
        
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
                SecureRandom random = new SecureRandom();
                byte[] saltBytes = new byte[16];
                random.nextBytes(saltBytes);
                String salt = Base64.getEncoder().encodeToString(saltBytes);
                String data = itemId + ":" + time + ":" + salt + ":" + System.currentTimeMillis();
                byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                return "invalid";
            }
        }
        
        public boolean verifyIntegrity() {
            return hash != null && !hash.isEmpty() && !hash.equals("invalid");
        }
    }
    
    public RunDataManager() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        reloadResults();
    }
    
    public void reloadResults() {
        synchronized (resultsLock) {
            results.clear();
        }
        
        loadLocalResults().thenRun(() -> {
            synchronized (resultsLock) {
                results.putAll(localResults);
            }
        });
    }

    private File getPlayerHistoryFile() {
        String playerName = "unknown";
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getUser() != null) {
                playerName = client.getUser().getName();
            }
        } catch (Exception e) {
            playerName = "unknown_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return new File(CONFIG_DIR, "run_history_" + playerName + ".json");
    }

    private CompletableFuture<Void> loadLocalResults() {
        return CompletableFuture.runAsync(() -> {
            File file = getPlayerHistoryFile();
            if (!file.exists()) {
                synchronized (resultsLock) {
                    localResults.clear();
                }
                return;
            }

            try (Reader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                Map<String, RunResult> loaded = GSON.fromJson(reader, 
                    new TypeToken<Map<String, RunResult>>(){}.getType());
                if (loaded != null && validateLoadedData(loaded)) {
                    synchronized (resultsLock) {
                        localResults.clear();
                        localResults.putAll(loaded);
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("[RunData] Ошибка загрузки", e);
            }
        }, IO_EXECUTOR);
    }

    private boolean validateLoadedData(Map<String, RunResult> data) {
        for (RunResult result : data.values()) {
            if (result.attemptsHistory != null && result.attemptsHistory.size() > MAX_HISTORY_SIZE) {
                result.attemptsHistory = result.attemptsHistory.subList(
                    result.attemptsHistory.size() - MAX_HISTORY_SIZE, 
                    result.attemptsHistory.size()
                );
            }
        }
        return true;
    }

    private void saveLocalResults() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_DEBOUNCE_MS) {
            return;
        }
        
        if (!isDirty.compareAndSet(true, false)) {
            return;
        }
        
        lastSaveTime = now;
        
        final Map<String, RunResult> snapshot;
        synchronized (resultsLock) {
            snapshot = new HashMap<>(localResults);
        }
        
        IO_EXECUTOR.submit(() -> writeLocalResults(snapshot));
    }

    private void writeLocalResults(Map<String, RunResult> snapshot) {
        try {
            File historyFile = getPlayerHistoryFile();
            File tempFile = new File(historyFile.getAbsolutePath() + ".tmp");

            if (historyFile.getParentFile() != null && !historyFile.getParentFile().exists()) {
                historyFile.getParentFile().mkdirs();
            }

            try (FileChannel channel = FileChannel.open(tempFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 FileLock lock = channel.lock();
                 Writer writer = new BufferedWriter(new OutputStreamWriter(
                     Files.newOutputStream(tempFile.toPath()), StandardCharsets.UTF_8))) {
                GSON.toJson(snapshot, writer);
            }

            Files.move(tempFile.toPath(), historyFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            isDirty.set(true);
            RandomRunMod.LOGGER.error("[RunData] Ошибка сохранения", e);
        }
    }
    
    public void startNewRun(Item item, String worldName) {
        this.targetItem = item;
        this.status = RunStatus.FROZEN;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.runWorldName = worldName;
        resetPauseState();
    }

    private void resetPauseState() {
        this.paused = false;
        this.pauseStartTime = 0;
        this.totalPausedTime = 0;
    }
    
    public void unfreezeRun() {
        if (status != RunStatus.FROZEN) return;
        
        if (!isValidWorld()) {
            RandomRunMod.LOGGER.warn("[RunData] Неверный мир: " + runWorldName);
            return;
        }
        
        if (hasPlayerItem()) {
            RandomRunMod.LOGGER.warn("[RunData] Предмет уже есть, отмена");
            cancelRun();
            sendPlayerMessage("§cОшибка: У вас уже есть этот предмет! Спидран отменен.");
            return;
        }

        healPlayer();
        
        this.startTime = System.currentTimeMillis();
        this.elapsedTime = 0;
        this.status = RunStatus.RUNNING;
        resetPauseState();
        
        com.randomrun.infrastructure.handler.VictoryHandler.reset();
    }
    
    private boolean hasPlayerItem() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (targetItem == null || client == null || client.player == null) {
                return false;
            }
            
            for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = client.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() == targetItem) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }
    
    private void healPlayer() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.setHealth(client.player.getMaxHealth());
                client.player.getFoodData().setFoodLevel(20);
                client.player.getFoodData().setSaturation(5.0f);
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("[RunData] Ошибка исцеления: " + e.getMessage());
        }
    }
    
    private void sendPlayerMessage(String message) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                com.randomrun.infrastructure.util.ClientMessages.send(client.player, Component.literal(message), false);
            }
        } catch (Exception e) {
        }
    }
    
    public void pauseRun() {
        if (status == RunStatus.RUNNING && !paused) {
            paused = true;
            pauseStartTime = System.currentTimeMillis();
        }
    }
    
    public void resumeRun() {
        if (status == RunStatus.RUNNING && paused) {
            totalPausedTime += System.currentTimeMillis() - pauseStartTime;
            paused = false;
            pauseStartTime = 0;
        }
    }
    
    public void completeRun() {
        if (status == RunStatus.RUNNING) {
            elapsedTime = System.currentTimeMillis() - startTime - totalPausedTime;
            status = RunStatus.COMPLETED;
            
            com.randomrun.core.service.RunCompletionService.getInstance()
                .completeRun(com.randomrun.core.history.RunResult.VICTORY);
        }
    }
    
    public void failRun() {
        if (status == RunStatus.RUNNING || status == RunStatus.FROZEN) {
            if (status == RunStatus.RUNNING) {
                elapsedTime = System.currentTimeMillis() - startTime;
            }
            status = RunStatus.FAILED;
            
            com.randomrun.core.service.RunCompletionService.getInstance()
                .completeRun(com.randomrun.core.history.RunResult.DEATH);
        }
    }
    
    public void cancelRun() {
        status = RunStatus.INACTIVE;
        targetItem = null;
        runWorldName = null;
        startTime = 0;
        elapsedTime = 0;
    }
    
    private boolean isValidWorld() {
        if (runWorldName == null) return true;
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSingleplayerServer() != null) {
                String currentWorld = client.getSingleplayerServer().getWorldData().getLevelName();
                return runWorldName.equals(currentWorld);
            }
        } catch (Exception e) {
        }
        return true;
    }
    
    public boolean checkItemPickup(Item item) {
        if (status == RunStatus.RUNNING && targetItem != null) {
            if (!isValidWorld()) {
                RandomRunMod.LOGGER.warn("[RunData] Неверный мир для предмета");
                return false;
            }
            return item == targetItem;
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
    
    private String getCurrentRunSeed() {
        String seed = com.randomrun.features.challenges.classic.world.WorldCreator.getLastCreatedSeed();
        if (seed != null && !seed.isEmpty()) return seed;

        try {
            com.randomrun.features.battle.manager.BattleRoom room = 
                com.randomrun.features.battle.manager.BattleManager.getInstance().getCurrentRoom();
            if (room != null && room.getSeed() != null) return room.getSeed();
        } catch (Exception e) {}

        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSingleplayerServer() != null) {
                return String.valueOf(client.getSingleplayerServer().overworld().getSeed());
            }
        } catch (Exception e) {}
        
        return SEED_UNKNOWN;
    }

    private void saveResult() {
        String itemId = getItemIdForTarget();
        if (itemId == null) return;
        
        String seed = getCurrentRunSeed();
        boolean isOnlineRun = isOnlineRun();
        boolean isHardcore = isHardcoreMode();
        
        updateResultData(itemId, elapsedTime, seed, RESULT_VICTORY, isOnlineRun, isHardcore);
    }
    
    private void incrementAttempts() {
        String itemId = getItemIdForTarget();
        if (itemId == null) return;
        
        String seed = getCurrentRunSeed();
        String resultStatus = (status == RunStatus.FAILED) ? RESULT_DEATH : RESULT_FAILED;
        boolean isOnlineRun = isOnlineRun();
        boolean isHardcore = isHardcoreMode();
        
        long duration = (elapsedTime > 0) ? elapsedTime : 
                       (startTime > 0) ? (System.currentTimeMillis() - startTime) : 0;

        updateResultData(itemId, duration, seed, resultStatus, isOnlineRun, isHardcore);
    }
    
    private String getItemIdForTarget() {
        if (targetItem == null) return null;
        return IdCompressor.compress(BuiltInRegistries.ITEM.getKey(targetItem).toString());
    }
    
    private boolean isOnlineRun() {
        return com.randomrun.features.battle.manager.BattleManager.getInstance().isInBattle();
    }
    
    private boolean isHardcoreMode() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.level != null) {
                return client.level.getLevelData().isHardcore();
            }
            return RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
        } catch (Exception e) {
            return true;
        }
    }
    
    private void updateResultData(String itemId, long duration, String seed, String resultStatus, 
                                   boolean isOnlineRun, boolean isHardcore) {
        long timestamp = System.currentTimeMillis();
        RunResult.AttemptRecord attempt = new RunResult.AttemptRecord(
            timestamp, seed, duration, resultStatus, isOnlineRun, isHardcore
        );
        
        boolean isVictory = RESULT_VICTORY.equals(resultStatus);
        
        synchronized (resultsLock) {
            updateResultMap(localResults, itemId, timestamp, duration, isVictory, attempt);
            updateResultMap(results, itemId, timestamp, duration, isVictory, attempt);
        }
        
        isDirty.set(true);
        saveLocalResults();
    }
    
    private void updateResultMap(Map<String, RunResult> map, String itemId, long timestamp, 
                                  long duration, boolean isVictory, RunResult.AttemptRecord attempt) {
        RunResult result = map.computeIfAbsent(itemId, k -> {
            RunResult r = new RunResult();
            r.itemId = itemId;
            r.bestTime = isVictory ? duration : Long.MAX_VALUE;
            r.hash = isVictory ? RunResult.generateHash(itemId, duration) : "";
            r.attempts = 0;
            r.lastAttempt = timestamp;
            return r;
        });
        
        result.attempts++;
        result.lastAttempt = timestamp;
        
        if (result.attemptsHistory.size() >= MAX_HISTORY_SIZE) {
            result.attemptsHistory.remove(0);
        }
        result.attemptsHistory.add(attempt);
        
        if (isVictory && duration < result.bestTime) {
            result.bestTime = duration;
            result.hash = RunResult.generateHash(itemId, duration);
        }
    }
    
    public static String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    public void shutdown() {
        if (isDirty.compareAndSet(true, false)) {
            final Map<String, RunResult> snapshot;
            synchronized (resultsLock) {
                snapshot = new HashMap<>(localResults);
            }
            
            IO_EXECUTOR.submit(() -> writeLocalResults(snapshot));
        }
        
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                RandomRunMod.LOGGER.warn("[RunData] Таймаут сохранения при выходе");
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public Item getTargetItem() { return targetItem; }
    public String getRunWorldName() { return runWorldName; }
    public void setRunWorldName(String runWorldName) { this.runWorldName = runWorldName; }
    public RunStatus getStatus() { return status; }
    public long getElapsedTime() { return elapsedTime; }
    public boolean isPaused() { return paused; }
    public void setTargetItem(Item item) { this.targetItem = item; }
    
    public Map<String, RunResult> getResults() {
        synchronized (resultsLock) {
            return new HashMap<>(results);
        }
    }
    
    public RunResult getResultForItem(String itemId) {
        synchronized (resultsLock) {
            return results.get(itemId);
        }
    }
    
    public List<RunResult> getAllResultsSorted() {
        synchronized (resultsLock) {
            List<RunResult> sorted = new ArrayList<>(results.values());
            sorted.sort(Comparator.comparingLong(r -> r.bestTime));
            return sorted;
        }
    }
}

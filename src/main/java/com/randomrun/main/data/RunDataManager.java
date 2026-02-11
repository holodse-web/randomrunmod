package com.randomrun.main.data;

import com.google.gson.annotations.SerializedName;
import com.randomrun.main.RandomRunMod;
import com.randomrun.leaderboard.LeaderboardManager;
import com.randomrun.leaderboard.LeaderboardEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import com.randomrun.main.util.IdCompressor;

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
    private String runWorldName;
    private RunStatus status = RunStatus.INACTIVE;
    private long startTime;
    private long elapsedTime;
    private long timeLimit; 
    private Map<String, RunResult> results = new HashMap<>();
    private Map<String, RunResult> localResults = new HashMap<>();
    
    private boolean paused = false;
    private long pauseStartTime = 0;
    private long totalPausedTime = 0;
    
    // Modifiers
    private final List<com.randomrun.challenges.modifier.Modifier> activeModifiers = new ArrayList<>();
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "randomrun");
    private static final java.util.concurrent.ExecutorService IO_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean isDirty = false;

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
        
        // Новые поля для истории
        @SerializedName("attempts_history")
        public List<AttemptRecord> attemptsHistory = new ArrayList<>();
        
        public static class AttemptRecord {
            public long timestamp;
            public String seed;
            public long duration;
            public String result; // "VICTORY", "DEATH", "GAVE_UP", "FAILED"
            public boolean isOnline;
            public boolean isHardcore; // New field
            
            public AttemptRecord(long timestamp, String seed, long duration, String result, boolean isOnline, boolean isHardcore) {
                this.timestamp = timestamp;
                this.seed = seed;
                this.duration = duration;
                this.result = result;
                this.isOnline = isOnline;
                this.isHardcore = isHardcore;
            }
            
            // Legacy constructor
            public AttemptRecord(long timestamp, String seed, long duration, String result, boolean isOnline) {
                this(timestamp, seed, duration, result, isOnline, true); // Default to hardcore for old records
            }
        }
        
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
                // Use a more complex salt for result signing
                String salt = "rrm_v1_secure_salt_" + (time % 9999) + "_DoNotReverseThisPlease";
                String data = itemId + ":" + time + ":" + salt;
                byte[] hash = md.digest(data.getBytes());
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
            return hash.equals(generateHash(itemId, bestTime));
        }
    }
    
    public RunDataManager() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        reloadResults();
    }
    
    public void reloadResults() {
        synchronized (results) {
            results.clear();
        }
        
        loadLocalResults().thenRun(() -> {
            // Always populate results with local data first
            synchronized (localResults) {
                synchronized (results) {
                    results.putAll(localResults);
                }
            }
            
            if (RandomRunMod.getInstance().getConfig().isOnlineMode()) {
                refreshResultsFromLeaderboard();
            }
        });
    }
    
    public CompletableFuture<Void> refreshResultsFromLeaderboard() {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            com.randomrun.main.data.PlayerProfile profile = com.randomrun.main.data.PlayerProfile.get();
            if (profile != null) {
                synchronized (results) {
                    // Do NOT clear results - merge with local data instead
                    // results.clear(); 
                    
                    // 1. Load bests
                    for (Map.Entry<String, Long> entry : profile.getBests().entrySet()) {
                         String itemId = entry.getKey();
                         long time = entry.getValue();
                         
                         RunResult r = results.get(itemId);
                         if (r == null) {
                             r = new RunResult(itemId, time);
                             r.attempts = 0; 
                             results.put(itemId, r);
                         } else {
                             // Update best time if online is better
                             if (time < r.bestTime) {
                                 r.bestTime = time;
                             }
                         }
                    }
                    
                    // 2. Load history
                    List<com.randomrun.main.data.PlayerProfile.RunRecord> history = profile.getHistory();
                    RandomRunMod.LOGGER.info("Loading history from profile. Size: " + history.size());
                    
                    for (com.randomrun.main.data.PlayerProfile.RunRecord record : history) {
                        String itemId = record.itemId;
                        RunResult r = results.get(itemId);
                        if (r == null) {
                            // If we have history but no best time (e.g. only failed runs), create result entry
                            r = new RunResult();
                            r.itemId = itemId;
                            r.bestTime = Long.MAX_VALUE; // No valid best time yet
                            results.put(itemId, r);
                        }
                        
                        // Deduplication check
                        boolean exists = false;
                        for (RunResult.AttemptRecord existing : r.attemptsHistory) {
                            if (Math.abs(existing.timestamp - record.timestamp) < 1000 && existing.duration == record.duration) {
                                exists = true;
                                break;
                            }
                        }
                        
                        if (!exists) {
                            r.attempts++;
                            if (record.timestamp > r.lastAttempt) {
                                r.lastAttempt = record.timestamp;
                            }
                            
                            // Convert Profile Record to Attempt Record
                            String resultStatus = record.win ? "VICTORY" : "FAILED";
                            r.attemptsHistory.add(new RunResult.AttemptRecord(
                                record.timestamp, record.seed, record.duration, resultStatus, record.isOnline, record.isHardcore
                            ));
                            
                            // Update best time if needed
                            if (record.win && record.duration < r.bestTime) {
                                r.bestTime = record.duration;
                            }
                        }
                    }
                    
                    // 3. Fix attempts count for items with best time but NO history (legacy data)
                    for (RunResult r : results.values()) {
                        if (r.attempts == 0) {
                            r.attempts = 1;
                            // Add a dummy history record for the best time
                            r.attemptsHistory.add(new RunResult.AttemptRecord(
                                System.currentTimeMillis(), "unknown", r.bestTime, "VICTORY", true
                            ));
                        }
                    }
                }
                RandomRunMod.LOGGER.info("Synced results from PlayerProfile. Total: " + results.size());
            }
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

    private CompletableFuture<Void> loadLocalResults() {
        return CompletableFuture.runAsync(() -> {
            File file = getPlayerHistoryFile();
            if (!file.exists()) {
                synchronized (localResults) {
                    localResults.clear();
                }
                return;
            }

            try (Reader reader = new FileReader(file)) {
                Map<String, RunResult> loaded = gson.fromJson(reader, new TypeToken<Map<String, RunResult>>(){}.getType());
                if (loaded != null) {
                    synchronized (localResults) {
                        localResults.clear();
                        localResults.putAll(loaded);
                    }
                    RandomRunMod.LOGGER.info("Загружено " + loaded.size() + " локальных результатов из " + file.getName());
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Не удалось загрузить локальную историю забегов", e);
            }
        }, IO_EXECUTOR);
    }

    private void saveLocalResults() {
        if (!isDirty) return;
        
        // Create snapshot for saving
        final Map<String, RunResult> snapshot;
        synchronized (localResults) {
            snapshot = new HashMap<>(localResults);
        }
        
        IO_EXECUTOR.submit(() -> {
            File historyFile = getPlayerHistoryFile();
            // Ensure directory exists
            if (historyFile.getParentFile() != null && !historyFile.getParentFile().exists()) {
                historyFile.getParentFile().mkdirs();
            }
            
            try (Writer writer = new FileWriter(historyFile)) {
                gson.toJson(snapshot, writer);
                RandomRunMod.LOGGER.info("Сохранена локальная история забегов: " + historyFile.getName());
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Не удалось сохранить локальную историю забегов", e);
            }
        });
        isDirty = false;
    }
    
    public void startNewRun(Item item, long timeLimitMs, String worldName) {
        this.targetType = TargetType.ITEM;
        this.targetItem = item;
        this.targetAdvancementId = null;
        this.status = RunStatus.FROZEN;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.timeLimit = timeLimitMs;
        this.runWorldName = worldName;
        this.activeModifiers.clear();
        // RandomRunMod.getInstance().getConfig().setModifiersEnabled(false); // REMOVED: Do not disable modifiers in config!
        RandomRunMod.LOGGER.info("Начат новый забег для предмета: " + Registries.ITEM.getId(item));
    }
    
    public void startNewRun(net.minecraft.util.Identifier advancementId, long timeLimitMs, String worldName) {
        this.targetType = TargetType.ADVANCEMENT;
        this.targetItem = null;
        this.targetAdvancementId = advancementId;
        this.targetAdvancementName = null;
        this.targetAdvancementIcon = null;
        this.status = RunStatus.FROZEN;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.timeLimit = timeLimitMs;
        this.runWorldName = worldName;
        this.activeModifiers.clear();
        RandomRunMod.LOGGER.info("Начат новый забег для достижения: " + advancementId);
    }

    public List<com.randomrun.challenges.modifier.Modifier> getActiveModifiers() {
        return activeModifiers;
    }

    public void addActiveModifier(com.randomrun.challenges.modifier.Modifier modifier) {
        if (!this.activeModifiers.contains(modifier)) {
            this.activeModifiers.add(modifier);
        }
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
    
    public String getRunWorldName() {
        return runWorldName;
    }

    public void setRunWorldName(String runWorldName) {
        this.runWorldName = runWorldName;
    }
    
    public void unfreezeRun() {
        if (status == RunStatus.FROZEN) {
            if (!isCorrectWorld()) {
                RandomRunMod.LOGGER.warn("Невозможно начать забег в неправильном мире. Ожидается: " + runWorldName);
                return;
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (targetType == TargetType.ITEM && targetItem != null && client.player != null) {
                // Check if player already has the item
                boolean hasItem = false;
                for (int i = 0; i < client.player.getInventory().size(); i++) {
                    ItemStack stack = client.player.getInventory().getStack(i);
                    if (!stack.isEmpty() && stack.getItem() == targetItem) {
                        hasItem = true;
                        break;
                    }
                }
                
                if (hasItem) {
                    RandomRunMod.LOGGER.warn("У игрока уже есть целевой предмет! Отмена забега.");
                    cancelRun();
                    client.player.sendMessage(Text.literal("§cОшибка: У вас уже есть этот предмет! Спидран отменен."), false);
                    return;
                }
            }

            // Always use local time for fairness against clock skew
            // Clock skew caused 11s vs 4s issues previously.
            // Network latency (100ms) is acceptable compared to clock skew (seconds).
            this.startTime = System.currentTimeMillis();
            
            this.elapsedTime = 0;
            this.status = RunStatus.RUNNING;
            this.paused = false;
            this.pauseStartTime = 0;
            this.totalPausedTime = 0;
            
            // Reset Victory Handler state
            com.randomrun.main.util.VictoryHandler.reset();
            
            RandomRunMod.LOGGER.info("Забег начался! Цель: " + (targetType == TargetType.ITEM ? targetItem : targetAdvancementId));
        }
    }
    
    public void pauseRun() {
        if (status == RunStatus.RUNNING && !paused) {
            paused = true;
            pauseStartTime = System.currentTimeMillis();
            RandomRunMod.LOGGER.info("Забег приостановлен");
        }
    }
    
    public void resumeRun() {
        if (status == RunStatus.RUNNING && paused) {
            totalPausedTime += System.currentTimeMillis() - pauseStartTime;
            paused = false;
            pauseStartTime = 0;
            RandomRunMod.LOGGER.info("Забег возобновлен");
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
            RandomRunMod.LOGGER.info("Забег завершен! Время: " + formatTime(elapsedTime));
        }
    }
    
    public void failRun() {
        if (status == RunStatus.RUNNING || status == RunStatus.FROZEN) {
            if (status == RunStatus.RUNNING) {
                elapsedTime = System.currentTimeMillis() - startTime;
            }
            status = RunStatus.FAILED;
            incrementAttempts();
            RandomRunMod.LOGGER.info("Забег провален!");
        }
    }
    
    public void cancelRun() {
        status = RunStatus.INACTIVE;
        targetItem = null;
        targetAdvancementId = null;
        runWorldName = null;
        startTime = 0;
        elapsedTime = 0;
        timeLimit = 0;
    }
    
    private boolean isCorrectWorld() {
        if (runWorldName == null) return true;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getServer() != null) {
                String currentWorld = client.getServer().getSaveProperties().getLevelName();
                return runWorldName.equals(currentWorld);
            }
        } catch (Exception e) {
        }
        return true;
    }
    
    public boolean checkItemPickup(Item item) {
        if (status == RunStatus.RUNNING && targetType == TargetType.ITEM && targetItem != null) {
            if (!isCorrectWorld()) {
                RandomRunMod.LOGGER.warn("Игнорирование подбора предмета в неправильном мире. Ожидается: " + runWorldName);
                return false;
            }
            return item == targetItem;
        }
        return false;
    }
    
    public boolean checkAdvancementCompleted(net.minecraft.util.Identifier advancementId) {
        if (status == RunStatus.RUNNING && targetType == TargetType.ADVANCEMENT && targetAdvancementId != null) {
            if (!isCorrectWorld()) {
                RandomRunMod.LOGGER.warn("Игнорирование достижения в неправильном мире. Ожидается: " + runWorldName);
                return false;
            }
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
        // FIXED: Always use local time for better sync (especially in shared worlds)
        // Server time causes ~2 second desync in shared worlds due to network latency
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
    
    private String getCurrentRunSeed() {
        // 1. Try to get explicitly stored seed from WorldCreator (most reliable for speedruns)
        String lastSeed = com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed();
        if (lastSeed != null && !lastSeed.isEmpty()) {
            return lastSeed;
        }

        // 2. Try to get from BattleRoom if in battle
        try {
            com.randomrun.battle.BattleRoom room = com.randomrun.battle.BattleManager.getInstance().getCurrentRoom();
            if (room != null && room.getSeed() != null) {
                return room.getSeed();
            }
        } catch (Exception e) {}

        // 3. Try to get from server if available (Integrated Server)
        try {
             MinecraftClient client = MinecraftClient.getInstance();
             if (client.getServer() != null) {
                 return String.valueOf(client.getServer().getOverworld().getSeed());
             }
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("Failed to get seed from server", e);
        }
        
        // 4. Try to get from client world (if possible, though client usually doesn't know seed in MP)
        // In singleplayer, client.getServer() covers it. In MP, we might not know unless sent by packet.
        
        return "unknown";
    }

    private void saveResult() {
        // Если это челлендж на время (timeLimit > 0), не сохраняем в общие рекорды
        if (timeLimit > 0) {
            RandomRunMod.LOGGER.info("Результат не сохранен в рекорды (Испытание на время)");
            return;
        }

        String itemId;
        
        if (targetType == TargetType.ITEM) {
            if (targetItem == null) return;
            itemId = IdCompressor.compress(Registries.ITEM.getId(targetItem).toString());
        } else {
            if (targetAdvancementId == null) return;
            itemId = targetAdvancementId.toString();
        }
        
        // Получаем сид мира
        String seed = getCurrentRunSeed();
        if (seed == null || seed.isEmpty() || "unknown".equals(seed)) {
            // Try to get seed from WorldCreator if local logic failed
            seed = com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed();
        }
        
        // Определяем статус результата
        String resultStatus = "VICTORY";
        
        // Используем BattleManager для определения, был ли это онлайн забег
        boolean isOnlineRun = com.randomrun.battle.BattleManager.getInstance().isInBattle();
        
        // Определяем режим Хардкор
        boolean isHardcore = true;
        try {
            if (MinecraftClient.getInstance().world != null) {
                isHardcore = MinecraftClient.getInstance().world.getLevelProperties().isHardcore();
            } else {
                 isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
            }
        } catch (Exception e) {}

        // 1. Update purely local results (Always update local history regardless of mode)
        synchronized (localResults) {
            RunResult local = localResults.get(itemId);
            if (local == null) {
                local = new RunResult(itemId, elapsedTime);
                local.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, elapsedTime, resultStatus, isOnlineRun, isHardcore
                ));
                localResults.put(itemId, local);
            } else {
                local.attempts++;
                local.lastAttempt = System.currentTimeMillis();
                local.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, elapsedTime, resultStatus, isOnlineRun, isHardcore
                ));
                
                if (elapsedTime < local.bestTime) {
                    local.bestTime = elapsedTime;
                    local.hash = RunResult.generateHash(itemId, elapsedTime);
                }
            }
        }
        saveLocalResults();
        
        // 2. Update view results (merged)
        synchronized (results) {
            RunResult existing = results.get(itemId);
            if (existing == null) {
                RunResult newRes = new RunResult(itemId, elapsedTime);
                newRes.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, elapsedTime, resultStatus, isOnlineRun, isHardcore
                ));
                results.put(itemId, newRes);
            } else {
                existing.attempts++; 
                existing.lastAttempt = System.currentTimeMillis();
                existing.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, elapsedTime, resultStatus, isOnlineRun, isHardcore
                ));
                
                if (elapsedTime < existing.bestTime) {
                    existing.bestTime = elapsedTime;
                    existing.hash = RunResult.generateHash(itemId, elapsedTime);
                }
            }
        }
        
        // Note: Leaderboard submission is handled by VictoryHandler to prevent duplicates.
    }
    
    private void incrementAttempts() {
        // Just update local cache, no DB save for attempts on failure
        String itemId;
        if (targetType == TargetType.ITEM) {
            if (targetItem == null) return;
            itemId = IdCompressor.compress(Registries.ITEM.getId(targetItem).toString());
        } else {
            if (targetAdvancementId == null) return;
            itemId = targetAdvancementId.toString();
        }
        
        // Получаем сид мира
        String seed = getCurrentRunSeed();
        
        // Определяем статус результата
        String resultStatus = "FAILED";
        if (status == RunStatus.FAILED) resultStatus = "DEATH"; // Предполагаем смерть, если FAILED
        // TODO: Передавать точную причину провала (Сдался, Умер, Вышел)
        
        // Используем BattleManager для определения, был ли это онлайн забег
        boolean isOnlineRun = com.randomrun.battle.BattleManager.getInstance().isInBattle();
        
        // Определяем режим Хардкор
        boolean isHardcore = true;
        try {
            if (MinecraftClient.getInstance().world != null) {
                isHardcore = MinecraftClient.getInstance().world.getLevelProperties().isHardcore();
            } else {
                 isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
            }
        } catch (Exception e) {}
        
        long duration;
        if (elapsedTime > 0) {
            duration = elapsedTime;
        } else if (startTime > 0) {
            duration = System.currentTimeMillis() - startTime;
        } else {
            duration = 0; // Забег не начался (был заморожен)
        }

        // Всегда сохраняем в локальную историю
        synchronized (localResults) {
            RunResult local = localResults.get(itemId);
            if (local == null) {
                // Если записи нет, создаем новую с 0 временем (так как это только попытка)
                local = new RunResult();
                local.itemId = itemId;
                local.attempts = 1;
                local.lastAttempt = System.currentTimeMillis();
                local.bestTime = Long.MAX_VALUE; // Нет успешного времени
                local.hash = "";
                local.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, duration, resultStatus, isOnlineRun, isHardcore
                ));
                localResults.put(itemId, local);
            } else {
                local.attempts++;
                local.lastAttempt = System.currentTimeMillis();
                local.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, duration, resultStatus, isOnlineRun, isHardcore
                ));
            }
        }
        // Важно сохранить, чтобы попытки не терялись
        saveLocalResults();
        
        synchronized (results) {
            RunResult existing = results.get(itemId);
            if (existing == null) {
                 // Если записи нет в общем списке, создаем
                existing = new RunResult();
                existing.itemId = itemId;
                existing.attempts = 1;
                existing.lastAttempt = System.currentTimeMillis();
                existing.bestTime = Long.MAX_VALUE;
                existing.hash = "";
                existing.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, duration, resultStatus, isOnlineRun, isHardcore
                ));
                results.put(itemId, existing);
            } else {
                existing.attempts++;
                existing.lastAttempt = System.currentTimeMillis();
                existing.attemptsHistory.add(new RunResult.AttemptRecord(
                    System.currentTimeMillis(), seed, duration, resultStatus, isOnlineRun, isHardcore
                ));
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

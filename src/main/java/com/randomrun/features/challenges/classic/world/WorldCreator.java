package com.randomrun.features.challenges.classic.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

public class WorldCreator {
    private static final WorldCreator INSTANCE = new WorldCreator();
    private static final String LOG_PREFIX = "[World] ";
    private static final long CREATION_TIMEOUT_MS = 60000;
    
    private volatile Item pendingTargetItem = null;
    private volatile String pendingSeed = null;
    private volatile boolean pendingRandomDrop = false;
    private volatile boolean pendingUpgrader = false;
    private volatile boolean lastCreatedUpgrader = false;
    private volatile boolean lastCreatedRandomDrop = false;
    private volatile String lastCreatedSeed = null;
    private volatile boolean isManualSeed = false;
    private volatile boolean isSpeedrunLoading = false;
    private volatile boolean creationTriggered = false;
    private volatile long creationStartTime = 0;
    private volatile boolean creationLocked = false;
    
    private final List<WorldHistoryEntry> worldHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 10;
    
    private WorldCreator() {}
    
    public static WorldCreator getInstance() {
        return INSTANCE;
    }
    
    public void createWorld(WorldCreationRequest request) {
        if (request == null) {
            logError("WorldCreationRequest не может быть null");
            return;
        }
        
        if (!validateRequest(request)) {
            return;
        }
        
        if (creationLocked) {
            long elapsed = System.currentTimeMillis() - creationStartTime;
            if (elapsed > 5000) {
                logWarn("Принудительная разблокировка (прошло " + elapsed + "ms)");
                unlockCreation();
            } else {
                logWarn("Создание мира уже в процессе");
                return;
            }
        }
        
        Minecraft client = Minecraft.getInstance();
        
        com.randomrun.ui.screen.main.ResultsScreen.clearCache();
        
        creationLocked = true;
        creationStartTime = System.currentTimeMillis();
        
        pendingTargetItem = request.getTargetItem();
        pendingSeed = request.getSeed();
        pendingRandomDrop = request.isRandomDrop();
        pendingUpgrader = request.isUpgrader();
        lastCreatedUpgrader = request.isUpgrader();
        lastCreatedRandomDrop = request.isRandomDrop();
        isSpeedrunLoading = true;
        creationTriggered = false;
        
        logCreationStart(request);
        
        try {
            client.gui.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(null));
        } catch (Exception e) {
            logError("Ошибка открытия SelectWorldScreen");
            unlockCreation();
        }
    }
    
    @Deprecated
    public static void createSpeedrunWorld(Item targetItem) {
        getInstance().createWorld(new WorldCreationRequest.Builder()
            .targetItem(targetItem)
            .mode(getWorldMode())
            .build());
    }
    
    @Deprecated
    public static void createSpeedrunWorld(Item targetItem, String seed) {
        getInstance().createWorld(new WorldCreationRequest.Builder()
            .targetItem(targetItem)
            .seed(seed)
            .mode(getWorldMode())
            .build());
    }
    
    private boolean validateRequest(WorldCreationRequest request) {
        return true;
    }
    
    public void checkTimeout() {
        if (isSpeedrunLoading && creationStartTime > 0) {
            long elapsed = System.currentTimeMillis() - creationStartTime;
            if (elapsed > CREATION_TIMEOUT_MS) {
                logWarn("Таймаут создания мира (" + (CREATION_TIMEOUT_MS/1000) + "s)");
                cancelCreation();
            }
        }
    }
    
    public void cancelCreation() {
        logInfo("Создание мира отменено");
        clearPendingData();
        unlockCreation();
    }
    
    private void unlockCreation() {
        creationLocked = false;
        creationStartTime = 0;
    }
    
    public void addToHistory(Item item, String seed, WorldMode mode) {
        WorldHistoryEntry entry = new WorldHistoryEntry(item, seed, mode, System.currentTimeMillis());
        worldHistory.add(0, entry);
        
        if (worldHistory.size() > MAX_HISTORY_SIZE) {
            worldHistory.remove(worldHistory.size() - 1);
        }
        
        logInfo("Добавлено в историю: " + entry.getItemName());
    }
    
    public List<WorldHistoryEntry> getWorldHistory() {
        return new ArrayList<>(worldHistory);
    }
    
    public static Item getPendingTargetItem() {
        return getInstance().pendingTargetItem;
    }
    
    public static String getPendingSeed() {
        return getInstance().pendingSeed;
    }
    
    public static boolean isPendingRandomDrop() {
        return getInstance().pendingRandomDrop;
    }

    public static boolean isPendingUpgrader() { return getInstance().pendingUpgrader; }
    public static boolean isLastCreatedUpgrader() { return getInstance().lastCreatedUpgrader; }
    public static boolean isLastCreatedRandomDrop() { return getInstance().lastCreatedRandomDrop; }
    
    public static String getLastCreatedSeed() {
        return getInstance().lastCreatedSeed;
    }
    
    public static boolean isManualSeed() {
        return getInstance().isManualSeed;
    }
    
    public static boolean isCreationTriggered() {
        return getInstance().creationTriggered;
    }
    
    public static boolean isSpeedrunLoading() {
        return getInstance().isSpeedrunLoading;
    }
    
    public static boolean hasPendingRun() {
        return getInstance().isSpeedrunLoading;
    }
    
    public static boolean isCreationLocked() {
        return getInstance().creationLocked;
    }
    
    public static void setLastCreatedSeed(String seed) {
        setLastCreatedSeed(seed, false);
    }

    public static void setLastCreatedSeed(String seed, boolean manual) {
        WorldCreator instance = getInstance();
        instance.lastCreatedSeed = seed != null ? seed.trim() : null;
        instance.isManualSeed = manual;
        instance.logInfo("Сид сохранен" + (manual ? " (ручной)" : " (авто)"));
        instance.unlockCreation();
    }
    
    public static void setCreationTriggered(boolean triggered) {
        getInstance().creationTriggered = triggered;
    }
    
    public static void clearPendingData() {
        WorldCreator instance = getInstance();
        instance.pendingTargetItem = null;
        instance.pendingSeed = null;
        instance.pendingRandomDrop = false;
        instance.pendingUpgrader = false;
        instance.isSpeedrunLoading = false;
        instance.creationTriggered = false;
        instance.isManualSeed = false;
        instance.unlockCreation();
        instance.logInfo("Данные очищены");
    }
    
    @Deprecated
    public static String generateWorldName(Item item) {
        return WorldNameGenerator.generate(item, getWorldMode());
    }
    
    private static WorldMode getWorldMode() {
        return WorldMode.STANDARD;
    }
    
    private void logCreationStart(WorldCreationRequest request) {
        String targetName = request.getTargetItem() != null 
            ? net.minecraft.network.chat.Component.translatable(request.getTargetItem().getDescriptionId()).getString() 
            : "MOB";
        StringBuilder log = new StringBuilder(LOG_PREFIX).append(targetName);
        
        if (request.getSeed() != null) {
            log.append(" | seed=").append(request.getSeed());
        }
        
        log.append(" | ").append(request.getMode().getDisplayName());
        
        com.randomrun.infrastructure.util.CompactLogger.core(log.toString());
    }
    
    private void logInfo(String message) {
        com.randomrun.infrastructure.util.CompactLogger.core(LOG_PREFIX + message);
    }
    
    private void logWarn(String message) {
        com.randomrun.infrastructure.util.CompactLogger.warn(LOG_PREFIX + message);
    }
    
    private void logError(String message) {
        com.randomrun.infrastructure.util.CompactLogger.error(LOG_PREFIX + message);
    }
    
    public static class WorldHistoryEntry {
        private final String itemName;
        private final String seed;
        private final WorldMode mode;
        private final long timestamp;
        
        public WorldHistoryEntry(Item item, String seed, WorldMode mode, long timestamp) {
            this.itemName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
            this.seed = seed;
            this.mode = mode;
            this.timestamp = timestamp;
        }
        
        public String getItemName() {
            return itemName;
        }
        
        public String getSeed() {
            return seed;
        }
        
        public WorldMode getMode() {
            return mode;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}

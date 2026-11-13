package com.randomrun.features.speedrun;

import com.randomrun.core.run.RunSession;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.speedrun.WorldCreationRequest.WorldMode;
import com.randomrun.infrastructure.RandomRunMod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
    private volatile boolean serverSeed = false;
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

        var battle = com.randomrun.features.battle.manager.BattleManager.getInstance();
        var mode = battle.isInBattle() && battle.isHost()
            ? com.randomrun.ui.overlay.LoadingCurtain.Mode.HOST : com.randomrun.ui.overlay.LoadingCurtain.Mode.WORLD;
        serverSeed = false;
        boolean verify = !battle.isInBattle() && (pendingSeed == null || pendingSeed.isBlank()) && !request.isUpgrader()
            && !com.randomrun.features.voice.control.VoiceController.voiceRunPending();
        java.util.concurrent.CompletableFuture<com.randomrun.network.RunVerifier.Ticket> ticket = verify
            ? com.randomrun.network.RunVerifier.begin(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pendingTargetItem).getPath())
            : java.util.concurrent.CompletableFuture.completedFuture(null);
        com.randomrun.ui.overlay.LoadingCurtain.close(mode, () -> ticket.completeOnTimeout(null, 4, java.util.concurrent.TimeUnit.SECONDS).thenAccept(issued -> Minecraft.getInstance().execute(() -> {
            if (!isSpeedrunLoading) return;
            com.randomrun.network.RunVerifier.prepare(issued);
            if (issued != null) {
                pendingSeed = issued.seed();
                serverSeed = true;
            }
            try {
                Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(null));
            } catch (Exception e) {
                logError("Ошибка открытия SelectWorldScreen");
                unlockCreation();
            }
        })));
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
    
    public static boolean isServerSeed() {
        return getInstance().serverSeed;
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
        instance.unlockCreation();
    }
    
    public static void setCreationTriggered(boolean triggered) {
        getInstance().creationTriggered = triggered;
    }
    
    /** File the mod leaves in every world it creates itself; only such worlds may ever be deleted automatically. */
    public static final String RUN_WORLD_MARKER = "randomrun-run-world.marker";

    /** Marks the world as created by the mod (called right after the mod's own world creation finished loading). */
    public static void markRunWorld(java.nio.file.Path worldRoot) {
        try {
            java.nio.file.Files.writeString(worldRoot.resolve(RUN_WORLD_MARKER),
                "Created by RandomRun for a speedrun. Worlds with this file may be deleted automatically if world auto-delete is on.\n");
        } catch (Exception e) {
            com.randomrun.infrastructure.RandomRunMod.LOGGER.warn("[World] Could not mark run world: {}", e.getMessage());
        }
    }

    /** True only for worlds the mod created itself: the marker file must be there, the folder name alone is not enough. */
    public static boolean isRunWorld(java.nio.file.Path worldRoot) {
        if (worldRoot == null || worldRoot.getParent() == null || worldRoot.getParent().getFileName() == null) return false;
        // a world folder directly inside "saves", never anything else
        return worldRoot.getParent().getFileName().toString().equals("saves")
            && java.nio.file.Files.isRegularFile(worldRoot.resolve(RUN_WORLD_MARKER));
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
        instance.serverSeed = false;
        instance.unlockCreation();
    }
    
    @Deprecated
    public static String generateWorldName(Item item) {
        return WorldNameGenerator.generate(item, getWorldMode());
    }
    
    private static WorldMode getWorldMode() {
        return WorldMode.STANDARD;
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

    public static class WorldNameGenerator {
        private static final String PREFIX = "RandomRun: ";

        public static String generate(Item item, WorldMode mode) {
            if (item == null) {
                throw new IllegalArgumentException("Item не может быть null");
            }
            String itemName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
            return PREFIX + itemName + " (" + mode.getDisplayName() + ")";
        }

        public static String generate(Item item, WorldMode mode, long timeLimitMs) {
            String baseName = generate(item, mode);
            if (timeLimitMs > 0) {
                int seconds = (int) (timeLimitMs / 1000);
                return baseName.replace(")", " - " + seconds + "s)");
            }
            return baseName;
        }
    }

    /** Restarts the current classic run in a fresh world with the same target ("Сменить сид"). */
    public static final class SeedChanger {
        public static final long COOLDOWN_MS = 5000;

        private SeedChanger() {}

        public static boolean available() {
            RunSessionManager sessions = RunSessionManager.getInstance();
            RunSession session = sessions.getCurrentSession();
            return !BattleManager.getInstance().isInBattle() && sessions.hasActiveRun() && session != null
                && session.getTargetItem() != null && Minecraft.getInstance().getSingleplayerServer() != null;
        }

        /** Milliseconds left before the seed can be changed again, 0 when it can. */
        public static long cooldownLeft() {
            RunSession session = RunSessionManager.getInstance().getCurrentSession();
            return session == null ? 0 : Math.max(0, COOLDOWN_MS - session.getTimeSinceWorldLoad());
        }

        public static boolean changeSeed() {
            if (!available() || cooldownLeft() > 0) return false;
            Minecraft client = Minecraft.getInstance();
            RunSessionManager sessions = RunSessionManager.getInstance();
            RunSession current = sessions.getCurrentSession();
            WorldCreationRequest request = new WorldCreationRequest.Builder()
                .targetItem(current.getTargetItem()).randomDrop(current.isRandomDrop()).upgrader(current.isUpgrader())
                .mode(WorldMode.STANDARD).build();
            sessions.cancelRun();
            RandomRunMod.getInstance().getRunDataManager().cancelRun();
            WorldCreator.clearPendingData();
            client.disconnectFromWorld(Component.translatable("menu.returnToMenu"));
            client.execute(() -> WorldCreator.getInstance().createWorld(request));
            return true;
        }
    }
}

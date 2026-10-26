package com.randomrun.core.session;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import java.util.Objects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class RunSession {
    private static final String LOG_TAG = "[Session]";
    
    private final String targetId;
    private final TargetType targetType;
    private final Item targetItem;
    private final EntityType<?> targetMob;
    private final String mobVariant;
    
    private long startTime;
    private long elapsedTime;
    private long worldLoadTime;
    
    private volatile RunStatus status;
    private volatile boolean paused;
    private long pauseStartTime;
    private long totalPausedTime;
    
    private final String worldName;
    private String worldSeed;
    
    private final boolean isBattle;
    private final String battleRoomId;
    private final String customSeed;
    
    private final String modVersion;
    private final boolean isHardcore;
    private final boolean isUpgrader;
    private boolean isRandomDrop;
    
    private WorldIntegrityChecker integrityChecker;
    
    public RunSession(Item targetItem, boolean isBattle, 
                     String battleRoomId, String customSeed, String worldName) {
        this(targetItem, isBattle, battleRoomId, customSeed, worldName,
            !isBattle && RandomRunMod.getInstance().getConfig().isUpgraderEnabled());
    }

    public RunSession(Item targetItem, boolean isBattle,
                      String battleRoomId, String customSeed, String worldName, boolean upgrader) {
        Objects.requireNonNull(targetItem, "targetItem не может быть null");
        Objects.requireNonNull(worldName, "worldName не может быть null");
        
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("worldName не может быть пустым");
        }
        
        this.targetItem = targetItem;
        this.targetMob = null;
        this.mobVariant = null;
        this.targetId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetItem).toString();
        this.targetType = TargetType.ITEM;
        
        this.startTime = 0;
        this.elapsedTime = 0;
        this.worldLoadTime = 0;
        
        this.status = RunStatus.FROZEN;
        this.paused = false;
        this.pauseStartTime = 0;
        this.totalPausedTime = 0;
        
        this.worldName = worldName;
        this.worldSeed = null;
        
        this.isBattle = isBattle;
        this.battleRoomId = battleRoomId;
        this.customSeed = customSeed;
        
        this.modVersion = RandomRunMod.MOD_VERSION;
        this.isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
        this.isUpgrader = upgrader;
        this.isRandomDrop = false;
    }
    
    public RunSession(EntityType<?> targetMob, String mobVariant, boolean isBattle,
                     String battleRoomId, String customSeed, String worldName) {
        this(targetMob, mobVariant, isBattle, battleRoomId, customSeed, worldName,
            !isBattle && RandomRunMod.getInstance().getConfig().isUpgraderEnabled());
    }

    public RunSession(EntityType<?> targetMob, String mobVariant, boolean isBattle,
                      String battleRoomId, String customSeed, String worldName, boolean upgrader) {
        Objects.requireNonNull(targetMob, "targetMob не может быть null");
        Objects.requireNonNull(worldName, "worldName не может быть null");
        
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("worldName не может быть пустым");
        }
        
        this.targetItem = null;
        this.targetMob = targetMob;
        this.mobVariant = mobVariant;
        
        String baseId = net.minecraft.world.entity.EntityType.getKey(targetMob).toString();
        this.targetId = mobVariant != null ? baseId + ":" + mobVariant : baseId;
        this.targetType = TargetType.MOB;
        
        this.startTime = 0;
        this.elapsedTime = 0;
        this.worldLoadTime = 0;
        
        this.status = RunStatus.FROZEN;
        this.paused = false;
        this.pauseStartTime = 0;
        this.totalPausedTime = 0;
        
        this.worldName = worldName;
        this.worldSeed = null;
        
        this.isBattle = isBattle;
        this.battleRoomId = battleRoomId;
        this.customSeed = customSeed;
        
        this.modVersion = RandomRunMod.MOD_VERSION;
        this.isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
        this.isUpgrader = upgrader;
        this.isRandomDrop = false;
    }
    
    public void start() {
        if (status == RunStatus.FROZEN) {
            this.startTime = System.currentTimeMillis();
            this.worldLoadTime = System.currentTimeMillis();
            this.status = RunStatus.RUNNING;
            
            try {
                net.minecraft.server.MinecraftServer server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
                if (server != null) {
                    integrityChecker = new WorldIntegrityChecker(server);
                    integrityChecker.captureSnapshot();
                }
            } catch (Exception e) {
                CompactLogger.error("{} Ошибка создания снимка: {}", LOG_TAG, e.getMessage());
            }
        } else {
            CompactLogger.warn("{} Попытка старта в неправильном статусе: {} (ожидался FROZEN)", LOG_TAG, status);
        }
    }
    
    public void pause() {
        if (status == RunStatus.RUNNING && !paused && !isBattle) {
            updateElapsedTime();
            paused = true;
            pauseStartTime = System.currentTimeMillis();
        }
    }
    
    public void resume() {
        if (paused) {
            paused = false;
            totalPausedTime += System.currentTimeMillis() - pauseStartTime;
            pauseStartTime = 0;
        }
    }
    
    public void updateElapsedTime() {
        if (status == RunStatus.RUNNING && !paused) {
            long currentTime = System.currentTimeMillis();
            elapsedTime = currentTime - startTime - totalPausedTime;
        }
    }
    
    public long getElapsedTime() {
        updateElapsedTime();
        return elapsedTime;
    }
    
    public long getRtaTime() {
        if (status == RunStatus.RUNNING && startTime > 0) {
            return System.currentTimeMillis() - startTime;
        }
        return elapsedTime + totalPausedTime;
    }
    
    public String getFormattedTime() {
        long ms = getElapsedTime();
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds % 60);
        } else {
            return String.format("%d.%ds", seconds, (ms % 1000) / 100);
        }
    }
    
    public void complete() {
        this.status = RunStatus.COMPLETED;
        elapsedTime = getElapsedTime();
    }
    
    public boolean validateWorldIntegrity() {
        if (integrityChecker == null) {
            CompactLogger.warn("{} Проверка целостности недоступна (нет снимка)", LOG_TAG);
            return true;
        }
        
        boolean isValid = integrityChecker.validateIntegrity();
        if (!isValid) {
            CompactLogger.warn("{} Обнаружено нарушение целостности мира!", LOG_TAG);
        }
        return isValid;
    }
    
    public void fail() {
        this.status = RunStatus.FAILED;
        elapsedTime = getElapsedTime();
    }
    
    public void setWorldSeed(String seed) {
        this.worldSeed = seed;
    }
    
    public boolean isActive() {
        return status.isActive();
    }
    
    public boolean isCompleted() {
        return status == RunStatus.COMPLETED;
    }
    
    public boolean isFinished() {
        return status.isFinished();
    }
    
    public SessionSnapshot snapshot() {
        return new SessionSnapshot(this);
    }
    
    @Override
    public String toString() {
        return String.format("RunSession{target=%s, status=%s, elapsed=%dms}", 
            targetId, status, elapsedTime);
    }
    
    public String getTargetId() { return targetId; }
    public TargetType getTargetType() { return targetType; }
    public Item getTargetItem() { return targetItem; }
    public EntityType<?> getTargetMob() { return targetMob; }
    public String getMobVariant() { return mobVariant; }
    public RunStatus getStatus() { return status; }
    public boolean isPaused() { return paused; }
    public String getWorldName() { return worldName; }
    public String getWorldSeed() { return worldSeed; }
    public boolean isBattle() { return isBattle; }
    public String getBattleRoomId() { return battleRoomId; }
    public String getCustomSeed() { return customSeed; }
    public String getModVersion() { return modVersion; }
    public boolean isHardcore() { return isHardcore; }
    public boolean isRandomDrop() { return isRandomDrop; }
    public boolean isUpgrader() { return isUpgrader; }
    public long getWorldLoadTime() { return worldLoadTime; }
    
    public void setRandomDrop(boolean isRandomDrop) {
        this.isRandomDrop = isRandomDrop;
    }
    
    public long getTimeSinceWorldLoad() {
        if (worldLoadTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - worldLoadTime;
    }
    
    public static class SessionSnapshot {
        public final String targetId;
        public final RunStatus status;
        public final long elapsedTime;
        public final boolean paused;
        public final String worldSeed;
        public final boolean isBattle;
        public final boolean isHardcore;
        public final boolean isUpgrader;
        public final long timestamp;
        
        private SessionSnapshot(RunSession session) {
            this.targetId = session.targetId;
            this.status = session.status;
            this.elapsedTime = session.elapsedTime;
            this.paused = session.paused;
            this.worldSeed = session.worldSeed;
            this.isBattle = session.isBattle;
            this.isHardcore = session.isHardcore;
            this.isUpgrader = session.isUpgrader;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("SessionSnapshot{target=%s, status=%s, elapsed=%dms, battle=%s}", 
                targetId, status, elapsedTime, isBattle);
        }
    }
}

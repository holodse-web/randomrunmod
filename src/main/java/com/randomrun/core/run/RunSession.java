package com.randomrun.core.run;

import com.randomrun.core.run.Run.TargetType;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public class RunSession {
    private static final String LOG_TAG = "[Session]";
    
    private final String targetId;
    private final TargetType targetType;
    private final Item targetItem;
    
    private long startTime;
    private long finalRta;
    private long elapsedTime;
    private long worldLoadTime;
    
    private volatile RunStatus status;
    
    private final String worldName;
    private String worldSeed;
    
    private final boolean isBattle;
    private final String battleRoomId;
    private final String customSeed;
    
    private final String modVersion;
    private final boolean isHardcore;
    private final boolean isUpgrader;
    private boolean isRandomDrop;
    
    public RunSession(Item targetItem, boolean isBattle, 
                     String battleRoomId, String customSeed, String worldName) {
        this(targetItem, isBattle, battleRoomId, customSeed, worldName,
            !isBattle && RandomRunMod.getInstance().getConfig().isUpgraderEnabled());
    }

    public RunSession(Item targetItem, boolean isBattle,
                      String battleRoomId, String customSeed, String worldName, boolean upgrader) {
        Objects.requireNonNull(targetItem, "targetItem must not be null");
        Objects.requireNonNull(worldName, "worldName must not be null");
        
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("World name is required");
        }
        
        this.targetItem = targetItem;
        this.targetId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetItem).toString();
        this.targetType = TargetType.ITEM;
        
        this.startTime = 0;
        this.elapsedTime = 0;
        this.worldLoadTime = System.currentTimeMillis();
        
        this.status = RunStatus.FROZEN;
        
        this.worldName = worldName;
        this.worldSeed = null;
        
        this.isBattle = isBattle;
        this.battleRoomId = battleRoomId;
        this.customSeed = customSeed;
        
        this.modVersion = RandomRunMod.MOD_VERSION;
        var client = net.minecraft.client.Minecraft.getInstance();
        this.isHardcore = client != null && client.level != null
            ? client.level.getLevelData().isHardcore()
            : !isBattle && RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
        this.isUpgrader = upgrader;
        this.isRandomDrop = false;
    }
    
    public synchronized void start() {
        if (status == RunStatus.FROZEN) {
            this.startTime = System.nanoTime();
            this.status = RunStatus.RUNNING;
        } else {
            CompactLogger.warn("{} Start requires FROZEN, got {}", LOG_TAG, status);
        }
    }
    
    public synchronized void updateElapsedTime() {
        if (status == RunStatus.RUNNING) elapsedTime = elapsedMillis(System.nanoTime());
    }

    public synchronized long getElapsedTime() {
        updateElapsedTime();
        return elapsedTime;
    }

    public synchronized long getRtaTime() {
        return status == RunStatus.RUNNING ? millis(System.nanoTime() - startTime) : finalRta;
    }

    private long elapsedMillis(long now) {
        return millis(now - startTime);
    }

    private static long millis(long nanos) {
        return Math.max(0, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanos));
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
    
    public boolean complete() {
        return finish(RunStatus.COMPLETED);
    }
    
    public boolean fail() {
        return finish(RunStatus.FAILED);
    }

    private synchronized boolean finish(RunStatus result) {
        if (status.isFinished()) return false;
        if (status == RunStatus.RUNNING) {
            long now = System.nanoTime();
            elapsedTime = elapsedMillis(now);
            finalRta = millis(now - startTime);
        }
        status = result;
        return true;
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


    public RunStatus getStatus() { return status; }
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
        public final String worldSeed;
        public final boolean isBattle;
        public final boolean isHardcore;
        public final boolean isUpgrader;
        public final long timestamp;
        
        private SessionSnapshot(RunSession session) {
            this.targetId = session.targetId;
            this.status = session.status;
            this.elapsedTime = session.elapsedTime;
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

    public enum RunStatus {
        INACTIVE,
        FROZEN,
        RUNNING,
        COMPLETED,
        FAILED;

        public boolean isActive() {
            return this == RUNNING || this == FROZEN;
        }

        public boolean isFinished() {
            return this == COMPLETED || this == FAILED;
        }

        public boolean canTransitionTo(RunStatus newStatus) {
            return switch (this) {
                case INACTIVE -> newStatus == FROZEN || newStatus == RUNNING;
                case FROZEN -> newStatus == RUNNING || newStatus == FAILED || newStatus == INACTIVE;
                case RUNNING -> newStatus == COMPLETED || newStatus == FAILED || newStatus == FROZEN || newStatus == INACTIVE;
                case COMPLETED, FAILED -> newStatus == INACTIVE;
            };
        }

        public String getTranslationKey() {
            return "randomrun.status." + name().toLowerCase();
        }

        public Component getDisplayName() {
            return Component.translatable(getTranslationKey());
        }
    }
}

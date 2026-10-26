package com.randomrun.core.session;

import com.randomrun.infrastructure.util.CompactLogger;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class RunSessionManager {
    private static final String LOG_TAG = "[SessionMgr]";
    
    private volatile RunSession currentSession;
    
    private RunSessionManager() {}
    
    private static class Holder {
        private static final RunSessionManager INSTANCE = new RunSessionManager();
    }
    
    public static RunSessionManager getInstance() {
        return Holder.INSTANCE;
    }
    
    public synchronized boolean canStartRun() {
        return currentSession == null || !currentSession.isActive();
    }
    
    public synchronized void startRun(Item targetItem, boolean isBattle, 
                        String battleRoomId, String customSeed, String worldName) {
        startRun(targetItem, isBattle, battleRoomId, customSeed, worldName,
            !isBattle && com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isUpgraderEnabled());
    }

    public synchronized void startRun(Item targetItem, boolean isBattle,
                        String battleRoomId, String customSeed, String worldName, boolean upgrader) {
        if (!canStartRun()) {
            CompactLogger.warn("{} Невозможно запустить", LOG_TAG);
            return;
        }
        
        try {
            currentSession = new RunSession(targetItem, isBattle, battleRoomId, customSeed, worldName, upgrader);
        } catch (Exception e) {
            CompactLogger.error("{} Ошибка создания: {}", LOG_TAG, e.getMessage());
        }
    }
    
    public synchronized void startMobRun(EntityType<?> targetMob, String mobVariant, 
                           boolean isBattle, String battleRoomId, String customSeed, String worldName) {
        startMobRun(targetMob, mobVariant, isBattle, battleRoomId, customSeed, worldName,
            !isBattle && com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isUpgraderEnabled());
    }

    public synchronized void startMobRun(EntityType<?> targetMob, String mobVariant,
                           boolean isBattle, String battleRoomId, String customSeed, String worldName, boolean upgrader) {
        if (!canStartRun()) {
            CompactLogger.warn("{} Невозможно запустить", LOG_TAG);
            return;
        }
        
        try {
            currentSession = new RunSession(targetMob, mobVariant, isBattle, battleRoomId, customSeed, worldName, upgrader);
        } catch (Exception e) {
            CompactLogger.error("{} Ошибка создания: {}", LOG_TAG, e.getMessage());
        }
    }
    
    public void unfreezeRun() {
        if (currentSession != null) {
            currentSession.start();
        }
    }
    
    public void pauseRun() {
        if (currentSession != null) {
            currentSession.pause();
        }
    }
    
    public void resumeRun() {
        if (currentSession != null) {
            currentSession.resume();
        }
    }
    
    public synchronized void cancelRun() {
        if (currentSession != null) {
            currentSession = null;
        }
    }
    
    public void setWorldSeed(String seed) {
        if (currentSession != null) {
            currentSession.setWorldSeed(seed);
        }
    }
    
    public RunSession getCurrentSession() {
        return currentSession;
    }
    
    public boolean hasActiveRun() {
        return currentSession != null && currentSession.isActive();
    }
    
    public boolean isFrozen() {
        return currentSession != null && currentSession.getStatus() == RunStatus.FROZEN;
    }
    
    public boolean isRunning() {
        return currentSession != null && currentSession.getStatus() == RunStatus.RUNNING;
    }
    
    public boolean isPaused() {
        return currentSession != null && currentSession.isPaused();
    }
    
    public boolean isCompleted() {
        return currentSession != null && currentSession.isCompleted();
    }
    
    public boolean isFinished() {
        return currentSession != null && currentSession.isFinished();
    }
    
    public long getElapsedTime() {
        return currentSession != null ? currentSession.getElapsedTime() : 0;
    }
    
    public long getRtaTime() {
        return currentSession != null ? currentSession.getRtaTime() : 0;
    }
    
    public String getTargetId() {
        return currentSession != null ? currentSession.getTargetId() : null;
    }
    
    public TargetType getTargetType() {
        return currentSession != null ? currentSession.getTargetType() : null;
    }
    
    public Item getTargetItem() {
        return currentSession != null ? currentSession.getTargetItem() : null;
    }
    
    public EntityType<?> getTargetMob() {
        return currentSession != null ? currentSession.getTargetMob() : null;
    }
    
    public String getMobVariant() {
        return currentSession != null ? currentSession.getMobVariant() : null;
    }
    
    public RunStatus getRunStatus() {
        return currentSession != null ? currentSession.getStatus() : RunStatus.INACTIVE;
    }
}

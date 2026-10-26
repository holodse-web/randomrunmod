package com.randomrun.infrastructure.handler;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.audio.RunSoundHelper;
import com.randomrun.core.session.RunSessionManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class StartRunHandler {
    private static final BattleManager BATTLE_MANAGER = BattleManager.getInstance();
    private static final RunSessionManager SESSION_MANAGER = RunSessionManager.getInstance();
    
    private static final long COOLDOWN_MS = 1000;
    private static final Map<UUID, Long> lastStartAttempt = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> startAttempts = new ConcurrentHashMap<>();
    
    public static boolean tryStartRun(Player player) {
        StartRunResult result = tryStartRunDetailed(player);
        
        if (!result.isSuccess() && player != null) {
            if (result == StartRunResult.NOT_FROZEN) {
                return false;
            }
            com.randomrun.infrastructure.util.ClientMessages.send(player, Component.literal("§c" + result.getMessage()), false);
            RunSoundHelper.playErrorSound(player);
        }
        
        return result.isSuccess();
    }
    
    public static StartRunResult tryStartRunDetailed(Player player) {
        if (player == null) {
            return StartRunResult.NO_PLAYER;
        }
        
        if (!SESSION_MANAGER.isFrozen()) {
            return StartRunResult.NOT_FROZEN;
        }
        
        if (!checkCooldown(player)) {
            return StartRunResult.COOLDOWN;
        }
        
        if (!validateWorldState(player)) {
            return StartRunResult.INVALID_STATE;
        }
        
        updateStartAttempt(player);
        
        if (BATTLE_MANAGER.isInBattle()) {
            return startBattleRun(player);
        } else {
            return startSoloRun(player);
        }
    }
    
    private static StartRunResult startBattleRun(Player player) {
        try {
            player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
            player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
            
            BATTLE_MANAGER.sendReady();
            RunSoundHelper.playStartSound(player, true);
            return StartRunResult.SUCCESS;
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[StartRun] Ошибка battle старта", e);
            return StartRunResult.INVALID_STATE;
        }
    }
    
    private static StartRunResult startSoloRun(Player player) {
        try {
            player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
            player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
            
            com.randomrun.infrastructure.data.RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            
            runManager.unfreezeRun();
            SESSION_MANAGER.unfreezeRun();
            
            if (SESSION_MANAGER.isFrozen()) {
                RandomRunMod.LOGGER.error("[StartRun] Разморозка не удалась! Статус все еще FROZEN");
                return StartRunResult.INVALID_STATE;
            }
            
            RunSoundHelper.playStartSound(player, false);
            com.randomrun.infrastructure.util.ClientMessages.send(player, Component.literal("§a§lGO!"), true);
            return StartRunResult.SUCCESS;
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[StartRun] Ошибка solo старта", e);
            return StartRunResult.INVALID_STATE;
        }
    }
    
    private static boolean checkCooldown(Player player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        
        Long lastAttempt = lastStartAttempt.get(playerId);
        if (lastAttempt != null && now - lastAttempt < COOLDOWN_MS) {
            return false;
        }
        
        lastStartAttempt.put(playerId, now);
        cleanupOldAttempts();
        return true;
    }
    
    private static void updateStartAttempt(Player player) {
        UUID playerId = player.getUUID();
        startAttempts.merge(playerId, 1, Integer::sum);
    }
    
    private static boolean validateWorldState(Player player) {
        try {
            if (player.level() == null) {
                return false;
            }
            
            if (player.isDeadOrDying()) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void cleanupOldAttempts() {
        long now = System.currentTimeMillis();
        lastStartAttempt.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        
        if (startAttempts.size() > 100) {
            startAttempts.clear();
        }
    }
    
    public static boolean canStartRun(Player player) {
        if (player == null) return false;
        if (!SESSION_MANAGER.isFrozen()) return false;
        if (!validateWorldState(player)) return false;
        
        UUID playerId = player.getUUID();
        Long lastAttempt = lastStartAttempt.get(playerId);
        if (lastAttempt != null && System.currentTimeMillis() - lastAttempt < COOLDOWN_MS) {
            return false;
        }
        
        return true;
    }
    
    public static String getStartFailureReason(Player player) {
        if (player == null) {
            return StartRunResult.NO_PLAYER.getMessage();
        }
        
        if (!SESSION_MANAGER.isFrozen()) {
            return StartRunResult.NOT_FROZEN.getMessage();
        }
        
        UUID playerId = player.getUUID();
        Long lastAttempt = lastStartAttempt.get(playerId);
        if (lastAttempt != null && System.currentTimeMillis() - lastAttempt < COOLDOWN_MS) {
            return StartRunResult.COOLDOWN.getMessage();
        }
        
        if (!validateWorldState(player)) {
            return StartRunResult.INVALID_STATE.getMessage();
        }
        
        return "Неизвестная причина";
    }
    
    public static Map<UUID, Integer> getStartAttempts() {
        return Map.copyOf(startAttempts);
    }
    
    public static void clearStatistics() {
        lastStartAttempt.clear();
        startAttempts.clear();
    }
}

package com.randomrun.mixin.security;

import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public class GameModeTrackerMixin {
    private static GameType lastGameMode = null;
    private static long lastModeChangeTime = 0;
    
    @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("HEAD"))
    private void onGameModeChange(GameType gameMode, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (!isRunActive(runManager)) {
            lastGameMode = gameMode;
            return;
        }
        
        if (lastGameMode == null) {
            lastGameMode = gameMode;
            return;
        }
        
        if (lastGameMode != gameMode) {
            long now = System.currentTimeMillis();
            
            if (gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) {
                handleCheatModeChange(gameMode, runManager);
            }
            else if (now - lastModeChangeTime < 5000) {
                handleSuspiciousModeChange(gameMode);
            }
            
            lastGameMode = gameMode;
            lastModeChangeTime = now;
        }
    }
    
    private boolean isRunActive(RunDataManager runManager) {
        RunDataManager.RunStatus status = runManager.getStatus();
        return status == RunDataManager.RunStatus.RUNNING || status == RunDataManager.RunStatus.FROZEN;
    }
    
    private void handleCheatModeChange(GameType gameMode, RunDataManager runManager) {
        RandomRunMod.LOGGER.error("[AntiCheat] Смена на читерский режим: {}", gameMode);
        
        PlayerProfile profile = PlayerProfile.get();
        if (profile != null) {
            profile.applyTrustPenalty(PlayerProfile.PENALTY_CREATIVE, 
                "Смена на режим " + gameMode);
            PlayerProfile.save();
        }
        
        runManager.failRun();
        
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            com.randomrun.infrastructure.util.ClientMessages.send(client.player, net.minecraft.network.chat.Component.literal("§c§l[RandomRun] §cСмена режима игры запрещена!"), false);
        }
    }
    
    private void handleSuspiciousModeChange(GameType gameMode) {
        RandomRunMod.LOGGER.warn("[AntiCheat] Подозрительная смена режима: {}", gameMode);
        
        PlayerProfile profile = PlayerProfile.get();
        if (profile != null) {
            profile.addSuspiciousFlag();
            PlayerProfile.save();
        }
    }
    
    private static void reset() {
        lastGameMode = null;
        lastModeChangeTime = 0;
    }
}

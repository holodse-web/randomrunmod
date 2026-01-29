package com.randomrun.main.util;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class StartRunHandler {
    
    public static boolean tryStartRun(PlayerEntity player) {
        BattleManager battleManager = BattleManager.getInstance();
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() != RunDataManager.RunStatus.FROZEN) {
            if (player != null) {
                player.sendMessage(Text.translatable("randomrun.command.already_started"), false);
            }
            return false;
        }
        
        if (battleManager.isInBattle()) {
            battleManager.sendReady();
            if (player != null) {
                player.sendMessage(Text.translatable("randomrun.battle.ready_waiting"), false);
            }
        } else {
            runManager.unfreezeRun();
            
            // Play sound
            if (player != null && RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), volume, 1.0f);
            }
        }
        
        return true;
    }
}

package com.randomrun.util;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import com.randomrun.gui.screen.DefeatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvents;

public class TimeLimitHandler {
    
    private static boolean defeatTriggered = false;
    
    public static void tick() {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() != RunDataManager.RunStatus.RUNNING) {
            defeatTriggered = false;
            return;
        }
        
        // Check time limit
        if (runManager.isTimeLimitExceeded() && !defeatTriggered) {
            defeatTriggered = true;
            handleTimeOut();
        }
    }
    
    private static void handleTimeOut() {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.player == null || client.world == null) return;
        
        Item targetItem = runManager.getTargetItem();
        long elapsedTime = runManager.getCurrentTime();
        
        // Fail the run
        runManager.failRun();
        
        // Play defeat sound
        if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
            float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
            client.player.playSound(SoundEvents.ENTITY_WITHER_DEATH, volume, 1.0f);
        }
        
        // Show defeat screen
        client.execute(() -> {
            client.setScreen(new DefeatScreen(targetItem, elapsedTime, "Время вышло!"));
        });
    }
    
    public static void reset() {
        defeatTriggered = false;
    }
}

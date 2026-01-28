package com.randomrun.util;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;

public class TickHandler {
    
    private static boolean registered = false;
    private static boolean wasInMenu = false;
    
    public static void register() {
        if (!registered) {
            ClientTickEvents.END_CLIENT_TICK.register(TickHandler::onEndTick);
            registered = true;
        }
    }
    
    private static void onEndTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        
        // Check menu state for pause/resume
        checkMenuState(client);
        
        // Update victory handler
        VictoryHandler.tick();
        
        // Update time limit handler
        TimeLimitHandler.tick();
        
        // Check for inventory item pickup (backup check)
        checkInventoryForTarget(client);
    }
    
    private static void checkMenuState(MinecraftClient client) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        boolean isInMenu = client.currentScreen instanceof GameMenuScreen;
        
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            if (isInMenu && !wasInMenu) {
                // Just opened menu
                if (!runManager.isPaused()) {
                    runManager.pauseRun();
                }
            } else if (!isInMenu && wasInMenu) {
                // Just closed menu
                if (runManager.isPaused()) {
                    runManager.resumeRun();
                }
            }
        }
        
        wasInMenu = isInMenu;
    }
    
    private static void checkInventoryForTarget(MinecraftClient client) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() != RunDataManager.RunStatus.RUNNING) return;
        if (runManager.getTargetItem() == null) return;
        
        // Check if target item is in inventory
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            if (client.player.getInventory().getStack(i).getItem() == runManager.getTargetItem()) {
                // Found the item!
                VictoryHandler.handleVictory();
                break;
            }
        }
    }
}

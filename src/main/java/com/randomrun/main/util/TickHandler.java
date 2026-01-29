package com.randomrun.main.util;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.time.util.TimeLimitHandler;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.main.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.InputUtil;

public class TickHandler {
    
    private static boolean registered = false;
    private static boolean wasInMenu = false;
    private static boolean wasKeyPressed = false;
    
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
        
        // Check start key
        checkStartKey(client);
    }
    
    private static void checkStartKey(MinecraftClient client) {
        if (client.currentScreen != null) return;
        
        ModConfig config = RandomRunMod.getInstance().getConfig();
        if (config.getStartMethod() != ModConfig.StartMethod.KEYBIND) return;
        
        int key = config.getStartKey();
        if (key == -1) return;
        
        boolean isPressed = InputUtil.isKeyPressed(client.getWindow().getHandle(), key);
        
        if (isPressed && !wasKeyPressed) {
             StartRunHandler.tryStartRun(client.player);
        }
        
        wasKeyPressed = isPressed;
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

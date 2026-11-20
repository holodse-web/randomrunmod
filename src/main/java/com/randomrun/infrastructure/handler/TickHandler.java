package com.randomrun.infrastructure.handler;

import com.randomrun.core.run.RunSessionManager;
import com.randomrun.infrastructure.util.CompactLogger;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

public class TickHandler {
    private static final RunSessionManager SESSION_MANAGER = RunSessionManager.getInstance();

    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            ClientTickEvents.END_CLIENT_TICK.register(TickHandler::onEndTick);
            registered = true;
        }
    }

    private static void onEndTick(Minecraft client) {
        com.randomrun.features.speedrun.WorldCreator.getInstance().checkTimeout();
        if (client.player == null || client.level == null) return;

        com.randomrun.features.speedrun.AutoStart.tick(client);
        com.randomrun.features.battle.manager.BattleTrace.tick(client);
        VictoryHandler.tick();

        checkInventoryForTarget(client);
        com.randomrun.infrastructure.security.InventoryMonitor.checkInventory();

        com.randomrun.infrastructure.security.AntiCheatService.getInstance().tick();
    }

    private static void checkInventoryForTarget(Minecraft client) {
        if (!SESSION_MANAGER.hasActiveRun() || !SESSION_MANAGER.isRunning()) return;
        if (com.randomrun.features.upgrader.client.UpgraderClient.isAnimating()) return;

        Item targetItem = SESSION_MANAGER.getTargetItem();
        if (targetItem == null) return;

        if (client.player == null || client.player.getInventory() == null) return;

        try {
            var inventory = client.player.getInventory();
            int size = inventory.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (inventory.getItem(i).getItem() == targetItem) {
                    VictoryHandler.handleVictory();
                    break;
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("[TickHandler] Inventory check error: {}", e.getMessage());
        }
    }
}

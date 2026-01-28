package com.randomrun.util;

import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class BattleCleanupHandler {
    
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BattleManager battleManager = BattleManager.getInstance();
            
            if (battleManager.isInBattle()) {
                // Save room ID at disconnect time - this is the room we want to cleanup
                final String roomIdToCleanup = battleManager.getCurrentRoomId();
                
                if (roomIdToCleanup == null) {
                    RandomRunMod.LOGGER.info("Player disconnected but no room ID - skipping cleanup");
                    return;
                }
                
                RandomRunMod.LOGGER.info("Player disconnected during battle (room: " + roomIdToCleanup + ") - will cleanup in 30 seconds if not reconnected");
                
                // Wait 30 seconds before cleanup to allow reconnection
                new Thread(() -> {
                    try {
                        Thread.sleep(30000);
                        
                        // Check if still in the SAME room after timeout
                        String currentRoomId = battleManager.getCurrentRoomId();
                        if (roomIdToCleanup.equals(currentRoomId) && battleManager.isInBattle()) {
                            battleManager.deleteRoom();
                            battleManager.stopBattle();
                            RandomRunMod.LOGGER.info("Battle cleanup completed after timeout for room: " + roomIdToCleanup);
                        } else {
                            RandomRunMod.LOGGER.info("Room changed or battle ended - no cleanup needed (old: " + roomIdToCleanup + ", current: " + currentRoomId + ")");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        RandomRunMod.LOGGER.error("Cleanup thread interrupted", e);
                    }
                }).start();
            }
        });
    }
}

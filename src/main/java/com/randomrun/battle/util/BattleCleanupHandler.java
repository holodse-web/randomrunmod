package com.randomrun.battle.util;

import com.randomrun.main.RandomRunMod;
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
                
                RandomRunMod.LOGGER.info("Player disconnected during battle (room: " + roomIdToCleanup + ") - cleaning up immediately");
                
                // Cleanup immediately
                if (roomIdToCleanup.equals(battleManager.getCurrentRoomId())) {
                    battleManager.handleDisconnect();
                }
            }
        });
    }
}

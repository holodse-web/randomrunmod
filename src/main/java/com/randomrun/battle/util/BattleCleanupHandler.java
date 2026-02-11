package com.randomrun.battle.util;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import com.randomrun.ui.widget.GlobalParticleSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class BattleCleanupHandler {
    
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Всегда очищать частицы при отключении
            GlobalParticleSystem.getInstance().clearParticles();
            GlobalParticleSystem.getInstance().setRedMode(false);
            GlobalParticleSystem.getInstance().setGreenMode(false);
            
            BattleManager battleManager = BattleManager.getInstance();
            
            if (battleManager.isInBattle()) {
                // Сохраняем ID комнаты во время отключения - это комната, которую мы хотим очистить
                final String roomIdToCleanup = battleManager.getCurrentRoomId();
                
                if (roomIdToCleanup == null) {
                    RandomRunMod.LOGGER.info("Игрок отключился, но ID комнаты отсутствует - пропуск очистки");
                    return;
                }
                
                RandomRunMod.LOGGER.info("Игрок отключился во время битвы (комната: " + roomIdToCleanup + ") - немедленная очистка");
                
                // Trigger Self Disconnect logic to update Firebase BEFORE cleanup
                battleManager.handleSelfDisconnect();
                
                // Немедленная очистка
                if (roomIdToCleanup.equals(battleManager.getCurrentRoomId())) {
                    battleManager.handleDisconnect();
                }
            }
        });
    }
}

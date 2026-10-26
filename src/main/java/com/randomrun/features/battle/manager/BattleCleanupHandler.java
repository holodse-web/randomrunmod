package com.randomrun.features.battle.manager;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.widget.GlobalParticleSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class BattleCleanupHandler {
    private static BattleRoom cleaningRoom;

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
            particles.clearParticles();
            particles.setRedMode(false);
            particles.setGreenMode(false);
            BattleManager manager = BattleManager.getInstance();
            BattleRoom room = manager.getCurrentRoom();
            String roomId = manager.getCurrentRoomId();
            if (!manager.isInBattle() || room == null || roomId == null || cleaningRoom == room) return;
            cleaningRoom = room;
            BattleCleanupTask.finish(manager.notifyDisconnect(roomId, room), 2000, client,
                () -> manager.getCurrentRoom() == room && roomId.equals(manager.getCurrentRoomId()),
                manager::stopBattle).whenComplete((notified, error) -> {
                    if (cleaningRoom == room) cleaningRoom = null;
                    if (error != null) RandomRunMod.LOGGER.error("[BattleCleanup] Ошибка локальной очистки", error);
                    else if (!notified) RandomRunMod.LOGGER.warn("[BattleCleanup] Сервер не подтвердил выход из комнаты {}", roomId);
                });
        });
    }

    public static void shutdown() { cleaningRoom = null; }
}

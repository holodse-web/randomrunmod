package com.randomrun.features.battle.screen.lobby.util.helper;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.room.RoomWaitingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import com.randomrun.features.battle.screen.room.PasswordInputScreen;

public class RoomClickHandler {
    private final Minecraft client;
    private final Screen parentScreen;
    private final Screen lobbyScreen;
    private boolean joining;
    
    public RoomClickHandler(Minecraft client, Screen parentScreen, Screen lobbyScreen) {
        this.client = client;
        this.parentScreen = parentScreen;
        this.lobbyScreen = lobbyScreen;
    }
    
    public boolean handleClick(BattleRoom room) {
        if (joining || client.gui.screen() != lobbyScreen) return false;
        if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
            playErrorSound();
            return false;
        }
        
        int currentPlayers = room.getPlayers() != null ? room.getPlayers().size() : 0;
        if (currentPlayers >= room.getMaxPlayers()) {
            playErrorSound();
            return false;
        }
        
        if (room.isPrivate()) {
            client.gui.setScreen(new PasswordInputScreen(lobbyScreen, room));
            return true;
        }
        
        joinRoom(room);
        return true;
    }
    
    private void joinRoom(BattleRoom room) {
        joining = true;
        String username = client.getUser().getName();
        
        BattleManager.getInstance()
            .joinRoom(username, room.getRoomCode(), "")
            .whenComplete((success, throwable) -> client.execute(() -> {
                joining = false;
                if (client.gui.screen() != lobbyScreen) return;
                if (throwable != null) {
                    com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[RoomClick] Ошибка подключения", throwable);
                    playErrorSound();
                } else if (Boolean.TRUE.equals(success)) {
                        client.gui.setScreen(new RoomWaitingScreen(
                            parentScreen, 
                            room.getRoomCode(), 
                            false
                        ));
                } else {
                    playErrorSound();
                }
            }));
    }

    public boolean isJoining() { return joining; }
    
    private void playErrorSound() {
        client.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f
            )
        );
    }
}

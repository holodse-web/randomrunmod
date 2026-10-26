package com.randomrun.features.battle.manager;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

public class DisconnectHandler {
    private static final String LOG_TAG = "[DisconnectHandler]";
    
    private final BattleApiHelper apiHelper;

    public DisconnectHandler(BattleApiHelper apiHelper) {
        this.apiHelper = apiHelper;
    }

    public CompletableFuture<Boolean> notifyDeparture(String roomId, BattleRoom room, boolean host, String name) {
        if (roomId == null || room == null || name == null || !room.hasPlayer(name)) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> updates = new HashMap<>();
        String opponent = host ? room.getGuest() : room.getHost();
        if (room.isPrivate() && (room.getStatus() == BattleRoom.RoomStatus.STARTED
                || room.getStatus() == BattleRoom.RoomStatus.FROZEN) && opponent != null && !opponent.isEmpty()) {
            updates.put("w", opponent);
            updates.put("st", "FINISHED");
            updates.put("dd/" + name, true);
            return apiHelper.updateRoomFields(roomId, updates);
        }
        if (host) return apiHelper.deleteRoom(roomId).thenApply(ignored -> true);
        updates.put("dd/" + name, true);
        return apiHelper.updateRoomFields(roomId, updates);
    }

    public void handlePlayerDisconnect(String playerName, String roomId, BattleRoom currentRoom, boolean isHost) {
        if (currentRoom == null || !isHost || roomId == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("dd/" + playerName, true);
                
                apiHelper.updateRoomFields(roomId, updates)
                    .orTimeout(2, TimeUnit.SECONDS)
                    .thenAccept(success -> {
                        if (success) {
                            currentRoom.setPlayerDisconnected(playerName, true);
                            checkGameStateAfterDisconnect(currentRoom, roomId);
                        }
                    }).join();
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка обработки отключения", LOG_TAG, e);
            }
        });
    }

    public void handleSelfDisconnect(String roomId, BattleRoom currentRoom, boolean isHost, boolean isInBattle) {
        if (!isInBattle || currentRoom == null || roomId == null) return;

        String myName = Minecraft.getInstance().getUser().getName();

        if (!isHost) {
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("dd/" + myName, true);
                    apiHelper.updateRoomFields(roomId, updates)
                        .orTimeout(1, TimeUnit.SECONDS)
                        .join();
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("{} Ошибка отправки статуса отключения", LOG_TAG, e);
                }
            });
        }
    }

    public void handleDisconnectDuringBattle(String roomId, BattleRoom currentRoom, boolean isHost) {
        if (currentRoom == null || roomId == null) return;

        String myName = Minecraft.getInstance().getUser().getName();

        if (currentRoom.isPrivate() &&
            (currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED ||
             currentRoom.getStatus() == BattleRoom.RoomStatus.FROZEN)) {
            String opponentName = isHost ? currentRoom.getGuest() : currentRoom.getHost();

            if (opponentName != null && !opponentName.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        processDisconnectResult(roomId, myName, opponentName, false);
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("{} Ошибка обработки отключения в битве", LOG_TAG, e);
                    }
                });
            }
        }
    }

    public void cleanupOnShutdown(String roomId, BattleRoom currentRoom, boolean isHost) {
        if (roomId == null) return;

        try {
            if (currentRoom != null && currentRoom.isPrivate() &&
                (currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED ||
                 currentRoom.getStatus() == BattleRoom.RoomStatus.FROZEN)) {
                String myName = Minecraft.getInstance().getUser().getName();
                String opponentName = isHost ? currentRoom.getGuest() : currentRoom.getHost();

                if (opponentName != null && !opponentName.isEmpty()) {
                    processDisconnectResult(roomId, myName, opponentName, true);
                    return;
                }
            }

            if (isHost) {
                HttpClient.getInstance().delete("/rooms/" + roomId)
                    .orTimeout(1, TimeUnit.SECONDS)
                    .join();
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка cleanup", LOG_TAG, e);
        }
    }

    private void processDisconnectResult(String roomId, String disconnectedPlayer,
                                        String opponentName, boolean isShutdown) {
        try {
            Map<String, Object> updates = new HashMap<>();

            if (isShutdown) {
                updates.put("w", opponentName);
                updates.put("st", "FINISHED");
            } else {
                updates.put("ep/" + disconnectedPlayer, true);
            }

            if (!updates.isEmpty()) {
                apiHelper.updateRoomFields(roomId, updates)
                    .orTimeout(2, TimeUnit.SECONDS)
                    .join();
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка processDisconnectResult", LOG_TAG, e);
        }
    }

    private void checkGameStateAfterDisconnect(BattleRoom room, String roomId) {
        if (room == null) return;

        List<String> players = room.getPlayers();
        long activeCount = players.stream()
            .filter(p -> !room.isPlayerDisconnected(p) && !room.getEliminatedPlayers().contains(p))
            .count();

        Map<String, Object> updates = new HashMap<>();
        
        if (players.size() == 2 && activeCount == 1 && 
            room.getStatus() == BattleRoom.RoomStatus.STARTED) {
            updates.put("st", "DRAW");
            updates.put("dr", "disconnect");
        }
        else if (activeCount == 1 && players.size() > 2 && 
            room.getStatus() == BattleRoom.RoomStatus.STARTED) {
            String winner = players.stream()
                .filter(p -> !room.isPlayerDisconnected(p) && !room.getEliminatedPlayers().contains(p))
                .findFirst()
                .orElse(null);
            
            if (winner != null) {
                updates.put("w", winner);
                updates.put("st", "FINISHED");
            }
        }
        else if (activeCount == 0 && players.size() > 1) {
            updates.put("st", "DRAW");
            updates.put("dr", "disconnect");
        }
        
        if (!updates.isEmpty()) {
            apiHelper.updateRoomFields(roomId, updates)
                .orTimeout(2, TimeUnit.SECONDS);
        }
    }
}

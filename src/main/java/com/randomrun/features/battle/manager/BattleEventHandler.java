package com.randomrun.features.battle.manager;

import com.google.gson.JsonObject;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class BattleEventHandler {
    private static final String LOG_TAG = "[BattleEvent]";
    
    private final ScheduledExecutorService scheduler;
    private final BattleApiHelper apiHelper;
    private final AtomicBoolean battleEndHandled = new AtomicBoolean(false);
    
    public BattleEventHandler(ScheduledExecutorService scheduler, BattleApiHelper apiHelper) {
        this.scheduler = scheduler;
        this.apiHelper = apiHelper;
    }
    
    public boolean isBattleEndHandled() { return battleEndHandled.get(); }
    public void setBattleEndHandled(boolean handled) { this.battleEndHandled.set(handled); }
    
    public CompletableFuture<Boolean> reportBattleResult(String roomId, BattleRoom currentRoom,
                                                          long time, BattleResultType resultType) {
        if (!validateRoomId(roomId) || currentRoom == null) {
            RandomRunMod.LOGGER.error("{} Невалидные данные для отчета", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }
        
        final String playerName = Minecraft.getInstance().getUser().getName();
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        if (time <= 0 && resultType != BattleResultType.DRAW) {
            RandomRunMod.LOGGER.warn("{} Некорректное время: {} для типа {}", LOG_TAG, time, resultType);
            return CompletableFuture.completedFuture(false);
        }
        
        if (currentRoom.getWinner() != null && !currentRoom.getWinner().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        
        if (currentRoom.getStatus() == BattleRoom.RoomStatus.DRAW) {
            return CompletableFuture.completedFuture(false);
        }
        
        Map<String, Object> updates = new HashMap<>();
        
        switch (resultType) {
            case VICTORY, SHARED_WORLD_VICTORY -> {
                updates.put("w", playerName);
                updates.put("st", "FINISHED");
                if (time > 0) {
                    boolean isHost = currentRoom.isHost(playerName);
                    String timePath = (isHost ? "h/" : "g/") + playerName + "/t";
                    updates.put(timePath, time);
                }
            }
            case DRAW -> {
                updates.put("st", "DRAW");
                if (time > 0) {
                    boolean isHost = currentRoom.isHost(playerName);
                    String timePath = (isHost ? "h/" : "g/") + playerName + "/t";
                    updates.put(timePath, time);
                }
            }
        }
        
        return apiHelper.updateRoomFields(roomId, updates)
            .thenApply(success -> {
                if (!success) {
                    RandomRunMod.LOGGER.error("{} Не удалось отправить результат", LOG_TAG);
                }
                return success;
            }).exceptionally(e -> {
            RandomRunMod.LOGGER.error("{} Ошибка отправки результата", LOG_TAG, e);
            return false;
        }).orTimeout(BattleConstants.EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    public CompletableFuture<Boolean> reportEvent(String roomId, EventType eventType, Map<String, Object> data) {
        if (!validateRoomId(roomId)) {
            return CompletableFuture.completedFuture(false);
        }
        
        String playerName = Minecraft.getInstance().getUser().getName();
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return retryOperation(() -> {
            String path = BattleConstants.API_ROOMS_PATH + roomId + BattleConstants.API_EVENTS_PATH;
            
            JsonObject event = new JsonObject();
            event.addProperty("type", eventType.name());
            event.addProperty("player", playerName);
            event.addProperty("timestamp", System.currentTimeMillis());
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    event.addProperty(key, (String) value);
                } else if (value instanceof Number) {
                    event.addProperty(key, (Number) value);
                } else if (value instanceof Boolean) {
                    event.addProperty(key, (Boolean) value);
                }
            }
            
            return com.randomrun.network.HttpClient.getInstance().post(path, event)
                .thenApply(result -> result != null);
        }, 0).thenApply(success -> {
            if (!success) {
                RandomRunMod.LOGGER.error("{} Не удалось отправить событие {}", LOG_TAG, eventType);
            }
            return success;
        }).exceptionally(e -> {
            RandomRunMod.LOGGER.error("{} Ошибка отправки события {}", LOG_TAG, eventType, e);
            return false;
        }).orTimeout(BattleConstants.EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    public CompletableFuture<Boolean> reportElimination(String roomId, BattleRoom currentRoom) {
        if (currentRoom == null) {
            RandomRunMod.LOGGER.warn("{} Попытка элиминации без комнаты", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }
        
        String myName = Minecraft.getInstance().getUser().getName();
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("ep/" + myName, true);
        
        return apiHelper.updateRoomFields(roomId, updates)
            .orTimeout(2, TimeUnit.SECONDS)
            .thenApply(success -> {
                if (success) {
                    currentRoom.addEliminatedPlayer(myName);
                } else {
                    RandomRunMod.LOGGER.error("{} Не удалось зафиксировать элиминацию", LOG_TAG);
                }
                return success;
            }).exceptionally(e -> {
                RandomRunMod.LOGGER.error("{} Ошибка элиминации", LOG_TAG, e);
                return false;
            });
    }
    
    public CompletableFuture<Boolean> reportDefeat(String roomId, BattleRoom currentRoom, String reason) {
        if (currentRoom == null) {
            RandomRunMod.LOGGER.warn("{} Попытка отчета поражения без комнаты", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }
        
        String myName = Minecraft.getInstance().getUser().getName();
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("ep/" + myName, true);
        
        boolean isDisconnect = reason != null && (reason.toLowerCase().contains("disconnect") || reason.toLowerCase().contains("crash"));
        if (isDisconnect) {
            updates.put("st", "DRAW");
        } else {
            List<String> allPlayers = currentRoom.getPlayers();
            Map<String, Boolean> eliminatedMap = currentRoom.getEliminationMap();
            int activeCount = (int) allPlayers.stream()
                .filter(p -> !p.equals(myName) && !eliminatedMap.containsKey(p)).count();
        }
        
        CompletableFuture<Boolean> fieldUpdate = apiHelper.updateRoomFields(roomId, updates)
            .orTimeout(2, TimeUnit.SECONDS);
        CompletableFuture<Boolean> eventUpdate = reportEvent(roomId, EventType.DEATH, Map.of());
        
        return CompletableFuture.allOf(fieldUpdate, eventUpdate)
            .thenApply(v -> {
                try {
                    return fieldUpdate.get();
                } catch (Exception e) {
                    return false;
                }
            }).exceptionally(e -> {
                RandomRunMod.LOGGER.error("{} Ошибка отчета поражения", LOG_TAG, e);
                return false;
            });
    }
    
    public CompletableFuture<Boolean> reportAchievement(String roomId, String achievementId) {
        String compressedId = IdCompressor.compress(achievementId);
        return reportEvent(roomId, EventType.ACHIEVEMENT, Map.of("aid", compressedId));
    }
    
    public void handleBattleEnd(BattleRoom room, boolean isHost, Runnable onCleanup) {
        String playerName = Minecraft.getInstance().getUser().getName();
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();

        boolean serverSaysIWon = room.getWinner() != null && room.getWinner().equals(playerName);
        
        if (room.getStatus() == BattleRoom.RoomStatus.DRAW) {
            handleDraw(room);
            scheduleCleanup(isHost, onCleanup);
            return;
        }

        boolean won = room.getWinner() != null && room.getWinner().equals(playerName);

        if (won) {
            handleVictory(room, playerName);
        } else {
            handleDefeat(room, playerName);
        }
        
        scheduleCleanup(isHost, onCleanup);
    }
    
    private void handleDraw(BattleRoom room) {
        String reason = room.getDefeatReason();
        if (reason == null) reason = "Ничья (Игрок отключился)";
        if ("disconnect".equals(reason)) reason = "Ничья! Игрок отключился.";
        
        final String finalReason = reason;
        Minecraft.getInstance().execute(() -> {
            Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(room.getTargetItem()));
            Minecraft.getInstance().gui.setScreen(
                new com.randomrun.ui.screen.endgame.DrawScreen(targetItem, 0, finalReason));
        });
    }
    
    private void scheduleCleanup(boolean isHost, Runnable onCleanup) {
        BattleRoom completedRoom = BattleManager.getInstance().getCurrentRoom();
        boolean countClassicRun = completedRoom != null && !completedRoom.isUpgrader();
        scheduler.schedule(() -> {
            if (isHost) {
                if (countClassicRun) com.randomrun.infrastructure.data.GlobalStatsManager.incrementRun();
                BattleManager.getInstance().deleteRoom();
            }
            onCleanup.run();
        }, 2, TimeUnit.SECONDS);
    }
    
    private void handleVictory(BattleRoom room, String playerName) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager != null && runManager.getStatus() == RunDataManager.RunStatus.COMPLETED) {
            return;
        }
        
        showNormalVictory();
    }
    
    private void showDrawScreen(String reasonKey, long time) {
        Minecraft.getInstance().execute(() -> {
            Item targetItem = RandomRunMod.getInstance().getRunDataManager().getTargetItem();
            Minecraft.getInstance().gui.setScreen(new com.randomrun.ui.screen.endgame.DrawScreen(
                targetItem, time, com.randomrun.infrastructure.util.LanguageManager.text(reasonKey).getString()
            ));
        });
    }
    
    private void showNormalVictory() {
        playSound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        com.randomrun.core.service.RunCompletionService.getInstance()
            .completeWithVictory()
            .thenAccept(record -> {
                if (record == null) {
                    RandomRunMod.LOGGER.error("{} Не удалось сохранить победу", LOG_TAG);
                }
            });
    }
    
    private void handleDefeat(BattleRoom room, String playerName) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager != null && runManager.getStatus() == RunDataManager.RunStatus.COMPLETED) {
            showDefeatScreen(room, runManager);
            return;
        }
        
        com.randomrun.core.service.RunCompletionService.getInstance()
            .completeWithDeath()
            .thenAccept(record -> {
                if (record == null) {
                    RandomRunMod.LOGGER.error("{} Не удалось сохранить поражение", LOG_TAG);
                }
            });
        
        showDefeatScreen(room, runManager);
    }
    
    private void showDefeatScreen(BattleRoom room, RunDataManager runManager) {
        if (Minecraft.getInstance().gui.screen() instanceof com.randomrun.ui.screen.endgame.VictoryScreen) {
            Minecraft.getInstance().gui.setScreen(null);
        }
        
        String winnerName = room.getWinner();
        playSound(net.minecraft.sounds.SoundEvents.WITHER_DEATH, 1.0f, 1.0f);
        
        long opponentTime = room.getPlayerTime(winnerName);
        String reason = com.randomrun.infrastructure.util.LanguageManager
            .text("randomrun.defeat.reason.opponent_won", winnerName).getString();
        if (opponentTime > 0) {
            reason += " (Время: " + formatDuration(opponentTime) + ")";
        }
        
        Item targetItem = getTargetItem(room, runManager);
        long elapsedTime = runManager.getCurrentTime();
        
        final Item finalTargetItem = targetItem;
        final String finalReason = reason;
        
        Minecraft.getInstance().gui.setScreen(new DefeatScreen(finalTargetItem, elapsedTime, finalReason));
    }
    
    private Item getTargetItem(BattleRoom room, RunDataManager runManager) {
        Item targetItem = runManager.getTargetItem();
        if (targetItem == null && room.getTargetItem() != null) {
            try {
                targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(IdCompressor.decompress(room.getTargetItem())));
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка получения целевого предмета", LOG_TAG, e);
            }
        }
        return targetItem;
    }
    
    private boolean validateRoomId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            RandomRunMod.LOGGER.error("{} Пустой roomId", LOG_TAG);
            return false;
        }
        return true;
    }
    
    private boolean validatePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            RandomRunMod.LOGGER.error("{} Пустое имя игрока", LOG_TAG);
            return false;
        }
        return true;
    }
    
    private <T> CompletableFuture<T> retryOperation(java.util.function.Supplier<CompletableFuture<T>> operation,
                                                     int attempt) {
        return operation.get().thenCompose(result -> CompletableFuture.completedFuture(result))
            .exceptionallyCompose(e -> {
                if (attempt < BattleConstants.EVENT_MAX_RETRY_ATTEMPTS - 1) {
                    RandomRunMod.LOGGER.warn("{} Повтор операции ({}/{})", LOG_TAG, attempt + 2, BattleConstants.EVENT_MAX_RETRY_ATTEMPTS);
                    CompletableFuture<T> delayed = new CompletableFuture<>();
                    scheduler.schedule(() ->
                        retryOperation(operation, attempt + 1)
                            .thenAccept(delayed::complete)
                            .exceptionally(ex -> {
                                delayed.completeExceptionally(ex);
                                return null;
                            }),
                        BattleConstants.EVENT_RETRY_DELAY_MS * (attempt + 1), TimeUnit.MILLISECONDS);
                    return delayed;
                }
                return CompletableFuture.failedFuture(new RuntimeException(e));
            });
    }
    
    private String formatDuration(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playSound(sound, volume, pitch);
            }
        });
    }
    
    public enum BattleResultType {
        VICTORY,
        SHARED_WORLD_VICTORY,
        DRAW
    }
    
    public enum EventType {
        DEATH,
        ACHIEVEMENT,
        ELIMINATION
    }
}

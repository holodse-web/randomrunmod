package com.randomrun.features.battle.manager;

import com.randomrun.core.run.RunDataManager;
import com.randomrun.features.battle.manager.BattleSupport.BattleConstants;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.ui.screen.endgame.EndgameScreen.DefeatScreen;

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

        final String playerName = com.randomrun.network.BackendSession.playerName();
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



    public CompletableFuture<Boolean> reportElimination(String roomId, BattleRoom currentRoom) {
        if (currentRoom == null) {
            RandomRunMod.LOGGER.warn("{} Попытка элиминации без комнаты", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }

        String myName = com.randomrun.network.BackendSession.playerName();

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

    public CompletableFuture<Boolean> reportDefeat(String roomId, BattleRoom room, String reason) {
        return reportElimination(roomId, room);
    }



    public void handleBattleEnd(BattleRoom room, boolean isHost, Runnable onCleanup) {
        String playerName = com.randomrun.network.BackendSession.playerName();

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
        if (reason == null) reason = com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.battle.draw_disconnect");
        if ("disconnect".equals(reason)) reason = com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.battle.draw_disconnect");

        final String finalReason = reason;
        Minecraft.getInstance().execute(() -> {
            Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(room.getTargetItem()));
            Minecraft.getInstance().gui.setScreen(
                new com.randomrun.ui.screen.endgame.EndgameScreen.DrawScreen(targetItem, 0, finalReason));
        });
    }

    private void scheduleCleanup(boolean isHost, Runnable onCleanup) {
        BattleRoom completedRoom = BattleManager.getInstance().getCurrentRoom();
        boolean countClassicRun = completedRoom != null && !completedRoom.isUpgrader();
        scheduler.schedule(() -> {
            if (isHost) {
                BattleManager.getInstance().deleteRoom();
            }
            onCleanup.run();
        }, 2, TimeUnit.SECONDS);
    }

    private void handleVictory(BattleRoom room, String playerName) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();

        if (com.randomrun.infrastructure.handler.VictoryHandler.isItemVictory()
                || (runManager != null && runManager.getStatus() == RunDataManager.RunStatus.COMPLETED)) {
            return;
        }

        showNormalVictory();
    }

    private void showNormalVictory() {
        playSound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        com.randomrun.core.run.RunCompletionService.getInstance()
            .completeWithSurvivorVictory()
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

        com.randomrun.core.run.RunCompletionService.getInstance()
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
            reason += com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.defeat.reason.time", formatDuration(opponentTime));
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

}

package com.randomrun.features.battle.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.HttpClient;

import java.util.Map;
import java.util.concurrent.*;

public enum BattleApiHelper {
    INSTANCE;
    
    private static final String LOG_TAG = "[BattleAPI]";
    
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer())
        .create();
    
    private ScheduledExecutorService scheduler;
    
    public CompletableFuture<Boolean> createRoom(String code, BattleRoom room) {
        if (!validateCode(code)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return retryOperation(() -> 
            HttpClient.getInstance().put("/rooms/" + code, room)
        ).thenApply(success -> {
            if (!success) {
                RandomRunMod.LOGGER.warn("{} Создание комнаты - ОШИБКА: {}", LOG_TAG, code);
            }
            return success;
        });
    }
    
    public CompletableFuture<Boolean> updateRoom(String code, BattleRoom room) {
        if (!validateCode(code)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return retryOperation(() -> 
            HttpClient.getInstance().patch("/rooms/" + code, room)
        ).thenApply(success -> {
            if (!success) {
                RandomRunMod.LOGGER.warn("{} Обновление комнаты - ОШИБКА: {}", LOG_TAG, code);
            }
            return success;
        });
    }
    
    public CompletableFuture<BattleRoom> getRoomData(String code) {
        if (!validateCode(code)) {
            return CompletableFuture.completedFuture(null);
        }
        
        return retryOperation(() -> 
            HttpClient.getInstance().getNoCache("/rooms/" + code)
        ).thenApply(element -> {
            if (element != null && element.isJsonObject()) {
                BattleRoom room = gson.fromJson(element.getAsJsonObject(), BattleRoom.class);
                room.setRoomCode(code);
                return room;
            }
            return null;
        }).exceptionally(e -> {
            RandomRunMod.LOGGER.error("{} Получение комнаты - ОШИБКА: {}", LOG_TAG, e.getMessage());
            return null;
        });
    }
    
    public CompletableFuture<Boolean> updateRoomFields(String code, Map<String, Object> updates) {
        if (!validateCode(code)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return retryOperation(() -> 
            HttpClient.getInstance().patch("/rooms/" + code, updates)
        ).thenApply(success -> {
            if (!success) {
                RandomRunMod.LOGGER.warn("{} Обновление полей - ОШИБКА: {}", LOG_TAG, code);
            }
            return success;
        });
    }
    
    public CompletableFuture<Void> deleteRoom(String roomId) {
        if (!validateCode(roomId)) {
            return CompletableFuture.completedFuture(null);
        }
        
        return retryOperation(() -> 
            HttpClient.getInstance().delete("/rooms/" + roomId)
        ).thenApply(success -> {
            if (!success) {
                RandomRunMod.LOGGER.warn("{} Удаление комнаты - ОШИБКА: {}", LOG_TAG, roomId);
            }
            return null;
        });
    }
    
    public Gson getRoomGson() {
        return gson;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    private boolean validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return true;
    }
    
    private boolean validatePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        return true;
    }
    
    private <T> CompletableFuture<T> retryOperation(java.util.function.Supplier<CompletableFuture<T>> operation) {
        return retryOperation(operation, 0);
    }
    
    private <T> CompletableFuture<T> retryOperation(
            java.util.function.Supplier<CompletableFuture<T>> operation,
            int attempt) {
        return operation.get()
            .orTimeout(BattleConstants.API_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .thenCompose(CompletableFuture::completedFuture)
            .exceptionallyCompose(e -> {
                if (attempt < BattleConstants.API_MAX_RETRIES - 1) {
                    long delay = (long) Math.pow(2, attempt) * 500;

                    if (scheduler == null) {
                        RandomRunMod.LOGGER.error("{} Scheduler не инициализирован", LOG_TAG);
                        return CompletableFuture.failedFuture(new RuntimeException(e));
                    }

                    CompletableFuture<T> delayed = new CompletableFuture<>();
                    scheduler.schedule(() ->
                        retryOperation(operation, attempt + 1)
                            .thenAccept(delayed::complete)
                            .exceptionally(ex -> {
                                delayed.completeExceptionally(ex);
                                return null;
                            }),
                        delay, TimeUnit.MILLISECONDS);
                    return delayed;
                }
                RandomRunMod.LOGGER.error("{} Операция провалена после {} попыток", LOG_TAG, BattleConstants.API_MAX_RETRIES);
                return CompletableFuture.failedFuture(new RuntimeException(e));
            });
    }
}

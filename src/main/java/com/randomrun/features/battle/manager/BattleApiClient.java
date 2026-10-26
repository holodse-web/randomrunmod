package com.randomrun.features.battle.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.network.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class BattleApiClient {
    private static final String LOG_TAG = "BattleAPI";
    
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer())
        .create();
    
    private ScheduledExecutorService scheduler;
    
    public BattleApiClient() {}
    
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    public Gson getGson() {
        return gson;
    }
    
    public CompletableFuture<Boolean> createRoom(String code, BattleRoom room) {
        if (!validateCode(code)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return retryOperation(() -> 
            HttpClient.getInstance().put("/rooms/" + code, room)
        ).thenApply(success -> {
            if (!success) {
                CompactLogger.warn(LOG_TAG, "Room creation failed: " + code);
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
                CompactLogger.warn(LOG_TAG, "Room update failed: " + code);
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
            CompactLogger.warn(LOG_TAG, "Room not found: " + code);
            return null;
        }).exceptionally(e -> {
            CompactLogger.error(LOG_TAG, "Get room error: " + e.getMessage());
            return null;
        });
    }
    
    public CompletableFuture<List<BattleRoom>> getAllRooms() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonElement element = HttpClient.getInstance().get("/rooms").join();
                
                if (element != null && element.isJsonArray()) {
                    List<BattleRoom> rooms = new ArrayList<>();
                    for (JsonElement roomElement : element.getAsJsonArray()) {
                        if (roomElement.isJsonObject()) {
                            BattleRoom room = gson.fromJson(roomElement, BattleRoom.class);
                            rooms.add(room);
                        }
                    }
                    return rooms;
                }
                
                return new ArrayList<>();
            } catch (Exception e) {
                CompactLogger.error(LOG_TAG, "Get all rooms error: " + e.getMessage());
                return new ArrayList<>();
            }
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
                CompactLogger.warn(LOG_TAG, "Room deletion failed: " + roomId);
            }
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
                CompactLogger.warn(LOG_TAG, "Fields update failed: " + code);
            }
            return success;
        });
    }
    
    private boolean validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
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
                        CompactLogger.error(LOG_TAG, "Scheduler not initialized");
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
                CompactLogger.error(LOG_TAG, "Operation failed after " + BattleConstants.API_MAX_RETRIES + " attempts");
                return CompletableFuture.failedFuture(new RuntimeException(e));
            });
    }
}

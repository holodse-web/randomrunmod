package com.randomrun.features.battle.manager;

import com.randomrun.features.battle.manager.BattleSupport.BattleConstants;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.HttpClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Map;
import java.util.concurrent.*;

public enum BattleApiHelper {
    INSTANCE;

    private static final String LOG_TAG = "[BattleAPI]";

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer())
        .create();

    private ScheduledExecutorService scheduler;





    public CompletableFuture<BattleRoom> getRoomData(String code) {
        if (!validateCode(code)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid room code"));
        }
        return HttpClient.getInstance().getNoCache("/rooms/" + code).thenApply(element -> {
            BattleRoom room = gson.fromJson(element, BattleRoom.class);
            if (room == null) throw new IllegalStateException("Missing room data");
            room.setRoomCode(code);
            return room;
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
                RandomRunMod.LOGGER.warn("{} Room update failed: {}", LOG_TAG, code);
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
                RandomRunMod.LOGGER.warn("{} Could not delete room {}", LOG_TAG, roomId);
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

    private <T> CompletableFuture<T> retryOperation(java.util.function.Supplier<CompletableFuture<T>> operation) {
        return retryOperation(operation, 0, com.randomrun.network.BackendSession.current());
    }

    private <T> CompletableFuture<T> retryOperation(
            java.util.function.Supplier<CompletableFuture<T>> operation,
            int attempt, com.randomrun.network.BackendSession.Session owner) {
        if (owner == null || owner != com.randomrun.network.BackendSession.current()) return CompletableFuture.failedFuture(new CancellationException("Session changed"));
        return operation.get()
            .orTimeout(BattleConstants.API_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .thenCompose(CompletableFuture::completedFuture)
            .exceptionallyCompose(e -> {
                if (attempt < BattleConstants.API_MAX_RETRIES - 1) {
                    long delay = (long) Math.pow(2, attempt) * 500;

                    if (scheduler == null) {
                        RandomRunMod.LOGGER.error("{} Retry scheduler unavailable", LOG_TAG);
                        return CompletableFuture.failedFuture(new RuntimeException(e));
                    }

                    CompletableFuture<T> delayed = new CompletableFuture<>();
                    scheduler.schedule(() ->
                        retryOperation(operation, attempt + 1, owner)
                            .thenAccept(delayed::complete)
                            .exceptionally(ex -> {
                                delayed.completeExceptionally(ex);
                                return null;
                            }),
                        delay, TimeUnit.MILLISECONDS);
                    return delayed;
                }
                RandomRunMod.LOGGER.error("{} Request failed after {} attempts", LOG_TAG, BattleConstants.API_MAX_RETRIES);
                return CompletableFuture.failedFuture(new RuntimeException(e));
            });
    }
}

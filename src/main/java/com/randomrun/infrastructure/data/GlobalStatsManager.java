package com.randomrun.infrastructure.data;

import com.google.gson.JsonObject;
import com.randomrun.network.HttpClient;
import com.randomrun.infrastructure.RandomRunMod;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalStatsManager {
    private static final String LOG_TAG = "[Stats]";
    
    private static final HttpClient HTTP_CLIENT = HttpClient.getInstance();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "RandomRun-Stats");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean stopping = new AtomicBoolean();
    
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000;
    private static final long BATCH_DELAY_MS = 5000;
    private static final long RATE_LIMIT_MS = 1000;
    
    private static final Map<StatType, Integer> pendingIncrements = new ConcurrentHashMap<>();
    private static final Map<StatType, Long> lastIncrementTime = new ConcurrentHashMap<>();
    private static ScheduledFuture<?> batchTask;

    public enum StatType {
        TOTAL_RUNS("total_runs"),
        ROOMS_CREATED("total_rooms_created");
        
        private final String key;
        
        StatType(String key) {
            this.key = key;
        }
        
        public String getKey() {
            return key;
        }
    }

    public static void incrementRun() {
        increment(StatType.TOTAL_RUNS);
    }

    public static void incrementRoomCreated() {
        increment(StatType.ROOMS_CREATED);
    }

    public static void increment(StatType... stats) {
        if (stopping.get() || !isOnlineMode()) {
            return;
        }
        
        for (StatType stat : stats) {
            if (!checkRateLimit(stat)) {
                continue;
            }
            
            pendingIncrements.merge(stat, 1, Integer::sum);
            lastIncrementTime.put(stat, System.currentTimeMillis());
        }
        
        scheduleBatchSend();
    }

    public static void incrementBy(StatType stat, int amount) {
        if (stopping.get() || !isOnlineMode() || amount <= 0) {
            return;
        }
        
        if (!checkRateLimit(stat)) {
            return;
        }
        
        pendingIncrements.merge(stat, amount, Integer::sum);
        lastIncrementTime.put(stat, System.currentTimeMillis());
        scheduleBatchSend();
    }

    public static CompletableFuture<JsonObject> getStats() {
        if (!isOnlineMode()) {
            return CompletableFuture.completedFuture(new JsonObject());
        }
        
        return HTTP_CLIENT.get("/stats/global")
            .thenApply(response -> {
                if (response != null && response.isJsonObject()) {
                    return response.getAsJsonObject();
                }
                return new JsonObject();
            })
            .exceptionally(ex -> {
                RandomRunMod.LOGGER.error("{} Ошибка получения статистики", LOG_TAG, ex);
                return new JsonObject();
            });
    }

    private static synchronized void scheduleBatchSend() {
        if (stopping.get() || (batchTask != null && !batchTask.isDone())) {
            return;
        }
        
        batchTask = SCHEDULER.schedule(() -> {
            if (!pendingIncrements.isEmpty()) {
                sendBatch();
            }
        }, BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void sendBatch() {
        Map<StatType, Integer> batch = new HashMap<>(pendingIncrements);
        pendingIncrements.clear();
        
        if (batch.isEmpty()) {
            return;
        }
        
        sendBatchWithRetry(batch, 0);
    }

    private static void sendBatchWithRetry(Map<StatType, Integer> batch, int attempt) {
        if (stopping.get()) {
            returnBatchToQueue(batch);
            return;
        }
        if (attempt >= MAX_RETRIES) {
            RandomRunMod.LOGGER.error("{} Превышен лимит попыток отправки", LOG_TAG);
            returnBatchToQueue(batch);
            scheduleBatchSend();
            return;
        }
        
        try {
            JsonObject updates = createBatchPayload(batch);
            
            HTTP_CLIENT.patch("/stats/global", updates)
                .exceptionally(ex -> {
                    synchronized (GlobalStatsManager.class) {
                        if (stopping.get()) {
                            returnBatchToQueue(batch);
                            return null;
                        }
                        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt);
                        RandomRunMod.LOGGER.warn("{} Ошибка отправки, повтор {}/{} через {}ms",
                            LOG_TAG, attempt + 1, MAX_RETRIES, delay);

                        SCHEDULER.schedule(() -> sendBatchWithRetry(batch, attempt + 1),
                            delay, TimeUnit.MILLISECONDS);
                    }
                    return null;
                });
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка создания запроса, возврат в очередь", LOG_TAG, e);
            returnBatchToQueue(batch);
            scheduleBatchSend();
        }
    }
    
    private static void returnBatchToQueue(Map<StatType, Integer> batch) {
        for (Map.Entry<StatType, Integer> entry : batch.entrySet()) {
            pendingIncrements.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private static JsonObject createBatchPayload(Map<StatType, Integer> batch) {
        JsonObject updates = new JsonObject();
        
        for (Map.Entry<StatType, Integer> entry : batch.entrySet()) {
            updates.add(entry.getKey().getKey(), createIncrementPayload(entry.getValue()));
        }
        
        return updates;
    }

    private static JsonObject createIncrementPayload(int amount) {
        JsonObject incrementValue = new JsonObject();
        JsonObject serverValue = new JsonObject();
        serverValue.addProperty("increment", amount);
        incrementValue.add(".sv", serverValue);
        return incrementValue;
    }

    private static boolean checkRateLimit(StatType stat) {
        Long lastTime = lastIncrementTime.get(stat);
        if (lastTime == null) {
            return true;
        }
        
        long elapsed = System.currentTimeMillis() - lastTime;
        return elapsed >= RATE_LIMIT_MS;
    }

    private static boolean isOnlineMode() {
        try {
            return RandomRunMod.getInstance().getConfig().isOnlineMode();
        } catch (Exception e) {
            return false;
        }
    }

    public static void shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        final Map<StatType, Integer> finalBatch;
        synchronized (GlobalStatsManager.class) {
            if (batchTask != null) {
                batchTask.cancel(false);
            }
            SCHEDULER.shutdownNow();
            finalBatch = new HashMap<>(pendingIncrements);
            pendingIncrements.clear();
        }
        if (finalBatch.isEmpty()) {
            return;
        }
        try {
            HTTP_CLIENT.patch("/stats/global", createBatchPayload(finalBatch))
                .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RandomRunMod.LOGGER.warn("{} Финальная отправка прервана", LOG_TAG);
        } catch (TimeoutException e) {
            RandomRunMod.LOGGER.warn("{} Таймаут при финальной отправке", LOG_TAG);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка при финальной отправке", LOG_TAG, e);
        }
    }
}

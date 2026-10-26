package com.randomrun.core.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.network.HttpClient;
import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunResult;
import com.randomrun.infrastructure.RandomRunMod;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public enum CloudStorage implements StorageAdapter {
    INSTANCE;
    
    private static final String LOG_TAG = "[Cloud]";
    
    private static final String FIELD_TIME = "t";
    private static final String FIELD_DURATION = "d";
    private static final String FIELD_RESULT = "r";
    private static final String FIELD_SEED = "s";
    private static final String FIELD_CUSTOM_SEED = "cs";
    private static final String FIELD_BATTLE = "b";
    private static final String FIELD_BATTLE_ROOM = "br";
    private static final String FIELD_HARDCORE = "h";
    private static final String FIELD_MOD_VERSION = "m";
    
    private static final long AVAILABILITY_CACHE_TTL_MS = 30_000;
    
    private final HttpClient httpClient;
    
    private volatile long lastAvailabilityCheck = 0;
    private volatile boolean cachedAvailability = false;
    
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    
    CloudStorage() {
        this.httpClient = HttpClient.getInstance();
    }
    
    @Override
    public CompletableFuture<List<Run>> loadHistory(String playerName) {
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        requestCount.incrementAndGet();
        String path = "/profiles/" + playerName + "/history";
        
        return httpClient.getNoCache(path)
            .thenApply(result -> {
                if (result == null || result.isJsonNull()) {
                    RandomRunMod.LOGGER.warn("{} Получен null ответ для {}", LOG_TAG, playerName);
                    return new ArrayList<Run>();
                }
                
                if (result.isJsonObject() && result.getAsJsonObject().size() == 0) {
                    return new ArrayList<Run>();
                }
                
                return parseHistoryResponse(result, playerName);
            })
            .exceptionally(e -> {
                failedRequests.incrementAndGet();
                RandomRunMod.LOGGER.error("{} Загрузка истории - ОШИБКА: {}", LOG_TAG, e.getMessage());
                if (e.getCause() != null) {
                    RandomRunMod.LOGGER.error("{} Причина: {}", LOG_TAG, e.getCause().getMessage());
                }
                return new ArrayList<>();
            });
    }
    
    private List<Run> parseHistoryResponse(JsonElement result, String playerName) {
        if (result == null || !result.isJsonObject()) {
            return new ArrayList<>();
        }
        
        List<Run> records = new ArrayList<>();
        JsonObject historyObj = result.getAsJsonObject();
        
        for (String itemKey : historyObj.keySet()) {
            JsonElement itemElement = historyObj.get(itemKey);
            
            String targetId;
            try {
                targetId = com.randomrun.infrastructure.util.IdCompressor.decompress(itemKey);
            } catch (Exception e) {
                targetId = itemKey;
            }
            
            List<Run> itemRecords = parseHistoryItem(itemElement, targetId, playerName, itemKey);
            records.addAll(itemRecords);
        }
        
        return records;
    }
    
    private List<Run> parseHistoryItem(JsonElement itemElement, String targetId, String playerName, String compressedItemId) {
        List<Run> records = new ArrayList<>();
        
        if (itemElement.isJsonObject()) {
            JsonObject itemRecords = itemElement.getAsJsonObject();
            
            for (String recordId : itemRecords.keySet()) {
                try {
                    JsonElement recordElement = itemRecords.get(recordId);
                    if (recordElement.isJsonObject()) {
                        int attemptNumber = 1;
                        try {
                            attemptNumber = Integer.parseInt(recordId);
                        } catch (NumberFormatException e) {
                            RandomRunMod.LOGGER.warn("{} Неверный формат ключа: {}", LOG_TAG, recordId);
                        }
                        
                        Run record = parseRunRecord(recordElement.getAsJsonObject(), targetId, attemptNumber);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                } catch (Exception e) {
                    RandomRunMod.LOGGER.warn("{} Пропуск записи {}/{}: {}", LOG_TAG, compressedItemId, recordId, e.getMessage());
                }
            }
        } else if (itemElement.isJsonArray()) {
            com.google.gson.JsonArray itemRecords = itemElement.getAsJsonArray();
            
            for (int i = 0; i < itemRecords.size(); i++) {
                try {
                    JsonElement recordElement = itemRecords.get(i);
                    
                    if (recordElement == null || recordElement.isJsonNull()) {
                        continue;
                    }
                    
                    if (recordElement.isJsonObject()) {
                        Run record = parseRunRecord(recordElement.getAsJsonObject(), targetId, i + 1);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                } catch (Exception e) {
                    RandomRunMod.LOGGER.warn("{} Пропуск записи {}/[{}]: {}", LOG_TAG, compressedItemId, i, e.getMessage());
                }
            }
        }
        
        return records;
    }
    
    private Run parseRunRecord(JsonObject obj, String targetId, int attemptNumber) {
        try {
            String resultCode = obj.get(FIELD_RESULT).getAsString();
            RunResult result = RunResult.fromCode(resultCode);
            long duration = obj.get(FIELD_DURATION).getAsLong();
            long timestamp = obj.get(FIELD_TIME).getAsLong();
            
            return new Run.Builder()
                .targetId(targetId)
                .timestamp(timestamp)
                .duration(duration)
                .result(result)
                .worldSeed(obj.has(FIELD_SEED) ? obj.get(FIELD_SEED).getAsString() : null)
                .customSeed(obj.has(FIELD_CUSTOM_SEED) && !obj.get(FIELD_CUSTOM_SEED).isJsonNull() 
                    ? obj.get(FIELD_CUSTOM_SEED).getAsString() : null)
                .isBattle(obj.has(FIELD_BATTLE) && obj.get(FIELD_BATTLE).getAsBoolean())
                .battleRoomId(obj.has(FIELD_BATTLE_ROOM) && !obj.get(FIELD_BATTLE_ROOM).isJsonNull() 
                    ? obj.get(FIELD_BATTLE_ROOM).getAsString() : null)
                .isHardcore(obj.has(FIELD_HARDCORE) && obj.get(FIELD_HARDCORE).getAsBoolean())
                .modVersion(obj.has(FIELD_MOD_VERSION) ? obj.get(FIELD_MOD_VERSION).getAsString() : "unknown")
                .attemptNumber(attemptNumber)
                .build();
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> saveRecord(String playerName, Run record) {
        if (record != null && record.isUpgrader()) {
            return CompletableFuture.completedFuture(false);
        }
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        requestCount.incrementAndGet();
        
        Map<String, Object> recordData = buildRecordData(record);
        String compressedItemId = com.randomrun.infrastructure.util.IdCompressor.compress(record.getTargetId());
        String recordId = String.valueOf(record.getAttemptNumber());
        String path = "/profiles/" + playerName + "/history/" + compressedItemId + "/" + recordId;
        
        return httpClient.put(path, recordData)
            .thenApply(success -> {
                if (!success) {
                    failedRequests.incrementAndGet();
                    RandomRunMod.LOGGER.warn("{} Сохранение записи - ОШИБКА", LOG_TAG);
                }
                return success;
            })
            .exceptionally(e -> {
                failedRequests.incrementAndGet();
                RandomRunMod.LOGGER.error("{} Сохранение записи - ОШИБКА: {}", LOG_TAG, e.getMessage());
                return false;
            });
    }
    
    @Override
    public CompletableFuture<Boolean> saveHistory(String playerName, List<Run> records) {
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        if (records.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        
        requestCount.incrementAndGet();
        
        Map<String, Object> batchData = new HashMap<>();
        
        for (Run record : records) {
            if (record.isUpgrader()) continue;
            Map<String, Object> recordData = buildRecordData(record);
            
            String compressedItemId = com.randomrun.infrastructure.util.IdCompressor.compress(record.getTargetId());
            String recordId = String.valueOf(record.getAttemptNumber());
            String path = "profiles/" + playerName + "/history/" + compressedItemId + "/" + recordId;
            
            batchData.put(path, recordData);
        }

        if (batchData.isEmpty()) return CompletableFuture.completedFuture(true);
        
        return httpClient.patch("/", batchData)
            .thenApply(success -> {
                if (!success) {
                    failedRequests.incrementAndGet();
                    RandomRunMod.LOGGER.warn("{} Синхронизация истории - ОШИБКА", LOG_TAG);
                }
                return success;
            })
            .exceptionally(e -> {
                failedRequests.incrementAndGet();
                RandomRunMod.LOGGER.error("{} Синхронизация - ОШИБКА: {}", LOG_TAG, e.getMessage());
                return false;
            });
    }
    
    private Map<String, Object> buildRecordData(Run record) {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(FIELD_DURATION, record.getDuration());
        recordData.put(FIELD_TIME, record.getTimestamp());
        recordData.put(FIELD_RESULT, record.getResult().getCode());
        recordData.put(FIELD_SEED, record.getWorldSeed() != null && !record.getWorldSeed().isEmpty() 
            ? record.getWorldSeed() : "0");
        
        if (record.getCustomSeed() != null && !record.getCustomSeed().isEmpty()) {
            recordData.put(FIELD_CUSTOM_SEED, record.getCustomSeed());
        }
        if (record.isBattle()) {
            recordData.put(FIELD_BATTLE, true);
        }
        if (record.getBattleRoomId() != null) {
            recordData.put(FIELD_BATTLE_ROOM, record.getBattleRoomId());
        }
        if (record.isHardcore()) {
            recordData.put(FIELD_HARDCORE, true);
        }
        
        String shortVersion = record.getModVersion().replace("-BETA", "").replace("-ALPHA", "");
        recordData.put(FIELD_MOD_VERSION, shortVersion);
        
        return recordData;
    }
    
    @Override
    public CompletableFuture<Boolean> isAvailable() {
        long now = System.currentTimeMillis();
        
        if (now - lastAvailabilityCheck < AVAILABILITY_CACHE_TTL_MS) {
            return CompletableFuture.completedFuture(cachedAvailability);
        }
        
        return httpClient.get("/version")
            .thenApply(result -> {
                cachedAvailability = result != null;
                lastAvailabilityCheck = System.currentTimeMillis();
                if (!cachedAvailability) {
                    RandomRunMod.LOGGER.warn("{} Проверка доступности - НЕДОСТУПЕН", LOG_TAG);
                }
                return cachedAvailability;
            })
            .exceptionally(e -> {
                RandomRunMod.LOGGER.warn("{} Проверка доступности - ОШИБКА: {}", LOG_TAG, e.getMessage());
                cachedAvailability = false;
                lastAvailabilityCheck = System.currentTimeMillis();
                return false;
            });
    }
    
    @Override
    public String getType() {
        return "firebase";
    }
    
    @Override
    public CompletableFuture<Map<String, Integer>> getMetrics() {
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("requests", requestCount.get());
        metrics.put("failures", failedRequests.get());
        return CompletableFuture.completedFuture(metrics);
    }
    
    private boolean validatePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        return true;
    }
}

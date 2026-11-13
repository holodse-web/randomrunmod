package com.randomrun.features.leaderboard;

import com.randomrun.features.leaderboard.LeaderboardEntry.LatestRunEntry;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.network.HttpClient;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class LeaderboardService {
    private static final String LOG_TAG = "[RRM:Leaderboard]";
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_RETRIES = 5;
    private static final int MAX_LIMIT = 1000;
    private static final long CACHE_TTL_MS = 60000;
    
    private final HttpClient httpClient;
    private final Map<String, CachedLeaderboard> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleanupExecutor;
    
    private LeaderboardService() {
        this.httpClient = HttpClient.getInstance();
        this.cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LeaderboardCacheCleanup");
            t.setDaemon(true);
            return t;
        });
        startCacheCleanup();
    }
    
    private static class Holder {
        private static final LeaderboardService INSTANCE = new LeaderboardService();
    }
    
    public static LeaderboardService getInstance() {
        return Holder.INSTANCE;
    }
    
    private void startCacheCleanup() {
        cacheCleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка очистки кэша: {}", LOG_TAG, e.getMessage());
            }
        }, CACHE_TTL_MS, CACHE_TTL_MS, TimeUnit.MILLISECONDS);
    }
    

    




    
    public CompletableFuture<List<LatestRunEntry>> getLatestRuns(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final int finalLimit = (limit <= 0 || limit > MAX_LIMIT) ? 100 : limit;
                
                JsonElement dataElement = executeWithRetry(() -> 
                    httpClient.get("/latest_runs")
                        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                );
                    
                if (dataElement == null || !dataElement.isJsonObject()) {
                    return new ArrayList<>();
                }

                List<LatestRunEntry> entries = new ArrayList<>();
                JsonObject wrapper = dataElement.getAsJsonObject();
                
                for (String playerName : wrapper.keySet()) {
                    try {
                        JsonElement playerElement = wrapper.get(playerName);
                        
                        if (playerElement.isJsonArray()) {
                            JsonArray playerArray = playerElement.getAsJsonArray();
                            for (int i = 0; i < playerArray.size(); i++) {
                                JsonElement entryElement = playerArray.get(i);
                                if (entryElement != null && entryElement.isJsonObject()) {
                                    tryAddLatestEntry(entries, entryElement.getAsJsonObject(), playerName);
                                }
                            }
                            continue;
                        }

                        if (!playerElement.isJsonObject()) continue;

                        JsonObject playerData = playerElement.getAsJsonObject();

                        if (playerData.has("ti") && playerData.has("d") && playerData.has("ts")) {
                            tryAddLatestEntry(entries, playerData, playerName);
                        } else {
                            for (String key : playerData.keySet()) {
                                JsonElement entryElement = playerData.get(key);
                                if (entryElement != null && entryElement.isJsonObject()) {
                                    tryAddLatestEntry(entries, entryElement.getAsJsonObject(), playerName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.warn("{} Ошибка парсинга latest для {}: {}", LOG_TAG, playerName, e.getMessage());
                    }
                }
                
                entries.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                if (entries.size() > finalLimit) {
                    entries = entries.subList(0, finalLimit);
                }

                return entries;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка загрузки latest: {}", LOG_TAG, e.getMessage());
                return new ArrayList<>();
            }
        });
    }

    private void tryAddLatestEntry(List<LatestRunEntry> entries, JsonObject obj, String playerName) {
        try {
            LatestRunEntry entry = GSON.fromJson(obj, LatestRunEntry.class);
            if (entry != null && entry.getTargetId() != null) {
                entry.setPlayerName(playerName);
                entries.add(entry);
            }
        } catch (Exception ignored) {
        }
    }

    public CompletableFuture<List<LeaderboardEntry>> getTopRecords(String targetId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final int finalLimit = (limit <= 0 || limit > MAX_LIMIT) ? 100 : limit;
                
                CachedLeaderboard cached = cache.get(targetId);
                if (cached != null && !cached.isExpired()) {
                    List<LeaderboardEntry> result = cached.entries;
                    if (result.size() > finalLimit) {
                        result = result.subList(0, finalLimit);
                    }
                    return result;
                }
                
                String path = "/leaderboards/" + sanitizeId(IdCompressor.compress(targetId));
                
                JsonElement dataElement = executeWithRetry(() -> 
                    httpClient.get(path)
                        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                );
                    
                if (dataElement == null) {
                    RandomRunMod.LOGGER.warn("{} Нет данных для {}", LOG_TAG, targetId);
                    return new ArrayList<>();
                }
                
                List<LeaderboardEntry> entries = new ArrayList<>();
                
                if (dataElement.isJsonObject()) {
                    JsonObject leaderboard = dataElement.getAsJsonObject();
                    
                    for (String playerName : leaderboard.keySet()) {
                        try {
                            JsonObject obj = leaderboard.getAsJsonObject(playerName);
                            
                            long time = obj.get("t").getAsLong();
                            long timestamp = obj.has("ts") ? obj.get("ts").getAsLong() : System.currentTimeMillis();
                            
                            LeaderboardEntry entry = new LeaderboardEntry.Builder()
                                .targetId(targetId)
                                .completionTime(time)
                                .worldSeed(obj.has("s") ? obj.get("s").getAsString() : "unknown")
                                .modVersion("unknown")
                                .attempts(1)
                                .build();
                            entry.setTargetId(targetId);
                            entry.setPlayerName(playerName);
                            entry.setTimestamp(timestamp);
                            
                            if (entry.verifyIntegrity()) {
                                entries.add(entry);
                            }
                        } catch (Exception e) {
                            RandomRunMod.LOGGER.warn("{} Ошибка парсинга для {}", LOG_TAG, playerName);
                        }
                    }
                }
                
                entries.sort(Comparator.comparingLong(LeaderboardEntry::getCompletionTime));
                
                cache.put(targetId, new CachedLeaderboard(entries));
                
                List<LeaderboardEntry> result = entries;
                if (result.size() > finalLimit) {
                    result = result.subList(0, finalLimit);
                }
                
                return result;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка загрузки топа {}: {}", LOG_TAG, targetId, e.getMessage());
                return new ArrayList<>();
            }
        });
    }
    
    public CompletableFuture<LeaderboardEntry> getPlayerRecord(String targetId, String playerName) {
        return getTopRecords(targetId, MAX_LIMIT).thenApply(entries -> entries.stream()
            .filter(entry -> entry.getPlayerName().equalsIgnoreCase(playerName)).findFirst().orElse(null));
    }
    
    public CompletableFuture<List<String>> getPlayerRecordItems(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonElement dataElement = executeWithRetry(() -> 
                    httpClient.get("/leaderboards")
                        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                );
                
                if (dataElement == null || !dataElement.isJsonObject()) {
                    RandomRunMod.LOGGER.warn("{} Нет данных для предметов игрока", LOG_TAG);
                    return new ArrayList<>();
                }
                
                List<String> itemsWithRecords = new ArrayList<>();
                JsonObject allLeaderboards = dataElement.getAsJsonObject();
                
                for (String compressedTargetId : allLeaderboards.keySet()) {
                    try {
                        JsonObject targetLeaderboard = allLeaderboards.getAsJsonObject(compressedTargetId);
                        
                        if (targetLeaderboard.has(playerName)) {
                            String fullTargetId = IdCompressor.decompress(compressedTargetId);
                            itemsWithRecords.add(fullTargetId);
                        }
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.warn("{} Ошибка обработки {}", LOG_TAG, compressedTargetId);
                    }
                }
                
                return itemsWithRecords;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка загрузки предметов игрока: {}", LOG_TAG, e.getMessage());
                return new ArrayList<>();
            }
        });
    }
    
    public CompletableFuture<List<String>> getAllItemsWithRecords() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonElement dataElement = executeWithRetry(() -> 
                    httpClient.get("/leaderboards")
                        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                );
                
                if (dataElement == null || !dataElement.isJsonObject()) {
                    RandomRunMod.LOGGER.warn("{} Нет данных для всех предметов", LOG_TAG);
                    return new ArrayList<>();
                }
                
                List<String> itemsWithRecords = new ArrayList<>();
                JsonObject allLeaderboards = dataElement.getAsJsonObject();
                
                for (String compressedTargetId : allLeaderboards.keySet()) {
                    try {
                        JsonObject targetLeaderboard = allLeaderboards.getAsJsonObject(compressedTargetId);
                        
                        if (targetLeaderboard.size() > 0) {
                            String fullTargetId = IdCompressor.decompress(compressedTargetId);
                            itemsWithRecords.add(fullTargetId);
                        }
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.warn("{} Ошибка обработки {}", LOG_TAG, compressedTargetId);
                    }
                }
                
                return itemsWithRecords;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка загрузки всех предметов: {}", LOG_TAG, e.getMessage());
                return new ArrayList<>();
            }
        });
    }
    
    public CompletableFuture<Map<String, List<LeaderboardEntry>>> loadAllLeaderboards() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonElement dataElement = executeWithRetry(() -> 
                    httpClient.get("/leaderboards")
                        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                );
                
                if (dataElement == null || !dataElement.isJsonObject()) {
                    RandomRunMod.LOGGER.warn("{} Нет данных для всех лидербордов", LOG_TAG);
                    return new java.util.HashMap<>();
                }
                
                Map<String, List<LeaderboardEntry>> allLeaderboards = new java.util.HashMap<>();
                JsonObject allData = dataElement.getAsJsonObject();
                
                for (String compressedTargetId : allData.keySet()) {
                    try {
                        String fullTargetId = IdCompressor.decompress(compressedTargetId);
                        JsonObject targetLeaderboard = allData.getAsJsonObject(compressedTargetId);
                        
                        List<LeaderboardEntry> entries = new ArrayList<>();
                        
                        for (String playerName : targetLeaderboard.keySet()) {
                            try {
                                JsonObject obj = targetLeaderboard.getAsJsonObject(playerName);
                                
                                long time = obj.get("t").getAsLong();
                                long timestamp = obj.has("ts") ? obj.get("ts").getAsLong() : System.currentTimeMillis();
                                
                                LeaderboardEntry entry = new LeaderboardEntry.Builder()
                                    .targetId(fullTargetId)
                                    .completionTime(time)
                                    .worldSeed(obj.has("s") ? obj.get("s").getAsString() : "unknown")
                                    .modVersion("unknown")
                                    .attempts(1)
                                    .build();
                                entry.setTargetId(fullTargetId);
                                entry.setPlayerName(playerName);
                                entry.setTimestamp(timestamp);
                                
                                if (entry.verifyIntegrity()) {
                                    entries.add(entry);
                                }
                            } catch (Exception e) {
                                RandomRunMod.LOGGER.warn("{} Ошибка парсинга {} для {}", LOG_TAG, playerName, fullTargetId);
                            }
                        }
                        
                        entries.sort(Comparator.comparingLong(LeaderboardEntry::getCompletionTime));
                        cache.put(fullTargetId, new CachedLeaderboard(entries));
                        allLeaderboards.put(fullTargetId, entries);
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.warn("{} Ошибка обработки {}", LOG_TAG, compressedTargetId);
                    }
                }
                
                return allLeaderboards;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка загрузки всех лидербордов: {}", LOG_TAG, e.getMessage());
                return new java.util.HashMap<>();
            }
        });
    }
    
    public CompletableFuture<Map<String, List<LeaderboardEntry>>> getBatchRecords(List<String> targetIds, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (targetIds == null || targetIds.isEmpty()) {
                    return new java.util.HashMap<>();
                }
                
                final int finalLimit = (limit <= 0 || limit > MAX_LIMIT) ? 100 : limit;
                
                List<List<String>> batches = new ArrayList<>();
                for (int i = 0; i < targetIds.size(); i += 20) {
                    batches.add(targetIds.subList(i, Math.min(i + 20, targetIds.size())));
                }
                
                Map<String, List<LeaderboardEntry>> allResults = new java.util.HashMap<>();
                
                for (List<String> batch : batches) {
                    List<String> compressedIds = new ArrayList<>();
                    Map<String, String> compressedToFull = new java.util.HashMap<>();
                    
                    for (String targetId : batch) {
                        String compressed = sanitizeId(IdCompressor.compress(targetId));
                        compressedIds.add(compressed);
                        compressedToFull.put(compressed, targetId);
                    }
                    
                    String itemsParam = String.join(",", compressedIds);
                    String path = "/batch?items=" + itemsParam;
                    
                    JsonElement dataElement = executeWithRetry(() -> 
                        httpClient.get(path)
                            .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .join()
                    );
                    
                    if (dataElement == null || !dataElement.isJsonObject()) {
                        RandomRunMod.LOGGER.warn("{} Batch нет данных", LOG_TAG);
                        continue;
                    }
                    
                    JsonObject batchData = dataElement.getAsJsonObject();
                    
                    for (String compressedId : compressedIds) {
                        String fullTargetId = compressedToFull.get(compressedId);
                        
                        if (!batchData.has(compressedId)) {
                            allResults.put(fullTargetId, new ArrayList<>());
                            continue;
                        }
                        
                        JsonElement itemData = batchData.get(compressedId);
                        if (itemData == null || itemData.isJsonNull()) {
                            allResults.put(fullTargetId, new ArrayList<>());
                            continue;
                        }
                        
                        List<LeaderboardEntry> entries = new ArrayList<>();
                        
                        if (itemData.isJsonObject()) {
                            JsonObject leaderboard = itemData.getAsJsonObject();
                            
                            for (String playerName : leaderboard.keySet()) {
                                try {
                                    JsonObject obj = leaderboard.getAsJsonObject(playerName);
                                    
                                    long time = obj.get("t").getAsLong();
                                    long timestamp = obj.has("ts") ? obj.get("ts").getAsLong() : System.currentTimeMillis();
                                    
                                    LeaderboardEntry entry = new LeaderboardEntry.Builder()
                                        .targetId(fullTargetId)
                                        .completionTime(time)
                                        .worldSeed(obj.has("s") ? obj.get("s").getAsString() : "unknown")
                                        .modVersion("unknown")
                                        .attempts(1)
                                        .build();
                                    entry.setTargetId(fullTargetId);
                                    entry.setPlayerName(playerName);
                                    entry.setTimestamp(timestamp);
                                    
                                    if (entry.verifyIntegrity()) {
                                        entries.add(entry);
                                    }
                                } catch (Exception e) {
                                    RandomRunMod.LOGGER.warn("{} Batch ошибка парсинга {}", LOG_TAG, playerName);
                                }
                            }
                        }
                        
                        entries.sort(Comparator.comparingLong(LeaderboardEntry::getCompletionTime));
                        cache.put(fullTargetId, new CachedLeaderboard(entries));
                        
                        if (entries.size() > finalLimit) {
                            entries = entries.subList(0, finalLimit);
                        }
                        
                        allResults.put(fullTargetId, entries);
                    }
                }
                
                return allResults;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Batch ошибка: {}", LOG_TAG, e.getMessage());
                return new java.util.HashMap<>();
            }
        });
    }
    

    
    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES - 1) {
                    long delay = (long) Math.pow(2, attempt) * 1000;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
        }
        throw new RuntimeException("Retry failed", lastException);
    }
    

    

    
    private String sanitizeId(String id) {
        return id.replace(":", "_").replace("/", "_").replace(".", "_");
    }
    
    private void invalidateCache(String targetId) {
        cache.remove(targetId);
    }
    
    public void clearCache() {
        cache.clear();
    }
    
    private static class CachedLeaderboard {
        final List<LeaderboardEntry> entries;
        final long timestamp;
        
        CachedLeaderboard(List<LeaderboardEntry> entries) {
            this.entries = new ArrayList<>(entries);
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}

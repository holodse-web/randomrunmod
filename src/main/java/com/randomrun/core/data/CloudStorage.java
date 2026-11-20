package com.randomrun.core.data;

import com.randomrun.core.data.DataSyncService.StorageAdapter;
import com.randomrun.core.run.Run;
import com.randomrun.core.run.Run.RunResult;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.HttpClient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
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
                    RandomRunMod.LOGGER.warn("{} Empty history response for {}", LOG_TAG, playerName);
                    return new ArrayList<Run>();
                }
                
                if (result.isJsonObject() && result.getAsJsonObject().size() == 0) {
                    return new ArrayList<Run>();
                }
                
                return parseHistoryResponse(result, playerName);
            })
            .exceptionally(e -> {
                failedRequests.incrementAndGet();
                RandomRunMod.LOGGER.error("{} History load failed", LOG_TAG, e);
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
                            RandomRunMod.LOGGER.warn("{} Invalid record key: {}", LOG_TAG, recordId);
                        }
                        
                        Run record = parseRunRecord(recordElement.getAsJsonObject(), targetId, attemptNumber);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                } catch (Exception e) {
                    RandomRunMod.LOGGER.warn("{} Skipping run {}/{}: {}", LOG_TAG, compressedItemId, recordId, e.getMessage());
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
                    RandomRunMod.LOGGER.warn("{} Skipping run {}/[{}]: {}", LOG_TAG, compressedItemId, i, e.getMessage());
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
            
            Run record = new Run.Builder()
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
                .isUpgrader(obj.has("u") && obj.get("u").getAsBoolean())
                .isRandomDrop(obj.has("rd") && obj.get("rd").getAsBoolean())
                .attemptNumber(attemptNumber)
                .build();
            if (obj.has("sv") && obj.get("sv").getAsBoolean()) record.markSurvivor();
            return record;
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> saveRecord(String playerName, Run record) {
        return submitRecord(playerName, record).thenApply(status -> status != null).exceptionally(e -> false);
    }

    public CompletableFuture<JsonObject> submitRecord(String playerName, Run record) {
        if (record == null || record.isUpgrader() || !validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(null);
        }
        requestCount.incrementAndGet();
        String path = recordPath(playerName, record);
        return httpClient.request("PUT", path, buildRecordData(record), true)
            .thenApply(result -> {
                httpClient.clearCache();
                record.setProof(null);
                JsonObject status = result != null && result.isJsonObject() ? result.getAsJsonObject() : new JsonObject();
                record.setServerStatus(!status.has("counted") || status.get("counted").getAsBoolean(),
                    status.has("reason") ? status.get("reason").getAsString() : null);
                return status;
            })
            .exceptionally(e -> {
                failedRequests.incrementAndGet();
                RandomRunMod.LOGGER.warn("{} Run save failed: {}", LOG_TAG, e.getMessage());
                return null;
            });
    }

    private static String recordPath(String playerName, Run record) {
        String compressedItemId = com.randomrun.infrastructure.util.IdCompressor.compress(record.getTargetId());
        return "/profiles/" + playerName + "/history/" + compressedItemId + "/" + record.getAttemptNumber();
    }

    private static final java.nio.file.Path PENDING_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("randomrun").resolve("pending_runs.json");
    private static final Object PENDING_LOCK = new Object();
    private static final java.util.concurrent.atomic.AtomicBoolean FLUSHING = new java.util.concurrent.atomic.AtomicBoolean();

    public void queuePending(String playerName, Run record) {
        if (record == null || record.isUpgrader() || playerName == null) return;
        synchronized (PENDING_LOCK) {
            com.google.gson.JsonArray entries = readPending();
            JsonObject entry = new JsonObject();
            entry.addProperty("player", playerName);
            entry.addProperty("path", recordPath(playerName, record));
            entry.add("data", new com.google.gson.Gson().toJsonTree(buildRecordData(record)));
            entries.add(entry);
            while (entries.size() > 50) entries.remove(0);
            writePending(entries);
        }
        RandomRunMod.LOGGER.info("{} Run queued for upload", LOG_TAG);
    }

    public void flushPending() {
        var session = com.randomrun.network.BackendSession.current();
        if (session == null || !Files.exists(PENDING_FILE) || !FLUSHING.compareAndSet(false, true)) return;
        List<JsonObject> mine = new ArrayList<>();
        synchronized (PENDING_LOCK) {
            for (JsonElement element : readPending()) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.get("player").getAsString().equalsIgnoreCase(session.name())) mine.add(entry);
            }
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (JsonObject entry : mine) {
            String path = entry.get("path").getAsString().replaceFirst("^/profiles/[^/]+/", "/profiles/" + session.name() + "/");
            chain = chain.thenCompose(ignored -> session != com.randomrun.network.BackendSession.current()
                ? CompletableFuture.completedFuture(null)
                : httpClient.request("PUT", path, entry.get("data"), true).handle((result, error) -> {
                    Throwable cause = error instanceof java.util.concurrent.CompletionException ? error.getCause() : error;
                    boolean done = error == null || cause instanceof HttpClient.ApiException api && api.status() >= 400 && api.status() < 500 && api.status() != 401 && api.status() != 429;
                    if (done) removePending(entry);
                    return null;
                }));
        }
        chain.whenComplete((value, error) -> {
            FLUSHING.set(false);
            if (!mine.isEmpty()) httpClient.clearCache();
        });
    }

    private void removePending(JsonObject entry) {
        synchronized (PENDING_LOCK) {
            com.google.gson.JsonArray entries = readPending(), kept = new com.google.gson.JsonArray();
            for (JsonElement element : entries) if (!element.equals(entry)) kept.add(element);
            writePending(kept);
        }
    }

    private static com.google.gson.JsonArray readPending() {
        try {
            if (Files.exists(PENDING_FILE)) {
                JsonElement value = com.google.gson.JsonParser.parseString(Files.readString(PENDING_FILE));
                if (value.isJsonArray()) return value.getAsJsonArray();
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("{} Pending runs unreadable: {}", LOG_TAG, e.getMessage());
        }
        return new com.google.gson.JsonArray();
    }

    private static void writePending(com.google.gson.JsonArray entries) {
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(PENDING_FILE);
                return;
            }
            Files.createDirectories(PENDING_FILE.getParent());
            Files.writeString(PENDING_FILE, entries.toString());
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("{} Pending runs not saved: {}", LOG_TAG, e.getMessage());
        }
    }
    
    @Override
    public CompletableFuture<Boolean> saveHistory(String playerName, List<Run> records) {
        if (!validatePlayerName(playerName)) return CompletableFuture.completedFuture(false);
        var owner = com.randomrun.network.BackendSession.current();
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(true);
        for (Run record : records) {
            if (record.isUpgrader()) continue;
            result = result.thenCompose(saved -> !saved || owner != com.randomrun.network.BackendSession.current()
                ? CompletableFuture.completedFuture(false) : saveRecord(playerName, record));
        }
        return result;
    }
    
    private Map<String, Object> buildRecordData(Run record) {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(FIELD_DURATION, record.getDuration());
        recordData.put("rd", record.isRandomDrop());
        if (record.isSurvivor()) recordData.put("sv", true);
        recordData.put(FIELD_TIME, record.getTimestamp());
        recordData.put(FIELD_RESULT, record.getResult().getCode());
        var proof = record.getProof();
        if (proof != null) {
            recordData.put("rid", proof.id());
            recordData.put("rta", proof.rta());
        }
        recordData.put(FIELD_SEED, record.getWorldSeed() != null && !record.getWorldSeed().isEmpty() 
            ? record.getWorldSeed() : "0");
        
        if (!record.isBattle() && record.getCustomSeed() != null && !record.getCustomSeed().isEmpty()) {
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
                    RandomRunMod.LOGGER.warn("{} Storage unavailable", LOG_TAG);
                }
                return cachedAvailability;
            })
            .exceptionally(e -> {
                RandomRunMod.LOGGER.warn("{} Storage health check failed: {}", LOG_TAG, e.getMessage());
                cachedAvailability = false;
                lastAvailabilityCheck = System.currentTimeMillis();
                return false;
            });
    }
    
    @Override
    public String getType() {
        return "cloudflare";
    }
    
    @Override
    public CompletableFuture<Map<String, Integer>> getMetrics() {
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("requests", requestCount.get());
        metrics.put("failures", failedRequests.get());
        return CompletableFuture.completedFuture(metrics);
    }
    
    private boolean validatePlayerName(String playerName) {
        return com.randomrun.network.BackendSession.owns(playerName);
    }
}

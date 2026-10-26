package com.randomrun.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoomSerializer;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HttpClient {
    private static volatile HttpClient instance;
    
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer())
        .create();
    
    private static final long DEFAULT_CACHE_TTL_MS = 10000; 
    private static final long CACHE_CLEANUP_INTERVAL_MS = 60000; 
    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private static final int MAX_NETWORK_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors());
    
    private final java.net.http.HttpClient httpClient;
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleanupExecutor;

    private static final ExecutorService NETWORK_EXECUTOR = Executors.newFixedThreadPool(
        MAX_NETWORK_THREADS,
        r -> {
            Thread t = new Thread(r, "RandomRun-Network");
            t.setDaemon(true);
            return t;
        }
    );

    private HttpClient() {
        this.httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SEC))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .executor(NETWORK_EXECUTOR)
            .version(java.net.http.HttpClient.Version.HTTP_2)
            .build();

        this.cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RandomRun-CacheCleanup");
            t.setDaemon(true);
            return t;
        });
        
        cacheCleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredCache,
            CACHE_CLEANUP_INTERVAL_MS,
            CACHE_CLEANUP_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    private static class CachedResponse {
        final JsonElement data;
        final long timestamp;
        final long ttlMs;
        
        CachedResponse(JsonElement data, long ttlMs) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    public static synchronized HttpClient getInstance() {
        if (instance == null) {
            instance = new HttpClient();
        }
        return instance;
    }

    public CompletableFuture<JsonElement> get(String path) {
        return getCached(path, DEFAULT_CACHE_TTL_MS);
    }
    
    public CompletableFuture<JsonElement> getCached(String path, long cacheTtlMs) {
        if (!isOnlineMode() && !"/version".equals(path)) {
            return CompletableFuture.completedFuture(null);
        }

        if (cacheTtlMs > 0) {
            CachedResponse cached = responseCache.get(path);
            if (cached != null && !cached.isExpired()) {
                return CompletableFuture.completedFuture(cached.data);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = buildRequest(path)
                    .GET()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (isEmptyResponse(body)) {
                        return null;
                    }
                    
                    try {
                        JsonElement result = JsonParser.parseString(body);
                        if (cacheTtlMs > 0) {
                            responseCache.put(path, new CachedResponse(result, cacheTtlMs));
                        }
                        return result;
                    } catch (com.google.gson.JsonSyntaxException e) {
                        CompactLogger.warn("Invalid JSON response from GET {}", path);
                        return null;
                    }
                } else {
                    CompactLogger.networkError("GET {} failed: {}", path, response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                CompactLogger.networkError("GET {} error: {}", path, e.getMessage());
                return null;
            }
        }, NETWORK_EXECUTOR);
    }
    
    public CompletableFuture<JsonElement> getNoCache(String path) {
        return getCached(path, 0);
    }

    public CompletableFuture<Boolean> put(String path, Object data) {
        if (!isOnlineMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = GSON.toJson(data);
                HttpRequest request = buildRequest(path)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() == 200;
                
                if (!success) {
                    CompactLogger.networkError("PUT {} failed: {}", path, response.statusCode());
                }
                
                invalidateRelatedPaths(path);
                return success;
            } catch (Exception e) {
                CompactLogger.networkError("PUT {} error: {}", path, e.getMessage());
                return false;
            }
        }, NETWORK_EXECUTOR);
    }

    public CompletableFuture<Boolean> patch(String path, Object data) {
        if (!isOnlineMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = GSON.toJson(data);
                HttpRequest request = buildRequest(path)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() == 200;
                
                if (!success) {
                    CompactLogger.networkError("PATCH {} failed: {}", path, response.statusCode());
                }
                
                invalidateRelatedPaths(path);
                return success;
            } catch (Exception e) {
                CompactLogger.networkError("PATCH {} error: {}", path, e.getMessage());
                return false;
            }
        }, NETWORK_EXECUTOR);
    }

    public CompletableFuture<String> post(String path, Object data) {
        if (!isOnlineMode()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = GSON.toJson(data);
                HttpRequest request = buildRequest(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    invalidateRelatedPaths(path);
                    return response.body();
                } else {
                    CompactLogger.networkError("POST {} failed: {}", path, response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                CompactLogger.networkError("POST {} error: {}", path, e.getMessage());
                return null;
            }
        }, NETWORK_EXECUTOR);
    }

    public CompletableFuture<Boolean> delete(String path) {
        if (!isOnlineMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = buildRequest(path)
                    .DELETE()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                boolean success = response.statusCode() == 200 || response.statusCode() == 204;
                
                if (!success) {
                    CompactLogger.networkError("DELETE {} failed: {}", path, response.statusCode());
                }
                
                invalidateRelatedPaths(path);
                return success;
            } catch (Exception e) {
                CompactLogger.networkError("DELETE {} error: {}", path, e.getMessage());
                return false;
            }
        }, NETWORK_EXECUTOR);
    }
    
    public void invalidateCache(String path) {
        invalidateRelatedPaths(path);
    }
    
    public void clearCache() {
        responseCache.clear();
    }

    private void cleanupExpiredCache() {
        try {
            responseCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        } catch (Exception e) {
            CompactLogger.debug("Cache cleanup error: {}", e.getMessage());
        }
    }

    private void invalidateRelatedPaths(String path) {
        responseCache.remove(path);
        
        String[] parts = path.split("/");
        StringBuilder parentPath = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                parentPath.append("/").append(parts[i]);
                responseCache.remove(parentPath.toString());
            }
        }
    }
    
    public static ExecutorService getNetworkExecutor() {
        return NETWORK_EXECUTOR;
    }
    
    private boolean isOnlineMode() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode();
    }
    
    private boolean isEmptyResponse(String body) {
        return body == null || body.equals("null") || body.isEmpty();
    }
    
    private HttpRequest.Builder buildRequest(String path) {
        return buildRequest(path, Duration.ofSeconds(DEFAULT_TIMEOUT_SEC));
    }
    
    private HttpRequest.Builder buildRequest(String path, Duration timeout) {
        String firebaseUrl = FirebaseClient.DatabaseRouter.getUrlForPath(path);
        
        return HttpRequest.newBuilder()
            .uri(URI.create(firebaseUrl))
            .timeout(timeout);
    }
}

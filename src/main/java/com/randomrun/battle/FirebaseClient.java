package com.randomrun.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.main.RandomRunMod;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class FirebaseClient {
    private static FirebaseClient instance;
    
    // Placeholder for Firebase URL - user will need to configure this
    private static String FIREBASE_URL = "https://rrmmoddata0223-default-rtdb.europe-west1.firebasedatabase.app";
    private static String FIREBASE_SECRET = "jt7mkzJR7eTkKP5HflMOACShsvTUraalkO0dgPcG"; // Optional: Database Secret

    private static final Gson GSON = new GsonBuilder().create();
    private final HttpClient httpClient;

    private FirebaseClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public static FirebaseClient getInstance() {
        if (instance == null) {
            instance = new FirebaseClient();
        }
        return instance;
    }
    
    public void setUrl(String url) {
        if (url != null && !url.isEmpty()) {
            if (url.endsWith("/")) {
                FIREBASE_URL = url.substring(0, url.length() - 1);
            } else {
                FIREBASE_URL = url;
            }
        }
    }
    
    public void setSecret(String secret) {
        FIREBASE_SECRET = secret;
    }

    private String buildUrl(String path) {
        StringBuilder urlBuilder = new StringBuilder(FIREBASE_URL);
        if (!path.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(path);
        if (!path.endsWith(".json")) {
            urlBuilder.append(".json");
        }
        
        if (FIREBASE_SECRET != null && !FIREBASE_SECRET.isEmpty()) {
            urlBuilder.append("?auth=").append(FIREBASE_SECRET);
        }
        
        return urlBuilder.toString();
    }

    // ETag Cache (Path -> ETag)
    private final java.util.Map<String, String> etagCache = new java.util.concurrent.ConcurrentHashMap<>();

    // GET Request with ETag support option
    public CompletableFuture<JsonElement> get(String path) {
        return get(path, true); // Default to using cache for backward compatibility if needed, but safer to check usages
    }

    public CompletableFuture<JsonElement> get(String path, boolean useETag) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(path);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();
                
                // Add ETag if available for this path AND requested
                if (useETag) {
                    String cachedEtag = etagCache.get(path);
                    if (cachedEtag != null) {
                        requestBuilder.header("If-None-Match", cachedEtag);
                        requestBuilder.header("X-Firebase-ETag", "true");
                    }
                }

                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Handle 304 Not Modified
                if (response.statusCode() == 304) {
                    JsonObject noChange = new JsonObject();
                    noChange.addProperty("_etag_no_change", true);
                    return noChange;
                }

                if (response.statusCode() == 200) {
                    // Update ETag cache only if we used it or want to cache it
                    if (useETag) {
                        response.headers().firstValue("ETag").ifPresent(etag -> etagCache.put(path, etag));
                    }
                    
                    if (response.body() == null || response.body().equals("null")) {
                        return null;
                    }
                    return JsonParser.parseString(response.body());
                } else {
                    RandomRunMod.LOGGER.error("Firebase GET error: " + response.statusCode() + " " + response.body());
                    return null;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase GET exception: " + path, e);
                return null;
            }
        });
    }

    // PUT Request (Write/Replace)
    public CompletableFuture<Boolean> put(String path, Object data) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(path);
                String json = GSON.toJson(data);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                return response.statusCode() == 200;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase PUT exception: " + path, e);
                return false;
            }
        });
    }

    // PATCH Request (Update specific fields)
    public CompletableFuture<Boolean> patch(String path, Object data) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(path);
                String json = GSON.toJson(data);

                // Java HttpClient doesn't directly support PATCH in standard builder in older versions, 
                // but method("PATCH", ...) works.
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                return response.statusCode() == 200;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase PATCH exception: " + path, e);
                return false;
            }
        });
    }

    // POST Request (Push with auto-ID)
    public CompletableFuture<String> post(String path, Object data) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(path);
                String json = GSON.toJson(data);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (result.has("name")) {
                        return result.get("name").getAsString(); // Returns the generated ID
                    }
                }
                return null;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase POST exception: " + path, e);
                return null;
            }
        });
    }

    // DELETE Request
    public CompletableFuture<Boolean> delete(String path) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(path);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                return response.statusCode() == 200;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase DELETE exception: " + path, e);
                return false;
            }
        });
    }
}

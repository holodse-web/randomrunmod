package com.randomrun.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.RandomRunMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class FirebaseClient {
    private static FirebaseClient instance;
    private static final String FIREBASE_URL = "https://rrmmod-f67df-default-rtdb.europe-west1.firebasedatabase.app";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final HttpClient httpClient;
    
    private FirebaseClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public static FirebaseClient getInstance() {
        if (instance == null) {
            instance = new FirebaseClient();
        }
        return instance;
    }
    
    public CompletableFuture<JsonObject> get(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FIREBASE_URL + path + ".json"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body == null || body.equals("null")) {
                        return null;
                    }
                    return JsonParser.parseString(body).getAsJsonObject();
                } else {
                    RandomRunMod.LOGGER.error("Firebase GET failed: " + response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase GET error: " + path, e);
                return null;
            }
        });
    }
    
    public CompletableFuture<Boolean> put(String path, Object data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = GSON.toJson(data);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FIREBASE_URL + path + ".json"))
                    .timeout(Duration.ofSeconds(10))
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    RandomRunMod.LOGGER.info("Firebase PUT success: " + path);
                    return true;
                } else {
                    RandomRunMod.LOGGER.error("Firebase PUT failed: " + response.statusCode());
                    return false;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase PUT error: " + path, e);
                return false;
            }
        });
    }
    
    public CompletableFuture<Boolean> patch(String path, Object data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = GSON.toJson(data);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FIREBASE_URL + path + ".json"))
                    .timeout(Duration.ofSeconds(10))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    RandomRunMod.LOGGER.info("Firebase PATCH success: " + path);
                    return true;
                } else {
                    RandomRunMod.LOGGER.error("Firebase PATCH failed: " + response.statusCode());
                    return false;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase PATCH error: " + path, e);
                return false;
            }
        });
    }
    
    public CompletableFuture<Boolean> delete(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FIREBASE_URL + path + ".json"))
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    RandomRunMod.LOGGER.info("Firebase DELETE success: " + path);
                    return true;
                } else {
                    RandomRunMod.LOGGER.error("Firebase DELETE failed: " + response.statusCode());
                    return false;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Firebase DELETE error: " + path, e);
                return false;
            }
        });
    }
}

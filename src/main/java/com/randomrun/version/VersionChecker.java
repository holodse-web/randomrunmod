package com.randomrun.version;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.RandomRunMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class VersionChecker {
    private static VersionChecker instance;
    private static final String FIREBASE_URL = "https://rrmmod-f67df-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String CURRENT_VERSION = "26.5 BETA";
    
    private final HttpClient httpClient;
    private String latestVersion = null;
    private boolean updateRequired = false;
    private boolean checkCompleted = false;
    
    // Нормализация версии для сравнения (убираем BETA, ALPHA и т.д.)
    private String normalizeVersion(String version) {
        if (version == null) return "";
        return version.replaceAll("(?i)\\s*(BETA|ALPHA|RC|SNAPSHOT).*$", "").trim();
    }
    
    private VersionChecker() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        // Сразу проверяем версию при создании
        checkVersionSync();
    }
    
    public static VersionChecker getInstance() {
        if (instance == null) {
            instance = new VersionChecker();
        }
        return instance;
    }
    
    // Синхронная проверка версии
    private void checkVersionSync() {
        try {
            RandomRunMod.LOGGER.info("Starting version check...");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(FIREBASE_URL + "/version.json"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                latestVersion = json.get("latest").getAsString();
                
                // Сравниваем нормализованные версии (без BETA, ALPHA и т.д.)
                String normalizedCurrent = normalizeVersion(CURRENT_VERSION);
                String normalizedLatest = normalizeVersion(latestVersion);
                updateRequired = !normalizedCurrent.equals(normalizedLatest);
                checkCompleted = true;
                
                RandomRunMod.LOGGER.info("Version check completed!");
                RandomRunMod.LOGGER.info("Current: " + CURRENT_VERSION + " (normalized: " + normalizedCurrent + ")");
                RandomRunMod.LOGGER.info("Latest: " + latestVersion + " (normalized: " + normalizedLatest + ")");
                RandomRunMod.LOGGER.info("Update required: " + updateRequired);
            } else {
                RandomRunMod.LOGGER.warn("Version check failed with status code: " + response.statusCode());
                checkCompleted = true;
                updateRequired = false;
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to check version: " + e.getMessage());
            checkCompleted = true;
            updateRequired = false; // Если проверка не удалась, разрешаем доступ
        }
    }
    
    public CompletableFuture<Boolean> checkVersion() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FIREBASE_URL + "/version.json"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    latestVersion = json.get("latest").getAsString();
                    
                    // Сравниваем нормализованные версии (без BETA, ALPHA и т.д.)
                    String normalizedCurrent = normalizeVersion(CURRENT_VERSION);
                    String normalizedLatest = normalizeVersion(latestVersion);
                    updateRequired = !normalizedCurrent.equals(normalizedLatest);
                    checkCompleted = true;
                    
                    RandomRunMod.LOGGER.info("Version check: Current=" + CURRENT_VERSION + " (" + normalizedCurrent + "), Latest=" + latestVersion + " (" + normalizedLatest + ")");
                    return updateRequired;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to check version", e);
                checkCompleted = true;
                updateRequired = false; // Если проверка не удалась, разрешаем доступ
            }
            return false;
        });
    }
    
    public boolean isUpdateRequired() {
        return updateRequired;
    }
    
    public boolean isCheckCompleted() {
        return checkCompleted;
    }
    
    public String getCurrentVersion() {
        return CURRENT_VERSION;
    }
    
    public String getLatestVersion() {
        return latestVersion != null ? latestVersion : CURRENT_VERSION;
    }
}

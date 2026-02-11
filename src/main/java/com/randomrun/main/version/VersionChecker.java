package com.randomrun.main.version;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.main.RandomRunMod;
import com.randomrun.battle.FirebaseClient;

import java.util.concurrent.CompletableFuture;

public class VersionChecker {
    private static VersionChecker instance;
    // Текущая версия мода
    private static final String CURRENT_VERSION = "26.7 BETA";
    
    private String latestVersion = null;
    private boolean updateRequired = false;
    private boolean checkCompleted = false;
    
    // Нормализация версии для сравнения (убираем BETA, ALPHA и т.д.)
    private String normalizeVersion(String version) {
        if (version == null) return "";
        return version.replaceAll("(?i)\\s*(BETA|ALPHA|RC|SNAPSHOT).*$", "").trim();
    }
    
    private VersionChecker() {
        // Run check asynchronously to prevent startup freeze
        if (RandomRunMod.getInstance().getConfig() != null) {
            checkVersion();
        } else {
            RandomRunMod.LOGGER.warn("Config not initialized, skipping initial version check");
        }
    }
    
    public static VersionChecker getInstance() {
        if (instance == null) {
            instance = new VersionChecker();
        }
        return instance;
    }
    
    // Синхронная проверка версии удалена для оптимизации запуска
    
    public CompletableFuture<Boolean> checkVersion() {
        return FirebaseClient.getInstance().get("/version").thenApply(dataElement -> {
            try {
                if (dataElement != null && dataElement.isJsonObject()) {
                    JsonObject data = dataElement.getAsJsonObject();
                    if (data.has("latest")) {
                        latestVersion = data.get("latest").getAsString();
                        
                        String normalizedCurrent = normalizeVersion(CURRENT_VERSION);
                        String normalizedLatest = normalizeVersion(latestVersion);
                        
                        // Use semantic version comparison instead of string equality
                        // This handles cases like 26.6 < 26.9 correctly
                        updateRequired = compareVersions(normalizedCurrent, normalizedLatest) < 0;
                        checkCompleted = true;
                        
                        RandomRunMod.LOGGER.info("Проверка версии (Асинхронно): Текущая=" + CURRENT_VERSION + ", Последняя=" + latestVersion + ", Обновление=" + updateRequired);
                        return updateRequired;
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Ошибка асинхронной проверки версии", e);
            }
            checkCompleted = true;
            return false;
        });
    }
    
    // Returns negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }
    
    private int parseVersionPart(String part) {
        try {
            // Remove non-numeric characters just in case
            return Integer.parseInt(part.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
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

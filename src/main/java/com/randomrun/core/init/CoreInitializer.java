package com.randomrun.core.init;

import com.randomrun.core.service.DataSyncService;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.network.FirebaseClient.AuthManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoreInitializer {
    private static final String LOG_TAG = "[CoreInit]";
    private static final int INIT_TIMEOUT_SECONDS = 30;
    
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile CompletableFuture<Void> initializationFuture = null;
    
    public static CompletableFuture<Void> initialize(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            CompactLogger.error("{} Имя игрока пустое", LOG_TAG);
            return CompletableFuture.failedFuture(new IllegalArgumentException("playerName не может быть пустым"));
        }
        
        synchronized (CoreInitializer.class) {
            if (initialized.get()) {
                return CompletableFuture.completedFuture(null);
            }
            
            if (initializationFuture != null && !initializationFuture.isDone()) {
                return initializationFuture;
            }
            
            initializationFuture = CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                
                try {
                    AuthManager.initialize();
                    
                    if (!AuthManager.isInitialized() && RandomRunMod.getInstance().getConfig().isOnlineMode()) {
                        CompactLogger.error("{} Ошибка авторизации", LOG_TAG);
                        throw new RuntimeException("Не удалось инициализировать Firebase подключение");
                    }
                    
                    DataSyncService dataSyncService = DataSyncService.getInstance();
                    CompletableFuture<Boolean> loadFuture = dataSyncService.loadPlayerData(playerName);
                    boolean loadSuccess = loadFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    
                    if (!loadSuccess) {
                        CompactLogger.error("{} Ошибка загрузки данных", LOG_TAG);
                        throw new RuntimeException("Не удалось загрузить данные игрока");
                    }
                    
                    long duration = System.currentTimeMillis() - startTime;
                    initialized.set(true);
                } catch (TimeoutException e) {
                    CompactLogger.error("{} Таймаут инициализации ({}s)", LOG_TAG, INIT_TIMEOUT_SECONDS);
                    resetOnError();
                    throw new RuntimeException("Таймаут инициализации Core", e);
                } catch (Exception e) {
                    CompactLogger.error("{} Ошибка запуска: {}", LOG_TAG, e.getMessage());
                    resetOnError();
                    throw new RuntimeException("Не удалось инициализировать Core", e);
                }
            });
            
            return initializationFuture;
        }
    }
    
    public static boolean isInitialized() {
        return initialized.get();
    }
    
    private static void resetOnError() {
        synchronized (CoreInitializer.class) {
            initializationFuture = null;
        }
    }
    
    public static void reset() {
        synchronized (CoreInitializer.class) {
            if (initialized.get()) {
                initialized.set(false);
                initializationFuture = null;
            }
        }
    }
}

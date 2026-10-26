package com.randomrun.features.battle.screen.lobby.util.helper;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class PingMonitor {
    private static final long PING_INTERVAL = 30000;
    
    private volatile int currentPing = 0;
    private long lastPingTime = 0;
    private volatile boolean isUpdating = false;
    
    public void update() {
        if (System.currentTimeMillis() - lastPingTime > PING_INTERVAL) {
            measurePing();
            lastPingTime = System.currentTimeMillis();
        }
    }
    
    public void forceUpdate() {
        if (!isUpdating) {
            measurePing();
            lastPingTime = System.currentTimeMillis();
        }
    }
    
    private synchronized void measurePing() {
        if (isUpdating) return;
        
        isUpdating = true;
        CompletableFuture.runAsync(() -> {
            HttpURLConnection conn = null;
            try {
                long startTime = System.currentTimeMillis();
                conn = (HttpURLConnection) URI.create(com.randomrun.network.FirebaseClient.getProxyUrl("/stats/global")).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + com.randomrun.network.FirebaseClient.getProxyToken());
                conn.setRequestProperty("X-Player-UUID",
                    com.randomrun.infrastructure.auth.PlayerUuidManager.getInstance().getPlayerUuid());
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                long endTime = System.currentTimeMillis();
                
                if (responseCode == 200) {
                    currentPing = (int)(endTime - startTime);
                } else {
                    currentPing = -1;
                }
            } catch (Exception e) {
                currentPing = -1;
            } finally {
                if (conn != null) conn.disconnect();
                isUpdating = false;
            }
        });
    }
    
    public int getCurrentPing() {
        return currentPing;
    }
    
    public int getPingColor() {
        if (currentPing == -1) {
            return UIColors.PING_ERROR;
        } else if (currentPing == 0) {
            return UIColors.PING_LOADING;
        } else if (currentPing <= 300) {
            return 0xFF55FF55;
        } else if (currentPing <= 600) {
            return 0xFFFFFF55;
        } else {
            return 0xFFFF5555;
        }
    }
    
    public String getPingText() {
        if (currentPing == -1) {
            return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.ping_error").getString();
        } else if (currentPing == 0) {
            return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.ping_loading").getString();
        } else {
            return currentPing + "ms";
        }
    }
    
    public boolean isUpdating() {
        return isUpdating;
    }
}

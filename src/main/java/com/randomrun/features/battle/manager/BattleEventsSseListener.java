package com.randomrun.features.battle.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.network.FirebaseClient.FirebaseSseClient;

import java.util.concurrent.ScheduledExecutorService;

public class BattleEventsSseListener {
    private static final String LOG_TAG = "[BattleEvents]";
    private FirebaseSseClient sseClient;
    private volatile boolean active = false;
    private String currentRoomId;
    private long generation;
    private BattleEventState eventState = new BattleEventState();
    private final ScheduledExecutorService scheduler;
    
    public BattleEventsSseListener(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    public synchronized void start(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        
        stop();
        
        this.currentRoomId = roomId;
        this.active = true;
        this.eventState = new BattleEventState();
        final long expectedGeneration = ++generation;
        
        try {
            String eventsPath = roomId + "/events";
            sseClient = new FirebaseSseClient(
                eventsPath,
                (event, data) -> handleEvent(expectedGeneration, event, data),
                error -> { synchronized (this) { if (generation == expectedGeneration) handleError(error); } },
                () -> { synchronized (this) { if (generation == expectedGeneration) handleDisconnect(); } }
            );
            sseClient.connect();
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка запуска слушателя событий: {}", LOG_TAG, e.getMessage());
            active = false;
        }
    }
    
    public synchronized void stop() {
        generation++;
        active = false;
        
        if (sseClient != null) {
            sseClient.shutdown();
            sseClient = null;
        }
        
        currentRoomId = null;
    }
    
    public boolean isConnected() {
        return sseClient != null && sseClient.isConnected();
    }
    
    private synchronized void handleEvent(long expectedGeneration, String eventType, String data) {
        if (!active || generation != expectedGeneration || data == null || data.isEmpty()
                || !FirebaseSseClient.EVENT_PUT.equals(eventType) && !FirebaseSseClient.EVENT_PATCH.equals(eventType)) {
            return;
        }
        
        try {
            JsonElement element = JsonParser.parseString(data);
            if (!element.isJsonObject()) {
                return;
            }
            
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("data") || !obj.has("path")) {
                return;
            }
            
            for (JsonObject next : eventState.apply(eventType, obj.get("path").getAsString(), obj.get("data"))) {
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    synchronized (this) {
                        if (active && generation == expectedGeneration) processEvent(next);
                    }
                });
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка обработки события: {}", LOG_TAG, e.getMessage());
        }
    }
    
    private void processEvent(JsonObject event) {
        try {
            if (!event.has("type") || !event.has("player")) {
                return;
            }
            
            String type = event.get("type").getAsString();
            String playerName = event.get("player").getAsString();
            
            String myName = net.minecraft.client.Minecraft.getInstance().getUser().getName();
            if (playerName.equals(myName)) {
                return;
            }
            
            switch (type) {
                case "ACHIEVEMENT" -> {
                    if (event.has("aid")) {
                        String compressedId = event.get("aid").getAsString();
                        String achievementId = IdCompressor.decompress(compressedId);
                        handleAchievementEvent(playerName, achievementId);
                    }
                }
                case "DEATH" -> {
                }
                case "ELIMINATION" -> {
                }
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка обработки типа события: {}", LOG_TAG, e.getMessage());
        }
    }
    
    private void handleAchievementEvent(String playerName, String achievementId) {
        long expectedGeneration = generation;
        String roomId = currentRoomId;
        var client = net.minecraft.client.Minecraft.getInstance();
        com.randomrun.features.battle.util.advancement.AdvancementLoader.getInfoAsync(achievementId)
            .thenAccept(info -> client.execute(() -> {
                synchronized (this) {
                    if (!active || generation != expectedGeneration || !java.util.Objects.equals(roomId, currentRoomId)) return;
                    if (info == null) {
                        return;
                    }
                    String iconId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(info.icon.getItem()).toString();
                    com.randomrun.features.battle.util.advancement.OpponentAchievementHud.show(playerName, info.title.getString(), iconId);
                }
            })).exceptionally(error -> {
                return null;
            });
    }
    
    private void handleError(Exception e) {
        if (!active) return;
        RandomRunMod.LOGGER.warn("{} Ошибка подключения: {}", LOG_TAG, e.getMessage());
    }
    
    private void handleDisconnect() {
        if (!active) return;
        RandomRunMod.LOGGER.warn("{} Подключение разорвано", LOG_TAG);
    }
}

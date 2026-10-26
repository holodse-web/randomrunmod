package com.randomrun.features.battle.screen.lobby.util.helper;

import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoomSerializer;
import com.randomrun.network.HttpClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LobbyPollingManager {
    private static final String LOG_TAG = "[LobbyPoller]";
    private static final long MIN_POLL_INTERVAL_MS = 2000;
    private static final long MAX_POLL_INTERVAL_MS = 10000;
    private static final int UNCHANGED_THRESHOLD = 5;
    private static final long INTERVAL_INCREMENT_MS = 1000;
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollingTask;
    private int lastDataHash = 0;
    private long currentInterval = MIN_POLL_INTERVAL_MS;
    private int unchangedCount = 0;
    private boolean firstSuccessfulPoll = false;
    private volatile boolean isPaused = false;
    private boolean active;
    private long generation;
    
    private final Consumer<List<BattleRoom>> onRoomsUpdate;
    private final Consumer<ConnectionStatus> onStatusChange;
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    
    public enum ConnectionStatus {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }
    
    public LobbyPollingManager(Consumer<List<BattleRoom>> onRoomsUpdate, 
                               Consumer<ConnectionStatus> onStatusChange) {
        this.onRoomsUpdate = onRoomsUpdate;
        this.onStatusChange = onStatusChange;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LobbyPoller");
            t.setDaemon(true);
            return t;
        });
    }
    
    public synchronized void connect() {
        if (active || scheduler.isShutdown()) return;
        
        if (!com.randomrun.network.FirebaseClient.AuthManager.isInitialized()) {
            com.randomrun.infrastructure.RandomRunMod.LOGGER.error("{} AuthManager не инициализирован!", LOG_TAG);
            updateStatus(ConnectionStatus.ERROR);
            return;
        }
        
        updateStatus(ConnectionStatus.CONNECTING);
        active = true;
        generation++;
        lastDataHash = 0;
        firstSuccessfulPoll = false;
        currentInterval = MIN_POLL_INTERVAL_MS;
        unchangedCount = 0;
        
        scheduleNextPoll(0, generation);
    }
    
    private synchronized void scheduleNextPoll(long delay, long expectedGeneration) {
        if (!active || generation != expectedGeneration || scheduler.isShutdown()) return;
        pollingTask = scheduler.schedule(() -> {
            synchronized (this) {
                if (!active || generation != expectedGeneration) return;
            }
            if (isPaused && firstSuccessfulPoll) {
                scheduleNextPoll(currentInterval, expectedGeneration);
                return;
            }
            
            HttpClient.getInstance().getNoCache("/rooms")
                .thenAccept(data -> {
                    synchronized (this) {
                    if (!active || generation != expectedGeneration) return;
                    if (data != null) {
                        int currentHash = data.toString().hashCode();
                        
                        if (currentHash != lastDataHash) {
                            lastDataHash = currentHash;
                            unchangedCount = 0;
                            currentInterval = MIN_POLL_INTERVAL_MS;
                            handleData(data);
                        } else {
                            unchangedCount++;
                            if (unchangedCount >= UNCHANGED_THRESHOLD) {
                                currentInterval = Math.min(MAX_POLL_INTERVAL_MS, 
                                    currentInterval + INTERVAL_INCREMENT_MS);
                            }
                        }
                        
                        if (!firstSuccessfulPoll) {
                            firstSuccessfulPoll = true;
                            updateStatus(ConnectionStatus.CONNECTED);
                        }
                    } else {
                        onRoomsUpdate.accept(new ArrayList<>());
                    }
                    updateStatus(ConnectionStatus.CONNECTED);
                    
                    scheduleNextPoll(currentInterval, expectedGeneration);
                    }
                })
                .exceptionally(e -> {
                    synchronized (this) {
                    if (!active || generation != expectedGeneration) return null;
                    if (status != ConnectionStatus.ERROR) com.randomrun.infrastructure.RandomRunMod.LOGGER.warn("{} Ошибка запроса: {}", LOG_TAG, e.getMessage());
                    updateStatus(ConnectionStatus.ERROR);
                    
                    scheduleNextPoll(currentInterval, expectedGeneration);
                    }
                    return null;
                });
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    private void handleData(JsonElement element) {
        try {
            List<BattleRoom> rooms = new ArrayList<>();
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer())
                .create();
            
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                
                for (var entry : obj.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && !value.isJsonNull() && value.isJsonObject()) {
                        BattleRoom room = gson.fromJson(value, BattleRoom.class);
                        room.setRoomCode(entry.getKey());
                        rooms.add(room);
                    }
                }
            } else if (element.isJsonArray()) {
                for (JsonElement el : element.getAsJsonArray()) {
                    if (el != null && !el.isJsonNull() && el.isJsonObject()) {
                        rooms.add(gson.fromJson(el, BattleRoom.class));
                    }
                }
            }
            
            onRoomsUpdate.accept(rooms);
        } catch (Exception e) {
            com.randomrun.infrastructure.RandomRunMod.LOGGER.error("{} Ошибка парсинга: {}", 
                LOG_TAG, e.getMessage());
            onRoomsUpdate.accept(new ArrayList<>());
        }
    }
    
    private void updateStatus(ConnectionStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            onStatusChange.accept(newStatus);
        }
    }
    
    public synchronized void disconnect() {
        active = false;
        generation++;
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        lastDataHash = 0;
        unchangedCount = 0;
        currentInterval = MIN_POLL_INTERVAL_MS;
        firstSuccessfulPoll = false;
        updateStatus(ConnectionStatus.DISCONNECTED);
    }
    
    public ConnectionStatus getStatus() {
        return status;
    }
    
    public synchronized boolean isConnected() {
        return active;
    }
    
    public void pause() {
        if (!isPaused) {
            isPaused = true;
        }
    }
    
    public void resume() {
        if (isPaused) {
            isPaused = false;
        }
    }
    
    public void shutdown() {
        disconnect();
        scheduler.shutdownNow();
    }
}

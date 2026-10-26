package com.randomrun.features.battle.screen.lobby.util.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoomSerializer;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.FirebaseClient.FirebaseSseClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LobbySseListener {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer()).create();
    private static final String LOG_TAG = "[LobbySSE]";
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private final Consumer<List<BattleRoom>> onRoomsUpdate;
    private final Consumer<ConnectionStatus> onStatusChange;
    private final Runnable onReconnectFailed;
    private final ScheduledExecutorService scheduler;
    private FirebaseSseClient sseClient;
    private ScheduledFuture<?> reconnectTask;
    private boolean active;
    private long generation;
    private int reconnectAttempts;
    private JsonObject state = new JsonObject();
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    public enum ConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

    public LobbySseListener(Consumer<List<BattleRoom>> onRoomsUpdate,
                            Consumer<ConnectionStatus> onStatusChange,
                            Runnable onReconnectFailed, ScheduledExecutorService scheduler) {
        this.onRoomsUpdate = onRoomsUpdate;
        this.onStatusChange = onStatusChange;
        this.onReconnectFailed = onReconnectFailed;
        this.scheduler = scheduler;
    }

    public synchronized void start() {
        if (active || scheduler.isShutdown()) return;
        active = true;
        reconnectAttempts = 0;
        state = new JsonObject();
        updateStatus(ConnectionStatus.CONNECTING);
        loadAndConnect(++generation);
    }

    private void loadAndConnect(long expectedGeneration) {
        com.randomrun.network.HttpClient.getInstance().getNoCache("/rooms")
            .whenComplete((data, error) -> {
                synchronized (this) {
                    if (!isCurrent(expectedGeneration)) return;
                    if (error == null) {
                        state = data != null && data.isJsonObject() ? data.getAsJsonObject().deepCopy() : new JsonObject();
                        onRoomsUpdate.accept(parseRooms(state));
                    }
                    connectInternal(expectedGeneration);
                }
            });
    }

    private boolean isCurrent(long expectedGeneration) {
        return active && generation == expectedGeneration && !scheduler.isShutdown();
    }

    private void connectInternal(long expectedGeneration) {
        if (!isCurrent(expectedGeneration)) return;
        closeStream();
        try {
            sseClient = new FirebaseSseClient("rooms",
                (event, data) -> handleEvent(expectedGeneration, event, data),
                error -> connectionFailed(expectedGeneration, error),
                () -> connectionFailed(expectedGeneration, null));
            sseClient.connect();
        } catch (Exception error) {
            connectionFailed(expectedGeneration, error);
        }
    }

    private synchronized void handleEvent(long expectedGeneration, String event, String data) {
        if (!isCurrent(expectedGeneration)) return;
        if (FirebaseSseClient.EVENT_KEEP_ALIVE.equals(event)) {
            updateStatus(ConnectionStatus.CONNECTED);
            return;
        }
        if (!FirebaseSseClient.EVENT_PUT.equals(event) && !FirebaseSseClient.EVENT_PATCH.equals(event)) return;
        try {
            JsonElement parsed = JsonParser.parseString(data);
            if (!parsed.isJsonObject()) return;
            JsonObject payload = parsed.getAsJsonObject();
            if (!payload.has("path") || !payload.has("data")) return;
            JsonObject updated = LobbyRoomState.apply(state, event, payload.get("path").getAsString(), payload.get("data"));
            if (!updated.equals(state)) {
                state = updated;
                onRoomsUpdate.accept(parseRooms(state));
            }
            reconnectAttempts = 0;
            updateStatus(ConnectionStatus.CONNECTED);
        } catch (Exception error) {
            RandomRunMod.LOGGER.error("{} Некорректное обновление списка комнат", LOG_TAG, error);
        }
    }

    private synchronized void connectionFailed(long expectedGeneration, Exception error) {
        if (!isCurrent(expectedGeneration)) return;
        long nextGeneration = ++generation;
        closeStream();
        if (++reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            RandomRunMod.LOGGER.error("{} Не удалось восстановить список комнат", LOG_TAG, error);
            updateStatus(ConnectionStatus.ERROR);
            if (onReconnectFailed != null) onReconnectFailed.run();
            return;
        }
        updateStatus(ConnectionStatus.CONNECTING);
        long delay = Math.min(2000L << (reconnectAttempts - 1), 30000L);
        reconnectTask = scheduler.schedule(() -> {
            synchronized (this) {
                if (isCurrent(nextGeneration)) loadAndConnect(nextGeneration);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private List<BattleRoom> parseRooms(JsonObject roomsData) {
        List<BattleRoom> rooms = new ArrayList<>();
        for (var entry : roomsData.entrySet()) {
            if (!entry.getKey().matches("[0-9]{5}") || !entry.getValue().isJsonObject()) continue;
            try {
                BattleRoom room = GSON.fromJson(entry.getValue(), BattleRoom.class);
                room.setRoomCode(entry.getKey());
                rooms.add(room);
            } catch (Exception error) {
                RandomRunMod.LOGGER.warn("{} Некорректная комната {}: {}", LOG_TAG, entry.getKey(), error.getMessage());
            }
        }
        return List.copyOf(rooms);
    }

    private void updateStatus(ConnectionStatus next) {
        if (status == next) return;
        status = next;
        if (onStatusChange != null) onStatusChange.accept(next);
    }

    private void closeStream() {
        if (sseClient != null) {
            FirebaseSseClient previous = sseClient;
            sseClient = null;
            previous.shutdown();
        }
    }

    public synchronized void stop() {
        active = false;
        generation++;
        if (reconnectTask != null) reconnectTask.cancel(false);
        reconnectTask = null;
        closeStream();
        scheduler.shutdownNow();
        state = new JsonObject();
        updateStatus(ConnectionStatus.DISCONNECTED);
    }

    public synchronized boolean isConnected() { return active && sseClient != null && sseClient.isConnected(); }
    public synchronized boolean isHealthy() { return isConnected(); }
    public synchronized ConnectionStatus getStatus() { return active ? status : ConnectionStatus.DISCONNECTED; }
}

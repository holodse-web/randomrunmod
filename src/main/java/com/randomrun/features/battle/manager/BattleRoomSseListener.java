package com.randomrun.features.battle.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.features.battle.screen.lobby.util.helper.LobbyRoomState;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.FirebaseClient.FirebaseSseClient;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class BattleRoomSseListener {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer()).create();
    private static final String LOG_TAG = "[BattleSSE]";
    private final Consumer<BattleRoom> onRoomUpdate;
    private final Runnable onReconnectFailed;
    private final ScheduledExecutorService scheduler;
    private FirebaseSseClient sseClient;
    private ScheduledFuture<?> reconnectTask;
    private String currentRoomId;
    private JsonObject state;
    private boolean active, snapshotLoading;
    private long generation, connectionGeneration, lastActivityTime;
    private int reconnectAttempts;

    public BattleRoomSseListener(Consumer<BattleRoom> onRoomUpdate, Runnable onReconnectFailed,
                                 ScheduledExecutorService scheduler) {
        this.onRoomUpdate = onRoomUpdate;
        this.onReconnectFailed = onReconnectFailed;
        this.scheduler = scheduler;
    }

    public synchronized void start(String roomId) {
        if (roomId == null || roomId.isBlank() || scheduler.isShutdown()) return;
        if (active && roomId.equals(currentRoomId)) return;
        stop();
        currentRoomId = roomId;
        active = true;
        loadAndConnect(generation);
    }

    public synchronized void stop() {
        active = false;
        generation++;
        connectionGeneration++;
        if (reconnectTask != null) reconnectTask.cancel(false);
        reconnectTask = null;
        closeStream();
        currentRoomId = null;
        state = null;
        snapshotLoading = false;
        reconnectAttempts = 0;
        lastActivityTime = 0;
    }

    private boolean isCurrent(long expectedGeneration) {
        return active && generation == expectedGeneration && !scheduler.isShutdown();
    }

    private void loadAndConnect(long expectedGeneration) {
        if (!isCurrent(expectedGeneration) || snapshotLoading) return;
        snapshotLoading = true;
        String roomId = currentRoomId;
        BattleApiHelper.INSTANCE.getRoomData(roomId).whenComplete((room, error) -> {
            synchronized (this) {
                if (!isCurrent(expectedGeneration)) return;
                snapshotLoading = false;
                if (error != null || room == null) {
                    queueReconnect(expectedGeneration);
                    return;
                }
                state = GSON.toJsonTree(room).getAsJsonObject();
                lastActivityTime = System.currentTimeMillis();
                publish(expectedGeneration, room);
                connectInternal(expectedGeneration);
            }
        });
    }

    private void connectInternal(long expectedGeneration) {
        if (!isCurrent(expectedGeneration)) return;
        long connection = ++connectionGeneration;
        closeStream();
        try {
            sseClient = new FirebaseSseClient(currentRoomId,
                (event, data) -> handleEvent(expectedGeneration, connection, event, data),
                error -> connectionFailed(expectedGeneration, connection),
                () -> connectionFailed(expectedGeneration, connection));
            sseClient.connect();
        } catch (Exception error) {
            queueReconnect(expectedGeneration);
        }
    }

    private synchronized void handleEvent(long expectedGeneration, long connection, String event, String data) {
        if (!isCurrent(expectedGeneration) || connection != connectionGeneration) return;
        lastActivityTime = System.currentTimeMillis();
        if (FirebaseSseClient.EVENT_KEEP_ALIVE.equals(event)) {
            reconnectAttempts = 0;
            return;
        }
        if (!FirebaseSseClient.EVENT_PUT.equals(event) && !FirebaseSseClient.EVENT_PATCH.equals(event)) return;
        try {
            JsonElement parsed = JsonParser.parseString(data);
            if (!parsed.isJsonObject()) return;
            JsonObject envelope = parsed.getAsJsonObject();
            if (!envelope.has("path") || !envelope.has("data")) return;
            state = LobbyRoomState.apply(state, event, envelope.get("path").getAsString(), envelope.get("data"));
            if (state.size() == 0) {
                queueReconnect(expectedGeneration);
                return;
            }
            BattleRoom room = GSON.fromJson(state, BattleRoom.class);
            if (room != null) {
                reconnectAttempts = 0;
                publish(expectedGeneration, room);
            }
        } catch (Exception error) {
            RandomRunMod.LOGGER.warn("{} Invalid room update: {}", LOG_TAG, error.getMessage());
            queueReconnect(expectedGeneration);
        }
    }

    private void publish(long expectedGeneration, BattleRoom room) {
        room.setRoomCode(currentRoomId);
        Minecraft.getInstance().schedule(() -> {
            synchronized (this) {
                if (!isCurrent(expectedGeneration)) return;
            }
            onRoomUpdate.accept(room);
        });
    }

    private synchronized void connectionFailed(long expectedGeneration, long connection) {
        if (isCurrent(expectedGeneration) && connection == connectionGeneration) queueReconnect(expectedGeneration);
    }

    private void queueReconnect(long expectedGeneration) {
        if (!isCurrent(expectedGeneration) || snapshotLoading
                || reconnectTask != null && !reconnectTask.isDone()) return;
        connectionGeneration++;
        closeStream();
        int attempt = ++reconnectAttempts;
        if (attempt > BattleConstants.SSE_MAX_RECONNECT_ATTEMPTS) {
            active = false;
            RandomRunMod.LOGGER.warn("{} Reconnect failed; using room polling", LOG_TAG);
            Minecraft.getInstance().schedule(() -> {
                synchronized (this) {
                    if (generation != expectedGeneration || active || onReconnectFailed == null) return;
                }
                onReconnectFailed.run();
            });
            return;
        }
        long delay = Math.min(BattleConstants.SSE_RECONNECT_DELAY_MS * (1L << Math.min(attempt - 1, 20)),
            BattleConstants.SSE_RECONNECT_MAX_DELAY_MS);
        reconnectTask = scheduler.schedule(() -> {
            synchronized (this) {
                if (!isCurrent(expectedGeneration)) return;
                reconnectTask = null;
                loadAndConnect(expectedGeneration);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void closeStream() {
        FirebaseSseClient previous = sseClient;
        sseClient = null;
        if (previous != null) previous.shutdown();
    }

    public synchronized boolean isConnected() {
        return active && sseClient != null && sseClient.isConnected();
    }

    public synchronized boolean isHealthy() {
        return isConnected() && System.currentTimeMillis() - lastActivityTime
            < Math.max(60_000, BattleConstants.SSE_HEALTH_CHECK_INTERVAL_MS);
    }
}

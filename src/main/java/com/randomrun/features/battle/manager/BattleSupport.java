package com.randomrun.features.battle.manager;

import com.randomrun.features.battle.screen.LobbyScreen;
import com.randomrun.features.battle.screen.RoomWaitingScreen;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.BackendSession;
import com.randomrun.network.HttpClient;
import com.randomrun.network.RealtimeClient;
import com.randomrun.ui.widget.GlobalParticleSystem;

import com.google.gson.Gson;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class BattleSupport {
    private BattleSupport() {
    }

    public final static class BattleConstants {
        public static final int MAX_CONSECUTIVE_ERRORS = 5;
        public static final long POLLING_INTERVAL_FALLBACK_MS = 3000;
        public static final long WORLD_LOAD_DELAY_MS = 400;
        public static final long GUEST_CONNECTION_DELAY_MS = 150;
        public static final long EVENT_TIMEOUT_MS = 5000;
        public static final long API_OPERATION_TIMEOUT_MS = 10000;
        public static final int API_MAX_RETRIES = 3;
        public static final int COUNTDOWN_START_NUMBER = 3;
        public static final long COUNTDOWN_INTERVAL_MS = 1000;

        private BattleConstants() {}
    }

    public final static class BattleCleanupTask {
        private BattleCleanupTask() {}

        public static CompletableFuture<Boolean> finish(CompletableFuture<Boolean> notification, long timeoutMillis,
                Executor clientExecutor, BooleanSupplier sameRoom, Runnable cleanup) {
            return notification.copy().completeOnTimeout(false, timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((success, error) -> error == null && Boolean.TRUE.equals(success))
                .thenApplyAsync(success -> {
                    if (sameRoom.getAsBoolean()) cleanup.run();
                    return success;
                }, clientExecutor);
        }
    }

    public final static class DisconnectHandler {
        public DisconnectHandler(BattleApiHelper ignored) {}
        public CompletableFuture<Boolean> notifyDeparture(String code, BattleRoom room, boolean host, String name) {
            if (code == null) return CompletableFuture.completedFuture(false);
            return HttpClient.getInstance().post("/rooms/" + code + "/leave", Map.of()).thenApply(value -> true).exceptionally(error -> false);
        }
        public void handlePlayerDisconnect(String name, String code, BattleRoom room, boolean host) {
            if (!host || code == null || room == null || !room.isGuest(name)) return;
            HttpClient.getInstance().post("/rooms/" + code + "/disconnect", Map.of("player", name)).exceptionally(error -> null);
        }
        public void handleSelfDisconnect(String code, BattleRoom room, boolean host, boolean active) {
            if (active) notifyDeparture(code, room, host, "");
        }
        public void handleDisconnectDuringBattle(String code, BattleRoom room, boolean host) { notifyDeparture(code, room, host, ""); }
        public void cleanupOnShutdown(String code, BattleRoom room, boolean host) { notifyDeparture(code, room, host, ""); }
    }

    public enum RoomJoinResult {
        JOINED(null),
        INVALID_PASSWORD("password"),
        ROOM_FULL("full"),
        ROOM_NOT_FOUND("missing"),
        ACCESS_DENIED("access"),
        CONNECTION_FAILED("connection"),
        CANCELLED("cancelled");

        private final String messageKey;

        RoomJoinResult(String suffix) {
            messageKey = suffix == null ? null : "randomrun.battle.join_error." + suffix;
        }

        public boolean joined() {
            return this == JOINED;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public final static class BattleRoomUpdates {
        private final Consumer<BattleRoom> listener;
        private RealtimeClient.Subscription subscription;
        private volatile long generation;
        public BattleRoomUpdates(Consumer<BattleRoom> listener, Runnable failed, ScheduledExecutorService scheduler) { this.listener = listener; }
        public synchronized void start(String roomId) {
            stop();
            long expected = generation;
            subscription = RealtimeClient.getInstance().subscribeRooms(rooms -> {
                if (generation != expected) return;
                BattleRoom room = rooms.has(roomId) ? new Gson().fromJson(rooms.get(roomId), BattleRoom.class) : null;
                if (room != null) room.setRoomCode(roomId);
                listener.accept(room);
            });
        }
        public synchronized void stop() { generation++; if (subscription != null) subscription.close(); subscription = null; }
        public synchronized boolean isConnected() { return subscription != null && RealtimeClient.getInstance().isConnected(); }
        public boolean isHealthy() { return isConnected(); }
    }

    public final static class RoomJoiner {
        private static volatile boolean joining;

        private RoomJoiner() {}

        public static boolean isJoining() {
            return joining;
        }

        public static CompletableFuture<RoomJoinResult> join(String code, Screen returnTo) {
            if (joining) return CompletableFuture.completedFuture(RoomJoinResult.CANCELLED);
            joining = true;
            return BattleManager.getInstance().joinRoomDetailed(BackendSession.playerName(), code, "")
                .exceptionally(error -> RoomJoinResult.CONNECTION_FAILED)
                .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                    joining = false;
                    if (result != null && result.joined()) {
                        Minecraft.getInstance().gui.setScreen(new RoomWaitingScreen(new LobbyScreen(returnTo), code, false));
                    }
                }));
        }
    }

    public final static class BattleCleanupHandler {
        private static BattleRoom cleaningRoom;

        public static void register() {
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
                particles.clearParticles();
                particles.setRedMode(false);
                particles.setGreenMode(false);
                BattleManager manager = BattleManager.getInstance();
                client.execute(manager::releaseFinishedConnection);
                BattleRoom room = manager.getCurrentRoom();
                String roomId = manager.getCurrentRoomId();
                if (!manager.isInBattle() || room == null || roomId == null || cleaningRoom == room) return;
                cleaningRoom = room;
                BattleCleanupTask.finish(manager.notifyDisconnect(roomId, room), 2000, client,
                    () -> manager.getCurrentRoom() == room && roomId.equals(manager.getCurrentRoomId()),
                    manager::stopBattle).whenComplete((notified, error) -> {
                        if (cleaningRoom == room) cleaningRoom = null;
                        if (error != null) RandomRunMod.LOGGER.error("[BattleCleanup] Ошибка локальной очистки", error);
                        else if (!notified) RandomRunMod.LOGGER.warn("[BattleCleanup] Сервер не подтвердил выход из комнаты {}", roomId);
                    });
            });
        }

        public static void shutdown() { cleaningRoom = null; }
    }
}

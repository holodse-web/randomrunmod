package com.randomrun.features.battle.manager;

import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.network.BackendSession;
import com.randomrun.network.HttpClient;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class RoomLifecycleManager {
    private static final Gson GSON = new Gson();
    private final AtomicLong generation = new AtomicLong();
    private CompletableFuture<Void> membershipQueue = CompletableFuture.completedFuture(null);
    public void cancelPendingOperations() { generation.incrementAndGet(); }

    private <T> CompletableFuture<T> enqueue(Supplier<CompletableFuture<T>> request) {
        CompletableFuture<Void> previous;
        CompletableFuture<Void> release = new CompletableFuture<>();
        synchronized (this) {
            previous = membershipQueue;
            membershipQueue = release;
        }
        return previous.thenCompose(ignored -> request.get()).whenComplete((value, error) -> release.complete(null));
    }

    public void leaveRoom(String code) {
        var owner = BackendSession.current();
        enqueue(() -> leaveNow(code, owner));
    }

    private CompletableFuture<Void> leaveNow(String code, BackendSession.Session owner) {
        if (owner == null || BackendSession.current() != owner) return CompletableFuture.completedFuture(null);
        return HttpClient.getInstance().post("/rooms/" + code + "/leave", Map.of()).handle((value, error) -> null);
    }
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean shared, boolean privateRoom, String password, int capacity, String mode, boolean randomDrop, RoomSetupCallback callback) {
        return createRoom(playerName, targetItem, shared, privateRoom, password, capacity, mode, randomDrop, false, callback);
    }
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean shared, boolean privateRoom, String password, int capacity, String mode, boolean randomDrop, boolean upgrader, RoomSetupCallback callback) {
        return createRoom(playerName, targetItem, shared, privateRoom, password, capacity, mode, randomDrop, upgrader, false, callback);
    }
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean shared, boolean privateRoom, String password, int capacity, String mode, boolean randomDrop, boolean upgrader, boolean voice, RoomSetupCallback callback) {
        long operation = generation.get();
        var owner = BackendSession.current();
        Map<String, Object> payload = new HashMap<>();
        payload.put("maxPlayers", capacity);
        payload.put("target", targetItem == null ? null : IdCompressor.compress(BuiltInRegistries.ITEM.getKey(targetItem).toString()));
        payload.put("seed", String.valueOf(new Random().nextLong()));
        payload.put("password", password == null ? "" : password);
        payload.put("randomDrop", randomDrop); payload.put("upgrader", upgrader); payload.put("voice", voice);
        payload.put("mode", shared ? "webrtc" : "sw");
        String streamer = com.randomrun.features.streamer.StreamerNet.roomStreamer();
        if (streamer != null) payload.put("streamer", streamer);
        return enqueue(() -> {
            if (operation != generation.get() || owner != BackendSession.current()) return CompletableFuture.completedFuture(null);
            return HttpClient.getInstance().post("/rooms", payload).thenCompose(value -> {
                BattleRoom room = GSON.fromJson(value, BattleRoom.class);
                String code = value.getAsJsonObject().get("roomCode").getAsString();
                room.setRoomCode(code);
                if (voice) room.setVoice(true);
                if (operation != generation.get()) return leaveNow(code, owner).thenApply(ignored -> null);
                callback.setup(room, code, true);
                return CompletableFuture.completedFuture(code);
            });
        });
    }
    public CompletableFuture<Boolean> joinRoom(String playerName, String code, String password, RoomSetupCallback callback) {
        return joinRoomDetailed(playerName, code, password, callback).thenApply(RoomJoinResult::joined);
    }
    public CompletableFuture<RoomJoinResult> joinRoomDetailed(String playerName, String code, String password, RoomSetupCallback callback) {
        if (code == null || !code.matches("[0-9]{5}")) return CompletableFuture.completedFuture(RoomJoinResult.ROOM_NOT_FOUND);
        long operation = generation.get();
        var owner = BackendSession.current();
        Map<String, Object> payload = new HashMap<>();
        payload.put("password", password == null ? "" : password);
        String streamer = com.randomrun.features.streamer.StreamerNet.roomStreamer();
        if (streamer != null) payload.put("streamer", streamer);
        return enqueue(() -> {
            if (operation != generation.get() || owner != BackendSession.current()) {
                return CompletableFuture.completedFuture(RoomJoinResult.CANCELLED);
            }
            return HttpClient.getInstance().post("/rooms/" + code + "/join", payload).thenCompose(value -> {
                if (operation != generation.get()) return leaveNow(code, owner).thenApply(ignored -> RoomJoinResult.CANCELLED);
                BattleRoom room = GSON.fromJson(value, BattleRoom.class); room.setRoomCode(code);
                callback.setup(room, code, false);
                return CompletableFuture.completedFuture(RoomJoinResult.JOINED);
            });
        }).exceptionally(error -> {
            Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
            return switch (Objects.toString(cause.getMessage(), "")) {
                case "WRONG_ROOM_PASSWORD" -> RoomJoinResult.INVALID_PASSWORD;
                case "ROOM_FULL" -> RoomJoinResult.ROOM_FULL;
                case "ROOM_NOT_FOUND" -> RoomJoinResult.ROOM_NOT_FOUND;
                case "ALREADY_IN_ROOM", "GAME_ALREADY_STARTED" -> RoomJoinResult.ACCESS_DENIED;
                default -> RoomJoinResult.CONNECTION_FAILED;
            };
        });
    }
    static BattleRoom createRoomDetails(String name, String seed, String target, String code, boolean privateRoom, String password, boolean shared, int capacity, String mode, boolean randomDrop, boolean upgrader) {
        return new BattleRoom(name, seed, target, code, privateRoom, null, true, capacity, "webrtc", randomDrop, upgrader);
    }
    public interface RoomSetupCallback { void setup(BattleRoom room, String code, boolean host); }
}

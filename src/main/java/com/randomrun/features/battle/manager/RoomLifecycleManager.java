package com.randomrun.features.battle.manager;

import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.network.HttpClient;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class RoomLifecycleManager {
    private final AtomicLong generation = new AtomicLong();
    public RoomLifecycleManager(BattleApiHelper apiHelper) {}
    public void cancelPendingOperations() { generation.incrementAndGet(); }
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean shared, boolean privateRoom, String password, int capacity, String mode, boolean randomDrop, RoomSetupCallback callback) {
        return createRoom(playerName, targetItem, shared, privateRoom, password, capacity, mode, randomDrop, false, callback);
    }
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean shared, boolean privateRoom, String password, int capacity, String mode, boolean randomDrop, boolean upgrader, RoomSetupCallback callback) {
        return createRoom(playerName, targetItem, shared, privateRoom, password, capacity, mode, randomDrop, upgrader, false, callback);
    }
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean shared, boolean privateRoom, String password, int capacity, String mode, boolean randomDrop, boolean upgrader, boolean voice, RoomSetupCallback callback) {
        long operation = generation.get();
        Map<String, Object> payload = new HashMap<>();
        payload.put("maxPlayers", capacity);
        payload.put("target", targetItem == null ? null : IdCompressor.compress(BuiltInRegistries.ITEM.getKey(targetItem).toString()));
        payload.put("seed", String.valueOf(new Random().nextLong()));
        payload.put("password", password == null ? "" : password);
        payload.put("randomDrop", randomDrop); payload.put("upgrader", upgrader); payload.put("voice", voice);
        payload.put("mode", shared ? "webrtc" : "sw");
        return HttpClient.getInstance().post("/rooms", payload).thenApply(value -> {
            BattleRoom room = new Gson().fromJson(value, BattleRoom.class);
            String code = value.getAsJsonObject().get("roomCode").getAsString();
            room.setRoomCode(code);
            if (voice) room.setVoice(true); // older servers drop the flag; keep it for the host's own view
            if (operation != generation.get()) { HttpClient.getInstance().post("/rooms/" + code + "/leave", Map.of()); return null; }
            callback.setup(room, code, true); return code;
        }).exceptionally(error -> null);
    }
    public CompletableFuture<Boolean> joinRoom(String playerName, String code, String password, RoomSetupCallback callback) {
        return joinRoomDetailed(playerName, code, password, callback).thenApply(RoomJoinResult::joined);
    }
    public CompletableFuture<RoomJoinResult> joinRoomDetailed(String playerName, String code, String password, RoomSetupCallback callback) {
        if (code == null || !code.matches("[0-9]{5}")) return CompletableFuture.completedFuture(RoomJoinResult.ROOM_NOT_FOUND);
        long operation = generation.get();
        return HttpClient.getInstance().post("/rooms/" + code + "/join", Map.of("password", password == null ? "" : password)).thenApply(value -> {
            if (operation != generation.get()) { HttpClient.getInstance().post("/rooms/" + code + "/leave", Map.of()); return RoomJoinResult.CANCELLED; }
            BattleRoom room = new Gson().fromJson(value, BattleRoom.class); room.setRoomCode(code);
            callback.setup(room, code, false); return RoomJoinResult.JOINED;
        }).exceptionally(error -> {
            Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
            return switch (Objects.toString(cause.getMessage(), "")) {
                case "WRONG_ROOM_PASSWORD" -> RoomJoinResult.INVALID_PASSWORD;
                case "ROOM_FULL" -> RoomJoinResult.ROOM_FULL;
                case "ROOM_NOT_FOUND" -> RoomJoinResult.ROOM_NOT_FOUND;
                default -> RoomJoinResult.CONNECTION_FAILED;
            };
        });
    }
    static BattleRoom createRoomDetails(String name, String seed, String target, String code, boolean privateRoom, String password, boolean shared, int capacity, String mode, boolean randomDrop, boolean upgrader) {
        return new BattleRoom(name, seed, target, code, privateRoom, null, true, capacity, "webrtc", randomDrop, upgrader);
    }
    public interface RoomSetupCallback { void setup(BattleRoom room, String code, boolean host); }
}

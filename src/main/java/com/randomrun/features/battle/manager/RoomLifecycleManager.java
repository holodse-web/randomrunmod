package com.randomrun.features.battle.manager;

import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.infrastructure.util.PasswordHasher;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public class RoomLifecycleManager {
    private static final String LOG_TAG = "RoomLifecycle";
    
    private final BattleApiHelper apiHelper;
    private final ScheduledExecutorService scheduler;
    private volatile boolean isCreatingRoom = false;
    private final AtomicLong generation = new AtomicLong();

    public void cancelPendingOperations() { generation.incrementAndGet(); }
    
    public RoomLifecycleManager(BattleApiHelper apiHelper, ScheduledExecutorService scheduler) {
        this.apiHelper = apiHelper;
        this.scheduler = scheduler;
    }
    
    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean isSharedWorld, 
                                                 boolean isPrivate, String password, int maxPlayers, 
                                                 String creationMode, boolean randomDrop, RoomSetupCallback setupCallback) {
        return createRoom(playerName, targetItem, isSharedWorld, isPrivate, password, maxPlayers,
            creationMode, randomDrop, false, setupCallback);
    }

    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean isSharedWorld,
                                                 boolean isPrivate, String password, int maxPlayers,
                                                 String creationMode, boolean randomDrop, boolean upgrader, RoomSetupCallback setupCallback) {
        if (isCreatingRoom) {
            CompactLogger.warn("[{}] Room creation already in progress", LOG_TAG);
            return CompletableFuture.completedFuture(null);
        }
        isCreatingRoom = true;
        final long operation = generation.get();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String roomCode = generateRoomCodeWithRetry();
                if (operation != generation.get()) return null;
                if (roomCode == null) {
                    sendPlayerMessage("\u00A7cНе удалось сгенерировать уникальный код комнаты");
                    return null;
                }
                
                String itemId = targetItem != null ? 
                    IdCompressor.compress(BuiltInRegistries.ITEM.getKey(targetItem).toString()) : null;
                String generatedSeed = isSharedWorld ? null : String.valueOf(new Random().nextLong());
                
                BattleRoom newRoom = createRoomDetails(playerName, generatedSeed, itemId, roomCode, isPrivate,
                    password, isSharedWorld, maxPlayers, creationMode, randomDrop, upgrader);
                
                if (apiHelper.createRoom(roomCode, newRoom).join()) {
                    if (operation != generation.get()) {
                        apiHelper.deleteRoom(roomCode);
                        return null;
                    }
                    setupCallback.setup(newRoom, roomCode, true);
                    com.randomrun.infrastructure.data.GlobalStatsManager.incrementRoomCreated();
                    registerPlayer(playerName);
                    showRoomCode(roomCode);
                    return roomCode;
                }
                
                sendPlayerMessage("\u00A7cОшибка создания комнаты!");
                return null;
            } catch (Exception e) {
                CompactLogger.error("[{}] Room creation failed: {}", LOG_TAG, e.getClass().getSimpleName());
                sendPlayerMessage("\u00A7cОшибка: " + e.getMessage());
                return null;
            } finally {
                isCreatingRoom = false;
            }
        });
    }

    public CompletableFuture<Boolean> joinRoom(String playerName, String roomCode, String password,
                                                RoomSetupCallback setupCallback) {
        return joinRoomDetailed(playerName, roomCode, password, setupCallback).thenApply(RoomJoinResult::joined);
    }

    public CompletableFuture<RoomJoinResult> joinRoomDetailed(String playerName, String roomCode, String password,
                                                             RoomSetupCallback setupCallback) {
        long startTime = System.currentTimeMillis();
        final long operation = generation.get();
        
        return CompletableFuture.supplyAsync(() -> {
            if (!checkAntiCheat(playerName)) {
                return RoomJoinResult.ACCESS_DENIED;
            }

            BattleRoom initialRoom = apiHelper.getRoomData(roomCode).join();
            if (operation != generation.get()) return RoomJoinResult.CANCELLED;
            if (initialRoom == null) {
                CompactLogger.warn("[{}] Room not found: {}", LOG_TAG, roomCode);
                return RoomJoinResult.ROOM_NOT_FOUND;
            }

            if (!validatePassword(initialRoom, password)) {
                CompactLogger.warn("[{}] Invalid room password", LOG_TAG);
                return RoomJoinResult.INVALID_PASSWORD;
            }

            return attemptJoinWithRetry(playerName, roomCode, setupCallback, startTime, operation);
        }).exceptionally(error -> {
            CompactLogger.error("[{}] Join failed: {}", LOG_TAG, error.getClass().getSimpleName());
            return operation == generation.get() ? RoomJoinResult.CONNECTION_FAILED : RoomJoinResult.CANCELLED;
        });
    }

    private RoomJoinResult attemptJoinWithRetry(String playerName, String roomCode,
                                        RoomSetupCallback setupCallback, long startTime, long operation) {
        for (int attempt = 0; attempt < BattleConstants.MAX_JOIN_ATTEMPTS; attempt++) {
            try {
                if (operation != generation.get()) return RoomJoinResult.CANCELLED;
                BattleRoom room = apiHelper.getRoomData(roomCode).join();
                if (operation != generation.get()) return RoomJoinResult.CANCELLED;
                
                if (room == null) {
                    CompactLogger.warn("[{}] Room disappeared on attempt {}", LOG_TAG, attempt + 1);
                    return RoomJoinResult.ROOM_NOT_FOUND;
                }

                List<String> currentPlayers = room.getPlayers();

                if (currentPlayers.contains(playerName)) {
                    setupCallback.setup(room, roomCode, false);
                    logJoinSuccess(startTime, attempt + 1);
                    return RoomJoinResult.JOINED;
                }

                if (currentPlayers.size() >= room.getMaxPlayers()) {
                    CompactLogger.warn("[{}] Room full ({}/{})", LOG_TAG, currentPlayers.size(), room.getMaxPlayers());
                    return RoomJoinResult.ROOM_FULL;
                }

                room.addPlayer(playerName);

                if (apiHelper.updateRoom(roomCode, room).join()) {
                    if (operation != generation.get()) {
                        java.util.Map<String, Object> cleanup = new java.util.HashMap<>();
                        cleanup.put("g/" + playerName, null);
                        apiHelper.updateRoomFields(roomCode, cleanup);
                        return RoomJoinResult.CANCELLED;
                    }
                    BattleRoom verifyRoom = apiHelper.getRoomData(roomCode).join();
                    if (operation != generation.get()) return RoomJoinResult.CANCELLED;
                    
                    if (verifyRoom != null && verifyRoom.getPlayers().contains(playerName)) {
                        setupCallback.setup(verifyRoom, roomCode, false);
                        logJoinSuccess(startTime, attempt + 1);
                        sendJoinMessage(playerName, verifyRoom);
                        registerPlayer(playerName);
                        return RoomJoinResult.JOINED;
                    } else {
                        CompactLogger.warn("[{}] Join verification failed, attempt {}", LOG_TAG, attempt + 1);
                    }
                }

                if (attempt < BattleConstants.MAX_JOIN_ATTEMPTS - 1) {
                    long delay = calculateBackoff(attempt);
                    TimeUnit.MILLISECONDS.sleep(delay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CompactLogger.warn("[{}] Join interrupted on attempt {}", LOG_TAG, attempt + 1);
                return RoomJoinResult.CANCELLED;
            } catch (Exception e) {
                CompactLogger.error("[{}] Join attempt {} failed: {}", LOG_TAG, attempt + 1, e.getClass().getSimpleName());
                if (attempt == BattleConstants.MAX_JOIN_ATTEMPTS - 1) {
                    return RoomJoinResult.CONNECTION_FAILED;
                }
            }
        }

        CompactLogger.error("[{}] Failed to join after {} attempts", LOG_TAG, BattleConstants.MAX_JOIN_ATTEMPTS);
        return RoomJoinResult.CONNECTION_FAILED;
    }

    static BattleRoom createRoomDetails(String playerName, String seed, String itemId, String roomCode,
                                        boolean isPrivate, String password, boolean isSharedWorld,
                                        int maxPlayers, String creationMode, boolean randomDrop, boolean upgrader) {
        return new BattleRoom(playerName, seed, itemId, roomCode, isPrivate, password,
            isSharedWorld, maxPlayers, creationMode, randomDrop, upgrader);
    }

    private long calculateBackoff(int attempt) {
        long baseDelay = BattleConstants.RETRY_BASE_DELAY_MS;
        long exponential = (long) Math.pow(2, attempt) * baseDelay;
        long jitter = (long) (Math.random() * BattleConstants.RETRY_JITTER_MS);
        return Math.min(exponential + jitter, 2000);
    }

    private void logJoinSuccess(long startTime, int attempts) {
        long duration = System.currentTimeMillis() - startTime;
    }

    private boolean checkAntiCheat(String playerName) {
        com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
        if (!com.randomrun.infrastructure.security.ProfileAntiCheat.canJoinBattle(profile)) {
            CompactLogger.warn("[{}] Anti-cheat denied room access for {}", LOG_TAG, playerName);
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (client.player != null) {
                        com.randomrun.infrastructure.util.ClientMessages.send(client.player, Component.literal("\u00A7cВы не можете присоединиться к битве. Причина: низкий рейтинг доверия или бан."), false);
                    }
                });
            }
            return false;
        }
        return true;
    }

    static boolean validatePassword(BattleRoom room, String password) {
        if (!room.isPrivate()) return true;
        String stored = room.getPassword();
        return stored != null && !stored.isEmpty() && PasswordHasher.verify(password, stored);
    }
    
    private void sendJoinMessage(String playerName, BattleRoom room) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    String modeText = room.isSharedWorld() ? "общий мир" : "разные миры";
                    com.randomrun.infrastructure.util.ClientMessages.send(client.player, Component.literal("§a" + playerName + " §7подключился (§e" + modeText + "§7)"), false);
                }
            });
        }
    }
    
    private String generateRoomCodeWithRetry() {
        for (int i = 0; i < BattleConstants.MAX_ROOM_CODE_COLLISIONS; i++) {
            String roomCode = generateRoomCode();
            BattleRoom existingRoom = apiHelper.getRoomData(roomCode).join();
            
            if (existingRoom == null) {
                return roomCode;
            }
            CompactLogger.warn("[{}] Code collision {}, attempt {}", LOG_TAG, roomCode, i + 1);
        }
        CompactLogger.error("[{}] Failed to generate unique code after {} attempts", LOG_TAG, BattleConstants.MAX_ROOM_CODE_COLLISIONS);
        return null;
    }
    
    private String generateRoomCode() {
        return String.format("%05d", new Random().nextInt(100000));
    }
    
    private void showRoomCode(String roomCode) {
        Minecraft.getInstance().execute(() -> {
            sendPlayerMessage("\u00A7a[RandomRun] \u00A7fКомната создана! Код: \u00A7e\u00A7l" + roomCode);
        });
    }
    
    private void registerPlayer(String playerName) {
        com.randomrun.core.profile.PlayerProfile.load(playerName);
    }
    
    private void sendPlayerMessage(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                com.randomrun.infrastructure.util.ClientMessages.send(Minecraft.getInstance().player, Component.literal(message), false);
            }
        });
    }

    @FunctionalInterface
    public interface RoomSetupCallback {
        void setup(BattleRoom room, String roomCode, boolean isHost);
    }
}

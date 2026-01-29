/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.battle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.screen.DefeatScreen;
import com.randomrun.challenges.classic.screen.ItemSelectionAnimationScreen;
import com.randomrun.challenges.classic.world.WorldCreator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BattleManager {
    
    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";

    private static BattleManager instance;
    private final FirebaseClient firebaseClient;
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;

    private volatile BattleRoom currentRoom;
    private String currentRoomId;
    private boolean isHost;
    private boolean isFrozen;
    private boolean isInBattle;

    private java.util.concurrent.ScheduledFuture<?> matchmakingTask;
    private java.util.concurrent.ScheduledFuture<?> roomListenerTask;
    private java.util.concurrent.ScheduledFuture<?> publicQueueTask;

    private boolean battleEndHandled = false;

    private volatile boolean loadingTriggered = false;
    
    private BattleManager() {
        this.firebaseClient = FirebaseClient.getInstance();
    }
    
    public static BattleManager getInstance() {
        if (instance == null) {
            instance = new BattleManager();
        }
        return instance;
    }
    
    public CompletableFuture<String> createPrivateRoom(String playerName, Item targetItem) {

        resetForNewGame();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String roomCode = generateRoomCode();
                String seed = String.valueOf(new Random().nextLong());
                String itemId = Registries.ITEM.getId(targetItem).toString();
                
                BattleRoom room = new BattleRoom(playerName, seed, itemId, roomCode, true);
                
                boolean success = firebaseClient.put("/rooms/private/" + roomCode, room).join();
                
                if (success) {
                    this.currentRoom = room;
                    this.currentRoomId = roomCode;
                    this.isHost = true;
                    this.isInBattle = true;
                    
                    if (scheduler == null || scheduler.isShutdown()) {
                        scheduler = Executors.newSingleThreadScheduledExecutor();
                    }
                    
                    startRoomListener(roomCode);
                    
                    RandomRunMod.LOGGER.info("Created private room: " + roomCode);
                    return roomCode;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to create private room", e);
            }
            return null;
        });
    }
    
    public CompletableFuture<BattleRoom> joinPrivateRoom(String playerName, String roomCode) {

        resetForNewGame();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject roomData = firebaseClient.get("/rooms/private/" + roomCode).join();
                
                if (roomData == null) {
                    return null;
                }
                
                BattleRoom room = gson.fromJson(roomData, BattleRoom.class);
                
                if (room.getGuest() != null) {
                    return null;
                }
                
                room.setGuest(playerName);

                
                boolean success = firebaseClient.patch("/rooms/private/" + roomCode, room).join();
                
                if (success) {
                    this.currentRoom = room;
                    this.currentRoomId = roomCode;
                    this.isHost = false;
                    this.isInBattle = true;
                    
                    if (scheduler == null || scheduler.isShutdown()) {
                        scheduler = Executors.newSingleThreadScheduledExecutor();
                    }
                    
                    startRoomListener(roomCode);
                    
                    RandomRunMod.LOGGER.info("Joined private room: " + roomCode);
                    return room;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to join private room: " + roomCode, e);
            }
            return null;
        });
    }
    
    public CompletableFuture<Boolean> joinPublicQueue(String playerName) {

        resetForNewGame();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject queueEntry = new JsonObject();
                queueEntry.addProperty("playerName", playerName);
                queueEntry.addProperty("timestamp", System.currentTimeMillis());
                
                firebaseClient.put("/queue/public/" + playerName, queueEntry).join();
                
                this.isInBattle = true;
                
                if (scheduler == null || scheduler.isShutdown()) {
                    scheduler = Executors.newSingleThreadScheduledExecutor();
                }
                
                startMatchmaking(playerName);
                startPublicQueueListener();
                
                RandomRunMod.LOGGER.info("Joined public queue: " + playerName);
                return true;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to join public queue", e);
                return false;
            }
        });
    }
    
    private long lastEventTime = 0;
    
    private void checkForEvents(String eventsPath) {
        try {
            JsonObject events = firebaseClient.get(eventsPath).join();
            if (events == null) return;
            
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            
            for (String key : events.keySet()) {
                JsonObject event = events.getAsJsonObject(key);
                long timestamp = event.get("timestamp").getAsLong();
                
                if (timestamp > lastEventTime) {
                    lastEventTime = timestamp;
                    
                    String eventPlayer = event.get("player").getAsString();
                    if (!eventPlayer.equals(playerName) && "ACHIEVEMENT".equals(event.get("type").getAsString())) {
                        String title = event.get("title").getAsString();
                        String iconId = event.get("icon").getAsString();
                        
                        MinecraftClient.getInstance().execute(() -> {
                            com.randomrun.challenges.advancement.hud.OpponentAchievementHud.show(title, iconId);
                        });
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors (might be empty or connection issue)
        }
    }

    private void startMatchmaking(String playerName) {
        if (matchmakingTask != null && !matchmakingTask.isCancelled()) {
            matchmakingTask.cancel(false);
        }
        
        matchmakingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                JsonObject queue = firebaseClient.get("/queue/public").join();
                
                if (queue == null || queue.size() < 2) {
                    return;
                }
                
                String opponent = null;
                for (String key : queue.keySet()) {
                    if (!key.equals(playerName)) {
                        opponent = key;
                        break;
                    }
                }
                
                if (opponent != null) {

                    if (matchmakingTask != null) {
                        matchmakingTask.cancel(false);
                    }
                    
                    String matchId = generateRoomCode();
                    String seed = String.valueOf(new Random().nextLong());
                    
                    List<Item> allItems = ItemDifficulty.getAllItems(
                        RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems()
                    );
                    Item randomItem = allItems.get(new Random().nextInt(allItems.size()));
                    String itemId = Registries.ITEM.getId(randomItem).toString();
                    
                    BattleRoom room = new BattleRoom(playerName, seed, itemId, matchId, false);
                    room.setGuest(opponent);
                    room.setStatus(BattleRoom.RoomStatus.WAITING);
                    
                    firebaseClient.put("/rooms/public/" + matchId, room).join();
                    
                    firebaseClient.delete("/queue/public/" + playerName).join();
                    firebaseClient.delete("/queue/public/" + opponent).join();
                    
                    this.currentRoom = room;
                    this.currentRoomId = matchId;
                    this.isHost = true;
                    this.isInBattle = true;
                    
                    startRoomListener(matchId);
                    
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().setScreen(
                            new ItemSelectionAnimationScreen(null, matchId)
                        );
                    });
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Match found")) {
                    RandomRunMod.LOGGER.info("Matchmaking stopped - match found");
                } else {
                    RandomRunMod.LOGGER.error("Matchmaking error", e);
                }
            }
        }, 0, 2, TimeUnit.SECONDS);
    }
    
    private void startPublicQueueListener() {
        if (publicQueueTask != null && !publicQueueTask.isCancelled()) {
            publicQueueTask.cancel(false);
        }
        
        publicQueueTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!isInBattle) {
                    if (publicQueueTask != null) {
                        publicQueueTask.cancel(false);
                    }
                    return;
                }
                
                JsonObject publicRooms = firebaseClient.get("/rooms/public").join();
                
                if (publicRooms == null) {
                    return;
                }
                
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                
                for (String matchId : publicRooms.keySet()) {
                    JsonObject roomData = publicRooms.getAsJsonObject(matchId);
                    BattleRoom room = gson.fromJson(roomData, BattleRoom.class);
                    
                    if (room.hasPlayer(playerName) && currentRoom == null) {
                        
                        if (publicQueueTask != null) {
                            publicQueueTask.cancel(false);
                        }
                        
                        currentRoom = room;
                        currentRoomId = matchId;
                        isHost = room.isHost(playerName);
                        
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient.getInstance().setScreen(
                                new ItemSelectionAnimationScreen(null, matchId)
                            );
                        });
                        
                        return;
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Public queue listener error", e);
                if (publicQueueTask != null) {
                    publicQueueTask.cancel(false);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private void startRoomListener(String roomId) {
        if (roomListenerTask != null && !roomListenerTask.isCancelled()) {
            roomListenerTask.cancel(false);
        }
        
        roomListenerTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                String path = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                JsonObject roomData = firebaseClient.get(path + roomId).join();
                
                if (roomData == null) {
                    stopBattle();
                    return;
                }
                
                BattleRoom updatedRoom = gson.fromJson(roomData, BattleRoom.class);
                BattleRoom.RoomStatus oldStatus = currentRoom.getStatus();
                BattleRoom.RoomStatus newStatus = updatedRoom.getStatus();
                
            
                if (!currentRoom.isPrivate() && !isHost && 
                    newStatus == BattleRoom.RoomStatus.WAITING && 
                    oldStatus != BattleRoom.RoomStatus.WAITING) {
                    
                    currentRoom = updatedRoom;
                    
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().setScreen(
                            new ItemSelectionAnimationScreen(null, roomId)
                        );
                    });
                    return;
                }
                
          
                if (newStatus == BattleRoom.RoomStatus.WAITING) {
                    String playerName = MinecraftClient.getInstance().getSession().getUsername();
                    boolean isHostPlayer = currentRoom.isHost(playerName);
                    
             
                    boolean wasOpponentReady = isHostPlayer ? 
                        (currentRoom != null && currentRoom.isGuestReady()) : 
                        (currentRoom != null && currentRoom.isHostReady());
                    boolean isOpponentReady = isHostPlayer ? updatedRoom.isGuestReady() : updatedRoom.isHostReady();
                    
                    if (!wasOpponentReady && isOpponentReady) {
                        RandomRunMod.LOGGER.info("✓ Opponent ready in lobby: " + (isHostPlayer ? "guest" : "host"));
                    }
                    
            
                    // Only trigger loading if we are the host OR if we detect both ready and no one has triggered it yet
                    // Allow ANYONE to trigger it to prevent "Host waiting for Guest" deadlocks
                    if (updatedRoom.isHostReady() && updatedRoom.isGuestReady()) {
                        // Check if we already triggered loading locally to avoid double execution
                        if (!loadingTriggered) {
                            RandomRunMod.LOGGER.info("✓ Both players ready in lobby - triggering LOADING");
                            MinecraftClient.getInstance().execute(() -> {
                                setStatusLoading();
                            });
                        }
                    }
                }
                
              
                if (newStatus == BattleRoom.RoomStatus.LOADING && 
                    updatedRoom.isHostLoaded() && updatedRoom.isGuestLoaded() &&
                    (!currentRoom.isHostLoaded() || !currentRoom.isGuestLoaded())) {
                    
                    // ANYONE can trigger FROZEN status if both are loaded
                    // This prevents the issue where host is loaded but doesn't check again
                    
                    CompletableFuture.runAsync(() -> {
                        try {
                            String roomPath = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                            
                            JsonObject statusUpdate = new JsonObject();
                            statusUpdate.addProperty("status", "FROZEN");
                            // Reset ready status for the FROZEN phase
                            statusUpdate.addProperty("hostReady", false);
                            statusUpdate.addProperty("guestReady", false);
                            
                            // Allow ANY player to trigger this to ensure it happens
                            // The first one to patch wins, subsequent patches are redundant but harmless
                            firebaseClient.patch(roomPath + roomId, statusUpdate).join();
                            RandomRunMod.LOGGER.info("✓ Both players loaded - status set to FROZEN (ready flags reset)");
                            
                            // Reset local ready flags immediately
                            if (currentRoom != null) {
                                currentRoom.setHostReady(false);
                                currentRoom.setGuestReady(false);
                                currentRoom.setStatus(BattleRoom.RoomStatus.FROZEN);
                            }
                        } catch (Exception e) {
                            RandomRunMod.LOGGER.error("Failed to set FROZEN status", e);
                        }
                    });
                }
                
            
                if ((newStatus == BattleRoom.RoomStatus.LOADING || 
                     newStatus == BattleRoom.RoomStatus.FROZEN || 
                     newStatus == BattleRoom.RoomStatus.STARTED) && 
                    oldStatus == BattleRoom.RoomStatus.WAITING) {
                    currentRoom = updatedRoom;
                    
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().setScreen(null);
                        
                        Item targetItem = Registries.ITEM.get(new Identifier(updatedRoom.getTargetItem()));
                        String seed = updatedRoom.getSeed();
                        
                
                        if (seed == null || seed.isEmpty() || seed.equals("null")) {
                            RandomRunMod.LOGGER.error("Invalid seed received! Cannot create world.");
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(
                                    Text.literal("§cОшибка: неверный сид!"), false
                                );
                            }
                            return;
                        }
                        
                        RandomRunMod.LOGGER.info("═══════════════════════════════════");
                        RandomRunMod.LOGGER.info("🌍 LOADING WORLD");
                        RandomRunMod.LOGGER.info("  - Seed: " + seed);
                        RandomRunMod.LOGGER.info("  - Item: " + updatedRoom.getTargetItem());
                        RandomRunMod.LOGGER.info("═══════════════════════════════════");
                        
               
                        RandomRunMod.getInstance().getRunDataManager().setTargetItem(targetItem);
                        
                        // Force player spawn point to world spawn if needed
                        // This logic is primarily handled in PlayerJoinWorldMixin, but we ensure consistent seed here
                        WorldCreator.createSpeedrunWorld(targetItem, seed);
                    });
                    return;
                }
                
          
                if (newStatus == BattleRoom.RoomStatus.FROZEN) {
                    String playerName = MinecraftClient.getInstance().getSession().getUsername();
                    boolean isHost = currentRoom.isHost(playerName);
                    
                 
                    boolean wasOpponentReady = isHost ? 
                        (currentRoom != null && currentRoom.isGuestReady()) : 
                        (currentRoom != null && currentRoom.isHostReady());
                    boolean isOpponentReady = isHost ? updatedRoom.isGuestReady() : updatedRoom.isHostReady();
                    
              
                    boolean myReady = isHost ? updatedRoom.isHostReady() : updatedRoom.isGuestReady();
                    
            
                    if (!wasOpponentReady && isOpponentReady && !myReady) {
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(
                                    Text.literal("§a✓ Противник готов! Напишите /go для старта"), false
                                );
                            }
                        });
                    }
                }
                
           
                if (newStatus == BattleRoom.RoomStatus.STARTED && oldStatus != BattleRoom.RoomStatus.STARTED) {
                    // Start countdown logic
                    Runnable startRunnable = () -> {
                        MinecraftClient.getInstance().execute(() -> {
                            startCountdown();
                        });
                    };

                    // For Public rooms: Guest enters immediately, Host waits 1.5s
                    // This prevents "Host 2/2 Ready but stuck" issues
                    if (!currentRoom.isPrivate() && isHost) {
                        RandomRunMod.LOGGER.info("Public Room Host: Delaying start by 1.5s to allow guest to enter first");
                        scheduler.schedule(startRunnable, 1500, TimeUnit.MILLISECONDS);
                    } else {
                        // Private rooms OR Guest in Public room: Start immediately
                        startRunnable.run();
                    }
                }
                
          
                if (updatedRoom.getWinner() != null && !battleEndHandled) {
                    battleEndHandled = true;
                    MinecraftClient.getInstance().execute(() -> {
                        handleBattleEnd(updatedRoom);
                    });
                }
                
                // Check for new events (achievements) if in private room
                if (currentRoom.isPrivate() && newStatus == BattleRoom.RoomStatus.STARTED) {
                    checkForEvents(path + roomId + "/events");
                }
                
                currentRoom = updatedRoom;
                
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Room listener error", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    public void setStatusLoading() {
        if (currentRoomId == null || currentRoom == null) {
            RandomRunMod.LOGGER.warn("Cannot set status LOADING - no active room");
            return;
        }
        
    
        if (loadingTriggered) {
            RandomRunMod.LOGGER.info("LOADING already triggered, skipping");
            return;
        }
        loadingTriggered = true;
        
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        RandomRunMod.LOGGER.info("🔄 SETTING STATUS TO LOADING");
        RandomRunMod.LOGGER.info("  - Room ID: " + currentRoomId);
        RandomRunMod.LOGGER.info("  - Is Private: " + currentRoom.isPrivate());
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                
                JsonObject update = new JsonObject();
                update.addProperty("status", "LOADING");
                update.addProperty("readyCount", 0);
                update.addProperty("hostReady", false);
                update.addProperty("guestReady", false);
                update.addProperty("hostLoaded", false);
                update.addProperty("guestLoaded", false);
                
                boolean success = firebaseClient.patch(path + currentRoomId, update).join();
                
                RandomRunMod.LOGGER.info("Status set to LOADING, readyCount reset to 0. Success: " + success);
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to set status LOADING", e);
            }
        });
    }
    
    public void setPlayerLoaded() {
        if (currentRoomId == null || currentRoom == null) {
            RandomRunMod.LOGGER.warn("Cannot set player loaded - no active room");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isHost = currentRoom.isHost(playerName);
                
                String loadedField = isHost ? "hostLoaded" : "guestLoaded";
                
                JsonObject update = new JsonObject();
                update.addProperty(loadedField, true);
                
                boolean success = firebaseClient.patch(path + currentRoomId, update).join();
                
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
                RandomRunMod.LOGGER.info("✓ Player loaded into world: " + playerName);
                RandomRunMod.LOGGER.info("  - Set " + loadedField + " = true");
                RandomRunMod.LOGGER.info("  - Success: " + success);
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
                
                if (success) {
                    if (isHost) {
                        currentRoom.setHostLoaded(true);
                    } else {
                        currentRoom.setGuestLoaded(true);
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to set player loaded", e);
            }
        });
    }
    

    public void sendLobbyReady() {
        if (currentRoom == null || currentRoomId == null) {
            RandomRunMod.LOGGER.warn("Cannot send lobby ready - no active room");
            return;
        }
        
        if (currentRoom.getStatus() != BattleRoom.RoomStatus.WAITING) {
            RandomRunMod.LOGGER.warn("Cannot send lobby ready - not in WAITING status: " + currentRoom.getStatus());
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isHostPlayer = currentRoom.isHost(playerName);
                
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
                RandomRunMod.LOGGER.info("⚡ SENDING LOBBY READY");
                RandomRunMod.LOGGER.info("  - Room ID: " + currentRoomId);
                RandomRunMod.LOGGER.info("  - Player: " + playerName + " (isHost: " + isHostPlayer + ")");
                
                String readyField = isHostPlayer ? "hostReady" : "guestReady";
                
                JsonObject update = new JsonObject();
                update.addProperty(readyField, true);
                
                boolean success = firebaseClient.patch(path + currentRoomId, update).join();
                
                RandomRunMod.LOGGER.info("  - PATCH success: " + success);
                RandomRunMod.LOGGER.info("  - Set " + readyField + " = true");
                
  
                if (isHostPlayer) {
                    currentRoom.setHostReady(true);
                } else {
                    currentRoom.setGuestReady(true);
                }
                
    
                JsonObject latestRoomData = firebaseClient.get(path + currentRoomId).join();
                if (latestRoomData != null) {
                    BattleRoom latestRoom = gson.fromJson(latestRoomData, BattleRoom.class);
                    
                    currentRoom = latestRoom;
                    
                    if (latestRoom.isHostReady() && latestRoom.isGuestReady() && 
                        latestRoom.getStatus() == BattleRoom.RoomStatus.WAITING) {
                        
                        if (isHostPlayer) {
                            RandomRunMod.LOGGER.info("✓ Both players ready in lobby - HOST transitioning to LOADING");
                            MinecraftClient.getInstance().execute(() -> {
                                setStatusLoading();
                            });
                        } else {
                            RandomRunMod.LOGGER.info("✓ Both players ready in lobby - waiting for HOST to transition");
                        }
                    }
                }
                
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to send lobby ready", e);
            }
        });
    }
    

    public void sendReady() {

        if (currentRoom == null) {
            RandomRunMod.LOGGER.info("sendReady called but not in battle - allowing for solo speedrun");
            return;
        }
        
    
        if (currentRoom.getStatus() != BattleRoom.RoomStatus.FROZEN) {
            RandomRunMod.LOGGER.warn("Cannot send ready - not in FROZEN status: " + currentRoom.getStatus());
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isHost = currentRoom.isHost(playerName);
                
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
                RandomRunMod.LOGGER.info("⚡ SENDING READY (/go)");
                RandomRunMod.LOGGER.info("  - Room ID: " + currentRoomId);
                RandomRunMod.LOGGER.info("  - Player: " + playerName + " (isHost: " + isHost + ")");
                
        
                String readyField = isHost ? "hostReady" : "guestReady";
                
                JsonObject update = new JsonObject();
                update.addProperty(readyField, true);
                
                boolean success = firebaseClient.patch(path + currentRoomId, update).join();
                
                RandomRunMod.LOGGER.info("  - PATCH success: " + success);
                RandomRunMod.LOGGER.info("  - Set " + readyField + " = true");
                
        
                if (isHost) {
                    currentRoom.setHostReady(true);
                } else {
                    currentRoom.setGuestReady(true);
                }
                
                // If I am ready, I should wait for opponent.
                // If I am the LAST one to be ready, I trigger the start.
                // But to be safe, let's check the server state.
          
                JsonObject latestRoomData = firebaseClient.get(path + currentRoomId).join();
                if (latestRoomData != null) {
                    BattleRoom latestRoom = gson.fromJson(latestRoomData, BattleRoom.class);
            
                    // UPDATE LOCAL STATE with latest from server
                    // IMPORTANT: Force our own ready state to match what we just patched
                    // This fixes the "Early /go" issue where server data might be slightly stale
                    // despite the .join() call on patch
                    if (isHost) {
                        latestRoom.setHostReady(true);
                    } else {
                        latestRoom.setGuestReady(true);
                    }
                    
                    currentRoom = latestRoom;
                    
                    if (latestRoom.isHostReady() && latestRoom.isGuestReady() && 
                        latestRoom.getStatus() == BattleRoom.RoomStatus.FROZEN) {
                        
                        // ANYONE can trigger start if both are ready
                        
                        JsonObject statusUpdate = new JsonObject();
                        statusUpdate.addProperty("status", "STARTED");
                        firebaseClient.patch(path + currentRoomId, statusUpdate).join();
                        
                        RandomRunMod.LOGGER.info("✓ Both players ready - battle starting!");
                    } else {
                        RandomRunMod.LOGGER.info("⏳ Waiting for opponent to be ready...");
                        if (MinecraftClient.getInstance().player != null) {
                            MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("§eОжидание готовности противника..."), false
                            );
                        }
                    }
                }
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to send ready", e);
            }
        });
    }
    
    public void reportVictory(long time) {
        if (currentRoom == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = currentRoom.isPrivate() ? "/rooms/private/" : "/rooms/public/";
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                
                JsonObject update = new JsonObject();
                update.addProperty("winner", playerName);
                update.addProperty("status", "FINISHED");
                
                if (isHost) {
                    update.addProperty("hostTime", time);
                } else {
                    update.addProperty("guestTime", time);
                }
                
                firebaseClient.patch(path + currentRoomId, update).join();
                
                RandomRunMod.LOGGER.info("Reported victory: " + time + "ms");
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to report victory", e);
            }
        });
    }
    
    private void handleBattleEnd(BattleRoom room) {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        boolean won = room.getWinner().equals(playerName);
        
      
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (!won && runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            runManager.failRun();
        }
        
        String winnerName = room.getWinner();
        
        if (won) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§a§l✓ ПОБЕДА!"), false
            );
            MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§7Вы первым добыли целевой предмет!"), false
            );
            
   
            MinecraftClient.getInstance().player.playSound(
                net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f
            );
        } else {
            long opponentTime = isHost ? room.getGuestTime() : room.getHostTime();
            String reason = Text.translatable("randomrun.defeat.reason.opponent_won", winnerName).getString();
            if (opponentTime > 0) {
                reason += Text.translatable("randomrun.defeat.reason.time", formatTime(opponentTime)).getString();
            }
            
    
            Item targetItem = runManager.getTargetItem();
            if (targetItem == null && room.getTargetItem() != null) {
                targetItem = Registries.ITEM.get(new Identifier(room.getTargetItem()));
            }
            
            long elapsedTime = runManager.getCurrentTime();
            
       
            final Item finalTargetItem = targetItem;
            final String finalReason = reason;
            MinecraftClient.getInstance().setScreen(new DefeatScreen(finalTargetItem, elapsedTime, finalReason));
        }
        
        scheduler.schedule(() -> {
            // Archive private games before deleting
            if (room.isPrivate()) {
                archiveRoom(room);
            }
            
            deleteRoom();
            stopBattle();
        }, 5, TimeUnit.SECONDS);
    }
    
    private void archiveRoom(BattleRoom room) {
        if (room == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/history/private/" + room.getRoomCode();
                firebaseClient.put(path, room).join();
                RandomRunMod.LOGGER.info("Archived private room: " + room.getRoomCode());
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to archive room: " + room.getRoomCode(), e);
            }
        });
    }

    public void reportAchievement(String achievementId, String title, String iconItem) {
        if (currentRoom == null || !currentRoom.isPrivate()) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/private/" + currentRoomId + "/events";
                JsonObject event = new JsonObject();
                event.addProperty("type", "ACHIEVEMENT");
                event.addProperty("player", MinecraftClient.getInstance().getSession().getUsername());
                event.addProperty("achievementId", achievementId);
                event.addProperty("title", title);
                event.addProperty("icon", iconItem);
                event.addProperty("timestamp", System.currentTimeMillis());
                
                firebaseClient.post(path, event).join();
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to report achievement event", e);
            }
        });
    }

    public void leavePublicQueue(String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                firebaseClient.delete("/queue/public/" + playerName).join();
                RandomRunMod.LOGGER.info("Left public queue: " + playerName);
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to leave public queue", e);
            }
        });
    }
    
    public void deleteRoom() {
        if (currentRoomId == null) return;
        
        final String roomId = currentRoomId;
        final boolean isPrivate = currentRoom != null && currentRoom.isPrivate();
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = isPrivate ? "/rooms/private/" : "/rooms/public/";
                firebaseClient.delete(path + roomId).join();
                RandomRunMod.LOGGER.info("Deleted room: " + roomId + " (private: " + isPrivate + ")");
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to delete room: " + roomId, e);
            }
        });
    }

    public void cleanupOnShutdown() {
        if (currentRoomId == null) return;
        
        // Synchronous cleanup for shutdown hook
        try {
            // Check if we need to award victory on shutdown
            if (currentRoom != null && currentRoom.isPrivate() && 
               (currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED || currentRoom.getStatus() == BattleRoom.RoomStatus.FROZEN)) {
                
                String myName = MinecraftClient.getInstance().getSession().getUsername();
                String opponentName = isHost ? currentRoom.getGuest() : currentRoom.getHost();
                
                if (opponentName != null) {
                    JsonObject update = new JsonObject();
                    update.addProperty("winner", opponentName);
                    update.addProperty("status", "FINISHED");
                    
                    String path = "/rooms/private/" + currentRoomId;
                    firebaseClient.patch(path, update).join();
                    RandomRunMod.LOGGER.info("Surrendered on shutdown. Winner: " + opponentName);
                    return; // Don't delete room, let the winner handle it
                }
            }
            
            String path = (currentRoom != null && currentRoom.isPrivate()) ? "/rooms/private/" : "/rooms/public/";
            firebaseClient.delete(path + currentRoomId).join();
            RandomRunMod.LOGGER.info("Deleted room on shutdown: " + currentRoomId);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to delete room on shutdown", e);
        }
    }
    
    public void handleDisconnect() {
        if (currentRoom == null || currentRoomId == null) return;
        
        // If game is active (STARTED or FROZEN) and private, surrender
        if (currentRoom.isPrivate() && 
           (currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED || currentRoom.getStatus() == BattleRoom.RoomStatus.FROZEN)) {
            
            String myName = MinecraftClient.getInstance().getSession().getUsername();
            String opponentName = isHost ? currentRoom.getGuest() : currentRoom.getHost();
            
            if (opponentName != null) {
                RandomRunMod.LOGGER.info("Disconnecting from active private battle - Surrendering to " + opponentName);
                
                CompletableFuture.runAsync(() -> {
                    try {
                        JsonObject update = new JsonObject();
                        update.addProperty("winner", opponentName);
                        update.addProperty("status", "FINISHED");
                        
                        String path = "/rooms/private/" + currentRoomId;
                        firebaseClient.patch(path, update).join();
                        RandomRunMod.LOGGER.info("Surrender sent successfully");
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("Failed to send surrender", e);
                    }
                });
                
                // Stop local battle state but DON'T delete room immediately
                // The opponent needs to see the winner status
                stopBattle();
                return;
            }
        }
        
        // Default cleanup (delete room if host, etc)
        deleteRoom();
        stopBattle();
    }
    
    public void stopBattle() {
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        RandomRunMod.LOGGER.info("🛑 STOPPING BATTLE - FULL RESET");
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        
      
        if (matchmakingTask != null && !matchmakingTask.isCancelled()) {
            matchmakingTask.cancel(true);
            matchmakingTask = null;
        }
        if (roomListenerTask != null && !roomListenerTask.isCancelled()) {
            roomListenerTask.cancel(true);
            roomListenerTask = null;
        }
        if (publicQueueTask != null && !publicQueueTask.isCancelled()) {
            publicQueueTask.cancel(true);
            publicQueueTask = null;
        }
        
     
        this.currentRoom = null;
        this.currentRoomId = null;
        this.isHost = false;
        this.isFrozen = false;
        this.isInBattle = false;
        this.battleEndHandled = false;
        this.loadingTriggered = false;
        
     
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        
        RandomRunMod.LOGGER.info("✓ Battle state fully reset");
    }
    
    
    public void resetForNewGame() {
        RandomRunMod.LOGGER.info("🔄 Resetting BattleManager for new game");
        
        this.lastEventTime = System.currentTimeMillis();
        
      
        if (matchmakingTask != null) {
            matchmakingTask.cancel(true);
            matchmakingTask = null;
        }
        if (roomListenerTask != null) {
            roomListenerTask.cancel(true);
            roomListenerTask = null;
        }
        if (publicQueueTask != null) {
            publicQueueTask.cancel(true);
            publicQueueTask = null;
        }
        
      
        this.currentRoom = null;
        this.currentRoomId = null;
        this.isHost = false;
        this.isFrozen = false;
        this.isInBattle = false;
        this.battleEndHandled = false;
        this.loadingTriggered = false;
        
     
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        RandomRunMod.LOGGER.info("✓ BattleManager reset complete");
    }
    
    public void freezePlayer() {
        this.isFrozen = true;
    }
    
    public void unfreezePlayer() {
        this.isFrozen = false;
    }
    
    private String generateRoomCode() {
        Random random = new Random();
        return String.format("%05d", random.nextInt(100000));
    }
    
    private String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    
    private void startCountdown() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
     
        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§e§l3..."), true
                    );
                    MinecraftClient.getInstance().player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.8f
                    );
                }
            });
        }, 0, TimeUnit.MILLISECONDS);
        
      
        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§e§l2..."), true
                    );
                    MinecraftClient.getInstance().player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f
                    );
                }
            });
        }, 1000, TimeUnit.MILLISECONDS);
        
     
        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§e§l1..."), true
                    );
                    MinecraftClient.getInstance().player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.2f
                    );
                }
            });
        }, 2000, TimeUnit.MILLISECONDS);
        
      
        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                unfreezePlayer();
                RandomRunMod.getInstance().getRunDataManager().unfreezeRun();
                
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§a§lGO!"), true
                    );
                    MinecraftClient.getInstance().player.playSound(
                        net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f
                    );
                }
                
                RandomRunMod.LOGGER.info("✓ Countdown complete - speedrun started!");
            });
        }, 3000, TimeUnit.MILLISECONDS);
    }
    
    public boolean isFrozen() {
        return isFrozen;
    }
    
    public boolean isInBattle() {
        return isInBattle;
    }
    
    public boolean isHost() {
        return isHost;
    }
    
    public BattleRoom getCurrentRoom() {
        return currentRoom;
    }
    
    public String getCurrentRoomId() {
        return currentRoomId;
    }
}

package com.randomrun.features.battle.manager;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.IdCompressor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class BattleStateController {
    private static final String LOG_TAG = "BattleState";
    
    private final BattleApiHelper apiHelper;
    private final ScheduledExecutorService scheduler;
    
    private final AtomicBoolean loadingTriggered = new AtomicBoolean(false);
    private final AtomicBoolean waitingForWorldLoad = new AtomicBoolean(false);
    private final AtomicBoolean isFrozen = new AtomicBoolean(false);
    private final AtomicBoolean worldCreationInitiated = new AtomicBoolean(false);
    private final AtomicReference<com.randomrun.core.session.RunSession> scheduledSession = new AtomicReference<>();
    private volatile boolean countdownCancelled = false;
    private ScheduledFuture<?> countdownTask;
    private ScheduledFuture<?> unfreezeTask;
    
    private long clockOffset = 0;
    private long lastCalibration = 0;
    
    public BattleStateController(BattleApiHelper apiHelper, ScheduledExecutorService scheduler) {
        this.apiHelper = apiHelper;
        this.scheduler = scheduler;
    }
    
    public boolean isLoadingTriggered() { return loadingTriggered.get(); }
    public boolean isWaitingForWorldLoad() { return waitingForWorldLoad.get(); }
    public boolean isFrozen() { return isFrozen.get(); }

    public static com.randomrun.features.challenges.classic.world.WorldCreationRequest createWorldRequest(BattleRoom room) {
        Objects.requireNonNull(room, "room");
        String targetId = IdCompressor.decompress(room.getTargetItem());
        Item target = targetId == null || targetId.isEmpty() ? null
            : BuiltInRegistries.ITEM.getValue(Identifier.parse(targetId));
        return new com.randomrun.features.challenges.classic.world.WorldCreationRequest.Builder()
            .targetItem(target).seed(room.getSeed())
            .mode(com.randomrun.features.challenges.classic.world.WorldMode.STANDARD)
            .randomDrop(room.isRandomDrop()).upgrader(room.isUpgrader()).build();
    }
    
    public void setWaitingForWorldLoad(boolean waiting) { 
        this.waitingForWorldLoad.set(waiting); 
    }
    
    public void setStatusLoading(String roomId, BattleRoom currentRoom, boolean isHost) {
        if (roomId == null || currentRoom == null) return;
        
        if (!loadingTriggered.compareAndSet(false, true)) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> update = new HashMap<>();
                update.put("st", "LOADING");
                
                boolean success = apiHelper.updateRoomFields(roomId, update).join();
                if (!success) {
                    loadingTriggered.set(false);
                    CompactLogger.error(LOG_TAG, "Failed to set LOADING status");
                    return;
                }
                
                if (currentRoom.isSharedWorld() && isHost) {
                    Minecraft client = Minecraft.getInstance();
                    if (client != null) {
                        client.execute(() -> {
                            if (!worldCreationInitiated.compareAndSet(false, true)) return;
                            waitingForWorldLoad.set(true);
                            
                            com.randomrun.features.challenges.classic.world.WorldCreationRequest request = createWorldRequest(currentRoom);
                            com.randomrun.features.challenges.classic.world.WorldCreator.getInstance().createWorld(request);
                        });
                    }
                }
            } catch (Exception e) {
                loadingTriggered.set(false);
                CompactLogger.error(LOG_TAG, "Error setting LOADING: " + e.getMessage());
            }
        }, scheduler);
    }
    
    public void setPlayerLoaded(String roomId, BattleRoom currentRoom) {
        if (roomId == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String playerName = Minecraft.getInstance().getUser().getName();
                boolean isHost = currentRoom != null && currentRoom.isHost(playerName);
                
                Map<String, Object> update = new HashMap<>();
                String path = isHost ? "h/" + playerName + "/l" : "g/" + playerName + "/l";
                update.put(path, true);
                
                boolean success = apiHelper.updateRoomFields(roomId, update).join();
                
                if (!success) {
                    CompactLogger.error(LOG_TAG, "Failed to update loaded for: " + playerName);
                    return;
                }
                
                if (currentRoom != null) {
                    currentRoom.setPlayerLoaded(playerName, true);
                    currentRoom.markUpdated();
                }
            } catch (Exception e) {
                CompactLogger.error(LOG_TAG, "Error in setPlayerLoaded: " + e.getMessage());
            }
        }, scheduler);
    }

    public void sendReady(String roomId, BattleRoom currentRoom, boolean isLobby, boolean isHost, Runnable onAllReady) {
        if (currentRoom == null || roomId == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String playerName = Minecraft.getInstance().getUser().getName();
                
                Map<String, Object> updates = new HashMap<>();
                String readyPath = isHost ? "h/" + playerName + "/r" : "g/" + playerName + "/r";
                updates.put(readyPath, true);
                
                if (!isLobby && currentRoom.isPlayerDisconnected(playerName)) {
                    updates.put("dd/" + playerName, false);
                }
                
                boolean success = apiHelper.updateRoomFields(roomId, updates).join();
                
                if (!success) {
                    CompactLogger.error(LOG_TAG, "Failed to update ready for: " + playerName);
                    return;
                }
                
                currentRoom.setPlayerReady(playerName, true);
                if (!isLobby && currentRoom.isPlayerDisconnected(playerName)) {
                    currentRoom.setPlayerDisconnected(playerName, false);
                }
                currentRoom.markUpdated();
                
                if (!isLobby) {
                    sendPlayerMessage("§a✓ Вы готовы! Ожидание других игроков...");
                }
            } catch (Exception e) {
                CompactLogger.error(LOG_TAG, "Error in sendReady: " + e.getMessage());
            }
        }, scheduler);
    }
    
    public void handleBattleStart(String roomId, BattleRoom latestRoom, Runnable onAllReady) {
        try {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("st", "STARTED");
            
            long startDelay = BattleConstants.BATTLE_START_DELAY_MS;
            long serverStartTime = System.currentTimeMillis() + startDelay;
            statusUpdate.put("srv_start", serverStartTime);
            
            if (latestRoom.isSharedWorld()) {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.level != null) {
                    long targetWorldTime = client.level.getGameTime() + 120;
                    statusUpdate.put("swt", targetWorldTime);
                    latestRoom.setSharedWorldStartTime(targetWorldTime);
                }
            }
            
            latestRoom.setServerStartTime(serverStartTime);
            latestRoom.setStatus(BattleRoom.RoomStatus.STARTED);
            
            boolean success = apiHelper.updateRoomFields(roomId, statusUpdate).join();
            
            if (!success) {
                CompactLogger.error(LOG_TAG, "Failed to set STARTED status");
                return;
            }
            
            Minecraft.getInstance().execute(onAllReady);
        } catch (Exception e) {
            CompactLogger.error(LOG_TAG, "Error in handleBattleStart: " + e.getMessage());
        }
    }
    
    public void transitionToFrozen(String roomCode, BattleRoom currentRoom) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> statusUpdate = new HashMap<>();
                statusUpdate.put("st", "FROZEN");
                
                apiHelper.updateRoomFields(roomCode, statusUpdate).join();
                
                if (currentRoom != null) {
                    currentRoom.setStatus(BattleRoom.RoomStatus.FROZEN);
                }
            } catch (Exception e) {
                CompactLogger.error(LOG_TAG, "Error setting FROZEN: " + e.getMessage());
            }
        }, scheduler);
    }
    
    public void handleSeparateWorldCreation(BattleRoom updatedRoom) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        
        client.execute(() -> {
            String decompressedItem = IdCompressor.decompress(updatedRoom.getTargetItem());
            Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(decompressedItem));
            String seed = updatedRoom.getSeed();
            
            if (seed == null || seed.isEmpty() || seed.equals("null")) {
                CompactLogger.error(LOG_TAG, "Invalid seed for separate world");
                sendPlayerMessage("\u00A7cОшибка: неверный сид!");
                Minecraft.getInstance().gui.setScreen(
                    new com.randomrun.ui.screen.main.MainModScreen(
                        new net.minecraft.client.gui.screens.TitleScreen()));
                return;
            }
            
            Minecraft.getInstance().gui.setScreen(null);
            RandomRunMod.getInstance().getRunDataManager().setTargetItem(targetItem);
            
            com.randomrun.features.challenges.classic.world.WorldCreationRequest request = createWorldRequest(updatedRoom);
            com.randomrun.features.challenges.classic.world.WorldCreator.getInstance().createWorld(request);
        });
    }
    
    public void notifyPlayerReadiness(BattleRoom updatedRoom, BattleRoom currentRoom, String playerName) {
        List<String> oldReady = currentRoom != null ? currentRoom.getReadyPlayers() : new ArrayList<>();
        List<String> newReady = updatedRoom.getReadyPlayers();
        
        if (newReady != null) {
            for (String readyPlayer : newReady) {
                if (!oldReady.contains(readyPlayer) && !readyPlayer.equals(playerName)) {
                    sendPlayerMessage("\u00A7a✓ Игрок " + readyPlayer + " готов!");
                }
            }
        }
        
        boolean amIReady = updatedRoom.isPlayerReady(playerName);
        int totalPlayers = updatedRoom.getPlayers().size();
        int readyCount = newReady != null ? newReady.size() : 0;
        
        if (!amIReady && readyCount == totalPlayers - 1) {
            sendPlayerMessage("\u00A7eВсе готовы! Напишите /go для старта", true);
        }
    }

    public void scheduleStart(BattleRoom room, Runnable onUnfreeze) {
        var session = com.randomrun.core.session.RunSessionManager.getInstance().getCurrentSession();
        if (session == null || session.getStatus() != com.randomrun.core.session.RunStatus.FROZEN) return;
        if (scheduledSession.getAndSet(session) == session) return;
        
        countdownCancelled = false;
        calibrateClockIfNeeded(room);
        long delayToStart = calculateStartDelay(room);
        
        startCountdown(delayToStart);
        
        if (delayToStart < 0) delayToStart = 0;

        unfreezeTask = scheduler.schedule(() -> {
            if (countdownCancelled) return;
            
            Minecraft.getInstance().execute(() -> {
                if (countdownCancelled || com.randomrun.core.session.RunSessionManager.getInstance().getCurrentSession() != session) return;
                onUnfreeze.run();
                healAllPlayers();
                
                sendPlayerMessage("§a§lGO!", true);
                playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 2.0f);
                
                RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
                if (runManager != null) {
                    runManager.unfreezeRun();
                }
                com.randomrun.core.session.RunSessionManager.getInstance().unfreezeRun();
            });
        }, delayToStart, TimeUnit.MILLISECONDS);
    }
    
    private void calibrateClockIfNeeded(BattleRoom room) {
        long now = System.currentTimeMillis();
        
        if (room.getStatus() == BattleRoom.RoomStatus.FROZEN) {
            performCalibration(room);
            return;
        }
        
        if (now - lastCalibration < BattleConstants.SYNC_CALIBRATION_INTERVAL_MS) {
            return;
        }
        
        performCalibration(room);
    }
    
    private void performCalibration(BattleRoom room) {
        if (!room.isSharedWorld() && room.getServerStartTime() > 0) {
            long serverTime = room.getServerStartTime();
            long localTime = System.currentTimeMillis();
            long estimatedOffset = serverTime - localTime;
            
            if (Math.abs(estimatedOffset) < BattleConstants.SYNC_MAX_CLOCK_SKEW_MS) {
                if (lastCalibration == 0) {
                    clockOffset = estimatedOffset;
                } else {
                    clockOffset = (long) (clockOffset * 0.7 + estimatedOffset * 0.3);
                }
                lastCalibration = System.currentTimeMillis();
            }
        }
    }

    private long calculateStartDelay(BattleRoom room) {
        if (room.isSharedWorld() && room.getSharedWorldStartTime() > 0) {
            Long sharedDelay = trySharedWorldSync(room);
            if (sharedDelay != null) return sharedDelay;
        }
        
        Long serverDelay = trySeparateWorldSync(room);
        if (serverDelay != null) return serverDelay;
        
        CompactLogger.warn(LOG_TAG, "Using fallback delay");
        return BattleConstants.SYNC_DEFAULT_DELAY_MS;
    }
    
    private Long trySharedWorldSync(BattleRoom room) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            CompactLogger.warn(LOG_TAG, "World unavailable for shared sync");
            return null;
        }
        
        long diffTicks = room.getSharedWorldStartTime() - client.level.getGameTime();
        
        if (Math.abs(diffTicks) >= BattleConstants.SYNC_MAX_TICK_DIFF) {
            CompactLogger.warn(LOG_TAG, "Tick diff too large: " + diffTicks);
            return null;
        }
        
        long delay = diffTicks * 50;
        return delay;
    }
    
    private Long trySeparateWorldSync(BattleRoom room) {
        long serverStartTime = room.getServerStartTime();
        if (serverStartTime <= 0) return null;
        
        long now = System.currentTimeMillis();
        long calculatedDelay = serverStartTime - (now + clockOffset);
        
        if (calculatedDelay > BattleConstants.SYNC_MAX_CLOCK_SKEW_MS || 
            calculatedDelay < BattleConstants.SYNC_MIN_CLOCK_SKEW_MS) {
            CompactLogger.warn(LOG_TAG, "Clock desync: " + calculatedDelay + "ms (offset: " + clockOffset + "ms)");
            return null;
        }
        
        return calculatedDelay;
    }

    private void startCountdown(long totalDelay) {
        if (scheduler == null || scheduler.isShutdown()) {
            CompactLogger.error(LOG_TAG, "Scheduler unavailable for countdown");
            return;
        }
        
        int startCount = BattleConstants.COUNTDOWN_START_NUMBER;
        long firstDelay = totalDelay - (startCount * BattleConstants.COUNTDOWN_INTERVAL_MS);
        
        if (totalDelay < (startCount * BattleConstants.COUNTDOWN_INTERVAL_MS)) {
            startCount = (int) (totalDelay / BattleConstants.COUNTDOWN_INTERVAL_MS);
            firstDelay = totalDelay - (startCount * BattleConstants.COUNTDOWN_INTERVAL_MS);
        }
        
        if (firstDelay < 0) firstDelay = 0;
        
        final int count = startCount;
        if (count > 0) {
            countdownTask = scheduler.schedule(() -> 
                countdownRecursive(count), firstDelay, TimeUnit.MILLISECONDS);
        }
    }
    
    private void countdownRecursive(int count) {
        if (countdownCancelled || count <= 0) return;
        
        float pitch = 0.8f + ((4 - count) * 0.2f);
        showCountdown("§e§l" + count + "...", pitch);
        
        if (count > 1) {
            countdownTask = scheduler.schedule(() -> 
                countdownRecursive(count - 1), BattleConstants.COUNTDOWN_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }
    
    private void showCountdown(String text, float pitch) {
        if (countdownCancelled) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                sendPlayerMessage(text, true);
                playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, pitch);
            });
        }
    }
    
    public void cancelCountdown() {
        countdownCancelled = true;
        if (unfreezeTask != null) unfreezeTask.cancel(false);
        if (countdownTask != null && !countdownTask.isDone()) {
            countdownTask.cancel(false);
        }
    }

    public void freezePlayer() { 
        isFrozen.set(true);
    }
    
    public void unfreezePlayer() { 
        isFrozen.set(false);
    }
    
    public boolean areAllPlayersLoaded(BattleRoom room) {
        if (room == null || room.getPlayers() == null || room.getPlayers().isEmpty()) return false;
        
        for (String player : room.getPlayers()) {
            if (!room.isPlayerLoaded(player)) {
                return false;
            }
        }
        return true;
    }
    
    public void reset() {
        if (unfreezeTask != null) unfreezeTask.cancel(false);
        loadingTriggered.set(false);
        waitingForWorldLoad.set(false);
        isFrozen.set(false);
        worldCreationInitiated.set(false);
        scheduledSession.set(null);
        clockOffset = 0;
        lastCalibration = 0;
        countdownCancelled = false;
        if (countdownTask != null && !countdownTask.isDone()) {
            countdownTask.cancel(false);
        }
    }

    private void healAllPlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        
        if (client.getSingleplayerServer() != null) {
            try {
                var playerManager = client.getSingleplayerServer().getPlayerList();
                for (var serverPlayer : playerManager.getPlayers()) {
                    serverPlayer.setHealth(serverPlayer.getMaxHealth());
                    serverPlayer.getFoodData().setFoodLevel(20);
                    serverPlayer.getFoodData().setSaturation(5.0f);
                    serverPlayer.removeAllEffects();
                }
            } catch (Exception e) {
                CompactLogger.error(LOG_TAG, "Error healing players: " + e.getMessage());
            }
        } else if (client.player != null) {
            client.player.setHealth(client.player.getMaxHealth());
            client.player.getFoodData().setFoodLevel(20);
            client.player.getFoodData().setSaturation(5.0f);
            client.player.removeAllEffects();
        }
    }
    
    private void sendPlayerMessage(String message) {
        sendPlayerMessage(message, false);
    }
    
    private void sendPlayerMessage(String message, boolean actionBar) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    com.randomrun.infrastructure.util.ClientMessages.send(client.player, Component.literal(message), actionBar);
                }
            });
        }
    }
    
    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.playSound(sound, volume, pitch);
                }
            });
        }
    }
}

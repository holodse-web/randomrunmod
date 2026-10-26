package com.randomrun.features.battle.manager;

import com.google.gson.JsonElement;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.network.HttpClient;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class BattleManager {
    private static final String LOG_TAG = "BattleMgr";
    private static volatile BattleManager instance;
    private ScheduledExecutorService scheduler;

    private BattleNetworkManager networkManager;
    private BattleNetworkManager connectionManager;
    private BattleStateController stateController;
    private BattleEventHandler eventHandler;
    private RoomLifecycleManager roomLifecycle;
    private BattleApiClient apiClient;
    private BattleApiHelper apiHelper;
    private DisconnectHandler disconnectHandler;
    private BattleRoomSseListener sseListener;
    private BattleEventsSseListener eventsListener;

    private volatile BattleRoom currentRoom;
    private final Object roomLock = new Object();
    private volatile long lifecycleGeneration;

    private final AtomicBoolean victoryReported = new AtomicBoolean(false);
    private final AtomicBoolean battleStartTriggered = new AtomicBoolean(false);
    private volatile String currentRoomId;
    private volatile boolean isHost;
    private volatile boolean isInBattle;
    private volatile boolean disconnectConfirmed = false;
    private ScheduledFuture<?> pollingTask;
    private int consecutiveErrors = 0;

    private BattleManager() {
        ensureScheduler();
        initializeManagers();
    }

    public static BattleManager getInstance() {
        if (instance == null) {
            synchronized (BattleManager.class) {
                if (instance == null) {
                    instance = new BattleManager();
                }
            }
        }
        return instance;
    }
    
    private void initializeManagers() {
        this.apiClient = new BattleApiClient();
        this.apiClient.setScheduler(scheduler);
        this.apiHelper = BattleApiHelper.INSTANCE;
        this.apiHelper.setScheduler(scheduler);
        this.networkManager = new BattleNetworkManager(scheduler);
        this.connectionManager = networkManager; 
        final long generation = lifecycleGeneration;
        this.networkManager.initializeListeners(room -> {
            if (generation == lifecycleGeneration) handleSseRoomUpdate(room);
        }, () -> {
            if (generation == lifecycleGeneration) handleSseReconnectFailed();
        });
        this.sseListener = this.networkManager.getSseListener();
        this.eventsListener = this.networkManager.getEventsListener();
        this.stateController = new BattleStateController(apiHelper, scheduler);
        this.eventHandler = new BattleEventHandler(scheduler, apiHelper);
        this.roomLifecycle = new RoomLifecycleManager(apiHelper, scheduler);
        this.disconnectHandler = new DisconnectHandler(apiHelper);
    }
    
    public void setAwaitingE4mcDomain(boolean awaiting) {
        networkManager.setAwaitingE4mcDomain(awaiting);
    }
    
    public boolean isAwaitingE4mcDomain() { return networkManager.isAwaitingE4mcDomain(); }
    public String getLastReceivedDomain() { return networkManager.getLastReceivedDomain(); }
    public long getDomainReceivedTime() { return networkManager.getDomainReceivedTime(); }
    public BattleRoom getCurrentRoom() { return currentRoom; }
    public boolean isHost() { return isHost; }
    public boolean isFrozen() { return stateController.isFrozen(); }
    public boolean isLoadingTriggered() { return stateController.isLoadingTriggered(); }
    public boolean isSharedWorld() { return currentRoom != null && currentRoom.isSharedWorld(); }
    public boolean isInBattle() { return isInBattle; }
    public String getCurrentRoomId() { return currentRoomId; }
    
    public boolean isDisconnectConfirmed() { return disconnectConfirmed; }
    public void setDisconnectConfirmed(boolean confirmed) { this.disconnectConfirmed = confirmed; }
    
    public void setManualServerAddress(String addr) {
        connectionManager.setManualServerAddress(addr);
    }

    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean isSharedWorld,
                                                 boolean isPrivate, String password, int maxPlayers, String creationMode, boolean randomDrop) {
        return createRoom(playerName, targetItem, isSharedWorld, isPrivate, password, maxPlayers, creationMode, randomDrop, false);
    }

    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean isSharedWorld,
                                                 boolean isPrivate, String password, int maxPlayers, String creationMode, boolean randomDrop, boolean upgrader) {
        resetForNewGame();
        final long generation = lifecycleGeneration;
        return roomLifecycle.createRoom(playerName, targetItem, isSharedWorld, isPrivate, password,
            maxPlayers, creationMode, randomDrop, upgrader, (room, code, host) -> {
                synchronized (roomLock) {
                    if (generation != lifecycleGeneration) return;
                    setupRoom(room, code, host);
                    startRoomListener(code);
                }
            }).thenApply(code -> generation == lifecycleGeneration ? code : null);
    }

    public CompletableFuture<Boolean> joinRoom(String playerName, String roomCode, String password) {
        return joinRoomDetailed(playerName, roomCode, password).thenApply(RoomJoinResult::joined);
    }

    public CompletableFuture<RoomJoinResult> joinRoomDetailed(String playerName, String roomCode, String password) {
        resetForNewGame();
        final long generation = lifecycleGeneration;
        return roomLifecycle.joinRoomDetailed(playerName, roomCode, password, (room, code, host) -> {
            synchronized (roomLock) {
                if (generation != lifecycleGeneration) return;
                setupRoom(room, code, host);
                startRoomListener(code);
            }
        }).thenApply(result -> generation == lifecycleGeneration ? result : RoomJoinResult.CANCELLED);
    }

    public CompletableFuture<List<BattleRoom>> getAllRooms() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonElement element = HttpClient.getInstance().get("/rooms").join();
                
                if (element != null && element.isJsonArray()) {
                    List<BattleRoom> rooms = new ArrayList<>();
                    for (JsonElement roomElement : element.getAsJsonArray()) {
                        if (roomElement.isJsonObject()) {
                            BattleRoom room = BattleApiHelper.INSTANCE.getRoomGson()
                                .fromJson(roomElement, BattleRoom.class);
                            rooms.add(room);
                        }
                    }
                    return rooms;
                }
                
                return new ArrayList<>();
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("{} Ошибка получения списка комнат", LOG_TAG, e);
                return new ArrayList<>();
            }
        });
    }

    public void onWorldLoaded() {
        if (currentRoom != null && currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED) {
            stateController.scheduleStart(currentRoom, this::unfreezePlayer);
            battleStartTriggered.set(true);
        }
        if (isHost && stateController.isWaitingForWorldLoad() && currentRoom != null) {
            showRoomCode();
            if (currentRoom.isSharedWorld()) {
                final long generation = lifecycleGeneration;
                final String roomId = currentRoomId;
                final BattleNetworkManager hostConnection = connectionManager;
                scheduler.schedule(() -> Minecraft.getInstance().execute(() -> {
                    if (isCurrentRoom(roomId, generation) && isHost) hostConnection.openWorldToLAN();
                }),
                    BattleConstants.WORLD_LOAD_DELAY_SEC, TimeUnit.SECONDS);
            }
            stateController.setWaitingForWorldLoad(false);
        }
    }
    
    public void updateServerAddress(String domain) {
        connectionManager.updateServerAddress(domain);
        saveServerAddressToDatabase(domain);
    }
    
    public void saveServerAddressToDatabase(String domain) {
        if (currentRoomId != null && currentRoom != null) {
            currentRoom.setServerAddress(domain);
            final String roomId = currentRoomId;
            Map<String, Object> updates = new HashMap<>();
            updates.put("ip", domain);
            scheduler.schedule(() ->
                apiHelper.updateRoomFields(roomId, updates).thenAccept(success -> {
                    if (!success) {
                        CompactLogger.error(LOG_TAG, "Failed to update server address");
                    }
                }),
                500, TimeUnit.MILLISECONDS);
        }
    }

    public void setStatusLoading() {
        stateController.setStatusLoading(currentRoomId, currentRoom, isHost);
    }
    
    public void setPlayerLoaded() {
        stateController.setPlayerLoaded(currentRoomId, currentRoom);
    }
    
    public void saveSeedToDatabase(String seed) {
        if (currentRoomId == null || !isHost) return;
        final String roomId = currentRoomId;
        Map<String, Object> updates = new HashMap<>();
        updates.put("s", seed);
        scheduler.schedule(() ->
            apiHelper.updateRoomFields(roomId, updates).thenAccept(success -> {
            }),
            500, TimeUnit.MILLISECONDS);
    }
    
    public void sendLobbyReady() {
        stateController.sendReady(currentRoomId, currentRoom, true, isHost, this::setStatusLoading);
    }
    
    public void sendReady() {
        stateController.sendReady(currentRoomId, currentRoom, false, isHost, () -> {
            if (currentRoom != null) {
                stateController.scheduleStart(currentRoom, this::unfreezePlayer);
            }
        });
    }
    
    public void reportVictory(long time) {
        eventHandler.reportBattleResult(currentRoomId, currentRoom, time, 
            BattleEventHandler.BattleResultType.VICTORY);
    }
    
    public void reportElimination() {
        if (currentRoom == null || !isInBattle) return;
        eventHandler.reportElimination(currentRoomId, currentRoom);
    }
    
    public void reportDefeat(String reason) {
        if (currentRoom == null || !isInBattle) return;
        eventHandler.reportDefeat(currentRoomId, currentRoom, reason);
    }
    
    public void reportAchievement(String achievementId) {
        if (currentRoom == null) return;
        eventHandler.reportAchievement(currentRoomId, achievementId);
    }

    private void startRoomListener(String roomId) {
        final long generation = lifecycleGeneration;
        final BattleRoomSseListener listener = sseListener;
        final BattleEventsSseListener roomEvents = eventsListener;
        if (!isCurrentRoom(roomId, generation)) return;
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        if (sseListener != null) sseListener.stop();
        if (eventsListener != null) eventsListener.stop();

        consecutiveErrors = 0;

        scheduler.schedule(() -> {
            synchronized (roomLock) {
                if (!isCurrentRoom(roomId, generation)) return;
                if (listener != null) listener.start(roomId);
                if (roomEvents != null) roomEvents.start(roomId);
            }
        }, 500, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> {
            synchronized (roomLock) {
                if (!isCurrentRoom(roomId, generation)) return;
                if (listener == null || !listener.isConnected()) {
                    RandomRunMod.LOGGER.warn("{} SSE недоступен, запуск polling", LOG_TAG);
                    startPolling(roomId);
                }
            }
        }, 2500, TimeUnit.MILLISECONDS);
    }
    
    private void startPolling(String roomId) {
        synchronized (roomLock) {
            final long generation = lifecycleGeneration;
            if (!isCurrentRoom(roomId, generation)) return;
            if (pollingTask != null && !pollingTask.isCancelled()) {
                return;
            }
        
            long interval = BattleConstants.POLLING_INTERVAL_FALLBACK_MS;
        
            pollingTask = scheduler.scheduleAtFixedRate(
                () -> pollRoom(roomId, generation),
                0,
                interval,
                TimeUnit.MILLISECONDS
            );
        }
    }
    
    private void handleSseReconnectFailed() {
        RandomRunMod.LOGGER.error("{} SSE переподключение не удалось, переход на polling", LOG_TAG);
        
        if (currentRoomId != null) {
            startPolling(currentRoomId);
        }
    }
    
    private void handleSseRoomUpdate(BattleRoom updatedRoom) {
        if (updatedRoom == null || currentRoomId == null) return;
        try {
            handleRoomUpdate(updatedRoom.getRoomCode(), updatedRoom);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка обработки SSE обновления", LOG_TAG, e);
        }
    }

    private boolean isCurrentRoom(String roomId, long generation) {
        return generation == lifecycleGeneration && isInBattle && Objects.equals(roomId, currentRoomId);
    }

    private void pollRoom(String roomId, long generation) {
        if (!isCurrentRoom(roomId, generation)) return;
        if (sseListener != null && sseListener.isHealthy()) {
            return;
        }
        
        try {
            BattleRoom updatedRoom = apiHelper.getRoomData(roomId).join();
            synchronized (roomLock) {
                if (!isCurrentRoom(roomId, generation)) return;
            
                if (updatedRoom == null) {
                    consecutiveErrors++;
                    if (consecutiveErrors >= BattleConstants.MAX_CONSECUTIVE_ERRORS) {
                        RandomRunMod.LOGGER.error("{} Слишком много ошибок ({}), остановка polling",
                            LOG_TAG, consecutiveErrors);
                        if (pollingTask != null) {
                            pollingTask.cancel(false);
                        }
                    }
                    return;
                }
            
                consecutiveErrors = 0;
                handleRoomUpdate(roomId, updatedRoom);
            }
        } catch (Exception e) {
            synchronized (roomLock) {
                if (!isCurrentRoom(roomId, generation)) return;
                consecutiveErrors++;
                RandomRunMod.LOGGER.error("{} Ошибка polling ({}/{}): {}",
                    LOG_TAG, consecutiveErrors, BattleConstants.MAX_CONSECUTIVE_ERRORS, e.getMessage());
            
                if (consecutiveErrors >= BattleConstants.MAX_CONSECUTIVE_ERRORS) {
                    RandomRunMod.LOGGER.error("{} Критическая ошибка, остановка polling", LOG_TAG);
                    if (pollingTask != null) {
                        pollingTask.cancel(false);
                    }
                }
            }
        }
    }

    private void handleRoomUpdate(String roomId, BattleRoom updatedRoom) {
         final long generation = lifecycleGeneration;
         try {
            synchronized (roomLock) {
                if (!isInBattle || roomId == null || !Objects.equals(roomId, currentRoomId)) return;
                BattleRoom.RoomStatus oldStatus = currentRoom != null ? currentRoom.getStatus() : BattleRoom.RoomStatus.WAITING;
                BattleRoom.RoomStatus newStatus = updatedRoom.getStatus();

                if (isHost && newStatus == BattleRoom.RoomStatus.FROZEN && battleStartTriggered.compareAndSet(false, true)) {
                    boolean allReady = true;
                    for (String player : updatedRoom.getPlayers()) {
                        if (!updatedRoom.isPlayerReady(player)) { allReady = false; break; }
                    }

                    if (allReady && updatedRoom.getPlayers().size() > 1) {
                        Minecraft.getInstance().execute(() -> {
                            if (!isCurrentRoom(roomId, generation)) return;
                            stateController.handleBattleStart(roomId, updatedRoom, () -> {
                                if (isCurrentRoom(roomId, generation)) stateController.scheduleStart(updatedRoom, this::unfreezePlayer);
                            });
                        });
                    } else {
                        battleStartTriggered.set(false);
                    }
                }
                
                processRoomStatusChange(oldStatus, newStatus, updatedRoom, roomId);
                
                if ((updatedRoom.getWinner() != null || 
                     updatedRoom.getStatus() == BattleRoom.RoomStatus.DRAW || 
                     updatedRoom.getStatus() == BattleRoom.RoomStatus.FINISHED) 
                    && !eventHandler.isBattleEndHandled()) {
                    eventHandler.setBattleEndHandled(true);
                    Minecraft.getInstance().execute(() -> {
                        if (isCurrentRoom(roomId, generation)) eventHandler.handleBattleEnd(updatedRoom, isHost, this::stopBattle);
                    });
                }
                
                if (newStatus == BattleRoom.RoomStatus.STARTED) {
                    checkSharedWorldVictory(updatedRoom);
                }
                
                currentRoom = updatedRoom;
            }
         } catch (Exception e) {
             RandomRunMod.LOGGER.error("{} Ошибка обновления состояния комнаты", LOG_TAG, e);
         }
    }

    private void checkSharedWorldVictory(BattleRoom updatedRoom) {
        if (!updatedRoom.isSharedWorld() || updatedRoom.getWinner() != null) return;
        
        if (victoryReported.get()) return;
        
        List<String> allPlayers = updatedRoom.getPlayers();
        List<String> eliminated = updatedRoom.getEliminatedPlayers();
        Map<String, Long> playerTimes = updatedRoom.getPlayerTimes();
        
        String myName = Minecraft.getInstance().getUser().getName();
        
        List<String> stillPlaying = new ArrayList<>();
        for (String p : allPlayers) {
            if (!eliminated.contains(p) && !playerTimes.containsKey(p)) {
                stillPlaying.add(p);
            }
        }
        
        if (stillPlaying.size() == 1 && stillPlaying.get(0).equals(myName) && 
            eliminated.size() > 0 && allPlayers.size() > 1) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            long time = runManager.getCurrentTime();
            if (time <= 0) time = System.currentTimeMillis() - updatedRoom.getStartTime();
            
            if (time > 0 && victoryReported.compareAndSet(false, true)) {
                eventHandler.reportBattleResult(currentRoomId, currentRoom, time, 
                    BattleEventHandler.BattleResultType.VICTORY);
            }
        }
    }

    private void processRoomStatusChange(BattleRoom.RoomStatus oldStatus, BattleRoom.RoomStatus newStatus, 
                                         BattleRoom updatedRoom, String roomId) {
        String playerName = Minecraft.getInstance().getUser().getName();

        if (updatedRoom.isSharedWorld() && currentRoom != null) {
            checkForDisconnections(updatedRoom, currentRoom);
        }
        
        boolean allLoadedNow = stateController.areAllPlayersLoaded(updatedRoom);
        boolean allLoadedBefore = stateController.areAllPlayersLoaded(currentRoom);
        
        if ((newStatus == BattleRoom.RoomStatus.LOADING || newStatus == BattleRoom.RoomStatus.WAITING) &&
            allLoadedNow && !allLoadedBefore) {
            stateController.transitionToFrozen(roomId, currentRoom);
        }
        
        if (updatedRoom.isSharedWorld() && !isHost && updatedRoom.getServerAddress() != null) {
            handleGuestConnection(updatedRoom);
        }

        if (!updatedRoom.isSharedWorld() &&
            (newStatus == BattleRoom.RoomStatus.LOADING ||
             newStatus == BattleRoom.RoomStatus.FROZEN ||
             newStatus == BattleRoom.RoomStatus.STARTED) &&
            oldStatus == BattleRoom.RoomStatus.WAITING) {
            currentRoom = updatedRoom;
            stateController.handleSeparateWorldCreation(updatedRoom);
            return;
        }
        
        if (newStatus == BattleRoom.RoomStatus.FROZEN) {
            stateController.notifyPlayerReadiness(updatedRoom, currentRoom, playerName);
        }
        
        if (newStatus == BattleRoom.RoomStatus.STARTED && oldStatus != BattleRoom.RoomStatus.STARTED && battleStartTriggered.compareAndSet(false, true)) {
            stateController.scheduleStart(updatedRoom, this::unfreezePlayer);
        }
    }
    
    private void checkForDisconnections(BattleRoom updatedRoom, BattleRoom oldRoom) {
        if (oldRoom == null) return;
        
        String myName = Minecraft.getInstance().getUser().getName();

        for (String player : updatedRoom.getPlayers()) {
            boolean wasDisconnected = oldRoom.isPlayerDisconnected(player);
            boolean isDisconnected = updatedRoom.isPlayerDisconnected(player);
            
            if (!wasDisconnected && isDisconnected && !player.equals(myName)) {
                RandomRunMod.LOGGER.warn("{} Игрок отключился: {}", LOG_TAG, player);
                sendPlayerMessage("§c[!] Игрок " + player + " отключился");

                if (updatedRoom.isHost(player) && updatedRoom.isSharedWorld()) {
                    RandomRunMod.LOGGER.error("{} Хост отключился в Shared World!", LOG_TAG);
                    sendPlayerMessage("§c[!] Хост отключился! Битва завершена.");

                    scheduler.schedule(() -> {
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().disconnectWithProgressScreen();
                        });
                    }, 3, TimeUnit.SECONDS);
                }
            }
        }
    }

    private void handleGuestConnection(BattleRoom updatedRoom) {
        final String roomId = currentRoomId;
        final long generation = lifecycleGeneration;
        String serverAddr = updatedRoom.getServerAddress();
        
        if (serverAddr == null || serverAddr.trim().isEmpty()) return;
        
        boolean isNewAddress = connectionManager.getLastConnectionAddress() == null || 
                              !connectionManager.getLastConnectionAddress().equals(serverAddr);
        
        if (isNewAddress) {
            scheduler.schedule(() -> {
                if (isCurrentRoom(roomId, generation)) connectionManager.connectToHost(serverAddr);
            },
                BattleConstants.GUEST_CONNECTION_DELAY_SEC, TimeUnit.SECONDS);
        } else {
            updateSharedWorldItem(updatedRoom);
        }
    }
    
    private void updateSharedWorldItem(BattleRoom updatedRoom) {
        final String roomId = currentRoomId;
        Minecraft.getInstance().execute(() -> {
            if (!isInBattle || !Objects.equals(roomId, currentRoomId) || Minecraft.getInstance().level == null) return;
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            String roomItemId = IdCompressor.decompress(updatedRoom.getTargetItem());
            
            if (roomItemId != null) {
                Item roomItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(roomItemId));
                Item currentRunItem = runManager.getTargetItem();
                
                if (roomItem != null && (currentRunItem != roomItem || 
                    runManager.getStatus() == RunDataManager.RunStatus.INACTIVE)) {
                    runManager.cancelRun();
                    runManager.startNewRun(roomItem, updatedRoom.getServerAddress());
                    
                    if (updatedRoom.getStatus() == BattleRoom.RoomStatus.LOADING || 
                        updatedRoom.getStatus() == BattleRoom.RoomStatus.FROZEN) {
                        freezePlayer();
                    }
                }
                var sessions = com.randomrun.core.session.RunSessionManager.getInstance();
                var session = sessions.getCurrentSession();
                if (roomItem != null && (session == null || session.getTargetItem() != roomItem
                        || !session.isBattle() || !Objects.equals(session.getBattleRoomId(), roomId)
                        || session.isUpgrader() != updatedRoom.isUpgrader())) {
                    sessions.cancelRun();
                    sessions.startRun(roomItem, true, roomId, updatedRoom.getSeed(),
                        updatedRoom.getServerAddress(), updatedRoom.isUpgrader());
                    var created = sessions.getCurrentSession();
                    if (created != null) created.setRandomDrop(updatedRoom.isRandomDrop());
                    if (updatedRoom.getStatus() == BattleRoom.RoomStatus.STARTED) {
                        stateController.scheduleStart(updatedRoom, this::unfreezePlayer);
                    }
                }
            }
        });
    }

    public void stopBattle() {
        stateController.cancelCountdown();
        cancelAllTasks();
        resetAllFlags();
        
        if (RandomRunMod.getInstance().getRunDataManager() != null) {
            RandomRunMod.getInstance().getRunDataManager().cancelRun();
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }
    
    public void resetForNewGame() {
        cancelAllTasks();
        resetAllFlags();

        com.randomrun.features.challenges.classic.world.WorldCreator.clearPendingData();

        com.randomrun.core.session.RunSessionManager.getInstance().cancelRun();
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ensureScheduler();
        initializeManagers();
        
        unfreezePlayer();
        
        if (RandomRunMod.getInstance().getRunDataManager() != null) {
            RandomRunMod.getInstance().getRunDataManager().cancelRun();
        }
    }
    
    public void leaveRoom() {
        final String leavingRoomId = currentRoomId;
        final BattleRoom leavingRoom = currentRoom;
        final String playerName = Minecraft.getInstance().getUser().getName();
        if (leavingRoomId != null && leavingRoom != null && leavingRoom.hasPlayer(playerName)) {
            if (isHost && leavingRoom.isHost(playerName)) {
                apiHelper.deleteRoom(leavingRoomId);
            } else if (leavingRoom.isGuest(playerName)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("g/" + playerName, null);
                apiHelper.updateRoomFields(leavingRoomId, updates);
            }
        }
        
        stateController.cancelCountdown();
        cancelAllTasks();
        resetForNewGame();
    }
    
    public void freezePlayer() {
        stateController.freezePlayer();
    }
    
    public void unfreezePlayer() {
        stateController.unfreezePlayer();
    }

    public void deleteRoom() {
        if (currentRoomId == null || !isHost || currentRoom == null
                || !currentRoom.isHost(Minecraft.getInstance().getUser().getName())) return;
        
        final String roomId = currentRoomId;
        
        apiHelper.deleteRoom(roomId);
    }
    
    public void handlePlayerDisconnect(String playerName) {
        disconnectHandler.handlePlayerDisconnect(playerName, currentRoomId, currentRoom, isHost);
    }
    
    public void handleSelfDisconnect() {
        disconnectHandler.handleSelfDisconnect(currentRoomId, currentRoom, isHost, isInBattle);
        cleanupOnShutdown();
    }

    public void cleanupOnShutdown() {
        disconnectHandler.cleanupOnShutdown(currentRoomId, currentRoom, isHost);
    }

    public CompletableFuture<Boolean> notifyDisconnect(String roomId, BattleRoom room) {
        String name = Minecraft.getInstance().getUser().getName();
        return disconnectHandler.notifyDeparture(roomId, room, room != null && room.isHost(name), name);
    }

    public void shutdown() {
        try {
            stateController.cancelCountdown();
            cancelAllTasks();
            cleanupOnShutdown();
        } finally {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            resetAllFlags();
        }
    }
    
    public void handleDisconnect() {
        disconnectHandler.handleDisconnectDuringBattle(currentRoomId, currentRoom, isHost);
        
        if (isHost) {
            deleteRoom();
        }
        
        stopBattle();
    }

    public CompletableFuture<Boolean> kickPlayer(String roomCode, String playerName) {
        String myName = Minecraft.getInstance().getUser().getName();
        synchronized (roomLock) {
            if (currentRoom == null || !currentRoom.isHost(myName)) {
                RandomRunMod.LOGGER.warn("{} Только хост может кикнуть игрока", LOG_TAG);
                return CompletableFuture.completedFuture(false);
            }
            if (currentRoom.isHost(playerName)) {
                RandomRunMod.LOGGER.warn("{} Нельзя кикнуть хоста", LOG_TAG);
                return CompletableFuture.completedFuture(false);
            }
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("g/" + playerName, null);
        return apiHelper.updateRoomFields(roomCode, updates)
            .orTimeout(2, TimeUnit.SECONDS)
            .thenApply(success -> {
                if (success) {
                    synchronized (roomLock) {
                        if (currentRoom != null) {
                            currentRoom.removePlayer(playerName);
                        }
                    }
                }
                return success;
            }).exceptionally(e -> {
                RandomRunMod.LOGGER.error("{} Ошибка кика игрока", LOG_TAG, e);
                return false;
            });
    }
    
    public CompletableFuture<Boolean> selectItemInRoom(String roomCode, Item targetItem) {
        String playerName = Minecraft.getInstance().getUser().getName();
        synchronized (roomLock) {
            if (currentRoom == null || !currentRoom.isHost(playerName)) {
                RandomRunMod.LOGGER.warn("{} Только хост может выбрать предмет", LOG_TAG);
                return CompletableFuture.completedFuture(false);
            }
            if (currentRoom.hasItemSelected()) {
                RandomRunMod.LOGGER.warn("{} Предмет уже выбран", LOG_TAG);
                return CompletableFuture.completedFuture(false);
            }
        }
        String itemId = IdCompressor.compress(BuiltInRegistries.ITEM.getKey(targetItem).toString());
        Map<String, Object> updates = new HashMap<>();
        updates.put("t", itemId);
        return apiHelper.updateRoomFields(roomCode, updates)
            .orTimeout(2, TimeUnit.SECONDS)
            .thenApply(success -> {
                if (success) {
                    synchronized (roomLock) {
                        if (currentRoom != null) currentRoom.setTargetItem(itemId);
                    }
                } else {
                    RandomRunMod.LOGGER.warn("{} Не удалось обновить предмет", LOG_TAG);
                }
                return success;
            }).exceptionally(e -> {
                RandomRunMod.LOGGER.error("{} Ошибка выбора предмета", LOG_TAG, e);
                return false;
            });
    }

    public CompletableFuture<Item> randomSelectItemInRoom(String roomCode) {
        String playerName = Minecraft.getInstance().getUser().getName();
        synchronized (roomLock) {
            if (currentRoom == null || !currentRoom.isHost(playerName)) {
                RandomRunMod.LOGGER.warn("{} Только хост может выбрать предмет", LOG_TAG);
                return CompletableFuture.completedFuture(null);
            }
        }
        boolean allowUnobtainable = com.randomrun.infrastructure.RandomRunMod.getInstance()
            .getConfig().isAllowUnobtainableItems();
        java.util.List<Item> allItems = com.randomrun.features.challenges.classic.data.ItemDifficulty.getAllItems(allowUnobtainable);
        if (allItems.isEmpty()) return CompletableFuture.completedFuture(null);
        Item randomItem = allItems.get(new java.util.Random().nextInt(allItems.size()));
        String itemId = IdCompressor.compress(BuiltInRegistries.ITEM.getKey(randomItem).toString());
        Map<String, Object> updates = new HashMap<>();
        updates.put("t", itemId);
        return apiHelper.updateRoomFields(roomCode, updates)
            .orTimeout(2, TimeUnit.SECONDS)
            .thenApply(success -> {
                if (success) {
                    synchronized (roomLock) {
                        if (currentRoom != null) currentRoom.setTargetItem(itemId);
                    }
                    return randomItem;
                }
                RandomRunMod.LOGGER.warn("{} Не удалось установить случайный предмет", LOG_TAG);
                return null;
            }).exceptionally(e -> {
                RandomRunMod.LOGGER.error("{} Ошибка случайного выбора предмета", LOG_TAG, e);
                return null;
            });
    }
    
    private void registerPlayer(String playerName) {
        com.randomrun.core.profile.PlayerProfile.load(playerName);
    }
    
    private void setupRoom(BattleRoom room, String roomCode, boolean asHost) {
        this.currentRoom = room;
        this.currentRoomId = roomCode;
        this.isHost = asHost;
        this.isInBattle = true;
        ensureScheduler();
    }
    
    private void ensureScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2,
                Thread.ofPlatform().daemon().name("RandomRun-Battle-", 1).factory());
        }
    }
    
    private void cancelAllTasks() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(true);
            pollingTask = null;
        }
        if (sseListener != null) {
            sseListener.stop();
        }
        if (eventsListener != null) {
            eventsListener.stop();
        }
    }
    
    private void resetAllFlags() {
        synchronized (roomLock) {
            lifecycleGeneration++;
            if (roomLifecycle != null) roomLifecycle.cancelPendingOperations();
            this.currentRoom = null;
            this.currentRoomId = null;
            this.isHost = false;
            this.isInBattle = false;
            this.victoryReported.set(false);
            this.battleStartTriggered.set(false);
            this.disconnectConfirmed = false;
            this.consecutiveErrors = 0;
        
            if (connectionManager != null) connectionManager.reset();
            if (stateController != null) stateController.reset();
        }
    }
    
    private void showRoomCode() {
        Minecraft.getInstance().execute(() -> {
            sendPlayerMessage("\u00A7a[RandomRun] \u00A7fКомната создана! Код: \u00A7e\u00A7l" + currentRoomId);
        });
    }
    
    private void sendPlayerMessage(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                com.randomrun.infrastructure.util.ClientMessages.send(Minecraft.getInstance().player, Component.literal(message), false);
            }
        });
    }
}

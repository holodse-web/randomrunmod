package com.randomrun.features.battle.manager;


import com.randomrun.features.battle.manager.BattleSupport.BattleConstants;
import com.randomrun.features.battle.manager.BattleSupport.BattleRoomUpdates;
import com.randomrun.features.battle.manager.BattleSupport.DisconnectHandler;
import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
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
    private BattleApiHelper apiHelper;
    private DisconnectHandler disconnectHandler;
    private BattleRoomUpdates sseListener;

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
        this.stateController = new BattleStateController(apiHelper, scheduler);
        this.eventHandler = new BattleEventHandler(scheduler, apiHelper);
        this.roomLifecycle = new RoomLifecycleManager(apiHelper);
        this.disconnectHandler = new DisconnectHandler(apiHelper);
    }

    public BattleRoom getCurrentRoom() { return currentRoom; }
    public boolean isHost() { return isHost; }
    public boolean isFrozen() { return stateController.isFrozen(); }
    public boolean isLoadingTriggered() { return stateController.isLoadingTriggered(); }
    public boolean isSharedWorld() { return currentRoom != null && currentRoom.isSharedWorld(); }
    public boolean isInBattle() { return isInBattle; }
    public String getCurrentRoomId() { return currentRoomId; }

    public boolean isDisconnectConfirmed() { return disconnectConfirmed; }
    public void setDisconnectConfirmed(boolean confirmed) { this.disconnectConfirmed = confirmed; }

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
                    BattleConstants.WORLD_LOAD_DELAY_MS, TimeUnit.MILLISECONDS);
            }
            stateController.setWaitingForWorldLoad(false);
        }
    }

    public void saveServerAddressToDatabase(String domain) {
        if (currentRoomId != null && currentRoom != null) {
            currentRoom.setServerAddress(domain);
            final String roomId = currentRoomId;
            Map<String, Object> updates = new HashMap<>();
            updates.put("ip", domain);
            apiHelper.updateRoomFields(roomId, updates).thenAccept(success -> {
                if (!success) CompactLogger.error(LOG_TAG, "Failed to update server address");
            });
        }
    }

    public void startFailed() { battleStartTriggered.set(false); }

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
        apiHelper.updateRoomFields(roomId, updates);
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

    private void startRoomListener(String roomId) {
        final long generation = lifecycleGeneration;
        final BattleRoomUpdates listener = sseListener;
        if (!isCurrentRoom(roomId, generation)) return;
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        if (sseListener != null) sseListener.stop();

        consecutiveErrors = 0;

        scheduler.schedule(() -> {
            synchronized (roomLock) {
                if (!isCurrentRoom(roomId, generation)) return;
                if (listener != null) listener.start(roomId);
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
        if (currentRoomId == null) return;
        if (updatedRoom == null || !updatedRoom.hasPlayer(com.randomrun.network.BackendSession.playerName())) {
            final long expected = lifecycleGeneration;
            Minecraft.getInstance().execute(() -> {
                if (expected != lifecycleGeneration) return;
                if (eventHandler.isBattleEndHandled()) {
                    finishBattle();
                    return;
                }
                stopBattle();
                if (Minecraft.getInstance().level != null) Minecraft.getInstance().disconnectFromWorld(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.room_closed"));
            });
            return;
        }
        handleRoomUpdate(updatedRoom.getRoomCode(), updatedRoom);
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
                        if (isCurrentRoom(roomId, generation)) eventHandler.handleBattleEnd(updatedRoom, isHost, this::finishBattle);
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

        String myName = com.randomrun.network.BackendSession.playerName();

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
        String playerName = com.randomrun.network.BackendSession.playerName();

        if (updatedRoom.isSharedWorld() && currentRoom != null) {
            checkForDisconnections(updatedRoom, currentRoom);
        }

        if (oldStatus != newStatus && (newStatus == BattleRoom.RoomStatus.LOADING || newStatus == BattleRoom.RoomStatus.FROZEN)) {
            com.randomrun.network.RealtimeClient.getInstance().syncClock();
        }

        if (!isHost && oldStatus == BattleRoom.RoomStatus.WAITING && newStatus == BattleRoom.RoomStatus.LOADING) {
            com.randomrun.network.ConnectionStatus.set(com.randomrun.network.ConnectionStatus.Stage.WAITING_HOST);
            com.randomrun.ui.overlay.LoadingCurtain.close(com.randomrun.ui.overlay.LoadingCurtain.Mode.GUEST);
            if (updatedRoom.isSharedWorld()) com.randomrun.network.WebRtcTransport.getInstance().prefetch();
            else stateController.createOwnWorld(updatedRoom);
        }

        boolean allLoadedNow = stateController.areAllPlayersLoaded(updatedRoom);
        boolean allLoadedBefore = stateController.areAllPlayersLoaded(currentRoom);

        if (isHost && newStatus == BattleRoom.RoomStatus.LOADING &&
            allLoadedNow && !allLoadedBefore) {
            stateController.transitionToFrozen(roomId, currentRoom);
        }

        if (updatedRoom.isSharedWorld() && !isHost && updatedRoom.getServerAddress() != null) {
            handleGuestConnection(updatedRoom);
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

        String myName = com.randomrun.network.BackendSession.playerName();

        for (String player : updatedRoom.getPlayers()) {
            boolean wasDisconnected = oldRoom.isPlayerDisconnected(player);
            boolean isDisconnected = updatedRoom.isPlayerDisconnected(player);

            if (!wasDisconnected && isDisconnected && !player.equals(myName)) {
                RandomRunMod.LOGGER.warn("{} Игрок отключился: {}", LOG_TAG, player);
                sendPlayerMessage(com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.battle.player_disconnected", player));

                if (updatedRoom.isHost(player) && updatedRoom.isSharedWorld() && !eventHandler.isBattleEndHandled()) {
                    RandomRunMod.LOGGER.error("{} Хост отключился в Shared World!", LOG_TAG);
                    sendPlayerMessage(com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.battle.host_disconnected"));

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
                BattleConstants.GUEST_CONNECTION_DELAY_MS, TimeUnit.MILLISECONDS);
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
        stopBattle(false);
    }

    public void finishBattle() {
        stopBattle(true);
    }

    private volatile boolean lingeringTransport;

    public void releaseFinishedConnection() {
        if (!lingeringTransport || isInBattle) return;
        lingeringTransport = false;
        com.randomrun.network.WebRtcTransport.getInstance().reset();
    }

    private void stopBattle(boolean keepConnection) {
        stateController.cancelCountdown();
        cancelAllTasks();
        resetAllFlags(keepConnection);

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
        String leaving = currentRoomId;
        if (leaving != null) HttpClient.getInstance().post("/rooms/" + leaving + "/leave", Map.of());
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
                || !currentRoom.isHost(com.randomrun.network.BackendSession.playerName())) return;

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
        String name = com.randomrun.network.BackendSession.playerName();
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
        String myName = com.randomrun.network.BackendSession.playerName();
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
        String playerName = com.randomrun.network.BackendSession.playerName();
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
        String playerName = com.randomrun.network.BackendSession.playerName();
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
    }

    private void resetAllFlags() {
        resetAllFlags(false);
    }

    private void resetAllFlags(boolean keepConnection) {
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

            if (connectionManager != null) {
                if (keepConnection) connectionManager.detach();
                else connectionManager.reset();
            }
            lingeringTransport = keepConnection;
            if (stateController != null) stateController.reset();
        }
    }

    private void showRoomCode() {
        Minecraft.getInstance().execute(() -> {
            sendPlayerMessage(com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.battle.room_created", currentRoomId));
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

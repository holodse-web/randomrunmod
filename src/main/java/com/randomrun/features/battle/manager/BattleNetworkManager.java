package com.randomrun.features.battle.manager;

import com.randomrun.infrastructure.util.CompactLogger;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public class BattleNetworkManager {
    private static final String LOG_TAG = "BattleNet";
    
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.IDLE);
    private final AtomicBoolean awaitingE4mcDomain = new AtomicBoolean(false);
    
    private volatile String lastConnectionAddress = null;
    private volatile String lastReceivedDomain = null;
    private volatile long domainReceivedTime = 0;
    private volatile String manualServerAddress = null;
    private volatile ScheduledFuture<?> currentMonitorTask = null;
    
    private BattleRoomSseListener sseListener;
    private BattleEventsSseListener eventsListener;
    private ScheduledFuture<?> pollingTask;
    private int consecutiveErrors = 0;
    
    public BattleNetworkManager(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    public void initializeListeners(Consumer<BattleRoom> onRoomUpdate, Runnable onReconnectFailed) {
        this.sseListener = new BattleRoomSseListener(onRoomUpdate, onReconnectFailed, scheduler);
        this.eventsListener = new BattleEventsSseListener(scheduler);
    }
    
    public ConnectionState getState() { 
        return state.get(); 
    }
    
    public boolean isAwaitingE4mcDomain() { 
        return awaitingE4mcDomain.get(); 
    }
    
    public String getLastReceivedDomain() { 
        return lastReceivedDomain; 
    }
    
    public long getDomainReceivedTime() { 
        return domainReceivedTime; 
    }
    
    public String getLastConnectionAddress() { 
        return lastConnectionAddress; 
    }
    
    public BattleRoomSseListener getSseListener() {
        return sseListener;
    }
    
    public BattleEventsSseListener getEventsListener() {
        return eventsListener;
    }
    
    public void setAwaitingE4mcDomain(boolean awaiting) {
        awaitingE4mcDomain.set(awaiting);
    }
    
    public void setManualServerAddress(String addr) {
        if (!validateAddress(addr)) {
            CompactLogger.warn(LOG_TAG, "Invalid manual address: " + addr);
            return;
        }
        this.manualServerAddress = addr;
    }
    
    public CompletableFuture<Boolean> openWorldToLAN() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Minecraft client = Minecraft.getInstance();
        
        client.execute(() -> {
            if (client.level == null) {
                CompactLogger.error(LOG_TAG, "World not loaded");
                future.complete(false);
                return;
            }
            
            setAwaitingE4mcDomain(manualServerAddress == null || manualServerAddress.isEmpty());
            
            var server = client.getSingleplayerServer();
            if (server == null) {
                CompactLogger.error(LOG_TAG, "Integrated server unavailable");
                future.complete(false);
                return;
            }
            
            server.setWorldGameType(net.minecraft.world.level.GameType.SURVIVAL);
            if (server.publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, false, 25565)) {
                server.setUsesAuthentication(false);

                if (manualServerAddress != null && !manualServerAddress.isEmpty()) {
                    sendPlayerMessage("§a[RandomRun] §7Сервер запущен");
                    String addressToSave = manualServerAddress;
                    manualServerAddress = null;
                    updateServerAddress(addressToSave);
                } else {
                    sendPlayerMessage("§a[RandomRun] §7Сервер запущен. Ожидание E4MC адреса...");
                    scheduleE4mcTimeout();
                }
                future.complete(true);
            } else {
                CompactLogger.error(LOG_TAG, "Failed to open LAN");
                sendPlayerMessage("§c[RandomRun] §7Ошибка открытия LAN");
                future.complete(false);
            }
        });
        
        return future;
    }
    
    private void scheduleE4mcTimeout() {
        scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                if (isAwaitingE4mcDomain()) {
                    CompactLogger.warn(LOG_TAG, "E4MC timeout " + (BattleConstants.E4MC_TIMEOUT_MS / 1000) + "s");
                    sendPlayerMessage("§c[RandomRun] §7E4MC адрес не получен");
                    sendPlayerMessage("§e[!] §7Решение: используйте VPN (страны Европы)");
                }
            });
        }, BattleConstants.E4MC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    public void updateServerAddress(String domain) {
        if (!validateAddress(domain)) {
            CompactLogger.warn(LOG_TAG, "Invalid address: " + domain);
            return;
        }
        
        awaitingE4mcDomain.set(false);
        this.lastReceivedDomain = domain;
        this.domainReceivedTime = System.currentTimeMillis();
        
        sendPlayerMessage("§a§l[✓] §7IP успешно получен!");
    }
    
    public CompletableFuture<Boolean> connectToHost(String addressStr) {
        return connectToHost(addressStr, false, 0);
    }
    
    private CompletableFuture<Boolean> connectToHost(String addressStr, boolean force, int retryCount) {
        if (!force && state.get() == ConnectionState.CONNECTING) {
            CompactLogger.warn(LOG_TAG, "Connection already in progress");
            return CompletableFuture.completedFuture(false);
        }
        
        if (!validateAddress(addressStr)) {
            CompactLogger.error(LOG_TAG, "Invalid address: " + addressStr);
            return CompletableFuture.completedFuture(false);
        }
        
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        state.set(ConnectionState.CONNECTING);
        lastConnectionAddress = addressStr;
        
        Minecraft.getInstance().execute(() -> {
            try {
                ServerData info = new ServerData("Battle Host", addressStr, ServerData.Type.OTHER);
                ServerAddress address = ServerAddress.parseString(addressStr);
                
                ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), 
                                     Minecraft.getInstance(), address, info, false, null);
                
                monitorConnectionAttempt(addressStr, retryCount, System.currentTimeMillis(), future);
            } catch (Exception e) {
                CompactLogger.error(LOG_TAG, "Connection error: " + e.getMessage());
                state.set(ConnectionState.FAILED);
                
                if (retryCount < BattleConstants.MAX_CONNECTION_RETRY_ATTEMPTS && scheduler != null && !scheduler.isShutdown()) {
                    long delay = calculateRetryDelay(retryCount);
                    scheduler.schedule(() -> connectToHost(addressStr, true, retryCount + 1)
                        .thenAccept(future::complete), delay, TimeUnit.MILLISECONDS);
                } else {
                    CompactLogger.error(LOG_TAG, "Connection attempts exhausted");
                    future.complete(false);
                }
            }
        });
        
        return future;
    }
    
    private void monitorConnectionAttempt(String addressStr, int retryCount, long startTime, 
                                          CompletableFuture<Boolean> future) {
        if (scheduler == null || scheduler.isShutdown()) {
            CompactLogger.error(LOG_TAG, "Scheduler unavailable for monitoring");
            future.complete(false);
            return;
        }
        
        currentMonitorTask = scheduler.scheduleAtFixedRate(() -> {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    state.set(ConnectionState.CONNECTED);
                    if (currentMonitorTask != null) {
                        currentMonitorTask.cancel(false);
                    }
                    future.complete(true);
                    return;
                }
                
                if (Minecraft.getInstance().gui.screen() != null) {
                    String screenName = Minecraft.getInstance().gui.screen().getClass().getSimpleName();
                    if (screenName.contains("Disconnected")) {
                        CompactLogger.warn(LOG_TAG, "Disconnected by server");
                        if (currentMonitorTask != null) {
                            currentMonitorTask.cancel(false);
                        }
                        handleConnectionFailure(addressStr, retryCount, "Disconnected by server", future);
                        return;
                    }
                }
                
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > BattleConstants.E4MC_TIMEOUT_MS) {
                    CompactLogger.warn(LOG_TAG, "Connection timeout " + elapsed + "ms");
                    if (currentMonitorTask != null) {
                        currentMonitorTask.cancel(false);
                    }
                    handleConnectionFailure(addressStr, retryCount, "Timeout", future);
                }
            });
        }, BattleConstants.MONITOR_INTERVAL_MS, BattleConstants.MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    private void handleConnectionFailure(String addressStr, int retryCount, String reason, 
                                        CompletableFuture<Boolean> future) {
        CompactLogger.warn(LOG_TAG, "Connection failed: " + reason);
        state.set(ConnectionState.FAILED);
        
        if (retryCount < BattleConstants.MAX_CONNECTION_RETRY_ATTEMPTS) {
            long delay = calculateRetryDelay(retryCount);
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.schedule(() -> connectToHost(addressStr, true, retryCount + 1)
                    .thenAccept(future::complete), delay, TimeUnit.MILLISECONDS);
            } else {
                CompactLogger.error(LOG_TAG, "Scheduler unavailable for retry");
                future.complete(false);
            }
        } else {
            CompactLogger.error(LOG_TAG, "All connection attempts exhausted");
            future.complete(false);
        }
    }
    
    public void startRoomListener(String roomId) {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        if (sseListener != null) sseListener.stop();
        if (eventsListener != null) eventsListener.stop();

        consecutiveErrors = 0;

        scheduler.schedule(() -> {
            if (sseListener != null) sseListener.start(roomId);
            if (eventsListener != null) eventsListener.start(roomId);
        }, 500, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> {
            if (sseListener == null || !sseListener.isConnected()) {
                CompactLogger.warn(LOG_TAG, "SSE unavailable, starting polling");
                startPolling(roomId);
            }
        }, 2500, TimeUnit.MILLISECONDS);
    }
    
    private void startPolling(String roomId) {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            return;
        }
        
        long interval = BattleConstants.POLLING_INTERVAL_FALLBACK_MS;
        
        pollingTask = scheduler.scheduleAtFixedRate(
            () -> {},
            0,
            interval,
            TimeUnit.MILLISECONDS
        );
    }
    
    public boolean isSseHealthy() {
        return sseListener != null && sseListener.isHealthy();
    }
    
    public void stopAllListeners() {
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
    
    public void reset() {
        if (currentMonitorTask != null && !currentMonitorTask.isDone()) {
            currentMonitorTask.cancel(false);
        }
        stopAllListeners();
        state.set(ConnectionState.IDLE);
        lastConnectionAddress = null;
        awaitingE4mcDomain.set(false);
        lastReceivedDomain = null;
        domainReceivedTime = 0;
        manualServerAddress = null;
        consecutiveErrors = 0;
    }
    
    private boolean validateAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        
        String addr = address.trim();
        if (!BattleConstants.IP_PATTERN.matcher(addr).matches() && 
            !BattleConstants.DOMAIN_PATTERN.matcher(addr).matches()) {
            return false;
        }
        
        if (addr.contains(":")) {
            try {
                int port = Integer.parseInt(addr.substring(addr.lastIndexOf(':') + 1));
                if (port < 1 || port > 65535) {
                    CompactLogger.warn(LOG_TAG, "Invalid port: " + port);
                    return false;
                }
            } catch (NumberFormatException e) {
                CompactLogger.warn(LOG_TAG, "Port parsing error: " + e.getMessage());
                return false;
            }
        }
        
        return true;
    }
    
    private long calculateRetryDelay(int retryCount) {
        return (long) (BattleConstants.CONNECTION_RETRY_DELAY_MS * Math.pow(1.5, retryCount));
    }
    
    private void sendPlayerMessage(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                com.randomrun.infrastructure.util.ClientMessages.send(Minecraft.getInstance().player, Component.literal(message), false);
            }
        });
    }
    
    public enum ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        FAILED
    }
}

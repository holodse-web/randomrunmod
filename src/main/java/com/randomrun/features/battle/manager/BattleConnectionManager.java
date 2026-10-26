package com.randomrun.features.battle.manager;

import com.randomrun.infrastructure.RandomRunMod;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public class BattleConnectionManager {
    private static final String LOG_TAG = "[BattleConnection]";
    
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.IDLE);
    private final AtomicBoolean awaitingE4mcDomain = new AtomicBoolean(false);
    
    private volatile String lastConnectionAddress = null;
    private volatile String lastReceivedDomain = null;
    private volatile long domainReceivedTime = 0;
    private volatile String manualServerAddress = null;
    private volatile ScheduledFuture<?> currentMonitorTask = null;
    
    public BattleConnectionManager(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
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
    
    public void setAwaitingE4mcDomain(boolean awaiting) {
        awaitingE4mcDomain.set(awaiting);
    }
    
    public void setManualServerAddress(String addr) {
        if (!validateAddress(addr)) {
            RandomRunMod.LOGGER.warn("{} Невалидный ручной адрес: {}", LOG_TAG, addr);
            return;
        }
        this.manualServerAddress = addr;
    }
    
    public CompletableFuture<Boolean> openWorldToLAN() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Minecraft client = Minecraft.getInstance();
        
        client.execute(() -> {
            if (client.level == null) {
                RandomRunMod.LOGGER.error("{} Мир не загружен", LOG_TAG);
                future.complete(false);
                return;
            }
            
            setAwaitingE4mcDomain(manualServerAddress == null || manualServerAddress.isEmpty());
            
            var server = client.getSingleplayerServer();
            if (server == null) {
                RandomRunMod.LOGGER.error("{} Интегрированный сервер недоступен", LOG_TAG);
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
                    BattleManager.getInstance().saveServerAddressToDatabase(addressToSave);
                } else {
                    sendPlayerMessage("§a[RandomRun] §7Сервер запущен. Ожидание E4MC адреса...");
                    scheduleE4mcTimeout();
                }
                future.complete(true);
            } else {
                RandomRunMod.LOGGER.error("{} Не удалось открыть LAN", LOG_TAG);
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
                    RandomRunMod.LOGGER.warn("{} E4MC таймаут {}с", LOG_TAG, BattleConstants.E4MC_TIMEOUT_MS / 1000);
                    sendPlayerMessage("§c[RandomRun] §7E4MC адрес не получен");
                    sendPlayerMessage("§e[!] §7Решение: используйте VPN (страны Европы)");
                }
            });
        }, BattleConstants.E4MC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    public void updateServerAddress(String domain) {
        if (!validateAddress(domain)) {
            RandomRunMod.LOGGER.warn("{} Невалидный адрес: {}", LOG_TAG, domain);
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
            RandomRunMod.LOGGER.warn("{} Подключение уже выполняется", LOG_TAG);
            return CompletableFuture.completedFuture(false);
        }
        
        if (!validateAddress(addressStr)) {
            RandomRunMod.LOGGER.error("{} Невалидный адрес: {}", LOG_TAG, addressStr);
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
                RandomRunMod.LOGGER.error("{} Ошибка подключения: {}", LOG_TAG, e.getMessage());
                state.set(ConnectionState.FAILED);
                
                if (retryCount < BattleConstants.MAX_CONNECTION_RETRY_ATTEMPTS && scheduler != null && !scheduler.isShutdown()) {
                    long delay = calculateRetryDelay(retryCount);
                    scheduler.schedule(() -> connectToHost(addressStr, true, retryCount + 1)
                        .thenAccept(future::complete), delay, TimeUnit.MILLISECONDS);
                } else {
                    RandomRunMod.LOGGER.error("{} Исчерпаны попытки подключения", LOG_TAG);
                    future.complete(false);
                }
            }
        });
        
        return future;
    }
    
    private void monitorConnectionAttempt(String addressStr, int retryCount, long startTime, 
                                          CompletableFuture<Boolean> future) {
        if (scheduler == null || scheduler.isShutdown()) {
            RandomRunMod.LOGGER.error("{} Scheduler недоступен для мониторинга", LOG_TAG);
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
                        RandomRunMod.LOGGER.warn("{} Отключен сервером", LOG_TAG);
                        if (currentMonitorTask != null) {
                            currentMonitorTask.cancel(false);
                        }
                        handleConnectionFailure(addressStr, retryCount, "Отключен сервером", future);
                        return;
                    }
                }
                
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > BattleConstants.E4MC_TIMEOUT_MS) {
                    RandomRunMod.LOGGER.warn("{} Таймаут подключения {}мс", LOG_TAG, elapsed);
                    if (currentMonitorTask != null) {
                        currentMonitorTask.cancel(false);
                    }
                    handleConnectionFailure(addressStr, retryCount, "Таймаут", future);
                }
            });
        }, BattleConstants.MONITOR_INTERVAL_MS, BattleConstants.MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    private void handleConnectionFailure(String addressStr, int retryCount, String reason, 
                                        CompletableFuture<Boolean> future) {
        RandomRunMod.LOGGER.warn("{} Подключение не удалось: {}", LOG_TAG, reason);
        state.set(ConnectionState.FAILED);
        
        if (retryCount < BattleConstants.MAX_CONNECTION_RETRY_ATTEMPTS) {
            long delay = calculateRetryDelay(retryCount);
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.schedule(() -> connectToHost(addressStr, true, retryCount + 1)
                    .thenAccept(future::complete), delay, TimeUnit.MILLISECONDS);
            } else {
                RandomRunMod.LOGGER.error("{} Scheduler недоступен для retry", LOG_TAG);
                future.complete(false);
            }
        } else {
            RandomRunMod.LOGGER.error("{} Исчерпаны все попытки подключения", LOG_TAG);
            future.complete(false);
        }
    }
    
    public void reset() {
        if (currentMonitorTask != null && !currentMonitorTask.isDone()) {
            currentMonitorTask.cancel(false);
        }
        state.set(ConnectionState.IDLE);
        lastConnectionAddress = null;
        awaitingE4mcDomain.set(false);
        lastReceivedDomain = null;
        domainReceivedTime = 0;
        manualServerAddress = null;
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
                    RandomRunMod.LOGGER.warn("{} Невалидный порт: {}", LOG_TAG, port);
                    return false;
                }
            } catch (NumberFormatException e) {
                RandomRunMod.LOGGER.warn("{} Ошибка парсинга порта: {}", LOG_TAG, e.getMessage());
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

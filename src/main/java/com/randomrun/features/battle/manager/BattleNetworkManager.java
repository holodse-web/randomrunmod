package com.randomrun.features.battle.manager;


import com.randomrun.features.battle.manager.BattleSupport.BattleRoomUpdates;
import com.randomrun.network.WebRtcTransport;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class BattleNetworkManager {
    public enum ConnectionState { IDLE, CONNECTING, CONNECTED, FAILED }
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.IDLE);
    private BattleRoomUpdates updates;
    private volatile String lastConnectionAddress;
    private volatile long generation;
    public BattleNetworkManager(ScheduledExecutorService scheduler) { this.scheduler = scheduler; }
    public void initializeListeners(Consumer<BattleRoom> update, Runnable failed) { updates = new BattleRoomUpdates(update, failed, scheduler); }
    public BattleRoomUpdates getSseListener() { return updates; }
    public ConnectionState getState() { return state.get(); }
    public String getLastConnectionAddress() { return lastConnectionAddress; }
    public CompletableFuture<Boolean> openWorldToLAN() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        long expected = generation;
        Minecraft.getInstance().execute(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null || expected != generation) { result.complete(false); return; }
            try {
                int port;
                try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = available.getLocalPort(); }
                WebRtcTransport.getInstance().setPublishing(true);
                boolean opened;
                try { opened = server.publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, false, port); }
                finally { WebRtcTransport.getInstance().setPublishing(false); }
                if (!opened) { result.complete(false); return; }
                server.setUsesAuthentication(false);
                WebRtcTransport.getInstance().host(port).whenComplete((ignored, error) -> {
                    if (error != null || expected != generation) { result.complete(false); return; }
                    BattleManager manager = BattleManager.getInstance();
                    manager.saveServerAddressToDatabase("webrtc:" + manager.getCurrentRoomId());
                    result.complete(true);
                });
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        result.whenComplete((success, error) -> { if (error != null || !Boolean.TRUE.equals(success)) failed(expected, true); });
        return result;
    }
    public CompletableFuture<Boolean> connectToHost(String marker) {
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || !("webrtc:" + room.getRoomCode()).equals(marker)) return CompletableFuture.completedFuture(false);
        if (!state.compareAndSet(ConnectionState.IDLE, ConnectionState.CONNECTING) && !state.compareAndSet(ConnectionState.FAILED, ConnectionState.CONNECTING)) return CompletableFuture.completedFuture(false);
        lastConnectionAddress = marker;
        long expected = generation;
        return WebRtcTransport.getInstance().connect(room).thenApply(endpoint -> {
            if (expected != generation) return false;
            Minecraft.getInstance().execute(() -> {
                if (expected != generation) return;
                String host = endpoint.getAddress().getHostAddress();
                String address = (host.contains(":") ? "[" + host + "]" : host) + ":" + endpoint.getPort();
                var client = Minecraft.getInstance();
                com.randomrun.network.ConnectionStatus.set(com.randomrun.network.ConnectionStatus.Stage.JOINING);
                ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(address), new ServerData("RandomRun", address, ServerData.Type.OTHER), false, null);
                state.set(ConnectionState.CONNECTED);
            });
            return true;
        }).exceptionally(error -> { failed(expected, false); return false; });
    }
    private void failed(long expected, boolean host) {
        Minecraft.getInstance().execute(() -> {
            if (expected != generation) return;
            state.set(ConnectionState.FAILED);
            boolean relay = WebRtcTransport.getInstance().isRelayAvailable();
            String message = host ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.connection.host_failed")
                : com.randomrun.infrastructure.util.LanguageManager.translate(relay ? "randomrun.connection.guest_failed" : "randomrun.connection.guest_failed_turn");
            BattleManager.getInstance().leaveRoom();
            var client = Minecraft.getInstance();
            if (client.level != null) client.disconnectFromWorld(net.minecraft.network.chat.Component.literal(message));
            client.gui.setScreen(new net.minecraft.client.gui.screens.DisconnectedScreen(
                new com.randomrun.ui.screen.main.MainModScreen(new net.minecraft.client.gui.screens.TitleScreen()),
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.connection.title"), net.minecraft.network.chat.Component.literal(message)));
        });
    }
    public void startRoomListener(String id) { if (updates != null) updates.start(id); }
    public boolean isSseHealthy() { return updates != null && updates.isHealthy(); }
    public void stopAllListeners() { if (updates != null) updates.stop(); }
    public void detach() { generation++; stopAllListeners(); state.set(ConnectionState.IDLE); lastConnectionAddress = null; }
    public void reset() { generation++; stopAllListeners(); state.set(ConnectionState.IDLE); lastConnectionAddress = null; WebRtcTransport.getInstance().reset(); }
}

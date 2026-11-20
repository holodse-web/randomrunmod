package com.randomrun.network;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;

import com.google.gson.*;
import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebRtcTransport {
    private static final WebRtcTransport INSTANCE = new WebRtcTransport();
    private static final long RECOVERY_GRACE_MS = 15000, RESTART_DELAY_MS = 1500, RESTART_INTERVAL_MS = 4000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "RandomRun-WebRTC"); t.setDaemon(true); return t; });
    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> expectedNames = new ConcurrentHashMap<>();
    private PeerConnectionFactory factory;
    private HeadlessAudioDeviceModule audio;
    private RTCConfiguration config;
    private volatile int hostPort;
    private volatile WeakReference<net.minecraft.server.MinecraftServer> publishedServer = new WeakReference<>(null);
    private volatile boolean publishing;
    private volatile boolean relayAvailable;
    private RealtimeClient.Subscription signals;
    private volatile long generation;
    private WebRtcTransport() {}
    public static WebRtcTransport getInstance() { return INSTANCE; }
    public boolean isPublishing() { return publishing; }
    public void setPublishing(boolean value) { publishing = value; }
    public void guardPublishedServer(net.minecraft.server.MinecraftServer server) {
        publishedServer = new WeakReference<>(server);
    }

    public boolean requiresPeerIdentity(net.minecraft.server.MinecraftServer server) {
        return publishedServer.get() == server;
    }
    public boolean isRelayAvailable() { return relayAvailable; }
    public String expectedPlayer(SocketAddress address) { return address instanceof InetSocketAddress remote && remote.getAddress().isLoopbackAddress() ? expectedNames.get(remote.getPort()) : null; }
    public CompletableFuture<Void> host(int port) {
        long expected = generation;
        return prepare().thenAcceptAsync(ignored -> { if (expected != generation) throw new CancellationException(); hostPort = port; }, executor);
    }
    public void prefetch() {
        prepare().exceptionally(error -> null);
    }
    private CompletableFuture<Void> prepare() {
        if (config != null && signals != null) return CompletableFuture.completedFuture(null);
        long expected = generation;
        return HttpClient.getInstance().post("/ice", Map.of()).thenAcceptAsync(value -> {
            if (expected != generation) throw new CancellationException();
            config = new RTCConfiguration();
            relayAvailable = value.getAsJsonObject().get("relayAvailable").getAsBoolean();
            if (Boolean.getBoolean("randomrun.rtcRelayOnly")) config.iceTransportPolicy = RTCIceTransportPolicy.RELAY;
            for (JsonElement element : value.getAsJsonObject().getAsJsonArray("iceServers")) {
                JsonObject object = element.getAsJsonObject(); RTCIceServer server = new RTCIceServer();
                JsonElement urls = object.get("urls");
                if (urls.isJsonArray()) for (JsonElement url : urls.getAsJsonArray()) server.urls.add(url.getAsString());
                else server.urls.add(urls.getAsString());
                if (object.has("username")) server.username = object.get("username").getAsString();
                if (object.has("credential")) server.password = object.get("credential").getAsString();
                config.iceServers.add(server);
            }
            if (factory == null) { audio = new HeadlessAudioDeviceModule(); factory = PeerConnectionFactory.builder().setAudioDeviceModule(audio).build(); }
            if (signals == null) signals = RealtimeClient.getInstance().subscribeSignals(message -> executor.execute(() -> receive(message)));
        }, executor);
    }
    public CompletableFuture<InetSocketAddress> connect(BattleRoom room) {
        long expected = generation;
        ConnectionStatus.set(ConnectionStatus.Stage.PREPARING);
        return prepare().thenComposeAsync(ignored -> {
            if (expected != generation) return CompletableFuture.failedFuture(new CancellationException());
            ConnectionStatus.set(ConnectionStatus.Stage.SIGNALING);
            String hostId = room.getHostId();
            if (hostId == null) return CompletableFuture.failedFuture(new IllegalStateException("Room has no host identity"));
            String id = UUID.randomUUID().toString();
            Peer peer = new Peer(id, hostId, room.getHost(), false, expected);
            peers.put(id, peer);
            RTCDataChannelInit init = new RTCDataChannelInit(); init.ordered = true;
            peer.channel(peer.connection.createDataChannel(RtcTcpBridge.CHANNEL_LABEL, init));
            peer.connection.createOffer(new RTCOfferOptions(), peer.description("offer"));
            return peer.endpoint.orTimeout(40, TimeUnit.SECONDS).whenComplete((address, error) -> { if (error != null) peer.close(); });
        }, executor);
    }
    private void receive(JsonObject message) {
        try {
            BattleRoom room = BattleManager.getInstance().getCurrentRoom();
            if (room == null || !room.getRoomCode().equals(message.get("room").getAsString())) return;
            String id = message.get("connection").getAsString(), remote = message.get("peer").getAsString(), kind = message.get("kind").getAsString();
            Peer peer = peers.get(id);
            if (kind.equals("offer")) {
                if (hostPort <= 0 || !BattleManager.getInstance().isHost()) return;
                String name = room.memberName(remote);
                if (name == null || remote.equals(BackendSession.current().id())) return;
                if (peer != null && peer.incoming && peer.remote.equals(remote) && peer.generation == generation && !peer.closed.get()) {
                    log("ICE restart requested by {}", name);
                } else {
                    for (Peer previous : peers.values()) if (previous.remote.equals(remote)) previous.close();
                    peer = new Peer(id, remote, name, true, generation);
                    peers.put(id, peer);
                }
            }
            if (peer == null || !peer.remote.equals(remote) || peer.generation != generation) return;
            JsonObject data = message.has("data") && message.get("data").isJsonObject() ? message.getAsJsonObject("data") : new JsonObject();
            if (kind.equals("offer") || kind.equals("answer")) peer.remoteDescription(kind, data.get("sdp").getAsString());
            else if (kind.equals("candidate")) peer.candidate(new RTCIceCandidate(data.get("mid").getAsString(), data.get("line").getAsInt(), data.get("candidate").getAsString()));
            else if (kind.equals("restart")) { if (!peer.incoming) peer.requestRestart(); }
            else if (kind.equals("close")) peer.close();
        } catch (Exception error) { log("Signal rejected: {}", error.getClass().getSimpleName()); }
    }
    public synchronized void reset() {
        generation++; hostPort = 0; publishing = false;
        if (signals != null) signals.close(); signals = null;
        for (Peer peer : List.copyOf(peers.values())) peer.close();
        peers.clear(); expectedNames.clear(); config = null; relayAvailable = false;
    }
    public void shutdown() {
        reset();
        CompletableFuture.runAsync(() -> {
            if (factory != null) { factory.dispose(); factory = null; }
            if (audio != null) { audio.dispose(); audio = null; }
        }, executor).join();
        executor.shutdown();
    }
    private final class Peer implements AutoCloseable {
        final String id, remote, name;
        final long generation;
        final boolean incoming;
        final RTCPeerConnection connection;
        final CompletableFuture<InetSocketAddress> endpoint = new CompletableFuture<>();
        final AtomicBoolean closed = new AtomicBoolean();
        final List<RTCIceCandidate> candidates = new ArrayList<>();
        boolean remoteSet, localSent;
        boolean established;
        long lostAt, lossEpisode, lastRestart, restoredAt;
        final List<JsonObject> localCandidates = new ArrayList<>();
        volatile RtcTcpBridge bridge;
        final Object tcpLock = new Object();
        ServerSocket listener;
        Socket connectingSocket;
        int localPort;
        Peer(String id, String remote, String name, boolean incoming, long generation) {
            this.id = id; this.remote = remote; this.name = name; this.incoming = incoming; this.generation = generation;
            connection = factory.createPeerConnection(config, new PeerConnectionObserver() {
                public void onIceCandidate(RTCIceCandidate candidate) {
                    JsonObject data = new JsonObject(); data.addProperty("mid", candidate.sdpMid); data.addProperty("line", candidate.sdpMLineIndex); data.addProperty("candidate", candidate.sdp);
                    executor.execute(() -> {
                        if (closed.get()) return;
                        if (localSent) send("candidate", data);
                        else if (localCandidates.size() < 128) localCandidates.add(data);
                        else close();
                    });
                }
                public void onDataChannel(RTCDataChannel channel) { executor.execute(() -> { if (incoming && !closed.get() && bridge == null && channel.getLabel().equals(RtcTcpBridge.CHANNEL_LABEL)) channel(channel); else { channel.close(); channel.dispose(); close(); } }); }
                public void onConnectionChange(RTCPeerConnectionState state) {
                    if (state == RTCPeerConnectionState.DISCONNECTED || state == RTCPeerConnectionState.FAILED) log("Peer {}: {}", name, state);
                    executor.execute(() -> connectionChanged(state)); }
            });
        }
        void send(String kind, JsonObject data) {
            if (closed.get()) return;
            RealtimeClient.getInstance().sendSignal(remote, id, kind, data).exceptionally(error -> {
                executor.execute(() -> { if (!established) close(); else if (!closed.get()) log("Could not send {} to {}: {}", kind, name, error.getClass().getSimpleName()); });
                return null;
            });
        }
        void connectionChanged(RTCPeerConnectionState state) {
            if (closed.get()) return;
            if (state == RTCPeerConnectionState.CONNECTED) {
                if (lostAt != 0) {
                    log("Reconnected to {} after {} ms", name, System.currentTimeMillis() - lostAt);
                    restoredAt = System.currentTimeMillis();
                }
                established = true; lostAt = 0; lossEpisode++;
                return;
            }
            if (state == RTCPeerConnectionState.CLOSED) { close(); return; }
            if (state != RTCPeerConnectionState.DISCONNECTED && state != RTCPeerConnectionState.FAILED) return;
            if (!established) { if (state == RTCPeerConnectionState.FAILED) close(); return; }
            if (lostAt != 0) return;
            lostAt = System.currentTimeMillis();
            long episode = ++lossEpisode;
            log("Reconnecting to {} ({} s)", name, RECOVERY_GRACE_MS / 1000);
            later(RECOVERY_GRACE_MS, () -> {
                if (closed.get() || lossEpisode != episode || lostAt == 0) return;
                log("Reconnect timed out for {}", name);
                close();
            });
            later(RESTART_DELAY_MS, () -> recover(episode));
        }
        void recover(long episode) {
            if (closed.get() || lossEpisode != episode || lostAt == 0) return;
            if (incoming) send("restart", new JsonObject()); else restart();
            later(RESTART_INTERVAL_MS, () -> recover(episode));
        }
        void requestRestart() {
            if (closed.get() || !established || System.currentTimeMillis() - restoredAt < 3000) return;
            restart();
        }
        void restart() {
            long now = System.currentTimeMillis();
            if (now - lastRestart < 3000) return;
            lastRestart = now;
            remoteSet = false; candidates.clear();
            localSent = false; localCandidates.clear();
            RTCOfferOptions options = new RTCOfferOptions(); options.iceRestart = true;
            connection.createOffer(options, description("offer"));
        }
        void negotiationFailed(String step, String error) {
            executor.execute(() -> {
                if (closed.get()) return;
                if (established) log("Recovery step {} failed for {}: {}", step, name, error);
                else close();
            });
        }
        CreateSessionDescriptionObserver description(String kind) {
            return new CreateSessionDescriptionObserver() {
                public void onSuccess(RTCSessionDescription sdp) {
                    connection.setLocalDescription(sdp, new SetSessionDescriptionObserver() {
                        public void onSuccess() { executor.execute(() -> {
                            JsonObject data = new JsonObject(); data.addProperty("sdp", sdp.sdp); send(kind, data);
                            localSent = true;
                            for (JsonObject candidate : localCandidates) send("candidate", candidate);
                            localCandidates.clear();
                        }); }
                        public void onFailure(String error) { negotiationFailed("set local " + kind, error); }
                    });
                }
                public void onFailure(String error) { negotiationFailed("create " + kind, error); }
            };
        }
        void remoteDescription(String kind, String sdp) {
            remoteSet = false;
            connection.setRemoteDescription(new RTCSessionDescription(kind.equals("offer") ? RTCSdpType.OFFER : RTCSdpType.ANSWER, sdp), new SetSessionDescriptionObserver() {
                public void onSuccess() { executor.execute(() -> {
                    if (closed.get()) return;
                    remoteSet = true; for (RTCIceCandidate candidate : candidates) connection.addIceCandidate(candidate); candidates.clear();
                    if (kind.equals("offer")) { localSent = false; localCandidates.clear(); connection.createAnswer(new RTCAnswerOptions(), description("answer")); }
                }); }
                public void onFailure(String error) { negotiationFailed("set remote " + kind, error); }
            });
        }
        void candidate(RTCIceCandidate value) { if (remoteSet) connection.addIceCandidate(value); else if (candidates.size() < 128) candidates.add(value); else close(); }
        void channel(RTCDataChannel channel) {
            RtcTcpBridge current;
            try {
                current = new RtcTcpBridge(channel, this::close);
                bridge = current;
            } catch (Exception error) { close(); return; }
            if (closed.get()) {
                current.close();
                return;
            }
            current.opened().thenRun(() -> Thread.ofVirtual().name("RandomRun-WebRTC-socket").start(() -> openSocket(current)));
        }

        private void openSocket(RtcTcpBridge current) {
            Socket socket = null;
            ServerSocket accepting = null;
            boolean attached = false;
            try {
                synchronized (tcpLock) {
                    if (closed.get() || generation != WebRtcTransport.this.generation) return;
                    if (incoming) {
                        socket = new Socket();
                        connectingSocket = socket;
                        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                        localPort = socket.getLocalPort();
                        expectedNames.put(localPort, name);
                    } else {
                        accepting = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                        listener = accepting;
                        accepting.setSoTimeout(30000);
                    }
                }
                if (incoming) {
                    socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), hostPort), 10000);
                } else {
                    if (closed.get()) return;
                    ConnectionStatus.set(ConnectionStatus.Stage.TUNNEL);
                    endpoint.complete(new InetSocketAddress(InetAddress.getLoopbackAddress(), accepting.getLocalPort()));
                    socket = accepting.accept();
                }
                synchronized (tcpLock) {
                    if (closed.get() || generation != WebRtcTransport.this.generation) return;
                    current.attach(socket);
                    connectingSocket = null;
                    attached = true;
                }
            } catch (Exception error) { close(); }
            finally {
                try { if (accepting != null) accepting.close(); } catch (java.io.IOException ignored) {}
                try { if (!attached && socket != null) socket.close(); } catch (java.io.IOException ignored) {}
            }
        }

        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            peers.remove(id, this);
            synchronized (tcpLock) {
                expectedNames.remove(localPort, name);
                try { if (listener != null) listener.close(); } catch (java.io.IOException ignored) {}
                try { if (connectingSocket != null) connectingSocket.close(); } catch (java.io.IOException ignored) {}
            }
            endpoint.completeExceptionally(new java.io.IOException("WebRTC connection closed"));
            if (bridge != null) bridge.close();
            executor.execute(() -> { try { if (bridge != null) bridge.disposed().join(); connection.close(); } catch (Exception ignored) { } });
        }
    }

    private void later(long delayMillis, Runnable task) {
        try { CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS, executor).execute(task); }
        catch (RejectedExecutionException ignored) { }
    }
    private static void log(String message, Object... args) { com.randomrun.infrastructure.util.CompactLogger.warn("[WebRTC] " + message, args); }

    public static final class ConnectionStatus {
        public enum Stage {
            IDLE, WAITING_HOST, PREPARING, SIGNALING, TUNNEL, JOINING;

            public String key() {
                return "randomrun.curtain.stage." + name().toLowerCase();
            }
        }

        private static volatile Stage stage = Stage.IDLE;
        private static volatile long since = System.currentTimeMillis();

        private ConnectionStatus() {}

        public static void set(Stage value) {
            if (stage == value) return;
            stage = value;
            since = System.currentTimeMillis();
        }

        public static Stage get() {
            return stage;
        }

        public static long since() {
            return since;
        }
    }
}

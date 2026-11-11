package com.randomrun.network;

import com.google.gson.*;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebRtcTransport {
    private static final WebRtcTransport INSTANCE = new WebRtcTransport();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "RandomRun-WebRTC"); t.setDaemon(true); return t; });
    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> expectedNames = new ConcurrentHashMap<>();
    private PeerConnectionFactory factory;
    private HeadlessAudioDeviceModule audio;
    private RTCConfiguration config;
    private volatile int hostPort;
    private volatile boolean publishing;
    private volatile boolean relayAvailable;
    private RealtimeClient.Subscription signals;
    private volatile long generation;
    private WebRtcTransport() {}
    public static WebRtcTransport getInstance() { return INSTANCE; }
    public boolean isPublishing() { return publishing; }
    public void setPublishing(boolean value) { publishing = value; }
    public boolean isHosting() { return hostPort > 0; }
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
                for (Peer previous : peers.values()) if (previous.remote.equals(remote)) previous.close();
                peer = new Peer(id, remote, name, true, generation);
                peers.put(id, peer);
            }
            if (peer == null || !peer.remote.equals(remote) || peer.generation != generation) return;
            JsonObject data = message.has("data") && message.get("data").isJsonObject() ? message.getAsJsonObject("data") : new JsonObject();
            if (kind.equals("offer") || kind.equals("answer")) peer.remoteDescription(kind, data.get("sdp").getAsString());
            else if (kind.equals("candidate")) peer.candidate(new RTCIceCandidate(data.get("mid").getAsString(), data.get("line").getAsInt(), data.get("candidate").getAsString()));
            else if (kind.equals("close")) peer.close();
        } catch (Exception error) { com.randomrun.infrastructure.util.CompactLogger.warn("WebRTC signal rejected: {}", error.getClass().getSimpleName()); }
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
        final List<JsonObject> localCandidates = new ArrayList<>();
        RtcTcpBridge bridge;
        ServerSocket listener;
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
                    if (state == RTCPeerConnectionState.DISCONNECTED || state == RTCPeerConnectionState.FAILED) com.randomrun.infrastructure.util.CompactLogger.warn("WebRTC connection {} with {}", state, name);
                    if (state == RTCPeerConnectionState.FAILED || state == RTCPeerConnectionState.CLOSED) executor.execute(Peer.this::close); }
            });
        }
        void send(String kind, JsonObject data) { if (!closed.get()) RealtimeClient.getInstance().sendSignal(remote, id, kind, data).exceptionally(error -> { close(); return null; }); }
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
                        public void onFailure(String error) { close(); }
                    });
                }
                public void onFailure(String error) { close(); }
            };
        }
        void remoteDescription(String kind, String sdp) {
            connection.setRemoteDescription(new RTCSessionDescription(kind.equals("offer") ? RTCSdpType.OFFER : RTCSdpType.ANSWER, sdp), new SetSessionDescriptionObserver() {
                public void onSuccess() { executor.execute(() -> {
                    if (closed.get()) return;
                    remoteSet = true; for (RTCIceCandidate candidate : candidates) connection.addIceCandidate(candidate); candidates.clear();
                    if (kind.equals("offer")) connection.createAnswer(new RTCAnswerOptions(), description("answer"));
                }); }
                public void onFailure(String error) { close(); }
            });
        }
        void candidate(RTCIceCandidate value) { if (remoteSet) connection.addIceCandidate(value); else if (candidates.size() < 128) candidates.add(value); else close(); }
        void channel(RTCDataChannel channel) {
            try { bridge = new RtcTcpBridge(channel, this::close); }
            catch (Exception error) { close(); return; }
            bridge.opened().thenRun(() -> Thread.ofVirtual().name("RandomRun-WebRTC-socket").start(() -> {
                try {
                    if (closed.get() || generation != WebRtcTransport.this.generation) return;
                    Socket socket;
                    if (incoming) {
                        socket = new Socket(); socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                        localPort = socket.getLocalPort(); expectedNames.put(localPort, name);
                        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), hostPort), 10000);
                    } else {
                        ConnectionStatus.set(ConnectionStatus.Stage.TUNNEL);
                        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); listener.setSoTimeout(30000);
                        endpoint.complete(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()));
                        socket = listener.accept(); listener.close();
                    }
                    if (closed.get()) { socket.close(); return; }
                    bridge.attach(socket);
                } catch (Exception error) { close(); }
            }));
        }
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            peers.remove(id, this); expectedNames.remove(localPort, name);
            endpoint.completeExceptionally(new java.io.IOException("WebRTC connection closed"));
            try { if (listener != null) listener.close(); } catch (Exception ignored) { }
            if (bridge != null) bridge.close();
            executor.execute(() -> { try { if (bridge != null) bridge.disposed().join(); connection.close(); } catch (Exception ignored) { } });
        }
    }
}

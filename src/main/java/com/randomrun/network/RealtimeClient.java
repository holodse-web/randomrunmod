package com.randomrun.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class RealtimeClient {
    public interface Subscription extends AutoCloseable { void close(); }
    private static final RealtimeClient INSTANCE = new RealtimeClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "RandomRun-Realtime"); t.setDaemon(true); return t; });
    private final CopyOnWriteArrayList<Consumer<JsonObject>> roomListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<JsonObject>> signalListeners = new CopyOnWriteArrayList<>();
    private volatile String presence = "{\"type\":\"presence\",\"activity\":\"menu\"}";
    private final AtomicLong generation = new AtomicLong();
    private volatile WebSocket socket;
    private volatile JsonObject rooms;
    private volatile long lastActivity, serverOffset;
    private volatile long revision = -1;
    private volatile int online;
    private int retry;
    private boolean connecting;
    private long bestRtt = Long.MAX_VALUE;
    private CompletableFuture<WebSocket> pendingSend = CompletableFuture.completedFuture(null);
    private ScheduledFuture<?> retryTask, pingTask;
    private RealtimeClient() {}
    public static RealtimeClient getInstance() { return INSTANCE; }
    public boolean isConnected() { return socket != null && rooms != null && System.currentTimeMillis() - lastActivity < 45000; }
    public JsonObject rooms() { return rooms == null ? null : rooms.deepCopy(); }
    public long serverNow() { return System.currentTimeMillis() + serverOffset; }
    private long rttAt;
    public synchronized void observeServerTime(long serverTime, long sent, long received) {
        long rtt = received - sent;
        if (rtt < 0 || serverTime <= 0) return;
        boolean stale = System.currentTimeMillis() - rttAt > 120000;
        if (rtt <= bestRtt + 50 || stale) {
            bestRtt = stale ? rtt : Math.min(bestRtt, rtt);
            rttAt = System.currentTimeMillis();
            serverOffset = serverTime - (sent + rtt / 2);
        }
    }
    private void ping() {
        JsonObject ping = new JsonObject(); ping.addProperty("type", "ping"); ping.addProperty("sentAt", System.currentTimeMillis());
        sendText(ping);
    }
    public void syncClock() {
        if (socket == null) return;
        long expected = generation.get();
        for (int i = 0; i < 4; i++) scheduler.schedule(() -> { if (generation.get() == expected && socket != null) ping(); }, i * 400L, TimeUnit.MILLISECONDS);
    }
    public int onlineCount() { return online; }
    public Subscription subscribeRooms(Consumer<JsonObject> listener) {
        roomListeners.add(listener);
        if (rooms != null) listener.accept(rooms.deepCopy());
        connect();
        return () -> roomListeners.remove(listener);
    }
    public Subscription subscribeSignals(Consumer<JsonObject> listener) { signalListeners.add(listener); connect(); return () -> signalListeners.remove(listener); }
    public synchronized void connect() {
        if (socket != null || connecting || !BackendSession.isAuthenticated()) return;
        connecting = true;
        if (retryTask != null) retryTask.cancel(false);
        long attempt = generation.incrementAndGet();
        BackendSession.Session session = BackendSession.current();
        java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + session.token())
            .buildAsync(BackendConfig.events(), new WebSocket.Listener() {
                private final StringBuilder text = new StringBuilder();
                private boolean current() { return generation.get() == attempt && BackendSession.current() == session; }
                @Override public void onOpen(WebSocket connection) {
                    synchronized (RealtimeClient.this) {
                        if (!current()) { connection.abort(); return; }
                        connecting = false; socket = connection; lastActivity = System.currentTimeMillis(); retry = 0;
                        sendText(JsonParser.parseString(presence).getAsJsonObject());
                        if (pingTask != null) pingTask.cancel(false);
                        pingTask = scheduler.scheduleAtFixedRate(() -> {
                            if (!current()) return;
                            if (System.currentTimeMillis() - lastActivity > 45000) { connection.abort(); failed(attempt); }
                            else {
                                connection.sendPing(ByteBuffer.wrap(new byte[] {1}));
                                ping();
                            }
                        }, 15, 15, TimeUnit.SECONDS);
                        syncClock();
                    }
                    connection.request(1);
                }
                @Override public CompletionStage<?> onText(WebSocket connection, CharSequence part, boolean last) {
                    if (!current()) { connection.abort(); return null; }
                    if (text.length() + part.length() > 1048576) { connection.abort(); failed(attempt); return null; }
                    text.append(part);
                    if (last) {
                        try {
                            JsonObject message = JsonParser.parseString(text.toString()).getAsJsonObject();
                            lastActivity = System.currentTimeMillis();
                            String type = message.get("type").getAsString();
                            if (type.equals("pong") && message.has("sentAt")) {
                                observeServerTime(message.get("serverTime").getAsLong(), message.get("sentAt").getAsLong(), System.currentTimeMillis());
                            } else if (bestRtt == Long.MAX_VALUE && message.has("serverTime")) serverOffset = message.get("serverTime").getAsLong() - System.currentTimeMillis();
                            if (type.equals("snapshot")) {
                                JsonObject snapshot = null;
                                synchronized (RealtimeClient.this) {
                                    if (current() && message.get("revision").getAsLong() >= revision) {
                                        revision = message.get("revision").getAsLong();
                                        rooms = message.getAsJsonObject("rooms").deepCopy();
                                        online = message.getAsJsonArray("online").size();
                                        snapshot = rooms.deepCopy();
                                    }
                                }
                                if (message.has("social") && message.get("social").isJsonObject()) SocialService.getInstance().accept(message.getAsJsonObject("social"));
                                if (snapshot != null) for (var listener : roomListeners) { try { listener.accept(snapshot.deepCopy()); } catch (Exception ignored) {} }
                            } else if (type.equals("signal")) {
                                for (var listener : signalListeners) { try { listener.accept(message.deepCopy()); } catch (Exception ignored) {} }
                            }
                        } catch (Exception ignored) { connection.abort(); failed(attempt); }
                        text.setLength(0);
                    }
                    connection.request(1); return null;
                }
                @Override public CompletionStage<?> onPong(WebSocket connection, ByteBuffer message) { if (current()) lastActivity = System.currentTimeMillis(); connection.request(1); return null; }
                @Override public CompletionStage<?> onPing(WebSocket connection, ByteBuffer message) { connection.request(1); return connection.sendPong(message); }
                @Override public CompletionStage<?> onClose(WebSocket connection, int code, String reason) {
                    if (current()) { if ((code == 4000 || code == 4001) && !BackendSession.isChangingPassword(session)) BackendSession.invalidate(session); else failed(attempt); }
                    return null;
                }
                @Override public void onError(WebSocket connection, Throwable error) { if (current()) failed(attempt); }
            }).whenComplete((connection, error) -> { if (error != null && generation.get() == attempt) failed(attempt); });
    }
    private synchronized void failed(long attempt) {
        if (generation.get() != attempt) return;
        generation.incrementAndGet();
        if (socket != null) socket.abort();
        connecting = false; socket = null; rooms = null; pendingSend = CompletableFuture.completedFuture(null);
        if (pingTask != null) pingTask.cancel(false);
        if (BackendSession.isAuthenticated()) retryTask = scheduler.schedule(this::connect, Math.min(30, 1 << Math.min(retry++, 5)), TimeUnit.SECONDS);
    }
    public void setPresence(String activity, String target) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "presence");
        message.addProperty("activity", activity);
        if (target != null) message.addProperty("target", target);
        String text = message.toString();
        if (text.equals(presence)) return;
        presence = text;
        if (socket != null) sendText(message);
    }
    public CompletableFuture<WebSocket> sendSignal(String peer, String connection, String kind, JsonObject data) {
        WebSocket current = socket;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("Сервис комнат недоступен"));
        JsonObject message = new JsonObject();
        message.addProperty("type", "signal"); message.addProperty("peer", peer); message.addProperty("connection", connection); message.addProperty("kind", kind); message.add("data", data);
        return sendText(message);
    }
    private synchronized CompletableFuture<WebSocket> sendText(JsonObject message) {
        WebSocket expected = socket;
        if (expected == null) return CompletableFuture.failedFuture(new IllegalStateException("WebSocket disconnected"));
        pendingSend = pendingSend.handle((value, error) -> (WebSocket) null).thenCompose(ignored -> {
            if (socket != expected) return CompletableFuture.failedFuture(new CancellationException("WebSocket replaced"));
            return expected.sendText(message.toString(), true);
        });
        return pendingSend;
    }
    public synchronized void acceptRoom(JsonObject room) {
        if (!room.has("roomCode") || !room.has("revision") || room.get("revision").getAsLong() < revision) return;
        revision = room.get("revision").getAsLong();
        JsonObject updated = rooms == null ? new JsonObject() : rooms.deepCopy();
        updated.add(room.get("roomCode").getAsString(), room.deepCopy());
        rooms = updated;
    }
    public synchronized void disconnect() {
        generation.incrementAndGet();
        if (retryTask != null) retryTask.cancel(false);
        if (pingTask != null) pingTask.cancel(false);
        if (socket != null) socket.abort();
        connecting = false; socket = null; rooms = null; pendingSend = CompletableFuture.completedFuture(null); online = 0; revision = -1; bestRtt = Long.MAX_VALUE; serverOffset = 0;
    }
}

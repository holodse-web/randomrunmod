package com.randomrun.network;

import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoomSerializer;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

public final class HttpClient {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer()).create();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8, r -> { Thread thread = new Thread(r, "RandomRun-API"); thread.setDaemon(true); return thread; });
    private static final HttpClient INSTANCE = new HttpClient();
    private final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).executor(EXECUTOR).build();
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(JsonElement value, long expires, BackendSession.Session session) {}
    public static final class ApiException extends RuntimeException {
        private final int status;
        public ApiException(int status, String code) { super(code); this.status = status; }
        public int status() { return status; }
    }
    private HttpClient() {}
    public static HttpClient getInstance() { return INSTANCE; }
    public static ExecutorService getNetworkExecutor() { return EXECUTOR; }
    public CompletableFuture<JsonElement> request(String method, String path, Object data, boolean authenticated) {
        // Offline (or not asked yet on first launch): nothing leaves the computer.
        com.randomrun.infrastructure.RandomRunMod mod = com.randomrun.infrastructure.RandomRunMod.getInstance();
        if (mod == null || mod.getConfig() == null || !mod.getConfig().isOnlineMode())
            return CompletableFuture.failedFuture(new ApiException(0, "OFFLINE"));
        BackendSession.Session session = BackendSession.current();
        if (authenticated && !BackendSession.isAuthenticated()) return CompletableFuture.failedFuture(new ApiException(401, "AUTH_REQUIRED"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(BackendConfig.endpoint(path)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
        if (authenticated) builder.header("Authorization", "Bearer " + session.token());
        builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(GSON.toJson(data)));
        if (data != null) builder.header("Content-Type", "application/json");
        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (authenticated && BackendSession.current() != session) throw new CancellationException("Session changed");
            JsonElement value;
            try { value = JsonParser.parseString(response.body()); } catch (Exception error) { throw new ApiException(response.statusCode(), "INVALID_SERVER_RESPONSE"); }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (authenticated && response.statusCode() == 401 && value.isJsonObject() && value.getAsJsonObject().has("error")
                        && java.util.Set.of("SESSION_EXPIRED", "AUTH_REQUIRED").contains(value.getAsJsonObject().get("error").getAsString())) BackendSession.invalidate(session);
                if (authenticated && response.statusCode() == 403 && value.isJsonObject() && value.getAsJsonObject().has("error")
                        && "ACCOUNT_BANNED".equals(value.getAsJsonObject().get("error").getAsString())) BackendSession.markBanned(session);
                throw new ApiException(response.statusCode(), value.isJsonObject() && value.getAsJsonObject().has("error") ? value.getAsJsonObject().get("error").getAsString() : "REQUEST_FAILED");
            }
            if (authenticated && !method.equals("GET") && value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                if (object.has("roomCode")) RealtimeClient.getInstance().acceptRoom(object);
                else if (object.has("room") && object.get("room").isJsonObject()) RealtimeClient.getInstance().acceptRoom(object.getAsJsonObject("room"));
            }
            return value;
        });
    }
    public CompletableFuture<JsonElement> get(String path) { return getCached(path, 10000); }
    public CompletableFuture<JsonElement> getNoCache(String path) { return getCached(path, 0); }
    public CompletableFuture<JsonElement> getCached(String path, long ttl) {
        if (ttl > 0 && RealtimeClient.getInstance().isConnected()) {
            JsonObject snapshot = RealtimeClient.getInstance().rooms();
            if (snapshot != null && path.equals("/rooms")) return CompletableFuture.completedFuture(snapshot);
            if (snapshot != null && path.matches("/rooms/[0-9]{5}") && snapshot.has(path.substring(7))) return CompletableFuture.completedFuture(snapshot.get(path.substring(7)));
        }
        BackendSession.Session session = BackendSession.current();
        Cached hit = cache.get(path);
        if (ttl > 0 && hit != null && hit.session == session && hit.expires > System.currentTimeMillis()) return CompletableFuture.completedFuture(hit.value.deepCopy());
        return request("GET", path, null, !path.equals("/version") && !path.equals("/health")).thenApply(value -> {
            if (ttl > 0 && BackendSession.current() == session) cache.put(path, new Cached(value.deepCopy(), System.currentTimeMillis() + ttl, session));
            return value;
        });
    }
    public CompletableFuture<Boolean> put(String path, Object data) { return mutate("PUT", path, data); }
    public CompletableFuture<Boolean> patch(String path, Object data) { return mutate("PATCH", path, data); }
    public CompletableFuture<JsonElement> post(String path, Object data) { return request("POST", path, data, true); }
    public CompletableFuture<Boolean> delete(String path) { return mutate("DELETE", path, null); }
    private CompletableFuture<Boolean> mutate(String method, String path, Object data) { return request(method, path, data, true).thenApply(value -> { clearCache(); return true; }); }
    public void invalidateCache(String path) { clearCache(); }
    public void clearCache() { cache.clear(); }

    public static final class BackendConfig {
        private static final URI BASE = load();
        private BackendConfig() {}
        private static URI load() {
            String address = System.getProperty("randomrun.api", "http://127.0.0.1:8787");
            try (var input = BackendConfig.class.getResourceAsStream("/randomrun-online.json")) {
                if (System.getProperty("randomrun.api") == null && input != null) address = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().get("apiUrl").getAsString();
            } catch (Exception error) { throw new IllegalStateException("Invalid RandomRun server configuration", error); }
            URI value = URI.create(address);
            boolean local = "127.0.0.1".equals(value.getHost()) || "localhost".equals(value.getHost()) || "[::1]".equals(value.getHost());
            if (!("https".equals(value.getScheme()) || local && "http".equals(value.getScheme())) || value.getHost() == null || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null || !(value.getPath().isEmpty() || "/".equals(value.getPath()))) throw new IllegalArgumentException("RandomRun requires an HTTPS API address");
            return URI.create(address.replaceAll("/+$", "") + "/v1/");
        }
        public static URI endpoint(String path) {
            if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains(":") || path.contains("..") || path.contains("?") || path.contains("#") || path.contains("%") || path.contains("\\")) throw new IllegalArgumentException("Invalid API path");
            return BASE.resolve(path.substring(1));
        }
        public static URI events() { return URI.create(endpoint("/events").toString().replaceFirst("^http", "ws")); }
    }
}

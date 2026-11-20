package com.randomrun.network;

import com.randomrun.network.RunVerifier.ModIntegrity;
import com.randomrun.network.SocialService.PendingInvites;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackendSession {
    public record Session(String token, String id, String name, long expires, long generation) {
        @Override public String toString() { return "Session[" + name + "]"; }
    }
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final Path REMEMBER_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("randomrun").resolve("session.dat");
    private static volatile Session current;
    private static volatile Session passwordChange;
    private static volatile String bannedName;
    private static volatile Session extendedSession;
    private static volatile long extendedUntil;
    private static volatile CompletableFuture<Boolean> restoring;
    public static synchronized void markBanned(Session session) {
        if (session == null || session != current) return;
        bannedName = session.name();
        forget();
        clear();
    }

    public static synchronized String takeBanned() {
        String value = bannedName;
        bannedName = null;
        return value;
    }
    private BackendSession() {}
    public static String playerName() {
        String minecraftName = net.minecraft.client.Minecraft.getInstance().getUser().getName();
        Session value = current;
        return value != null && value.name().equalsIgnoreCase(minecraftName) ? value.name() : minecraftName;
    }
    public static Session current() { return current; }
    public static long generation() { return GENERATION.get(); }
    public static long expiresOf(Session value) {
        if (value == null) return 0;
        return value == extendedSession ? Math.max(value.expires(), extendedUntil) : value.expires();
    }
    public static void extend(Session value, long expires) {
        if (value == null || value != current || expires <= expiresOf(value)) return;
        extendedSession = value;
        extendedUntil = expires;
    }
    public static boolean isAuthenticated() { Session value = current; return value != null && expiresOf(value) > System.currentTimeMillis(); }
    public static boolean owns(String name) { Session value = current; return value != null && expiresOf(value) > System.currentTimeMillis() && value.name().equalsIgnoreCase(name); }
    public static boolean isChangingPassword(Session value) { return passwordChange == value; }
    public static CompletableFuture<JsonObject> authenticate(String name, String password, boolean register) {
        return authenticate(name, password, register, false);
    }
    public static CompletableFuture<JsonObject> authenticate(String name, String password, boolean register, boolean remember) {
        long attempt;
        synchronized (BackendSession.class) { clear(); attempt = GENERATION.get(); }
        return ModIntegrity.fingerprint().thenCombine(HttpClient.getInstance().request("GET", "/auth/challenge", null, false), (fingerprint, challenge) -> {
            String nonce = challenge.getAsJsonObject().get("nonce").getAsString();
            return Map.<String, Object>of("username", name, "password", password, "nonce", nonce, "proof", ModIntegrity.proof(fingerprint, nonce, name), "remember", remember);
        }).thenCompose(payload -> HttpClient.getInstance().request("POST", register ? "/auth/register" : "/auth/login", payload, false)).thenApply(result -> {
            JsonObject value = result.getAsJsonObject();
            synchronized (BackendSession.class) {
                if (GENERATION.get() != attempt) throw new java.util.concurrent.CancellationException("Authentication cancelled");
                install(value, attempt);
            }
            if (remember) remember(value); else forget();
            return value.getAsJsonObject("profile");
        });
    }
    public static synchronized CompletableFuture<Boolean> restore(String name) {
        if (owns(name)) return CompletableFuture.completedFuture(true);
        if (restoring != null && !restoring.isDone()) return restoring;
        String[] saved = recall();
        if (saved == null || !saved[0].equalsIgnoreCase(name)) return CompletableFuture.completedFuture(false);
        long attempt;
        clear();
        attempt = GENERATION.get();
        CompletableFuture<Boolean> result = ModIntegrity.fingerprint().thenCombine(HttpClient.getInstance().request("GET", "/auth/challenge", null, false), (fingerprint, challenge) -> {
            String nonce = challenge.getAsJsonObject().get("nonce").getAsString();
            return Map.<String, Object>of("refresh", saved[1], "nonce", nonce, "proof", ModIntegrity.proof(fingerprint, nonce, saved[0]));
        }).thenCompose(payload -> HttpClient.getInstance().request("POST", "/auth/refresh", payload, false)).thenApply(response -> {
            JsonObject value = response.getAsJsonObject();
            synchronized (BackendSession.class) {
                if (GENERATION.get() != attempt) return false;
                install(value, attempt);
            }
            remember(value);
            return true;
        }).exceptionally(error -> {
            Throwable cause = error instanceof java.util.concurrent.CompletionException ? error.getCause() : error;
            if (cause instanceof HttpClient.ApiException api && api.status() >= 400 && api.status() < 500 && api.status() != 429) forget();
            com.randomrun.infrastructure.util.CompactLogger.warn("[Auth] Saved login not restored: {}", cause == null ? "?" : cause.getMessage());
            return false;
        });
        restoring = result;
        return result;
    }
    public static boolean hasRemembered(String name) {
        String[] saved = recall();
        return saved != null && saved[0].equalsIgnoreCase(name);
    }
    public static CompletableFuture<Boolean> changePassword(String oldPassword, String newPassword) {
        Session expected;
        synchronized (BackendSession.class) {
            expected = current;
            if (expected == null || passwordChange != null) return CompletableFuture.failedFuture(new IllegalStateException("Password change unavailable"));
            passwordChange = expected;
        }
        boolean remember = recall() != null;
        return HttpClient.getInstance().request("POST", "/auth/password", Map.of("currentPassword", oldPassword, "newPassword", newPassword, "remember", remember), true).thenApply(result -> {
            synchronized (BackendSession.class) {
                if (current != expected) throw new java.util.concurrent.CancellationException("Session changed");
                install(result.getAsJsonObject(), GENERATION.incrementAndGet());
            }
            if (remember) remember(result.getAsJsonObject());
            return true;
        }).whenComplete((value, error) -> {
            synchronized (BackendSession.class) {
                if (passwordChange == expected) passwordChange = null;
                if (current == expected) RealtimeClient.getInstance().connect();
            }
        });
    }
    private static void install(JsonObject value, long generation) {
        RealtimeClient.getInstance().disconnect();
        JsonObject user = value.getAsJsonObject("user");
        current = new Session(value.get("token").getAsString(), user.get("id").getAsString(), user.get("name").getAsString(), value.get("expires").getAsLong(), generation);
        HttpClient.getInstance().clearCache();
        com.randomrun.core.data.DataSyncService.resetCache();
        RealtimeClient.getInstance().connect();
        com.randomrun.core.data.CloudStorage.INSTANCE.flushPending();
    }
    public static synchronized void invalidate(Session expected) { if (current == expected) clear(); }
    public static synchronized void clear() {
        Session previous = current;
        current = null; passwordChange = null; extendedSession = null; extendedUntil = 0; GENERATION.incrementAndGet();
        RealtimeClient.getInstance().disconnect();
        SocialService.getInstance().clear();
        PendingInvites.clear();
        WebRtcTransport.getInstance().reset();
        HttpClient.getInstance().clearCache();
        com.randomrun.core.data.DataSyncService.CoreInitializer.reset();
        com.randomrun.core.data.PlayerProfile.clear();
        if (previous != null) {
            var manager = com.randomrun.features.battle.manager.BattleManager.getInstance();
            String roomId = manager.getCurrentRoomId();
            long roomGeneration = manager.roomGeneration();
            if (roomId != null) net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (!manager.isCurrentRoom(roomId, roomGeneration)) return;
                manager.stopBattle();
                var client = net.minecraft.client.Minecraft.getInstance();
                if (client.level != null) client.disconnectFromWorld(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.session.ended"));
            });
        }
        com.randomrun.core.data.DataSyncService.resetCache();
    }
    public static CompletableFuture<Void> logout() {
        String[] saved = recall();
        Map<String, Object> payload = saved != null ? Map.of("refresh", saved[1]) : Map.of();
        forget();
        var result = isAuthenticated() ? HttpClient.getInstance().request("POST", "/auth/logout", payload, true).handle((value, error) -> (Void) null) : CompletableFuture.<Void>completedFuture(null);
        clear(); return result;
    }

    private static void remember(JsonObject value) {
        if (!value.has("refresh") || !value.has("user")) return;
        try {
            String plain = value.getAsJsonObject("user").get("name").getAsString() + "\n" + value.get("refresh").getAsString();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deviceKey(), new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] data = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, data, 0, iv.length);
            System.arraycopy(sealed, 0, data, iv.length, sealed.length);
            Files.createDirectories(REMEMBER_FILE.getParent());
            Files.writeString(REMEMBER_FILE, Base64.getEncoder().encodeToString(data));
        } catch (Exception error) {
            com.randomrun.infrastructure.util.CompactLogger.warn("[Auth] Login not saved: {}", error.getClass().getSimpleName());
        }
    }

    private static String[] recall() {
        try {
            if (!Files.exists(REMEMBER_FILE)) return null;
            byte[] data = Base64.getDecoder().decode(Files.readString(REMEMBER_FILE).trim());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deviceKey(), new GCMParameterSpec(128, data, 0, 12));
            String[] parts = new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8).split("\n", 2);
            return parts.length == 2 && parts[1].matches("[0-9a-f]{64}") ? parts : null;
        } catch (Exception error) {
            forget();
            return null;
        }
    }

    public static void forget() {
        try {
            Files.deleteIfExists(REMEMBER_FILE);
        } catch (Exception ignored) {
        }
    }

    private static SecretKeySpec deviceKey() throws Exception {
        String seed = System.getProperty("user.name", "") + "|" + System.getProperty("user.home", "") + "|" + System.getProperty("os.name", "") + "|"
            + System.getenv().getOrDefault("COMPUTERNAME", System.getenv().getOrDefault("HOSTNAME", "")) + "|randomrun-session";
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}

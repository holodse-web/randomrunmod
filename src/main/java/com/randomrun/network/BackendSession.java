package com.randomrun.network;

import com.randomrun.network.RunVerifier.ModIntegrity;
import com.randomrun.network.SocialService.PendingInvites;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class BackendSession {
    public record Session(String token, String id, String name, long expires, long generation) {
        @Override public String toString() { return "Session[" + name + "]"; }
    }
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile Session current;
    private static volatile Session passwordChange;
    private static volatile String bannedName;
    public static void markBanned(Session session) { if (session != null) bannedName = session.name(); invalidate(session); }
    public static String takeBanned() { String value = bannedName; bannedName = null; return value; }
    private BackendSession() {}
    public static String playerName() {
        String minecraftName = net.minecraft.client.Minecraft.getInstance().getUser().getName();
        Session value = current;
        return value != null && value.name().equalsIgnoreCase(minecraftName) ? value.name() : minecraftName;
    }
    public static Session current() { return current; }
    public static long generation() { return GENERATION.get(); }
    public static boolean isAuthenticated() { Session value = current; return value != null && value.expires() > System.currentTimeMillis(); }
    public static boolean owns(String name) { Session value = current; return value != null && value.expires() > System.currentTimeMillis() && value.name().equalsIgnoreCase(name); }
    public static boolean isChangingPassword(Session value) { return passwordChange == value; }
    public static CompletableFuture<JsonObject> authenticate(String name, String password, boolean register) {
        long attempt;
        synchronized (BackendSession.class) { clear(); attempt = GENERATION.get(); }
        return ModIntegrity.fingerprint().thenCombine(HttpClient.getInstance().request("GET", "/auth/challenge", null, false), (fingerprint, challenge) -> {
            String nonce = challenge.getAsJsonObject().get("nonce").getAsString();
            return Map.<String, Object>of("username", name, "password", password, "nonce", nonce, "proof", ModIntegrity.proof(fingerprint, nonce, name));
        }).thenCompose(payload -> HttpClient.getInstance().request("POST", register ? "/auth/register" : "/auth/login", payload, false)).thenApply(result -> {
            JsonObject value = result.getAsJsonObject();
            synchronized (BackendSession.class) {
                if (GENERATION.get() != attempt) throw new java.util.concurrent.CancellationException("Authentication cancelled");
                install(value, attempt);
            }
            return value.getAsJsonObject("profile");
        });
    }
    public static CompletableFuture<Boolean> changePassword(String oldPassword, String newPassword) {
        Session expected;
        synchronized (BackendSession.class) {
            expected = current;
            if (expected == null || passwordChange != null) return CompletableFuture.failedFuture(new IllegalStateException("Password change unavailable"));
            passwordChange = expected;
        }
        return HttpClient.getInstance().request("POST", "/auth/password", Map.of("currentPassword", oldPassword, "newPassword", newPassword), true).thenApply(result -> {
            synchronized (BackendSession.class) {
                if (current != expected) throw new java.util.concurrent.CancellationException("Session changed");
                install(result.getAsJsonObject(), GENERATION.incrementAndGet());
            }
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
    }
    public static synchronized void invalidate(Session expected) { if (current == expected) clear(); }
    public static synchronized void clear() {
        Session previous = current;
        current = null; passwordChange = null; GENERATION.incrementAndGet();
        RealtimeClient.getInstance().disconnect();
        SocialService.getInstance().clear();
        PendingInvites.clear();
        WebRtcTransport.getInstance().reset();
        HttpClient.getInstance().clearCache();
        com.randomrun.core.data.DataSyncService.CoreInitializer.reset();
        com.randomrun.core.data.PlayerProfile.clear();
        if (previous != null) {
            var manager = com.randomrun.features.battle.manager.BattleManager.getInstance();
            var oldRoom = manager.getCurrentRoom();
            if (oldRoom != null) net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (manager.getCurrentRoom() != oldRoom) return;
                manager.stopBattle();
                var client = net.minecraft.client.Minecraft.getInstance();
                if (client.level != null) client.disconnectFromWorld(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.session.ended"));
            });
        }
        com.randomrun.core.data.DataSyncService.resetCache();
    }
    public static CompletableFuture<Void> logout() {
        var result = isAuthenticated() ? HttpClient.getInstance().request("POST", "/auth/logout", Map.of(), true).handle((value, error) -> (Void) null) : CompletableFuture.<Void>completedFuture(null);
        clear(); return result;
    }
}

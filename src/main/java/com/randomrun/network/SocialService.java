package com.randomrun.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SocialService {
    public enum Activity { OFFLINE, MENU, LOBBY, SOLO, ROOM, BATTLE }

    public record Friend(String name, boolean online, Activity activity, String target, String room, int roomPlayers, int roomMax, boolean roomPrivate) {}
    public record Invite(String from, String code, long expires) {}
    public record Snapshot(List<Friend> friends, List<String> incoming, List<String> outgoing, List<Invite> invites) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of(), List.of());

        public int onlineCount() {
            int count = 0;
            for (Friend friend : friends) if (friend.online()) count++;
            return count;
        }
    }

    private static final SocialService INSTANCE = new SocialService();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> sentInvites = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile long lastFetch;

    private SocialService() {}

    public static SocialService getInstance() {
        return INSTANCE;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void addListener(Runnable listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public void refresh() {
        if (!BackendSession.isAuthenticated()) return;
        long now = System.currentTimeMillis();
        if (RealtimeClient.getInstance().isConnected() && snapshot != Snapshot.EMPTY && now - lastFetch < 30000) return;
        if (now - lastFetch < 3000) return;
        lastFetch = now;
        HttpClient.getInstance().getNoCache("/friends").thenAccept(value -> {
            if (value.isJsonObject()) accept(value.getAsJsonObject());
        }).exceptionally(error -> null);
    }

    public void clear() {
        snapshot = Snapshot.EMPTY;
        sentInvites.clear();
        notifyListeners();
    }

    void accept(JsonObject value) {
        List<Friend> friends = new ArrayList<>();
        for (JsonElement element : array(value, "friends")) {
            if (!element.isJsonObject()) continue;
            JsonObject friend = element.getAsJsonObject();
            friends.add(new Friend(string(friend, "name"), friend.has("online") && friend.get("online").getAsBoolean(),
                activity(string(friend, "activity")), string(friend, "target"), string(friend, "room"),
                integer(friend, "roomPlayers"), integer(friend, "roomMax"), friend.has("roomPrivate") && friend.get("roomPrivate").getAsBoolean()));
        }
        List<String> incoming = names(array(value, "incoming"));
        List<String> outgoing = names(array(value, "outgoing"));
        List<Invite> invites = new ArrayList<>();
        for (JsonElement element : array(value, "invites")) {
            if (!element.isJsonObject()) continue;
            JsonObject invite = element.getAsJsonObject();
            invites.add(new Invite(string(invite, "from"), string(invite, "code"), invite.has("expires") ? invite.get("expires").getAsLong() : 0L));
        }
        Snapshot previous = snapshot;
        snapshot = new Snapshot(List.copyOf(friends), List.copyOf(incoming), List.copyOf(outgoing), List.copyOf(invites));
        if (previous != Snapshot.EMPTY) announce(previous, snapshot);
        lastFetch = System.currentTimeMillis();
        notifyListeners();
    }

    private static void announce(Snapshot before, Snapshot after) {
        for (String name : after.incoming()) {
            if (!before.incoming().contains(name)) notify(com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.friends.notify.request", name));
        }
        for (Invite invite : after.invites()) {
            boolean known = before.invites().stream().anyMatch(old -> old.from().equals(invite.from()) && old.code().equals(invite.code()));
            if (!known) notify(com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.friends.notify.invite", invite.from()));
        }
        for (String name : before.outgoing()) {
            if (!after.outgoing().contains(name) && after.friends().stream().anyMatch(friend -> friend.name().equalsIgnoreCase(name))) {
                notify(com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.friends.notify.accepted", name));
            }
        }
    }

    private static void notify(String text) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            com.randomrun.ui.widget.overlay.CustomNotification.show(text);
            com.randomrun.ui.util.Ui.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f, 0.5f);
        });
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try { listener.run(); } catch (RuntimeException ignored) {}
        }
    }

    public CompletableFuture<Void> request(String name) { return action("request", name); }
    public CompletableFuture<Void> accept(String name) { return action("accept", name); }
    public CompletableFuture<Void> decline(String name) { return action("decline", name); }
    public CompletableFuture<Void> remove(String name) { return action("remove", name); }

    private CompletableFuture<Void> action(String action, String name) {
        return HttpClient.getInstance().post("/friends/" + action, Map.of("name", name)).thenAccept(value -> {
            if (value.isJsonObject()) accept(value.getAsJsonObject());
        });
    }

    public CompletableFuture<Void> invite(String name) {
        return HttpClient.getInstance().post("/invites", Map.of("name", name)).thenAccept(value -> sentInvites.put(name.toLowerCase(), System.currentTimeMillis()));
    }

    public boolean recentlyInvited(String name) {
        Long time = sentInvites.get(name.toLowerCase());
        return time != null && System.currentTimeMillis() - time < 300000;
    }

    public CompletableFuture<Void> declineInvite(String from) {
        return HttpClient.getInstance().post("/invites/decline", Map.of("name", from)).thenAccept(value -> {});
    }

    public static String errorKey(Throwable error) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof HttpClient.ApiException) && cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String code = cause instanceof HttpClient.ApiException api ? api.getMessage() : "REQUEST_FAILED";
        return switch (code) {
            case "PLAYER_NOT_FOUND", "INVALID_USERNAME" -> "randomrun.friends.error.not_found";
            case "CANNOT_FRIEND_SELF" -> "randomrun.friends.error.self";
            case "ALREADY_FRIENDS" -> "randomrun.friends.error.already";
            case "FRIEND_LIMIT" -> "randomrun.friends.error.limit";
            case "RATE_LIMITED" -> "randomrun.friends.error.rate";
            case "NOT_IN_ROOM" -> "randomrun.friends.error.no_room";
            case "ROOM_FULL" -> "randomrun.friends.error.room_full";
            case "GAME_ALREADY_STARTED" -> "randomrun.friends.error.started";
            case "ALREADY_IN_ROOM" -> "randomrun.friends.error.in_room";
            default -> "randomrun.friends.error.generic";
        };
    }

    private static Activity activity(String value) {
        if (value == null) return Activity.OFFLINE;
        return switch (value) {
            case "menu" -> Activity.MENU;
            case "lobby" -> Activity.LOBBY;
            case "solo" -> Activity.SOLO;
            case "room" -> Activity.ROOM;
            case "battle" -> Activity.BATTLE;
            default -> Activity.OFFLINE;
        };
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static List<String> names(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonPrimitive()) result.add(element.getAsString());
        return result;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static int integer(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : 0;
    }
}

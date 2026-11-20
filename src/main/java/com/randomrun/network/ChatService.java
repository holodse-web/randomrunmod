package com.randomrun.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChatService {
    private static final ChatService INSTANCE = new ChatService();
    private static final int KEEP = 100;
    public static final int MAX_LENGTH = 140;

    public record Message(long id, String name, String text, long at, long receivedAt) {}

    private final List<Message> messages = new ArrayList<>();
    private long loadedConnection = -1, lastSeenId, lastSentAt;
    private int version;
    private String error;
    private long errorAt;
    private long viewedAt;

    private ChatService() {}

    public static ChatService getInstance() { return INSTANCE; }

    public synchronized List<Message> messages() { return List.copyOf(messages); }
    public synchronized int version() { return version; }
    public synchronized boolean loaded() { return loadedConnection >= 0; }

    public synchronized int unread() {
        int count = 0;
        String me = BackendSession.playerName();
        for (int i = messages.size() - 1; i >= 0 && messages.get(i).id() > lastSeenId; i--) {
            if (!messages.get(i).name().equalsIgnoreCase(me)) count++;
        }
        return count;
    }

    public synchronized void markSeen() {
        viewedAt = System.currentTimeMillis();
        if (!messages.isEmpty()) lastSeenId = Math.max(lastSeenId, messages.get(messages.size() - 1).id());
    }

    public synchronized String error() {
        if (error != null && System.currentTimeMillis() - errorAt > 3000) error = null;
        return error;
    }

    public void ensureHistory() {
        RealtimeClient client = RealtimeClient.getInstance();
        client.connect();
        long connection = client.connectionId();
        if (connection < 0) return;
        synchronized (this) {
            if (loadedConnection == connection) return;
            loadedConnection = connection;
        }
        JsonObject request = new JsonObject();
        request.addProperty("type", "chat_history");
        client.send(request).exceptionally(failure -> { synchronized (this) { loadedConnection = -1; } return null; });
    }

    public boolean send(String raw) {
        String text = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return false;
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastSentAt < 1200) { fail("RATE_LIMITED"); return false; }
            lastSentAt = now;
        }
        JsonObject message = new JsonObject();
        message.addProperty("type", "chat");
        message.addProperty("text", text);
        RealtimeClient.getInstance().send(message).exceptionally(failure -> { fail("OFFLINE"); return null; });
        return true;
    }

    synchronized void accept(JsonObject message) {
        String type = message.get("type").getAsString();
        if (type.equals("chat_error")) { fail(message.has("error") ? message.get("error").getAsString() : "INVALID_MESSAGE"); return; }
        if (type.equals("chat") && message.has("m")) add(message.getAsJsonObject("m"));
        else if (type.equals("chat_history") && message.has("m")) {
            JsonArray list = message.getAsJsonArray("m");
            for (JsonElement element : list) if (element.isJsonObject()) add(element.getAsJsonObject());
            if (lastSeenId == 0 && !messages.isEmpty()) lastSeenId = messages.get(messages.size() - 1).id();
        }
        messages.sort((a, b) -> Long.compare(a.id(), b.id()));
        while (messages.size() > KEEP) messages.remove(0);
        if (System.currentTimeMillis() - viewedAt < 500 && !messages.isEmpty()) lastSeenId = messages.get(messages.size() - 1).id();
        version++;
    }

    private void add(JsonObject value) {
        long id = value.get("i").getAsLong();
        for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).id() == id) return;
        messages.add(new Message(id, value.get("n").getAsString(), value.get("t").getAsString(), value.get("a").getAsLong(), System.currentTimeMillis()));
    }

    private void fail(String code) {
        error = switch (code) {
            case "RATE_LIMITED" -> "randomrun.chat.error.rate";
            case "ACCOUNT_BANNED" -> "randomrun.chat.error.banned";
            case "OFFLINE" -> "randomrun.chat.error.offline";
            default -> "randomrun.chat.error.generic";
        };
        errorAt = System.currentTimeMillis();
    }

    public synchronized void reset() {
        messages.clear();
        loadedConnection = -1;
        lastSeenId = 0;
        version++;
    }

    public static boolean mentions(String text, String name) {
        return name != null && !name.isEmpty() && text.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
    }
}

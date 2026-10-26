package com.randomrun.features.battle.screen.lobby.util.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class LobbyRoomState {
    private LobbyRoomState() {}

    public static JsonObject apply(JsonObject state, String event, String path, JsonElement data) {
        if (!"put".equals(event) && !"patch".equals(event)) throw new IllegalArgumentException("Invalid room event");
        if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("Invalid room path");
        JsonObject result = state == null ? new JsonObject() : state.deepCopy();
        if ("patch".equals(event) && data != null && data.isJsonObject()) {
            for (var entry : data.getAsJsonObject().entrySet()) {
                String childPath = (path.endsWith("/") ? path : path + "/") + entry.getKey();
                result = put(result, childPath, entry.getValue());
            }
            return result;
        }
        return put(result, path, data);
    }

    private static JsonObject put(JsonObject root, String path, JsonElement value) {
        String clean = path.substring(1);
        if (clean.isEmpty()) {
            if (value == null || value.isJsonNull()) return new JsonObject();
            if (!value.isJsonObject()) throw new IllegalArgumentException("Room root must be an object");
            return value.getAsJsonObject().deepCopy();
        }
        String[] parts = clean.split("/", -1);
        for (String part : parts) if (part.isEmpty()) throw new IllegalArgumentException("Empty room path segment");
        JsonObject cursor = root;
        for (int index = 0; index < parts.length - 1; index++) {
            JsonElement child = cursor.get(parts[index]);
            if (child == null || !child.isJsonObject()) {
                if (value == null || value.isJsonNull()) return root;
                JsonObject created = new JsonObject();
                cursor.add(parts[index], created);
                cursor = created;
            } else {
                cursor = child.getAsJsonObject();
            }
        }
        if (value == null || value.isJsonNull()) cursor.remove(parts[parts.length - 1]);
        else cursor.add(parts[parts.length - 1], value.deepCopy());
        return root;
    }
}

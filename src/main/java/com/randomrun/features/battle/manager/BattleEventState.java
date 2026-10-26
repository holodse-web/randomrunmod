package com.randomrun.features.battle.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.features.battle.screen.lobby.util.helper.LobbyRoomState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BattleEventState {
    private JsonObject state = new JsonObject();
    private final Set<String> delivered = new HashSet<>();

    public List<JsonObject> apply(String event, String path, JsonElement data) {
        JsonObject updated = LobbyRoomState.apply(state, event, path, data);
        List<JsonObject> events = new ArrayList<>();
        for (var entry : updated.entrySet()) {
            if (!entry.getValue().isJsonObject() || delivered.contains(entry.getKey())) continue;
            JsonObject value = entry.getValue().getAsJsonObject();
            if (!value.has("type") || !value.get("type").isJsonPrimitive()
                    || !value.has("player") || !value.get("player").isJsonPrimitive()) continue;
            if ("ACHIEVEMENT".equals(value.get("type").getAsString()) && !value.has("aid")) continue;
            delivered.add(entry.getKey());
            events.add(value.deepCopy());
        }
        state = updated;
        return List.copyOf(events);
    }
}

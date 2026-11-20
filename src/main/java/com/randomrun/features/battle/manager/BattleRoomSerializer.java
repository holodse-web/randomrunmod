package com.randomrun.features.battle.manager;

import com.google.gson.*;
import java.lang.reflect.Type;

public class BattleRoomSerializer implements JsonSerializer<BattleRoom> {
    private final Gson defaultGson = new Gson();
    
    @Override
    public JsonElement serialize(BattleRoom room, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = (JsonObject) defaultGson.toJsonTree(room);
        
        removeIfFalse(json, "pr");
        removeIfFalse(json, "ug");
        
        removeFalseFlags(json, "dd");
        removeFalseFlags(json, "ep");
        
        if (json.has("st") && "WAITING".equals(json.get("st").getAsString())) {
            json.remove("w");
            json.remove("dr");
            json.remove("srv_start");
            json.remove("swt");
        }
        
        removeIfNullOrEmpty(json, "w");
        removeIfNullOrEmpty(json, "dr");
        removeIfNullOrEmpty(json, "pwd");
        removeIfNullOrEmpty(json, "ip");
        removeIfZero(json, "srv_start");
        removeIfZero(json, "swt");
        
        return json;
    }
    
    private static void removeFalseFlags(JsonObject json, String key) {
        if (json.get(key) instanceof JsonObject flags) {
            flags.entrySet().removeIf(entry -> entry.getValue().isJsonPrimitive() && !entry.getValue().getAsBoolean());
            if (flags.size() == 0) json.remove(key);
        }
    }

    private void removeIfFalse(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            if (!json.get(key).getAsBoolean()) {
                json.remove(key);
            }
        }
    }
    
    private void removeIfNullOrEmpty(JsonObject json, String key) {
        if (json.has(key)) {
            JsonElement element = json.get(key);
            if (element.isJsonNull() || 
                (element.isJsonPrimitive() && element.getAsString().isEmpty())) {
                json.remove(key);
            }
        }
    }
    
    private void removeIfZero(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            if (json.get(key).getAsLong() == 0) {
                json.remove(key);
            }
        }
    }
}

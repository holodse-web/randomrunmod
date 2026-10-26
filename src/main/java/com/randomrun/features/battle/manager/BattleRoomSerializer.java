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
        
        if (json.has("dd") && json.get("dd").isJsonObject()) {
            JsonObject dd = json.getAsJsonObject("dd");
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (String key : dd.keySet()) {
                if (dd.get(key).isJsonPrimitive() && !dd.get(key).getAsBoolean()) {
                    toRemove.add(key);
                }
            }
            toRemove.forEach(dd::remove);
            if (dd.size() == 0) {
                json.remove("dd");
            }
        }
        
        if (json.has("ep") && json.get("ep").isJsonObject()) {
            JsonObject ep = json.getAsJsonObject("ep");
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (String key : ep.keySet()) {
                if (ep.get(key).isJsonPrimitive() && !ep.get(key).getAsBoolean()) {
                    toRemove.add(key);
                }
            }
            toRemove.forEach(ep::remove);
            if (ep.size() == 0) {
                json.remove("ep");
            }
        }
        
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

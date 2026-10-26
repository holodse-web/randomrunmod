package com.randomrun.features.battle;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.randomrun.features.battle.screen.lobby.util.helper.LobbyRoomState;

public final class LobbyRoomStateVerification {
    private static int checks;
    private LobbyRoomStateVerification() {}

    public static int verify() {
        checks = 0;
        JsonObject original = object("{\"12345\":{\"host\":\"Host\",\"ug\":true,\"g\":{\"Host\":true,\"Guest\":true},\"h\":{\"Host\":{\"r\":false}}},\"54321\":{\"ug\":false}}");
        String source = original.toString();
        JsonObject nested = LobbyRoomState.apply(original, "put", "/12345/h/Host/r", new JsonPrimitive(true));
        check(nested.getAsJsonObject("12345").getAsJsonObject("h").getAsJsonObject("Host").get("r").getAsBoolean(), "nested ready PUT reaches the existing room");
        check(nested.size() == 2 && !nested.has("12345/h/Host/r"), "nested field is not a fake room code");
        check(nested.getAsJsonObject("12345").get("ug").getAsBoolean(), "nested updates preserve the Upgrader room rule");
        check(original.toString().equals(source), "applying a patch never mutates the caller's prior snapshot");

        JsonObject patch = object("{\"g/Guest\":null,\"ug\":false,\"h/Host/r\":true,\"st\":\"LOADING\"}");
        JsonObject changed = LobbyRoomState.apply(original, "patch", "/12345", patch);
        check(!changed.getAsJsonObject("12345").getAsJsonObject("g").has("Guest"), "nested patch null removes only the guest");
        check(changed.getAsJsonObject("12345").getAsJsonObject("g").has("Host"), "host membership survives a guest removal");
        check(!changed.getAsJsonObject("12345").get("ug").getAsBoolean(), "remote rule change is applied exactly");
        check(changed.getAsJsonObject("12345").get("st").getAsString().equals("LOADING"), "scalar room status patch is accepted");
        check(changed.getAsJsonObject("54321").equals(original.getAsJsonObject("54321")), "other rooms survive a partial update");
        check(original.toString().equals(source), "multi-path patch leaves its input snapshot untouched");

        JsonObject multi = LobbyRoomState.apply(original, "patch", "/", object("{\"12345/ug\":false,\"54321/st\":\"STARTED\",\"22222\":{\"ug\":true}}"));
        check(multi.size() == 3 && multi.getAsJsonObject("22222").get("ug").getAsBoolean(), "root multi-path patch adds a genuine room");
        check(!multi.has("12345/ug") && !multi.getAsJsonObject("12345").get("ug").getAsBoolean(), "root slash keys update nested fields");
        check(multi.getAsJsonObject("54321").get("st").getAsString().equals("STARTED"), "multi-room patch applies all independent paths");

        JsonObject deleted = LobbyRoomState.apply(original, "put", "/12345", JsonNull.INSTANCE);
        check(!deleted.has("12345") && deleted.has("54321"), "room deletion preserves unrelated rooms");
        check(LobbyRoomState.apply(original, "put", "/", JsonNull.INSTANCE).size() == 0, "root null clears the list");
        check(LobbyRoomState.apply(original, "put", "/", object("{\"88888\":{\"ug\":true}}")).size() == 1, "root PUT replaces rather than merges old rooms");
        check(LobbyRoomState.apply(original, "patch", "/", new JsonObject()).equals(original), "empty PATCH preserves the snapshot");
        check(LobbyRoomState.apply(original, "put", "/missing/nested", JsonNull.INSTANCE).equals(original), "deleting an absent deep field does not create ghost rooms");

        JsonObject replaced = LobbyRoomState.apply(original, "put", "/12345/g", object("{\"NewGuest\":true}"));
        check(replaced.getAsJsonObject("12345").getAsJsonObject("g").size() == 1, "PUT replaces a whole subtree rather than preserving removed players");
        JsonObject replacedChild = object("{\"g\":{\"NewGuest\":true}}");
        JsonObject patchSubtree = LobbyRoomState.apply(original, "patch", "/12345", replacedChild);
        check(patchSubtree.getAsJsonObject("12345").getAsJsonObject("g").size() == 1, "PATCH immediate object fields replace their values like Firebase REST");
        replacedChild.getAsJsonObject("g").addProperty("InjectedLater", true);
        check(!patchSubtree.getAsJsonObject("12345").getAsJsonObject("g").has("InjectedLater"), "result does not alias mutable event payload objects");
        check(LobbyRoomState.apply(null, "put", "/12345/ug", new JsonPrimitive(true)).getAsJsonObject("12345").get("ug").getAsBoolean(), "initial nested event constructs missing state");

        JsonObject room = original.getAsJsonObject("12345");
        JsonObject ready = LobbyRoomState.apply(room, "patch", "/h/Host", object("{\"r\":true}"));
        check(ready.getAsJsonObject("h").getAsJsonObject("Host").get("r").getAsBoolean() && ready.get("ug").getAsBoolean(), "room stream nested ready patch preserves the host's rule metadata");
        rejected(() -> LobbyRoomState.apply(original, "get", "/", JsonNull.INSTANCE), "unsupported event rejected");
        rejected(() -> LobbyRoomState.apply(original, "put", "12345", JsonNull.INSTANCE), "relative path rejected");
        rejected(() -> LobbyRoomState.apply(original, "put", "/12345//ug", new JsonPrimitive(true)), "empty path segment rejected");
        rejected(() -> LobbyRoomState.apply(original, "put", "/", new JsonPrimitive(5)), "malformed scalar root rejected");
        check(original.toString().equals(source), "validation failures never partially mutate the previous state");
        return checks;
    }

    private static JsonObject object(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Lobby SSE state: " + description);
        checks++;
    }
    private static void rejected(Runnable action, String description) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { check(true, description); return; }
        throw new AssertionError("Lobby SSE state: " + description);
    }
}

package com.randomrun.features.battle.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.randomrun.infrastructure.util.PasswordHasher;
import java.util.Base64;

public final class RoomPasswordVerification {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer()).create();
    private static final String PASSWORD = "1234";
    private static final String KNOWN_V2 =
        "v2:100000:AAAAAAAAAAAAAAAAAAAAAA==:K/pXx+2bppVSdrvFjtc3UGc6WvLnq/s9vQRRG3DRfa0=";
    private static final String KNOWN_LEGACY =
        "d9e113ac28c1e662fdde5b62d978698b115d5e425159308c23f7643ec6636b20";
    private static int checks;

    private RoomPasswordVerification() {}

    public static int verify() {
        checks = 0;
        check(PasswordHasher.verify(PASSWORD, KNOWN_V2), "known PBKDF2 v2 vector accepts the exact password");
        check(!PasswordHasher.verify("1235", KNOWN_V2), "known PBKDF2 v2 vector rejects a wrong password");
        check(!PasswordHasher.verify(null, KNOWN_V2), "missing password never matches a stored v2 hash");
        check(!PasswordHasher.verify(PASSWORD + " ", KNOWN_V2), "verification does not silently trim a password");

        BattleRoom created = create(true, PASSWORD);
        check(PasswordHasher.verify(PASSWORD, created.getPassword()), "actual room creation hashes plaintext exactly once");
        check(RoomLifecycleManager.validatePassword(created, PASSWORD), "actual join password gate accepts a freshly created private room");
        check(!RoomLifecycleManager.validatePassword(created, "incorrect"), "actual join password gate rejects a wrong password");
        check(!PasswordHasher.verify("incorrect", created.getPassword()), "actual room creation still rejects the wrong password");
        check(created.isPrivate() && created.getMaxPlayers() == 2 && created.getStatus() == BattleRoom.RoomStatus.WAITING,
            "private waiting-room metadata matches the reported room shape");
        check(created.isUpgrader() && created.getGameMode() == GameMode.RANDOM_DROP,
            "password fix preserves room gameplay flags");
        String stored = created.getPassword();
        String[] encodedParts = stored.split(":");
        check(encodedParts.length == 4 && stored.startsWith("v2:100000:") && !stored.equals(PASSWORD)
            && Base64.getDecoder().decode(encodedParts[2]).length == 16
            && Base64.getDecoder().decode(encodedParts[3]).length == 32,
            "creation stores a salted 256-bit v2 hash rather than plaintext");

        JsonObject wire = GSON.toJsonTree(created).getAsJsonObject();
        check(wire.get("pwd").getAsString().equals(stored), "compact serializer does not hash the encoded password again");
        check(wire.get("pr").getAsBoolean() && wire.get("mp").getAsInt() == 2
            && wire.get("st").getAsString().equals("WAITING"), "compact wire metadata retains private WAITING mp2");
        check(!wire.has("password"), "only the compact pwd property is emitted");
        BattleRoom received = GSON.fromJson(wire, BattleRoom.class);
        check(received.getPassword().equals(stored), "room deserialization preserves the exact encoded password");
        check(PasswordHasher.verify(PASSWORD, received.getPassword()), "correct password works after the real JSON round trip");
        check(RoomLifecycleManager.validatePassword(received, PASSWORD), "actual join password gate accepts a deserialized private room");
        check(!PasswordHasher.verify("incorrect", received.getPassword()), "wrong password remains rejected after the round trip");
        for (int round = 0; round < 3; round++) {
            received = GSON.fromJson(GSON.toJson(received), BattleRoom.class);
            check(received.getPassword().equals(stored), "repeated room polling does not rehash or mutate pwd");
        }
        check(PasswordHasher.verify(PASSWORD, received.getPassword()), "password remains usable across repeated room polling");

        BattleRoom second = create(true, PASSWORD);
        check(!second.getPassword().equals(stored), "separate rooms receive independent salted hashes");
        check(PasswordHasher.verify(PASSWORD, second.getPassword()), "salt changes do not change the accepted password");

        BattleRoom direct = new BattleRoom("Host", "263", "minecraft:dirt", "12345", true,
            PASSWORD, false, 2, "sw", false, false);
        check(PasswordHasher.verify(PASSWORD, direct.getPassword()), "public constructor has the same plaintext password contract");
        BattleRoom encodedLookingPassword = create(true, KNOWN_V2);
        check(!encodedLookingPassword.getPassword().equals(KNOWN_V2), "hash-looking plaintext is not mistaken for a stored hash");
        check(PasswordHasher.verify(KNOWN_V2, encodedLookingPassword.getPassword()), "hash-looking plaintext remains a valid exact password");
        check(!PasswordHasher.verify(PASSWORD, encodedLookingPassword.getPassword()), "an encoded-looking plaintext does not accept its inner password");

        BattleRoom publicRoom = create(false, null);
        JsonObject publicWire = GSON.toJsonTree(publicRoom).getAsJsonObject();
        check(!publicRoom.isPrivate() && publicRoom.getPassword() == null, "public room needs no password");
        check(!publicWire.has("pwd") && !publicWire.has("pr"), "compact public room omits password and false privacy flag");
        BattleRoom publicReceived = GSON.fromJson(publicWire, BattleRoom.class);
        check(!publicReceived.isPrivate() && publicReceived.getPassword() == null, "public room stays public after JSON decoding");
        check(RoomLifecycleManager.validatePassword(publicReceived, null), "actual join password gate accepts a public room without a password");

        for (String missing : new String[] {null, ""}) {
            BattleRoom brokenPrivate = new BattleRoom();
            brokenPrivate.setPrivate(true);
            brokenPrivate.setPassword(missing);
            check(!RoomLifecycleManager.validatePassword(brokenPrivate, PASSWORD), "private room with missing stored hash fails closed");
            check(!RoomLifecycleManager.validatePassword(brokenPrivate, null), "missing private hash cannot be bypassed with a null password");
        }

        for (String legacy : new String[] {"v1:" + KNOWN_LEGACY, KNOWN_LEGACY}) {
            JsonObject legacyWire = wire.deepCopy();
            legacyWire.addProperty("pwd", legacy);
            BattleRoom legacyRoom = GSON.fromJson(legacyWire, BattleRoom.class);
            check(legacyRoom.getPassword().equals(legacy), "legacy stored hash is read without rehashing");
            check(PasswordHasher.verify(PASSWORD, legacyRoom.getPassword()), "legacy room password remains compatible");
            check(RoomLifecycleManager.validatePassword(legacyRoom, PASSWORD), "actual join password gate supports legacy stored hashes");
            check(!PasswordHasher.verify("incorrect", legacyRoom.getPassword()), "legacy room rejects a wrong password");
            check(GSON.toJsonTree(legacyRoom).getAsJsonObject().get("pwd").getAsString().equals(legacy),
                "legacy stored hash survives a room update unchanged");
        }
        return checks;
    }

    public static void main(String[] args) {
        System.out.println("Room password PASS " + verify() + " checks");
    }

    private static BattleRoom create(boolean privateRoom, String password) {
        return RoomLifecycleManager.createRoomDetails("Host", "263", "minecraft:dirt", "12345",
            privateRoom, password, false, 2, "sw", true, true);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

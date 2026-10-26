package com.randomrun.features.upgrader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunResult;
import com.randomrun.core.session.RunSession;
import com.randomrun.core.session.RunSessionManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoomSerializer;
import com.randomrun.features.battle.manager.BattleStateController;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import java.lang.reflect.Field;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

public final class UpgraderBattleModeVerification {
    private static int checks;
    private UpgraderBattleModeVerification() {}

    public static int verify() {
        checks = 0;
        Gson gson = new GsonBuilder().registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer()).create();
        BattleRoom legacy = gson.fromJson("{\"h\":{\"Host\":{\"r\":false,\"l\":false}},\"st\":\"WAITING\",\"ca\":1,\"mp\":2}", BattleRoom.class);
        check(!legacy.isUpgrader(), "absent room flag defaults to classic");
        check(!gson.toJsonTree(legacy).getAsJsonObject().has("ug"), "classic flag is omitted from compact JSON");
        check(!gson.fromJson("{\"ug\":null}", BattleRoom.class).isUpgrader(), "null legacy flag remains disabled");
        check(!new BattleRoom("Host", "263", "minecraft:diamond", "ABC123", false, null,
            false, 2, "sw", true).isUpgrader(), "legacy constructor cannot silently enable Upgrader");

        Field instance = field(RandomRunMod.class, "instance");
        Field configField = field(RandomRunMod.class, "config");
        Field currentSession = field(RunSessionManager.class, "currentSession");
        RunSessionManager manager = RunSessionManager.getInstance();
        Object beforeMod = get(instance, null);
        Object beforeSession = get(currentSession, manager);
        Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        Field loaderConfigDir = field(loader.getClass(), "configDir");
        Object beforeConfigDir = get(loaderConfigDir, loader);
        try {
            if (beforeConfigDir == null) set(loaderConfigDir, loader, java.nio.file.Path.of(System.getProperty("java.io.tmpdir")));
            RandomRunMod localMod = new RandomRunMod();
            ModConfig config = new ModConfig();
            config.setUpgraderEnabled(true);
            set(configField, localMod, config);
            set(instance, null, localMod);
            set(currentSession, manager, null);
            EntityType<?> pig = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("pig"));
            for (String mode : new String[] {"sw", "radmin", "e4mc"}) {
                for (boolean randomDrop : new boolean[] {false, true}) {
                    for (boolean enabled : new boolean[] {false, true}) {
                        BattleRoom room = new BattleRoom("Host", "263", "minecraft:diamond", "ABC123", false,
                            null, !mode.equals("sw"), 2, mode, randomDrop, enabled);
                        room.addPlayer("Guest");
                        BattleRoom remote = gson.fromJson(gson.toJson(room), BattleRoom.class);
                        check(remote.isUpgrader() == enabled, "room mode round trip: " + mode);
                        check(remote.isRandomDrop() == randomDrop, "Random Drop remains independent: " + mode);
                        check(gson.toJsonTree(room).getAsJsonObject().has("ug") == enabled,
                            "only enabled rooms transmit the compact flag: " + mode);
                        var request = BattleStateController.createWorldRequest(remote);
                        check(request.isUpgrader() == enabled, "battle world uses room flag despite enabled solo config: " + mode);
                        check(request.isRandomDrop() == randomDrop && request.getSeed().equals("263")
                            && request.getTargetItem() == Items.DIAMOND, "battle world preserves target, seed and drop rules: " + mode);
                        RunSession session = new RunSession(Items.DIAMOND, true, "ABC123", "263", "battle", remote.isUpgrader());
                        check(session.isBattle() && session.isUpgrader() == enabled,
                            "host and guest sessions can capture explicit battle mode: " + mode);
                        remote.setUpgrader(!enabled);
                        check(session.isUpgrader() == enabled && request.isUpgrader() == enabled,
                            "room changes cannot rewrite the captured run/world mode: " + mode);
                    }
                }
            }
            check(!new RunSession(Items.DIAMOND, true, "ABC123", "263", "battle").isUpgrader(),
                "legacy item battle constructor ignores the solo toggle");
            check(!new RunSession(pig, null, true, "ABC123", "263", "battle").isUpgrader(),
                "legacy mob battle constructor ignores the solo toggle");
            RunSession explicitMob = new RunSession(pig, null, true, "ABC123", "263", "battle", true);
            check(explicitMob.isBattle() && explicitMob.isUpgrader() && explicitMob.snapshot().isUpgrader,
                "explicit battle mode survives mob session snapshots");
            check(new RunSession(Items.DIAMOND, false, null, null, "solo").isUpgrader(),
                "solo default still captures the solo toggle");
            check(!new RunSession(Items.DIAMOND, false, null, null, "solo", false).isUpgrader(),
                "explicit disabled mode overrides solo defaults");
            manager.startRun(Items.DIAMOND, true, "ABC123", "263", "battle");
            check(manager.getCurrentSession() != null && !manager.getCurrentSession().isUpgrader(),
                "legacy manager item battle entry ignores enabled solo config");
            manager.cancelRun();
            manager.startMobRun(pig, null, true, "ABC123", "263", "battle");
            check(manager.getCurrentSession() != null && !manager.getCurrentSession().isUpgrader(),
                "legacy manager mob battle entry ignores enabled solo config");
            manager.cancelRun();
            manager.startRun(Items.DIAMOND, true, "ABC123", "263", "battle", true);
            check(manager.getCurrentSession() != null && manager.getCurrentSession().isUpgrader(),
                "manager accepts an explicitly enabled battle mode");
            config.setUpgraderEnabled(false);
            check(manager.getCurrentSession().isUpgrader(), "changing local settings cannot disable an active room mode");
            Run assistedBattle = new Run.Builder().targetId("minecraft:diamond").result(RunResult.VICTORY)
                .duration(1).isBattle(true).battleRoomId("ABC123").isHardcore(true).isUpgrader(true).build();
            Run loaded = new Gson().fromJson(new Gson().toJson(assistedBattle), Run.class);
            check(loaded.isBattle() && loaded.isUpgrader() && !loaded.isEligibleForLeaderboard(),
                "battle history preserves both flags and remains excluded from classic leaderboard");
        } finally {
            set(currentSession, manager, beforeSession);
            set(instance, null, beforeMod);
            set(loaderConfigDir, loader, beforeConfigDir);
        }
        return checks;
    }

    private static Field field(Class<?> owner, String name) {
        try { Field result = owner.getDeclaredField(name); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object get(Field field, Object owner) {
        try { return field.get(owner); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    private static void set(Field field, Object owner, Object value) {
        try { field.set(owner, value); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

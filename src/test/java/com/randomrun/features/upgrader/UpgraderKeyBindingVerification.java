package com.randomrun.features.upgrader;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.features.upgrader.client.UpgraderClient;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.infrastructure.util.LegacyKeyCodes;
import java.lang.reflect.Field;
import java.nio.file.Path;
import net.minecraft.client.input.KeyEvent;

public final class UpgraderKeyBindingVerification {
    private static int checks;
    private UpgraderKeyBindingVerification() {}

    public static void main(String[] args) {
        System.out.println("Upgrader binding verification passed: " + verify() + " assertions");
    }

    public static int verify() {
        checks = 0;
        Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        Field configDir = field(loader.getClass(), "configDir");
        Field instance = field(RandomRunMod.class, "instance");
        Object previousDir = get(configDir, loader), previousMod = get(instance, null);
        try {
            if (previousDir == null) set(configDir, loader, Path.of(System.getProperty("java.io.tmpdir")));
            Gson gson = new Gson();
            ModConfig legacy = gson.fromJson("{\"startKey\":293,\"upgraderEnabled\":true}", ModConfig.class);
            check(legacy.getUpgraderKey() == ModConfig.DEFAULT_UPGRADER_KEY, "old config defaults to U");
            check(LegacyKeyCodes.toInputKey(legacy.getUpgraderKey()) == InputConstants.KEY_U, "saved U uses SDL input code");
            check(legacy.getStartKey() == 293 && legacy.isUpgraderEnabled(), "old start binding and mode remain intact");
            check(gson.fromJson("{\"upgraderKey\":null}", ModConfig.class).getUpgraderKey() == ModConfig.DEFAULT_UPGRADER_KEY,
                "legacy null field retains U default");

            long now = System.currentTimeMillis();
            Field lastSave = field(ModConfig.class, "lastSaveTime");
            set(lastSave, legacy, now);
            legacy.setUpgraderKey(LegacyKeyCodes.toLegacyKey(InputConstants.KEY_K));
            check((long) get(lastSave, legacy) == 0, "rapid remapping bypasses save debounce");
            ModConfig loaded = gson.fromJson(gson.toJson(legacy), ModConfig.class);
            check(LegacyKeyCodes.toInputKey(loaded.getUpgraderKey()) == InputConstants.KEY_K, "custom binding survives JSON round trip");
            check(loaded.getStartKey() == 293, "upgrader remapping does not change the start key");

            RandomRunMod mod = new RandomRunMod();
            set(field(RandomRunMod.class, "config"), mod, loaded);
            set(instance, null, mod);
            check(UpgraderClient.matchesOpenKey(new KeyEvent(InputConstants.KEY_K, 0, 0)), "close key follows remapped config");
            check(!UpgraderClient.matchesOpenKey(new KeyEvent(InputConstants.KEY_U, 0, 0)), "old U does not also trigger");
            loaded.setUpgraderKey(-1);
            check(!UpgraderClient.matchesOpenKey(new KeyEvent(InputConstants.KEY_K, 0, 0)), "cleared binding cannot trigger");
            check(gson.fromJson(gson.toJson(loaded), ModConfig.class).getUpgraderKey() == -1, "unbound state survives JSON");
            loaded.setUpgraderKey(ModConfig.DEFAULT_UPGRADER_KEY);
            check(UpgraderClient.matchesOpenKey(new KeyEvent(InputConstants.KEY_U, 0, 0)), "reset restores the default U binding");
            set(lastSave, loaded, now);
            loaded.setStartKey(294);
            check((long) get(lastSave, loaded) == 0, "start remapping also persists after a recent save");
            set(lastSave, loaded, now);
            loaded.setStartMethod(ModConfig.StartMethod.KEYBIND);
            check((long) get(lastSave, loaded) == 0, "start method toggle persists after remapping");

            int unusual = 150;
            loaded.setUpgraderKey(LegacyKeyCodes.toLegacyKey(unusual));
            check(gson.fromJson(gson.toJson(loaded), ModConfig.class).getUpgraderKey() == 100_000 + unusual,
                "SDL-only key encoding survives configuration serialization");
        } finally {
            set(instance, null, previousMod);
            set(configDir, loader, previousDir);
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

package com.randomrun.infrastructure.handler;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.core.session.RunSessionManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

public class TickHandler {
    private static final RunSessionManager SESSION_MANAGER = RunSessionManager.getInstance();

    private static boolean registered = false;
    private static boolean lastKeyState = false;
    private static long lastKeyPressTime = 0;

    private static int keyCheckCounter = 0;

    private static final int KEY_CHECK_INTERVAL = 2;
    private static final long KEY_DEBOUNCE_MS = 200;

    private static ModConfig cachedConfig;
    private static long lastConfigCache = 0;
    private static final long CONFIG_CACHE_MS = 1000;

    public static void register() {
        if (!registered) {
            ClientTickEvents.END_CLIENT_TICK.register(TickHandler::onEndTick);
            registered = true;
        }
    }

    public static void unregister() {
        registered = false;
        reset();
    }

    private static void onEndTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (keyCheckCounter++ >= KEY_CHECK_INTERVAL) {
            keyCheckCounter = 0;
            checkStartKey(client);
        }

        VictoryHandler.tick();

        checkInventoryForTarget(client);
        com.randomrun.infrastructure.security.InventoryMonitor.checkInventory();

        com.randomrun.infrastructure.security.AntiCheatService.getInstance().tick();
    }

    private static void checkStartKey(Minecraft client) {
        if (client.gui.screen() != null) return;

        ModConfig config = getCachedConfig();
        if (config.getStartMethod() != ModConfig.StartMethod.KEYBIND) return;

        int key = config.getStartKey();
        if (key == -1) return;

        boolean isPressed = InputConstants.isKeyDown(com.randomrun.infrastructure.util.LegacyKeyCodes.toInputKey(key));

        long now = System.currentTimeMillis();
        if (isPressed && !lastKeyState && (now - lastKeyPressTime) >= KEY_DEBOUNCE_MS) {
            lastKeyPressTime = now;
            if (!StartRunHandler.tryStartRun(client.player)) {
                String reason = StartRunHandler.getStartFailureReason(client.player);
                if (!reason.contains("не заморожен")) {
                    CompactLogger.warn("[TickHandler] Start failed: {}", reason);
                }
            }
        }

        lastKeyState = isPressed;
    }

    private static void checkInventoryForTarget(Minecraft client) {
        if (!SESSION_MANAGER.hasActiveRun() || !SESSION_MANAGER.isRunning()) return;
        if (com.randomrun.features.upgrader.client.UpgraderClient.isAnimating()) return;

        Item targetItem = SESSION_MANAGER.getTargetItem();
        if (targetItem == null) return;

        if (client.player == null || client.player.getInventory() == null) return;

        try {
            var inventory = client.player.getInventory();
            int size = inventory.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (inventory.getItem(i).getItem() == targetItem) {
                    VictoryHandler.handleVictory();
                    break;
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("[TickHandler] Inventory check error: {}", e.getMessage());
        }
    }

    private static ModConfig getCachedConfig() {
        long now = System.currentTimeMillis();
        if (cachedConfig == null || now - lastConfigCache > CONFIG_CACHE_MS) {
            cachedConfig = RandomRunMod.getInstance().getConfig();
            lastConfigCache = now;
        }
        return cachedConfig;
    }

    public static void reset() {
        lastKeyState = false;
        lastKeyPressTime = 0;
        keyCheckCounter = 0;
        cachedConfig = null;
        lastConfigCache = 0;
    }

    public static void invalidateConfigCache() {
        cachedConfig = null;
        lastConfigCache = 0;
    }

    public static boolean isRegistered() {
        return registered;
    }
}

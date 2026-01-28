package com.randomrun.world;

import com.randomrun.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.item.Item;

public class WorldCreator {
    
    private static Item pendingTargetItem = null;
    private static long pendingTimeLimit = 0;
    private static String pendingSeed = null;
    private static String lastCreatedSeed = null;
    
    public static void createSpeedrunWorld(Item targetItem) {
        createSpeedrunWorld(targetItem, 0);
    }
    
    public static void createSpeedrunWorld(Item targetItem, long timeLimitMs) {
        createSpeedrunWorld(targetItem, timeLimitMs, null);
    }
    
    // Метод для онлайн режима с заданным сидом
    public static void createSpeedrunWorld(Item targetItem, String seed) {
        createSpeedrunWorld(targetItem, 0, seed);
    }
    
    public static void createSpeedrunWorld(Item targetItem, long timeLimitMs, String seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // ✅ КРИТИЧНО: Сохраняем данные ДО создания экрана
        pendingTargetItem = targetItem;
        pendingTimeLimit = timeLimitMs;
        pendingSeed = seed;
        
        // ✅ ДОБАВИТЬ ЛОГИ ДЛЯ ОТЛАДКИ
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        RandomRunMod.LOGGER.info("🌍 WorldCreator.createSpeedrunWorld()");
        RandomRunMod.LOGGER.info("  - Target Item: " + (targetItem != null ? targetItem.toString() : "NULL"));
        RandomRunMod.LOGGER.info("  - Time Limit: " + timeLimitMs + " ms");
        RandomRunMod.LOGGER.info("  - Seed Param: " + seed);
        RandomRunMod.LOGGER.info("  - pendingSeed saved: " + pendingSeed);
        RandomRunMod.LOGGER.info("  - pendingTargetItem saved: " + (pendingTargetItem != null ? "YES" : "NULL"));
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        
        // Generate world name
        String worldName = generateWorldName(targetItem, timeLimitMs);
        
        if (seed != null) {
            RandomRunMod.LOGGER.info("🎮 Creating speedrun world: " + worldName + " with seed: " + seed);
        } else {
            RandomRunMod.LOGGER.info("🎮 Creating speedrun world: " + worldName);
        }
        
        // ✅ Открываем экран создания мира
        // CreateWorldScreenMixin перехватит init() и применит настройки
        CreateWorldScreen.create(client, client.currentScreen);
        
        RandomRunMod.LOGGER.info("✅ CreateWorldScreen.create() called");
    }
    
    public static Item getPendingTargetItem() {
        return pendingTargetItem;
    }
    
    public static long getPendingTimeLimit() {
        return pendingTimeLimit;
    }
    
    public static String getPendingSeed() {
        RandomRunMod.LOGGER.info("🔍 getPendingSeed() called, returning: " + pendingSeed);
        return pendingSeed;
    }
    
    public static String getLastCreatedSeed() {
        return lastCreatedSeed;
    }
    
    public static void setLastCreatedSeed(String seed) {
        RandomRunMod.LOGGER.info("💾 setLastCreatedSeed(): " + seed);
        lastCreatedSeed = seed;
    }
    
    public static void clearPendingData() {
        RandomRunMod.LOGGER.info("🧹 clearPendingData() called");
        pendingTargetItem = null;
        pendingTimeLimit = 0;
        pendingSeed = null;
    }
    
    public static boolean hasPendingRun() {
        boolean result = pendingTargetItem != null;
        RandomRunMod.LOGGER.info("❓ hasPendingRun() = " + result);
        return result;
    }
    
    public static String generateWorldName(Item item) {
        return generateWorldName(item, 0);
    }
    
    public static String generateWorldName(Item item, long timeLimitMs) {
        String itemName = item.getName().getString();
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        String mode = timeChallengeEnabled ? "Время" : "Стандарт";
        return itemName + " - " + mode;
    }
}
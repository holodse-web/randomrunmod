package com.randomrun.challenges.classic.world;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

public class WorldCreator {
    
    private static Item pendingTargetItem = null;
    private static net.minecraft.util.Identifier pendingAdvancementId = null;
    private static long pendingTimeLimit = 0;
    private static String pendingSeed = null;
    private static String lastCreatedSeed = null;
    private static boolean isManualSeed = false; // New flag to distinguish manual vs random seeds
    private static boolean isSpeedrunLoading = false;
    private static boolean creationTriggered = false;
    
    public static void createSpeedrunWorld(Item targetItem) {
        createSpeedrunWorld(targetItem, 0);
    }
    
    public static void createSpeedrunWorld(Item targetItem, long timeLimitMs) {
        createSpeedrunWorld(targetItem, timeLimitMs, null);
    }
    
    // Метод для онлайн режима с заданным сидом
    public static void createSpeedrunWorld(Item targetItem, String seed) {
        // ИСПРАВЛЕНИЕ: Обрезать сид, если он не null
        if (seed != null) seed = seed.trim();
        createSpeedrunWorld(targetItem, 0, seed);
    }
    
    public static void createSpeedrunWorld(Item targetItem, long timeLimitMs, String seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // ИСПРАВЛЕНИЕ: Обрезать сид, если он не null
        if (seed != null) seed = seed.trim();
        
        // ✅ КРИТИЧНО: Сохраняем данные ДО создания экрана
        pendingTargetItem = targetItem;
        pendingAdvancementId = null;
        pendingTimeLimit = timeLimitMs;
        pendingSeed = seed;
        isSpeedrunLoading = true;
        creationTriggered = false;
        
        // ✅ ДОБАВИТЬ ЛОГИ ДЛЯ ОТЛАДКИ
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        RandomRunMod.LOGGER.info("🌍 WorldCreator.createSpeedrunWorld()");
        RandomRunMod.LOGGER.info("  - Целевой предмет: " + (targetItem != null ? targetItem.toString() : "NULL"));
        RandomRunMod.LOGGER.info("  - Лимит времени: " + timeLimitMs + " мс");
        RandomRunMod.LOGGER.info("  - Параметр сида: " + seed);
        RandomRunMod.LOGGER.info("  - pendingSeed сохранен: " + pendingSeed);
        RandomRunMod.LOGGER.info("  - pendingTargetItem сохранен: " + (pendingTargetItem != null ? "ДА" : "NULL"));
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        
        // Генерация имени мира
        String worldName = generateWorldName(targetItem, timeLimitMs);
        
        if (seed != null) {
            RandomRunMod.LOGGER.info("🎮 Создание мира для спидрана: " + worldName + " с сидом: " + seed);
        } else {
            RandomRunMod.LOGGER.info("🎮 Создание мира для спидрана: " + worldName);
        }
        
        // Попытка открыть CreateWorldScreen напрямую
        // CreateWorldScreen.create(client, client.currentScreen); // Старая сигнатура метода 1.20
        // В 1.21.4 create требует больше аргументов или использует другой статический метод.
        // Самый надежный способ - использовать SelectWorldScreen и позволить пользователю нажать 'Создать новый мир',
        // ИЛИ попытаться вызвать правильный конструктор, если это возможно.
        // Однако, так как были ошибки компиляции, вернемся к безопасному подходу с SelectWorldScreen
        // но добавим четкое сообщение и логирование.
        
        try {
            // Попытка открыть SelectWorldScreen (меню одиночной игры)
            // Оттуда пользователь может нажать 'Создать новый мир'.
            // Наш WorldManagementMixin должен обработать автозаполнение при нажатии 'Создать новый мир'.
            client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(client.currentScreen));
            
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("§e⚠ Пожалуйста, нажмите 'Создать новый мир' для продолжения."), false);
            }
            RandomRunMod.LOGGER.info("✅ Открыт SelectWorldScreen для ручного шага создания");
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Не удалось открыть SelectWorldScreen", e);
        }
    }
    
    public static void createSpeedrunWorld(net.minecraft.util.Identifier advancementId, long timeLimitMs, String seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // ИСПРАВЛЕНИЕ: Обрезать сид, если он не null
        if (seed != null) seed = seed.trim();
        
        pendingAdvancementId = advancementId;
        pendingTargetItem = null;
        pendingTimeLimit = timeLimitMs;
        pendingSeed = seed;
        creationTriggered = false;
        
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        RandomRunMod.LOGGER.info("🌍 WorldCreator.createSpeedrunWorld() [Достижение]");
        RandomRunMod.LOGGER.info("  - ID Достижения: " + advancementId);
        RandomRunMod.LOGGER.info("  - Лимит времени: " + timeLimitMs + " мс");
        RandomRunMod.LOGGER.info("  - Параметр сида: " + seed);
        RandomRunMod.LOGGER.info("═══════════════════════════════════");
        
        String worldName = "RandomRun " + advancementId.getPath().replace('/', '_');
        
        if (seed != null) {
            RandomRunMod.LOGGER.info("🎮 Создание мира для спидрана: " + worldName + " с сидом: " + seed);
        } else {
            RandomRunMod.LOGGER.info("🎮 Создание мира для спидрана: " + worldName);
        }
        
        // Попытка открыть CreateWorldScreen напрямую
        // В 1.21.4 create требует больше аргументов. Возвращаемся к SelectWorldScreen как запасному варианту.
        try {
            // ВАЖНО: Мы открываем SelectWorldScreen (список миров).
            // Пользователь должен нажать "Создать новый мир" вручную.
            // Наш миксин (WorldManagementMixin) перехватит инициализацию экрана создания и заполнит данные.
            // Автоматическое открытие экрана создания (CreateWorldScreen) пока невозможно из-за API изменений.
            client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(client.currentScreen));
            
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("§e⚠ Пожалуйста, нажмите 'Создать новый мир' для продолжения."), false);
            }
            RandomRunMod.LOGGER.info("✅ Открыт SelectWorldScreen для ручного шага создания");
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Не удалось открыть SelectWorldScreen", e);
        }
    }
    
    public static Item getPendingTargetItem() {
        return pendingTargetItem;
    }
    
    public static long getPendingTimeLimit() {
        return pendingTimeLimit;
    }
    
    public static String getPendingSeed() {
        RandomRunMod.LOGGER.info("🔍 getPendingSeed() вызван, возвращает: " + pendingSeed);
        return pendingSeed;
    }
    
    public static String getLastCreatedSeed() {
        return lastCreatedSeed;
    }
    
    public static void setLastCreatedSeed(String seed) {
        setLastCreatedSeed(seed, false);
    }

    public static void setLastCreatedSeed(String seed, boolean manual) {
        if (seed != null) seed = seed.trim();
        RandomRunMod.LOGGER.info("💾 setLastCreatedSeed(): '" + seed + "' (Manual: " + manual + ")");
        lastCreatedSeed = seed;
        isManualSeed = manual;
    }
    
    public static boolean isManualSeed() {
        return isManualSeed;
    }
    
    public static net.minecraft.util.Identifier getPendingAdvancementId() {
        return pendingAdvancementId;
    }

    public static void clearPendingData() {
        RandomRunMod.LOGGER.info("🧹 clearPendingData() вызван");
        pendingTargetItem = null;
        pendingAdvancementId = null;
        pendingTimeLimit = 0;
        pendingSeed = null;
        isSpeedrunLoading = false; // FIX: Reset loading flag
        creationTriggered = false;
    }
    
    public static boolean isCreationTriggered() {
        return creationTriggered;
    }

    public static void setCreationTriggered(boolean triggered) {
        creationTriggered = triggered;
    }
    
    public static boolean isSpeedrunLoading() {
        return isSpeedrunLoading;
    }

    public static boolean hasPendingRun() {
        boolean result = pendingTargetItem != null || pendingAdvancementId != null;
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
        return "RandomRun: " + itemName + " (" + mode + ")"; // FIX: Add prefix for safer detection
    }
}
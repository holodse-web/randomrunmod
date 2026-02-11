package com.randomrun.main.util;

import com.randomrun.main.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.leaderboard.LeaderboardManager;
import com.randomrun.ui.screen.endgame.VictoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class VictoryHandler {
    
    private static long victoryTime = 0;
    private static boolean victoryScheduled = false;
    private static boolean waitingForDelay = false;
    
    public static void handleVictory() {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        ModConfig config = RandomRunMod.getInstance().getConfig();
        BattleManager battleManager = BattleManager.getInstance();
        
        // Получить прошедшее время ПЕРЕД завершением забега
        long elapsedTime = runManager.getCurrentTime();
        
        // Завершить забег
        runManager.completeRun();
        
        // Отправить в таблицу лидеров (База данных)
        // Мы отправляем здесь безусловно, так как убрали отправку из RunDataManager.saveResult()
        // Это гарантирует, что мы отправляем только ОДИН РАЗ за победу.
        
        // Check if run was seeded manually
        String seed = com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed();
        boolean isManualSeed = com.randomrun.challenges.classic.world.WorldCreator.isManualSeed();
        
        if (isManualSeed) {
            RandomRunMod.LOGGER.info("Manual seeded run detected (" + seed + "). Skipping leaderboard submission.");
        } else {
            submitToLeaderboard(runManager, elapsedTime);
        }
        
        // Определяем режим Хардкор для сохранения в статистику
        boolean isHardcore = true;
        try {
            if (MinecraftClient.getInstance().world != null) {
                isHardcore = MinecraftClient.getInstance().world.getLevelProperties().isHardcore();
            } else {
                 isHardcore = config.isHardcoreModeEnabled();
            }
        } catch (Exception e) {}
        
        // Сообщить о победе в Firebase, если в битве
        if (battleManager.isInBattle()) {
            RandomRunMod.LOGGER.info("VictoryHandler: Сообщаем о победе в BattleManager. Время: " + elapsedTime);
            battleManager.reportVictory(elapsedTime);
        } else {
            RandomRunMod.LOGGER.info("VictoryHandler: Не в битве, пропускаем отчет в BattleManager.");
            // Для одиночных забегов увеличиваем глобальную статистику напрямую
            // Это гарантирует, что одиночные забеги учитываются в глобальной статистике "Всего спидранов"
            com.randomrun.main.data.GlobalStatsManager.incrementRun();
            
            // Add to Player Profile (Stats/History only, bests are disabled in PlayerProfile)
            // We need this here because we removed it from LeaderboardManager
            String targetId = runManager.getTargetType() == RunDataManager.TargetType.ITEM 
                ? Registries.ITEM.getId(runManager.getTargetItem()).toString() 
                : runManager.getTargetAdvancementId().toString();
            
            // Получить сид для сохранения в профиль
            String currentSeed = com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed();
            
            com.randomrun.main.data.PlayerProfile.get().addRun(elapsedTime, true, targetId, elapsedTime, currentSeed, false, isHardcore);
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        // Используем уже полученное прошедшее время
        String itemName;
        if (runManager.getTargetItem() != null) {
            itemName = runManager.getTargetItem().getName().getString();
        } else {
            itemName = "Achievement";
        }
        
        // Воспроизведение звука победы
        if (config.isSoundEffectsEnabled()) {
            float volume = config.getSoundVolume() / 100f;
            client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, volume, 1.0f);
        }
        
        // Показываем экран победы в зависимости от настройки
        ModConfig.VictoryScreenMode mode = config.getVictoryScreenMode();
        
        // Показываем заголовок и сообщение в чате, только если НЕ в режиме "Показать через 10 секунд"
        // Или если в режиме "Скрыть" (игрок остается в мире, нужно знать, что он выиграл)
        if (mode != ModConfig.VictoryScreenMode.SHOW_AFTER_10_SECONDS) {
            // Показать заголовок победы
            client.inGameHud.setTitle(Text.literal("§a§lVICTORY!"));
            client.inGameHud.setSubtitle(Text.literal("§e" + RunDataManager.formatTime(elapsedTime)));
            
            // Отправить сообщение в чат
            client.player.sendMessage(
                Text.translatable("randomrun.victory.message", itemName, RunDataManager.formatTime(elapsedTime)),
                false
            );
        }
        
        // Запуск фейерверков (клиентские частицы)
        spawnVictoryParticles(client);
        
        switch (mode) {
            case SHOW -> {
                // Сразу показываем экран победы
                showVictoryScreen(client, runManager);
            }
            case HIDE -> {
                // Не показываем экран и ничего не делаем
                // Игрок остается в мире
            }
            case SHOW_AFTER_10_SECONDS -> {
                // Запланировать показ через 10 секунд
                victoryScheduled = true;
                waitingForDelay = true;
                victoryTime = System.currentTimeMillis() + 10000; // 10 секунд
            }
        }
        
        // ВАЖНО: Если мы в битве, НЕ останавливаем игру здесь, BattleManager сам решит когда остановить (после задержки)
        // В одиночной игре:
        if (!battleManager.isInBattle()) {
             // Если режим HIDE, мы НЕ останавливаем рантайм, даем игроку бегать
             // Но статус уже COMPLETED, так что таймер остановлен
        }
    }
    
    
    private static void showVictoryScreen(MinecraftClient client, RunDataManager runManager) {
        // Показываем экран победы через execute для вызова из рендер-потока
        // НЕ выходим из мира - выход происходит при нажатии кнопок в VictoryScreen
        client.execute(() -> {
            if (runManager.getTargetItem() != null) {
                client.setScreen(new VictoryScreen(
                    runManager.getTargetItem(),
                    runManager.getElapsedTime()
                ));
            } else if (runManager.getTargetAdvancementId() != null) {
                client.setScreen(new VictoryScreen(
                    runManager.getTargetAdvancementId(),
                    runManager.getElapsedTime()
                ));
            }
        });
    }
    
    public static void tick() {
        if (victoryScheduled && waitingForDelay) {
            if (System.currentTimeMillis() >= victoryTime) {
                // 10 секунд прошло - показываем экран
                MinecraftClient client = MinecraftClient.getInstance();
                RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
                showVictoryScreen(client, runManager);
                waitingForDelay = false;
                victoryScheduled = false;
            }
        }
    }
    
    private static void spawnVictoryParticles(MinecraftClient client) {
        // Отключен запуск фейерверков при старте забега
        // Этот метод должен вызываться только при фактической победе
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (runManager.getStatus() != RunDataManager.RunStatus.COMPLETED) {
            return;
        }

        if (client.player == null || client.world == null) return;
        
        double x = client.player.getX();
        double y = client.player.getY() + 1;
        double z = client.player.getZ();
        
        // Запуск праздничных частиц
        for (int i = 0; i < 50; i++) {
            double offsetX = (Math.random() - 0.5) * 4;
            double offsetY = Math.random() * 3;
            double offsetZ = (Math.random() - 0.5) * 4;
            
            client.world.addParticle(
                ParticleTypes.FIREWORK,
                x + offsetX, y + offsetY, z + offsetZ,
                0, 0.1, 0
            );
        }
    }
    
    public static void reset() {
        victoryScheduled = false;
        waitingForDelay = false;
        victoryTime = 0;
    }
    
    private static void submitToLeaderboard(RunDataManager runManager, long elapsedTime) {
        // Проверка Онлайн Режима
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            RandomRunMod.LOGGER.info("Онлайн режим отключен, пропускаем отправку в таблицу лидеров.");
            return;
        }

        // 1. Проверка Античита - УДАЛЕНО
        
        // 2. Проверка Подозрительной Активности (Эвристика) - УДАЛЕНО

        try {
            LeaderboardManager leaderboardManager = LeaderboardManager.getInstance();
            
            // Примечание: targetId уже определен выше в области видимости
            String targetId = runManager.getTargetType() == RunDataManager.TargetType.ITEM 
                ? Registries.ITEM.getId(runManager.getTargetItem()).toString() 
                : runManager.getTargetAdvancementId().toString();
            
            String targetType;
            String difficulty = "UNKNOWN";
            
            if (runManager.getTargetType() == RunDataManager.TargetType.ITEM) {
                if (runManager.getTargetItem() == null) return;
                // targetId уже установлен
                targetType = "ITEM";
                
                try {
                    com.randomrun.challenges.classic.data.ItemDifficulty.Difficulty diff = 
                        com.randomrun.challenges.classic.data.ItemDifficulty.getDifficulty(runManager.getTargetItem());
                    difficulty = diff.displayName;
                } catch (Exception e) {
                    RandomRunMod.LOGGER.warn("Не удалось получить сложность предмета", e);
                }
            } else {
                if (runManager.getTargetAdvancementId() == null) return;
                // targetId уже установлен
                targetType = "ADVANCEMENT";
                difficulty = "Achievement";
            }
            
            long timeLimit = runManager.getTimeLimit();
            boolean isTimeChallenge = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
            
            // Получить текущие попытки из RunDataManager
            int attempts = 1;
            try {
                if (targetId != null) {
                    RunDataManager.RunResult result = runManager.getResultForItem(targetId);
                    if (result != null) {
                        attempts = result.attempts;
                    }
                }
            } catch (Exception e) {
                // Игнорируем, по умолчанию 1
            }
            
            // Определяем режим Хардкор
            boolean isHardcore = true;
            try {
                if (MinecraftClient.getInstance().world != null) {
                    isHardcore = MinecraftClient.getInstance().world.getLevelProperties().isHardcore();
                } else {
                     isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
                }
            } catch (Exception e) {}
            
            leaderboardManager.submitCurrentRun(
                targetId,
                targetType,
                elapsedTime,
                timeLimit,
                difficulty,
                isTimeChallenge,
                attempts,
                isHardcore
            ).thenAccept(success -> {
                if (success) {
                    RandomRunMod.LOGGER.info("✅ Запись успешно отправлена в глобальную таблицу лидеров!");
                } else {
                    RandomRunMod.LOGGER.warn("⚠️ Не удалось отправить запись в таблицу лидеров");
                }
            }).exceptionally(throwable -> {
                RandomRunMod.LOGGER.error("❌ Ошибка отправки в таблицу лидеров", throwable);
                return null;
            });
            
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Ошибка в submitToLeaderboard", e);
        }
    }
}

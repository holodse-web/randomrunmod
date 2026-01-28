package com.randomrun.util;

import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.config.ModConfig;
import com.randomrun.data.RunDataManager;
import com.randomrun.gui.screen.VictoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
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
        
        // Get elapsed time BEFORE completing the run
        long elapsedTime = runManager.getCurrentTime();
        
        // Complete the run
        runManager.completeRun();
        
        // Report victory to Firebase if in battle
        if (battleManager.isInBattle()) {
            RandomRunMod.LOGGER.info("═══════════════════════════════════");
            RandomRunMod.LOGGER.info("🏆 VICTORY via VictoryHandler!");
            RandomRunMod.LOGGER.info("  - Time: " + elapsedTime + "ms");
            RandomRunMod.LOGGER.info("═══════════════════════════════════");
            battleManager.reportVictory(elapsedTime);
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        // Use the elapsed time we already captured
        String itemName = runManager.getTargetItem().getName().getString();
        
        // Play victory sound
        if (config.isSoundEffectsEnabled()) {
            float volume = config.getSoundVolume() / 100f;
            client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, volume, 1.0f);
        }
        
        // Show victory title
        client.inGameHud.setTitle(Text.literal("§a§lVICTORY!"));
        client.inGameHud.setSubtitle(Text.literal("§e" + RunDataManager.formatTime(elapsedTime)));
        
        // Send chat message
        client.player.sendMessage(
            Text.translatable("randomrun.victory.message", itemName, RunDataManager.formatTime(elapsedTime)),
            false
        );
        
        // Spawn fireworks (client-side particles)
        spawnVictoryParticles(client);
        
        // Показываем экран победы в зависимости от настройки
        ModConfig.VictoryScreenMode mode = config.getVictoryScreenMode();
        
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
    }
    
    
    private static void showVictoryScreen(MinecraftClient client, RunDataManager runManager) {
        // Показываем экран победы через execute для вызова из рендер-потока
        // НЕ выходим из мира - выход происходит при нажатии кнопок в VictoryScreen
        client.execute(() -> {
            client.setScreen(new VictoryScreen(
                runManager.getTargetItem(),
                runManager.getElapsedTime()
            ));
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
        if (client.player == null || client.world == null) return;
        
        double x = client.player.getX();
        double y = client.player.getY() + 1;
        double z = client.player.getZ();
        
        // Spawn celebration particles
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
}

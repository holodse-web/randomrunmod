package com.randomrun.infrastructure.handler;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.core.service.RunCompletionService;
import com.randomrun.core.session.RunSessionManager;
import com.randomrun.core.history.Run;
import com.randomrun.ui.screen.endgame.VictoryScreen;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class VictoryHandler {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static final RunSessionManager SESSION_MANAGER = RunSessionManager.getInstance();

    private static final long DELAYED_SCREEN_MS = 10000;
    private static final int PARTICLE_COUNT = 50;
    private static final double PARTICLE_SPREAD = 4.0;
    private static final double PARTICLE_HEIGHT = 3.0;

    private static final AtomicBoolean victoryInProgress = new AtomicBoolean(false);

    private static volatile long victoryTime = 0;
    private static volatile boolean victoryScheduled = false;
    private static volatile boolean waitingForDelay = false;
    private static volatile Run completedRecord = null;

    private static ModConfig cachedConfig;
    private static long lastConfigCache = 0;
    private static final long CONFIG_CACHE_MS = 1000;

    public static void handleVictory() {
        if (!victoryInProgress.compareAndSet(false, true)) return;

        if (!SESSION_MANAGER.hasActiveRun()) {
            CompactLogger.warn("[Victory] No active run");
            victoryInProgress.set(false);
            return;
        }

        long elapsedTime = SESSION_MANAGER.getElapsedTime();

        CLIENT.execute(() -> showVictoryEffectsImmediate(elapsedTime));

        com.randomrun.core.session.RunSession currentSession = SESSION_MANAGER.getCurrentSession();
        if (currentSession != null && currentSession.isBattle()) {
            BattleManager.getInstance().reportVictory(elapsedTime);
        }

        RunCompletionService.getInstance().completeWithVictory()
            .thenAccept(record -> {
                completedRecord = record;
            })
            .exceptionally(throwable -> {
                CompactLogger.error("[Victory] Save error: {}", throwable.getMessage());
                return null;
            });
    }

    private static void showVictoryEffectsImmediate(long elapsedTime) {
        ModConfig config = getCachedConfig();

        playVictorySound(config);

        ModConfig.VictoryScreenMode mode = config.getVictoryScreenMode();

        if (mode != ModConfig.VictoryScreenMode.SHOW_AFTER_10_SECONDS) {
            showVictoryTitle(elapsedTime);
        }

        if (!config.isHardcoreModeEnabled() && CLIENT.player != null) {
            com.randomrun.infrastructure.util.ClientMessages.send(CLIENT.player, net.minecraft.network.chat.Component.literal("§e⚠ Режим не Хардкор — результат не будет отправлен в таблицу лидеров."), false);
        }

        spawnVictoryParticles();
        handleVictoryScreenImmediate(mode, elapsedTime);
    }

    private static void handleVictoryScreenImmediate(ModConfig.VictoryScreenMode mode, long elapsedTime) {
        switch (mode) {
            case SHOW -> {
                Run tempRecord = createTempRecord(elapsedTime);
                showVictoryScreen(tempRecord);
            }
            case HIDE -> {}
            case SHOW_AFTER_10_SECONDS -> scheduleDelayedScreen();
        }
    }

    private static Run createTempRecord(long elapsedTime) {
        try {
            com.randomrun.core.session.RunSession session = SESSION_MANAGER.getCurrentSession();
            if (session != null) {
                return new Run.Builder()
                    .targetId(session.getTargetId())
                    .timestamp(System.currentTimeMillis())
                    .duration(elapsedTime)
                    .result(com.randomrun.core.history.RunResult.VICTORY)
                    .worldSeed(session.getWorldSeed())
                    .customSeed(session.getCustomSeed())
                    .isBattle(session.isBattle())
                    .battleRoomId(session.getBattleRoomId())
                    .isHardcore(session.isHardcore())
                    .isRandomDrop(session.isRandomDrop())
                    .isUpgrader(session.isUpgrader())
                    .modVersion(session.getModVersion())
                    .attemptNumber(1)
                    .targetType(session.getTargetType())
                    .mobVariant(session.getMobVariant())
                    .build();
            }
        } catch (Exception e) {
            CompactLogger.error("[Victory] Failed to create temp record: {}", e.getMessage());
        }
        return null;
    }

    private static void handleVictoryRecord(Run record, long elapsedTime) {
        if (record == null) {
            CompactLogger.error("[Victory] Completion failed - record is null");
            return;
        }

        completedRecord = record;

        CLIENT.execute(() -> showVictoryEffects(record, elapsedTime));
    }

    private static void showVictoryEffects(Run record, long elapsedTime) {
        ModConfig config = getCachedConfig();

        playVictorySound(config);

        ModConfig.VictoryScreenMode mode = config.getVictoryScreenMode();

        if (mode != ModConfig.VictoryScreenMode.SHOW_AFTER_10_SECONDS) {
            showVictoryTitle(elapsedTime);
            sendVictoryMessage(record, elapsedTime);
        }

        spawnVictoryParticles();
        handleVictoryScreen(mode, record);
    }

    private static void playVictorySound(ModConfig config) {
        try {
            if (CLIENT.player != null && config.isSoundEffectsEnabled()) {
                float volume = config.getSoundVolume() / 100f;
                CLIENT.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, volume, 1.0f);
            }
        } catch (Exception e) {
            CompactLogger.warn("[Victory] Sound error: {}", e.getMessage());
        }
    }

    private static void showVictoryTitle(long elapsedTime) {
        CLIENT.gui.hud.setTitle(Component.literal("\u00A7a\u00A7lVICTORY!"));
        CLIENT.gui.hud.setSubtitle(Component.literal("\u00A7e" + formatTime(elapsedTime)));
    }

    private static void sendVictoryMessage(Run record, long elapsedTime) {
        if (CLIENT.player != null) {
            String itemName = getItemName(record);
            com.randomrun.infrastructure.util.ClientMessages.send(CLIENT.player, com.randomrun.infrastructure.util.LanguageManager.text(
                    "randomrun.victory.message", itemName, formatTime(elapsedTime)
                ), false);
        }
    }

    private static void handleVictoryScreen(ModConfig.VictoryScreenMode mode, Run record) {
        switch (mode) {
            case SHOW -> showVictoryScreen(record);
            case HIDE -> {}
            case SHOW_AFTER_10_SECONDS -> scheduleDelayedScreen();
        }
    }

    private static void scheduleDelayedScreen() {
        victoryScheduled = true;
        waitingForDelay = true;
        victoryTime = System.currentTimeMillis() + DELAYED_SCREEN_MS;
    }

    private static void showVictoryScreen(Run record) {
        CLIENT.execute(() -> CLIENT.gui.setScreen(new VictoryScreen(record)));
    }

    public static void tick() {
        if (victoryScheduled && waitingForDelay) {
            if (System.currentTimeMillis() >= victoryTime) {
                if (completedRecord != null) {
                    showVictoryScreen(completedRecord);
                }
                waitingForDelay = false;
                victoryScheduled = false;
            }
        }
    }

    private static void spawnVictoryParticles() {
        if (CLIENT.player == null || CLIENT.level == null) return;

        try {
            double x = CLIENT.player.getX();
            double y = CLIENT.player.getY() + 1;
            double z = CLIENT.player.getZ();

            for (int i = 0; i < PARTICLE_COUNT; i++) {
                double offsetX = (Math.random() - 0.5) * PARTICLE_SPREAD;
                double offsetY = Math.random() * PARTICLE_HEIGHT;
                double offsetZ = (Math.random() - 0.5) * PARTICLE_SPREAD;

                CLIENT.level.addParticle(
                    ParticleTypes.FIREWORK,
                    x + offsetX, y + offsetY, z + offsetZ,
                    0, 0.1, 0
                );
            }
        } catch (Exception e) {
            CompactLogger.warn("[Victory] Particle error: {}", e.getMessage());
        }
    }

    public static void reset() {
        victoryInProgress.set(false);
        victoryScheduled = false;
        waitingForDelay = false;
        victoryTime = 0;
        completedRecord = null;
        cachedConfig = null;
        lastConfigCache = 0;
    }

    private static String getItemName(Run record) {
        try {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(record.getTargetId());
            if (id != null) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
                if (item != net.minecraft.world.item.Items.AIR) {
                    return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("[Victory] Failed to get item name: {}", e.getMessage());
        }
        return "Unknown";
    }

    private static String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long millis = milliseconds % 1000;

        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        } else {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
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

    public static void invalidateConfigCache() {
        cachedConfig = null;
        lastConfigCache = 0;
    }

    public static boolean isVictoryScheduled() {
        return victoryScheduled;
    }

    public static long getTimeUntilScreen() {
        if (!victoryScheduled || !waitingForDelay) return 0;
        return Math.max(0, victoryTime - System.currentTimeMillis());
    }
}

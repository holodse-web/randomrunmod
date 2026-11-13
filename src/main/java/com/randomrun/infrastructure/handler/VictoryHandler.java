package com.randomrun.infrastructure.handler;

import com.randomrun.core.run.Run;
import com.randomrun.core.run.RunCompletionService;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
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

        com.randomrun.core.run.RunSession currentSession = SESSION_MANAGER.getCurrentSession();
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
            com.randomrun.infrastructure.util.CompactLogger.ClientMessages.send(CLIENT.player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory.not_hardcore"), false);
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
            com.randomrun.core.run.RunSession session = SESSION_MANAGER.getCurrentSession();
            if (session != null) {
                return new Run.Builder()
                    .targetId(session.getTargetId())
                    .timestamp(System.currentTimeMillis())
                    .duration(elapsedTime)
                    .result(com.randomrun.core.run.Run.RunResult.VICTORY)
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
                    .build();
            }
        } catch (Exception e) {
            CompactLogger.error("[Victory] Failed to create temp record: {}", e.getMessage());
        }
        return null;
    }

    private static void playVictorySound(ModConfig config) {
        try {
            if (CLIENT.player != null && config.isSoundEffectsEnabled()) {
                // Quiet on purpose: the victory screen plays the same jingle right after.
                float volume = config.getSoundVolume() / 100f * 0.35f;
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

    public static boolean isItemVictory() {
        return victoryInProgress.get();
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

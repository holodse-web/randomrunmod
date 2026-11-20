package com.randomrun.features.speedrun;

import com.randomrun.core.run.RunSession;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.handler.StartRunHandler;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.ui.overlay.LoadingCurtain;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class AutoStart {
    private static final long SOLO_COUNTDOWN_MS = 5000;
    private static final long BATTLE_SETTLE_MS = 2000;
    private static final long LOAD_TIMEOUT_MS = 20000;
    private static final long RESEND_MS = 8000;

    private static RunSession tracked;
    private static long trackedSince;
    private static long readyAt;
    private static long countdownEnd;
    private static long lastTick;
    private static int lastShown;
    private static long readySentAt;

    private AutoStart() {}

    public static void tick(Minecraft client) {
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        if (session == null || session.getStatus() != RunSession.RunStatus.FROZEN || client.player == null || client.level == null) {
            tracked = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (session != tracked) {
            tracked = session;
            trackedSince = now;
            readyAt = 0;
            countdownEnd = 0;
            lastShown = -1;
            readySentAt = 0;
        }
        long delta = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
        lastTick = now;
        if (readyAt == 0) {
            if (!worldReady(client) && (now - trackedSince < LOAD_TIMEOUT_MS || LoadingCurtain.isCovering())) return;
            readyAt = now;
        }
        BattleManager battle = BattleManager.getInstance();
        if (battle.isInBattle()) {
            if (now - readyAt < BATTLE_SETTLE_MS) return;
            var room = battle.getCurrentRoom();
            boolean waiting = room != null && (room.getStatus() == com.randomrun.features.battle.manager.BattleRoom.RoomStatus.LOADING
                || room.getStatus() == com.randomrun.features.battle.manager.BattleRoom.RoomStatus.FROZEN);
            boolean confirmed = room != null && room.isPlayerReady(com.randomrun.network.BackendSession.playerName());
            if (readySentAt == 0 || waiting && !confirmed && now - readySentAt > RESEND_MS) {
                readySentAt = now;
                StartRunHandler.tryStartRun(client.player);
            }
            return;
        }
        if (countdownEnd == 0) countdownEnd = now + SOLO_COUNTDOWN_MS;
        if (client.isPaused() || client.gui.screen() != null) {
            countdownEnd += delta;
            return;
        }
        int left = (int) Math.ceil((countdownEnd - now) / 1000.0);
        if (left > 0) {
            if (left != lastShown) {
                lastShown = left;
                CompactLogger.ClientMessages.send(client.player, Component.literal("§e§l" + left + "..."), true);
                client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.8f + (5 - left) * 0.15f);
            }
            return;
        }
        if (!StartRunHandler.tryStartRun(client.player)) {
            CompactLogger.warn("[AutoStart] Start failed, retrying");
            countdownEnd = now + 1000;
        }
    }

    private static boolean worldReady(Minecraft client) {
        if (LoadingCurtain.isCovering() || client.player.tickCount < 10) return false;
        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) return false;
        if (!client.level.hasChunkAt(client.player.blockPosition())) return false;
        return client.levelRenderer.hasRenderedAllSections();
    }

    public static String hudLabel() {
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        if (session == null || session != tracked || readyAt == 0) return "...";
        if (BattleManager.getInstance().isInBattle() || countdownEnd == 0) return "...";
        return String.valueOf(Math.max(1, (int) Math.ceil((countdownEnd - System.currentTimeMillis()) / 1000.0)));
    }
}

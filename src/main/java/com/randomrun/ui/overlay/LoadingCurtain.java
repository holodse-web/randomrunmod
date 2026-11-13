package com.randomrun.ui.overlay;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.speedrun.WorldCreator;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.WebRtcTransport.ConnectionStatus;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Logo;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

public final class LoadingCurtain {
    public enum Mode { WORLD, HOST, GUEST }
    private enum Phase { HIDDEN, CLOSING, CLOSED, OPENING }

    private static final long CLOSE_MS = 520;
    private static final long OPEN_MS = 650;
    private static final long MAX_CLOSED_MS = 180_000;
    private static final int READY_TICKS = 6;

    private static Phase phase = Phase.HIDDEN;
    private static Mode mode = Mode.WORLD;
    private static long phaseStart;
    private static long closedAt;
    private static Runnable pending;
    private static float worldProgress = -1f;
    private static float shownProgress;
    private static long lastFrame;
    private static int readyTicks;
    private static boolean registered;

    private LoadingCurtain() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(LoadingCurtain::tick);
    }

    public static boolean isActive() {
        return phase != Phase.HIDDEN;
    }

    public static boolean isCovering() {
        return phase == Phase.CLOSING || phase == Phase.CLOSED;
    }

    public static void close(Mode newMode) {
        close(newMode, null);
    }

    public static void close(Mode newMode, Runnable then) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(() -> close(newMode, then));
            return;
        }
        if (phase == Phase.HIDDEN || phase == Phase.OPENING || newMode != Mode.WORLD) mode = newMode;
        if (phase == Phase.CLOSED) {
            if (then != null) then.run();
            return;
        }
        if (phase == Phase.CLOSING) {
            pending = chain(pending, then);
            return;
        }
        long now = System.currentTimeMillis();
        float alreadyCovered = phase == Phase.OPENING ? 1f - Math.min(1f, (now - phaseStart) / (float) OPEN_MS) : 0f;
        phase = Phase.CLOSING;
        phaseStart = now - (long) (alreadyCovered * CLOSE_MS);
        closedAt = now;
        pending = then;
        worldProgress = -1f;
        shownProgress = 0f;
        readyTicks = 0;
        LoadingTips.refreshTip();
        Ui.playSound(SoundEvents.PISTON_EXTEND, 0.55f, 0.45f);
    }

    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(LoadingCurtain::open);
            return;
        }
        if (!isCovering()) return;
        Runnable action = pending;
        pending = null;
        phase = Phase.OPENING;
        phaseStart = System.currentTimeMillis();
        ConnectionStatus.set(ConnectionStatus.Stage.IDLE);
        Ui.playSound(SoundEvents.PISTON_CONTRACT, 0.65f, 0.45f);
        Ui.playSound(SoundEvents.IRON_DOOR_OPEN, 0.8f, 0.35f);
        if (action != null) action.run();
    }

    public static void abort() {
        open();
    }

    public static void reportWorldProgress(float progress) {
        if (isCovering()) worldProgress = Math.max(worldProgress, Math.clamp(progress, 0f, 1f));
    }

    private static Runnable chain(Runnable first, Runnable second) {
        if (first == null) return second;
        if (second == null) return first;
        return () -> { first.run(); second.run(); };
    }

    private static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (phase == Phase.CLOSING && now - phaseStart >= CLOSE_MS) {
            phase = Phase.CLOSED;
            Ui.playSound(SoundEvents.IRON_DOOR_CLOSE, 0.7f, 0.55f);
            Runnable action = pending;
            pending = null;
            if (action != null) action.run();
        }
        if (phase == Phase.OPENING && now - phaseStart >= OPEN_MS) {
            phase = Phase.HIDDEN;
            return;
        }
        if (phase != Phase.CLOSED) return;

        Screen screen = client.gui.screen();
        boolean inWorld = client.level != null && client.player != null
            && (screen == null || screen instanceof net.minecraft.client.gui.screens.PauseScreen || screen.isInGameUi());
        readyTicks = inWorld ? readyTicks + 1 : 0;
        if (readyTicks >= READY_TICKS) {
            open();
            return;
        }
        boolean timedOut = now - closedAt > MAX_CLOSED_MS;
        boolean failed = screen instanceof DisconnectedScreen;
        BattleManager battle = BattleManager.getInstance();
        boolean cancelled = client.level == null && switch (mode) {
            case GUEST -> battle.getCurrentRoom() == null;
            case HOST -> battle.getCurrentRoom() == null || !battle.isLoadingTriggered() && !WorldCreator.isSpeedrunLoading();
            case WORLD -> now - closedAt > 2500 && !WorldCreator.isSpeedrunLoading() && !(screen instanceof LevelLoadingScreen);
        };
        if (timedOut || failed || cancelled) open();
    }

    private static float targetProgress() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null && client.gui.screen() == null) return 1f;
        long sinceClose = System.currentTimeMillis() - closedAt;
        float creep = 1f - (float) Math.exp(-sinceClose / 9000.0);
        if (mode == Mode.GUEST && !(client.gui.screen() instanceof LevelLoadingScreen)) {
            return switch (ConnectionStatus.get()) {
                case IDLE, WAITING_HOST -> 0.04f + 0.22f * creep;
                case PREPARING -> 0.30f;
                case SIGNALING -> 0.40f;
                case TUNNEL -> 0.52f;
                case JOINING -> 0.62f;
            };
        }
        float base = mode == Mode.GUEST ? 0.62f : 0.14f;
        if (worldProgress < 0) return mode == Mode.GUEST ? base : 0.03f + 0.11f * creep;
        return base + (0.97f - base) * worldProgress;
    }

    private static Item roomTarget() {
        var room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || room.getTargetItem() == null || room.getTargetItem().isEmpty()) return null;
        try {
            var id = net.minecraft.resources.Identifier.tryParse(com.randomrun.infrastructure.util.IdCompressor.decompress(room.getTargetItem()));
            return id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Component statusText(float progress) {
        Minecraft client = Minecraft.getInstance();
        if (progress >= 0.999f) return LanguageManager.text("randomrun.curtain.ready");
        if (mode == Mode.GUEST && !(client.gui.screen() instanceof LevelLoadingScreen)) {
            if (client.gui.screen() instanceof ConnectScreen) return LanguageManager.text(ConnectionStatus.Stage.JOINING.key());
            ConnectionStatus.Stage stage = ConnectionStatus.get();
            return LanguageManager.text((stage == ConnectionStatus.Stage.IDLE ? ConnectionStatus.Stage.WAITING_HOST : stage).key());
        }
        if (worldProgress < 0) return LanguageManager.text(mode == Mode.HOST ? "randomrun.curtain.world.host" : "randomrun.curtain.world.create");
        String key = worldProgress < 0.25f ? "terrain" : worldProgress < 0.55f ? "structures" : worldProgress < 0.85f ? "chunks" : "spawn";
        return LanguageManager.text("randomrun.curtain.world." + key);
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        if (phase == Phase.HIDDEN) return;
        long now = System.currentTimeMillis();
        float cover = switch (phase) {
            case CLOSING -> Ui.easeOut(Math.min(1f, (now - phaseStart) / (float) CLOSE_MS));
            case CLOSED -> 1f;
            case OPENING -> 1f - Ui.easeInOut(Math.min(1f, (now - phaseStart) / (float) OPEN_MS));
            default -> 0f;
        };
        if (cover <= 0f) return;

        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float target = targetProgress();
        shownProgress = target < shownProgress ? shownProgress : shownProgress + (target - shownProgress) * Math.min(1f, dt * 4f);

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        float canvas = com.randomrun.ui.util.UiScale.factor();
        int width = com.randomrun.ui.util.UiScale.width();
        int height = com.randomrun.ui.util.UiScale.height();
        graphics.pose().pushMatrix();
        graphics.pose().scale(canvas, canvas);
        int edge = Math.round(height * cover);
        int offset = edge - height;
        Theme theme = Theme.current();

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, offset);

        // Same backdrop as the mod's menus: theme gradient with drifting dust.
        int middle = height / 2;
        graphics.fillGradient(0, 0, width, middle, 0xFF000000 | theme.bgTop, 0xFF000000 | theme.bgMid);
        graphics.fillGradient(0, middle, width, height, 0xFF000000 | theme.bgMid, 0xFF000000 | theme.bgBottom);
        graphics.fillGradient(0, 0, width, Math.min(60, height), 0xFF000000, 0x00000000);
        com.randomrun.ui.widget.GlobalParticleSystem particles = com.randomrun.ui.widget.GlobalParticleSystem.getInstance();
        particles.updateScreenSize(width, height);
        if (!(client.gui.screen() instanceof com.randomrun.ui.screen.main.AbstractRandomRunScreen)) particles.update(dt);
        particles.extractRenderState(graphics);
        if (cover < 0.999f) graphics.fill(0, height - 2, width, height, 0xFF000000 | theme.accent);

        float contentAlpha = Math.clamp((cover - 0.35f) / 0.5f, 0f, 1f);
        if (contentAlpha > 0f) extractContent(graphics, font, width, height, contentAlpha, theme);

        graphics.pose().popMatrix();
        if (edge < height) graphics.fillGradient(0, edge, width, Math.min(height, edge + 14), 0x99000000, 0x00000000);
        graphics.pose().popMatrix();
    }

    private static void extractContent(GuiGraphicsExtractor graphics, Font font, int width, int height, float alpha, Theme theme) {
        long now = System.currentTimeMillis();
        int centerX = width / 2;
        int logoWidth = Math.min(240, width / 2);
        int logoHeight = logoWidth / 3;
        int logoY = height / 2 - logoHeight / 2 - 24;
        Logo.draw(graphics, centerX - logoWidth / 2, logoY, logoWidth, logoHeight, alpha, shownProgress);

        Component modeLabel = LanguageManager.text("randomrun.curtain.mode." + mode.name().toLowerCase());
        GuiText.centeredText(graphics, font, modeLabel, centerX, logoY + logoHeight + 6, Ui.argb(0x9A9AA2, alpha));

        Item target = mode == Mode.GUEST ? roomTarget() : WorldCreator.getPendingTargetItem();
        if (target != null) {
            Component name = LanguageManager.text("randomrun.curtain.target", Component.translatable(target.getDescriptionId()).getString());
            GuiText.centeredText(graphics, font, name, centerX, logoY + logoHeight + 20, Ui.argb(0xFFFFFF, alpha));
        }

        Component status = Component.literal(statusText(shownProgress).getString() + "  " + (int) (shownProgress * 100) + "%");
        GuiText.centeredText(graphics, font, status, centerX, logoY + logoHeight + 40, Ui.argb(0xE4E4E7, alpha));
        String tip = LoadingTips.getCurrentTip();
        if (!tip.isEmpty()) GuiText.centeredText(graphics, font, Ui.ellipsize(font, tip, width - 40), centerX, height - 34, Ui.argb(0x8A8A93, alpha));

        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof LevelLoadingScreen || mode == Mode.GUEST) {
            Component hint = LanguageManager.text(mode == Mode.GUEST ? "randomrun.curtain.hint.leave" : "randomrun.curtain.hint.cancel");
            GuiText.text(graphics, font, hint, 8, height - 14, Ui.argb(0x888888, alpha), false);
        }
    }

    public static final class LoadingTips {
        private static final int TIP_COUNT = 24;
        private static final long TIP_REFRESH_INTERVAL = 8000;
        private static String currentTip = "";
        private static long lastChangeTime;
        private static int lastIndex = -1;

        private LoadingTips() {}

        public static void refreshTip() {
            int index;
            do index = 1 + ThreadLocalRandom.current().nextInt(TIP_COUNT); while (index == lastIndex && TIP_COUNT > 1);
            lastIndex = index;
            String key = "randomrun.loading.tip." + index;
            String text = LanguageManager.translate(key);
            currentTip = text.equals(key) ? "" : LanguageManager.translate("randomrun.loading.tip.prefix") + " §f" + text;
            lastChangeTime = System.currentTimeMillis();
        }

        public static String getCurrentTip() {
            if (currentTip.isEmpty() || System.currentTimeMillis() - lastChangeTime > TIP_REFRESH_INTERVAL) refreshTip();
            return currentTip;
        }
    }
}

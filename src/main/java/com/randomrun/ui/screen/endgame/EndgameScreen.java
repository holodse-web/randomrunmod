package com.randomrun.ui.screen.endgame;



import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.util.Previews.ItemPreviewRenderer;
import com.randomrun.ui.util.Previews.PreviewItems;
import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunHistory;
import com.randomrun.core.history.RunResult;
import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.features.challenges.classic.world.WorldCreationRequest;
import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.features.challenges.classic.world.WorldMode;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.theme.ButtonPalette;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.AbstractStyledButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public abstract class EndgameScreen extends AbstractRandomRunScreen {
    protected enum Kind {
        VICTORY("randomrun.screen.victory.title_label", 0x4ADE80, 0x0E3B17, ButtonPalette.GREEN, GlobalParticleSystem.ColorMode.GREEN),
        DEFEAT("randomrun.screen.defeat.title_label", 0xF87171, 0x4A0C0C, ButtonPalette.RED, GlobalParticleSystem.ColorMode.RED),
        DRAW("randomrun.screen.draw.title_label", 0xFACC15, 0x4A3A06, ButtonPalette.YELLOW, GlobalParticleSystem.ColorMode.YELLOW);

        final String titleKey;
        final int color;
        final int tint;
        final ButtonPalette palette;
        final GlobalParticleSystem.ColorMode particles;

        Kind(String titleKey, int color, int tint, ButtonPalette palette, GlobalParticleSystem.ColorMode particles) {
            this.titleKey = titleKey;
            this.color = color;
            this.tint = tint;
            this.palette = palette;
            this.particles = particles;
        }
    }

    protected record Stat(Component label, Component value, int color) {}

    private static final int CARD_WIDTH = 200;

    protected final Kind kind;
    protected final Item item;
    protected final long time;
    protected final boolean allowRetry;
    protected final boolean upgrader;
    protected final boolean randomDrop;
    private final ItemStack stack;
    private long openTime;
    private float rotationY;
    private long lastFrame;
    protected final List<Stat> stats = new ArrayList<>();
    protected volatile Component badge;
    protected volatile int badgeColor = 0xFFFFFF;
    protected volatile List<Component> notes = List.of();

    protected EndgameScreen(Kind kind, Item item, long time, boolean allowRetry, boolean upgrader, boolean randomDrop) {
        super(LanguageManager.text(kind.titleKey));
        this.kind = kind;
        this.item = item;
        this.time = time;
        this.allowRetry = allowRetry;
        this.upgrader = upgrader;
        this.randomDrop = randomDrop;
        this.stack = item != null ? PreviewItems.stack(item) : ItemStack.EMPTY;
    }

    protected abstract Component subtitle();

    protected abstract void collectStats();

    @Override
    protected void init() {
        super.init();
        if (openTime == 0) {
            openTime = System.currentTimeMillis();
            GlobalParticleSystem.getInstance().clearParticles();
            stats.clear();
            collectStats();
            onOpened();
        }
        GlobalParticleSystem.getInstance().setColorMode(kind.particles);
        int centerX = width / 2;
        int y = height - 34;
        if (allowRetry) {
            addRenderableWidget(button(centerX - 154, y, 150, LanguageManager.text("randomrun.button.retry_speedrun"), this::handleRetry));
            addRenderableWidget(button(centerX + 4, y, 150, LanguageManager.text("randomrun.button.to_menu"), this::handleExit));
        } else {
            addRenderableWidget(button(centerX - 100, y, 200, LanguageManager.text("randomrun.button.to_menu"), this::handleExit));
        }
    }

    protected void onOpened() {}

    private AbstractStyledButton button(int x, int y, int w, Component text, Runnable action) {
        return new ButtonDefault(x, y, w, 20, text, button -> action.run(), 0, 0.35f).setPalette(kind.palette);
    }

    protected int trustColor(int score) {
        if (score >= 150) return 0x4ADE80;
        if (score >= 100) return 0xFACC15;
        if (score >= PlayerProfile.LEADERBOARD_THRESHOLD) return 0xFB923C;
        return 0xF87171;
    }

    protected List<Run> history() {
        if (item == null) return List.of();
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return RunHistory.getInstance().getRecordsForTarget(id).stream().filter(run -> run.isUpgrader() == upgrader).toList();
    }

    protected long bestVictory(List<Run> runs) {
        return runs.stream().filter(run -> run.getResult() == RunResult.VICTORY && run.getDuration() > 0)
            .mapToLong(Run::getDuration).min().orElse(-1);
    }

    protected void addHistoryStats(List<Run> runs, int extraAttempt, int extraWin) {
        int attempts = runs.size() + extraAttempt;
        int wins = (int) runs.stream().filter(run -> run.getResult() == RunResult.VICTORY).count() + extraWin;
        stats.add(new Stat(LanguageManager.text("randomrun.endgame.attempts"), Component.literal(String.valueOf(attempts)), 0xFFFFFF));
        stats.add(new Stat(LanguageManager.text("randomrun.endgame.wins"), Component.literal(String.valueOf(wins)), 0x4ADE80));
        int rate = attempts > 0 ? Math.round(wins * 100f / attempts) : 0;
        stats.add(new Stat(LanguageManager.text("randomrun.endgame.winrate"), Component.literal(rate + "%"), rate >= 50 ? 0x4ADE80 : rate >= 25 ? 0xFACC15 : 0xF87171));
    }

    protected void addTrustStat() {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) return;
        PlayerProfile profile = PlayerProfile.get();
        if (profile == null) return;
        int score = profile.getTrustScore();
        stats.add(new Stat(Component.literal("Trust Score"), Component.literal(score + "/200"), trustColor(score)));
    }

    private void handleRetry() {
        cleanupAndDisconnect();
        WorldCreator.getInstance().createWorld(new WorldCreationRequest.Builder()
            .targetItem(item)
            .upgrader(upgrader)
            .randomDrop(randomDrop)
            .seed(WorldCreator.getLastCreatedSeed())
            .mode(WorldMode.STANDARD)
            .build());
    }

    private void handleExit() {
        cleanupAndDisconnect();
        minecraft.gui.setScreen(new MainModScreen(new TitleScreen()));
    }

    private void cleanupAndDisconnect() {
        GlobalParticleSystem.getInstance().setColorMode(GlobalParticleSystem.ColorMode.DEFAULT);
        GlobalParticleSystem.getInstance().clearParticles();
        if (minecraft.level != null) minecraft.level.disconnect(Component.translatable("menu.returnToMenu"));
        minecraft.disconnectWithProgressScreen();
        RandomRunMod.getInstance().getRunDataManager().cancelRun();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int mid = height / 2;
        context.fillGradient(0, 0, width, mid, 0xFF050505, 0xFF000000 | kind.tint);
        context.fillGradient(0, mid, width, height, 0xFF000000 | kind.tint, 0xFF050505);
        GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
        particles.updateScreenSize(width, height);
        particles.updateMousePosition(mouseX, mouseY);
        particles.update(delta);
        particles.extractRenderState(context);
    }

    private float appear(long delay) {
        return Ui.easeOut((System.currentTimeMillis() - openTime - delay) / 450f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        rotationY += seconds * 40f;

        float titleIn = appear(0);
        float pulse = 1f + 0.03f * (float) Math.sin((now - openTime) / 320.0);
        Ui.centeredScaled(context, font, LanguageManager.text(kind.titleKey), width / 2f, 16 + (1 - titleIn) * -20, 3f * pulse, Ui.argb(kind.color, titleIn));
        Component subtitle = subtitle();
        if (subtitle != null) GuiText.centeredText(context, font, subtitle, width / 2, 48, Ui.argb(0xD4D4D8, appear(150)));

        boolean wide = width >= 480;
        int itemX = wide ? width / 2 - CARD_WIDTH / 2 - 70 : width / 2;
        int itemY = wide ? height / 2 - 8 : height / 2 - 40;
        float itemIn = appear(200);
        if (!stack.isEmpty()) {
            float bob = (float) Math.sin((now - openTime) / 500.0) * 4f;
            ItemPreviewRenderer.extract(context, stack, itemX, itemY + bob + (1 - itemIn) * 30, 92f * itemIn, 0, rotationY, ItemDisplayContext.GUI);
            String name = Component.translatable(item.getDescriptionId()).getString();
            GuiText.centeredText(context, font, Component.literal(name), itemX, itemY + 54, Ui.argb(kind.color, itemIn));
        }

        int cardLeft = wide ? width / 2 - CARD_WIDTH / 2 + 50 : width / 2 - CARD_WIDTH / 2;
        int cardTop = wide ? height / 2 - 72 : height / 2 + 30;
        renderCard(context, cardLeft, cardTop, appear(300));

        GuiText.centeredText(context, font, LanguageManager.text("randomrun.victory.warning"), width / 2, height - 46, Ui.argb(0x71717A, appear(500)));
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderCard(GuiGraphicsExtractor context, int left, int top, float alpha) {
        if (alpha <= 0) return;
        int right = left + CARD_WIDTH;
        List<FormattedCharSequence> noteLines = new ArrayList<>();
        for (Component note : notes) noteLines.addAll(font.split(note, CARD_WIDTH - 16));
        int bottom = top + 44 + stats.size() * 12 + (badge != null ? 14 : 0) + (noteLines.isEmpty() ? 0 : noteLines.size() * 10 + 6) + 8;
        context.fill(left, top, right, bottom, Ui.argb(0x000000, 0.62f * alpha));
        context.fill(left, top, right, top + 2, Ui.argb(kind.color, alpha));
        Ui.border(context, left, top, right, bottom, 1, Ui.argb(0xFFFFFF, 0.08f * alpha));

        GuiText.text(context, font, LanguageManager.text("randomrun.endgame.time_label"), left + 8, top + 8, Ui.argb(0xA1A1AA, alpha), false);
        context.pose().pushMatrix();
        context.pose().translate(left + 8, top + 19);
        context.pose().scale(2f, 2f);
        GuiText.text(context, font, RunDataManager.formatTime(time), 0, 0, Ui.argb(0xFFFFFF, alpha), true);
        context.pose().popMatrix();

        int y = top + 42;
        if (badge != null) {
            GuiText.text(context, font, badge, left + 8, y, Ui.argb(badgeColor, alpha), true);
            y += 14;
        }
        Ui.divider(context, left + 6, right - 6, y - 3, kind.color, alpha);
        for (Stat stat : stats) {
            GuiText.text(context, font, stat.label(), left + 8, y + 2, Ui.argb(0xA1A1AA, alpha), false);
            int valueWidth = font.width(stat.value());
            GuiText.text(context, font, stat.value(), right - 8 - valueWidth, y + 2, Ui.argb(stat.color(), alpha), false);
            y += 12;
        }
        if (!noteLines.isEmpty()) {
            y += 6;
            for (FormattedCharSequence line : noteLines) {
                GuiText.text(context, font, line, left + 8, y, Ui.argb(0xD4D4D8, alpha), false);
                y += 10;
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        GlobalParticleSystem.getInstance().setColorMode(GlobalParticleSystem.ColorMode.DEFAULT);
        super.removed();
    }
}

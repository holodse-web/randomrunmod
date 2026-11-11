package com.randomrun.ui.widget.overlay;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.AbstractStyledButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OverlayWidgets {
    private OverlayWidgets() {
    }

    final static class CornerAnimation {
        private CornerAnimation() {}

        static float alpha(long creationTime, float delay) {
            long elapsed = System.currentTimeMillis() - creationTime - (long) (delay * 1000);
            if (elapsed <= 0) return 0f;
            float progress = Math.min(1f, elapsed / 300f);
            return 1f - (float) Math.pow(1 - progress, 3);
        }
    }

    public static class LanguageDropdownWidget extends AbstractStyledButton {
        private static final int SIZE = 20;
        private final long creationTime = System.currentTimeMillis();
        private final float delay;

        public LanguageDropdownWidget(int x, int y, Screen parentScreen, float delay) {
            super(x, y, SIZE, SIZE, Component.literal(LanguageManager.getCurrentLanguageCode()), button -> LanguageManager.cycleLanguage());
            this.delay = delay;
            setTooltip(Tooltip.create(LanguageManager.text("randomrun.corner.language", LanguageManager.getCurrentLanguageEnum().getDisplayName())));
        }

        public LanguageDropdownWidget(int x, int y, Screen parentScreen) {
            this(x, y, parentScreen, 0f);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            float alpha = CornerAnimation.alpha(creationTime, delay);
            if (alpha <= 0) return;
            updateAnimations(delta);
            handleHoverSound(alpha >= 1f);
            extractStyled(context, getX() - (int) ((1f - alpha) * SLIDE_DISTANCE), getY(), alpha);
        }
    }

    public static class ModInfoWidget {
        private static final String VERSION = RandomRunMod.MOD_VERSION;
        private static final int MARGIN_RIGHT = 10;
        private static final int MARGIN_BOTTOM = 20;
        private static final int COLOR_TEXT = 0xAAAAAA;

        private final int screenWidth;
        private final int screenHeight;
        private final Font textRenderer;

        public ModInfoWidget(int screenWidth, int screenHeight, Font textRenderer) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.textRenderer = textRenderer;
        }

        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int versionWidth = textRenderer.width(VERSION);
            int x = screenWidth - versionWidth - MARGIN_RIGHT;
            int y = screenHeight - MARGIN_BOTTOM;

            com.randomrun.ui.util.GuiText.text(context, textRenderer, VERSION, x, y, COLOR_TEXT);
        }

        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {

            return false;
        }
    }

    public static class ThemeSwitchWidget extends AbstractStyledButton {
        private static final int SIZE = 20;
        private final long creationTime = System.currentTimeMillis();
        private final float delay;

        public ThemeSwitchWidget(int x, int y, float delay) {
            super(x, y, SIZE, SIZE, Component.empty(), button -> {});
            this.delay = delay;
            updateTooltip();
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            Theme next = Theme.current().next();
            RandomRunMod.getInstance().getConfig().setTheme(next.id);
            RandomRunMod.getInstance().saveConfig();
            GlobalParticleSystem.getInstance().addFastExplosion(getX() + SIZE / 2, getY() + SIZE / 2, 16, next.accent);
            updateTooltip();
        }

        private void updateTooltip() {
            setTooltip(Tooltip.create(LanguageManager.text("randomrun.corner.theme", LanguageManager.translate(Theme.current().nameKey()))));
        }

        @Override
        public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            float alpha = CornerAnimation.alpha(creationTime, delay);
            if (alpha <= 0) return;
            updateAnimations(delta);
            handleHoverSound(alpha >= 1f);
            int x = getX() - (int) ((1f - alpha) * SLIDE_DISTANCE);
            extractStyled(context, x, getY(), alpha);
            Theme theme = Theme.current();
            int a = (int) (alpha * 255) << 24;
            context.fill(x + 5, getY() + 5, x + 10, getY() + 15, a | theme.accent);
            context.fill(x + 10, getY() + 5, x + 15, getY() + 15, a | theme.border);
            context.fill(x + 5, getY() + 13, x + 15, getY() + 15, a | theme.bgMid);
        }
    }
}

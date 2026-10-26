package com.randomrun.ui.widget.styled;

import com.randomrun.ui.util.GuiQuadBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ButtonRainbow extends AbstractStyledButton {
    private static final int COLOR_BASE = 0x302b63;
    private static final int COLOR_HOVER = 0x6930c3;
    private static final int COLOR_OVERLAY = 0xd042ff;
    private static final float RAINBOW_SPEED = 0.01f;

    private final float delay;
    private final long creationTime;
    private float animationProgress = 0f;
    private float rainbowAnimation = 0f;

    private int baseColor = COLOR_BASE;
    private int hoverColor = COLOR_HOVER;
    private AnimationDirection animationDirection = AnimationDirection.VERTICAL_UP;

    public enum AnimationDirection {
        VERTICAL_UP,
        HORIZONTAL_LEFT
    }

    public ButtonRainbow(int x, int y, int width, int height, Component message, OnPress onPress, int index, float delay) {
        super(x, y, width, height, message, onPress);
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
    }

    public ButtonRainbow setAnimationDirection(AnimationDirection direction) {
        this.animationDirection = direction;
        return this;
    }

    public ButtonRainbow setColors(int baseColor, int hoverColor) {
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        return this;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = calculateAppearanceProgress();
        if (alpha <= 0) return;

        int animatedX = getX();
        int animatedY = getY();

        if (animationDirection == AnimationDirection.VERTICAL_UP) {
            animatedY = getY() + (int) ((1f - alpha) * SLIDE_DISTANCE_VERTICAL);
        } else {
            animatedX = getX() - (int) ((1f - alpha) * SLIDE_DISTANCE);
        }

        updateAnimations(delta);
        rainbowAnimation += delta * RAINBOW_SPEED;
        if (rainbowAnimation > 1.0f) rainbowAnimation -= 1.0f;

        boolean hovered = isHovered();
        if (hovered && !wasHovered) {
            playHoverSound();
        }
        wasHovered = hovered;

        int bgColor = lerpColor(baseColor, hoverColor, hoverProgress);
        extractBackground(context, animatedX, animatedY, bgColor, alpha);
        renderRainbowBorder(context, animatedX, animatedY, animatedX + width, animatedY + height, alpha);
        renderText(context, animatedX, animatedY, alpha);
        renderHoverOverlay(context, animatedX, animatedY, COLOR_OVERLAY, alpha);
    }

    private void renderRainbowBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float alpha) {
        int w = right - left;
        int h = bottom - top;
        int perimeter = 2 * (w + h);
        GuiQuadBatch.Builder border = new GuiQuadBatch.Builder(perimeter);

        for (int i = 0; i < w; i++) {
            float progress = (rainbowAnimation + (float) i / perimeter) % 1.0f;
            int color = getRainbowColor(progress, alpha);
            border.fill(left + i, top, left + i + 1, top + BORDER_THICKNESS, color);
        }

        for (int i = 0; i < h; i++) {
            float progress = (rainbowAnimation + (float) (w + i) / perimeter) % 1.0f;
            int color = getRainbowColor(progress, alpha);
            border.fill(right - BORDER_THICKNESS, top + i, right, top + i + 1, color);
        }

        for (int i = 0; i < w; i++) {
            float progress = (rainbowAnimation + (float) (w + h + i) / perimeter) % 1.0f;
            int color = getRainbowColor(progress, alpha);
            border.fill(right - i - 1, bottom - BORDER_THICKNESS, right - i, bottom, color);
        }

        for (int i = 0; i < h; i++) {
            float progress = (rainbowAnimation + (float) (w * 2 + h + i) / perimeter) % 1.0f;
            int color = getRainbowColor(progress, alpha);
            border.fill(left, bottom - i - 1, left + BORDER_THICKNESS, bottom - i, color);
        }
        border.build().extract(context);
    }

    private int getRainbowColor(float t, float alpha) {
        int r = (int) (Math.sin(t * Math.PI * 2) * 127 + 128);
        int g = (int) (Math.sin((t + 0.33f) * Math.PI * 2) * 127 + 128);
        int b = (int) (Math.sin((t + 0.66f) * Math.PI * 2) * 127 + 128);

        int a = (int)(alpha * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float calculateAppearanceProgress() {
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;

        if (elapsed < delayMs) {
            animationProgress = 0f;
            return 0f;
        }

        float animElapsed = elapsed - delayMs;
        animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        return 1f - (float) Math.pow(1 - animationProgress, 3);
    }
}

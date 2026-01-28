package com.randomrun.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

public class VolumeSlider extends SliderWidget {
    private final Consumer<Integer> onValueChanged;
    private final Text label;
    private final long creationTime;
    private final boolean skipAnimation;
    private float animationProgress = 0f;
    
    private static final float ANIMATION_DURATION = 400f;
    
    public VolumeSlider(int x, int y, int width, int height, Text label, int initialValue, Consumer<Integer> onValueChanged, boolean skipAnimation) {
        super(x, y, width, height, Text.empty(), initialValue / 100.0);
        this.label = label;
        this.onValueChanged = onValueChanged;
        this.skipAnimation = skipAnimation;
        this.creationTime = skipAnimation ? 0 : System.currentTimeMillis();
        updateMessage();
    }
    
    public VolumeSlider(int x, int y, int width, int height, Text label, int initialValue, Consumer<Integer> onValueChanged) {
        this(x, y, width, height, label, initialValue, onValueChanged, false);
    }
    
    @Override
    protected void updateMessage() {
        int percent = (int) (value * 100);
        setMessage(Text.literal(label.getString() + ": " + percent + "%"));
    }
    
    @Override
    protected void applyValue() {
        int percent = (int) (value * 100);
        onValueChanged.accept(percent);
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Update slide-up animation
        if (skipAnimation) {
            animationProgress = 1f;
        } else {
            long elapsed = System.currentTimeMillis() - creationTime;
            animationProgress = Math.min(1f, elapsed / ANIMATION_DURATION);
        }
        
        // Ease out cubic for smooth animation
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        
        // Calculate slide offset (slide up from below)
        int slideOffset = (int) ((1f - easedProgress) * 30);
        float alpha = easedProgress;
        
        if (alpha <= 0) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        int animatedY = getY() + slideOffset;
        
        // Background
        int bgAlpha = (int) (alpha * 0xE0);
        context.fill(getX(), animatedY, getX() + width, animatedY + height, (bgAlpha << 24) | 0x302b63);
        
        // Border
        int borderAlpha = (int) (alpha * 255);
        context.drawBorder(getX(), animatedY, width, height, (borderAlpha << 24) | 0x6930c3);
        
        // Filled portion
        int filledWidth = (int) ((width - 4) * value);
        int fillAlpha = (int) (alpha * 255);
        context.fill(getX() + 2, animatedY + 2, getX() + 2 + filledWidth, animatedY + height - 2, (fillAlpha << 24) | 0x6930c3);
        
        // Slider handle
        int handleX = getX() + 2 + filledWidth - 2;
        handleX = MathHelper.clamp(handleX, getX() + 2, getX() + width - 6);
        int handleAlpha = (int) (alpha * 255);
        context.fill(handleX, animatedY + 2, handleX + 4, animatedY + height - 2, (handleAlpha << 24) | 0xFFFFFF);
        
        // Text
        int textAlpha = (int) (alpha * 255);
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            getMessage(),
            getX() + width / 2,
            animatedY + (height - 8) / 2,
            (textAlpha << 24) | 0xFFFFFF
        );
    }
}

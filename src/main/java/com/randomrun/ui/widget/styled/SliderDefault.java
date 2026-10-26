package com.randomrun.ui.widget.styled;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class SliderDefault extends AbstractSliderButton {
    private static final int COLOR_BASE = 0x302b63;
    private static final int COLOR_ACTIVE = 0x6930c3;
    private static final int COLOR_HANDLE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0x6930c3;
    private static final int COLOR_CURSOR = 0xFFFFFF;
    private static final int COLOR_OVERLAY = 0xd042ff;
    
    private static final int HANDLE_WIDTH = 8;
    
    private final AbstractStyledWidget styledHelper = new AbstractStyledWidget() {};
    private final Consumer<Double> onApply;
    private final float delay;
    private final boolean useSlideAnimation;
    private final long creationTime;
    
    public SliderDefault(int x, int y, int width, int height, Component text, double value, 
                        Consumer<Double> onApply, float delay, boolean useSlideAnimation) {
        super(x, y, width, height, text, value);
        this.onApply = onApply;
        this.delay = delay;
        this.useSlideAnimation = useSlideAnimation;
        this.creationTime = System.currentTimeMillis();
    }
    
    public SliderDefault(int x, int y, int width, int height, Component text, double value, 
                        Consumer<Double> onApply, float delay) {
        this(x, y, width, height, text, value, onApply, delay, false);
    }
    
    @Override
    protected void updateMessage() {
    }
    
    @Override
    protected void applyValue() {
        if (onApply != null) {
            onApply.accept(this.value);
        }
    }
    
    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = styledHelper.calculateAppearanceProgress(creationTime, delay);
        if (alpha <= 0) return;
        
        int offsetX = useSlideAnimation ? (int) ((1f - alpha) * AbstractStyledWidget.SLIDE_DISTANCE) : 0;
        int offsetY = useSlideAnimation ? 0 : (int) ((1f - alpha) * AbstractStyledWidget.SLIDE_DISTANCE_VERTICAL);
        
        int animatedX = getX() - offsetX;
        int animatedY = getY() + offsetY;
        
        boolean hovered = isHovered();
        styledHelper.updateAnimations(delta, hovered);
        
        if (hovered && !styledHelper.wasHovered) {
            styledHelper.playHoverSound();
        }
        styledHelper.wasHovered = hovered;
        
        int bgAlpha = (int) (alpha * AbstractStyledWidget.ALPHA_BACKGROUND);
        int finalBgColor = (bgAlpha << 24) | (COLOR_BASE & 0x00FFFFFF);
        context.fill(animatedX, animatedY, animatedX + width, animatedY + height, finalBgColor);
        
        int handleX = animatedX + (int)(this.value * (double)(this.width - HANDLE_WIDTH));
        int activeWidth = handleX - animatedX + HANDLE_WIDTH / 2;
        
        int activeAlpha = (int) (alpha * 255);
        int finalActiveColor = (activeAlpha << 24) | (COLOR_ACTIVE & 0x00FFFFFF);
        context.fill(animatedX, animatedY, animatedX + activeWidth, animatedY + height, finalActiveColor);
        
        int finalHandleColor = (activeAlpha << 24) | (COLOR_HANDLE & 0x00FFFFFF);
        context.fill(handleX, animatedY, handleX + HANDLE_WIDTH, animatedY + height, finalHandleColor);
        
        styledHelper.renderBorderWithCursorEffect(context, animatedX, animatedY, 
                                                  animatedX + width, animatedY + height, 
                                                  COLOR_BORDER, COLOR_CURSOR, alpha);
        
        Minecraft client = Minecraft.getInstance();
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        
        com.randomrun.ui.util.GuiText.centeredText(context, 
            client.font,
            getMessage(),
            animatedX + width / 2,
            animatedY + (height - 8) / 2,
            textColor
        );
        
        if (styledHelper.hoverProgress > 0.01f) {
            int whiteAlpha = (int) (styledHelper.hoverProgress * alpha * AbstractStyledWidget.ALPHA_OVERLAY);
            int whiteOverlay = (whiteAlpha << 24) | (COLOR_OVERLAY & 0x00FFFFFF);
            context.fill(animatedX, animatedY, animatedX + width, animatedY + height, whiteOverlay);
        }
    }
}

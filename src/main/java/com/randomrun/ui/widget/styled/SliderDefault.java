package com.randomrun.ui.widget.styled;

import java.util.function.Consumer;
import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class SliderDefault extends AbstractSliderButton {
    private static final int COLOR_HANDLE = 0xFFFFFFFF;
    private static final int COLOR_CURSOR = 0xFFFFFF;
    
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
        if (InterfaceStyle.isClassic()) {
            int x = getX(), y = getY();
            Theme theme = Theme.current();
            Ui.bevel(context, x, y, x + width, y + height, 0x30302C, 1f, true);
            int handleX = x + (int) (value * (width - HANDLE_WIDTH));
            context.fill(x + 2, y + height - 4, Math.max(x + 2, handleX + HANDLE_WIDTH / 2), y + height - 2, Ui.argb(theme.accent, active ? .9f : .35f));
            Ui.bevel(context, handleX, y, handleX + HANDLE_WIDTH, y + height, active && (isHovered() || isFocused()) ? 0xA6A69E : 0x808078, 1f, false);
            if (active && (isHovered() || isFocused())) Ui.border(context, x, y, x + width, y + height, 1, 0xFFFFFFA0);
            var font = Minecraft.getInstance().font;
            GuiText.centeredText(context, font, Ui.ellipsize(font, getMessage().getString(), Math.max(0, width - 12)), x + width / 2, y + (height - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A09A);
            return;
        }
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
        
        com.randomrun.ui.util.Theme theme = com.randomrun.ui.util.Theme.current();
        int colorBase = theme.buttonBase, colorActive = theme.buttonHover, colorBorder = theme.buttonHover, colorOverlay = theme.overlay;
        int bgAlpha = (int) (alpha * AbstractStyledWidget.ALPHA_BACKGROUND);
        int finalBgColor = (bgAlpha << 24) | (colorBase & 0x00FFFFFF);
        context.fill(animatedX, animatedY, animatedX + width, animatedY + height, finalBgColor);
        
        int handleX = animatedX + (int)(this.value * (double)(this.width - HANDLE_WIDTH));
        int activeWidth = handleX - animatedX + HANDLE_WIDTH / 2;
        
        int activeAlpha = (int) (alpha * 255);
        int finalActiveColor = (activeAlpha << 24) | (colorActive & 0x00FFFFFF);
        context.fill(animatedX, animatedY, animatedX + activeWidth, animatedY + height, finalActiveColor);
        
        int finalHandleColor = (activeAlpha << 24) | (COLOR_HANDLE & 0x00FFFFFF);
        context.fill(handleX, animatedY, handleX + HANDLE_WIDTH, animatedY + height, finalHandleColor);
        
        styledHelper.renderBorderWithCursorEffect(context, animatedX, animatedY, 
                                                  animatedX + width, animatedY + height, 
                                                  colorBorder, COLOR_CURSOR, alpha);
        
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
            int whiteOverlay = (whiteAlpha << 24) | (colorOverlay & 0x00FFFFFF);
            context.fill(animatedX, animatedY, animatedX + width, animatedY + height, whiteOverlay);
        }
    }
}

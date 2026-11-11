package com.randomrun.ui.widget.styled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class TextFieldStyled extends EditBox {
    private static final int COLOR_PLACEHOLDER = 0xAAAAAA;

    private static final float TEXT_SCALE = 1.2f;
    private static final int TEXT_Y_ADJUSTMENT = 8;

    protected int getTextYAdjustment() {
        return height >= 20 ? TEXT_Y_ADJUSTMENT : Math.round(0.6f * (height - 8)) - 1;
    }

    private final AbstractStyledWidget styledHelper = new AbstractStyledWidget() {};
    private final float delay;
    private final long creationTime;

    private Integer baseColor;
    private Integer hoverColor;
    private Integer borderColor;
    private Component centeredPlaceholder;
    private boolean wasHovered = false;

    public TextFieldStyled(Font textRenderer, int x, int y, int width, int height, Component message, float delay) {
        super(textRenderer, x, y, width, height, message);
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
        this.setBordered(false);
    }

    public TextFieldStyled(Font textRenderer, int x, int y, int width, int height, Component message) {
        this(textRenderer, x, y, width, height, message, 0f);
    }

    public TextFieldStyled setColors(int baseColor, int hoverColor) {
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        return this;
    }

    public TextFieldStyled setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    private boolean masked;

    private boolean maskFormatter;

    public TextFieldStyled masked() {
        return setMasked(true);
    }

    public TextFieldStyled setMasked(boolean value) {
        masked = value;
        if (!maskFormatter) {
            maskFormatter = true;
            addFormatter((text, offset) -> masked ? net.minecraft.util.FormattedCharSequence.forward("\u25CF".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY) : null);
        }
        return this;
    }

    public void setCenteredPlaceholder(Component text) {
        this.centeredPlaceholder = text;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = styledHelper.calculateAppearanceProgress(creationTime, delay);
        if (alpha <= 0) return;

        int slideOffset = (int) ((1f - alpha) * AbstractStyledWidget.SLIDE_DISTANCE_VERTICAL);
        int animatedY = getY() + slideOffset;

        boolean hovered = isHovered() || isFocused();

        if (hovered && !wasHovered) {
            playHoverSound();
        }
        wasHovered = hovered;

        styledHelper.updateAnimations(delta, hovered);

        com.randomrun.ui.theme.Theme theme = com.randomrun.ui.theme.Theme.current();
        int base = baseColor != null ? baseColor : theme.buttonBase;
        int fieldBg = com.randomrun.ui.util.Ui.lerp(0xFF000000 | base, 0xFF000000, 0.55f) & 0xFFFFFF;
        int idle = borderColor != null ? borderColor : base;
        int accent = hoverColor != null ? hoverColor : theme.accent;
        float focus = isFocused() ? 1f : styledHelper.hoverProgress * 0.5f;
        int border = styledHelper.lerpColor(idle, accent, focus);
        int bgAlpha = (int) (alpha * 235);
        context.fill(getX(), animatedY, getX() + width, animatedY + height, (bgAlpha << 24) | fieldBg);
        int a = ((int) (alpha * 255)) << 24 | (border & 0xFFFFFF);
        context.fill(getX(), animatedY, getX() + width, animatedY + 2, a);
        context.fill(getX(), animatedY + height - 2, getX() + width, animatedY + height, a);
        context.fill(getX(), animatedY + 2, getX() + 2, animatedY + height - 2, a);
        context.fill(getX() + width - 2, animatedY + 2, getX() + width, animatedY + height - 2, a);

        int originalY = getY();
        setY(animatedY);

        renderScaledText(context, mouseX, mouseY, delta);

        boolean shouldShowPlaceholder = centeredPlaceholder != null && 
                                       (getValue() == null || getValue().trim().isEmpty()) && 
                                       !isFocused();

        if (shouldShowPlaceholder) {
            renderCenteredPlaceholder(context);
        }

        setY(originalY);
    }

    private void playHoverSound() {
        if (!com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;

        float volume = com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BELL.value(), 
                1.8f,
                volume * 0.25f
            )
        );
    }

    private void renderScaledText(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font renderer = Minecraft.getInstance().font;
        int textWidth = renderer.width(masked ? "\u25CF".repeat(getValue().length()) : getValue());

        float baseTextX = 4.0f;
        float baseTextY = (height - 8) / 2.0f;

        float targetX = (width - textWidth * TEXT_SCALE) / 2.0f;
        float targetY = (height - 8 * TEXT_SCALE) / 2.0f + getTextYAdjustment();

        float tx = targetX - TEXT_SCALE * baseTextX;
        float ty = targetY - TEXT_SCALE * baseTextY;

        float matrixTx = getX() * (1 - TEXT_SCALE) + tx;
        float matrixTy = getY() * (1 - TEXT_SCALE) + ty;

        context.pose().pushMatrix();
        context.pose().translate(matrixTx, matrixTy);
        context.pose().scale(TEXT_SCALE, TEXT_SCALE);

        int adjustedMouseX = (int)((mouseX - matrixTx) / TEXT_SCALE);
        int adjustedMouseY = (int)((mouseY - matrixTy) / TEXT_SCALE);

        super.extractWidgetRenderState(context, adjustedMouseX, adjustedMouseY, delta);

        context.pose().popMatrix();
    }

    private void renderCenteredPlaceholder(GuiGraphicsExtractor context) {
        if (getValue() != null && !getValue().trim().isEmpty()) {
            return;
        }

        if (isFocused()) {
            return;
        }

        Font renderer = Minecraft.getInstance().font;
        int placeholderWidth = renderer.width(centeredPlaceholder);

        float phX = getX() + (width - placeholderWidth) / 2.0f;
        float phY = getY() + (height - 8) / 2.0f;

        context.pose().pushMatrix();
        context.pose().translate(phX, phY);
        com.randomrun.ui.util.GuiText.text(context, renderer, centeredPlaceholder, 0, 0, COLOR_PLACEHOLDER);
        context.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (mouseX >= getX() && mouseX <= getX() + width && 
            mouseY >= getY() && mouseY <= getY() + height) {
            double[] adjusted = adjustMouseCoordinates(mouseX, mouseY);
            boolean result = super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(adjusted[0], adjusted[1], event.buttonInfo()), doubleClick);

            if (result || button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                setFocused(true);
                return true;
            }
            return result;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();

        double[] adjusted = adjustMouseCoordinates(mouseX, mouseY);
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(adjusted[0], adjusted[1], event.buttonInfo()), deltaX / TEXT_SCALE, deltaY / TEXT_SCALE);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();

        double[] adjusted = adjustMouseCoordinates(mouseX, mouseY);
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(adjusted[0], adjusted[1], event.buttonInfo()));
    }

    private double[] adjustMouseCoordinates(double mouseX, double mouseY) {
        Font renderer = Minecraft.getInstance().font;
        int textWidth = renderer.width(masked ? "\u25CF".repeat(getValue().length()) : getValue());

        float baseTextX = 4.0f;
        float baseTextY = (height - 8) / 2.0f;

        float targetX = (width - textWidth * TEXT_SCALE) / 2.0f;
        float targetY = (height - 8 * TEXT_SCALE) / 2.0f + getTextYAdjustment();

        float tx = targetX - TEXT_SCALE * baseTextX;
        float ty = targetY - TEXT_SCALE * baseTextY;

        float matrixTx = getX() * (1 - TEXT_SCALE) + tx;
        float matrixTy = getY() * (1 - TEXT_SCALE) + ty;

        return new double[] {
            (mouseX - matrixTx) / TEXT_SCALE,
            (mouseY - matrixTy) / TEXT_SCALE
        };
    }
}

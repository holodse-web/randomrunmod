package com.randomrun.ui.widget.styled;

import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class OptionRow extends AbstractStyledButton {
    private final Supplier<Component> value;
    private final long creationTime = System.currentTimeMillis();
    private final float delay;

    public OptionRow(int x, int y, int width, Component label, Supplier<Component> value, OnPress onPress, float delay) {
        super(x, y, width, 20, label, onPress);
        this.value = value;
        this.delay = delay;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long elapsed = System.currentTimeMillis() - creationTime - (long) (delay * 1000);
        float alpha = elapsed <= 0 ? 0f : Ui.easeOut(elapsed / ANIMATION_DURATION);
        if (alpha <= 0) return;
        int y = getY() + (int) ((1f - alpha) * 12);
        updateAnimations(delta);
        handleHoverSound(alpha >= 1f);
        Theme theme = Theme.current();
        int bg = Ui.lerp(0xFF000000 | theme.panel, 0xFF000000 | theme.buttonBase, 0.4f + 0.6f * hoverProgress);
        context.fill(getX(), y, getX() + width, y + height, Ui.argb(bg, (active ? 0.85f : 0.4f) * alpha));
        context.fill(getX(), y, getX() + 2, y + height, Ui.argb(active ? theme.accent : 0x52525B, alpha));
        if (hoverProgress > 0.02f) Ui.border(context, getX(), y, getX() + width, y + height, 1, Ui.argb(theme.buttonHover, hoverProgress * alpha));
        Font font = Minecraft.getInstance().font;
        int textY = y + (height - 8) / 2;
        Component current = value.get();
        int valueWidth = font.width(current);
        int valueX = getX() + width - 10 - valueWidth;
        GuiText.text(context, font, Ui.ellipsize(font, getMessage().getString(), valueX - getX() - 24), getX() + 8, textY, Ui.argb(active ? 0xFFFFFF : 0x8A8A93, alpha), false);
        GuiText.text(context, font, current, valueX, textY, Ui.argb(active ? 0xFFFFFF : 0x8A8A93, alpha), true);
        if (active) {
            int arrow = Ui.argb(theme.accent, alpha * (0.4f + 0.6f * hoverProgress));
            com.randomrun.ui.util.Icons.CHEVRON_LEFT.drawCentered(context, valueX - 9, y, 4, height, arrow);
            com.randomrun.ui.util.Icons.CHEVRON_RIGHT.drawCentered(context, getX() + width - 7, y, 4, height, arrow);
        }
    }
}

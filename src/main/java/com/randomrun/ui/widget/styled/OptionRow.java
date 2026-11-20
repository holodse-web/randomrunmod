package com.randomrun.ui.widget.styled;

import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.InterfaceStyle;
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
    private final OnPress forward;
    private Runnable previous;
    private boolean arrows = true;
    private int leftArrowX = Integer.MIN_VALUE;

    public OptionRow(int x, int y, int width, Component label, Supplier<Component> value, OnPress onPress, float delay) {
        super(x, y, width, 20, label, onPress);
        this.value = value;
        this.delay = delay;
        this.forward = onPress;
    }

    public OptionRow previous(Runnable action) {
        previous = action;
        return this;
    }

    public OptionRow noArrows() {
        arrows = false;
        return this;
    }

    private void stepBack() {
        if (previous != null) previous.run();
        else forward.onPress(this);
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (arrows && event.x() >= leftArrowX - 6 && event.x() < leftArrowX + 8) stepBack();
        else super.onClick(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (active && arrows && isFocused()) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) { stepBack(); playDownSound(Minecraft.getInstance().getSoundManager()); return true; }
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) { forward.onPress(this); playDownSound(Minecraft.getInstance().getSoundManager()); return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long elapsed = System.currentTimeMillis() - creationTime - (long) (delay * 1000);
        float alpha = InterfaceStyle.isClassic() ? 1f : elapsed <= 0 ? 0f : Ui.easeOut(elapsed / ANIMATION_DURATION);
        if (alpha <= 0) return;
        int y = getY() + (int) ((1f - alpha) * 12);
        updateAnimations(delta);
        handleHoverSound(alpha >= 1f);
        Theme theme = Theme.current();
        if (InterfaceStyle.isClassic()) {
                int bg = active ? (isHovered() || isFocused() ? theme.buttonHover : theme.buttonBase) : 0x454541;
                Ui.bevel(context, getX(), y, getX() + width, y + height, bg, alpha, false);
                if (active && (isHovered() || isFocused())) Ui.border(context, getX(), y, getX() + width, y + height, 1, Ui.argb(0xFFFFA0, alpha));
        } else {
            int bg = Ui.lerp(0xFF000000 | theme.panel, 0xFF000000 | theme.buttonBase, 0.4f + 0.6f * hoverProgress);
            context.fill(getX(), y, getX() + width, y + height, Ui.argb(bg, (active ? 0.85f : 0.4f) * alpha));
            context.fill(getX(), y, getX() + 2, y + height, Ui.argb(active ? theme.accent : 0x52525B, alpha));
            if (hoverProgress > 0.02f) Ui.border(context, getX(), y, getX() + width, y + height, 1, Ui.argb(theme.buttonHover, hoverProgress * alpha));
        }
        Font font = Minecraft.getInstance().font;
        int textY = y + (height - 8) / 2;
        Component current = value.get();
        int maxValueWidth = Math.max(24, width / 2);
        if (font.width(current) > maxValueWidth) current = Component.literal(Ui.ellipsize(font, current.getString(), maxValueWidth)).setStyle(current.getStyle());
        int valueWidth = font.width(current);
        int valueX = getX() + width - 10 - valueWidth;
        GuiText.text(context, font, Ui.ellipsize(font, getMessage().getString(), valueX - getX() - 24), getX() + 8, textY, Ui.argb(active ? 0xFFFFFF : 0x8A8A93, alpha), false);
        GuiText.text(context, font, current, valueX, textY, Ui.argb(active ? 0xFFFFFF : 0x8A8A93, alpha), true);
        leftArrowX = valueX - 9;
        if (active && arrows) {
            boolean rowHover = isHovered();
            boolean onLeft = rowHover && mouseX >= leftArrowX - 6 && mouseX < leftArrowX + 8;
            int idle = InterfaceStyle.isClassic() ? 0xA0A09A : theme.accent;
            int lit = InterfaceStyle.isClassic() ? 0xFFFFA0 : 0xFFFFFF;
            float base = InterfaceStyle.isClassic() ? 1f : 0.4f + 0.6f * hoverProgress;
            com.randomrun.ui.util.Icons.CHEVRON_LEFT.drawCentered(context, leftArrowX, y, 4, height, Ui.argb(onLeft ? lit : idle, alpha * (onLeft ? 1f : base)));
            com.randomrun.ui.util.Icons.CHEVRON_RIGHT.drawCentered(context, getX() + width - 7, y, 4, height, Ui.argb(rowHover && !onLeft ? lit : idle, alpha * (rowHover && !onLeft ? 1f : base)));
        }
    }
}

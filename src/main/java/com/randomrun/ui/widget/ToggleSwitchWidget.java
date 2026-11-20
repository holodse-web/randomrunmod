package com.randomrun.ui.widget;

import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class ToggleSwitchWidget extends AbstractWidget {
    private static final int TRACK_X = 6, TRACK_W = 16;
    private boolean enabled, wasHovered;
    private final Runnable onPress;
    private float knob, hoverAnim;

    public ToggleSwitchWidget(int x, int y, boolean initialState, Runnable onPress, float delay) {
        super(x, y, 40, 16, Component.empty());
        enabled = initialState;
        knob = enabled ? 1f : 0f;
        this.onPress = onPress;
        updateLabel();
    }

    public ToggleSwitchWidget(int x, int y, boolean initialState, Runnable onPress) {
        this(x, y, initialState, onPress, 0f);
    }

    private void updateLabel() {
        setMessage(LanguageManager.text(enabled ? "randomrun.toggle.online" : "randomrun.toggle.offline"));
        setWidth(TRACK_X + TRACK_W + 6 + Minecraft.getInstance().font.width(getMessage()) + 7);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hover = active && (isHovered() || isFocused());
        if (hover && !wasHovered) Ui.hoverSound();
        wasHovered = hover;
        if (isHovered()) graphics.requestCursor(active ? com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND : com.mojang.blaze3d.platform.cursor.CursorTypes.NOT_ALLOWED);
        float target = enabled ? 1f : 0f;
        if (InterfaceStyle.isClassic()) {
            knob = target;
            hoverAnim = hover ? 1f : 0f;
            extractClassic(graphics, hover);
        } else {
            knob += (target - knob) * 0.3f;
            if (Math.abs(target - knob) < 0.01f) knob = target;
            hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * 0.25f;
            extractModern(graphics, hover);
        }
    }

    private void extractClassic(GuiGraphicsExtractor g, boolean hover) {
        int x = getX(), y = getY(), h = height;
        Ui.bevel(g, x, y, x + width, y + h, hover ? 0x3A3A36 : 0x303030, 1f, true);
        int tx = x + TRACK_X, ty = y + (h - 10) / 2;
        Ui.bevel(g, tx, ty, tx + TRACK_W, ty + 10, enabled ? 0x3F6E2A : 0x1E1E1C, 1f, true);
        int kx = tx + 1 + Math.round((TRACK_W - 10) * knob);
        Ui.bevel(g, kx, ty + 1, kx + 8, ty + 9, active ? (hover ? 0xC6C6C0 : 0xA8A8A0) : 0x60605A, 1f, false);
        com.randomrun.ui.util.GuiText.text(g, Minecraft.getInstance().font, getMessage(), tx + TRACK_W + 6, y + (h - 8) / 2,
            !active ? 0xFFA0A09A : hover ? 0xFFFFFFA0 : enabled ? 0xFFFFFFFF : 0xFFC0BDB2, true);
    }

    private void extractModern(GuiGraphicsExtractor g, boolean hover) {
        int x = getX(), y = getY(), w = width, h = height;
        int on = 0x4ADE80;
        g.fill(x, y, x + w, y + h, Ui.lerp(0x8C000000, 0xB018181B, hoverAnim));
        int border = Ui.lerp(0xFF000000 | 0x71717A, 0xFF000000 | on, knob);
        Ui.border(g, x, y, x + w, y + h, 1, Ui.argb(border & 0xFFFFFF, 0.35f + 0.4f * hoverAnim));
        int tx = x + TRACK_X, ty = y + (h - 8) / 2;
        int track = Ui.lerp(0xFF3F3F46, 0xFF000000 | 0x16A34A, knob);
        g.fill(tx + 1, ty, tx + TRACK_W - 1, ty + 8, track);
        g.fill(tx, ty + 1, tx + TRACK_W, ty + 7, track);
        int kx = tx + 1 + Math.round((TRACK_W - 8) * knob), ky = ty + 1;
        int face = !active ? 0xFF71717A : Ui.lerp(0xFFA1A1AA, 0xFFFFFFFF, Math.max(knob, hoverAnim));
        g.fill(kx + 1, ky, kx + 5, ky + 6, face);
        g.fill(kx, ky + 1, kx + 6, ky + 5, face);
        int text = Ui.lerp(0xFFA1A1AA, 0xFFFFFFFF, Math.max(knob, hoverAnim));
        com.randomrun.ui.util.GuiText.text(g, Minecraft.getInstance().font, getMessage(), tx + TRACK_W + 6, y + (h - 8) / 2, text, false);
    }

    private void toggle() {
        if (!active) return;
        enabled = !enabled;
        updateLabel();
        Ui.click();
        if (onPress != null) onPress.run();
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { toggle(); }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (isFocused() && (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE
                || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
                || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
            toggle();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

package com.randomrun.ui.widget;

import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class CheckboxRowWidget extends AbstractWidget {
    private static final int BOX = 10;
    private boolean checked, wasHovered;
    private float check, hoverAnim;
    private final Runnable onToggle;

    public CheckboxRowWidget(int x, int y, int width, Component label, boolean checked, Runnable onToggle) {
        super(x, y, width, 14, label);
        this.checked = checked;
        this.check = checked ? 1f : 0f;
        this.onToggle = onToggle;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hover = active && (isHovered() || isFocused());
        if (hover && !wasHovered) Ui.hoverSound();
        wasHovered = hover;
        if (isHovered()) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
        check += ((checked ? 1f : 0f) - check) * 0.35f;
        hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * 0.3f;
        Theme theme = Theme.current();
        int x = getX(), y = getY() + (height - BOX) / 2;
        if (InterfaceStyle.isClassic()) {
            Ui.bevel(graphics, x, y, x + BOX, y + BOX, 0x24241F, 1f, true);
            if (hover) Ui.border(graphics, x, y, x + BOX, y + BOX, 1, 0xFFFFFFA0);
        } else {
            graphics.fill(x, y, x + BOX, y + BOX, Ui.argb(Ui.lerp(0xFF000000 | theme.buttonBase, 0xFF000000, .55f) & 0xFFFFFF, .92f));
            graphics.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, Ui.argb(theme.accent, 0.85f * check));
            Ui.border(graphics, x, y, x + BOX, y + BOX, 1, Ui.argb(Ui.lerp(0xFF000000 | theme.buttonBase, 0xFF000000 | theme.accent, Math.max(check, hoverAnim * 0.6f)) & 0xFFFFFF, 1f));
        }
        if (check > 0.05f) Icons.CHECK.drawCentered(graphics, x, y, BOX, BOX, Ui.argb(0xFFFFFF, check));
        int text = InterfaceStyle.isClassic() ? (hover ? 0xFFFFFFA0 : 0xFFC0BDB2) : Ui.lerp(0xFFA1A1AA, 0xFFFFFFFF, Math.max(hoverAnim, check * 0.4f));
        GuiText.text(graphics, Minecraft.getInstance().font, getMessage(), x + BOX + 6, getY() + (height - 8) / 2, text, false);
    }

    private void toggle() {
        if (!active) return;
        checked = !checked;
        Ui.click();
        if (onToggle != null) onToggle.run();
    }

    public boolean isChecked() {
        return checked;
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

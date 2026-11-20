package com.randomrun.ui.widget.styled;

import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TextFieldStyled extends EditBox {
    private final Font renderer;
    private Integer baseColor, hoverColor, borderColor;
    private Component placeholder;
    private static final int EYE_SPACE = 14;
    private boolean masked, revealable, revealed, centered, extractingText;
    private int maskStart, maskHighlight;
    private long focusTime;
    private final long createdAt = System.currentTimeMillis();
    private final float delay;
    private float alpha = 1f;

    public TextFieldStyled(Font font, int x, int y, int width, int height, Component message, float delay) {
        super(font, x, y, width, height, message);
        renderer = font;
        this.delay = delay;
        setBordered(true);
        setTextColor(0xFFE8E8E8);
        setTextColorUneditable(0xFFA0A092);
    }
    public TextFieldStyled(Font font, int x, int y, int width, int height, Component message) {
        this(font, x, y, width, height, message, 0f);
    }
    public TextFieldStyled setColors(int baseColor, int hoverColor) {
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        return this;
    }
    public TextFieldStyled setBorderColor(int borderColor) { this.borderColor = borderColor; return this; }
    public TextFieldStyled masked() { return setMasked(true); }
    public TextFieldStyled setMasked(boolean value) { masked = value; return this; }
    public TextFieldStyled revealable() { revealable = true; return this; }
    private boolean hidden() { return masked && !revealed; }
    private boolean overEye(double mouseX, double mouseY) {
        return revealable && mouseX >= getX() + width - EYE_SPACE - 2 && mouseX < getX() + width - 2 && mouseY >= getY() && mouseY < getY() + height;
    }
    public TextFieldStyled centered() { setCentered(true); return this; }
    @Override public void setCentered(boolean value) { centered = value; super.setCentered(value); }
    public void setCenteredPlaceholder(Component text) { placeholder = text; }
    protected int getTextYAdjustment() { return 0; }

    @Override
    public void setFocused(boolean value) {
        if (value == isFocused()) return;
        super.setFocused(value);
        if (value) focusTime = System.currentTimeMillis();
    }

    private float appear() {
        if (InterfaceStyle.isClassic()) return 1f;
        float t = (System.currentTimeMillis() - createdAt - delay * 1000f) / 400f;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private int fade(int argb) {
        return alpha >= 1f ? argb : ((int) (((argb >>> 24) & 0xFF) * alpha) << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float progress = appear();
        if (progress <= 0f) return;
        alpha = progress;
        if (progress >= 1f) { extractField(graphics, mouseX, mouseY, delta); return; }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, (1f - progress) * 30f);
        extractField(graphics, mouseX, mouseY, delta);
        graphics.pose().popMatrix();
        alpha = 1f;
    }

    private void extractField(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        Theme theme = Theme.current();
        int accent = hoverColor != null ? hoverColor : theme.accent;
        if (InterfaceStyle.isClassic()) {
            int face = baseColor == null ? 0x24241F : Ui.lerp(0xFF24241F, 0xFF000000 | baseColor, .15f) & 0xFFFFFF;
            Ui.bevel(graphics, x, y, x + width, y + height, face, 1f, true);
            if (active && isFocused()) Ui.border(graphics, x, y, x + width, y + height, 1, 0xFFFFFFA0);
            else if (active && isHovered()) Ui.border(graphics, x, y, x + width, y + height, 1, Ui.argb(borderColor != null ? borderColor : accent, .7f));
        } else {
            int base = baseColor != null ? baseColor : theme.buttonBase;
            graphics.fill(x, y, x + width, y + height, Ui.argb(Ui.lerp(0xFF000000 | base, 0xFF000000, .55f), .92f * alpha));
            int idle = borderColor != null ? borderColor : base;
            Ui.border(graphics, x, y, x + width, y + height, 2, Ui.argb(isFocused() ? accent : isHovered() ? Ui.lerp(0xFF000000 | idle, 0xFF000000 | accent, .5f) : idle, alpha));
        }
        if (alpha < 0.6f) return;
        if (hidden()) {
            extractMasked(graphics);
        } else {
            extractingText = true;
            try { super.extractWidgetRenderState(graphics, mouseX, mouseY, delta); }
            finally { extractingText = false; }
        }
        if (placeholder != null && getValue().isEmpty() && !isFocused()) {
            String text = Ui.ellipsize(renderer, placeholder.getString(), Math.max(0, width - 10 - (revealable ? EYE_SPACE * 2 : 0)));
            int textX = centered ? x + (width - renderer.width(text)) / 2 : x + 4;
            GuiText.text(graphics, renderer, text, textX, y + (height - 8) / 2, fade(0xFFA8A89E), true);
        }
        if (revealable) extractEye(graphics, mouseX, mouseY, accent);
    }

    private void extractEye(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent) {
        boolean hover = active && overEye(mouseX, mouseY);
        int color = !active ? 0xFF6E6E66 : hover ? (InterfaceStyle.isClassic() ? 0xFFFFFFA0 : Ui.argb(accent, 1f)) : 0xFFA8A89E;
        Icons icon = revealed ? Icons.EYE : Icons.EYE_OFF;
        icon.drawCentered(graphics, getX() + width - EYE_SPACE - 2, getY(), EYE_SPACE, height, fade(color));
        if (hover) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
    }

    private int maskCapacity() { return Math.max(1, getInnerWidth() / Math.max(1, renderer.width("*"))); }
    private void updateMaskScroll() {
        int capacity = maskCapacity(), cursor = getCursorPosition();
        maskStart = Math.min(maskStart, Math.max(0, getValue().length() - capacity));
        if (cursor < maskStart) maskStart = cursor;
        if (cursor > maskStart + capacity) maskStart = cursor - capacity;
    }
    private int maskTextX() {
        int length = Math.min(maskCapacity(), getValue().length() - maskStart);
        int textWidth = renderer.width("*".repeat(Math.max(0, length)));
        return centered ? getX() + (width - textWidth) / 2 : getX() + 4;
    }
    private void extractMasked(GuiGraphicsExtractor graphics) {
        updateMaskScroll();
        int length = Math.min(maskCapacity(), getValue().length() - maskStart);
        int x = maskTextX(), y = getY() + (height - 8) / 2;
        int glyphWidth = Math.max(1, renderer.width("*"));
        int cursor = Math.clamp(getCursorPosition() - maskStart, 0, length);
        int highlight = Math.clamp(maskHighlight - maskStart, 0, length);
        int color = active ? 0xFFE8E8E8 : 0xFFA0A092;
        graphics.enableScissor(getX() + 3, getY() + 2, getX() + width - 3, getY() + height - 2);
        GuiText.text(graphics, renderer, "*".repeat(length), x, y + 2, color, true);
        if (cursor != highlight) graphics.textHighlight(x + Math.min(cursor, highlight) * glyphWidth, y - 1,
            x + Math.max(cursor, highlight) * glyphWidth, y + 9, true);
        int cursorX = x + cursor * glyphWidth;
        if (isFocused() && ((System.currentTimeMillis() - focusTime) / 300L & 1L) == 0) graphics.fill(cursorX, y - 1, cursorX + 1, y + 9, color);
        graphics.disableScissor();
        if (canConsumeInput()) Minecraft.getInstance().textInputManager().setTextInputArea(cursorX, y, cursorX + 1, y + 10);
    }
    @Override public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        maskHighlight = Math.clamp(position, 0, getValue().length());
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean accepted = super.mouseClicked(event, doubleClick);
        if (accepted) setFocused(true);
        return accepted;
    }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (overEye(event.x(), event.y())) {
            revealed = !revealed;
            moveCursorTo(getCursorPosition(), false);
            return;
        }
        if (!hidden()) { super.onClick(event, doubleClick); return; }
        updateMaskScroll();
        int position = Math.clamp(maskStart + (int) Math.round((event.x() - maskTextX()) / Math.max(1, renderer.width("*"))), 0, getValue().length());
        if (doubleClick) { moveCursorToStart(false); moveCursorToEnd(true); }
        else moveCursorTo(position, event.hasShiftDown());
    }
    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (overEye(event.x(), event.y())) return;
        if (!hidden()) { super.onDrag(event, dx, dy); return; }
        int position = Math.clamp(maskStart + (int) Math.round((event.x() - maskTextX()) / Math.max(1, renderer.width("*"))), 0, getValue().length());
        moveCursorTo(position, true);
    }
    @Override protected MutableComponent createNarrationMessage() {
        return hidden() ? Component.translatable("gui.narrate.editBox", getMessage(), "*".repeat(getValue().length())) : super.createNarrationMessage();
    }
    @Override public boolean isBordered() { return !extractingText && super.isBordered(); }
    @Override public int getInnerWidth() { return Math.max(1, width - 8 - (revealable ? EYE_SPACE * 2 : 0)); }
}

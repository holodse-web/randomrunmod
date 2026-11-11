package com.randomrun.features.battle.screen.room;




import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;
import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
import com.randomrun.features.battle.screen.room.RoomSupport.CodeFormatter;
import com.randomrun.features.battle.screen.room.RoomSupport.RecentDataManager;
import com.randomrun.features.battle.screen.room.RoomSupport.SoundHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class RoomJoinScreen extends AbstractRandomRunScreen {
    private static final int CODE_LENGTH = 5;
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 168;
    private static final int CELL = 28;
    private static final int CELL_HEIGHT = 32;
    private static final int GAP = 6;
    private static final int MAX_RECENT = 4;

    private final Screen parent;
    private final List<String> recentRooms;
    private final List<int[]> recentHits = new ArrayList<>();
    private String code = "";
    private TextFieldStyled passwordField;
    private ButtonRainbow joinButton;
    private String errorMessage;
    private long errorTime;
    private long shakeTime;
    private long openTime;
    private long typedTime;
    private boolean joining;

    public RoomJoinScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.join.title"));
        this.parent = parent;
        this.recentRooms = RecentDataManager.getRoomInstance().getRecent();
    }

    private int top() {
        return Math.max(10, height / 2 - PANEL_HEIGHT / 2 - 20);
    }

    private int cellsLeft() {
        return width / 2 - (CODE_LENGTH * CELL + (CODE_LENGTH - 1) * GAP) / 2;
    }

    private int cellsTop() {
        return top() + 46;
    }

    @Override
    protected void init() {
        String password = passwordField == null ? "" : passwordField.getValue();
        super.init();
        if (openTime == 0) openTime = System.currentTimeMillis();
        int cx = width / 2, top = top();

        passwordField = new TextFieldStyled(font, cx - 100, top + 112, 200, 20, LanguageManager.text("randomrun.battle.password"), 0.05f);
        passwordField.setMaxLength(32);
        passwordField.setCenteredPlaceholder(LanguageManager.text("randomrun.screen.join.password_hint"));
        passwordField.setValue(password);
        addRenderableWidget(passwordField);

        joinButton = new ButtonRainbow(cx - 100, top + PANEL_HEIGHT + 10, 200, 20,
            LanguageManager.text("randomrun.battle.join_room"), button -> joinRoom(), 0, 0.1f);
        joinButton.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        addRenderableWidget(joinButton);
        addRenderableWidget(new ButtonMenu(cx - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 1, 0.15f));
        setFocused(null);
        updateJoinButton();
    }

    private void updateJoinButton() {
        if (joinButton != null) joinButton.active = !joining && code.length() == CODE_LENGTH;
    }

    private void setCode(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() > CODE_LENGTH) digits = digits.substring(0, CODE_LENGTH);
        if (!digits.equals(code)) typedTime = System.currentTimeMillis();
        code = digits;
        errorMessage = null;
        updateJoinButton();
    }

    private void fail(String message) {
        errorMessage = message;
        errorTime = System.currentTimeMillis();
        shakeTime = errorTime;
        SoundHelper.playError();
    }

    private void joinRoom() {
        if (joining) return;
        if (code.length() != CODE_LENGTH) {
            fail(LanguageManager.translate("randomrun.screen.join.error.invalid_code"));
            return;
        }
        String joinCode = code;
        joining = true;
        errorMessage = null;
        updateJoinButton();
        BattleManager.getInstance().joinRoomDetailed(BackendSession.playerName(), joinCode, passwordField.getValue().trim()).whenComplete((result, error) ->
            Minecraft.getInstance().execute(() -> {
                joining = false;
                updateJoinButton();
                if (Minecraft.getInstance().gui.screen() != this) return;
                if (error != null) {
                    RandomRunMod.LOGGER.error("[Room] Join failed: code={}", joinCode, error);
                    fail(LanguageManager.translate(RoomJoinResult.CONNECTION_FAILED.messageKey()));
                } else if (result != null && result.joined()) {
                    RecentDataManager.getRoomInstance().add(joinCode);
                    SoundHelper.playJoin();
                    Minecraft.getInstance().gui.setScreen(new RoomWaitingScreen(parent, joinCode, false));
                } else {
                    RoomJoinResult reason = result == null ? RoomJoinResult.CONNECTION_FAILED : result;
                    if (reason == RoomJoinResult.CANCELLED) return;
                    RandomRunMod.LOGGER.warn("[Room] Join rejected: code={}, reason={}", joinCode, reason);
                    fail(LanguageManager.translate(reason.messageKey()));
                }
            }));
    }

    @Override
    public void onClose() {
        if (!joining) Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        Theme theme = Theme.current();
        int left = width / 2 - PANEL_WIDTH / 2, right = left + PANEL_WIDTH;
        int top = top(), bottom = top + PANEL_HEIGHT;
        Ui.panel(context, left, top, right, bottom, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, 0xFFFFFF);

        GuiText.centeredText(context, font, LanguageManager.text("randomrun.screen.join.title"), width / 2, top + 10, Ui.argb(0xFFFFFF, fade));
        Ui.divider(context, left + 12, right - 12, top + 23, theme.border, fade);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.screen.join.code_field_label"), width / 2, top + 32, Ui.argb(0xA1A1AA, fade));

        drawCells(context, mouseX, mouseY, theme, fade);
        drawRecent(context, mouseX, mouseY, theme, fade);

        int statusY = top + PANEL_HEIGHT - 16;
        if (joining) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 300 % 4));
            Component text = Component.literal(LanguageManager.translate("randomrun.screen.join.connecting") + dots);
            int w = font.width(text);
            Ui.spinner(context, width / 2 - w / 2 - 9, statusY + 4, 4, theme.accent);
            GuiText.text(context, font, text, width / 2 - w / 2, statusY, Ui.argb(0x4ADE80, fade), false);
        } else if (errorMessage != null && System.currentTimeMillis() - errorTime < 5000) {
            float alpha = Math.min(1f, (5000 - (System.currentTimeMillis() - errorTime)) / 400f);
            GuiText.centeredText(context, font, Component.literal(Ui.ellipsize(font, errorMessage.replaceAll("§.", ""), PANEL_WIDTH - 20)), width / 2, statusY, Ui.argb(0xF87171, alpha * fade));
        }
    }

    private void drawCells(GuiGraphicsExtractor context, int mouseX, int mouseY, Theme theme, float fade) {
        long now = System.currentTimeMillis();
        float shake = now - shakeTime < 360 ? (float) Math.sin((now - shakeTime) / 22.0) * 4f * (1f - (now - shakeTime) / 360f) : 0f;
        int x0 = cellsLeft() + Math.round(shake), y = cellsTop();
        boolean focused = getFocused() == null && !joining;
        boolean blink = (now / 500) % 2 == 0;
        boolean error = errorMessage != null && now - errorTime < 1200;
        for (int i = 0; i < CODE_LENGTH; i++) {
            int x = x0 + i * (CELL + GAP);
            boolean filled = i < code.length();
            boolean active = focused && i == Math.min(code.length(), CODE_LENGTH - 1) && code.length() < CODE_LENGTH;
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, CELL, CELL_HEIGHT);
            context.fill(x, y, x + CELL, y + CELL_HEIGHT, Ui.argb(hovered ? theme.buttonHover : theme.buttonBase, 0.85f * fade));
            int border = error ? 0xF87171 : filled ? theme.accent : active ? 0xFFFFFF : 0x52525B;
            Ui.border(context, x, y, x + CELL, y + CELL_HEIGHT, 1, Ui.argb(border, (filled || active || error ? 1f : 0.7f) * fade));
            if (filled) {
                float pop = i == code.length() - 1 ? Ui.easeOut((now - typedTime) / 160f) : 1f;
                float scale = 2f * (0.7f + 0.3f * pop);
                Ui.centeredScaled(context, font, Component.literal(String.valueOf(code.charAt(i))), x + CELL / 2f, y + CELL_HEIGHT / 2f - 4 * scale + 0.5f, scale, Ui.argb(0xFFFFFF, fade));
                context.fill(x + 6, y + CELL_HEIGHT - 4, x + CELL - 6, y + CELL_HEIGHT - 3, Ui.argb(theme.accent, 0.8f * fade));
            } else if (active && blink) {
                context.fill(x + CELL / 2 - 4, y + CELL_HEIGHT - 9, x + CELL / 2 + 4, y + CELL_HEIGHT - 7, Ui.argb(0xFFFFFF, fade));
            }
        }
    }

    private void drawRecent(GuiGraphicsExtractor context, int mouseX, int mouseY, Theme theme, float fade) {
        recentHits.clear();
        if (recentRooms.isEmpty()) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.screen.join.paste_hint"), width / 2, cellsTop() + CELL_HEIGHT + 9, Ui.argb(0x71717A, fade));
            return;
        }
        List<String> shown = recentRooms.stream().limit(MAX_RECENT).toList();
        Component label = LanguageManager.text("randomrun.screen.join.recent");
        int total = font.width(label) + 4;
        for (String room : shown) total += font.width(CodeFormatter.format(room)) + 12;
        int x = width / 2 - total / 2, y = cellsTop() + CELL_HEIGHT + 7;
        GuiText.text(context, font, label, x, y + 2, Ui.argb(0x71717A, fade), false);
        x += font.width(label) + 4;
        for (String room : shown) {
            Component text = Component.literal(CodeFormatter.format(room));
            int w = font.width(text) + 8;
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, w, 13);
            Ui.chip(context, font, text, x, y, Ui.argb(hovered ? theme.buttonHover : theme.buttonBase, 0.9f * fade), Ui.argb(hovered ? theme.accent : 0xD4D4D8, fade));
            recentHits.add(new int[]{x, y, w, 13, recentRooms.indexOf(room)});
            x += w + 4;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !joining) {
            for (int[] hit : recentHits) {
                if (Ui.inside(event.x(), event.y(), hit[0], hit[1], hit[2], hit[3])) {
                    setCode(recentRooms.get(hit[4]));
                    setFocused(null);
                    SoundHelper.playClick();
                    return true;
                }
            }
            int span = CODE_LENGTH * CELL + (CODE_LENGTH - 1) * GAP;
            if (Ui.inside(event.x(), event.y(), cellsLeft(), cellsTop(), span, CELL_HEIGHT)) {
                setFocused(null);
                return true;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) setFocused(null);
        return handled;
    }

    private static int digit(int key) {
        if (key == InputConstants.KEY_0 || key == InputConstants.KEY_NUMPAD0) return 0;
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) return key - InputConstants.KEY_1 + 1;
        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) return key - InputConstants.KEY_NUMPAD1 + 1;
        return -1;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            joinRoom();
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            setFocused(getFocused() == passwordField ? null : passwordField);
            return true;
        }
        if (getFocused() != passwordField && !joining) {
            if (event.isPaste()) {
                setCode(minecraft.keyboardHandler.getClipboard());
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!code.isEmpty()) setCode(event.hasControlDown() ? "" : code.substring(0, code.length() - 1));
                return true;
            }
            int digit = digit(key);
            if (digit >= 0 && !event.hasControlDown()) {
                if (code.length() < CODE_LENGTH) {
                    setCode(code + digit);
                    SoundHelper.playClick();
                }
                return true;
            }
            if (key == InputConstants.KEY_DELETE) {
                setCode("");
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class PrivateJoinScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private TextFieldWidget codeField;
    private TextFieldWidget passwordField;
    private String errorMessage = null;
    private long startTime;
    
    public PrivateJoinScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.join.title"));
        this.parent = parent;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Code Field (StyledButton2 style) - сдвинуто вниз для лейбла
        codeField = new TextFieldWidget(textRenderer, centerX - 100, centerY - 30, 200, 20, Text.translatable("randomrun.screen.join.code_field_label"));
        codeField.setMaxLength(5);
        codeField.setDrawsBackground(false);
        codeField.setPlaceholder(Text.literal("§712345"));
        codeField.setChangedListener(text -> {
            errorMessage = null;
            if (text.length() > 0 && !text.matches("\\d*")) {
                codeField.setText(text.replaceAll("\\D", ""));
            }
        });
        addDrawableChild(codeField);
        setInitialFocus(codeField);
        
        // Password Field (StyledButton2 style) - сдвинуто вниз для лейбла
        passwordField = new TextFieldWidget(textRenderer, centerX - 100, centerY + 2, 200, 20, Text.translatable("randomrun.battle.password"));
        passwordField.setMaxLength(32);
        passwordField.setDrawsBackground(false);
        passwordField.setPlaceholder(Text.literal("§71234"));
        addDrawableChild(passwordField);
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 55,
            200, 20,
            Text.translatable("randomrun.battle.join_room"),
            button -> joinRoom(),
            0, 0.1f
        ));
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f
        ));
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        int contentLeft = width / 2 - 120;
        int contentTop = centerY - 70;
        int contentRight = width / 2 + 120;
        int contentBottom = centerY + 50;
        
        context.fill(contentLeft, contentTop, contentRight, contentBottom, 0xCC1a0b2e);
        
        // Border
        com.randomrun.ui.screen.main.MainModScreen.renderAnimatedBorder(context, contentLeft, contentTop, contentRight, contentBottom, 2);
        
        // Separator
        context.fill(centerX - 100, contentTop + 25, centerX + 100, contentTop + 26, 0xFFFFFFFF);
    }
    
    private void joinRoom() {
        String code = codeField.getText().trim();
        String password = passwordField.getText();
        
        if (code.length() != 5) {
            errorMessage = Text.translatable("randomrun.screen.join.error.invalid_code").getString();
            return;
        }
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
        errorMessage = Text.translatable("randomrun.screen.join.status.connecting").getString();
        
        BattleManager.getInstance().joinRoom(playerName, code, password).thenAccept(success -> {
            if (success) {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen(new BattleWaitingScreen(parent, code, false));
                });
            } else {
                MinecraftClient.getInstance().execute(() -> {
                    errorMessage = Text.translatable("randomrun.screen.join.error.room_not_found").getString();
                });
            }
        });
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Рендерим поле ввода кода вручную с фоном (Стиль StyledButton2)
        if (codeField != null) {
            int cx = codeField.getX();
            int cy = codeField.getY();
            int cw = codeField.getWidth();
            int ch = codeField.getHeight();
            
            boolean codeHovered = codeField.isMouseOver(mouseX, mouseY) || codeField.isFocused();
            
            int baseColor = 0x302b63;
            int hoverColor = 0x6930c3;
            
            int bgColor = codeHovered ? hoverColor : baseColor;
            int bgAlpha = 0xE0;
            int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
            
            context.fill(cx, cy, cx + cw, cy + ch, finalBgColor);
            
            int borderColor = codeHovered ? 0xFFd042ff : 0xFF6930c3;
            
            context.fill(cx - 1, cy - 1, cx + cw + 1, cy, borderColor);
            context.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, borderColor);
            context.fill(cx - 1, cy, cx, cy + ch, borderColor);
            context.fill(cx + cw, cy, cx + cw + 1, cy + ch, borderColor);
            
            codeField.render(context, mouseX, mouseY, delta);
        }
        
        // Рендерим поле ввода пароля вручную с фоном (Стиль StyledButton2)
        if (passwordField != null) {
            int px = passwordField.getX();
            int py = passwordField.getY();
            int pw = passwordField.getWidth();
            int ph = passwordField.getHeight();
            
            boolean hovered = passwordField.isMouseOver(mouseX, mouseY) || passwordField.isFocused();
            
            // Цвета из StyledButton2
            int baseColor = 0x302b63;
            int hoverColor = 0x6930c3;
            
            int bgColor = hovered ? hoverColor : baseColor;
            int bgAlpha = 0xE0;
            int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
            
            context.fill(px, py, px + pw, py + ph, finalBgColor);
            
            // Граница
            int borderColor = hovered ? 0xFFd042ff : 0xFF6930c3;
            
            context.fill(px - 1, py - 1, px + pw + 1, py, borderColor); // Верх
            context.fill(px - 1, py + ph, px + pw + 1, py + ph + 1, borderColor); // Низ
            context.fill(px - 1, py, px, py + ph, borderColor); // Лево
            context.fill(px + pw, py, px + pw + 1, py + ph, borderColor); // Право
            
            passwordField.render(context, mouseX, mouseY, delta);
        }
        
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        int contentTop = centerY - 70;
        
        // Анимированный радужный цвет для заголовка
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;
        float hue = (time * 0.5f) % 1.0f;
        int rainbowColor = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        
        String title = Text.translatable("randomrun.screen.join.title").getString();
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal(title), 
            centerX, contentTop + 10, rainbowColor);
        
        // Лейбл "Введите код комнаты" - над полем ввода кода
        context.drawTextWithShadow(textRenderer,
            Text.translatable("randomrun.screen.join.label.enter_code"),
            centerX - 100, centerY - 42, 0xAAAAAA);
        
        // Лейбл "Пароль (Опционально)" - над полем ввода пароля
        context.drawTextWithShadow(textRenderer,
            Text.translatable("randomrun.battle.password_optional"),
            centerX - 100, centerY - 12, 0xAAAAAA);
        
        if (errorMessage != null) {
            int errorColor = errorMessage.startsWith("§c") ? 0xFF5555 : 0xAAAAAA;
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(errorMessage),
                centerX, centerY + 30, errorColor);
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter или Numpad Enter
            joinRoom();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
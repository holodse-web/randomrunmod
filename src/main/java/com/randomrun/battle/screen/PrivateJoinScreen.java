package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class PrivateJoinScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private TextFieldWidget codeField;
    private String errorMessage = null;
    private long startTime;
    
    public PrivateJoinScreen(Screen parent) {
        super(Text.literal("Присоединиться"));
        this.parent = parent;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        codeField = new TextFieldWidget(textRenderer, centerX - 100, centerY, 200, 20, Text.literal("Код комнаты"));
        codeField.setPlaceholder(Text.literal("Введите 5-значный код"));
        codeField.setMaxLength(5);
        codeField.setChangedListener(text -> {
            errorMessage = null;
            if (text.length() > 0 && !text.matches("\\d*")) {
                codeField.setText(text.replaceAll("\\D", ""));
            }
        });
        addDrawableChild(codeField);
        setInitialFocus(codeField);
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 55,
            200, 20,
            Text.translatable("randomrun.battle.join_room"),
            button -> joinRoom(),
            0, 0.1f
        ));
        
        addDrawableChild(new StyledButton2(
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
        int contentTop = centerY - 60;
        int contentRight = width / 2 + 120;
        int contentBottom = centerY + 60;
        
        context.fill(contentLeft, contentTop, contentRight, contentBottom, 0xCC1a0b2e);
        
        // Border
        com.randomrun.ui.screen.MainModScreen.renderAnimatedBorder(context, contentLeft, contentTop, contentRight, contentBottom, 2);
        
        // Separator
        context.fill(centerX - 100, contentTop + 25, centerX + 100, contentTop + 26, 0xFFFFFFFF);
    }
    
    private void joinRoom() {
        String code = codeField.getText().trim();
        
        if (code.length() != 5) {
            errorMessage = "§cКод должен содержать 5 цифр";
            return;
        }
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
        errorMessage = "§7Подключение...";
        
        BattleManager.getInstance().joinPrivateRoom(playerName, code).thenAccept(room -> {
            if (room != null) {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen(new BattleWaitingScreen(parent, code, false));
                });
            } else {
                MinecraftClient.getInstance().execute(() -> {
                    errorMessage = "§cКомната не найдена или занята";
                });
            }
        });
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        int contentTop = centerY - 60;
        
        // Анимированный радужный цвет для заголовка
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;
        float hue = (time * 0.5f) % 1.0f;
        int rainbowColor = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        
        String title = "Присоединиться к комнате";
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal(title), 
            centerX, contentTop + 10, rainbowColor);
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§7Введите код комнаты:"),
            centerX, centerY - 20, 0xAAAAAA);
        
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
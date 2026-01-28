package com.randomrun.gui.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class PrivateJoinScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private TextFieldWidget codeField;
    private String errorMessage = null;
    
    public PrivateJoinScreen(Screen parent) {
        super(Text.literal("Присоединиться"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        codeField = new TextFieldWidget(textRenderer, centerX - 100, centerY - 10, 200, 20, Text.literal("Код комнаты"));
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
            centerX - 100, centerY + 25,
            200, 20,
            Text.literal("§a⚡ Присоединиться"),
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
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l§bПРИСОЕДИНИТЬСЯ"), 
            centerX, 30, 0xFFFFFF);
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§7Введите код комнаты:"),
            centerX, centerY - 35, 0xAAAAAA);
        
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(errorMessage),
                centerX, centerY + 50, 0xFF5555);
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            joinRoom();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

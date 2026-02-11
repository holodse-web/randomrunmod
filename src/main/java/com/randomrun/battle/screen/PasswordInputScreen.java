package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class PasswordInputScreen extends AbstractRandomRunScreen {

    private final Screen parent;
    private final BattleRoom room;
    private TextFieldStyled passwordField;
    private boolean joining = false;
    private String errorMsg = null;

    public PasswordInputScreen(Screen parent, BattleRoom room) {
        super(Text.translatable("randomrun.battle.enter_password"));
        this.parent = parent;
        this.room = room;
    }

    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        passwordField = new TextFieldStyled(textRenderer, centerX - 100, centerY - 15, 200, 20, Text.translatable("randomrun.battle.password"), 0.05f);
        passwordField.setMaxLength(32);
        passwordField.setCenteredPlaceholder(Text.translatable("randomrun.battle.enter_password_placeholder"));
        addDrawableChild(passwordField);
        setInitialFocus(passwordField);
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, centerY + 20, 200, 20,
            Text.translatable("randomrun.battle.join_room"),
            button -> joinRoom(),
            0, 0.1f
        ));
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, centerY + 50, 200, 20,
            Text.translatable("randomrun.button.cancel"),
            button -> client.setScreen(parent),
            1, 0.15f
        ));
    }
    
    private void joinRoom() {
        if (joining) return;
        joining = true;
        errorMsg = null;
        
        String password = passwordField.getText();
        String playerName = client.getSession().getUsername();
        
        BattleManager.getInstance().joinRoom(playerName, room.getRoomCode(), password)
            .thenAccept(success -> {
                joining = false;
                if (success) {
                    client.execute(() -> client.setScreen(new BattleWaitingScreen(parent, room.getRoomCode(), false)));
                } else {
                    errorMsg = "Неверный пароль или комната полна";
                }
            });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.battle.enter_password"), width / 2, height / 2 - 50, 0xFFFFFF);
        
        // Render buttons FIRST
        super.render(context, mouseX, mouseY, delta);
        
        if (errorMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(errorMsg), width / 2, height / 2 + 5, 0xFF5555);
        }
        
        if (joining) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Вход..."), width / 2, height / 2 + 80, 0xAAAAAA);
        }
    }
}

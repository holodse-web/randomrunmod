package com.randomrun.battle.screen;

import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class BattleMenuScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private long startTime;
    
    public BattleMenuScreen(Screen parent) {
        super(Text.translatable("randomrun.battle.title"));
        this.parent = parent;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY - 27,
            200, 20,
            Text.translatable("randomrun.battle.private_room"),
            button -> MinecraftClient.getInstance().setScreen(new PrivateBattleMenuScreen(this)),
            0, 0.1f
        ));
        
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY + 8,
            200, 20,
            Text.translatable("randomrun.battle.public_queue"),
            button -> MinecraftClient.getInstance().setScreen(new PublicQueueScreen(this)),
            1, 0.15f
        ));
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            2, 0.2f
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
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        int contentTop = centerY - 60;
        
        // Анимированный радужный цвет
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;
        float hue = (time * 0.5f) % 1.0f;
        int rainbowColor = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        
        String title = Text.translatable("randomrun.battle.title").getString();
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal(title), 
            centerX, contentTop + 10, rainbowColor);
    }
}
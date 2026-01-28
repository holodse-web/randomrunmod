package com.randomrun.gui.screen;

import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class PrivateBattleMenuScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    
    public PrivateBattleMenuScreen(Screen parent) {
        super(Text.translatable("randomrun.battle.private_room"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY - 15,
            200, 20,
            Text.literal("§a" + Text.translatable("randomrun.battle.create_room").getString()),
            button -> MinecraftClient.getInstance().setScreen(new PrivateHostScreen(this)),
            0, 0.1f
        ));
        
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY + 20,
            200, 20,
            Text.literal("§b" + Text.translatable("randomrun.battle.join_room").getString()),
            button -> MinecraftClient.getInstance().setScreen(new PrivateJoinScreen(this)),
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l§a" + Text.translatable("randomrun.battle.private_room").getString().toUpperCase()), 
            centerX, 30, 0xFFFFFF);
    }
}

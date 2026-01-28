/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.gui.screen;

import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ChallengesSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    
    public ChallengesSettingsScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.time_challenge_settings.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = 60;
        int spacing = 30;
        
        // Time Challenge settings (Original "Time Challenge" screen)
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.time_challenge"),
            button -> MinecraftClient.getInstance().setScreen(new TimeChallengeSettingsScreen(this)),
            0, 0.1f
        ));
        
        // You can add more challenge buttons here in the future
        
        // Back button
        addDrawableChild(new StyledButton2(
            centerX - 100 / 2, height - 30,
            100, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
}

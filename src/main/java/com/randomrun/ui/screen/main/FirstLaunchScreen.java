package com.randomrun.ui.screen.main;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FirstLaunchScreen extends AbstractRandomRunScreen {
    private final Screen parent;

    public FirstLaunchScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.first_launch.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;
        int centerY = height / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;

        // Enable Button
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, centerY + 50,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.enable_online").formatted(Formatting.GREEN),
            button -> saveChoice(true),
            0, 0.1f
        ));

        // Disable Button
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, centerY + 80,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.disable_online").formatted(Formatting.RED),
            button -> saveChoice(false),
            1, 0.1f
        ));
    }

    private void saveChoice(boolean enableOnline) {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        config.setOnlineMode(enableOnline); // Это вызовет логику сохранения профиля, если enableOnline = true
        config.setFirstRun(false);
        RandomRunMod.getInstance().saveConfig();

        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 110, 0xFFFFFF);

        // Render multi-line message
        Text message = Text.translatable("randomrun.screen.first_launch.message");
        int y = height / 2 - 80;
        for (var line : textRenderer.wrapLines(message, width - 80)) {
             context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xCCCCCC);
             y += 12;
        }
        
        Text note = Text.translatable("randomrun.screen.first_launch.note").formatted(Formatting.GRAY, Formatting.ITALIC);
        context.drawCenteredTextWithShadow(textRenderer, note, width / 2, height - 30, 0xAAAAAA);
    }
}

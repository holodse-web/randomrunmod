package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.config.ModConfig;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class SpeedrunSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;
    
    public SpeedrunSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public SpeedrunSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.speedrun_settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 250;
        int buttonHeight = 20;
        int startY = 80;
        int spacing = 30;
        
        // Allow unobtainable items toggle
        String toggleText = config.isAllowUnobtainableItems() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.unobtainable_items", toggleText),
            button -> {
                config.setAllowUnobtainableItems(!config.isAllowUnobtainableItems());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing // Skip animation if refreshing
        ));
        
        // Victory screen mode button
        spacing = 30;
        String victoryModeText = switch (config.getVictoryScreenMode()) {
            case SHOW -> "§a" + Text.translatable("randomrun.victory_screen.show").getString();
            case HIDE -> "§c" + Text.translatable("randomrun.victory_screen.hide").getString();
            case SHOW_AFTER_10_SECONDS -> "§e" + Text.translatable("randomrun.victory_screen.show_after_10").getString();
        };
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY + spacing,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.victory_screen", victoryModeText),
            button -> {
                // Cycle through modes
                ModConfig.VictoryScreenMode currentMode = config.getVictoryScreenMode();
                ModConfig.VictoryScreenMode nextMode = switch (currentMode) {
                    case SHOW -> ModConfig.VictoryScreenMode.HIDE;
                    case HIDE -> ModConfig.VictoryScreenMode.SHOW_AFTER_10_SECONDS;
                    case SHOW_AFTER_10_SECONDS -> ModConfig.VictoryScreenMode.SHOW;
                };
                config.setVictoryScreenMode(nextMode);
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        // Ask for custom seed button
        spacing += 30;
        String seedToggleText = config.isAskForCustomSeed() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY + spacing,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.ask_custom_seed", seedToggleText),
            button -> {
                config.setAskForCustomSeed(!config.isAskForCustomSeed());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        // Back button (aligned with main menu, skip animation on refresh)
        addDrawableChild(new StyledButton2(
            centerX - 50, height - 30,
            100, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f, isRefreshing
        ));
    }
    
    private void refreshScreen() {
        MinecraftClient.getInstance().setScreen(new SpeedrunSettingsScreen(parent, true));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
    
}

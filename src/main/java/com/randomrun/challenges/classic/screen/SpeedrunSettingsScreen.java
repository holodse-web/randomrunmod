package com.randomrun.challenges.classic.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
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
        int startY = 50;
        int spacing = 25;
        
        // Переключатель недоступных предметов
        String toggleText = config.isAllowUnobtainableItems() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.unobtainable_items", toggleText),
            button -> {
                config.setAllowUnobtainableItems(!config.isAllowUnobtainableItems());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing // Пропустить анимацию, если обновление
        ));
        
        startY += spacing;
        
        // Кнопка режима экрана победы
        String victoryModeText = switch (config.getVictoryScreenMode()) {
            case SHOW -> "§a" + Text.translatable("randomrun.victory_screen.show").getString();
            case HIDE -> "§c" + Text.translatable("randomrun.victory_screen.hide").getString();
            case SHOW_AFTER_10_SECONDS -> "§e" + Text.translatable("randomrun.victory_screen.show_after_10").getString();
        };
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.victory_screen", victoryModeText),
            button -> {
                // Переключение режимов
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
        
        startY += spacing;
        
        // Кнопка запроса кастомного сида
        String seedToggleText = config.isAskForCustomSeed() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.ask_custom_seed", seedToggleText),
            button -> {
                boolean newState = !config.isAskForCustomSeed();
                config.setAskForCustomSeed(newState);
                RandomRunMod.getInstance().saveConfig();
                
                if (newState) {
                    MinecraftClient.getInstance().getToastManager().add(new net.minecraft.client.toast.SystemToast(
                        net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION, 
                        Text.translatable("randomrun.settings.seed_warning.title"), 
                        Text.translatable("randomrun.settings.seed_warning.desc")
                    ));
                }
                
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));

        startY += spacing;

        String deleteWorldsText = config.isDeleteWorldsAfterSpeedrun()
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.delete_worlds", deleteWorldsText),
            button -> {
                config.setDeleteWorldsAfterSpeedrun(!config.isDeleteWorldsAfterSpeedrun());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        startY += spacing;

        // Кнопка режима Хардкор
        String hardcoreText = config.isHardcoreModeEnabled()
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.hardcore_mode", hardcoreText),
            button -> {
                config.setHardcoreModeEnabled(!config.isHardcoreModeEnabled());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        // Кнопка назад (выровнена с главным меню, пропуск анимации при обновлении)
        addDrawableChild(new ButtonDefault(
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
        
        // Заголовок
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
    
}

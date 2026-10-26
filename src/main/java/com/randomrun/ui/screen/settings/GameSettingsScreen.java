package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.mojang.blaze3d.platform.InputConstants;

public class GameSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    
    private static final int WIDGET_WIDTH = 400;
    private static final int WIDGET_HEIGHT = 250;
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    
    private static final long FADE_DURATION = 300;
    private static final int SHADOW_SIZE = 8;
    private static final int BORDER_THICKNESS = 2;
    private static final int GRADIENT_LENGTH = 30;
    
    private static final int COLOR_PANEL = 0x150030;
    private static final int COLOR_BORDER = 0x6A0DAD;
    private static final int COLOR_WHITE = 0xFFFFFF;
    
    private long screenOpenTime = 0;
    private boolean closing = false;
    private long closeStartTime = 0;
    
    public GameSettingsScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.speedrun_settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
    }
    
    @Override
    protected void init() {
        super.init();
        screenOpenTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        int widgetY = (height - WIDGET_HEIGHT) / 2;
        int startY = widgetY + 50;
        int buttonIndex = 0;
        
        addVictoryScreenButton(centerX, startY + (BUTTON_SPACING * buttonIndex++));
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, widgetY + WIDGET_HEIGHT - 40,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> startClosing(),
            buttonIndex, 0.35f
        ));
    }
    
    private void addSettingButton(int centerX, int y, String translationKey, boolean currentState, Runnable onToggle) {
        String stateText = currentState 
            ? "\u00A7a" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.enabled").getString()
            : "\u00A7c" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.disabled").getString();
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text(translationKey, stateText),
            button -> {
                onToggle.run();
                updateButtons();
            },
            0, 0.1f
        ));
    }
    
    private void addVictoryScreenButton(int centerX, int y) {
        ModConfig.VictoryScreenMode mode = config.getVictoryScreenMode();
        String modeText = switch (mode) {
            case SHOW -> "\u00A7a" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory_screen.show").getString();
            case HIDE -> "\u00A7c" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory_screen.hide").getString();
            case SHOW_AFTER_10_SECONDS -> "\u00A7e" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory_screen.show_after_10").getString();
        };
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.victory_screen", modeText),
            button -> {
                ModConfig.VictoryScreenMode nextMode = switch (config.getVictoryScreenMode()) {
                    case SHOW -> ModConfig.VictoryScreenMode.HIDE;
                    case HIDE -> ModConfig.VictoryScreenMode.SHOW_AFTER_10_SECONDS;
                    case SHOW_AFTER_10_SECONDS -> ModConfig.VictoryScreenMode.SHOW;
                };
                config.setVictoryScreenMode(nextMode);
                saveAndLog("Экран победы: " + nextMode);
                updateButtons();
            },
            0, 0.15f
        ));
    }
    
    private void saveAndLog(String message) {
        RandomRunMod.getInstance().saveConfig();
    }
    
    private void updateButtons() {
        clearWidgets();
        init();
    }
    
    private void startClosing() {
        closing = true;
        closeStartTime = System.currentTimeMillis();
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (closing) {
            long elapsed = System.currentTimeMillis() - closeStartTime;
            if (elapsed >= FADE_DURATION && minecraft != null) {
                minecraft.gui.setScreen(parent);
            }
        }
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            startClosing();
            return true;
        }
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        
        float fadeProgress = calculateFadeProgress();
        
        int blurAlpha = (int) (180 * fadeProgress);
        context.fill(0, 0, width, height, (blurAlpha << 24));
        
        extractWidgetRenderState(context, fadeProgress, delta);
    }
    
    private float calculateFadeProgress() {
        if (closing) {
            long elapsed = System.currentTimeMillis() - closeStartTime;
            return 1.0f - Math.min(1.0f, elapsed / (float) FADE_DURATION);
        } else {
            long elapsed = System.currentTimeMillis() - screenOpenTime;
            return Math.min(1.0f, elapsed / (float) FADE_DURATION);
        }
    }
    
    private void extractWidgetRenderState(GuiGraphicsExtractor context, float fadeProgress, float delta) {
        int widgetX = (width - WIDGET_WIDTH) / 2;
        int widgetY = (height - WIDGET_HEIGHT) / 2;
        
        renderShadow(context, widgetX, widgetY, fadeProgress);
        renderPanel(context, widgetX, widgetY, fadeProgress);
        renderBorders(context, widgetX, widgetY, fadeProgress, delta);
        renderTitle(context, widgetY, fadeProgress);
    }
    
    private void renderShadow(GuiGraphicsExtractor context, int x, int y, float fadeProgress) {
        for (int i = 0; i < SHADOW_SIZE; i++) {
            float progress = (float) i / SHADOW_SIZE;
            int offset = (int) (SHADOW_SIZE * progress);
            int alpha = (int) (40 * (1.0f - progress * progress) * fadeProgress);
            context.fill(x - offset, y - offset, 
                        x + WIDGET_WIDTH + offset, y + WIDGET_HEIGHT + offset, 
                        (alpha << 24));
        }
    }
    
    private void renderPanel(GuiGraphicsExtractor context, int x, int y, float fadeProgress) {
        int panelAlpha = (int) (0xD0 * fadeProgress);
        int panelColor = (panelAlpha << 24) | COLOR_PANEL;
        context.fill(x, y, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, panelColor);
    }
    
    private void renderBorders(GuiGraphicsExtractor context, int x, int y, float fadeProgress, float delta) {
        int borderAlpha = (int) (255 * fadeProgress);
        int borderColor = (borderAlpha << 24) | COLOR_BORDER;
        
        context.fill(x, y, x + WIDGET_WIDTH, y + BORDER_THICKNESS, borderColor);
        context.fill(x, y + WIDGET_HEIGHT - BORDER_THICKNESS, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, borderColor);
        context.fill(x, y, x + BORDER_THICKNESS, y + WIDGET_HEIGHT, borderColor);
        context.fill(x + WIDGET_WIDTH - BORDER_THICKNESS, y, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, borderColor);
        
        if (config.isAnimationsEnabled()) {
            renderAnimatedBorder(context, x, y, fadeProgress);
        }
    }
    
    private void renderAnimatedBorder(GuiGraphicsExtractor context, int x, int y, float fadeProgress) {
        long time = System.currentTimeMillis();
        float animationProgress = ((time % 5000) / 5000.0f);
        
        int perimeter = 2 * (WIDGET_WIDTH + WIDGET_HEIGHT);
        float currentPos = animationProgress * perimeter;
        
        for (int i = 0; i < WIDGET_WIDTH; i++) {
            float dist = getCircularDistance(i, currentPos, perimeter);
            if (dist < GRADIENT_LENGTH) {
                int alpha = (int) (255 * (1.0f - (dist / GRADIENT_LENGTH)) * fadeProgress);
                context.fill(x + i, y, x + i + 1, y + BORDER_THICKNESS, (alpha << 24) | COLOR_WHITE);
            }
        }
        
        int rightStart = WIDGET_WIDTH;
        for (int i = 0; i < WIDGET_HEIGHT; i++) {
            float dist = getCircularDistance(rightStart + i, currentPos, perimeter);
            if (dist < GRADIENT_LENGTH) {
                int alpha = (int) (255 * (1.0f - (dist / GRADIENT_LENGTH)) * fadeProgress);
                context.fill(x + WIDGET_WIDTH - BORDER_THICKNESS, y + i, x + WIDGET_WIDTH, y + i + 1, (alpha << 24) | COLOR_WHITE);
            }
        }
        
        int bottomStart = WIDGET_WIDTH + WIDGET_HEIGHT;
        for (int i = 0; i < WIDGET_WIDTH; i++) {
            float dist = getCircularDistance(bottomStart + i, currentPos, perimeter);
            if (dist < GRADIENT_LENGTH) {
                int alpha = (int) (255 * (1.0f - (dist / GRADIENT_LENGTH)) * fadeProgress);
                context.fill(x + WIDGET_WIDTH - i - 1, y + WIDGET_HEIGHT - BORDER_THICKNESS, 
                           x + WIDGET_WIDTH - i, y + WIDGET_HEIGHT, (alpha << 24) | COLOR_WHITE);
            }
        }
        
        int leftStart = 2 * WIDGET_WIDTH + WIDGET_HEIGHT;
        for (int i = 0; i < WIDGET_HEIGHT; i++) {
            float dist = getCircularDistance(leftStart + i, currentPos, perimeter);
            if (dist < GRADIENT_LENGTH) {
                int alpha = (int) (255 * (1.0f - (dist / GRADIENT_LENGTH)) * fadeProgress);
                context.fill(x, y + WIDGET_HEIGHT - i - 1, x + BORDER_THICKNESS, y + WIDGET_HEIGHT - i, (alpha << 24) | COLOR_WHITE);
            }
        }
    }
    
    private float getCircularDistance(int pos, float currentPos, int perimeter) {
        float dist = Math.abs(pos - currentPos);
        if (dist > perimeter / 2) {
            dist = perimeter - dist;
        }
        return dist;
    }
    
    private void renderTitle(GuiGraphicsExtractor context, int widgetY, float fadeProgress) {
        int titleAlpha = (int) (255 * fadeProgress);
        int titleColor = (titleAlpha << 24) | COLOR_WHITE;
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, widgetY + 10, titleColor);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void onClose() {
        startClosing();
    }
}

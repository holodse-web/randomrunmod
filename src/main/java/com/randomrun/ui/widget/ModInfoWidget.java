package com.randomrun.ui.widget;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class ModInfoWidget {
    private static final String TELEGRAM_URL = "https://t.me/randomrunmod";
    private static final String VERSION = "26.7";
    
    private final int screenWidth;
    private final int screenHeight;
    private final TextRenderer textRenderer;
    
    private float telegramAnimationProgress = 0f;
    private boolean telegramHovered = false;
    
    public ModInfoWidget(int screenWidth, int screenHeight, TextRenderer textRenderer) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.textRenderer = textRenderer;
    }
    
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderVersion(context);
        // renderAntiCheatStatus(context); // Отключено
        renderTelegramLink(context, mouseX, mouseY, delta);
    }
    
    private void renderVersion(DrawContext context) {
        int versionWidth = textRenderer.getWidth(VERSION);
        int x = screenWidth - versionWidth - 10;
        int y = screenHeight - 20; // Снизу-справа
        
        context.drawTextWithShadow(textRenderer, VERSION, x, y, 0xAAAAAA);
    }
    
    private void renderTelegramLink(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = 10;
        int y = screenHeight - 20; // Снизу-слева
        
        String baseText = "Telegram";
        String fullText = TELEGRAM_URL;
        
        int baseWidth = textRenderer.getWidth(baseText);
        int fullWidth = textRenderer.getWidth(fullText);
        
        telegramHovered = mouseX >= x && mouseX <= x + Math.max(baseWidth, fullWidth) &&
                         mouseY >= y && mouseY <= y + textRenderer.fontHeight;
        
        if (telegramHovered) {
            telegramAnimationProgress = Math.min(1.0f, telegramAnimationProgress + delta * 0.2f);
        } else {
            telegramAnimationProgress = Math.max(0f, telegramAnimationProgress - delta * 0.2f);
        }
        
        if (telegramAnimationProgress < 0.01f) {
            context.drawTextWithShadow(textRenderer, baseText, x, y, 0x888888);
        } else {
            int linkColor = 0x00AAFF;
            int currentWidth = (int) (baseWidth + (fullWidth - baseWidth) * telegramAnimationProgress);
            
            String displayText;
            if (telegramAnimationProgress >= 0.99f) {
                displayText = fullText;
            } else {
                float charProgress = telegramAnimationProgress * fullText.length();
                int charCount = Math.min(fullText.length(), (int) charProgress + 1);
                displayText = fullText.substring(0, charCount);
            }
            
            context.drawTextWithShadow(textRenderer, displayText, x, y, linkColor);
            
            if (telegramAnimationProgress > 0.5f) {
                int underlineAlpha = (int) ((telegramAnimationProgress - 0.5f) * 2 * 255);
                int underlineColor = (underlineAlpha << 24) | (linkColor & 0xFFFFFF);
                context.fill(x, y + textRenderer.fontHeight, x + currentWidth, y + textRenderer.fontHeight + 1, underlineColor);
            }
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!telegramHovered || button != 0) {
            return false;
        }
        
        try {
            // Используем Minecraft API для открытия ссылок
            net.minecraft.util.Util.getOperatingSystem().open(TELEGRAM_URL);
            RandomRunMod.LOGGER.info("Открытие ссылки Telegram: " + TELEGRAM_URL);
            return true;
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Не удалось открыть ссылку Telegram", e);
            // Fallback на Desktop API
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(TELEGRAM_URL));
                return true;
            } catch (Exception e2) {
                RandomRunMod.LOGGER.error("Резервный метод также не сработал", e2);
                return false;
            }
        }
    }
}

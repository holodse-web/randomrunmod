package com.randomrun.ui.screen.settings;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AnimatedSettingsScreen extends AbstractRandomRunScreen {
    protected static final int WIDGET_WIDTH = 400;
    protected static final int WIDGET_HEIGHT = 250;
    protected static final long FADE_DURATION = 300;
    
    
    protected final Screen parent;
    protected final boolean isRefreshing;
    
    protected long screenOpenTime = 0;
    protected boolean closing = false;
    protected long closeStartTime = 0;
    
    protected AnimatedSettingsScreen(Screen parent, boolean isRefreshing, Component title) {
        super(title);
        this.parent = parent;
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        screenOpenTime = System.currentTimeMillis();
        initContent();
    }
    
    protected abstract void initContent();
    
    protected void startClosing() {
        closing = true;
        closeStartTime = System.currentTimeMillis();
    }
    
    protected int getWidgetX() {
        return (width - widgetWidth()) / 2;
    }

    protected int getWidgetY() {
        return Math.max(10, (height - 36 - widgetHeight()) / 2);
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        
        float fadeProgress = getFadeProgress();
        if (closing && fadeProgress <= 0) {
            Minecraft.getInstance().gui.setScreen(parent);
            return;
        }
        
        int blurAlpha = (int) (180 * fadeProgress);
        context.fill(0, 0, width, height, blurAlpha << 24);
        
        extractWidgetRenderState(context, fadeProgress, delta);
    }
    
    protected float getFadeProgress() {
        if (closing) {
            long elapsed = System.currentTimeMillis() - closeStartTime;
            return 1.0f - Math.min(1.0f, elapsed / (float) FADE_DURATION);
        } else {
            long elapsed = System.currentTimeMillis() - screenOpenTime;
            return Math.min(1.0f, elapsed / (float) FADE_DURATION);
        }
    }
    
    protected int widgetWidth() {
        return Math.min(WIDGET_WIDTH, width - 20);
    }

    protected int widgetHeight() {
        return Math.min(WIDGET_HEIGHT - 80, height - 50);
    }

    protected void extractWidgetRenderState(GuiGraphicsExtractor context, float fadeProgress, float delta) {
        int x = getWidgetX(), y = getWidgetY();
        int right = x + widgetWidth(), bottom = y + widgetHeight();
        com.randomrun.ui.util.Ui.panel(context, x, y, right, bottom, fadeProgress);
        com.randomrun.ui.util.Ui.glowBorder(context, x, y, right, bottom, 2, 0xFFFFFF);
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, y + 10, com.randomrun.ui.util.Ui.argb(0xFFFFFF, fadeProgress));
        com.randomrun.ui.util.Ui.divider(context, x + 12, right - 12, y + 24, com.randomrun.ui.util.Theme.current().border, fadeProgress);
        renderWidgetContent(context, x, y, fadeProgress);
    }

    protected void renderWidgetContent(GuiGraphicsExtractor context, int widgetX, int widgetY, float fadeProgress) {
    }

    protected int rowX() {
        return width / 2 - rowWidth() / 2;
    }

    protected int rowWidth() {
        return Math.min(320, widgetWidth() - 30);
    }

    protected int rowY(int index) {
        return getWidgetY() + 34 + index * 24;
    }

    protected void addBackButton(int order) {
        addRenderableWidget(new com.randomrun.ui.widget.styled.Buttons.ButtonDefault(width / 2 - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"), button -> startClosing(), order, 0.2f, isRefreshing));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            startClosing();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        startClosing();
    }
}

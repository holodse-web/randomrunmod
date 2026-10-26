package com.randomrun.ui.screen.main;

import com.randomrun.ui.background.BackgroundRenderer;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.overlay.ModInfoWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractRandomRunScreen extends Screen {
    protected ModInfoWidget modInfoWidget;
    
    private int savedGuiScale = -1;
    private boolean shouldRestoreScale = false;
    private static boolean isApplyingGuiScale = false;
    
    private static int calculateOptimalGuiScale(Minecraft client) {
        if (client == null || client.getWindow() == null) return 3;
        
        int width = client.getWindow().getScreenWidth();
        int height = client.getWindow().getScreenHeight();
        
        if (width >= 3840 || height >= 2160) {
            return 2;
        }
        else if (width >= 2560 || height >= 1440) {
            return 2;
        }
        else {
            return 3;
        }
    }
    
    protected AbstractRandomRunScreen(Component title) {
        super(title);
    }
    
    @Override
    protected void init() {
        super.init();
        setupGuiScale();
        initWidgets();
    }
    
    private void setupGuiScale() {
        if (isApplyingGuiScale) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        
        int optimalScale = calculateOptimalGuiScale(client);
        int currentScale = client.options.guiScale().get();
        
        if (currentScale != optimalScale) {
            savedGuiScale = currentScale;
            shouldRestoreScale = true;
            applyGuiScale(optimalScale);
        }
    }
    
    private void initWidgets() {
        GlobalParticleSystem.getInstance().updateScreenSize(width, height);
        modInfoWidget = new ModInfoWidget(width, height, font);
    }
    
    protected boolean shouldEnableAfk() {
        return false;
    }
    
    private void applyGuiScale(int scale) {
        if (isApplyingGuiScale) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        
        isApplyingGuiScale = true;
        try {
            client.options.guiScale().set(scale);
            client.getWindow().calculateScale(scale, client.isEnforceUnicode());
            client.resizeGui();
        } finally {
            isApplyingGuiScale = false;
        }
    }
    
    @Override
    public void removed() {
        super.removed();
        restoreGuiScale();
    }
    
    private void restoreGuiScale() {
        if (!shouldRestoreScale || savedGuiScale == -1) return;
        
        applyGuiScale(savedGuiScale);
        shouldRestoreScale = false;
        savedGuiScale = -1;
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        BackgroundRenderer.getInstance().extractBackground(context, mouseX, mouseY, delta);
        
        GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
        particles.update(delta);
        particles.extractRenderState(context);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        if (modInfoWidget != null) {
            modInfoWidget.extractRenderState(context, mouseX, mouseY, delta);
        }
        
        com.randomrun.ui.widget.overlay.CustomNotification.renderOverlay(context);
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        GlobalParticleSystem.getInstance().updateMousePosition((int) mouseX, (int) mouseY);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (modInfoWidget != null && modInfoWidget.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            return true;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
}

package com.randomrun.ui.screen.info;

import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.ui.widget.GlobalParticleSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ModBlockScreen extends Screen {
    private static final int COLOR_BG = 0xFF0f0518;
    private static final int COLOR_PANEL_BG = 0xE0101020;
    private static final int COLOR_BORDER = 0xFFFF6B35;
    private static final int COLOR_TITLE = 0xFFFF6B35;
    private static final int COLOR_WARNING = 0xFFFFAA00;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_GRAY = 0xFF888888;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("randomrun", "textures/gui/logo.png");
    private static final int LOGO_WIDTH = 128;
    private static final int LOGO_HEIGHT = 32;

    private final Screen parent;
    private final List<String> suspiciousMods;
    private final List<String> suspiciousPacks;

    public ModBlockScreen(Screen parent) {
        super(Component.literal("Предупреждение о модах"));
        this.parent = parent;
        ModScannerService scanner = ModScannerService.getInstance();
        this.suspiciousMods = scanner.getSuspiciousMods();
        this.suspiciousPacks = scanner.getSuspiciousResourcePacks();
    }

    @Override
    protected void init() {
        super.init();
        
        int buttonY = height - 50;
        int buttonWidth = 200;
        int buttonHeight = 20;
        
        addRenderableWidget(new com.randomrun.ui.widget.styled.ButtonMenu(
            width / 2 - buttonWidth - 5, buttonY, buttonWidth, buttonHeight,
            Component.literal("Продолжить (Untrusted)"),
            button -> Minecraft.getInstance().gui.setScreen(parent),
            6, 0.35f
        ));
        
        addRenderableWidget(new com.randomrun.ui.widget.styled.ButtonMenu(
            width / 2 + 5, buttonY, buttonWidth, buttonHeight,
            Component.literal("Выйти"),
            button -> Minecraft.getInstance().gui.setScreen(parent),
            6, 0.35f
        ));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, COLOR_BG);
        
        GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
        particles.updateScreenSize(width, height);
        particles.updateMousePosition(mouseX, mouseY);
        particles.update(delta);
        particles.extractRenderState(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int panelWidth = 500;
        int panelHeight = 400;
        int panelLeft = centerX - panelWidth / 2;
        int panelTop = height / 2 - panelHeight / 2 - 20;
        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        
        renderPanel(context, panelLeft, panelTop, panelRight, panelBottom);
        
        int logoX = centerX - LOGO_WIDTH / 2;
        int logoY = panelTop + 15;
        renderLogo(context, logoX, logoY);
        
        int y = logoY + LOGO_HEIGHT + 20;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font,
            Component.literal("§6§l⚠ ПРЕДУПРЕЖДЕНИЕ ⚠"), centerX, y, COLOR_TITLE);
        y += 25;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font,
            Component.literal("§eОбнаружены подозрительные моды или ресурспаки"), centerX, y, COLOR_WARNING);
        y += 20;
        
        drawWrappedText(context, "§7В режиме Untrusted:", centerX, y, panelWidth - 40);
        y += 12;
        drawWrappedText(context, "§c• Trust Score не будет начисляться", centerX, y, panelWidth - 40);
        y += 12;
        drawWrappedText(context, "§c• Результаты не попадут в таблицу лидеров", centerX, y, panelWidth - 40);
        y += 12;
        drawWrappedText(context, "§c• Некоторые функции могут быть недоступны", centerX, y, panelWidth - 40);
        y += 20;

        if (!suspiciousMods.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font,
                Component.literal("§6Подозрительные моды:"), centerX, y, COLOR_WARNING);
            y += 14;

            int maxDisplay = Math.min(suspiciousMods.size(), 5);
            for (int i = 0; i < maxDisplay; i++) {
                String mod = suspiciousMods.get(i);
                if (mod.length() > 50) mod = mod.substring(0, 47) + "...";
                com.randomrun.ui.util.GuiText.centeredText(context, font,
                    Component.literal("§f• " + mod), centerX, y, COLOR_WHITE);
                y += 11;
            }
            
            if (suspiciousMods.size() > 5) {
                com.randomrun.ui.util.GuiText.centeredText(context, font,
                    Component.literal("§7... и ещё " + (suspiciousMods.size() - 5)), centerX, y, COLOR_GRAY);
                y += 11;
            }
            y += 6;
        }

        if (!suspiciousPacks.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font,
                Component.literal("§6Подозрительные ресурспаки:"), centerX, y, COLOR_WARNING);
            y += 14;

            int maxDisplay = Math.min(suspiciousPacks.size(), 3);
            for (int i = 0; i < maxDisplay; i++) {
                String pack = suspiciousPacks.get(i);
                if (pack.length() > 50) pack = pack.substring(0, 47) + "...";
                com.randomrun.ui.util.GuiText.centeredText(context, font,
                    Component.literal("§f• " + pack), centerX, y, COLOR_WHITE);
                y += 11;
            }
            
            if (suspiciousPacks.size() > 3) {
                com.randomrun.ui.util.GuiText.centeredText(context, font,
                    Component.literal("§7... и ещё " + (suspiciousPacks.size() - 3)), centerX, y, COLOR_GRAY);
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    private void renderPanel(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, COLOR_PANEL_BG);
        
        renderAnimatedBorder(context, left, top, right, bottom);
    }
    
    private void renderLogo(GuiGraphicsExtractor context, int x, int y) {
        context.blit(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            LOGO_TEXTURE, 
            x, y, 
            0, 0, 
            LOGO_WIDTH, LOGO_HEIGHT, 
            LOGO_WIDTH, LOGO_HEIGHT
        );
    }
    
    private void renderAnimatedBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        int borderWidth = 2;
        
        int color1 = COLOR_BORDER;
        int color2 = 0xFF8B4513;
        
        context.fillGradient(left, top, right, top + borderWidth, color1, color2);
        context.fillGradient(left, bottom - borderWidth, right, bottom, color2, color1);
        context.fillGradient(left, top, left + borderWidth, bottom, color1, color2);
        context.fillGradient(right - borderWidth, top, right, bottom, color2, color1);
    }
    
    private void drawWrappedText(GuiGraphicsExtractor context, String text, int centerX, int y, int maxWidth) {
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(text), centerX, y, COLOR_TEXT);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

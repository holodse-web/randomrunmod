package com.randomrun.ui.screen.endgame;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DrawScreen extends AbstractRandomRunScreen {
    private final Item targetItem;
    private final String reason;
    
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    private static final int GRADIENT_TOP = 0xFF141000;
    private static final int GRADIENT_MID = 0xFF886600;
    private static final int GRADIENT_BOTTOM = 0xFF332200;
    private static final int BUTTON_COLOR_DARK = 0xAA8800;
    private static final int BUTTON_COLOR_LIGHT = 0xFFCC00;
    
    public DrawScreen(Item item, long time, String reason) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.draw.title"));
        this.targetItem = item;
        this.reason = reason;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        if (minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        }
        
        initParticles();
        initButtons();
    }
    
    private void initParticles() {
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        particleSystem.setYellowMode(true);
    }
    
    private void initButtons() {
        int centerX = width / 2;
        
        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 60, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.to_menu"),
            button -> handleExit()
        ).setColors(BUTTON_COLOR_DARK, BUTTON_COLOR_LIGHT));
    }
    
    private void handleExit() {
        GlobalParticleSystem.getInstance().clearParticles();
        
        if (minecraft.level != null) {
            minecraft.level.disconnect(net.minecraft.network.chat.Component.translatable("menu.returnToMenu"));
        }
        minecraft.disconnectWithProgressScreen();
        
        RandomRunMod.getInstance().getRunDataManager().cancelRun();
        minecraft.gui.setScreen(new MainModScreen(new TitleScreen()));
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height / 2, GRADIENT_TOP, GRADIENT_MID);
        context.fillGradient(0, height / 2, width, height, GRADIENT_MID, GRADIENT_BOTTOM);
        
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.updateScreenSize(width, height);
        particleSystem.updateMousePosition(mouseX, mouseY);
        particleSystem.update(delta);
        particleSystem.extractRenderState(context);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateAnimations(delta);
        extractBackground(context, mouseX, mouseY, delta);
        renderTitle(context);
        render3DItem(context, width / 2, height / 2 - 30);
        renderInfo(context);
        renderStatsPanel(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    private void updateAnimations(float delta) {
        long elapsed = System.currentTimeMillis() - openTime;
        rotationY += delta * 2f;
        levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
    }
    
    private void renderTitle(GuiGraphicsExtractor context) {
        long elapsed = System.currentTimeMillis() - openTime;
        float scale = 1.0f + (float) Math.sin(elapsed / 300.0) * 0.05f;
        
        context.pose().pushMatrix();
        context.pose().translate(width / 2f, 40);
        context.pose().scale(scale * 2, scale * 2);
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.draw.title_label"), 
            0, 0, 0xFFAA00);
        context.pose().popMatrix();
    }
    
    private void renderInfo(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int centerY = height / 2;
        
        String itemName = getTargetName();
        com.randomrun.ui.util.GuiText.centeredText(context, font, "§6" + itemName, centerX, centerY + 50, 0xFFAA00);
        
        if (reason != null && !reason.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, "§f" + reason, centerX, centerY + 80, 0xFFFFFF);
        }
    }
    
    private void renderStatsPanel(GuiGraphicsExtractor context) {
        int panelWidth = 150;
        int panelX = width - panelWidth - 10;
        int panelY = 30;
        int lineHeight = 13;
        int centerX = panelX + panelWidth / 2;
        int y = panelY;
        
        com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
        boolean online = com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isOnlineMode();
        
        int trustChange = com.randomrun.core.profile.PlayerProfile.BONUS_CLEAN_RUN;
        
        int lines = 2;
        if (online && profile != null) lines += 4;
        int panelHeight = lines * lineHeight + 20;
        
        context.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight, 0xE0000000);
        int borderColor = 0xFFAA8800;
        context.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY - 4, borderColor);
        context.fill(panelX - 5, panelY + panelHeight - 1, panelX + panelWidth + 5, panelY + panelHeight, borderColor);
        context.fill(panelX - 5, panelY - 5, panelX - 4, panelY + panelHeight, borderColor);
        context.fill(panelX + panelWidth + 4, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight, borderColor);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§l§6Статистика"), centerX, y, 0xFFAA00);
        y += lineHeight + 2;
        
        context.fill(panelX, y, panelX + panelWidth, y + 1, 0x66AA8800);
        y += 5;
        
        if (online && profile != null) {
            int trustScore = profile.getTrustScore();
            int tsColor = getTrustScoreColor(trustScore);
            com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7Trust: §f" + trustScore), centerX, y, tsColor);
            y += lineHeight;
            
            com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§a+" + trustChange + " §7(забег)"), centerX, y, 0x55FF55);
            y += lineHeight;
            
            boolean canSubmit = profile.canSubmitToLeaderboard();
            if (canSubmit) {
                com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§aВ лидерборде"), centerX, y, 0x55FF55);
            } else {
                com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§cНе в лидерборде"), centerX, y, 0xFF5555);
                y += lineHeight;
                if (trustScore < com.randomrun.core.profile.PlayerProfile.LEADERBOARD_THRESHOLD) {
                    com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7(низкий trust)"), centerX, y, 0xAAAAAA);
                }
            }
        }
    }
    
    private int getTrustScoreColor(int score) {
        if (score >= 150) return 0x55FF55;
        if (score >= 100) return 0xFFFF55;
        if (score >= 75) return 0xFFAA00;
        if (score >= 50) return 0xFF8800;
        return 0xFF5555;
    }
    
    private String getTargetName() {
        return net.minecraft.network.chat.Component.translatable(targetItem.getDescriptionId()).getString();
    }
    
    private void render3DItem(GuiGraphicsExtractor context, int x, int y) {
        com.randomrun.ui.util.ItemPreviewRenderer.extract(context, getItemStack(), x, y + levitationOffset,
            80f, 0, rotationY, ItemDisplayContext.GUI);
    }
    
    private ItemStack getItemStack() {
        return com.randomrun.ui.util.PreviewItems.stack(targetItem);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void removed() {
        GlobalParticleSystem.getInstance().setYellowMode(false);
        super.removed();
    }
    
    @Override
    public void onClose() {
        GlobalParticleSystem.getInstance().setYellowMode(false);
        super.onClose();
    }
}

package com.randomrun.ui.screen.endgame;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.ButtonRed;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DefeatScreen extends AbstractRandomRunScreen {
    private final Item targetItem;
    private final long elapsedTime;
    private final String reason;
    private final boolean allowRetry;
    private final boolean wasBattle;
    private final boolean wasUpgrader;
    private final boolean wasRandomDrop;
    
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    private static final int GRADIENT_TOP = 0xFF140000;
    private static final int GRADIENT_MID = 0xFF8c1414;
    private static final int GRADIENT_BOTTOM = 0xFF2e1a1a;
    
    public DefeatScreen(Item item, long time, String reason) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.defeat.title"));
        this.targetItem = item;
        this.elapsedTime = time;
        this.reason = reason;
        this.wasBattle = com.randomrun.features.battle.manager.BattleManager.getInstance().isInBattle();
        this.allowRetry = !this.wasBattle;
        com.randomrun.core.session.RunSession session =
            com.randomrun.core.session.RunSessionManager.getInstance().getCurrentSession();
        this.wasUpgrader = !wasBattle && (session != null ? session.isUpgrader() : WorldCreator.isLastCreatedUpgrader());
        this.wasRandomDrop = session != null ? session.isRandomDrop() : WorldCreator.isLastCreatedRandomDrop();
    }

    public DefeatScreen(Item item, long time) {
        this(item, time, null);
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        if (minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 1.0f, 1.0f);
        }
        
        initParticles();
        initButtons();
    }
    
    private void initParticles() {
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        particleSystem.setRedMode(true);
    }
    
    private void initButtons() {
        int centerX = width / 2;
        
        if (allowRetry) {
            addRenderableWidget(new ButtonRed(
                centerX - 100, height - 60, 200, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.retry_speedrun"),
                button -> handleRetry()
            ));
        }
        
        int exitButtonY = allowRetry ? height - 35 : height - 60;
        addRenderableWidget(new ButtonRed(
            centerX - 100, exitButtonY, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.to_menu"),
            button -> handleExit()
        ));
    }
    
    private void handleRetry() {
        cleanupAndDisconnect();
        
        String seed = WorldCreator.getLastCreatedSeed();
        
        com.randomrun.features.challenges.classic.world.WorldCreationRequest request = 
            new com.randomrun.features.challenges.classic.world.WorldCreationRequest.Builder()
                .targetItem(targetItem)
                .upgrader(wasUpgrader)
                .randomDrop(wasRandomDrop)
                .seed(seed)
                .mode(com.randomrun.features.challenges.classic.world.WorldMode.STANDARD)
                .build();
        
        WorldCreator.getInstance().createWorld(request);
    }
    
    private void handleExit() {
        cleanupAndDisconnect();
        minecraft.gui.setScreen(new MainModScreen(new TitleScreen()));
    }
    
    private void cleanupAndDisconnect() {
        GlobalParticleSystem.getInstance().setRedMode(false);
        GlobalParticleSystem.getInstance().clearParticles();
        
        if (minecraft.level != null) {
            minecraft.level.disconnect(net.minecraft.network.chat.Component.translatable("menu.returnToMenu"));
        }
        minecraft.disconnectWithProgressScreen();
        
        RandomRunMod.getInstance().getRunDataManager().cancelRun();
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
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.defeat.title_label"), 
            0, 0, 0xFF5555);
        context.pose().popMatrix();
    }
    
    private void renderInfo(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int centerY = height / 2;
        
        String itemName = getTargetName();
        com.randomrun.ui.util.GuiText.centeredText(context, font, "\u00A7c" + itemName, centerX, centerY + 50, 0xFF5555);
        
        String timeStr = RunDataManager.formatTime(elapsedTime);
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.defeat.time", "\u00A7e" + timeStr), 
            centerX, centerY + 70, 0xFFFFFF);
        
        if (reason != null && !reason.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, "\u00A77" + reason, centerX, centerY + 90, 0xAAAAAA);
        }
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory.warning"), 
            centerX, height - 80, 0x888888);
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
        
        if (targetItem == null) return;
        String recordId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetItem).toString();
        com.randomrun.core.history.RunHistory history = com.randomrun.core.history.RunHistory.getInstance();
        java.util.List<com.randomrun.core.history.Run> records = history.getRecordsForTarget(recordId);
        
        int attempts = records.size();
        int victories = (int) records.stream()
            .filter(r -> r.getResult() == com.randomrun.core.history.RunResult.VICTORY).count();
        long bestTime = records.stream()
            .filter(r -> r.getResult() == com.randomrun.core.history.RunResult.VICTORY)
            .mapToLong(com.randomrun.core.history.Run::getDuration)
            .min().orElse(-1);
        float winRate = attempts > 0 ? (victories * 100f / attempts) : 0;
        
        int trustChange = com.randomrun.core.profile.PlayerProfile.BONUS_CLEAN_RUN;
        
        int lines = 5;
        if (bestTime > 0) lines++;
        if (online && profile != null) lines += 2;
        int panelHeight = lines * lineHeight + 20;
        
        context.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight, 0xE0000000);
        int borderColor = 0xFF8B2222;
        context.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY - 4, borderColor);
        context.fill(panelX - 5, panelY + panelHeight - 1, panelX + panelWidth + 5, panelY + panelHeight, borderColor);
        context.fill(panelX - 5, panelY - 5, panelX - 4, panelY + panelHeight, borderColor);
        context.fill(panelX + panelWidth + 4, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight, borderColor);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§l§cСтатистика"), centerX, y, 0xFF5555);
        y += lineHeight + 2;
        
        context.fill(panelX, y, panelX + panelWidth, y + 1, 0x668B2222);
        y += 5;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7Попытки: §f" + attempts), centerX, y, 0xFFFFFF);
        y += lineHeight;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7Победы: §a" + victories), centerX, y, 0xFFFFFF);
        y += lineHeight;
        
        String winRateStr = String.format("%.0f%%", winRate);
        com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7Винрейт: §e" + winRateStr), centerX, y, 0xFFFFFF);
        y += lineHeight;
        
        if (bestTime > 0) {
            String bestStr = RunDataManager.formatTime(bestTime);
            com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7Лучшее: §6" + bestStr), centerX, y, 0xFFFFFF);
            y += lineHeight;
        }
        
        if (online && profile != null) {
            y += 4;
            context.fill(panelX, y, panelX + panelWidth, y + 1, 0x668B2222);
            y += 5;
            
            int trustScore = profile.getTrustScore();
            int tsColor = getTrustScoreColor(trustScore);
            com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§7Trust: §f" + trustScore), centerX, y, tsColor);
            y += lineHeight;
            
            com.randomrun.ui.util.GuiText.centeredText(context, font, net.minecraft.network.chat.Component.literal("§a+" + trustChange + " §7(забег)"), centerX, y, 0x55FF55);
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
        return targetItem != null ? net.minecraft.network.chat.Component.translatable(targetItem.getDescriptionId()).getString() : "?";
    }
    
    private void render3DItem(GuiGraphicsExtractor context, int x, int y) {
        com.randomrun.ui.util.ItemPreviewRenderer.extract(context, getItemStack(), x, y + levitationOffset,
            120f, 0, rotationY, ItemDisplayContext.GUI);
    }
    
    private ItemStack getItemStack() {
        return targetItem != null ? com.randomrun.ui.util.PreviewItems.stack(targetItem) : ItemStack.EMPTY;
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) return true;
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }
    
    @Override
    public void onClose() {
        GlobalParticleSystem.getInstance().setRedMode(false);
        super.onClose();
    }
}

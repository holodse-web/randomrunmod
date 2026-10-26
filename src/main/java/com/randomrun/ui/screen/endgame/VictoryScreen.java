package com.randomrun.ui.screen.endgame;

import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.ui.widget.styled.ButtonGreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.core.history.Run;
import com.randomrun.infrastructure.data.RunDataManager;

public class VictoryScreen extends AbstractRandomRunScreen {
    private final Item completedItem;
    private final long completionTime;
    private final boolean allowRetry;
    private final String winnerName;
    private final boolean wasBattle;
    private final Run currentRun;
    
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    private static final int GRADIENT_TOP = 0xFF001400;
    private static final int GRADIENT_MID = 0xFF145514;
    private static final int GRADIENT_BOTTOM = 0xFF0a2e0a;
    
    public VictoryScreen(Item item, long time, String winnerName) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.victory.title"));
        this.completedItem = item;
        this.completionTime = time;
        this.winnerName = winnerName;
        this.wasBattle = BattleManager.getInstance().isInBattle();
        this.allowRetry = !this.wasBattle;
        
        String itemId = item != null ? BuiltInRegistries.ITEM.getKey(item).toString() : "minecraft:air";
        String seed = com.randomrun.features.challenges.classic.world.WorldCreator.getLastCreatedSeed();
        
        this.currentRun = new Run.Builder()
            .targetId(itemId)
            .duration(time)
            .timestamp(System.currentTimeMillis())
            .result(com.randomrun.core.history.RunResult.VICTORY)
            .customSeed(seed)
            .isBattle(wasBattle)
            .isHardcore(false)
            .isUpgrader(!wasBattle && WorldCreator.isLastCreatedUpgrader())
            .isRandomDrop(WorldCreator.isLastCreatedRandomDrop())
            .build();
    }
    
    public VictoryScreen(Run record) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.victory.title"));
        
        Identifier itemId = Identifier.tryParse(record.getTargetId());
        this.completedItem = itemId != null ? BuiltInRegistries.ITEM.getValue(itemId) : null;
        
        this.completionTime = record.getDuration();
        this.winnerName = Minecraft.getInstance().getUser().getName();
        this.wasBattle = record.isBattle();
        this.allowRetry = !this.wasBattle;
        this.currentRun = record;
    }

    public VictoryScreen(Item item, long time) {
        this(item, time, Minecraft.getInstance().getUser().getName());
    }
    
    public boolean wasBattle() {
        return wasBattle;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        initParticles();
        initButtons();
    }
    
    private void initParticles() {
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        particleSystem.setGreenMode(true);
    }
    
    private void initButtons() {
        int centerX = width / 2;
        
        if (allowRetry) {
            addRenderableWidget(new ButtonGreen(
                centerX - 100, height - 60, 200, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.retry_speedrun"),
                button -> handleRetry()
            ));
        }
        
        int exitButtonY = allowRetry ? height - 35 : height - 60;
        addRenderableWidget(new ButtonGreen(
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
                .targetItem(completedItem)
                .upgrader(currentRun != null && currentRun.isUpgrader())
                .randomDrop(currentRun != null && currentRun.isRandomDrop())
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
        GlobalParticleSystem.getInstance().setGreenMode(false);
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
        renderWinnerName(context);
        
        DisplayData displayData = prepareDisplayData();
        render3DItem(context, width / 2, height / 2 - 30, displayData.stack);
        renderInfo(context, displayData);
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
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.victory.title_label"), 
            0, 0, 0x55FF55);
        context.pose().popMatrix();
    }
    
    private void renderWinnerName(GuiGraphicsExtractor context) {
        if (wasBattle && winnerName != null) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal("Winner: " + winnerName), 
                width / 2, 70, 0xFFFF00);
        }
    }
    
    private DisplayData prepareDisplayData() {
        if (currentRun != null && currentRun.getTargetType() == com.randomrun.core.session.TargetType.MOB) {
            String targetId = currentRun.getTargetId();
            String[] parts = targetId.split(":");
            if (parts.length >= 2) {
                String mobId = parts[0] + ":" + parts[1];
                Identifier mobIdentifier = Identifier.tryParse(mobId);
                if (mobIdentifier != null) {
                    net.minecraft.world.entity.EntityType<?> entityType = 
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(mobIdentifier);
                    
                    Item spawnEgg = getSpawnEggForEntity(entityType);
                    if (spawnEgg != null && spawnEgg != net.minecraft.world.item.Items.AIR) {
                        ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(spawnEgg);
                        Component name = Component.literal("§6" + entityType.getDescription().getString());
                        return new DisplayData(stack, name, targetId);
                    }
                }
            }
            ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(net.minecraft.world.item.Items.BARRIER);
            Component name = Component.literal("§6" + targetId);
            return new DisplayData(stack, name, targetId);
        }
        
        if (completedItem == null) {
            return new DisplayData(ItemStack.EMPTY, Component.literal("§cНеизвестный предмет"), null);
        }
        ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(completedItem);
        Component name = Component.literal("§6" + net.minecraft.network.chat.Component.translatable(completedItem.getDescriptionId()).getString());
        String recordId = BuiltInRegistries.ITEM.getKey(completedItem).toString();
        
        return new DisplayData(stack, name, recordId);
    }
    
    private Item getSpawnEggForEntity(net.minecraft.world.entity.EntityType<?> entityType) {
        net.minecraft.world.item.Item spawnEgg = net.minecraft.world.item.SpawnEggItem.byId(entityType).map(net.minecraft.core.Holder::value).orElse(null);
        return spawnEgg != null ? spawnEgg : net.minecraft.world.item.Items.AIR;
    }
    
    private void renderInfo(GuiGraphicsExtractor context, DisplayData data) {
        int centerX = width / 2;
        int centerY = height / 2;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, data.name, centerX, centerY + 50, 0x006400);
        
        String timeStr = RunDataManager.formatTime(completionTime);
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory.time", "§e" + timeStr), 
            centerX, centerY + 70, 0xFFFFFF);
        
        if (data.recordId != null && isNewRecord(data.recordId)) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory.new_record"), 
                centerX, centerY + 90, 0xA8FFB5);
        } else if (data.recordId != null) {
            com.randomrun.core.history.RunHistory history = com.randomrun.core.history.RunHistory.getInstance();
            java.util.List<com.randomrun.core.history.Run> records = history.getRecordsForTarget(data.recordId);
            long bestTime = records.stream()
                .filter(r -> r.getResult() == com.randomrun.core.history.RunResult.VICTORY)
                .mapToLong(com.randomrun.core.history.Run::getDuration)
                .min().orElse(Long.MAX_VALUE);
            
            if (bestTime < completionTime && bestTime != Long.MAX_VALUE) {
                String bestTimeStr = RunDataManager.formatTime(bestTime);
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.victory.best_time", "§e" + bestTimeStr), 
                    centerX, centerY + 90, 0xFFFFFF);
            }
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
        
        com.randomrun.infrastructure.security.ModScannerService scanner = 
            com.randomrun.infrastructure.security.ModScannerService.getInstance();
        boolean isTrusted = scanner.isTrusted();
        
        String recordId = BuiltInRegistries.ITEM.getKey(completedItem).toString();
        com.randomrun.core.history.RunHistory history = com.randomrun.core.history.RunHistory.getInstance();
        java.util.List<com.randomrun.core.history.Run> records = history.getRecordsForTarget(recordId);
        records = records.stream().filter(record -> record.isUpgrader() == currentRun.isUpgrader()).toList();
        
        boolean currentRunCounted = records.stream()
            .anyMatch(r -> r.getDuration() == completionTime && r.getResult() == com.randomrun.core.history.RunResult.VICTORY);
        int extraAttempts = currentRunCounted ? 0 : 1;
        int extraVictories = currentRunCounted ? 0 : 1;
        
        int attempts = records.size() + extraAttempts;
        int victories = (int) records.stream()
            .filter(r -> r.getResult() == com.randomrun.core.history.RunResult.VICTORY).count() + extraVictories;
        long bestFromHistory = records.stream()
            .filter(r -> r.getResult() == com.randomrun.core.history.RunResult.VICTORY)
            .mapToLong(com.randomrun.core.history.Run::getDuration)
            .min().orElse(Long.MAX_VALUE);
        long bestTime = Math.min(bestFromHistory, completionTime);
        if (bestTime == Long.MAX_VALUE) bestTime = completionTime;
        float winRate = attempts > 0 ? (victories * 100f / attempts) : 0;
        
        int trustChange = isTrusted && !currentRun.isUpgrader() ? (com.randomrun.core.profile.PlayerProfile.BONUS_CLEAN_RUN 
            + (wasBattle ? com.randomrun.core.profile.PlayerProfile.BONUS_BATTLE_VICTORY 
                         : com.randomrun.core.profile.PlayerProfile.BONUS_VICTORY)) : 0;
        
        int lines = 5;
        if (bestTime > 0) lines++;
        if (online && profile != null) {
            lines += 4;
            String submissionReason = getSubmissionFailureReason(profile, isTrusted);
            if (submissionReason != null) {
                if (font.width(Component.literal(submissionReason)) > panelWidth - 10) {
                    lines += 2;
                } else {
                    lines += 1;
                }
            }
        }
        int panelHeight = lines * lineHeight + 20;
        
        context.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight, 0xE0000000);
        int borderColor = 0xFF228B22;
        context.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY - 4, borderColor);
        context.fill(panelX - 5, panelY + panelHeight - 1, panelX + panelWidth + 5, panelY + panelHeight, borderColor);
        context.fill(panelX - 5, panelY - 5, panelX - 4, panelY + panelHeight, borderColor);
        context.fill(panelX + panelWidth + 4, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight, borderColor);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(currentRun.isUpgrader() ? "§l§eRRM Upgrader" : "§l§aСтатистика"), centerX, y, 0x55FF55);
        y += lineHeight + 2;
        
        context.fill(panelX, y, panelX + panelWidth, y + 1, 0x66228B22);
        y += 5;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7Попытки: §f" + attempts), centerX, y, 0xFFFFFF);
        y += lineHeight;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7Победы: §a" + victories), centerX, y, 0xFFFFFF);
        y += lineHeight;
        
        String winRateStr = String.format("%.0f%%", winRate);
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7Винрейт: §e" + winRateStr), centerX, y, 0xFFFFFF);
        y += lineHeight;
        
        if (bestTime > 0) {
            String bestStr = RunDataManager.formatTime(bestTime);
            com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7Лучшее: §6" + bestStr), centerX, y, 0xFFFFFF);
            y += lineHeight;
        }
        
        if (online && profile != null) {
            y += 4;
            context.fill(panelX, y, panelX + panelWidth, y + 1, 0x66228B22);
            y += 5;
            
            int trustScore = profile.getTrustScore();
            int tsColor = getTrustScoreColor(trustScore);
            com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7Trust: §f" + trustScore), centerX, y, tsColor);
            y += lineHeight;
            
            if (isTrusted && trustChange > 0) {
                com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§a+" + trustChange + " §7(победа)"), centerX, y, 0x55FF55);
            } else if (!isTrusted) {
                com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§c+0 §7(untrusted)"), centerX, y, 0xFF5555);
            }
            y += lineHeight;
            
            String submissionReason = getSubmissionFailureReason(profile, isTrusted);
            if (submissionReason == null) {
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.submitted"), 
                    centerX, y, 0x55FF55);
            } else {
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.not_submitted"), 
                    centerX, y, 0xFF5555);
                y += lineHeight;
                
                Component reasonText = Component.literal("§7" + submissionReason);
                if (font.width(reasonText) > panelWidth - 10) {
                    String[] words = submissionReason.split(" ");
                    StringBuilder line1 = new StringBuilder();
                    StringBuilder line2 = new StringBuilder();
                    boolean firstLine = true;
                    
                    for (String word : words) {
                        String testLine = (firstLine ? line1 : line2).toString();
                        if (!testLine.isEmpty()) testLine += " ";
                        testLine += word;
                        
                        if (font.width(Component.literal(testLine)) > panelWidth - 10 && firstLine) {
                            firstLine = false;
                            line2.append(word);
                        } else {
                            if (firstLine) {
                                if (line1.length() > 0) line1.append(" ");
                                line1.append(word);
                            } else {
                                if (line2.length() > 0) line2.append(" ");
                                line2.append(word);
                            }
                        }
                    }
                    
                    com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7" + line1.toString()), centerX, y, 0xAAAAAA);
                    if (line2.length() > 0) {
                        y += lineHeight;
                        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal("§7" + line2.toString()), centerX, y, 0xAAAAAA);
                    }
                } else {
                    com.randomrun.ui.util.GuiText.centeredText(context, font, reasonText, centerX, y, 0xAAAAAA);
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
    
    private String getSubmissionFailureReason(com.randomrun.core.profile.PlayerProfile profile, boolean isTrusted) {
        if (currentRun != null && currentRun.isUpgrader()) return "RRM Upgrader: локальная история";
        
        if (!isTrusted) {
            return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.reason.untrusted").getString();
        }
        
        if (!profile.canSubmitToLeaderboard()) {
            return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.reason.low_trust", 
                profile.getTrustScore(), com.randomrun.core.profile.PlayerProfile.LEADERBOARD_THRESHOLD).getString();
        }
        
        if (currentRun != null && !currentRun.isEligibleForLeaderboard()) {
            if (currentRun.isRandomDrop()) {
                return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.reason.random_drop").getString();
            }
            if (!currentRun.isHardcore()) {
                return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.reason.not_hardcore").getString();
            }
            if (!currentRun.isBattle() && currentRun.getCustomSeed() != null) {
                return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.reason.custom_seed").getString();
            }
        }
        
        if (currentRun != null) {
            try {
                java.util.List<com.randomrun.features.leaderboard.LeaderboardEntry> currentTop10 = 
                    com.randomrun.features.leaderboard.LeaderboardService.getInstance()
                        .getTopRecords(currentRun.getTargetId(), 10)
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);
                
                if (currentTop10.size() >= 10) {
                    long tenthBestTime = currentTop10.get(9).getCompletionTime();
                    
                    if (currentRun.getDuration() >= tenthBestTime) {
                        return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.endgame.reason.not_top10", 
                            RunDataManager.formatTime(currentRun.getDuration()), RunDataManager.formatTime(tenthBestTime)).getString();
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.warn("[Victory] Не удалось проверить топ-10: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    private boolean isNewRecord(String recordId) {
        com.randomrun.core.history.RunHistory history = com.randomrun.core.history.RunHistory.getInstance();
        java.util.List<com.randomrun.core.history.Run> records = history.getRecordsForTarget(recordId);
        records = records.stream().filter(record -> record.isUpgrader() == currentRun.isUpgrader()).toList();
        
        if (records.size() <= 1) return false;
        
        for (com.randomrun.core.history.Run record : records) {
            if (record.getResult() == com.randomrun.core.history.RunResult.VICTORY && 
                record.getDuration() < completionTime) {
                return false;
            }
        }
        return true;
    }
    
    private void render3DItem(GuiGraphicsExtractor context, int x, int y, ItemStack stack) {
        com.randomrun.ui.util.ItemPreviewRenderer.extract(context, stack, x, y + levitationOffset,
            120f, 0, rotationY, ItemDisplayContext.GUI);
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
    public void onClose() {
        GlobalParticleSystem.getInstance().setGreenMode(false);
        super.onClose();
    }
    
    private static class DisplayData {
        final ItemStack stack;
        final Component name;
        final String recordId;
        
        DisplayData(ItemStack stack, Component name, String recordId) {
            this.stack = stack;
            this.name = name;
            this.recordId = recordId;
        }
    }
}

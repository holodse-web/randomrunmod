package com.randomrun.ui.screen.main;

import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.core.history.RunHistory;
import com.randomrun.core.history.Run;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

public class ResultsScreen extends AbstractRandomRunScreen {
    private static final int RESULTS_PER_PAGE = 8;
    private static final int RESULT_HEIGHT = 30;
    private static final int LIST_X = 20;
    private static final int LIST_Y = 85;
    
    private static final int INITIAL_LOAD_COUNT = 30;
    private static final int LOAD_MORE_COUNT = 30;
    
    private static final int COLOR_BG_PANEL = 0x80000000;
    private static final int COLOR_BG_STATS = 0xA0000000;
    private static final int COLOR_ACCENT = 0xFF6930c3;
    private static final int COLOR_HOVER = 0x40FFFFFF;
    private static final int COLOR_SCROLLBAR_BG = 0x40000000;
    private static final int COLOR_TEXT_GRAY = 0x888888;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFF;
    private static final int COLOR_TEXT_LIGHT_GRAY = 0xAAAAAA;
    private static final int COLOR_SUCCESS = 0x55FF55;
    private static final int COLOR_WARNING = 0xFFFF55;
    private static final int COLOR_DANGER = 0xFF5555;
    private static final int COLOR_INFO = 0x55FFFF;
    private static final int COLOR_STAT_LABEL = 0xAAAAFF;
    
    private static final long MAX_VALID_TIME = 359999000L;
    private static final int MAX_ITEM_NAME_LENGTH = 25;
    private static final int MAX_BEST_ITEM_NAME_LENGTH = 20;
    
    private static List<Run> cachedHistory = null;
    private static int loadedRecordsCount = 0;
    
    private TextFieldStyled searchField;
    private List<TargetSummary> filteredResults = new ArrayList<>();
    private List<TargetSummary> allResults = new ArrayList<>();
    private Map<String, String> itemNameCache = new HashMap<>();
    
    private Stats cachedStats = null;
    private boolean showingUpgrader = false;
    
    private int scrollOffset = 0;
    private double scrollAmount = 0;
    private SortMode sortMode = SortMode.TIME;
    
    private boolean isLoadingMore = false;
    private boolean hasMoreRecords = false;
    
    public static void clearCache() {
        cachedHistory = null;
        loadedRecordsCount = 0;
    }
    
    private enum SortMode {
        TIME, ALPHABETICAL, ATTEMPTS
    }
    
    private record TargetSummary(String targetId, long bestTime, int totalAttempts, int victories) {
        float getWinRate() {
            return totalAttempts > 0 ? (victories * 100.0f / totalAttempts) : 0;
        }
        
        boolean hasValidBestTime() {
            return bestTime > 0 && bestTime <= MAX_VALID_TIME;
        }
    }
    
    private static class Stats {
        int totalRuns;
        int totalWins;
        long totalPlayTime;
        long bestOverallTime = Long.MAX_VALUE;
        String bestOverallItem;
        float winRate;
        long avgTime;
    }
    
    public ResultsScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.results.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        loadAndProcessData();
        initializeUI();
    }
    
    private void loadAndProcessData() {
        loadAndProcessData(false);
    }
    
    private void loadAndProcessData(boolean loadMore) {
        List<Run> allRecords;
        
        if (cachedHistory != null && !cachedHistory.isEmpty() && !loadMore) {
            allRecords = cachedHistory.subList(0, Math.min(loadedRecordsCount, cachedHistory.size()));
            hasMoreRecords = loadedRecordsCount < cachedHistory.size();
        } else {
            try {
                com.randomrun.core.service.DataSyncService syncService = com.randomrun.core.service.DataSyncService.getInstance();
                syncService.loadHistoryOnDemand().join();
            } catch (Exception e) {
                com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[Results] Ошибка загрузки истории", e);
            }
            
            List<Run> fullHistory = RunHistory.getInstance().getAllRecords();
            
            if (cachedHistory == null) {
                cachedHistory = fullHistory;
                loadedRecordsCount = Math.min(INITIAL_LOAD_COUNT, fullHistory.size());
            } else if (loadMore) {
                loadedRecordsCount = Math.min(loadedRecordsCount + LOAD_MORE_COUNT, cachedHistory.size());
            }
            
            allRecords = cachedHistory.subList(0, Math.min(loadedRecordsCount, cachedHistory.size()));
            hasMoreRecords = loadedRecordsCount < cachedHistory.size();
            
            if (!loadMore) {
                for (int i = 0; i < Math.min(5, allRecords.size()); i++) {
                    Run r = allRecords.get(i);
                }
            }
        }
        
        Map<String, List<Run>> byTarget = allRecords.stream()
            .filter(record -> record.isUpgrader() == showingUpgrader)
            .collect(Collectors.groupingBy(Run::getTargetId));
        
        allResults.clear();
        itemNameCache.clear();
        
        for (Map.Entry<String, List<Run>> entry : byTarget.entrySet()) {
            String targetId = entry.getKey();
            List<Run> records = entry.getValue();
            
            long bestTime = records.stream()
                .filter(Run::isVictory)
                .mapToLong(Run::getDuration)
                .min()
                .orElse(Long.MAX_VALUE);
            
            int totalAttempts = records.size();
            int victories = (int) records.stream().filter(Run::isVictory).count();
            
            allResults.add(new TargetSummary(targetId, bestTime, totalAttempts, victories));
        }
        
        sortResults();
        filteredResults = new ArrayList<>(allResults);
    }
    
    private void initializeUI() {
        int centerX = width / 2;
        
        searchField = new TextFieldStyled(
            font, centerX - 100, 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.search"), 0.05f
        );
        searchField.setCenteredPlaceholder(
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.search.placeholder")
        );
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);

        addRenderableWidget(new ButtonDefault(
            centerX + 110, 30, 125, 20,
            Component.literal(showingUpgrader ? "§eRRM Upgrader" : "Классика"),
            button -> {
                showingUpgrader = !showingUpgrader;
                if (cachedHistory != null) loadedRecordsCount = cachedHistory.size();
                cachedStats = null;
                scrollOffset = 0;
                scrollAmount = 0;
                loadAndProcessData();
                button.setMessage(Component.literal(showingUpgrader ? "§eRRM Upgrader" : "Классика"));
                filterResults(searchField.getValue());
            }
        ));
        
        addSortButtons(centerX);
        addBackButton(centerX);
    }
    
    private void addSortButtons(int centerX) {
        int btnWidth = 80;
        int btnSpacing = 5;
        int totalBtnWidth = (btnWidth * 3) + (btnSpacing * 2);
        int startX = centerX - (totalBtnWidth / 2);
        int btnY = 55;

        addRenderableWidget(new ButtonDefault(
            startX, btnY, btnWidth, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.sort.time"),
            button -> changeSortMode(SortMode.TIME)
        ));
        
        addRenderableWidget(new ButtonDefault(
            startX + btnWidth + btnSpacing, btnY, btnWidth, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.sort.name"),
            button -> changeSortMode(SortMode.ALPHABETICAL)
        ));
        
        addRenderableWidget(new ButtonDefault(
            startX + 2 * (btnWidth + btnSpacing), btnY, btnWidth, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.sort.attempts"),
            button -> changeSortMode(SortMode.ATTEMPTS)
        ));
    }
    
    private void addBackButton(int centerX) {
        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> Minecraft.getInstance().gui.setScreen(new MainModScreen(null))
        ));
        
        if (hasMoreRecords) {
            addRenderableWidget(new ButtonDefault(
                20, height - 30, 120, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.load_more"),
                button -> loadMoreRecords()
            ));
        }
    }
    
    private void loadMoreRecords() {
        if (isLoadingMore || !hasMoreRecords) return;
        
        isLoadingMore = true;
        CompletableFuture.runAsync(() -> {
            loadAndProcessData(true);
            Minecraft.getInstance().execute(() -> {
                isLoadingMore = false;
                rebuildWidgets();
            });
        });
    }
    
    private void changeSortMode(SortMode mode) {
        sortMode = mode;
        sortResults();
        filterResults(searchField.getValue());
    }
    
    private void sortResults() {
        switch (sortMode) {
            case TIME -> allResults.sort(Comparator.comparingLong(TargetSummary::bestTime));
            case ALPHABETICAL -> allResults.sort(Comparator.comparing(r -> getItemName(r.targetId)));
            case ATTEMPTS -> allResults.sort(Comparator.comparingInt(r -> -r.totalAttempts));
        }
    }
    
    private void onSearchChanged(String text) {
        filterResults(text);
    }
    
    private void filterResults(String text) {
        filteredResults.clear();
        String searchLower = text.toLowerCase();
        
        for (TargetSummary result : allResults) {
            String itemName = getItemName(result.targetId).toLowerCase();
            if (itemName.contains(searchLower) || result.targetId.toLowerCase().contains(searchLower)) {
                filteredResults.add(result);
            }
        }
        
        scrollOffset = 0;
        scrollAmount = 0;
    }
    
    private String getItemName(String itemId) {
        return itemNameCache.computeIfAbsent(itemId, id -> {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                if (item == net.minecraft.world.item.Items.AIR) {
                    return "Unknown Item";
                }
                String name = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
                if (name == null || name.trim().isEmpty()) {
                    return "Unknown Item";
                }
                return name;
            } catch (Exception e) {
                return id;
            }
        });
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        
        String playerName = Minecraft.getInstance().getUser().getName();
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.results.title", playerName), 
            width / 2, 10, COLOR_TEXT_WHITE);
        
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        renderResultsList(context, mouseX, mouseY);
        renderStatsSummary(context);
    }
    
    private void renderResultsList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int listWidth = width - 40;
        context.fill(LIST_X, LIST_Y, LIST_X + listWidth, LIST_Y + RESULTS_PER_PAGE * RESULT_HEIGHT, COLOR_BG_PANEL);
        
        if (filteredResults.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.results.empty"), 
                width / 2, LIST_Y + 50, COLOR_TEXT_GRAY);
            return;
        }
        
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, filteredResults.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            renderResultItem(context, mouseX, mouseY, i, listWidth);
        }
        
        renderScrollbar(context, listWidth);
    }
    
    private void renderResultItem(GuiGraphicsExtractor context, int mouseX, int mouseY, int index, int listWidth) {
        int localIndex = index - scrollOffset;
        int y = LIST_Y + localIndex * RESULT_HEIGHT;
        TargetSummary result = filteredResults.get(index);
        
        if (isHovered(mouseX, mouseY, y, listWidth)) {
            context.fill(LIST_X, y, LIST_X + listWidth, y + RESULT_HEIGHT, COLOR_HOVER);
        }
        
        com.randomrun.ui.util.GuiText.text(context, font, "#" + (index + 1), LIST_X + 5, y + 10, COLOR_TEXT_GRAY);
        
        renderItemIcon(context, result.targetId, y);
        renderItemName(context, result.targetId, y);
        renderBestTime(context, result, y);
        renderAttempts(context, result, y, listWidth);
    }
    
    private boolean isHovered(int mouseX, int mouseY, int y, int listWidth) {
        return mouseX >= LIST_X && mouseX <= LIST_X + listWidth &&
               mouseY >= y && mouseY < y + RESULT_HEIGHT;
    }
    
    private void renderItemIcon(GuiGraphicsExtractor context, String targetId, int y) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(targetId));
            ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(item);
            context.item(stack, LIST_X + 35, y + 6);
        } catch (Exception ignored) {}
    }
    
    private void renderItemName(GuiGraphicsExtractor context, String targetId, int y) {
        String itemName = getItemName(targetId);
        if (itemName.length() > MAX_ITEM_NAME_LENGTH) {
            itemName = itemName.substring(0, 22) + "...";
        }
        com.randomrun.ui.util.GuiText.text(context, font, itemName, LIST_X + 60, y + 6, COLOR_TEXT_WHITE);
    }
    
    private void renderBestTime(GuiGraphicsExtractor context, TargetSummary result, int y) {
        String timeStr;
        int timeColor;
        
        if (result.hasValidBestTime()) {
            timeStr = RunDataManager.formatTime(result.bestTime);
            timeColor = COLOR_SUCCESS;
        } else {
            timeStr = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.defeat.title").getString();
            timeColor = COLOR_DANGER;
        }
        
        com.randomrun.ui.util.GuiText.text(context, font, timeStr, LIST_X + 60, y + 18, timeColor);
    }
    
    private void renderAttempts(GuiGraphicsExtractor context, TargetSummary result, int y, int listWidth) {
        String attemptsStr = String.format("%d runs (%d wins, %.0f%%) [History]", 
            result.totalAttempts, result.victories, result.getWinRate());
        
        int attemptsX = LIST_X + listWidth - font.width(attemptsStr) - 10;
        com.randomrun.ui.util.GuiText.text(context, font, attemptsStr, attemptsX, y + 10, COLOR_TEXT_LIGHT_GRAY);
    }
    
    private void renderScrollbar(GuiGraphicsExtractor context, int listWidth) {
        if (filteredResults.size() <= RESULTS_PER_PAGE) return;
        
        int maxScroll = filteredResults.size() - RESULTS_PER_PAGE;
        float scrollRatio = scrollOffset / (float) maxScroll;
        
        int scrollBarX = LIST_X + listWidth + 5;
        int scrollBarHeight = RESULTS_PER_PAGE * RESULT_HEIGHT;
        int thumbHeight = Math.max(20, scrollBarHeight / (filteredResults.size() / RESULTS_PER_PAGE + 1));
        int thumbY = LIST_Y + (int) ((scrollBarHeight - thumbHeight) * scrollRatio);
        
        context.fill(scrollBarX, LIST_Y, scrollBarX + 6, LIST_Y + scrollBarHeight, COLOR_SCROLLBAR_BG);
        context.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight, COLOR_ACCENT);
    }
    
    private void renderStatsSummary(GuiGraphicsExtractor context) {
        if (cachedStats == null) {
            cachedStats = calculateStats();
        }
        renderStatsPanel(context, cachedStats);
        renderBestRecord(context, cachedStats);
    }
    
    private Stats calculateStats() {
        Stats stats = new Stats();
        
        List<Run> allRecords = cachedHistory != null ? cachedHistory : RunHistory.getInstance().getAllRecords();
        allRecords = allRecords.stream().filter(record -> record.isUpgrader() == showingUpgrader).toList();
        
        for (TargetSummary result : allResults) {
            stats.totalRuns += result.totalAttempts;
            stats.totalWins += result.victories;
            
            if (result.hasValidBestTime() && result.bestTime < stats.bestOverallTime) {
                stats.bestOverallTime = result.bestTime;
                stats.bestOverallItem = result.targetId;
            }
        }
        
        for (Run record : allRecords) {
            if (record.getDuration() > 0) {
                stats.totalPlayTime += record.getDuration();
            }
        }
        
        stats.winRate = stats.totalRuns > 0 ? (stats.totalWins * 100.0f / stats.totalRuns) : 0;
        stats.avgTime = stats.totalWins > 0 ? (allRecords.stream()
            .filter(r -> r.isVictory() && r.getDuration() > 0)
            .mapToLong(Run::getDuration)
            .sum() / stats.totalWins) : 0;
        
        return stats;
    }
    
    private void renderStatsPanel(GuiGraphicsExtractor context, Stats stats) {
        int panelY = height - 65;
        int panelX = 20;
        int panelW = width - 40;
        
        context.fill(panelX, panelY, panelX + panelW, panelY + 32, COLOR_BG_STATS);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, COLOR_ACCENT);
        
        int statY = panelY + 4;
        int sx = panelX + 10;
        int gap = 12;
        
        sx = renderStatItem(context, sx, statY, gap, 
            com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.stats.items_label", String.valueOf(allResults.size())),
            COLOR_STAT_LABEL);
        
        sx = renderStatItem(context, sx, statY, gap,
            com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.stats.runs", String.valueOf(stats.totalRuns)) + 
            " §8(§a" + stats.totalWins + "W§8/§c" + (stats.totalRuns - stats.totalWins) + "L§8)",
            COLOR_STAT_LABEL);
        
        int winRateColor = stats.winRate >= 50 ? COLOR_SUCCESS : stats.winRate >= 25 ? COLOR_WARNING : COLOR_DANGER;
        sx = renderStatItem(context, sx, statY, gap,
            com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.stats.winrate", String.format("%.0f%%", stats.winRate)),
            winRateColor);
        
        if (stats.avgTime > 0) {
            sx = renderStatItem(context, sx, statY, gap,
                com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.stats.avg_time", RunDataManager.formatTime(stats.avgTime)),
                COLOR_WARNING);
        }
        
        sx = renderStatItem(context, sx, statY, gap,
            com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.stats.playtime", RunDataManager.formatTime(stats.totalPlayTime)),
            COLOR_INFO);
        
        renderTrustScore(context, sx, statY);
    }
    
    private int renderStatItem(GuiGraphicsExtractor context, int x, int y, int gap, String text, int color) {
        com.randomrun.ui.util.GuiText.text(context, font, text, x, y, color);
        return x + font.width(text) + gap;
    }
    
    private void renderTrustScore(GuiGraphicsExtractor context, int x, int y) {
        if (!com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
        int trustScore = profile != null ? profile.getTrustScore() : -1;
        
        if (trustScore >= 0) {
            int trustColor = trustScore >= 150 ? COLOR_SUCCESS : trustScore >= 100 ? COLOR_WARNING : COLOR_DANGER;
            String trustText = com.randomrun.infrastructure.util.LanguageManager.translate(
                "randomrun.stats.trust_score", trustScore + "/200");
            com.randomrun.ui.util.GuiText.text(context, font, trustText, x, y, trustColor);
        }
    }
    
    private void renderBestRecord(GuiGraphicsExtractor context, Stats stats) {
        if (stats.bestOverallTime >= Long.MAX_VALUE || stats.bestOverallItem == null) return;
        
        String itemName = getItemName(stats.bestOverallItem);
        if (itemName.length() > MAX_BEST_ITEM_NAME_LENGTH) {
            itemName = itemName.substring(0, 17) + "...";
        }
        
        String bestText = "⭐ " + com.randomrun.infrastructure.util.LanguageManager.translate(
            "randomrun.stats.best_record", 
            RunDataManager.formatTime(stats.bestOverallTime), 
            itemName);
        
        com.randomrun.ui.util.GuiText.text(context, font, bestText, 30, height - 47, COLOR_SUCCESS);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            return true;
        }
        
        int listWidth = width - 40;
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, filteredResults.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int y = LIST_Y + localIndex * RESULT_HEIGHT;
            
            if (mouseX >= LIST_X && mouseX <= LIST_X + listWidth &&
                mouseY >= y && mouseY < y + RESULT_HEIGHT) {
                openHistoryScreen(filteredResults.get(i));
                return true;
            }
        }
        
        return false;
    }
    
    private void openHistoryScreen(TargetSummary result) {
        RunDataManager.RunResult runResult = 
            new RunDataManager.RunResult(result.targetId, result.bestTime);
        
        Minecraft.getInstance().gui.setScreen(new RunHistoryScreen(this, runResult, showingUpgrader));
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
            )
        );
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, filteredResults.size() - RESULTS_PER_PAGE);
        scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - verticalAmount));
        scrollOffset = (int) scrollAmount;
        return true;
    }
}

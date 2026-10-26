package com.randomrun.ui.screen.main;

import com.randomrun.features.leaderboard.LeaderboardEntry;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.ui.widget.styled.ButtonYellow;
import com.randomrun.ui.widget.GlobalParticleSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class LeaderboardScreen extends AbstractRandomRunScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderboardScreen.class);
    
    private static final int LEFT_PANEL_WIDTH = 220;
    private static final int PADDING = 15;
    private static final int ITEM_SIZE = 20;
    private static final int ITEM_SPACING = 4;
    private static final int ITEMS_PER_ROW = 8;
    private static final int TOP_RECORDS_LIMIT = 100;
    private static final int DISPLAYED_RECORDS_LIMIT = 10;
    private static final long SEARCH_DEBOUNCE_MS = 300L;
    
    private static final int COLOR_DARK_GOLD = 0xE0_3d2f06;
    private static final int COLOR_LIGHT_GOLD = 0xE0_3d2f06;
    private static final int COLOR_GOLD = 0xFFD700;
    private static final int COLOR_GOLD_BORDER = 0xFFDAA520;
    private static final int COLOR_GOLD_SEPARATOR = 0xAAFFD700;
    private static final int COLOR_GOLD_TEXT = 0xFFAA00;
    private static final int COLOR_SILVER = 0xC0C0C0;
    private static final int COLOR_BRONZE = 0xCD7F32;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_GRAY = 0xCCCCCC;
    private static final int COLOR_DARK_GRAY = 0x888888;
    private static final int COLOR_RED = 0xFF5555;
    private static final int COLOR_ROW_BG = 0x30_000000;
    private static final int COLOR_ITEM_BG = 0xFF3d2f06;
    private static final int COLOR_ITEM_HOVER = 0xFF5d4f16;
    private static final int COLOR_ITEM_SELECTED = 0xFFDAA520;
    private static final long ANIMATION_CYCLE_MS = 5000L;
    private static final int GRADIENT_LENGTH = 40;
    private static final int BORDER_THICKNESS = 2;
    
    private final Screen parent;
    private String selectedTarget = null;
    private List<LeaderboardEntry> currentRecords = new ArrayList<>();
    private boolean isFetching = false;
    private String lastErrorMessage = null;
    
    private java.util.Map<String, List<LeaderboardEntry>> leaderboardsCache = new java.util.HashMap<>();
    private java.util.Set<String> itemsWithRecords = new java.util.HashSet<>();
    private boolean allLeaderboardsLoaded = false;
    
    private com.randomrun.ui.widget.styled.TextFieldStyled searchBox;
    private List<String> allTargets = new ArrayList<>();
    private List<String> filteredTargets = new ArrayList<>();
    
    private double smoothScrollOffset = 0.0;
    private int itemGridScrollOffset = 0;
    
    private long lastSearchTime = 0L;
    private String pendingSearchQuery = "";
    private boolean searchPending = false;
    
    private enum SortType {
        RECENT,
        EASY_HARD,
        HARD_EASY
    }
    private SortType currentSort = SortType.RECENT;
    
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartScrollOffset = 0;
    
    private List<PlayerRecordEntry> playerTopRecords = new ArrayList<>();

    public LeaderboardScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.title"));
        this.parent = parent;
        
        boolean allowUnobtainable = com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
        
        List<net.minecraft.world.item.Item> items = com.randomrun.features.challenges.classic.data.ItemDifficulty.getAllItems(allowUnobtainable);
        items.forEach(item -> {
            net.minecraft.resources.Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                allTargets.add(id.toString());
            }
        });
        
        filteredTargets.addAll(allTargets);
    }

    @Override
    protected void init() {
        super.init();
        GlobalParticleSystem.getInstance().setYellowMode(true);
        initUI();
        
        if (!allLeaderboardsLoaded) {
            loadAllLeaderboards();
        } else if (selectedTarget != null) {
            showCachedRecords(selectedTarget);
        }
    }

    private void initUI() {
        initSearchBox();
        initObtainableFilterButton();
        initRefreshButton();
        initBackButton();
    }
    
    private void initSearchBox() {
        int searchWidth = LEFT_PANEL_WIDTH - 20;
        searchBox = new com.randomrun.ui.widget.styled.TextFieldStyled(font, PADDING + 10, PADDING + 45, searchWidth, 18, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.search"), 0f) {
            @Override
            protected int getTextYAdjustment() {
                return 5;
            }
        };
        searchBox.setColors(0x3d2f06, 0x5d4f16);
        searchBox.setBorderColor(0xDAA520);
        searchBox.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.search_placeholder")
            .withStyle(s -> s.withColor(COLOR_DARK_GRAY)));
        searchBox.setResponder(text -> {
            pendingSearchQuery = text;
            searchPending = true;
            lastSearchTime = System.currentTimeMillis();
        });
        addRenderableWidget(searchBox);
    }
    
    private void initObtainableFilterButton() {
    }
    
    private void initRefreshButton() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = width - buttonWidth - 20;
        int buttonY = 22;
        
        addRenderableWidget(new ButtonYellow(
            buttonX, buttonY, buttonWidth, buttonHeight,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.refresh"),
            button -> {
                leaderboardsCache.clear();
                itemsWithRecords.clear();
                allLeaderboardsLoaded = false;
                LeaderboardService.getInstance().clearCache();
                
                loadAllLeaderboards();
            }
        ));
    }
    
    private void initBackButton() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = height - 30;
        
        addRenderableWidget(new ButtonYellow(
            buttonX, buttonY, buttonWidth, buttonHeight,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> Minecraft.getInstance().gui.setScreen(parent)
        ));
        
        if (selectedTarget != null) {
            int profileButtonSize = 20;
            int profileButtonX = buttonX + buttonWidth + 10;
            int profileButtonY = buttonY;
            
            addRenderableWidget(new ButtonYellow(
                profileButtonX, profileButtonY, profileButtonSize, profileButtonSize,
                Component.literal("👤"),
                button -> {
                    selectedTarget = null;
                    currentRecords.clear();
                    
                    clearWidgets();
                    initUI();
                }
            ));
        }
    }
    
    private void filterTargets(String query) {
        if (query.isEmpty()) {
            filteredTargets = new ArrayList<>(allTargets);
        } else {
            String lowerQuery = query.toLowerCase();
            filteredTargets = allTargets.stream()
                .filter(id -> {
                    try {
                        String cleanId = id.contains(":") ? id.split(":")[1] : id;
                        
                        net.minecraft.resources.Identifier identifier = net.minecraft.resources.Identifier.tryParse(id);
                        if (identifier == null) return false;
                        
                        Item item = BuiltInRegistries.ITEM.getValue(identifier);
                        if (item == null) return false;
                        
                        String translatedName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString().toLowerCase();
                        
                        String englishName = cleanId.replace("_", " ").toLowerCase();
                        
                        return cleanId.toLowerCase().contains(lowerQuery) || 
                               translatedName.contains(lowerQuery) || 
                               englishName.contains(lowerQuery);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
        }
        itemGridScrollOffset = 0;
    }
    
    private void applySorting() {
        switch (currentSort) {
            case RECENT -> sortByRecentRecord();
            case EASY_HARD -> sortByDifficulty(false);
            case HARD_EASY -> sortByDifficulty(true);
        }
        itemGridScrollOffset = 0;
    }
    
    private void sortByRecentRecord() {
        filteredTargets.sort((id1, id2) -> {
            List<LeaderboardEntry> records1 = leaderboardsCache.get(id1);
            List<LeaderboardEntry> records2 = leaderboardsCache.get(id2);
            
            long time1 = records1 != null && !records1.isEmpty() ? records1.get(0).getTimestamp() : 0;
            long time2 = records2 != null && !records2.isEmpty() ? records2.get(0).getTimestamp() : 0;
            
            return Long.compare(time2, time1);
        });
    }
    
    private void sortByDifficulty(boolean hardFirst) {
        filteredTargets.sort((id1, id2) -> {
            try {
                int difficulty1Ordinal;
                int difficulty2Ordinal;
                
                net.minecraft.resources.Identifier identifier1 = net.minecraft.resources.Identifier.tryParse(id1);
                if (identifier1 != null) {
                    Item item1 = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(identifier1);
                    com.randomrun.features.challenges.classic.data.ItemDifficulty.Difficulty itemDiff = 
                        com.randomrun.features.challenges.classic.data.ItemDifficulty.getDifficulty(item1);
                    difficulty1Ordinal = itemDiff.ordinal();
                } else {
                    difficulty1Ordinal = 0;
                }
                
                net.minecraft.resources.Identifier identifier2 = net.minecraft.resources.Identifier.tryParse(id2);
                if (identifier2 != null) {
                    Item item2 = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(identifier2);
                    com.randomrun.features.challenges.classic.data.ItemDifficulty.Difficulty itemDiff = 
                        com.randomrun.features.challenges.classic.data.ItemDifficulty.getDifficulty(item2);
                    difficulty2Ordinal = itemDiff.ordinal();
                } else {
                    difficulty2Ordinal = 0;
                }
                
                int result = hardFirst ? 
                    Integer.compare(difficulty2Ordinal, difficulty1Ordinal) : 
                    Integer.compare(difficulty1Ordinal, difficulty2Ordinal);
                
                if (result == 0) {
                    result = id1.compareTo(id2);
                }
                
                return result;
            } catch (Exception e) {
                return id1.compareTo(id2);
            }
        });
    }
    
    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        initUI();
    }

    private void loadAllLeaderboards() {
        if (isFetching) return;
        
        isFetching = true;
        currentRecords.clear();
        lastErrorMessage = null;
        
        LeaderboardService.getInstance().loadAllLeaderboards().thenAccept(allLeaderboards -> {
            Minecraft.getInstance().execute(() -> {
                this.leaderboardsCache = allLeaderboards;
                this.itemsWithRecords = new java.util.HashSet<>(allLeaderboards.keySet());
                this.allLeaderboardsLoaded = true;
                this.isFetching = false;
                
                showCachedRecords(selectedTarget);
            });
        }).exceptionally(e -> {
            Minecraft.getInstance().execute(() -> {
                this.isFetching = false;
                this.allLeaderboardsLoaded = true;
                this.lastErrorMessage = com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.leaderboard.error");
                LOGGER.error("[Leaderboard] Ошибка загрузки всех лидербордов", e);
            });
            return null;
        });
    }
    
    private void showCachedRecords(String targetId) {
        currentRecords.clear();
        lastErrorMessage = null;
        
        if (leaderboardsCache.containsKey(targetId)) {
            List<LeaderboardEntry> cached = leaderboardsCache.get(targetId);
            currentRecords = new ArrayList<>(cached);
        }
        
        clearWidgets();
        initUI();
    }
    
    private List<LeaderboardEntry> getFilteredRecords() {
        return currentRecords.stream()
            .limit(DISPLAYED_RECORDS_LIMIT)
            .collect(Collectors.toList());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int GRADIENT_TOP = 0xFF141000;
        int GRADIENT_MID = 0xFF886600;
        int GRADIENT_BOTTOM = 0xFF332200;
        
        context.fillGradient(0, 0, width, height / 2, GRADIENT_TOP, GRADIENT_MID);
        context.fillGradient(0, height / 2, width, height, GRADIENT_MID, GRADIENT_BOTTOM);
        
        GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
        particles.update(delta);
        particles.extractRenderState(context);
        
        if (searchPending && System.currentTimeMillis() - lastSearchTime >= SEARCH_DEBOUNCE_MS) {
            filterTargets(pendingSearchQuery);
            searchPending = false;
        }
        
        smoothScrollOffset += (itemGridScrollOffset - smoothScrollOffset) * 0.3;
        
        int leftPanelRight = PADDING + LEFT_PANEL_WIDTH;
        int rightPanelLeft = leftPanelRight + PADDING;
        int rightPanelRight = width - PADDING;
        
        renderLeftPanel(context, leftPanelRight, mouseX, mouseY);
        renderRightPanel(context, rightPanelLeft, rightPanelRight, mouseX, mouseY);
    }
    
    private void renderLeftPanel(GuiGraphicsExtractor context, int leftPanelRight, int mouseX, int mouseY) {
        int panelBottom = height - 50;
        context.fill(PADDING, PADDING, leftPanelRight, panelBottom, COLOR_DARK_GOLD);
        renderGoldBorder(context, PADDING, PADDING, leftPanelRight, panelBottom, false);
        
        Component leftTitle = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.items_panel")
            .withStyle(s -> s.withColor(COLOR_GOLD));
        com.randomrun.ui.util.GuiText.text(context, font, leftTitle, PADDING + 15, PADDING + 15, COLOR_WHITE);
        
        context.fill(PADDING + 10, PADDING + 35, leftPanelRight - 10, PADDING + 37, COLOR_GOLD_SEPARATOR);
        
        renderItemGrid(context, mouseX, mouseY);
        
        String countText = filteredTargets.size() + " / " + allTargets.size();
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(countText).withStyle(s -> s.withColor(COLOR_GOLD_TEXT)), 
            PADDING + LEFT_PANEL_WIDTH / 2, panelBottom - 20, COLOR_WHITE);
    }
    
    private void renderItemGrid(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int gridX = PADDING + 10;
        int gridY = PADDING + 70;
        int gridWidth = LEFT_PANEL_WIDTH - 20;
        int panelBottom = height - 50;
        int gridHeight = panelBottom - gridY - 30;
        
        context.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);
        
        int itemsPerRow = ITEMS_PER_ROW;
        int totalRows = (int) Math.ceil((double) filteredTargets.size() / itemsPerRow);
        int visibleRows = gridHeight / (ITEM_SIZE + ITEM_SPACING);
        
        int startRow = (int) (smoothScrollOffset / (ITEM_SIZE + ITEM_SPACING));
        int endRow = Math.min(startRow + visibleRows + 2, totalRows);
        
        String hoveredItemId = null;
        int hoveredX = 0, hoveredY = 0;
        
        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < itemsPerRow; col++) {
                int index = row * itemsPerRow + col;
                if (index >= filteredTargets.size()) break;
                
                String targetId = filteredTargets.get(index);
                
                int x = gridX + col * (ITEM_SIZE + ITEM_SPACING);
                int y = gridY + row * (ITEM_SIZE + ITEM_SPACING) - (int) smoothScrollOffset;
                
                boolean isSelected = targetId.equals(selectedTarget);
                boolean isHovered = mouseX >= x && mouseX < x + ITEM_SIZE && 
                                   mouseY >= y && mouseY < y + ITEM_SIZE &&
                                   mouseY >= gridY && mouseY < gridY + gridHeight;
                
                int bgColor = isSelected ? COLOR_ITEM_SELECTED : (isHovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG);
                context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);
                
                if (isSelected) {
                    context.fill(x, y, x + ITEM_SIZE, y + 1, COLOR_GOLD);
                    context.fill(x, y + ITEM_SIZE - 1, x + ITEM_SIZE, y + ITEM_SIZE, COLOR_GOLD);
                    context.fill(x, y, x + 1, y + ITEM_SIZE, COLOR_GOLD);
                    context.fill(x + ITEM_SIZE - 1, y, x + ITEM_SIZE, y + ITEM_SIZE, COLOR_GOLD);
                }
                
                renderTarget(context, targetId, x + 2, y + 2, ITEM_SIZE - 4, mouseX, mouseY);
                
                if (itemsWithRecords.contains(targetId)) {
                    int borderColor = 0xFFFFD700;
                    context.fill(x, y, x + ITEM_SIZE, y + 1, borderColor);
                    context.fill(x, y + ITEM_SIZE - 1, x + ITEM_SIZE, y + ITEM_SIZE, borderColor);
                    context.fill(x, y, x + 1, y + ITEM_SIZE, borderColor);
                    context.fill(x + ITEM_SIZE - 1, y, x + ITEM_SIZE, y + ITEM_SIZE, borderColor);
                }
                
                if (isHovered) {
                    hoveredItemId = targetId;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }
        }
        
        context.disableScissor();
        
        if (hoveredItemId != null) {
            renderTargetTooltip(context, hoveredItemId, hoveredX, hoveredY);
        }
        
        if (totalRows * (ITEM_SIZE + ITEM_SPACING) > gridHeight) {
            int scrollBarX = gridX + gridWidth - 4;
            int scrollBarHeight = Math.max(20, gridHeight * gridHeight / (totalRows * (ITEM_SIZE + ITEM_SPACING)));
            int scrollBarY = gridY + (int) ((gridHeight - scrollBarHeight) * (smoothScrollOffset / ((totalRows * (ITEM_SIZE + ITEM_SPACING)) - gridHeight)));
            
            boolean isHovered = mouseX >= scrollBarX && mouseX <= scrollBarX + 3 && 
                               mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight;
            
            context.fill(scrollBarX, gridY, scrollBarX + 3, gridY + gridHeight, 0x40FFFFFF);
            
            int thumbColor = isDraggingScrollbar ? 0xFFFFD700 : (isHovered ? 0xFFFFCC00 : COLOR_GOLD);
            context.fill(scrollBarX, scrollBarY, scrollBarX + 3, scrollBarY + scrollBarHeight, thumbColor);
        }
    }
    
    private void renderRightPanel(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight, int mouseX, int mouseY) {
        int panelBottom = height - 50;
        context.fill(rightPanelLeft, PADDING, rightPanelRight, panelBottom, COLOR_LIGHT_GOLD);
        renderGoldBorder(context, rightPanelLeft, PADDING, rightPanelRight, panelBottom, true);
        
        renderRightPanelHeader(context, rightPanelLeft, rightPanelRight);
        renderLeaderboardTable(context, rightPanelLeft, rightPanelRight, mouseX, mouseY);
        renderFooter(context, rightPanelLeft, rightPanelRight);
    }
    
    private void renderRightPanelHeader(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight) {
        if (selectedTarget == null) {
            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            if (profile != null) {
                String playerName = profile.getPlayerName();
                
                int headSize = 20;
                int headX = rightPanelLeft + 20;
                int headY = 22;
                renderPlayerHead(context, playerName, null, headX, headY, headSize);
                
                Component nameText = Component.literal(playerName).withStyle(s -> s.withColor(COLOR_GOLD));
                com.randomrun.ui.util.GuiText.text(context, font, nameText, headX + headSize + 6, headY + 6, COLOR_WHITE);
            }
        } else {
            String itemName = getItemDisplayName(selectedTarget);
            Component titleText = Component.literal(itemName).withStyle(s -> s.withColor(COLOR_GOLD));
            com.randomrun.ui.util.GuiText.text(context, font, titleText, rightPanelLeft + 20, PADDING + 15, COLOR_WHITE);
        }
        
        context.fill(rightPanelLeft + 15, PADDING + 35, rightPanelRight - 15, PADDING + 37, COLOR_GOLD_SEPARATOR);
    }
    
    private String getItemDisplayName(String itemId) {
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Leaderboard] Ошибка получения имени для {}", itemId);
        }
        return com.randomrun.infrastructure.util.LanguageManager.translateItem(itemId);
    }
    
    private void renderLeaderboardTable(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight, int mouseX, int mouseY) {
        int startY = PADDING + 45;
        
        if (selectedTarget == null) {
            renderPlayerTopRecords(context, rightPanelLeft, rightPanelRight, startY, mouseX, mouseY);
        } else {
            int rankX = rightPanelLeft + 30;
            int playerX = rightPanelLeft + 80;
            int timeX = rightPanelRight - 120;
            
            renderTableHeaders(context, rankX, playerX, timeX, startY);
            
            int listY = startY + 20;
            
            if (isFetching) {
                renderLoadingMessage(context, rightPanelLeft, rightPanelRight, listY);
            } else if (lastErrorMessage != null) {
                renderErrorMessage(context, rightPanelLeft, rightPanelRight, listY);
            } else {
                renderRecordsList(context, rankX, playerX, timeX, listY, rightPanelLeft, rightPanelRight);
            }
        }
    }
    
    private void renderTableHeaders(GuiGraphicsExtractor context, int rankX, int playerX, int timeX, int startY) {
        com.randomrun.ui.util.GuiText.text(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.rank").withStyle(s -> s.withColor(COLOR_GOLD)), 
            rankX, startY, COLOR_WHITE);
        com.randomrun.ui.util.GuiText.text(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.player").withStyle(s -> s.withColor(COLOR_GOLD)), 
            playerX, startY, COLOR_WHITE);
        com.randomrun.ui.util.GuiText.text(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.time").withStyle(s -> s.withColor(COLOR_GOLD)), 
            timeX, startY, COLOR_WHITE);
    }
    
    private void renderLoadingMessage(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight, int listY) {
        int centerX = rightPanelLeft + (rightPanelRight - rightPanelLeft) / 2;
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.loading").withStyle(s -> s.withColor(COLOR_GOLD)), 
            centerX, listY + 40, COLOR_WHITE);
    }
    
    private void renderErrorMessage(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight, int listY) {
        int centerX = rightPanelLeft + (rightPanelRight - rightPanelLeft) / 2;
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            Component.literal(lastErrorMessage).withStyle(s -> s.withColor(COLOR_RED)), 
            centerX, listY + 40, COLOR_WHITE);
    }
    
    private void renderRecordsList(GuiGraphicsExtractor context, int rankX, int playerX, int timeX, int listY, int rightPanelLeft, int rightPanelRight) {
        List<LeaderboardEntry> filteredRecords = getFilteredRecords();
        
        if (filteredRecords.isEmpty()) {
            int centerX = rightPanelLeft + (rightPanelRight - rightPanelLeft) / 2;
            
            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            boolean lowTrustScore = profile != null && !profile.canSubmitToLeaderboard();
            
            if (lowTrustScore) {
                int ts = profile.getTrustScore();
                String lowTrustMsg = String.format(
                    com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.leaderboard.low_trust"), ts);
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    Component.literal(lowTrustMsg).withStyle(s -> s.withColor(COLOR_RED)), 
                    centerX, listY + 20, COLOR_WHITE);
                
                int trustScore = profile.getTrustScore();
                int needed = com.randomrun.core.profile.PlayerProfile.LEADERBOARD_THRESHOLD - trustScore;
                Component trustText = Component.literal(String.format("Trust Score: %d/%d (нужно +%d)", 
                    trustScore, com.randomrun.core.profile.PlayerProfile.LEADERBOARD_THRESHOLD, needed))
                    .withStyle(s -> s.withColor(COLOR_GOLD_TEXT));
                com.randomrun.ui.util.GuiText.centeredText(context, font, trustText, centerX, listY + 35, COLOR_WHITE);
                
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.earn_trust").withStyle(s -> s.withColor(COLOR_GOLD)), 
                    centerX, listY + 50, COLOR_WHITE);
            } else {
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.no_records").withStyle(s -> s.withColor(COLOR_RED)), 
                    centerX, listY + 30, COLOR_WHITE);
                com.randomrun.ui.util.GuiText.centeredText(context, font, 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.be_first").withStyle(s -> s.withColor(COLOR_GOLD)), 
                    centerX, listY + 50, COLOR_WHITE);
            }
            return;
        }
        
        for (int i = 0; i < filteredRecords.size(); i++) {
            LeaderboardEntry record = filteredRecords.get(i);
            
            renderRecordRow(context, record, i, rankX, playerX, timeX, listY);
            listY += 22;
        }
    }
    
    private void renderRecordRow(GuiGraphicsExtractor context, LeaderboardEntry record, int rank, int rankX, int playerX, int timeX, int listY) {
        context.fill(rankX - 5, listY - 3, timeX + 100, listY + 15, 0x60000000);
        
        String rankText = String.valueOf(rank + 1);
        int rankColor;
        
        switch (rank) {
            case 0 -> rankColor = COLOR_GOLD;
            case 1 -> rankColor = COLOR_SILVER;
            case 2 -> rankColor = COLOR_BRONZE;
            default -> rankColor = COLOR_GRAY;
        }
        
        com.randomrun.ui.util.GuiText.text(context, font, 
            Component.literal(rankText).withStyle(s -> s.withColor(rankColor)), 
            rankX, listY + 3, COLOR_WHITE);
        
        int headSize = 16;
        int headX = playerX - headSize - 4;
        int headY = listY - 2;
        renderPlayerHead(context, record.getPlayerName(), null, headX, headY, headSize);
        
        com.randomrun.ui.util.GuiText.text(context, font, 
            Component.literal(record.getPlayerName()).withStyle(s -> s.withColor(COLOR_WHITE)), 
            playerX, listY + 3, COLOR_WHITE);
        
        if (record.getPlayerName().equalsIgnoreCase("salopuzik2")) {
            int dateTimeX = timeX - 60;
            int playerNameWidth = font.width(record.getPlayerName());
            int playerNameEndX = playerX + playerNameWidth;
            
            int devLabelX = playerNameEndX + (dateTimeX - playerNameEndX) / 2 - 25;
            
            context.pose().pushMatrix();
            context.pose().translate(devLabelX, listY + 5);
            context.pose().scale(0.7f, 0.7f);
            com.randomrun.ui.util.GuiText.text(context, font, 
                Component.literal("Разработчик"), 
                0, 0, 0xFFFF5555, false);
            context.pose().popMatrix();
        }
        
        String dateTimeStr = formatDateTime(record.getTimestamp());
        int dateTimeX = timeX - 60;
        context.pose().pushMatrix();
        context.pose().translate(dateTimeX, listY + 5);
        context.pose().scale(0.7f, 0.7f);
        com.randomrun.ui.util.GuiText.text(context, font, 
            Component.literal(dateTimeStr), 
            0, 0, 0x80808080, false);
        context.pose().popMatrix();
        
        String timeStr = formatTime(record.getCompletionTime());
        com.randomrun.ui.util.GuiText.text(context, font, 
            Component.literal(timeStr).withStyle(s -> s.withColor(COLOR_GOLD_TEXT)), 
            timeX, listY + 3, COLOR_WHITE);
    }
    
    private String formatDateTime(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.ZonedDateTime dateTime = instant.atZone(java.time.ZoneId.systemDefault());
        
        int day = dateTime.getDayOfMonth();
        int month = dateTime.getMonthValue();
        int year = dateTime.getYear() % 100;
        int hour = dateTime.getHour();
        int minute = dateTime.getMinute();
        
        return String.format("%02d.%02d.%02d %02d:%02d", day, month, year, hour, minute);
    }
    
    private void renderPlayerHead(GuiGraphicsExtractor context, String playerName, String playerId, int x, int y, int size) {
        try {
            UUID playerUuid = playerId != null && !playerId.isEmpty() 
                ? UUID.fromString(playerId) 
                : com.randomrun.infrastructure.util.PlayerSkinHelper.getOfflineUUID(playerName);
            
            Identifier skinTexture = com.randomrun.infrastructure.util.PlayerSkinHelper.getPlayerSkin(playerName, playerUuid);
            
            context.fill(x - 1, y - 1, x + size + 1, y, COLOR_GOLD_BORDER);
            context.fill(x - 1, y + size, x + size + 1, y + size + 1, COLOR_GOLD_BORDER);
            context.fill(x - 1, y, x, y + size, COLOR_GOLD_BORDER);
            context.fill(x + size, y, x + size + 1, y + size, COLOR_GOLD_BORDER);
            
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinTexture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
            
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinTexture, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
        } catch (Exception e) {
            LOGGER.warn("[Leaderboard] Ошибка рендера головы для {}", playerName);
        }
    }
    
    private void renderFooter(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight) {
        int footerY = height - PADDING - 45;
        int centerX = rightPanelLeft + (rightPanelRight - rightPanelLeft) / 2 + 5;
        Component footerText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.footer")
            .withStyle(s -> s.withColor(COLOR_DARK_GRAY));
        com.randomrun.ui.util.GuiText.centeredText(context, font, footerText, centerX, footerY - 3, COLOR_WHITE);
    }
    
    private void renderPlayerTopRecords(GuiGraphicsExtractor context, int rightPanelLeft, int rightPanelRight, int startY, int mouseX, int mouseY) {
        com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
        if (profile == null) return;
        
        String playerName = profile.getPlayerName();
        
        List<PlayerRecordEntry> playerRecords = new ArrayList<>();
        for (java.util.Map.Entry<String, List<LeaderboardEntry>> entry : leaderboardsCache.entrySet()) {
            String itemId = entry.getKey();
            List<LeaderboardEntry> records = entry.getValue();
            
            for (int i = 0; i < records.size(); i++) {
                LeaderboardEntry record = records.get(i);
                if (record.getPlayerName().equals(playerName)) {
                    playerRecords.add(new PlayerRecordEntry(itemId, record.getCompletionTime(), i + 1));
                    break;
                }
            }
        }
        
        if (playerRecords.isEmpty()) {
            int centerX = rightPanelLeft + (rightPanelRight - rightPanelLeft) / 2;
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.leaderboard.no_personal_records")
                    .withStyle(s -> s.withColor(COLOR_GRAY)), 
                centerX, startY + 40, COLOR_WHITE);
            return;
        }
        
        playerRecords.sort((a, b) -> Long.compare(a.time, b.time));
        
        List<PlayerRecordEntry> top10 = playerRecords.stream().limit(10).collect(Collectors.toList());
        
        int itemX = rightPanelLeft + 30;
        int timeX = rightPanelRight - 120;
        int rankX = rightPanelRight - 60;
        int listY = startY + 10;
        
        for (int i = 0; i < top10.size(); i++) {
            PlayerRecordEntry record = top10.get(i);
            
            boolean isHovered = mouseX >= itemX - 5 && mouseX <= rightPanelRight - 20 &&
                               mouseY >= listY - 3 && mouseY < listY + 15;
            
            record.bounds = new int[]{itemX - 5, listY - 3, rightPanelRight - 20, listY + 15};
            
            context.fill(itemX - 5, listY - 3, rightPanelRight - 20, listY + 15, 0x60000000);
            
            if (isHovered) {
                int left = itemX - 5;
                int top = listY - 3;
                int right = rightPanelRight - 20;
                int bottom = listY + 15;
                int thickness = 1;
                
                context.fill(left, top, right, top + thickness, COLOR_GOLD_BORDER);
                context.fill(left, bottom - thickness, right, bottom, COLOR_GOLD_BORDER);
                context.fill(left, top, left + thickness, bottom, COLOR_GOLD_BORDER);
                context.fill(right - thickness, top, right, bottom, COLOR_GOLD_BORDER);
            }
            
            try {
                renderTarget(context, record.itemId, itemX, listY - 2, 16, itemX, listY);
            } catch (Exception e) {
                LOGGER.warn("[Leaderboard] Ошибка отрисовки цели {}", record.itemId);
            }
            
            String itemName = getItemDisplayName(record.itemId);
            com.randomrun.ui.util.GuiText.text(context, font, 
                Component.literal(itemName).withStyle(s -> s.withColor(COLOR_WHITE)), 
                itemX + 20, listY + 3, COLOR_WHITE);
            
            String timeStr = formatTime(record.time);
            com.randomrun.ui.util.GuiText.text(context, font, 
                Component.literal(timeStr).withStyle(s -> s.withColor(COLOR_GOLD_TEXT)), 
                timeX, listY + 3, COLOR_WHITE);
            
            String rankText = "#" + record.rank;
            int rankColor = switch (record.rank) {
                case 1 -> COLOR_GOLD;
                case 2 -> COLOR_SILVER;
                case 3 -> COLOR_BRONZE;
                default -> COLOR_GRAY;
            };
            com.randomrun.ui.util.GuiText.text(context, font, 
                Component.literal(rankText).withStyle(s -> s.withColor(rankColor)), 
                rankX, listY + 3, COLOR_WHITE);
            
            listY += 22;
        }
        
        this.playerTopRecords = top10;
    }
    
    private static class PlayerRecordEntry {
        String itemId;
        long time;
        int rank;
        int[] bounds;
        
        PlayerRecordEntry(String itemId, long time, int rank) {
            this.itemId = itemId;
            this.time = time;
            this.rank = rank;
        }
    }
    
    private String formatTime(long duration) {
        long hours = duration / (1000 * 60 * 60);
        long minutes = (duration / (1000 * 60)) % 60;
        long seconds = (duration / 1000) % 60;
        long millis = duration % 1000;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        } else {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
    }
    
    private void renderGoldBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, boolean animated) {
        context.fill(left, top, right, top + BORDER_THICKNESS, COLOR_GOLD_BORDER);
        context.fill(left, bottom - BORDER_THICKNESS, right, bottom, COLOR_GOLD_BORDER);
        context.fill(left, top, left + BORDER_THICKNESS, bottom, COLOR_GOLD_BORDER);
        context.fill(right - BORDER_THICKNESS, top, right, bottom, COLOR_GOLD_BORDER);
        
        if (animated && com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) {
            renderAnimatedGoldBorder(context, left, top, right, bottom);
        }
    }
    
    private void renderAnimatedGoldBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        long time = System.currentTimeMillis();
        float animationProgress = ((time % ANIMATION_CYCLE_MS) / (float) ANIMATION_CYCLE_MS);
        
        int width = right - left;
        int height = bottom - top;
        int perimeter = 2 * (width + height);
        float currentPos = animationProgress * perimeter;
        
        drawCircularGoldBorder(context, left, top, width, height, currentPos, GRADIENT_LENGTH, perimeter);
    }
    
    private void drawCircularGoldBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, 
                                        float currentPos, int gradientLength, int perimeter) {
        for (int i = 0; i < width; i++) {
            float dist = getCircularDistance(i, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0f - (dist / gradientLength);
                int color = lerpColor(COLOR_GOLD_TEXT, COLOR_WHITE, intensity);
                context.fill(x + i, y, x + i + 1, y + BORDER_THICKNESS, 0xFF000000 | color);
            }
        }
        
        int rightStart = width;
        for (int i = 0; i < height; i++) {
            float dist = getCircularDistance(rightStart + i, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0f - (dist / gradientLength);
                int color = lerpColor(COLOR_GOLD_TEXT, COLOR_WHITE, intensity);
                context.fill(x + width - BORDER_THICKNESS, y + i, x + width, y + i + 1, 0xFF000000 | color);
            }
        }
        
        int bottomStart = width + height;
        for (int i = 0; i < width; i++) {
            float dist = getCircularDistance(bottomStart + i, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0f - (dist / gradientLength);
                int color = lerpColor(COLOR_GOLD_TEXT, COLOR_WHITE, intensity);
                context.fill(x + width - i - 1, y + height - BORDER_THICKNESS, x + width - i, y + height, 0xFF000000 | color);
            }
        }
        
        int leftStart = 2 * width + height;
        for (int i = 0; i < height; i++) {
            float dist = getCircularDistance(leftStart + i, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0f - (dist / gradientLength);
                int color = lerpColor(COLOR_GOLD_TEXT, COLOR_WHITE, intensity);
                context.fill(x, y + height - i - 1, x + BORDER_THICKNESS, y + height - i, 0xFF000000 | color);
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
    
    private int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        
        return (r << 16) | (g << 8) | b;
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            return true;
        }
        
        if (selectedTarget == null && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !playerTopRecords.isEmpty()) {
            for (PlayerRecordEntry record : playerTopRecords) {
                if (record.bounds != null) {
                    int left = record.bounds[0];
                    int top = record.bounds[1];
                    int right = record.bounds[2];
                    int bottom = record.bounds[3];
                    
                    if (mouseX >= left && mouseX <= right && mouseY >= top && mouseY < bottom) {
                        selectedTarget = record.itemId;
                        showCachedRecords(record.itemId);
                        return true;
                    }
                }
            }
        }
        
        int gridX = PADDING + 10;
        int gridY = PADDING + 70;
        int gridWidth = LEFT_PANEL_WIDTH - 20;
        int panelBottom = height - 50;
        int gridHeight = panelBottom - gridY - 30;
        
        {
            int itemsPerRow = ITEMS_PER_ROW;
            int totalRows = (int) Math.ceil((double) filteredTargets.size() / itemsPerRow);
            
            if (totalRows * (ITEM_SIZE + ITEM_SPACING) > gridHeight) {
                int scrollBarX = gridX + gridWidth - 4;
                
                if (mouseX >= scrollBarX - 3 && mouseX <= scrollBarX + 6 && 
                    mouseY >= gridY && mouseY <= gridY + gridHeight) {
                    isDraggingScrollbar = true;
                    dragStartY = (int) mouseY;
                    dragStartScrollOffset = itemGridScrollOffset;
                    return true;
                }
            }
        }
        
        int scrollBarX = gridX + gridWidth - 4;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mouseX >= gridX && mouseX < scrollBarX - 3 && 
            mouseY >= gridY && mouseY < gridY + gridHeight) {
            int itemsPerRow = ITEMS_PER_ROW;
            int col = (int) ((mouseX - gridX) / (ITEM_SIZE + ITEM_SPACING));
            int row = (int) ((mouseY - gridY + smoothScrollOffset) / (ITEM_SIZE + ITEM_SPACING));
            int index = row * itemsPerRow + col;
            
            if (index >= 0 && index < filteredTargets.size()) {
                String itemId = filteredTargets.get(index);
                if (!itemId.equals(selectedTarget)) {
                    selectedTarget = itemId;
                    showCachedRecords(itemId);
                }
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        
        int gridX = PADDING + 10;
        int gridY = PADDING + 70;
        int gridWidth = LEFT_PANEL_WIDTH - 20;
        int panelBottom = height - 50;
        int gridHeight = panelBottom - gridY - 30;
        
        if (mouseX >= gridX && mouseX < gridX + gridWidth && 
            mouseY >= gridY && mouseY < gridY + gridHeight) {
            int itemsPerRow = ITEMS_PER_ROW;
            int totalRows = (int) Math.ceil((double) filteredTargets.size() / itemsPerRow);
            int maxScroll = Math.max(0, totalRows * (ITEM_SIZE + ITEM_SPACING) - gridHeight);
            
            itemGridScrollOffset = (int) Math.max(0, Math.min(maxScroll, itemGridScrollOffset - verticalAmount * 20));
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()));
    }
    
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (isDraggingScrollbar) {
            int gridY = PADDING + 70;
            int panelBottom = height - 50;
            int gridHeight = panelBottom - gridY - 30;
            
            int itemsPerRow = ITEMS_PER_ROW;
            int totalRows = (int) Math.ceil((double) filteredTargets.size() / itemsPerRow);
            int maxScroll = Math.max(0, totalRows * (ITEM_SIZE + ITEM_SPACING) - gridHeight);
            
            int mouseDelta = (int) mouseY - dragStartY;
            int scrollDelta = (int) ((float) mouseDelta / gridHeight * maxScroll);
            
            itemGridScrollOffset = Math.max(0, Math.min(maxScroll, dragStartScrollOffset + scrollDelta));
            return true;
        }
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), deltaX, deltaY);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public void onClose() {
        GlobalParticleSystem.getInstance().setYellowMode(false);
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }
    
    @Override
    public void removed() {
        leaderboardsCache.clear();
        itemsWithRecords.clear();
        allLeaderboardsLoaded = false;
        GlobalParticleSystem.getInstance().setYellowMode(false);
        super.removed();
    }
    
    private void renderTarget(GuiGraphicsExtractor context, String targetId, int x, int y, int size, float mouseX, float mouseY) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(targetId));
        context.item(com.randomrun.ui.util.PreviewItems.stack(item), x, y);
    }
    
    private void renderTargetTooltip(GuiGraphicsExtractor context, String targetId, int mouseX, int mouseY) {
        String displayName = getTargetDisplayName(targetId);
        if (displayName != null) {
            context.setTooltipForNextFrame(font, Component.literal(displayName), mouseX, mouseY);
        }
    }
    
    private String getTargetDisplayName(String targetId) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(targetId));
        return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
    }
}

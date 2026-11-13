package com.randomrun.ui.screen.main;

import com.randomrun.core.run.Run;
import com.randomrun.core.run.Run.RunResult;
import com.randomrun.core.run.RunDataManager;
import com.randomrun.core.run.RunHistory;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RunHistoryScreen extends AbstractRandomRunScreen {
    private static final int ITEMS_PER_PAGE = 5;
    private static final int ITEM_HEIGHT = 42;
    private static final int HEADER_HEIGHT = 48;
    private static final int LIST_X = 30;
    private static final int HEADER_X = 30;
    private static final int HEADER_Y = 5;

    private static final long MAX_VALID_TIME = 359999000L;
    private static final int MAX_SEED_LENGTH = 20;
    private static final int SEED_TRUNCATE_LENGTH = 17;

    private static final int COLOR_BG_HEADER = 0xB0000000;
    private static final int COLOR_BG_LIST = 0x60000000;
    private static final int COLOR_BG_CARD = 0x60101018;
    private static final int COLOR_BG_CARD_HOVER = 0x70181828;
    private static int COLOR_BORDER() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonBase; }
    private static int COLOR_BORDER_HOVER() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
    private static int COLOR_ACCENT() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
    private static final int COLOR_SCROLLBAR_BG = 0x30000000;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0x888888;
    private static final int COLOR_TEXT_LIGHT_GRAY = 0xAAAAAA;

    private static final int COLOR_SUCCESS = 0x55FF55;
    private static final int COLOR_WARNING = 0xFFFF55;
    private static final int COLOR_DANGER = 0xFF5555;
    private static final int COLOR_TIMEOUT = 0xFFAA00;
    private static final int COLOR_BADGE_HC = 0xFFAA0000;
    private static final int COLOR_BADGE_BATTLE = 0xFF0055AA;

    private final Screen parent;
    private final String targetId;
    private final boolean upgrader;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private int scrollOffset = 0;
    private double scrollAmount = 0;
    private FilterType currentFilter = FilterType.ALL;
    private List<Run> filteredHistory;

    private HeaderStats headerStats;

    private enum FilterType {
        ALL, WINS, LOSSES, SOLO, ONLINE
    }

    private record HeaderStats(
        ItemStack targetStack,
        String itemName,
        String totalRuns,
        String winRate,
        int winRateColor,
        String winLoss,
        String bestTime,
        String avgTime,
        String recordsPrefix
    ) {}

    public RunHistoryScreen(Screen parent, RunDataManager.RunResult runResult) {
        this(parent, runResult, false);
    }

    public RunHistoryScreen(Screen parent, RunDataManager.RunResult runResult, boolean upgrader) {
        super(LanguageManager.text("randomrun.screen.history.title"));
        this.parent = parent;
        this.targetId = runResult.itemId;
        this.upgrader = upgrader;
    }

    @Override
    protected void init() {
        super.init();
        initializeHeaderStats();
        initializeUI();
        updateFilteredList();
    }

    private void initializeHeaderStats() {
        ItemStack targetStack;
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(targetId));
            targetStack = com.randomrun.ui.util.Previews.PreviewItems.stack(item);
        } catch (Exception e) {
            targetStack = ItemStack.EMPTY;
        }

        String itemName = "§f§l" + getItemName(targetId);

        List<Run> allRecords = RunHistory.getInstance().getRecordsForTarget(targetId).stream()
            .filter(record -> record.isUpgrader() == upgrader).toList();
        int totalRuns = allRecords.size();
        int wins = (int) allRecords.stream().filter(Run::isVictory).count();
        int losses = totalRuns - wins;
        float winRate = totalRuns > 0 ? (wins * 100.0f / totalRuns) : 0;

        long bestTime = allRecords.stream()
            .filter(Run::isVictory)
            .mapToLong(Run::getDuration)
            .filter(d -> d > 0 && d <= MAX_VALID_TIME)
            .min().orElse(-1);

        long avgTime = 0;
        long avgCount = allRecords.stream()
            .filter(Run::isVictory)
            .filter(r -> r.getDuration() > 0 && r.getDuration() <= MAX_VALID_TIME)
            .count();

        if (avgCount > 0) {
            avgTime = allRecords.stream()
                .filter(Run::isVictory)
                .mapToLong(Run::getDuration)
                .filter(d -> d > 0 && d <= MAX_VALID_TIME)
                .sum() / avgCount;
        }

        String totalRunsText = LanguageManager.translate("randomrun.stats.runs", totalRuns);
        String winRateText = LanguageManager.translate("randomrun.stats.winrate", String.format("%.0f%%", winRate));
        int winRateColor = winRate >= 50 ? COLOR_SUCCESS : winRate >= 25 ? COLOR_WARNING : COLOR_DANGER;
        String winLossText = "§a" + wins + "W §c" + losses + "L";

        String bestTimeText = bestTime > 0 
            ? LanguageManager.translate("randomrun.stats.best_time", RunDataManager.formatTime(bestTime))
            : null;

        String avgTimeText = avgTime > 0
            ? LanguageManager.translate("randomrun.stats.avg_time", RunDataManager.formatTime(avgTime))
            : null;

        String recordsPrefix = "§8" + LanguageManager.translate("randomrun.screen.history.subtitle", getItemName(targetId)) + " §7(";

        headerStats = new HeaderStats(targetStack, itemName, totalRunsText, winRateText, 
            winRateColor, winLossText, bestTimeText, avgTimeText, recordsPrefix);
    }

    private void initializeUI() {
        int centerX = width / 2;
        int buttonsY = HEADER_HEIGHT + 10;

        int btnWidth = 60;
        int btnHeight = 16;
        int spacing = 4;
        int startX = centerX - (5 * btnWidth + 4 * spacing) / 2;

        addRenderableWidget(createFilterButton(startX, buttonsY, btnWidth, btnHeight, 
            "randomrun.history.filter.all", FilterType.ALL));
        addRenderableWidget(createFilterButton(startX + btnWidth + spacing, buttonsY, btnWidth, btnHeight, 
            "randomrun.history.filter.wins", FilterType.WINS));
        addRenderableWidget(createFilterButton(startX + 2 * (btnWidth + spacing), buttonsY, btnWidth, btnHeight, 
            "randomrun.history.filter.losses", FilterType.LOSSES));
        addRenderableWidget(createFilterButton(startX + 3 * (btnWidth + spacing), buttonsY, btnWidth, btnHeight, 
            "randomrun.history.filter.solo", FilterType.SOLO));
        addRenderableWidget(createFilterButton(startX + 4 * (btnWidth + spacing), buttonsY, btnWidth, btnHeight, 
            "randomrun.history.filter.online", FilterType.ONLINE));

        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"),
            button -> Minecraft.getInstance().gui.setScreen(parent)
        ));
    }

    private ButtonDefault createFilterButton(int x, int y, int w, int h, String labelKey, FilterType type) {
        return new ButtonDefault(x, y, w, h, LanguageManager.text(labelKey), button -> {
            this.currentFilter = type;
            this.scrollOffset = 0;
            this.scrollAmount = 0;
            updateFilteredList();
        });
    }

    private void updateFilteredList() {
        List<Run> allHistory = new ArrayList<>(RunHistory.getInstance().getRecordsForTarget(targetId));
        allHistory.removeIf(record -> record.isUpgrader() != upgrader);

        allHistory.sort((a, b) -> {
            boolean aValid = a.isVictory() && a.getDuration() > 0 && a.getDuration() <= MAX_VALID_TIME;
            boolean bValid = b.isVictory() && b.getDuration() > 0 && b.getDuration() <= MAX_VALID_TIME;

            if (aValid && bValid) {
                return Long.compare(a.getDuration(), b.getDuration());
            }
            if (aValid) return -1;
            if (bValid) return 1;

            return Long.compare(b.getTimestamp(), a.getTimestamp());
        });

        if (currentFilter == FilterType.ALL) {
            filteredHistory = allHistory;
        } else {
            filteredHistory = allHistory.stream()
                .filter(record -> switch (currentFilter) {
                    case WINS -> record.getResult() == RunResult.VICTORY;
                    case LOSSES -> record.getResult() != RunResult.VICTORY;
                    case SOLO -> !record.isBattle();
                    case ONLINE -> record.isBattle();
                    default -> true;
                })
                .collect(Collectors.toList());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        renderStatsHeader(context);
        renderHistoryList(context, mouseX, mouseY);
        drawFilterUnderline(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderStatsHeader(GuiGraphicsExtractor context) {
        int headerW = width - 60;
        context.fill(HEADER_X, HEADER_Y, HEADER_X + headerW, HEADER_Y + HEADER_HEIGHT, COLOR_BG_HEADER);
        context.fill(HEADER_X, HEADER_Y + HEADER_HEIGHT, HEADER_X + headerW, HEADER_Y + HEADER_HEIGHT + 1, COLOR_ACCENT());
        if (headerStats.targetStack != null && !headerStats.targetStack.isEmpty()) {
            com.randomrun.ui.util.Previews.ItemPreviewRenderer.extractLargeGui(context, headerStats.targetStack, HEADER_X + 8 + 16, HEADER_Y + 8 + 16, 32);
        }
        int textX = HEADER_X + 50;
        com.randomrun.ui.util.GuiText.text(context, font, headerStats.itemName, textX, HEADER_Y + 9, COLOR_TEXT_WHITE);
        int tagX = textX + font.width(headerStats.itemName) + 6;
        if (upgrader) tagX += com.randomrun.ui.util.Ui.chip(context, font, net.minecraft.network.chat.Component.literal("RRM Upgrader"),
            tagX, HEADER_Y + 6, 0xCC713F12, 0xFFFDE68A) + 4;
        String count = (filteredHistory != null ? filteredHistory.size() : 0) + " " + LanguageManager.translate("randomrun.stats.records");
        com.randomrun.ui.util.GuiText.text(context, font, count, HEADER_X + headerW - 8 - font.width(count), HEADER_Y + 9, COLOR_TEXT_GRAY);

        java.util.List<String> cells = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        cells.add(headerStats.totalRuns); colors.add(COLOR_TEXT_LIGHT_GRAY);
        cells.add(headerStats.winRate); colors.add(headerStats.winRateColor);
        cells.add(headerStats.winLoss); colors.add(COLOR_TEXT_LIGHT_GRAY);
        if (headerStats.bestTime != null) { cells.add(headerStats.bestTime); colors.add(COLOR_SUCCESS); }
        if (headerStats.avgTime != null) { cells.add(headerStats.avgTime); colors.add(COLOR_WARNING); }
        int available = headerW - 50 - 8;
        int total = 0;
        for (String cell : cells) total += font.width(cell);
        int minGap = 12;
        float scale = Math.min(1f, available / (float) (total + minGap * (cells.size() - 1)));
        float gap = cells.size() > 1 ? Math.min(40f, (available / scale - total) / (cells.size() - 1)) : 0;
        context.pose().pushMatrix();
        context.pose().translate(textX, HEADER_Y + 28);
        context.pose().scale(scale, scale);
        float cursor = 0;
        for (int i = 0; i < cells.size(); i++) {
            com.randomrun.ui.util.GuiText.text(context, font, cells.get(i), Math.round(cursor), 0, colors.get(i));
            cursor += font.width(cells.get(i)) + gap;
        }
        context.pose().popMatrix();
    }

    private void renderHistoryList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int listY = HEADER_HEIGHT + 32;
        int listWidth = width - 60;

        context.fill(LIST_X, listY, LIST_X + listWidth, listY + ITEMS_PER_PAGE * ITEM_HEIGHT, COLOR_BG_LIST);

        List<Run> history = filteredHistory;
        if (history == null) {
             updateFilteredList();
             history = filteredHistory;
        }

        if (history.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                LanguageManager.text("randomrun.screen.history.empty"), 
                width / 2, listY + 50, COLOR_TEXT_GRAY);
            return;
        }

        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            renderHistoryItem(context, mouseX, mouseY, history.get(i), i, listY, listWidth);
        }

        renderScrollbar(context, history, listY, listWidth);
    }

    private static final int RUN_NUMBER_WIDTH = 28;

    private void renderHistoryItem(GuiGraphicsExtractor context, int mouseX, int mouseY, Run record, 
                                   int index, int listY, int listWidth) {
        int localIndex = index - scrollOffset;
        int y = listY + localIndex * ITEM_HEIGHT;

        boolean hovered = mouseX >= LIST_X && mouseX <= LIST_X + listWidth &&
                          mouseY >= y && mouseY < y + ITEM_HEIGHT - 2;

        int bgColor = hovered ? COLOR_BG_CARD_HOVER : COLOR_BG_CARD;
        int borderColor = hovered ? COLOR_BORDER_HOVER() : COLOR_BORDER();

        String runNumber = "#" + record.getAttemptNumber();
        int numberWidth = font.width(runNumber);
        int numberX = LIST_X + (RUN_NUMBER_WIDTH - numberWidth) / 2;
        int numberY = y + (ITEM_HEIGHT - 2) / 2 - 4;
        context.fill(LIST_X, y, LIST_X + RUN_NUMBER_WIDTH, y + ITEM_HEIGHT - 2, 0x40000000);
        com.randomrun.ui.util.GuiText.text(context, font, runNumber, numberX, numberY, COLOR_TEXT_GRAY);

        int entryX = LIST_X + RUN_NUMBER_WIDTH;
        context.fill(entryX, y, LIST_X + listWidth, y + ITEM_HEIGHT - 2, bgColor);
        context.fill(entryX, y, LIST_X + listWidth, y + 1, borderColor);
        context.fill(entryX, y + ITEM_HEIGHT - 3, LIST_X + listWidth, y + ITEM_HEIGHT - 2, 0x30FFFFFF);

        int barColor = getStatusColor(record.getResult());
        context.fill(entryX, y, entryX + 3, y + ITEM_HEIGHT - 2, (0xFF << 24) | barColor);

        renderItemRow1(context, record, y, listWidth);
        renderItemRow2(context, record, y, listWidth);
        String seed = record.getWorldSeed();
        if (hovered && seed != null && !seed.isEmpty() && !seed.equals("0")) {
            String hint = LanguageManager.translate("randomrun.screen.history.click_copy");
            String shown = seed.length() > MAX_SEED_LENGTH ? seed.substring(0, SEED_TRUNCATE_LENGTH) + "..." : seed;
            int hintX = LIST_X + RUN_NUMBER_WIDTH + 10 + font.width(LanguageManager.translate("randomrun.screen.history.seed") + ": " + shown) + 10;
            com.randomrun.ui.util.GuiText.text(context, font, "§8· " + hint, hintX, y + 22, COLOR_TEXT_GRAY);
        }
    }

    private void renderItemRow1(GuiGraphicsExtractor context, Run record, int y, int listWidth) {
        int ox = LIST_X + RUN_NUMBER_WIDTH;
        String statusIcon = getStatusIcon(record.getResult());
        com.randomrun.ui.util.GuiText.text(context, font, statusIcon, ox + 10, y + 6, COLOR_TEXT_WHITE);

        String durationText;
        int durationColor;
        if (record.getResult() == RunResult.VICTORY && record.getDuration() > 0 && record.getDuration() <= MAX_VALID_TIME) {
            durationText = RunDataManager.formatTime(record.getDuration());
            durationColor = COLOR_SUCCESS;
        } else {
            durationText = getStatusLabel(record.getResult());
            durationColor = getStatusColor(record.getResult());
        }
        com.randomrun.ui.util.GuiText.text(context, font, durationText, ox + 80, y + 6, durationColor);

        String dateText = dateFormat.format(new Date(record.getTimestamp()));
        int dateWidth = font.width(dateText);
        com.randomrun.ui.util.GuiText.text(context, font, dateText, LIST_X + listWidth - dateWidth - 10, y + 6, COLOR_TEXT_GRAY);
    }

    private void renderItemRow2(GuiGraphicsExtractor context, Run record, int y, int listWidth) {
        int ox = LIST_X + RUN_NUMBER_WIDTH;
        String seedStr = record.getWorldSeed();
        if (seedStr == null || seedStr.isEmpty() || seedStr.equals("0")) {
            seedStr = "—";
        }
        if (seedStr.length() > MAX_SEED_LENGTH) {
            seedStr = seedStr.substring(0, SEED_TRUNCATE_LENGTH) + "...";
        }
        String seedLabel = LanguageManager.translate("randomrun.screen.history.seed") + ": ";
        String seedFull = "§8" + seedLabel + "§7" + seedStr;
        com.randomrun.ui.util.GuiText.text(context, font, seedFull, ox + 10, y + 22, COLOR_TEXT_LIGHT_GRAY);

        renderBadges(context, record, y, listWidth);
    }

    private void renderBadges(GuiGraphicsExtractor context, Run record, int y, int listWidth) {
        int badgeX = LIST_X + listWidth - 10;

        if (record.isUpgrader()) {
            badgeX = renderBadge(context, "UPGRADER", badgeX, y, 0xFF857022);
        }

        if (record.isHardcore()) {
            badgeX = renderBadge(context, "☠ HC", badgeX, y, COLOR_BADGE_HC);
        }

        if (record.isBattle()) {
            renderBadge(context, "BATTLE", badgeX, y, COLOR_BADGE_BATTLE);
        }
    }

    private int renderBadge(GuiGraphicsExtractor context, String text, int rightX, int y, int bgColor) {
        int width = font.width(text) + 6;
        int x = rightX - width - 4;
        context.fill(x, y + 20, x + width, y + 32, bgColor);
        com.randomrun.ui.util.GuiText.text(context, font, text, x + 3, y + 22, COLOR_TEXT_WHITE);
        return x;
    }

    private void renderScrollbar(GuiGraphicsExtractor context, List<Run> history, int listY, int listWidth) {
        if (history.size() <= ITEMS_PER_PAGE) return;

        int maxScroll = history.size() - ITEMS_PER_PAGE;
        float scrollRatio = scrollOffset / (float) maxScroll;

        int scrollBarX = LIST_X + listWidth + 4;
        int scrollBarHeight = ITEMS_PER_PAGE * ITEM_HEIGHT;
        int thumbHeight = Math.max(20, scrollBarHeight / (history.size() / ITEMS_PER_PAGE + 1));
        int thumbY = listY + (int) ((scrollBarHeight - thumbHeight) * scrollRatio);

        context.fill(scrollBarX, listY, scrollBarX + 4, listY + scrollBarHeight, COLOR_SCROLLBAR_BG);
        context.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, COLOR_ACCENT());
    }

    private void drawFilterUnderline(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int buttonsY = HEADER_HEIGHT + 10 + 16 + 2;
        int btnWidth = 60;
        int spacing = 4;
        int startX = centerX - (5 * btnWidth + 4 * spacing) / 2;

        int filterIndex = switch (currentFilter) {
            case ALL -> 0;
            case WINS -> 1;
            case LOSSES -> 2;
            case SOLO -> 3;
            case ONLINE -> 4;
        };

        int x = startX + filterIndex * (btnWidth + spacing);
        context.fill(x, buttonsY, x + btnWidth, buttonsY + 2, COLOR_ACCENT());
    }

    private String getStatusIcon(RunResult result) {
        if (result == null) return "?";
        return switch (result) {
            case VICTORY -> "§a✔ " + LanguageManager.translate("randomrun.screen.history.victory");
            case DEATH -> "§c☠ " + LanguageManager.translate("randomrun.screen.history.death");
            case GAVE_UP -> "§e✖ " + LanguageManager.translate("randomrun.screen.history.gave_up");
            case TIMEOUT -> "§6⏰ " + LanguageManager.translate("randomrun.screen.history.timeout");
            case FAILED -> "§4✘ " + LanguageManager.translate("randomrun.screen.history.failed");
        };
    }

    private String getStatusLabel(RunResult result) {
        if (result == null) return "?";
        return switch (result) {
            case DEATH -> LanguageManager.translate("randomrun.screen.history.death");
            case GAVE_UP -> LanguageManager.translate("randomrun.screen.history.gave_up");
            case TIMEOUT -> LanguageManager.translate("randomrun.screen.defeat.title");
            case FAILED -> LanguageManager.translate("randomrun.screen.history.failed");
            default -> "—";
        };
    }

    private int getStatusColor(RunResult result) {
        if (result == null) return COLOR_TEXT_WHITE;
        return switch (result) {
            case VICTORY -> COLOR_SUCCESS;
            case DEATH -> COLOR_DANGER;
            case GAVE_UP -> COLOR_WARNING;
            case TIMEOUT -> COLOR_TIMEOUT;
            default -> COLOR_TEXT_LIGHT_GRAY;
        };
    }

    private String getItemName(String itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (item == net.minecraft.world.item.Items.AIR) return itemId;
            return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
        } catch (Exception e) {
            return itemId;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && filteredHistory != null) {
            int listY = HEADER_HEIGHT + 32;

            int ox = LIST_X + RUN_NUMBER_WIDTH;

            int listWidth = width - 60;
            for (int i = scrollOffset; i < Math.min(scrollOffset + ITEMS_PER_PAGE, filteredHistory.size()); i++) {
                int y = listY + (i - scrollOffset) * ITEM_HEIGHT;
                if (mouseX < LIST_X || mouseX > LIST_X + listWidth || mouseY < y || mouseY >= y + ITEM_HEIGHT - 2) continue;
                String seedStr = filteredHistory.get(i).getWorldSeed();
                if (seedStr == null || seedStr.isEmpty() || seedStr.equals("0")) return true;
                Minecraft.getInstance().keyboardHandler.setClipboard(seedStr);
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.3f));
                com.randomrun.ui.overlay.CustomNotification.show(
                    LanguageManager.translate("randomrun.screen.history.seed_copied"), seedStr, "minecraft:paper");
                return true;
            }
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (filteredHistory == null) return false;

        int maxScroll = Math.max(0, filteredHistory.size() - ITEMS_PER_PAGE);
        scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - verticalAmount));
        scrollOffset = (int) scrollAmount;
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}

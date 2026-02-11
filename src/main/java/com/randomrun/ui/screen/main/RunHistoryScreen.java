package com.randomrun.ui.screen.main;

import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.util.BlurHandler;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class RunHistoryScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final RunDataManager.RunResult runResult;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    
    private int scrollOffset = 0;
    private double scrollAmount = 0;
    private static final int ITEMS_PER_PAGE = 6;
    private static final int ITEM_HEIGHT = 45;

    public RunHistoryScreen(Screen parent, RunDataManager.RunResult runResult) {
        super(Text.translatable("randomrun.screen.history.title"));
        this.parent = parent;
        this.runResult = runResult;
    }

    private enum FilterType {
        ALL, WINS, LOSSES, SOLO, ONLINE
    }
    
    private FilterType currentFilter = FilterType.ALL;
    private List<RunDataManager.RunResult.AttemptRecord> filteredHistory;

    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int listY = 65; // Start of list
        int buttonsY = 35;
        
        // Filter Buttons
        int btnWidth = 60;
        int btnHeight = 20;
        int spacing = 5;
        int startX = centerX - (5 * btnWidth + 4 * spacing) / 2;
        
        addDrawableChild(createFilterButton(startX, buttonsY, btnWidth, btnHeight, "randomrun.history.filter.all", FilterType.ALL));
        addDrawableChild(createFilterButton(startX + btnWidth + spacing, buttonsY, btnWidth, btnHeight, "randomrun.history.filter.wins", FilterType.WINS));
        addDrawableChild(createFilterButton(startX + 2 * (btnWidth + spacing), buttonsY, btnWidth, btnHeight, "randomrun.history.filter.losses", FilterType.LOSSES));
        addDrawableChild(createFilterButton(startX + 3 * (btnWidth + spacing), buttonsY, btnWidth, btnHeight, "randomrun.history.filter.solo", FilterType.SOLO));
        addDrawableChild(createFilterButton(startX + 4 * (btnWidth + spacing), buttonsY, btnWidth, btnHeight, "randomrun.history.filter.online", FilterType.ONLINE));

        // Back button
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent)
        ));
        
        updateFilteredList();
    }
    
    private ButtonDefault createFilterButton(int x, int y, int w, int h, String labelKey, FilterType type) {
        ButtonDefault btn = new ButtonDefault(x, y, w, h, Text.translatable(labelKey), button -> {
            this.currentFilter = type;
            this.scrollOffset = 0;
            this.scrollAmount = 0;
            updateFilteredList();
        });
        return btn;
    }
    
    private void updateFilteredList() {
        List<RunDataManager.RunResult.AttemptRecord> allHistory = runResult.attemptsHistory;
        // Sort first
        allHistory.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        
        if (currentFilter == FilterType.ALL) {
            filteredHistory = allHistory;
        } else {
            filteredHistory = new java.util.ArrayList<>();
            for (RunDataManager.RunResult.AttemptRecord record : allHistory) {
                boolean match = false;
                switch (currentFilter) {
                    case WINS: match = "VICTORY".equals(record.result); break;
                    case LOSSES: match = !"VICTORY".equals(record.result); break;
                    case SOLO: match = !record.isOnline; break;
                    case ONLINE: match = record.isOnline; break;
                    default: match = true;
                }
                if (match) filteredHistory.add(record);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw parent screen in background (blurred)
        if (parent != null) {
            // We can't easily render the parent screen without artifacts or complexity,
            // so we just render a dark blurred background
            renderGradientBackground(context);
            // Optional: apply extra blur or darkening if we had shaders
        } else {
            renderGradientBackground(context);
        }

        // Title
        String itemName = getItemName(runResult.itemId);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.screen.history.subtitle", itemName), 
            width / 2, 10, 0xFFFFFF);

        // Render List
        renderHistoryList(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHistoryList(DrawContext context, int mouseX, int mouseY) {
        int listX = 40;
        int listY = 65; // Moved down to accommodate buttons
        int listWidth = width - 80;
        // int listHeight = height - 100; // Adjusted height
        
        // Background for list area
        context.fill(listX, listY, listX + listWidth, listY + ITEMS_PER_PAGE * ITEM_HEIGHT, 0x80000000);
        
        // Полупрозрачный фон ниже истории (запрос 2)
        int historyBottom = listY + ITEMS_PER_PAGE * ITEM_HEIGHT;
        if (historyBottom < height) {
             context.fill(listX, historyBottom, listX + listWidth, height - 35, 0x60000000);
        }
        
        List<RunDataManager.RunResult.AttemptRecord> history = filteredHistory;
        if (history == null) { // Fallback if init order issue
             updateFilteredList();
             history = filteredHistory;
        }

        if (history.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.screen.history.empty"), 
                width / 2, listY + 50, 0x888888);
            return;
        }

        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int y = listY + localIndex * ITEM_HEIGHT;
            RunDataManager.RunResult.AttemptRecord record = history.get(i);
            
            // Row background
            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth &&
                              mouseY >= y && mouseY < y + ITEM_HEIGHT;
            int bgColor = hovered ? 0x40FFFFFF : (i % 2 == 0 ? 0x20000000 : 0x10000000);
            context.fill(listX, y, listX + listWidth, y + ITEM_HEIGHT, bgColor);
            
            // 1. Result Status Icon/Text
            String statusText = getStatusText(record.result);
            int statusColor = getStatusColor(record.result);
            context.drawTextWithShadow(textRenderer, statusText, listX + 10, y + 10, statusColor);
            
            // 2. Duration
            String durationText;
            int durationColor = 0xFFFFFF;
            
            // Если статус не "VICTORY" или время аномально большое/малое (признак проигрыша/бага)
            // ИЛИ если статус явно указывает на поражение
            if (!"VICTORY".equals(record.result) || record.duration <= 0 || record.duration > 359999000L) { // > 99 hours considered anomaly/loss
                 durationText = Text.translatable("randomrun.screen.defeat.title").getString();
                 durationColor = 0xFFFF5555; // Красный
            } else {
                 durationText = RunDataManager.formatTime(record.duration);
            }
            
            context.drawTextWithShadow(textRenderer, durationText, listX + 10, y + 25, durationColor);
            
            // 3. Date
            String dateText = dateFormat.format(new Date(record.timestamp));
            context.drawTextWithShadow(textRenderer, dateText, listX + 100, y + 10, 0xAAAAAA);
            
            // 4. Seed
            String seedLabel = Text.translatable("randomrun.screen.history.seed").getString();
            String seedText = seedLabel + ": " + (record.seed != null ? record.seed : "???");
            if (seedText.length() > 25) seedText = seedText.substring(0, 22) + "...";
            context.drawTextWithShadow(textRenderer, seedText, listX + 100, y + 25, 0xAAAAAA);
            
            // 5. Online Badge
            if (record.isOnline) {
                int badgeX = listX + listWidth - 60;
                int badgeY = y + 12;
                context.fill(badgeX, badgeY, badgeX + 50, badgeY + 14, 0xFF00AA00);
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.screen.history.online").getString(), badgeX + 25, badgeY + 3, 0xFFFFFF);
            }
        }
        
        // Scrollbar
        if (history.size() > ITEMS_PER_PAGE) {
            int maxScroll = history.size() - ITEMS_PER_PAGE;
            float scrollRatio = scrollOffset / (float) maxScroll;
            
            int scrollBarX = listX + listWidth + 5;
            int scrollBarHeight = ITEMS_PER_PAGE * ITEM_HEIGHT;
            int thumbHeight = Math.max(20, scrollBarHeight / (history.size() / ITEMS_PER_PAGE + 1));
            int thumbY = listY + (int) ((scrollBarHeight - thumbHeight) * scrollRatio);
            
            context.fill(scrollBarX, listY, scrollBarX + 6, listY + scrollBarHeight, 0x40000000);
            context.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight, 0xFF6930c3);
        }
        
        // Draw active filter underline
        drawFilterUnderline(context);
    }
    
    private void drawFilterUnderline(DrawContext context) {
        int centerX = width / 2;
        int buttonsY = 35 + 20 + 2; // Under button
        int btnWidth = 60;
        int spacing = 5;
        int startX = centerX - (5 * btnWidth + 4 * spacing) / 2;
        
        int filterIndex = 0;
        switch (currentFilter) {
            case ALL: filterIndex = 0; break;
            case WINS: filterIndex = 1; break;
            case LOSSES: filterIndex = 2; break;
            case SOLO: filterIndex = 3; break;
            case ONLINE: filterIndex = 4; break;
        }
        
        int x = startX + filterIndex * (btnWidth + spacing);
        context.fill(x, buttonsY, x + btnWidth, buttonsY + 2, 0xFFFFFFFF);
    }
    
    private String getStatusText(String result) {
        if (result == null) return "?";
        switch (result) {
            case "VICTORY": return "§a" + Text.translatable("randomrun.screen.history.victory").getString();
            case "DEATH": return "§c" + Text.translatable("randomrun.screen.history.death").getString();
            case "GAVE_UP": return "§e" + Text.translatable("randomrun.screen.history.gave_up").getString();
            case "FAILED": return "§4" + Text.translatable("randomrun.screen.history.failed").getString();
            default: return result;
        }
    }
    
    private int getStatusColor(String result) {
        if (result == null) return 0xFFFFFF;
        switch (result) {
            case "VICTORY": return 0x55FF55;
            case "DEATH": return 0xFF5555;
            case "GAVE_UP": return 0xFFFF55;
            default: return 0xAAAAAA;
        }
    }
    
    private String getItemName(String itemId) {
        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            if (item == net.minecraft.item.Items.AIR) return itemId;
            return item.getName().getString();
        } catch (Exception e) {
            return itemId;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<RunDataManager.RunResult.AttemptRecord> history = filteredHistory;
        if (history == null) return false;
        
        int maxScroll = Math.max(0, history.size() - ITEMS_PER_PAGE);
        scrollAmount = scrollAmount - verticalAmount;
        
        if (scrollAmount < 0) scrollAmount = 0;
        if (scrollAmount > maxScroll) scrollAmount = maxScroll;
        
        scrollOffset = (int) scrollAmount;
        return true;
    }
    
    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}

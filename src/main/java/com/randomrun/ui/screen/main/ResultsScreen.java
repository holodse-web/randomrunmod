package com.randomrun.ui.screen.main;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.randomrun.ui.screen.main.RunHistoryScreen;

public class ResultsScreen extends AbstractRandomRunScreen {
    private TextFieldStyled searchField;
    private List<RunDataManager.RunResult> filteredResults = new ArrayList<>();
    private List<RunDataManager.RunResult> allResults = new ArrayList<>();
    
    private int scrollOffset = 0;
    private double scrollAmount = 0;
    private static final int RESULTS_PER_PAGE = 8;
    private static final int RESULT_HEIGHT = 30;
    
    private SortMode sortMode = SortMode.TIME;
    
    private enum SortMode {
        TIME, ALPHABETICAL, ATTEMPTS
    }
    
    public ResultsScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.results.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        allResults = new ArrayList<>(RandomRunMod.getInstance().getRunDataManager().getAllResultsSorted());
        sortResults();
        filteredResults = new ArrayList<>(allResults);
        
        // Refresh from DB
        RandomRunMod.getInstance().getRunDataManager().refreshResultsFromLeaderboard().thenRun(() -> {
             if (MinecraftClient.getInstance() != null) {
                 MinecraftClient.getInstance().execute(() -> {
                     if (MinecraftClient.getInstance().currentScreen == this) {
                         allResults = new ArrayList<>(RandomRunMod.getInstance().getRunDataManager().getAllResultsSorted());
                         sortResults();
                         filterResults(searchField.getText());
                     }
                 });
             }
        });
        
        int centerX = width / 2;
        
        // Search field (Styled)
        searchField = new TextFieldStyled(textRenderer, centerX - 100, 30, 200, 20, Text.translatable("randomrun.search"), 0.05f);
        searchField.setCenteredPlaceholder(Text.translatable("randomrun.search.placeholder"));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);
        
        // Sort buttons
        int btnWidth = 80;
        int btnSpacing = 5;
        int totalBtnWidth = (btnWidth * 3) + (btnSpacing * 2);
        int startX = centerX - (totalBtnWidth / 2);
        int btnY = 55;

        addDrawableChild(new ButtonDefault(
            startX, btnY,
            btnWidth, 20,
            Text.translatable("randomrun.sort.time"),
            button -> {
                sortMode = SortMode.TIME;
                sortResults();
                filterResults(searchField.getText());
            }
        ));
        
        addDrawableChild(new ButtonDefault(
            startX + btnWidth + btnSpacing, btnY,
            btnWidth, 20,
            Text.translatable("randomrun.sort.name"),
            button -> {
                sortMode = SortMode.ALPHABETICAL;
                sortResults();
                filterResults(searchField.getText());
            }
        ));
        
        addDrawableChild(new ButtonDefault(
            startX + 2 * (btnWidth + btnSpacing), btnY,
            btnWidth, 20,
            Text.translatable("randomrun.sort.attempts"),
            button -> {
                sortMode = SortMode.ATTEMPTS;
                sortResults();
                filterResults(searchField.getText());
            }
        ));
        
        // Back button
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(new MainModScreen(null))
        ));
    }
    
    private void sortResults() {
        switch (sortMode) {
            case TIME:
                allResults.sort(Comparator.comparingLong(r -> r.bestTime));
                break;
            case ALPHABETICAL:
                allResults.sort(Comparator.comparing(r -> getItemName(r.itemId)));
                break;
            case ATTEMPTS:
                allResults.sort(Comparator.comparingInt(r -> -r.attempts));
                break;
        }
    }
    
    private void onSearchChanged(String text) {
        filterResults(text);
    }
    
    private void filterResults(String text) {
        filteredResults.clear();
        String searchLower = text.toLowerCase();
        
        for (RunDataManager.RunResult result : allResults) {
            String itemName = getItemName(result.itemId).toLowerCase();
            if (itemName.contains(searchLower) || result.itemId.toLowerCase().contains(searchLower)) {
                filteredResults.add(result);
            }
        }
        
        scrollOffset = 0;
        scrollAmount = 0;
    }
    
    private String getItemName(String itemId) {
        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            // Check if item is AIR or invalid
            if (item == net.minecraft.item.Items.AIR) {
                return "Unknown Item";
            }
            String name = item.getName().getString();
            // If name is empty or just whitespace, return fallback
            if (name == null || name.trim().isEmpty()) {
                return "Unknown Item";
            }
            return name;
        } catch (Exception e) {
            return itemId;
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render gradient background
        renderGradientBackground(context);
        
        // Title
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.results.title", playerName), 
            width / 2, 10, 0xFFFFFF);
        
        super.render(context, mouseX, mouseY, delta);
        
        // Render results list
        renderResultsList(context, mouseX, mouseY);
        
        // Render stats summary
        renderStatsSummary(context);
    }
    
    private void renderResultsList(DrawContext context, int mouseX, int mouseY) {
        int listX = 20;
        int listY = 85;
        int listWidth = width - 40;
        
        // Background
        context.fill(listX, listY, listX + listWidth, listY + RESULTS_PER_PAGE * RESULT_HEIGHT, 0x80000000);
        
        if (filteredResults.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.results.empty"), 
                width / 2, listY + 50, 0x888888);
            return;
        }
        
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, filteredResults.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int y = listY + localIndex * RESULT_HEIGHT;
            
            RunDataManager.RunResult result = filteredResults.get(i);
            
            // Hover highlight
            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth &&
                             mouseY >= y && mouseY < y + RESULT_HEIGHT;
            if (hovered) {
                context.fill(listX, y, listX + listWidth, y + RESULT_HEIGHT, 0x40FFFFFF);
            }
            
            // Rank number
            context.drawTextWithShadow(textRenderer, "#" + (i + 1), listX + 5, y + 10, 0x888888);
            
            // Item icon
            try {
                Item item = Registries.ITEM.get(Identifier.of(result.itemId));
                ItemStack stack = new ItemStack(item);
                context.drawItem(stack, listX + 35, y + 6);
            } catch (Exception e) {
                // Skip if item not found
            }
            
            // Item name
            String itemName = getItemName(result.itemId);
            if (itemName.length() > 25) {
                itemName = itemName.substring(0, 22) + "...";
            }
            context.drawTextWithShadow(textRenderer, itemName, listX + 60, y + 6, 0xFFFFFF);
            
            // Best time
            String timeStr;
            int timeColor = 0x55FF55;
            
            // Если время аномально большое или 0 (признак отсутствия побед/проигрыша)
            if (result.bestTime <= 0 || result.bestTime > 359999000L) { // > 99 hours considered anomaly/loss
                 timeStr = Text.translatable("randomrun.screen.defeat.title").getString();
                 timeColor = 0xFFFF5555; // Красный
            } else {
                 timeStr = RunDataManager.formatTime(result.bestTime);
            }
            
            context.drawTextWithShadow(textRenderer, timeStr, listX + 60, y + 18, timeColor);
            
            // Attempts
            String attemptsStr = result.attempts + " " + 
                (result.attempts == 1 ? "attempt" : "attempts");
            
            // Show [History] hint always or if attempts > 0
            attemptsStr += " [History]";
                
            int attemptsX = listX + listWidth - textRenderer.getWidth(attemptsStr) - 10;
            context.drawTextWithShadow(textRenderer, attemptsStr, attemptsX, y + 10, 0xAAAAAA);
        }
        
        // Scroll indicator
        if (filteredResults.size() > RESULTS_PER_PAGE) {
            int maxScroll = filteredResults.size() - RESULTS_PER_PAGE;
            float scrollRatio = scrollOffset / (float) maxScroll;
            
            int scrollBarX = listX + listWidth + 5;
            int scrollBarHeight = RESULTS_PER_PAGE * RESULT_HEIGHT;
            int thumbHeight = Math.max(20, scrollBarHeight / (filteredResults.size() / RESULTS_PER_PAGE + 1));
            int thumbY = listY + (int) ((scrollBarHeight - thumbHeight) * scrollRatio);
            
            context.fill(scrollBarX, listY, scrollBarX + 6, listY + scrollBarHeight, 0x40000000);
            context.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight, 0xFF6930c3);
        }
    }
    
    private void renderStatsSummary(DrawContext context) {
        int totalRuns = 0;
        
        for (RunDataManager.RunResult result : allResults) {
            totalRuns += result.attempts;
        }
        
        int y = height - 55;
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("randomrun.stats.total_items", allResults.size()),
            width / 2 - 80, y, 0xAAAAAA);
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("randomrun.stats.total_attempts", totalRuns),
            width / 2 + 80, y, 0xAAAAAA);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int listX = 20;
        int listY = 85;
        int listWidth = width - 40;
        
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, filteredResults.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int y = listY + localIndex * RESULT_HEIGHT;
            
            if (mouseX >= listX && mouseX <= listX + listWidth &&
                mouseY >= y && mouseY < y + RESULT_HEIGHT) {
                
                RunDataManager.RunResult result = filteredResults.get(i);
                MinecraftClient.getInstance().setScreen(new RunHistoryScreen(this, result));
                // Play click sound
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, filteredResults.size() - RESULTS_PER_PAGE);
        scrollAmount = scrollAmount - verticalAmount;
        
        // Clamp scrollAmount
        if (scrollAmount < 0) scrollAmount = 0;
        if (scrollAmount > maxScroll) scrollAmount = maxScroll;
        
        scrollOffset = (int) scrollAmount;
        return true;
    }
    
}

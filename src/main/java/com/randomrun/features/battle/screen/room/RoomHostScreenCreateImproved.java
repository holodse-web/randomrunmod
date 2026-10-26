package com.randomrun.features.battle.screen.room;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.util.ItemGlitchEffect;
import com.randomrun.ui.widget.RevealAnimationWidget;
import com.randomrun.features.challenges.classic.data.SpeedrunTarget;
import com.randomrun.features.challenges.classic.data.SpeedrunTargetProvider;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RoomHostScreenCreateImproved extends AbstractRandomRunScreen {
    private static final String MODE_SEPARATE = "sw";
    private static final String MODE_E4MC = "e4";
    private static final String MODE_RADMIN = "radmin";

    private static final int PANEL_WIDTH_SIDE = 180; 
    private static final int PANEL_WIDTH_CENTER = 220; 
    private static final int PANEL_PADDING = 15;
    private static final int PANEL_SPACING = 15;
    private static final int ITEM_SIZE = 18;
    private static final int ITEMS_PER_ROW = 9; 
    private static final int VISIBLE_ROWS = 6;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int BUTTON_WIDTH_NARROW = 140; 
    private static final int BORDER_WIDTH = 2; 
    private static final int SEPARATOR_HEIGHT = 2; 

    private static final int COLOR_PANEL_BG = 0x150030;
    private static final int COLOR_PANEL_BORDER = 0xFF6930c3;
    private static final int COLOR_PANEL_BORDER_HOVER = 0xFFA78BFA;
    private static final int COLOR_TITLE = 0xFFFFFFFF; 
    private static final int COLOR_SEPARATOR = 0xFF6930c3; 
    private static final int COLOR_ERROR = 0xFFFF5555;
    private static final int COLOR_SUCCESS = 0xFF55FF55;
    private static final int COLOR_WARNING = 0xFFFFAA00;
    private static final int COLOR_ITEM_SELECTED = 0xFF6930c3;
    private static final int COLOR_ITEM_HOVER = 0xFF4A2080;
    
    private final Screen parent;

    private TextFieldStyled passwordField;
    private TextFieldStyled searchField;
    private ButtonDefault randomItemButton;
    private ButtonDefault maxPlayersButton;
    private ButtonDefault modeButton;
    private ButtonDefault randomDropButton;
    private ButtonDefault upgraderButton;
    private net.minecraft.client.gui.components.Button createButton;
    
    private ItemGlitchEffect glitchEffect;
    private RevealAnimationWidget itemWidget = null;
    
    private int maxPlayers = 2;
    private String currentMode = MODE_SEPARATE;
    private boolean randomDropEnabled = false; 
    private boolean upgraderEnabled = false;
    private boolean creating = false;
    private String errorMsg = null;
    private Item selectedItem = null;
    private long startTime;

    private List<SpeedrunTarget> allTargets = new ArrayList<>();
    private List<SpeedrunTarget> filteredTargets = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private SpeedrunTarget hoveredTarget = null;

    private String passwordError = null;
    private String modeError = null;

    public RoomHostScreenCreateImproved(Screen parent, Item selectedItem) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.create_room"));
        this.parent = parent;
        this.selectedItem = selectedItem;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        String previousPassword = passwordField == null ? "" : passwordField.getValue();
        String previousSearch = searchField == null ? "" : searchField.getValue();
        super.init();
        itemWidget = null;

        var config = RandomRunMod.getInstance().getConfig();
        allTargets = SpeedrunTargetProvider.getAllTargets(config.isAllowUnobtainableItems());
        filteredTargets = new ArrayList<>(allTargets);
        glitchEffect = new ItemGlitchEffect(config.isAllowUnobtainableItems());
        
        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;
        
        initLeftPanel(startX);
        initCenterPanel(startX + PANEL_WIDTH_SIDE + PANEL_SPACING);
        initRightPanel(startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2);
        initBottomButtons(centerX);
        passwordField.setValue(previousPassword);
        searchField.setValue(previousSearch);
        
        if (selectedItem != null) {
            createItemWidget();
        }
        
        validateAll();
    }
    
    private void initLeftPanel(int x) {
        int y = 80;
        
        int fieldX = x + (PANEL_WIDTH_SIDE - BUTTON_WIDTH_NARROW) / 2;

        passwordField = new TextFieldStyled(font, fieldX, y + 15, BUTTON_WIDTH_NARROW, 20,
            Component.literal("Пароль"), 0.05f);
        passwordField.setMaxLength(32);
        passwordField.setCenteredPlaceholder(Component.literal("Пароль"));
        passwordField.setTooltip(Tooltip.create(Component.literal("Оставьте пустым если хотите публичную комнату")));
        passwordField.setResponder(text -> {
            validatePassword();
            errorMsg = null;
        });
        addRenderableWidget(passwordField);

        maxPlayersButton = new ButtonDefault(
            fieldX, y + 40, BUTTON_WIDTH_NARROW, 20,
            getPlayersText(),
            button -> {
                cyclePlayers();
                button.setMessage(getPlayersText());
            },
            0, 0.05f
        );
        maxPlayersButton.setTooltip(Tooltip.create(Component.literal("От 2 до 6 игроков")));
        addRenderableWidget(maxPlayersButton);
    }
    
    private void initCenterPanel(int x) {
        int y = 80;
        int fieldWidth = (PANEL_WIDTH_CENTER - PANEL_PADDING * 2 - 5) / 2;
        int fieldX = x + PANEL_PADDING;

        searchField = new TextFieldStyled(font, fieldX, y + 15, fieldWidth, 20,
            Component.literal("Поиск"), 0.05f);
        searchField.setCenteredPlaceholder(Component.literal("Поиск..."));
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);

        randomItemButton = new ButtonDefault(
            fieldX + fieldWidth + 5, y + 15, fieldWidth, 20,
            Component.literal("Случайный"),
            button -> {
                selectedItem = null;
                if (itemWidget != null) {
                    removeWidget(itemWidget);
                    itemWidget = null;
                }
            },
            0, 0.05f
        );
        randomItemButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("Случайный предмет (выбирается автоматически)")));
        addRenderableWidget(randomItemButton);
    }
    
    private void initRightPanel(int x) {
        int y = 80;
        
        int fieldX = x + (PANEL_WIDTH_SIDE - BUTTON_WIDTH_NARROW) / 2;

        modeButton = new ButtonDefault(
            fieldX, y + 15, BUTTON_WIDTH_NARROW, 20,
            getModeText(),
            button -> {
                cycleMode();
                button.setMessage(getModeText());
                validateMode();
            },
            0, 0.05f
        );
        updateModeTooltip();
        addRenderableWidget(modeButton);

        randomDropButton = new ButtonDefault(
            fieldX, y + 40, BUTTON_WIDTH_NARROW, 20,
            getRandomDropText(),
            button -> {
                randomDropEnabled = !randomDropEnabled;
                button.setMessage(getRandomDropText());
                updateRandomDropTooltip();
            },
            1, 0.05f
        );
        updateRandomDropTooltip();
        addRenderableWidget(randomDropButton);

        upgraderButton = new ButtonDefault(
            fieldX, y + 65, BUTTON_WIDTH_NARROW, 20,
            getUpgraderText(), button -> {
                upgraderEnabled = !upgraderEnabled;
                button.setMessage(getUpgraderText());
            }, 1, 0.05f);
        upgraderButton.setTooltip(Tooltip.create(Component.literal(
            "RRM Upgrader: обмен предметов с шансом\nПравило задаёт хост для всех игроков комнаты\nЗабег отдельно от обычной таблицы лидеров")));
        addRenderableWidget(upgraderButton);
    }
    
    private void initBottomButtons(int centerX) {
        createButton = new ButtonRainbow(
            centerX - 100, height - 55, 200, 20,
            Component.literal("Создать комнату"),
            button -> createRoom(),
            1, 0.12f
        );
        addRenderableWidget(createButton);

        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            Component.literal("Назад"),
            button -> onClose(),
            2, 0.15f
        ));
    }

    private void validateAll() {
        updateCreateButton();
    }
    
    private void validatePassword() {
        updateCreateButton();
    }
    
    private void validateMode() {
        updateCreateButton();
    }
    
    private void updateCreateButton() {
        if (createButton != null) createButton.active = !creating;
    }

    private void cyclePlayers() {
        maxPlayers = maxPlayers >= 6 ? 2 : maxPlayers + 1;
    }
    
    private void cycleMode() {
        switch (currentMode) {
            case MODE_SEPARATE:
                currentMode = MODE_E4MC;
                break;
            case MODE_E4MC:
                currentMode = MODE_RADMIN;
                break;
            case MODE_RADMIN:
                currentMode = MODE_SEPARATE;
                break;
        }
        updateModeTooltip();
    }
    
    private void updateModeTooltip() {
        String tooltip = getModeTooltip();
        modeButton.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }
    
    private void updateRandomDropTooltip() {
        String tooltip = randomDropEnabled 
            ? "Random Drop: §aВКЛ\n§7Выпадают случайные предметы\n§7Не попадает в таблицу лидеров"
            : "Random Drop: §cВЫКЛ\n§7Обычное соревнование\n§7Попадает в таблицу лидеров";
        randomDropButton.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private Component getPlayersText() {
        return Component.literal("Игроков: " + maxPlayers);
    }
    
    private Component getModeText() {
        switch (currentMode) {
            case MODE_E4MC:
                return Component.literal("E4MC");
            case MODE_RADMIN:
                return Component.literal("Radmin VPN");
            default:
                return Component.literal("Отдельные миры");
        }
    }
    
    private Component getRandomDropText() {
        return Component.literal(randomDropEnabled ? "Random Drop: §aВКЛ" : "Random Drop: §cВЫКЛ");
    }

    private Component getUpgraderText() {
        return Component.literal(upgraderEnabled ? "RRM Upgrader: §aВКЛ" : "RRM Upgrader: §cВЫКЛ");
    }
    
    private String getModeTooltip() {
        switch (currentMode) {
            case MODE_E4MC:
                return "Общий мир через E4MC\n§7Требуется мод E4MC\n§7Все игроки в одном мире";
            case MODE_RADMIN:
                return "Общий мир через Radmin VPN\n§7Требуется Radmin VPN\n§7Все игроки в одном мире";
            default:
                return "Отдельные миры\n§7Каждый игрок в своем мире\n§7Не требует дополнительных модов";
        }
    }

    private void onSearchChanged(String text) {
        filteredTargets.clear();
        String searchLower = text.toLowerCase();
        
        if (searchLower.isEmpty()) {
            filteredTargets.addAll(allTargets);
        } else {
            for (SpeedrunTarget target : allTargets) {
                if (target.matchesDisplayName(searchLower)) {
                    filteredTargets.add(target);
                }
            }
        }
        
        scrollOffset = 0;
    }
    
    private void selectItem(SpeedrunTarget target) {
        if (target != null) {
            selectedItem = target.getItem();
            if (itemWidget != null) {
                removeWidget(itemWidget);
                itemWidget = null;
            }
            createItemWidget();
            playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
        }
    }
    
    private void createItemWidget() {
        if (selectedItem != null) {
            int centerX = width / 2;
            int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
            int startX = centerX - totalWidth / 2;
            int centerPanelX = startX + PANEL_WIDTH_SIDE + PANEL_SPACING;
            
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
            int itemX = centerPanelX + 50;
            int itemY = 60 + 60 + gridHeight + 30;
            
            itemWidget = new RevealAnimationWidget(itemX, itemY, com.randomrun.ui.util.PreviewItems.stack(selectedItem));
            addRenderableWidget(itemWidget);
        }
    }

    private void createRoom() {
        if (creating) return;

        String password = passwordField.getValue().trim();

        if (!password.isEmpty() && password.length() < 4) {
            errorMsg = "Ошибка: Пароль - минимум 4 символа";
            playErrorSound();
            return;
        }

        creating = true;
        errorMsg = null;
        createButton.active = false;
        
        String playerName = minecraft.getUser().getName();
        boolean isPrivate = !password.isEmpty();
        boolean isSharedWorld = !MODE_SEPARATE.equals(currentMode);
        String requestedMode = currentMode;
        boolean requestedDrop = randomDropEnabled;
        boolean requestedUpgrader = upgraderEnabled;
        
        CompletableFuture<String> createFuture = BattleManager.getInstance()
            .createRoom(playerName, selectedItem, isSharedWorld, isPrivate, password, maxPlayers,
                requestedMode, requestedDrop, requestedUpgrader);
        
        createFuture.whenComplete((roomCode, throwable) -> minecraft.execute(() -> {
            creating = false;
            createButton.active = true;
            if (minecraft.gui.screen() != this) return;
            if (throwable != null) {
                errorMsg = "Ошибка создания комнаты";
                RandomRunMod.LOGGER.error("[RoomHostCreate] Ошибка", throwable);
                playErrorSound();
                return;
            }
            
            if (roomCode != null && !roomCode.isEmpty()) {
                    playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP);
                    boolean isRadmin = MODE_RADMIN.equals(requestedMode);
                    minecraft.gui.setScreen(new RoomWaitingScreen(parent, roomCode, true, isRadmin));
            } else {
                errorMsg = "Не удалось создать комнату";
                playErrorSound();
            }
        }));
    }

    @Override
    public void onClose() {
        if (!creating) minecraft.gui.setScreen(parent);
    }
    
    private void playErrorSound() {
        minecraft.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0f, 0.5f
            )
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;

        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
        int centerPanelHeight = 60 + gridHeight + 60; 

        renderPanelBackground(context, startX, 60, PANEL_WIDTH_SIDE, centerPanelHeight, "Настройки комнаты");
        renderPanelBackground(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING, 60, PANEL_WIDTH_CENTER, centerPanelHeight, "Выбор предмета");
        renderPanelBackground(context, startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2, 60, PANEL_WIDTH_SIDE, centerPanelHeight, "Режим игры");
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;

        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            Component.literal("Создание комнаты"), 
            centerX, 20, COLOR_TITLE);

        super.extractRenderState(context, mouseX, mouseY, delta);

        renderLeftPanelContent(context, startX, mouseX, mouseY);
        renderCenterPanelContent(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING, mouseX, mouseY);
        renderRightPanelContent(context, startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2, mouseX, mouseY);

        renderStatus(context, centerX);
    }

    private void renderPanelBackground(GuiGraphicsExtractor context, int x, int y, int width, int height, String title) {
        context.fill(x, y, x + width, y + height, 0xD0202020);

        context.fill(x, y, x + width, y + BORDER_WIDTH, COLOR_PANEL_BORDER); 
        context.fill(x, y + height - BORDER_WIDTH, x + width, y + height, COLOR_PANEL_BORDER); 
        context.fill(x, y, x + BORDER_WIDTH, y + height, COLOR_PANEL_BORDER); 
        context.fill(x + width - BORDER_WIDTH, y, x + width, y + height, COLOR_PANEL_BORDER); 

        int separatorY = y + 25;
        context.fill(x + 5, separatorY, x + width - 5, separatorY + SEPARATOR_HEIGHT, COLOR_SEPARATOR);

        int titleWidth = font.width(title);
        int titleY = y + (25 - font.lineHeight) / 2 + 2; 
        com.randomrun.ui.util.GuiText.text(context, font, 
            Component.literal(title), 
            x + width / 2 - titleWidth / 2, titleY, COLOR_TITLE);
    }

    private void renderLeftPanelContent(GuiGraphicsExtractor context, int x, int mouseX, int mouseY) {
    }
    
    private void renderCenterPanelContent(GuiGraphicsExtractor context, int x, int mouseX, int mouseY) {
        int y = 60;

        renderItemGrid(context, x + PANEL_PADDING, y + 60, mouseX, mouseY);
    }
    
    private void renderRightPanelContent(GuiGraphicsExtractor context, int x, int mouseX, int mouseY) {
    }
    
    private void renderItemGrid(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + 2);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
        
        hoveredTarget = null;
        
        int maxScroll = Math.max(0, (int) Math.ceil((double) filteredTargets.size() / ITEMS_PER_ROW) - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        
        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + VISIBLE_ROWS * ITEMS_PER_ROW, filteredTargets.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            SpeedrunTarget target = filteredTargets.get(i);
            
            int localIndex = i - startIndex;
            int row = localIndex / ITEMS_PER_ROW;
            int col = localIndex % ITEMS_PER_ROW;
            
            int itemX = x + col * (ITEM_SIZE + 2);
            int itemY = y + row * (ITEM_SIZE + 2);
            
            boolean isHovered = mouseX >= itemX && mouseX < itemX + ITEM_SIZE &&
                               mouseY >= itemY && mouseY < itemY + ITEM_SIZE;
            boolean isSelected = target.getItem() == selectedItem;
            
            if (isHovered) {
                hoveredTarget = target;
            }

            int slotColor = isSelected ? COLOR_ITEM_SELECTED : 
                           (isHovered ? COLOR_ITEM_HOVER : 0x80000000);
            context.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, slotColor);

            context.item(target.getPreviewStack(), itemX + 1, itemY + 1);
        }

        if (maxScroll > 0) {
            renderScrollbar(context, x + gridWidth + 3, y, gridHeight, maxScroll);
        }

        if (hoveredTarget != null) {
            context.setTooltipForNextFrame(font, Component.literal(hoveredTarget.getDisplayName()), mouseX, mouseY);
        }
    }
    
    private void renderScrollbar(GuiGraphicsExtractor context, int x, int y, int height, int maxScroll) {
        context.fill(x, y, x + SCROLLBAR_WIDTH, y + height, 0x80000000);

        int scrollbarHeight = Math.max(20, height / (maxScroll + VISIBLE_ROWS));
        int scrollbarY = y + (int) ((height - scrollbarHeight) * ((double) scrollOffset / maxScroll));
        
        context.fill(x, scrollbarY, x + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, COLOR_PANEL_BORDER);
    }
    
    private void renderStatus(GuiGraphicsExtractor context, int centerX) {
        int errorY = height - 80;
        
        if (errorMsg != null) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal("§c" + errorMsg), 
                centerX, errorY, COLOR_ERROR);
        }

        if (creating) {
            long time = System.currentTimeMillis();
            String dots = ".".repeat((int)((time / 300) % 4));
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal("§eСоздание комнаты" + dots), 
                centerX, errorY, COLOR_WARNING);
        }

        int selectedY = height - 105;
        if (selectedItem != null) {
            String itemName = net.minecraft.network.chat.Component.translatable(selectedItem.getDescriptionId()).getString();
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal("§7Выбран: §f" + itemName), 
                centerX, selectedY, COLOR_TITLE);
        } else {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal("§7Выбран: §6Случайный"), 
                centerX, selectedY, 0xFFFFAA00);
        }
    }
    
    private String getModeDescription() {
        switch (currentMode) {
            case MODE_E4MC:
                return "Все игроки в одном мире\nЧерез мод E4MC";
            case MODE_RADMIN:
                return "Все игроки в одном мире\nЧерез Radmin VPN";
            default:
                return "Каждый игрок\nв своем мире";
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            if (hoveredTarget != null) {
                selectItem(hoveredTarget);
                return true;
            }

            int centerX = width / 2;
            int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
            int startX = centerX - totalWidth / 2;
            int centerPanelX = startX + PANEL_WIDTH_SIDE + PANEL_SPACING;
            int gridX = centerPanelX + PANEL_PADDING;
            int gridY = 60 + 60;
            int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + 2);
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
            int scrollbarX = gridX + gridWidth + 3;
            
            if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= gridY && mouseY < gridY + gridHeight) {
                isDraggingScrollbar = true;
                return true;
            }
        }

        boolean handled = super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);

        if (passwordField != null && passwordField.isMouseOver(mouseX, mouseY)) {
            setFocused(passwordField);
            passwordField.setFocused(true);
            handled = true;
        } else if (searchField != null && searchField.isMouseOver(mouseX, mouseY)) {
            setFocused(searchField);
            searchField.setFocused(true);
            handled = true;
        }

        if (!handled && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            setFocused(null);
        }
        
        return handled;
    }
    
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            isDraggingScrollbar = false;
        }
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()));
    }
    
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (isDraggingScrollbar) {
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
            int maxScroll = Math.max(0, (int) Math.ceil((double) filteredTargets.size() / ITEMS_PER_ROW) - VISIBLE_ROWS);
            
            if (maxScroll > 0) {
                int gridY = 60 + 60;
                double relativeY = (mouseY - gridY) / gridHeight;
                scrollOffset = (int) (relativeY * maxScroll);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }
            return true;
        }
        
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), deltaX, deltaY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;
        int centerPanelX = startX + PANEL_WIDTH_SIDE + PANEL_SPACING;

        if (mouseX >= centerPanelX && mouseX < centerPanelX + PANEL_WIDTH_CENTER) {
            int maxScroll = Math.max(0, (int) Math.ceil((double) filteredTargets.size() / ITEMS_PER_ROW) - VISIBLE_ROWS);
            scrollOffset -= (int) verticalAmount;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        minecraft.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0f)
        );
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return !creating;
    }
    
    @Override
    protected boolean shouldEnableAfk() {
        return false;
    }
}

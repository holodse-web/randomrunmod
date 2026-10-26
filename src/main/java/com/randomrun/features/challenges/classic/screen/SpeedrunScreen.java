package com.randomrun.features.challenges.classic.screen;

import com.randomrun.features.challenges.classic.data.SpeedrunTarget;
import com.randomrun.features.challenges.classic.data.SpeedrunTargetProvider;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import com.randomrun.ui.widget.RevealAnimationWidget;
import com.randomrun.features.challenges.classic.screen.util.CompactSlotMachineAnimator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public class SpeedrunScreen extends AbstractRandomRunScreen {
    private static final int PANEL_WIDTH_SIDE = 180;
    private static final int PANEL_WIDTH_CENTER = 220;
    private static final int PANEL_PADDING = 15;
    private static final int PANEL_SPACING = 15;
    private static final int ITEM_SIZE = 18;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 6;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int BUTTON_WIDTH_NARROW = 160;
    private static final int BORDER_WIDTH = 2;
    private static final int SEPARATOR_HEIGHT = 2;

    private static final int COLOR_PANEL_BORDER = 0xFF6930c3;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_SEPARATOR = 0xFF6930c3;
    private static final int COLOR_ITEM_SELECTED = 0xFF6930c3;
    private static final int COLOR_ITEM_HOVER = 0xFF4A2080;

    private final Screen parent;

    private com.randomrun.ui.widget.styled.TextFieldStyled searchField;
    private ButtonDefault randomDropButton;
    private ButtonDefault upgraderButton;
    private ButtonDefault randomItemButton;
    private net.minecraft.client.gui.components.Button startButton;
    private com.randomrun.ui.widget.styled.TextFieldStyled seedField;

    private boolean randomDropEnabled = false;
    private Item selectedItem = null;
    private Item namedItem;
    private Language nameLanguage;
    private Component selectedName = Component.empty();

    private CompactSlotMachineAnimator slotAnimator = null;
    private boolean slotMachineActive = false;

    private RevealAnimationWidget itemWidget = null;

    private List<SpeedrunTarget> allTargets = new ArrayList<>();
    private List<SpeedrunTarget> filteredTargets = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private SpeedrunTarget hoveredTarget = null;

    public SpeedrunScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.speedrun.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        nameLanguage = null;

        var config = RandomRunMod.getInstance().getConfig();
        allTargets = SpeedrunTargetProvider.getAllTargets(config.isAllowUnobtainableItems());
        filteredTargets = new ArrayList<>(allTargets);

        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;

        initLeftPanel(startX);
        initCenterPanel(startX + PANEL_WIDTH_SIDE + PANEL_SPACING);
        initRightPanel(startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2);
        initBottomButtons(centerX);

        if (selectedItem != null) {
            createItemWidget();
        }
    }

    private void initLeftPanel(int x) {
        int y = 60;
        int fieldX = x + (PANEL_WIDTH_SIDE - BUTTON_WIDTH_NARROW) / 2;
        var config = RandomRunMod.getInstance().getConfig();

        int currentY = y + 35;

        ButtonDefault unobtainableButton = new ButtonDefault(
            fieldX, currentY, BUTTON_WIDTH_NARROW, 20,
            getToggleText("randomrun.settings.unobtainable_items", config.isAllowUnobtainableItems()),
            button -> {
                config.setAllowUnobtainableItems(!config.isAllowUnobtainableItems());
                RandomRunMod.getInstance().saveConfig();
                button.setMessage(getToggleText("randomrun.settings.unobtainable_items", config.isAllowUnobtainableItems()));
                updateUnobtainableTooltip((ButtonDefault)button, config.isAllowUnobtainableItems());

                allTargets = SpeedrunTargetProvider.getAllTargets(config.isAllowUnobtainableItems());
                filteredTargets = new ArrayList<>(allTargets);
                scrollOffset = 0;
            },
            0, 0.05f
        );
        updateUnobtainableTooltip(unobtainableButton, config.isAllowUnobtainableItems());
        addRenderableWidget(unobtainableButton);
        currentY += 25;

        ButtonDefault deleteWorldsButton = new ButtonDefault(
            fieldX, currentY, BUTTON_WIDTH_NARROW, 20,
            getToggleText("randomrun.settings.delete_worlds", config.isDeleteWorldsAfterSpeedrun()),
            button -> {
                config.setDeleteWorldsAfterSpeedrun(!config.isDeleteWorldsAfterSpeedrun());
                RandomRunMod.getInstance().saveConfig();
                button.setMessage(getToggleText("randomrun.settings.delete_worlds", config.isDeleteWorldsAfterSpeedrun()));
                updateDeleteWorldsTooltip((ButtonDefault)button, config.isDeleteWorldsAfterSpeedrun());
            },
            0, 0.1f
        );
        updateDeleteWorldsTooltip(deleteWorldsButton, config.isDeleteWorldsAfterSpeedrun());
        addRenderableWidget(deleteWorldsButton);
        currentY += 25;

        ButtonDefault hardcoreButton = new ButtonDefault(
            fieldX, currentY, BUTTON_WIDTH_NARROW, 20,
            getToggleText("randomrun.settings.hardcore_mode", config.isHardcoreModeEnabled()),
            button -> {
                config.setHardcoreModeEnabled(!config.isHardcoreModeEnabled());
                RandomRunMod.getInstance().saveConfig();
                button.setMessage(getToggleText("randomrun.settings.hardcore_mode", config.isHardcoreModeEnabled()));
                updateHardcoreTooltip((ButtonDefault)button, config.isHardcoreModeEnabled());
            },
            0, 0.15f
        );
        updateHardcoreTooltip(hardcoreButton, config.isHardcoreModeEnabled());
        addRenderableWidget(hardcoreButton);
        currentY += 25;

        seedField = new com.randomrun.ui.widget.styled.TextFieldStyled(
            font, fieldX, currentY, BUTTON_WIDTH_NARROW, 20,
            Component.literal("Сид"), 0.2f
        );
        seedField.setCenteredPlaceholder(Component.literal("Сид..."));
        seedField.setMaxLength(50);
        seedField.setValue(config.getCustomSeed() != null ? config.getCustomSeed() : "");
        seedField.setResponder(text -> {
            config.setCustomSeed(text.isEmpty() ? null : text);
            RandomRunMod.getInstance().saveConfig();
        });
        seedField.setTooltip(Tooltip.create(Component.literal("§7Кастомный сид\n§cЗабег не попадет в таблицу лидеров")));
        addRenderableWidget(seedField);
    }

    private void updateUnobtainableTooltip(ButtonDefault button, boolean enabled) {
        String tooltip = enabled
            ? "§aВКЛ §7- Включены недобываемые предметы\n§7(командные блоки, барьеры и т.д.)"
            : "§cВЫКЛ §7- Только добываемые предметы";
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private void updateDeleteWorldsTooltip(ButtonDefault button, boolean enabled) {
        String tooltip = enabled
            ? "§aВКЛ §7- Миры удаляются после забега"
            : "§cВЫКЛ §7- Миры сохраняются";
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private void updateHardcoreTooltip(ButtonDefault button, boolean enabled) {
        String tooltip = enabled
            ? "§aВКЛ §7- Режим хардкор"
            : "§cВЫКЛ §7- Обычный режим\n§cЗабег не попадет в таблицу лидеров";
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private void initCenterPanel(int x) {
        int y = 80;
        int fieldWidth = (PANEL_WIDTH_CENTER - PANEL_PADDING * 2 - 5) / 2;
        int fieldX = x + PANEL_PADDING;

        searchField = new com.randomrun.ui.widget.styled.TextFieldStyled(font, fieldX, y + 15, fieldWidth, 20,
            Component.literal("Поиск"), 0.05f);
        searchField.setCenteredPlaceholder(Component.literal("Поиск..."));
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);

        randomItemButton = new ButtonDefault(
            fieldX + fieldWidth + 5, y + 15, fieldWidth, 20,
            Component.literal("Случайный"),
            button -> startRandomSelection(),
            0, 0.05f
        );
        randomItemButton.setTooltip(Tooltip.create(Component.literal("Выбрать случайный предмет")));
        addRenderableWidget(randomItemButton);
    }

    private void initRightPanel(int x) {
        int y = 60;
        int fieldX = x + (PANEL_WIDTH_SIDE - BUTTON_WIDTH_NARROW) / 2;

        randomDropButton = new ButtonDefault(
            fieldX, y + 35, BUTTON_WIDTH_NARROW, 20,
            getRandomDropText(),
            button -> {
                randomDropEnabled = !randomDropEnabled;
                button.setMessage(getRandomDropText());
                updateRandomDropTooltip();
            },
            0, 0.05f
        );
        updateRandomDropTooltip();
        addRenderableWidget(randomDropButton);

        var config = RandomRunMod.getInstance().getConfig();
        upgraderButton = new ButtonDefault(
            fieldX, y + 60, BUTTON_WIDTH_NARROW, 20,
            getUpgraderText(),
            button -> {
                config.setUpgraderEnabled(!config.isUpgraderEnabled());
                RandomRunMod.getInstance().saveConfig();
                button.setMessage(getUpgraderText());
                updateUpgraderTooltip();
            },
            0, 0.1f
        );
        updateUpgraderTooltip();
        addRenderableWidget(upgraderButton);
    }

    private void initBottomButtons(int centerX) {
        startButton = new ButtonRainbow(
            centerX - 100, height - 55, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.speedrun.start"),
            button -> startSpeedrun(),
            1, 0.12f
        );
        addRenderableWidget(startButton);

        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> onClose(),
            2, 0.15f
        ));
    }

    private Component getRandomDropText() {
        return Component.literal(randomDropEnabled ? "Random Drop: §aВКЛ" : "Random Drop: §cВЫКЛ");
    }

    private void updateRandomDropTooltip() {
        String tooltip = randomDropEnabled
            ? "Random Drop: §aВКЛ\n§7Выпадают случайные предметы\n§cЗабег не попадет в таблицу лидеров"
            : "Random Drop: §cВЫКЛ\n§7Обычный режим";
        randomDropButton.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private Component getUpgraderText() {
        return Component.literal(RandomRunMod.getInstance().getConfig().isUpgraderEnabled()
            ? "RRM Upgrader: §aВКЛ" : "RRM Upgrader: §cВЫКЛ");
    }

    private void updateUpgraderTooltip() {
        upgraderButton.setTooltip(Tooltip.create(Component.literal(
            "§eRRM Upgrader\n§7Меняйте предметы из инвентаря с честным шансом\n"
                + "§7Открыть: " + com.randomrun.features.upgrader.client.UpgraderClient.keyName().getString()
                + " (можно изменить в управлении)\n"
                + "§7Режим фиксируется при старте забега\n§cОтдельная локальная история, без лидерборда")));
    }

    private void onSearchChanged(String text) {
        filteredTargets.clear();
        String searchLower = text.toLowerCase(Locale.ROOT);

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
            if (itemWidget != null) {
                removeWidget(itemWidget);
                itemWidget = null;
            }

            selectedItem = target.getItem();
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

    private void startRandomSelection() {
        if (slotMachineActive) return;

        if (itemWidget != null) {
            removeWidget(itemWidget);
            itemWidget = null;
        }

        List<Item> allItems = new ArrayList<>();
        for (SpeedrunTarget target : allTargets) {
            allItems.add(target.getItem());
        }

        if (allItems.isEmpty()) return;

        Item result = allItems.get(ThreadLocalRandom.current().nextInt(allItems.size()));

        slotAnimator = new CompactSlotMachineAnimator(allItems, result);
        slotMachineActive = true;
        selectedItem = result;
    }

    @Override
    public void tick() {
        super.tick();

        if (slotMachineActive && slotAnimator != null) {
            if (slotAnimator.isComplete()) {
                slotMachineActive = false;
                createItemWidget();
                slotAnimator = null;
            }
        }
    }

    private void startSpeedrun() {
        if (selectedItem == null) {
            com.randomrun.infrastructure.util.CompactLogger.warn("Не выбран предмет для спидрана");
            return;
        }

        var config = RandomRunMod.getInstance().getConfig();
        String customSeed = config.getCustomSeed();
        boolean hasCustomSeed = customSeed != null && !customSeed.trim().isEmpty();
        com.randomrun.features.challenges.classic.world.WorldCreator.getInstance().createWorld(
            new com.randomrun.features.challenges.classic.world.WorldCreationRequest.Builder()
                .targetItem(selectedItem)
                .seed(hasCustomSeed ? customSeed : null)
                .randomDrop(randomDropEnabled)
                .upgrader(config.isUpgraderEnabled())
                .build()
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

        renderPanelBackground(context, startX, 60, PANEL_WIDTH_SIDE, centerPanelHeight, "Настройки спидрана");
        renderPanelBackground(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING, 60, PANEL_WIDTH_CENTER, centerPanelHeight, "Выбор предмета");
        renderPanelBackground(context, startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2, 60, PANEL_WIDTH_SIDE, centerPanelHeight, "Режим игры");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;

        com.randomrun.ui.util.GuiText.centeredText(context, font,
            Component.literal("Спидран"),
            centerX, 20, COLOR_TITLE);

        super.extractRenderState(context, mouseX, mouseY, delta);

        if (RandomRunMod.getInstance().getConfig().isUpgraderEnabled()) {
            int rightPanelX = startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2;
            if (!RandomRunMod.getInstance().getConfig().hasSeenUpgraderHint()) {
                com.randomrun.ui.util.GuiText.text(context, font, "§e"
                    + com.randomrun.features.upgrader.client.UpgraderClient.keyName().getString()
                    + " §7— RRM Upgrader", rightPanelX + 10, 154, COLOR_TITLE);
            }
            com.randomrun.ui.util.GuiText.text(context, font, "§7Локальный режим", rightPanelX + 10, 169, COLOR_TITLE);
        }

        renderCenterPanelContent(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING, mouseX, mouseY);

        if (slotMachineActive && slotAnimator != null) {
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
            int animX = startX + PANEL_WIDTH_SIDE + PANEL_SPACING + 50;
            int animY = 60 + 60 + gridHeight + 30;
            slotAnimator.extractRenderState(context, animX, animY, delta);
        }

        if (selectedItem != null && !slotMachineActive) {
            renderItemName(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING);
        }
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

    private Component getToggleText(String key, boolean enabled) {
        String stateText = enabled
            ? "§a" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.enabled").getString()
            : "§c" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.disabled").getString();
        return com.randomrun.infrastructure.util.LanguageManager.text(key, stateText);
    }

    private void renderCenterPanelContent(GuiGraphicsExtractor context, int x, int mouseX, int mouseY) {
        int y = 60;
        renderItemGrid(context, x + PANEL_PADDING, y + 60, mouseX, mouseY);
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

    private void renderItemName(GuiGraphicsExtractor context, int centerPanelX) {
        if (selectedItem == null) return;

        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + 2);
        int itemX = centerPanelX + 50;
        int itemY = 60 + 60 + gridHeight + 30;

        int textX = itemX + 40;
        int textY = itemY - font.lineHeight / 2;
        Language language = Language.getInstance();
        if (selectedItem != namedItem || language != nameLanguage) {
            String itemName = Component.translatable(selectedItem.getDescriptionId()).getString();
            if (font.width(itemName) > 140) {
                itemName = font.plainSubstrByWidth(itemName, 140 - font.width("...")) + "...";
            }
            selectedName = Component.literal(itemName);
            namedItem = selectedItem;
            nameLanguage = language;
        }
        com.randomrun.ui.util.GuiText.text(context, font,
            selectedName,
            textX, textY, COLOR_TITLE);
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

        if (seedField != null && seedField.isMouseOver(mouseX, mouseY)) {
            setFocused(seedField);
            seedField.setFocused(true);
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
        var config = RandomRunMod.getInstance().getConfig();
        if (!config.isSoundEffectsEnabled()) return;
        minecraft.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0f, config.getSoundVolume() / 100f)
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    protected boolean shouldEnableAfk() {
        return false;
    }
}

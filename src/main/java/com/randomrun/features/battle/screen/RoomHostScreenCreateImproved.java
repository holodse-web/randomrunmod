package com.randomrun.features.battle.screen;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.speedrun.SpeedrunTarget;
import com.randomrun.features.speedrun.SpeedrunTarget.SpeedrunTargetProvider;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.RevealAnimationWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;
import com.randomrun.ui.widget.styled.TextFieldStyled;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public class RoomHostScreenCreateImproved extends AbstractRandomRunScreen {

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

    private static int COLOR_PANEL_BORDER() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }

    private static final int COLOR_TITLE = 0xFFFFFFFF; 
    private static int COLOR_SEPARATOR() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
    private static final int COLOR_ERROR = 0xFFFF5555;

    private static final int COLOR_WARNING = 0xFFFFAA00;
    private static int COLOR_ITEM_SELECTED() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
    private static int COLOR_ITEM_HOVER() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonBase; }

    private final Screen parent;

    private TextFieldStyled passwordField;
    private TextFieldStyled searchField;
    private ButtonDefault randomItemButton;
    private ButtonDefault maxPlayersButton;
    private ButtonDefault randomDropButton;
    private ButtonDefault upgraderButton;
    private net.minecraft.client.gui.components.Button createButton;

    private RevealAnimationWidget itemWidget = null;

    private int maxPlayers = 2;
    private boolean randomDropEnabled = false; 
    private boolean upgraderEnabled = false;
    private boolean voiceEnabled = false;
    private ButtonDefault voiceButton;
    private boolean sharedWorld = true;
    private ButtonDefault worldButton;
    private long selectedAt;
    private boolean creating = false;
    private String errorMsg = null;
    private Item selectedItem = null;

    private List<SpeedrunTarget> allTargets = new ArrayList<>();
    private List<SpeedrunTarget> filteredTargets = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private SpeedrunTarget hoveredTarget = null;

    public RoomHostScreenCreateImproved(Screen parent, Item selectedItem) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.create_room"));
        this.parent = parent;
        this.selectedItem = selectedItem;
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
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.password"), 0.05f);
        passwordField.setMaxLength(32);
        passwordField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.password"));
        passwordField.setTooltip(Tooltip.create(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.password.tooltip")));
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
        maxPlayersButton.setTooltip(Tooltip.create(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.players.tooltip")));
        addRenderableWidget(maxPlayersButton);

        worldButton = new ButtonDefault(
            fieldX, y + 65, BUTTON_WIDTH_NARROW, 20,
            getWorldText(),
            button -> {
                sharedWorld = !sharedWorld;
                button.setMessage(getWorldText());
                button.setTooltip(Tooltip.create(getWorldTooltip()));
            },
            0, 0.05f
        );
        worldButton.setTooltip(Tooltip.create(getWorldTooltip()));
        addRenderableWidget(worldButton);
    }

    private void initCenterPanel(int x) {
        int y = 80;
        int fieldWidth = (PANEL_WIDTH_CENTER - PANEL_PADDING * 2 - 5) / 2;
        int fieldX = x + PANEL_PADDING;

        searchField = new TextFieldStyled(font, fieldX, y + 15, fieldWidth, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.search"), 0.05f);
        searchField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.search.placeholder"));
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);

        randomItemButton = new ButtonDefault(
            fieldX + fieldWidth + 5, y + 15, fieldWidth, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.speedrun.random"),
            button -> {
                selectedItem = null;
                selectedAt = System.currentTimeMillis();
                if (itemWidget != null) {
                    removeWidget(itemWidget);
                    itemWidget = null;
                }
            },
            0, 0.05f
        );
        randomItemButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.random.tooltip")));
        addRenderableWidget(randomItemButton);
    }

    private void initRightPanel(int x) {
        int y = 80;

        int fieldX = x + (PANEL_WIDTH_SIDE - BUTTON_WIDTH_NARROW) / 2;

        randomDropButton = new ButtonDefault(
            fieldX, y + 15, BUTTON_WIDTH_NARROW, 20,
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
            fieldX, y + 40, BUTTON_WIDTH_NARROW, 20,
            getUpgraderText(), button -> {
                upgraderEnabled = !upgraderEnabled;
                button.setMessage(getUpgraderText());
            }, 1, 0.05f);
        upgraderButton.setTooltip(Tooltip.create(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.upgrader.tooltip")));
        addRenderableWidget(upgraderButton);

        voiceButton = new ButtonDefault(
            fieldX, y + 65, BUTTON_WIDTH_NARROW, 20,
            getVoiceText(), button -> toggleVoice(), 1, 0.05f);
        voiceButton.setTooltip(Tooltip.create(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.voice.tooltip")));
        addRenderableWidget(voiceButton);
    }

    private void initBottomButtons(int centerX) {
        createButton = new ButtonRainbow(
            centerX - 100, height - 55, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.create"),
            button -> createRoom(),
            1, 0.12f
        );
        addRenderableWidget(createButton);

        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
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



    private void updateRandomDropTooltip() {
        String tooltip = randomDropEnabled 
            ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.mode.random_drop.tooltip_on")
            : com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.room.random_drop.tooltip_off");
        randomDropButton.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private Component getPlayersText() {
        return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.players", maxPlayers);
    }

    private Component getWorldText() {
        return com.randomrun.infrastructure.util.LanguageManager.text(sharedWorld ? "randomrun.room.world.shared" : "randomrun.room.world.separate");
    }

    private Component getWorldTooltip() {
        return com.randomrun.infrastructure.util.LanguageManager.text(sharedWorld ? "randomrun.room.world.shared.tooltip" : "randomrun.room.world.separate.tooltip");
    }

    private Component getRandomDropText() {
        return com.randomrun.infrastructure.util.LanguageManager.text(randomDropEnabled ? "randomrun.mode.random_drop.on" : "randomrun.mode.random_drop.off");
    }

    private Component getVoiceText() {
        return com.randomrun.infrastructure.util.LanguageManager.text(voiceEnabled ? "randomrun.room.voice.on" : "randomrun.room.voice.off");
    }

    /** Voice rooms need the speech model; set it up first if it is not on disk yet. */
    private void toggleVoice() {
        if (!voiceEnabled && !com.randomrun.features.voice.VoiceSettings.get().isReady()) {
            minecraft.gui.setScreen(new com.randomrun.features.voice.ui.VoiceSetupScreen(this, ok -> {
                if (ok) voiceEnabled = true;
            }, false));
            return;
        }
        voiceEnabled = !voiceEnabled;
        voiceButton.setMessage(getVoiceText());
    }

    private Component getUpgraderText() {
        return com.randomrun.infrastructure.util.LanguageManager.text(upgraderEnabled ? "randomrun.mode.upgrader.on" : "randomrun.mode.upgrader.off");
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
            selectedAt = System.currentTimeMillis();
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

            int cardTop = cardTop();
            int slot = CARD_HEIGHT - 8;
            itemWidget = new RevealAnimationWidget(centerPanelX + PANEL_PADDING + 4 + slot / 2, cardTop + 4 + slot / 2,
                com.randomrun.ui.util.Previews.PreviewItems.stack(selectedItem), 28f);
            addRenderableWidget(itemWidget);
        }
    }

    private static final int CARD_HEIGHT = 46;

    private static int cardTop() {
        return 60 + 60 + VISIBLE_ROWS * (ITEM_SIZE + 2) + 6;
    }

    private void createRoom() {
        if (creating) return;

        String password = passwordField.getValue().trim();

        if (!password.isEmpty() && password.length() < 4) {
            errorMsg = com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.room.error.password");
            playErrorSound();
            return;
        }

        creating = true;
        errorMsg = null;
        createButton.active = false;

        String playerName = com.randomrun.network.BackendSession.playerName();
        boolean isPrivate = !password.isEmpty();
        boolean isSharedWorld = sharedWorld;
        String requestedMode = sharedWorld ? "webrtc" : "sw";
        boolean requestedDrop = randomDropEnabled;
        boolean requestedUpgrader = upgraderEnabled;
        boolean requestedVoice = voiceEnabled;

        CompletableFuture<String> createFuture = BattleManager.getInstance()
            .createRoom(playerName, selectedItem, isSharedWorld, isPrivate, password, maxPlayers,
                requestedMode, requestedDrop, requestedUpgrader, requestedVoice);

        createFuture.whenComplete((roomCode, throwable) -> minecraft.execute(() -> {
            creating = false;
            createButton.active = true;
            if (minecraft.gui.screen() != this) return;
            if (throwable != null) {
                errorMsg = com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.room.error.create");
                RandomRunMod.LOGGER.error("[RoomHostCreate] Ошибка", throwable);
                playErrorSound();
                return;
            }

            if (roomCode != null && !roomCode.isEmpty()) {
                    playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP);
                    minecraft.gui.setScreen(new RoomWaitingScreen(parent, roomCode, true));
            } else {
                errorMsg = com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.room.error.create");
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

        renderPanelBackground(context, startX, 60, PANEL_WIDTH_SIDE, centerPanelHeight, "randomrun.room.panel.settings");
        renderPanelBackground(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING, 60, PANEL_WIDTH_CENTER, centerPanelHeight, "randomrun.speedrun.panel.item");
        renderPanelBackground(context, startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2, 60, PANEL_WIDTH_SIDE, centerPanelHeight, "randomrun.speedrun.panel.mode");
        int cx = startX + PANEL_WIDTH_SIDE + PANEL_SPACING;
        com.randomrun.ui.util.Ui.selectedItemCard(context, font, cx + PANEL_PADDING, cardTop(),
            cx + PANEL_WIDTH_CENTER - PANEL_PADDING, cardTop() + CARD_HEIGHT, selectedItem, selectedItem == null, selectedAt);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int totalWidth = PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_WIDTH_SIDE + PANEL_SPACING * 2;
        int startX = centerX - totalWidth / 2;

        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.title"),
            centerX, 20, COLOR_TITLE);

        super.extractRenderState(context, mouseX, mouseY, delta);

        renderLeftPanelContent(context, startX, mouseX, mouseY);
        renderCenterPanelContent(context, startX + PANEL_WIDTH_SIDE + PANEL_SPACING, mouseX, mouseY);
        renderRightPanelContent(context, startX + PANEL_WIDTH_SIDE + PANEL_WIDTH_CENTER + PANEL_SPACING * 2, mouseX, mouseY);

        renderStatus(context, centerX);
    }

    private void renderPanelBackground(GuiGraphicsExtractor context, int x, int y, int width, int height, String title) {
        com.randomrun.ui.util.Theme theme = com.randomrun.ui.util.Theme.current();
        com.randomrun.ui.util.Ui.panel(context, x, y, x + width, y + height, 1f);
        com.randomrun.ui.util.Ui.divider(context, x + 6, x + width - 6, y + 25, theme.accent, 1f);
        com.randomrun.ui.util.GuiText.centeredText(context, font, com.randomrun.infrastructure.util.LanguageManager.text(title), x + width / 2, y + (25 - font.lineHeight) / 2 + 2, 0xFFFFFFFF);
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

            int slotColor = isSelected ? COLOR_ITEM_SELECTED() :
                           (isHovered ? COLOR_ITEM_HOVER() : 0x80000000);
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

        context.fill(x, scrollbarY, x + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, COLOR_PANEL_BORDER());
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
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.room.creating").append(dots),
                centerX, errorY, COLOR_WARNING);
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

}

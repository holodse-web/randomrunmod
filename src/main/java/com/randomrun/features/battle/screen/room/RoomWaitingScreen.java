package com.randomrun.features.battle.screen.room;

import com.randomrun.features.battle.screen.lobby.util.animation.CodeAnimator;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.List;
import com.randomrun.features.battle.screen.widget.PlayerSkinWidget;
import com.randomrun.features.battle.screen.lobby.util.animation.ItemAnimationController;
import com.randomrun.features.battle.screen.lobby.util.render.PanelRenderer;

public class RoomWaitingScreen extends AbstractRandomRunScreen {
    private static final float ITEM_SCALE = 80f;
    private static final int ITEM_Y = 60;
    private static final int ITEM_NAME_Y = 110;
    private static final int ITEM_AREA_SIZE = 60;
    private static final int ITEM_AREA_TOP = 40;
    private static final int ITEM_AREA_BOTTOM = 120;
    
    private static final int PLAYER_SPACING = 120;
    private static final int PLAYER_SLOT_SIZE = 70;
    private static final int PLAYER_CHARACTER_SIZE = 85;
    private static final int PLAYER_Y_OFFSET = -25;
    private static final int PLAYER_CHARACTER_Y_OFFSET = 5;
    private static final int PLAYER_NAME_Y_OFFSET = 75;
    private static final int PLAYER_COUNT_Y_OFFSET = 95;
    
    private com.randomrun.features.battle.util.ItemGlitchEffect glitchEffect;
    private final Screen parent;

    private final String roomCode;
    private final boolean isHost;
    private final boolean isRadminMode;
    private int lastPlayerCount = 0;
    
    private final Map<String, PlayerSkinWidget> playerWidgets = new HashMap<>();
    private final ItemAnimationController itemAnimator = new ItemAnimationController();
    private final CodeAnimator codeAnimator;
    
    private com.randomrun.features.battle.screen.room.util.rendering.SlotMachineAnimator slotAnimator = null;
    private boolean isAnimatingSlot = false;
    private boolean selectingItem;
    private ButtonRainbow startGameButton;
    private boolean e4mcMonitoringStarted;
    private String controlState = "";
    private final java.util.Set<String> pendingKicks = new java.util.HashSet<>();

    public RoomWaitingScreen(Screen parent, String roomCode, boolean isHost) {
        this(parent, roomCode, isHost, false);
    }

    public RoomWaitingScreen(Screen parent, String roomCode, boolean isHost, boolean isRadminMode) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.waiting"));
        this.parent = parent;
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.isRadminMode = isRadminMode;
        this.codeAnimator = new CodeAnimator(roomCode);
    }
    
    @Override
    protected void init() {
        super.init();
        startGameButton = null;
        
        boolean allowUnobtainable = com.randomrun.infrastructure.RandomRunMod.getInstance()
            .getConfig().isAllowUnobtainableItems();
        glitchEffect = new com.randomrun.features.battle.util.ItemGlitchEffect(allowUnobtainable);
        
        if (isHost) {
            initE4mcMonitoring();
        }
        
        int centerX = width / 2;
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        controlState = roomControlState(room);
        
        if (room != null && room.getPlayers() != null) {
            lastPlayerCount = room.getPlayers().size();
        }
        
        if (isHost && room != null) {
            int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 1;
            int maxPlayers = room.getMaxPlayers();
            boolean roomFull = playerCount >= maxPlayers;
            
            if (roomFull && !room.hasItemSelected()) {
                addRenderableWidget(new ButtonRainbow(
                    centerX - 100, height - 80, 200, 20,
                    Component.literal("Случайный предмет"),
                    button -> startRandomItemSelection(),
                    0, 0.12f
                ));
            }
            
            if (room.hasItemSelected() && roomFull) {
                startGameButton = addRenderableWidget(new ButtonRainbow(
                    centerX - 100, height - 55, 200, 20,
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.start_game"),
                    button -> {
                        if (BattleManager.getInstance().isLoadingTriggered()) return;
                        button.active = false;
                        if (isRadminMode) {
                            Minecraft.getInstance().gui.setScreen(new RadminVpnIpScreen(this, roomCode));
                        } else {
                            BattleManager.getInstance().setStatusLoading();
                        }
                    },
                    1, 0.15f
                ));
                startGameButton.active = !BattleManager.getInstance().isLoadingTriggered();
            }
        }
        
        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.cancel"),
            button -> onClose(),
            2, 0.2f
        ));
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
    }
    
    private void render3DItem(GuiGraphicsExtractor context, int x, int y, Item targetItem) {
        ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(targetItem);
        com.randomrun.ui.util.ItemPreviewRenderer.extract(context, stack, x, y + itemAnimator.getLevitationOffset(),
            ITEM_SCALE, itemAnimator.getDragRotationX(), itemAnimator.getRotationY() + itemAnimator.getDragRotationY(), ItemDisplayContext.FIXED);
    }
    
    private void initE4mcMonitoring() { 
        if (e4mcMonitoringStarted) return;
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || !room.isSharedWorld()) {
            return;
        }
        
        if ("radmin".equals(room.getCreationMode()) || "sw".equals(room.getCreationMode())) {
             return;
        }

        e4mcMonitoringStarted = true;

        BattleManager manager = BattleManager.getInstance(); 
        if (manager != null) { 
            manager.setAwaitingE4mcDomain(true);
        }
    }

    private void startRandomItemSelection() {
        if (isAnimatingSlot || selectingItem) return;
        selectingItem = true;
        
        BattleManager.getInstance().randomSelectItemInRoom(roomCode).whenComplete((item, error) ->
            Minecraft.getInstance().execute(() -> {
                selectingItem = false;
                if (Minecraft.getInstance().gui.screen() != this) return;
                if (error != null) {
                    RandomRunMod.LOGGER.error("[RoomWaiting] Не удалось выбрать предмет", error);
                    return;
                }
                if (item != null) {
                    boolean allowUnobtainable = com.randomrun.infrastructure.RandomRunMod.getInstance()
                        .getConfig().isAllowUnobtainableItems();
                    java.util.List<Item> allItems = com.randomrun.features.challenges.classic.data.ItemDifficulty.getAllItems(allowUnobtainable);
                    slotAnimator = new com.randomrun.features.battle.screen.room.util.rendering.SlotMachineAnimator(
                        allItems, item);
                    isAnimatingSlot = true;
                }
            }));
    }
    
    @Override
    public void tick() {
        super.tick();
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || !roomCode.equals(room.getRoomCode())) {
            Minecraft.getInstance().gui.setScreen(parent);
            return;
        }
        if (startGameButton != null) startGameButton.active = !BattleManager.getInstance().isLoadingTriggered();
        if (isAnimatingSlot && slotAnimator != null && slotAnimator.isComplete()) {
            isAnimatingSlot = false;
            slotAnimator = null;
        }
        String state = roomControlState(room);
        if (!isAnimatingSlot && !state.equals(controlState)) {
            clearWidgets();
            init();
        }
    }

    private String roomControlState(BattleRoom room) {
        return room == null ? "" : room.getPlayers() + ":" + room.getMaxPlayers() + ":"
            + room.getTargetItem() + ":" + room.hasItemSelected() + ":" + room.getStatus();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (BattleManager.getInstance().getCurrentRoom() == null) {
            Minecraft.getInstance().gui.setScreen(
                new com.randomrun.ui.screen.main.MainModScreen(new net.minecraft.client.gui.screens.TitleScreen())
            );
            return;
        }

        extractBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        long elapsed = codeAnimator.getElapsedTime();
        
        itemAnimator.update(elapsed, delta);
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        if (room != null) {
            if (room.getPlayers() != null && !isAnimatingSlot) {
                int maxPlayers = room.getMaxPlayers();
                int totalWidth = (maxPlayers - 1) * PLAYER_SPACING;
                int panelLeft = centerX - totalWidth / 2 - PLAYER_SLOT_SIZE / 2 - 10;
                int panelRight = centerX + totalWidth / 2 + PLAYER_SLOT_SIZE / 2 + 10;
                int panelTop = centerY - PLAYER_SLOT_SIZE / 2 + PLAYER_Y_OFFSET - 10;
                int panelBottom = centerY + PLAYER_COUNT_Y_OFFSET + 15;
                context.fill(panelLeft, panelTop, panelRight, panelBottom, 0x40000000);
            }
            
            if (!isAnimatingSlot) {
                renderTargetItem(context, room, centerX);
            }
            
            if (room.getPlayers() != null) {
                renderPlayers(context, room, centerX, centerY);
            }
            
            PanelRenderer.renderRoomInfoPanel(context, font, room, centerX, height, 
                codeAnimator.getAnimatedCode(), elapsed, codeAnimator.getAnimationDuration());
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
        
        if (isAnimatingSlot && slotAnimator != null) {
            slotAnimator.extractRenderState(context, width / 2, ITEM_Y, delta);
        }
    }
    
    private void renderTargetItem(GuiGraphicsExtractor context, BattleRoom room, int centerX) {
        if (font == null) return;
        
        try {
            String itemId = room.getTargetItem();
            
            if (itemId == null || itemId.isEmpty()) {
                if (room.hasItemSelected()) {
                    com.randomrun.ui.util.GuiText.centeredText(context, font,
                        Component.literal("Загрузка..."),
                        centerX, ITEM_NAME_Y, 0xFFFFFF);
                } else {
                    List<String> players = room.getPlayers();
                    if (players != null && players.size() >= 2) {
                        com.randomrun.ui.util.GuiText.centeredText(context, font,
                            Component.literal("§6Случайный"),
                            centerX, ITEM_NAME_Y, 0xFFFFAA00);
                    } else {
                        com.randomrun.ui.util.GuiText.centeredText(context, font,
                            Component.literal("Выберите предмет"),
                            centerX, ITEM_NAME_Y, 0xFFFFFF);
                    }
                }
                return;
            }
            
            Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (targetItem != null) {
                render3DItem(context, centerX, ITEM_Y, targetItem);
                
                if (room.hasItemSelected()) {
                    String itemName = net.minecraft.network.chat.Component.translatable(targetItem.getDescriptionId()).getString();
                    com.randomrun.ui.util.GuiText.centeredText(context, font,
                        Component.literal(itemName),
                        centerX, ITEM_NAME_Y, 0xFFFFFF);
                }
            }
        } catch (Exception e) {
            com.randomrun.ui.util.GuiText.centeredText(context, font,
                Component.literal("Загрузка..."),
                centerX, ITEM_NAME_Y, 0xFFFFFF);
        }
    }
    
    private void renderPlayers(GuiGraphicsExtractor context, BattleRoom room, int centerX, int centerY) {
        List<String> players = room.getPlayers();
        if (players == null) return;
        
        playerWidgets.keySet().removeIf(name -> !players.contains(name));
        
        for (String player : players) {
            playerWidgets.computeIfAbsent(player, PlayerSkinWidget::new);
        }
        
        int maxPlayers = room.getMaxPlayers();
        int totalWidth = (maxPlayers - 1) * PLAYER_SPACING;
        int startX = centerX - totalWidth / 2;
        int slotY = centerY - PLAYER_SLOT_SIZE / 2 + PLAYER_Y_OFFSET;

        int nameY = centerY + PLAYER_NAME_Y_OFFSET;
        
        for (int i = 0; i < maxPlayers; i++) {
            int x = startX + (i * PLAYER_SPACING);
            
            if (i < players.size()) {
                renderPlayer(context, room, players.get(i), x, centerY, nameY);
            } else {
                PanelRenderer.renderEmptySlot(context, font, x, slotY, nameY, PLAYER_SLOT_SIZE);
            }
        }
    }
    
    private void renderPlayer(GuiGraphicsExtractor context, BattleRoom room, String player, int x, int centerY, int nameY) {
        PlayerSkinWidget widget = playerWidgets.get(player);
        if (widget == null) return;
        
        int characterY = centerY + PLAYER_CHARACTER_Y_OFFSET;
        
        widget.extractRenderState(context, x, characterY, PLAYER_CHARACTER_SIZE, 
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        
        PanelRenderer.renderPlayerNamePanel(context, font, player, x, nameY);
        
        if (room.isPlayerReady(player)) {
            int readyY = nameY + 15;
            com.randomrun.ui.util.GuiText.centeredText(context, font, "\u00A7f\u0413\u041E\u0422\u041E\u0412", x, readyY, 0xFFFFFF);
        }
        
        if (isHost && !room.isHost(player)) {
            int kickX = x + 6;
            int kickY = nameY - 10;
            com.randomrun.ui.util.GuiText.centeredText(context, font, "\u00A7cX", kickX, kickY, 0xFF5555);
        }
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int centerX = width / 2;
        
        if (handleRoomCodeClick(mouseX, mouseY)) return true;
        
        if (isHost && handleKickClick(mouseX, mouseY)) return true;
        
        if (mouseX >= centerX - ITEM_AREA_SIZE && mouseX <= centerX + ITEM_AREA_SIZE &&
            mouseY >= ITEM_AREA_TOP && mouseY <= ITEM_AREA_BOTTOM) {
            itemAnimator.startDrag(mouseX, mouseY);
            return true;
        }

        for (PlayerSkinWidget widget : playerWidgets.values()) {
            if (widget.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
                return true;
            }
        }
        
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
    
    private boolean handleRoomCodeClick(double mouseX, double mouseY) {
        if (font == null) return false;
        
        int[] bounds = PanelRenderer.getRoomCodeBounds(font, width / 2, height, 
            codeAnimator.getAnimatedCode());
        
        if (mouseX >= bounds[0] && mouseX <= bounds[2] && 
            mouseY >= bounds[1] && mouseY <= bounds[3]) {
            Minecraft.getInstance().keyboardHandler.setClipboard(roomCode);
            
            if (Minecraft.getInstance().player != null) {
                com.randomrun.infrastructure.util.ClientMessages.send(Minecraft.getInstance().player, net.minecraft.network.chat.Component.literal("§aКод комнаты скопирован: §f" + roomCode), true);
            }
            
            return true;
        }
        
        return false;
    }
    
    private boolean handleKickClick(double mouseX, double mouseY) {
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null) return false;
        
        List<String> players = room.getPlayers();
        if (players == null) return false;
        
        int maxPlayers = room.getMaxPlayers();
        int totalWidth = (maxPlayers - 1) * PLAYER_SPACING;
        int startX = width / 2 - totalWidth / 2;
        int nameY = height / 2 + PLAYER_NAME_Y_OFFSET;
        
        for (int i = 0; i < players.size(); i++) {
            String player = players.get(i);
            if (room.isHost(player)) continue;
            
            int x = startX + (i * PLAYER_SPACING);
            int kickX = x + 6;
            int kickY = nameY - 10;
            
            if (mouseX >= kickX - 6 && mouseX <= kickX + 6 &&
                mouseY >= kickY - 2 && mouseY <= kickY + 10) {
                if (!pendingKicks.add(player)) return true;
                BattleManager.getInstance().kickPlayer(roomCode, player).whenComplete((success, error) ->
                    Minecraft.getInstance().execute(() -> {
                        pendingKicks.remove(player);
                        if (Minecraft.getInstance().gui.screen() != this) return;
                        if (error != null) {
                            RandomRunMod.LOGGER.error("[RoomWaiting] Ошибка исключения игрока", error);
                        } else if (Boolean.TRUE.equals(success)) {
                            clearWidgets();
                            init();
                        }
                    }));
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        for (PlayerSkinWidget widget : playerWidgets.values()) {
            widget.mouseReleased();
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            itemAnimator.stopDrag();
        }
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()));
    }
    
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (itemAnimator.isDragging()) {
            itemAnimator.updateDrag(mouseX, mouseY);
            return true;
        }

        for (PlayerSkinWidget widget : playerWidgets.values()) {
            if (widget.mouseDragged(mouseX, mouseY, deltaX)) {
                return true;
            }
        }
        
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), deltaX, deltaY);
    }
    
    @Override
    public void onClose() {
        playerWidgets.clear();
        
        BattleManager.getInstance().leaveRoom();
        Minecraft.getInstance().gui.setScreen(parent);
    }
    
    @Override
    protected boolean shouldEnableAfk() {
        return false;
    }
}

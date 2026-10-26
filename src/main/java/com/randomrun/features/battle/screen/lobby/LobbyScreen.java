package com.randomrun.features.battle.screen.lobby;

import com.randomrun.features.battle.screen.room.RoomJoinScreen;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.lobby.util.helper.*;
import com.randomrun.features.battle.screen.lobby.util.render.*;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LobbyScreen extends AbstractRandomRunScreen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 20;
    private static final int BUTTON_Y = 15;
    private static final int SEARCH_WIDTH = 180;
    private static final int BUTTON_WIDTH_SMALL = 60;
    private static final int BUTTON_WIDTH_MEDIUM = 120;
    private static final int LIST_MARGIN = 40;
    private static final int LIST_TOP = 50;
    private static final int LIST_BOTTOM_OFFSET = 30;
    private static final int TITLE_Y = 22;
    private static final int ROOM_ENTRY_HEIGHT = 48;
    private static final int ROOM_ENTRY_SPACING = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    
    private com.randomrun.features.battle.util.ItemGlitchEffect glitchEffect;
    private final Screen parent;
    private RoomListWidget roomList;
    private List<BattleRoom> rooms = Collections.emptyList();
    private List<BattleRoom> filteredRooms = Collections.emptyList();
    private String searchQuery = "";
    
    private int pingTextX = 0;
    private int pingTextY = 0;
    private int pingTextWidth = 0;
    
    private final PingMonitor pingMonitor = new PingMonitor();
    private LobbySseListener sseListener;
    private SearchDebouncer searchDebouncer;
    private RoomClickHandler clickHandler;
    private boolean sseConnectionStarted = false;
    private long lifecycle;
    private boolean activeScreen;

    public LobbyScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.lobby"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        stopConnections();
        activeScreen = true;
        final long currentLifecycle = lifecycle;
        
        boolean allowUnobtainable = com.randomrun.infrastructure.RandomRunMod.getInstance()
            .getConfig().isAllowUnobtainableItems();
        glitchEffect = new com.randomrun.features.battle.util.ItemGlitchEffect(allowUnobtainable);
        
        searchDebouncer = new SearchDebouncer(() -> minecraft.execute(() -> {
            if (activeScreen && lifecycle == currentLifecycle) filterRooms();
        }));
        if (clickHandler == null || !clickHandler.isJoining()) clickHandler = new RoomClickHandler(minecraft, parent, this);
        
        initRoomList();
        initButtons();
        filterRooms();
        initPollingConnection();
    }
    
    private void initRoomList() {
        int listWidth = Math.min(600, width - LIST_MARGIN);
        int listHeight = height - LIST_TOP - LIST_BOTTOM_OFFSET;
        int bottom = height - LIST_BOTTOM_OFFSET;
        
        this.roomList = new RoomListWidget(minecraft, listWidth, listHeight, LIST_TOP, bottom, ROOM_ENTRY_HEIGHT);
        int centerX = (width - listWidth) / 2;
        this.roomList.setX(centerX);
        this.addWidget(this.roomList);
    }
    
    private void initButtons() {
        addRenderableWidget(new ButtonDefault(
            BUTTON_MARGIN, BUTTON_Y, BUTTON_WIDTH_SMALL, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> onClose(),
            0, 0.1f
        ));
        
        com.randomrun.ui.widget.styled.TextFieldStyled searchField = 
            new com.randomrun.ui.widget.styled.TextFieldStyled(
                font, 90, BUTTON_Y, SEARCH_WIDTH, BUTTON_HEIGHT, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.search"), 0.05f
            );
        searchField.setCenteredPlaceholder(
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.search_placeholder"));
        searchField.setResponder(text -> {
            searchQuery = text.toLowerCase(Locale.ROOT);
            searchDebouncer.trigger();
        });
        searchField.setValue(searchQuery);
        addRenderableWidget(searchField);
        
        addRenderableWidget(new ButtonRainbow(
            width - 270, BUTTON_Y, BUTTON_WIDTH_MEDIUM, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.create_room"),
            button -> {
                if (clickHandler.isJoining()) return;
                minecraft.gui.setScreen(new com.randomrun.features.battle.screen.room.RoomHostScreenCreateImproved(this, null));
            },
            1, 0.12f
        ));
        
        addRenderableWidget(new ButtonDefault(
            width - 140, BUTTON_Y, BUTTON_WIDTH_MEDIUM, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.enter_code"),
            button -> {
                if (!clickHandler.isJoining()) minecraft.gui.setScreen(new RoomJoinScreen(this));
            },
            1, 0.12f
        ));
    }
    
    private void initPollingConnection() {
        if (sseConnectionStarted) {
            return;
        }
        
        sseConnectionStarted = true;
        final long currentLifecycle = lifecycle;
        
        if (!com.randomrun.core.init.CoreInitializer.isInitialized()) {
            String playerName = minecraft.getUser().getName();
            com.randomrun.core.init.CoreInitializer.initialize(playerName)
                .thenRun(() -> {
                    minecraft.execute(() -> {
                        if (activeScreen && lifecycle == currentLifecycle) startSseConnection();
                    });
                })
                .exceptionally(ex -> {
                    com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[Lobby] Ошибка инициализации: {}", ex.getMessage());
                    minecraft.execute(() -> {
                        if (activeScreen && lifecycle == currentLifecycle) sseConnectionStarted = false;
                    });
                    return null;
                });
        } else {
            startSseConnection();
        }
    }
    
    private void startSseConnection() {
        if (!activeScreen || sseListener != null) return;
        final long currentLifecycle = lifecycle;
        sseListener = new LobbySseListener(
            newRooms -> minecraft.execute(() -> {
                if (activeScreen && lifecycle == currentLifecycle) onRoomsUpdate(newRooms);
            }),
            status -> minecraft.execute(() -> {
                if (activeScreen && lifecycle == currentLifecycle) onConnectionStatusChange(status);
            }),
            () -> minecraft.execute(() -> {
                if (activeScreen && lifecycle == currentLifecycle) onReconnectFailed();
            }),
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LobbySSE-Scheduler");
                t.setDaemon(true);
                return t;
            })
        );
        sseListener.start();
    }
    
    private void onReconnectFailed() {
        com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[Lobby] SSE переподключение не удалось");
    }
    
    private LobbyPollingManager.ConnectionStatus convertStatus(LobbySseListener.ConnectionStatus sseStatus) {
        switch (sseStatus) {
            case CONNECTED: return LobbyPollingManager.ConnectionStatus.CONNECTED;
            case CONNECTING: return LobbyPollingManager.ConnectionStatus.CONNECTING;
            case DISCONNECTED: return LobbyPollingManager.ConnectionStatus.DISCONNECTED;
            case ERROR: return LobbyPollingManager.ConnectionStatus.ERROR;
            default: return LobbyPollingManager.ConnectionStatus.DISCONNECTED;
        }
    }
    
    private void onRoomsUpdate(List<BattleRoom> newRooms) {
        this.rooms = List.copyOf(newRooms);
        filterRooms();
    }
    
    private void onConnectionStatusChange(LobbySseListener.ConnectionStatus status) {
    }
    
    private void filterRooms() {
        filteredRooms = RoomSearchFilter.filter(rooms, searchQuery);
        filteredRooms = RoomSorter.sort(filteredRooms, RoomSorter.SortType.PLAYERS_DESC);
        if (activeScreen && roomList != null) roomList.setRooms(filteredRooms);
    }

    @Override
    public void onClose() {
        if (clickHandler == null || !clickHandler.isJoining()) minecraft.gui.setScreen(parent);
    }

    private void stopConnections() {
        activeScreen = false;
        lifecycle++;
        if (sseListener != null) {
            sseListener.stop();
            sseListener = null;
        }
        if (searchDebouncer != null) {
            searchDebouncer.cancel();
        }
        sseConnectionStarted = false;
    }

    @Override
    public void removed() {
        stopConnections();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        pingMonitor.update();
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (roomList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mouseX >= pingTextX && mouseX <= pingTextX + pingTextWidth &&
            mouseY >= pingTextY && mouseY <= pingTextY + font.lineHeight) {
            pingMonitor.forceUpdate();
            minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f
                )
            );
            return true;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        
        if (roomList != null) {
            roomList.renderBackgrounds(context, mouseX, mouseY);
        }
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.lobby"), 
            width / 2, TITLE_Y, UIColors.TEXT_WHITE);
        
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        if (roomList != null) {
            roomList.renderContent(context, mouseX, mouseY, delta);
        }
        
        renderBottomInfo(context);
        renderEmptyState(context);
    }
    
    private void renderBottomInfo(GuiGraphicsExtractor context) {
        int bottomY = height - 20;
        
        String countText = String.format("Найдено: %d", filteredRooms.size());
        int leftMargin = (width - Math.min(600, width - LIST_MARGIN)) / 2;
        com.randomrun.ui.util.GuiText.text(context, font, Component.literal(countText), 
            leftMargin, bottomY, UIColors.TEXT_GRAY);
        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        String dateTime = now.format(dateFormatter) + " " + now.format(timeFormatter);
        String pingText = pingMonitor.isUpdating() ? "..." : pingMonitor.getPingText();
        
        int dateTimeWidth = font.width(dateTime);
        int pingWidth = font.width(pingText);
        int totalWidth = dateTimeWidth + 15 + pingWidth;
        int startX = (width - totalWidth) / 2;
        
        com.randomrun.ui.util.GuiText.text(context, font, Component.literal(dateTime), startX, bottomY, UIColors.TEXT_GRAY);
        
        pingTextX = startX + dateTimeWidth + 15;
        pingTextY = bottomY;
        pingTextWidth = pingWidth;
        
        com.randomrun.ui.util.GuiText.text(context, font, Component.literal(pingText), 
            pingTextX, pingTextY, pingMonitor.getPingColor());
        
        if (sseListener != null) {
            int listWidth = Math.min(600, width - LIST_MARGIN);
            int rightMargin = (width + listWidth) / 2;
            int connectionX = rightMargin - 110;
            ConnectionStatusRenderer.extractRenderState(context, font, 
                convertStatus(sseListener.getStatus()), connectionX, bottomY);
        }
    }
    
    private void renderEmptyState(GuiGraphicsExtractor context) {
        if (sseListener != null && sseListener.getStatus() == LobbySseListener.ConnectionStatus.CONNECTING 
            && filteredRooms.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.loading"), 
                width / 2, height / 2, UIColors.TEXT_GRAY);
        } else if (filteredRooms.isEmpty() && !searchQuery.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.rooms_not_found_query", searchQuery), 
                width / 2, height / 2, UIColors.TEXT_GRAY);
        } else if (filteredRooms.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.rooms_not_found"), 
                width / 2, height / 2, UIColors.TEXT_GRAY);
        }
    }
    
    class RoomListWidget extends ObjectSelectionList<RoomListWidget.RoomEntry> {
        public RoomListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, itemHeight);
        }
        
        public void setRooms(List<BattleRoom> rooms) {
            this.clearEntries();
            for (BattleRoom room : rooms) {
                this.addEntry(new RoomEntry(room));
            }
        }
        
        @Override
        public int getRowWidth() {
            return width - 20 - SCROLLBAR_WIDTH;
        }
        
        @Override
        public int scrollBarX() {
            return this.getX() + this.width - SCROLLBAR_WIDTH;
        }
        
        @Override
        protected int scrollerHeight() {
            int contentHeight = this.getItemCount() * this.defaultEntryHeight;
            int visibleHeight = this.getBottom() - this.getY();
            if (contentHeight <= visibleHeight) {
                return 0;
            }
            int thumbHeight = (visibleHeight * visibleHeight) / contentHeight;
            return Math.max(thumbHeight, 32);
        }
        
        protected void extractBackground(GuiGraphicsExtractor context) {
        }
        
        public void renderBackgrounds(GuiGraphicsExtractor context, int mouseX, int mouseY) {
            int entryHeight = this.defaultEntryHeight - ROOM_ENTRY_SPACING;
            int entryWidth = this.getRowWidth();
            int left = this.getRowLeft();
            
            for (int i = 0; i < this.getItemCount(); i++) {
                int entryTop = this.getRowTop(i);
                int entryBottom = entryTop + entryHeight;
                
                if (entryBottom >= this.getY() && entryTop <= this.getBottom()) {
                    RoomEntry entry = this.children().get(i);
                    boolean hovered = this.isMouseOver(mouseX, mouseY) && 
                                     mouseY >= entryTop && mouseY < entryBottom;
                    entry.renderDecoration(context, entryTop, left, entryWidth, entryHeight, hovered);
                }
            }
            
            renderScrollbar(context);
        }
        
        public void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int entryHeight = this.defaultEntryHeight - ROOM_ENTRY_SPACING;
            int entryWidth = this.getRowWidth();
            int left = this.getRowLeft();
            
            for (int i = 0; i < this.getItemCount(); i++) {
                int entryTop = this.getRowTop(i);
                int entryBottom = entryTop + entryHeight;
                
                if (entryBottom >= this.getY() && entryTop <= this.getBottom()) {
                    RoomEntry entry = this.children().get(i);
                    boolean hovered = this.isMouseOver(mouseX, mouseY) && 
                                     mouseY >= entryTop && mouseY < entryBottom;
                    entry.extractContent(context, mouseX, mouseY, hovered, delta);
                }
            }
        }
        
        private void renderScrollbar(GuiGraphicsExtractor context) {
            int contentHeight = this.getItemCount() * this.defaultEntryHeight;
            int visibleHeight = this.getBottom() - this.getY();
            int maxScroll = Math.max(0, contentHeight - visibleHeight);
            
            if (maxScroll <= 0) {
                return;
            }
            
            int scrollbarX = this.scrollBarX();
            int scrollbarTop = this.getY();
            int scrollbarBottom = this.getBottom();
            int scrollbarHeight = scrollbarBottom - scrollbarTop;
            
            context.fill(scrollbarX, scrollbarTop, scrollbarX + SCROLLBAR_WIDTH, scrollbarBottom, 0x80000000);
            
            int thumbHeight = this.scrollerHeight();
            if (thumbHeight > 0) {
                double scrollAmount = this.scrollAmount();
                
                int thumbY = scrollbarTop + (int) ((scrollbarHeight - thumbHeight) * (scrollAmount / maxScroll));
                
                context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, UIColors.BORDER_NORMAL);
            }
        }
        
        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        }
        
        class RoomEntry extends ObjectSelectionList.Entry<RoomEntry> {
            private final BattleRoom room;
            private final ItemStack roomIconStack;
            private final String modeTextCache;
            private final boolean isRandomItem;
            
            public RoomEntry(BattleRoom room) {
                this.room = room;
                this.isRandomItem = room.getTargetItem() == null || room.getTargetItem().isEmpty();
                this.roomIconStack = RoomListRenderer.createRoomIcon(room.getTargetItem(), glitchEffect);
                this.modeTextCache = RoomListRenderer.getRoomModeText(room);
            }
            
            public void renderDecoration(GuiGraphicsExtractor context, int top, int left, int entryWidth, 
                                        int entryHeight, boolean hovered) {
                int bgColor = room.getStatus() != BattleRoom.RoomStatus.WAITING ? UIColors.BG_DISABLED :
                             (hovered ? UIColors.BG_HOVERED : UIColors.BG_NORMAL);
                int borderColor = room.getStatus() != BattleRoom.RoomStatus.WAITING ? UIColors.BORDER_DISABLED :
                                 (hovered ? UIColors.BORDER_HOVERED : UIColors.BORDER_NORMAL);
                
                context.fill(left, top, left + entryWidth, top + entryHeight, bgColor);
                
                int borderWidth = 2;
                context.fill(left, top, left + entryWidth, top + borderWidth, borderColor);
                context.fill(left, top + entryHeight - borderWidth, left + entryWidth, top + entryHeight, borderColor);
                context.fill(left, top, left + borderWidth, top + entryHeight, borderColor);
                context.fill(left + entryWidth - borderWidth, top, left + entryWidth, top + entryHeight, borderColor);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int left = getX();
                int top = getY();
                int entryWidth = getWidth();
                int entryHeight = getHeight() - ROOM_ENTRY_SPACING;
                ItemStack icon = isRandomItem ? 
                    RoomListRenderer.createRoomIcon(null, glitchEffect) : roomIconStack;
                RoomListRenderer.renderRoomEntry(context, font, room, icon, 
                    modeTextCache, left, top, entryWidth, entryHeight, hovered);
            }
            
            @Override
            public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                    return clickHandler.handleClick(room);
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return com.randomrun.infrastructure.util.LanguageManager.text(
                    "randomrun.battle.room_narration", room.getHost());
            }
        }
    }
}

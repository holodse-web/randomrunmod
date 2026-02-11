package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

public class LobbyScreen extends AbstractRandomRunScreen {

    private final Screen parent;
    private RoomListWidget roomList;
    private boolean isLoading = true;
    private List<BattleRoom> rooms = Collections.emptyList();
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = 3000; // Auto-refresh every 5s

    public LobbyScreen(Screen parent) {
        super(Text.translatable("randomrun.battle.lobby"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        
        int listWidth = Math.min(600, width - 40);
        int listHeight = height - 80;
        int top = 50;
        int bottom = height - 30;
        
        this.roomList = new RoomListWidget(client, listWidth, listHeight, top, bottom, 48);
        this.roomList.setX((width - listWidth) / 2);
        this.addSelectableChild(this.roomList);
        
        // Кнопки
    // Кнопка назад (Левый верхний угол)
    addDrawableChild(new ButtonDefault(
        20, 15, 60, 20,
        Text.translatable("randomrun.button.back"),
        button -> client.setScreen(parent),
        0, 0.1f
    ));
    
    // Поле поиска (Справа от кнопки назад)
    // Уменьшаем ширину до 180, чтобы расстояние до центра (текста Лобби) было таким же, как у кнопки "Создать комнату" справа
    // Search Ends at 90 + 180 = 270. Gap to Center = W/2 - 270.
    // Create Room Starts at W - 270. Gap to Center = (W - 270) - W/2 = W/2 - 270.
    com.randomrun.ui.widget.styled.TextFieldStyled searchField = new com.randomrun.ui.widget.styled.TextFieldStyled(
        textRenderer, 90, 15, 180, 20, Text.translatable("randomrun.search"), 0.05f
    );
    searchField.setCenteredPlaceholder(Text.translatable("randomrun.battle.search_placeholder"));
    searchField.setChangedListener(text -> {
        // Логика фильтрации комнат (нужно реализовать в refreshRooms или отдельно)
        // Пока просто сохраняем текст, фильтрацию добавим позже
    });
    addDrawableChild(searchField);
    
    // Заголовок
    // Рендерится в render()
        
        // Создать комнату (Слева от поиска)
        addDrawableChild(new ButtonRainbow(
            width - 270, 15, 120, 20,
            Text.translatable("randomrun.battle.create_room"),
            button -> client.setScreen(new PrivateHostScreen(this)),
            1, 0.12f
        ));
        
        // Присоединиться по коду / Поиск (Правый верхний угол)
        addDrawableChild(new ButtonDefault(
            width - 140, 15, 120, 20,
            Text.translatable("randomrun.battle.enter_code"),
            button -> client.setScreen(new PrivateJoinScreen(this)),
            1, 0.12f
        ));
        
        refreshRooms();
    }
    
    private void refreshRooms() {
        isLoading = true;
        BattleManager.getInstance().getAllRooms().thenAccept(fetchedRooms -> {
            this.rooms = fetchedRooms;
            this.isLoading = false;
            
            client.execute(() -> {
                roomList.setRooms(rooms);
            });
        });
        lastRefreshTime = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        if (System.currentTimeMillis() - lastRefreshTime > REFRESH_INTERVAL) {
            refreshRooms();
        }
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (roomList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        
        // Сначала рендерим стандартные виджеты (кнопки)
        super.render(context, mouseX, mouseY, delta);
        
        // ПОСЛЕ super.render рендерим список комнат
        roomList.render(context, mouseX, mouseY, delta);
        
        // Title поверх всего
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.battle.lobby"), width / 2, 20, 0xFFFFFF);
        
        if (isLoading && rooms.isEmpty()) {
             context.drawCenteredTextWithShadow(textRenderer, Text.literal("Загрузка..."), width / 2, height / 2, 0xAAAAAA);
        } else if (rooms.isEmpty()) {
             context.drawCenteredTextWithShadow(textRenderer, Text.literal("Комнаты не найдены"), width / 2, height / 2, 0xAAAAAA);
        }
    }
    
    class RoomListWidget extends AlwaysSelectedEntryListWidget<RoomListWidget.RoomEntry> {
        
        public RoomListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, itemHeight);
            // this.setRenderBackground(false);
            // this.setRenderHeader(false, 0);
        }
        
        public void setRooms(List<BattleRoom> rooms) {
            this.clearEntries();
            for (BattleRoom room : rooms) {
                this.addEntry(new RoomEntry(room));
            }
        }
        
        @Override
        public int getRowWidth() {
            return width - 20;
        }
        
        @Override
        public int getScrollbarX() {
            return this.getX() + this.width - 6;
        }
        
        class RoomEntry extends AlwaysSelectedEntryListWidget.Entry<RoomEntry> {
            private final BattleRoom room;
            
            public RoomEntry(BattleRoom room) {
                this.room = room;
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                // СНАЧАЛА очищаем область (черный полупрозрачный фон для видимости)
                context.fill(left - 1, top - 1, left + entryWidth + 1, top + entryHeight + 1, 0x80000000);
                
                // Фон (ПОЛНОСТЬЮ НЕПРОЗРАЧНЫЙ)
                int color = hovered ? 0xFF2a1b3e : 0xFF1a0b2e; // Fully opaque (0xFF)
                context.fill(left, top, left + entryWidth, top + entryHeight, color);
                
                // Граница (Ручная реализация) - ОБНОВЛЕННЫЙ ЦВЕТ
                int borderColor = hovered ? 0xFFd042ff : 0xFF6930c3; // Ярко-фиолетовый при наведении, темно-фиолетовый в обычном состоянии
                
                // Если комната НЕ в ожидании, затемняем границу и фон
                if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
                    borderColor = 0xFF555555; // Серый бордюр
                    // Затемнение фона (Оверлей)
                    context.fill(left, top, left + entryWidth, top + entryHeight, 0x80000000);
                }
                
                // Рисуем границы (непрозрачные, толщина 2px)
                context.fill(left - 1, top - 1, left + entryWidth + 1, top + 1, borderColor); // Верх
                context.fill(left - 1, top + entryHeight - 1, left + entryWidth + 1, top + entryHeight + 1, borderColor); // Низ
                context.fill(left - 1, top - 1, left + 1, top + entryHeight + 1, borderColor); // Лево
                context.fill(left + entryWidth - 1, top - 1, left + entryWidth + 1, top + entryHeight + 1, borderColor); // Право
                
                // Иконка (Целевой предмет) - Масштаб 2x
                String itemId = room.getTargetItem();
                if (itemId == null || itemId.isEmpty()) {
                    itemId = "minecraft:barrier";
                }
                
                Item item = Registries.ITEM.get(Identifier.of(itemId));
                ItemStack stack = new ItemStack(item);
                
                context.getMatrices().push();
                context.getMatrices().translate(left + 8, top + 8, 0); // Отступ 8
                context.getMatrices().scale(2.0f, 2.0f, 1.0f); // Размер 32x32
                context.drawItem(stack, 0, 0);
                context.getMatrices().pop();
                
                // Имя комнаты / Хост
                String host = room.getHost() != null ? room.getHost() : "Unknown";
                // Сдвиг текста на x=50 (8 отступ + 32 иконка + 10 отступ)
                context.drawTextWithShadow(textRenderer, Text.literal(host + "'s Room"), left + 50, top + 10, 0xFFFFFF);
                
                // Инфо: Режим | Игроки ИЛИ Статус
                String mode;
                String cm = room.getCreationMode();
                
                if (cm != null) {
                    if ("rv".equals(cm)) mode = Text.translatable("randomrun.battle.shared_world_radmin").getString();
                    else if ("e4".equals(cm)) mode = Text.translatable("randomrun.battle.shared_world").getString();
                    else mode = Text.translatable("randomrun.battle.separate_worlds").getString();
                } else if (room.isSharedWorld()) {
                     // Fallback for old rooms without cm
                     String addr = room.getServerAddress();
                     if (addr != null && (addr.startsWith("26.") || addr.startsWith("192.168.") || addr.startsWith("127.") || addr.equals("localhost"))) {
                         mode = Text.translatable("randomrun.battle.shared_world_radmin").getString();
                     } else {
                         mode = Text.translatable("randomrun.battle.shared_world").getString();
                     }
                } else {
                    mode = Text.translatable("randomrun.battle.separate_worlds").getString();
                }
                
                String info = mode + " | " + room.getPlayers().size() + "/" + room.getMaxPlayers();
                
                if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
                    // Показать статус "В игре"
                    String statusText = "§c⚠ " + Text.translatable("randomrun.battle.status.in_game").getString();
                    // Резервный вариант, если ключ перевода отсутствует
                    if (statusText.contains("randomrun.battle.status")) statusText = "§c⚠ В игре";
                    
                    context.drawTextWithShadow(textRenderer, Text.literal(statusText), left + 50, top + 26, 0xFFAA00);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal(info), left + 50, top + 26, 0xAAAAAA);
                }
                
                // Иконка замка (если приватно)
                if (room.isPrivate()) {
                    context.drawTextWithShadow(textRenderer, Text.literal("🔒"), left + entryWidth - 20, top + 20, 0xFFAA00);
                }
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // Запретить присоединение, если не WAITING
                if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
                    client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f));
                    return false;
                }
                
                // Запретить присоединение, если комната ПОЛНАЯ
                if (room.getPlayers().size() >= room.getMaxPlayers()) {
                     client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f));
                     // Опционально: Показать сообщение "Room Full" (сейчас только звук)
                     return false;
                }
                
                if (room.isPrivate()) {
                    client.setScreen(new PasswordInputScreen(LobbyScreen.this, room));
                } else {
                    // Присоединиться к публичной
                    BattleManager.getInstance().joinRoom(client.getSession().getUsername(), room.getRoomCode(), "")
                        .thenAccept(success -> {
                            if (success) {
                                client.execute(() -> client.setScreen(new BattleWaitingScreen(parent, room.getRoomCode(), false)));
                            }
                        });
                }
                return true;
            }

            @Override
            public Text getNarration() {
                return Text.literal("Комната игрока " + room.getHost());
            }
        }
    }
}

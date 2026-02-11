/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.challenges.classic.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.battle.screen.LobbyScreen;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SpeedrunScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private TextFieldStyled searchField;
    private List<Item> filteredItems = new ArrayList<>();
    private List<Item> allItems = new ArrayList<>();
    
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 6;
    private static final int ITEM_SIZE = 20;
    private static final int GRID_PADDING = 2;
    
    // Slot machine animation
    // Анимация слот-машины
    private boolean slotMachineActive = false;
    private long slotMachineStartTime;
    private int slotMachineIndex = 0;
    private Item slotMachineResult = null;
    private boolean soundPlayed = false;
    private long lastTickTime = 0;
    private List<Item> slotMachineItems = new ArrayList<>();
    private static final long SLOT_MACHINE_DURATION = 3000;
    private List<Particle> particles = new ArrayList<>();
    private long openTime;
    
    public SpeedrunScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.speedrun.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        boolean allowUnobtainable = RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
        allItems = ItemDifficulty.getAllItems(allowUnobtainable);
        filteredItems = new ArrayList<>(allItems);
        
        int centerX = width / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        // Search field (above grid)
        // Поле поиска (над сеткой)
        searchField = new TextFieldStyled(textRenderer, centerX - 100, gridY - 30, 200, 20, Text.translatable("randomrun.search"));
        searchField.setCenteredPlaceholder(Text.translatable("randomrun.search.placeholder"));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);
        
        // Random item button (below grid)
        // Кнопка случайного предмета (под сеткой)
        addDrawableChild(new ButtonDefault(
            centerX - 100, gridY + gridHeight + 10,
            200, 20,
            Text.translatable("randomrun.button.random_item"),
            button -> startRandomSelection(),
            0, 0.1f
        ));
        
        // Online button
        ButtonRainbow onlineButton = new ButtonRainbow(
            width / 2 - 100, height - 55,
            200, 20,
            Text.translatable("randomrun.menu.online"),
            button -> MinecraftClient.getInstance().setScreen(new LobbyScreen(this)),
            1, 0.12f
        );
        
        // Disable button if online mode is OFF
        // Отключить кнопку, если онлайн режим выключен
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            onlineButton.active = false;
            onlineButton.setMessage(Text.translatable("randomrun.menu.online_disabled").formatted(Formatting.RED));
        }
        
        addDrawableChild(onlineButton);
        
        // Кнопка назад (по центру внизу)
        // Back button (centered at bottom)
        addDrawableChild(new ButtonDefault(
            width / 2 - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            2, 0.15f
        ));
    }
    
    private void onSearchChanged(String text) {
        filteredItems.clear();
        String searchLower = text.toLowerCase();
        
        for (Item item : allItems) {
            String itemName = item.getName().getString().toLowerCase();
            String itemId = Registries.ITEM.getId(item).toString().toLowerCase();
            
            if (itemName.contains(searchLower) || itemId.contains(searchLower)) {
                filteredItems.add(item);
            }
        }
        
        scrollOffset = 0;
    }
    
    private void startRandomSelection() {
        List<Item> availableItems = ItemDifficulty.getAllItems(
            RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems()
        );
        
        if (availableItems.isEmpty()) return;
        
        // Setup slot machine
        // Настройка слот-машины
        this.slotMachineItems = new ArrayList<>(availableItems);
        Collections.shuffle(this.slotMachineItems); // Перемешать для случайности
        
        // Pick result
        // Выбрать результат
        Item randomItem = this.slotMachineItems.get(0);
        this.slotMachineResult = randomItem;
        
        // Start animation
        // Запуск анимации
        this.slotMachineActive = true;
        this.slotMachineStartTime = System.currentTimeMillis();
        this.slotMachineIndex = 0;
        this.soundPlayed = false;
        this.lastTickTime = System.currentTimeMillis();
        this.particles.clear();
    }
    
    // Security layer 2
    public static final byte[] URL_P2 = {18, 65, 86, 75, 12, 48, 31, 12, 22, 114, 11, 87, 93, 7, 10, 23, 15};
    public static final String S2 = "speed_scr_x2";
    public static final byte[] KEY_P2 = {67, 64, 92, 87, 92, 104, 69, 86};
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Calculate animation progress
        long elapsed = System.currentTimeMillis() - openTime;
        float animationProgress = Math.min(1f, elapsed / 400f);
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int slideOffset = (int) ((1f - easedProgress) * 30);

        // Call parent render (handles fade automatically)
        // Вызов родительского рендера (автоматически обрабатывает затемнение)
        super.render(context, mouseX, mouseY, delta);
        
        if (slotMachineActive) {
            renderSlotMachine(context, delta);
            updateParticles(delta);
            
            // Auto-proceed when slot machine finishes
            // Авто-продолжение, когда слот-машина завершит работу
            long elapsedSlot = System.currentTimeMillis() - slotMachineStartTime;
            if (elapsedSlot >= SLOT_MACHINE_DURATION + 1000 && slotMachineResult != null) {
                // Animation finished, open reveal screen
                // Анимация завершена, открыть экран показа
                MinecraftClient.getInstance().setScreen(new ItemRevealScreen(this, slotMachineResult));
                slotMachineActive = false;
            }
        } else {
            // Title
            // Заголовок
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
            
            // Show grid
            // Показать сетку
            int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
            searchField.setY(gridY - 30 + slideOffset);
            
            renderItemGrid(context, mouseX, mouseY, slideOffset);
            
            // Render scroll indicator
            // Рендер индикатора прокрутки
            if (filteredItems.size() > ITEMS_PER_ROW * VISIBLE_ROWS) {
                renderScrollIndicator(context, mouseX, mouseY, slideOffset);
            }
        }
    }
    
    private void renderItemGrid(DrawContext context, int mouseX, int mouseY, int slideOffset) {
        int gridX = width / 2 - (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
        
        // Background for grid
        // Фон для сетки
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        context.fill(gridX - 5, gridY - 5, gridX + gridWidth + 5, gridY + gridHeight + 5, 0x80000000);
        
        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + ITEMS_PER_ROW * VISIBLE_ROWS, filteredItems.size());
        
        Item hoveredItem = null;
        int hoveredX = 0, hoveredY = 0;
        
        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int row = localIndex / ITEMS_PER_ROW;
            int col = localIndex % ITEMS_PER_ROW;
            
            int x = gridX + col * (ITEM_SIZE + GRID_PADDING);
            int y = gridY + row * (ITEM_SIZE + GRID_PADDING);
            
            Item item = filteredItems.get(i);
            ItemStack stack = new ItemStack(item);
            
            // Check hover
            // Проверка наведения
            boolean hovered = mouseX >= x && mouseX < x + ITEM_SIZE &&
                             mouseY >= y && mouseY < y + ITEM_SIZE;
            
            // Draw slot background
            // Рисование фона слота
            int bgColor = hovered ? 0xFF6930c3 : 0xFF3a3a5c;
            context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);
            
            // Draw item
            // Рисование предмета
            context.drawItem(stack, x + 2, y + 2);
            
            if (hovered) {
                hoveredItem = item;
                hoveredX = mouseX;
                hoveredY = mouseY;
            }
        }
        
        // Render tooltip for hovered item
        // Рендер подсказки для наведенного предмета
        if (hoveredItem != null) {
            ItemStack stack = new ItemStack(hoveredItem);
            context.drawItemTooltip(textRenderer, stack, hoveredX, hoveredY);
        }
    }
    
    private void renderSlotMachine(DrawContext context, float delta) {
        long elapsed = System.currentTimeMillis() - slotMachineStartTime;
        float progress = Math.min(1.0f, elapsed / (float) SLOT_MACHINE_DURATION);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Pulsating background
        // Пульсирующий фон
        float pulse = (float) Math.sin(elapsed / 200.0) * 0.1f + 0.9f;
        int bgAlpha = (int) (0xCC * pulse) << 24;
        context.fill(centerX - 70, centerY - 60, centerX + 70, centerY + 60, bgAlpha);
        
        // Animated border
        // Анимированная рамка
        int borderColor = 0xFF6930c3;
        if (progress >= 1.0f) {
            float glow = (float) Math.sin(elapsed / 150.0) * 0.3f + 0.7f;
            borderColor = interpolateColor(0xFF6930c3, 0xFFFFD700, glow);
        }
        context.drawBorder(centerX - 70, centerY - 60, 140, 120, borderColor);
        
        if (progress < 1.0f) {
            // Spinning animation
            // Анимация вращения
            long currentTime = System.currentTimeMillis();
            int tickInterval = (int) (50 + progress * 200);
            
            if (currentTime - lastTickTime >= tickInterval) {
                slotMachineIndex = (slotMachineIndex + 1) % slotMachineItems.size();
                lastTickTime = currentTime;
                
                // Spawn particles
                // Создание частиц
                spawnParticles(centerX, centerY);
                
                if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                    float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                    float pitch = 0.8f + progress * 0.4f;
                    MinecraftClient.getInstance().getSoundManager().play(
                        PositionedSoundInstance.master(
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            pitch,
                            volume * 0.15f
                        )
                    );
                }
            }
            
            Item displayItem = slotMachineItems.get(slotMachineIndex);
            ItemStack stack = new ItemStack(displayItem);
            
            // Rotating icon with motion blur effect
            // Вращающаяся иконка с эффектом размытия в движении
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY, 0);
            
            // Rotation
            // Вращение
            float rotation = (currentTime % 1000) / 1000.0f * 360f;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation * progress));
            
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(stack, -8, -8);
            matrices.pop();
            
            // Spinning text
            // Вращающийся текст
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.slotmachine.spinning"), 
                centerX, centerY + 48, interpolateColor(0xFFFFFF, 0xFFD700, (float) Math.sin(elapsed / 100.0)));
        } else {
            // Result with celebration
            // Результат с празднованием
            if (!soundPlayed && RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(
                        SoundEvents.ENTITY_PLAYER_LEVELUP,
                        1.2f,
                        volume * 0.7f
                    )
                );
                soundPlayed = true;
                
                // Spawn celebration particles
                // Создание праздничных частиц
                Random random = new Random();
                for (int i = 0; i < 30; i++) {
                    spawnCelebrationParticle(centerX, centerY, random);
                }
            }
            
            // Bouncing icon animation
            // Анимация прыгающей иконки
            float bounceProgress = (elapsed - SLOT_MACHINE_DURATION) / 1000.0f;
            float bounce = (float) Math.abs(Math.sin(bounceProgress * Math.PI * 4)) * (1 - bounceProgress) * 10;
            
            ItemStack stack = new ItemStack(slotMachineResult);
            
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY - bounce, 0);
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(stack, -8, -8);
            matrices.pop();
        }
        
        // Render particles
        // Рендер частиц
        renderParticles(context, delta);
    }
    
    private void spawnParticles(int x, int y) {
        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2;
            float speed = 1 + random.nextFloat() * 2;
            particles.add(new Particle(x, y, 
                (float) Math.cos(angle) * speed, 
                (float) Math.sin(angle) * speed, 
                interpolateColor(0xFF6930c3, 0xFFA78BFA, random.nextFloat())));
        }
    }
    
    private void spawnCelebrationParticle(int x, int y, Random random) {
        float angle = random.nextFloat() * (float) Math.PI * 2;
        float speed = 2 + random.nextFloat() * 4;
        particles.add(new Particle(x, y, 
            (float) Math.cos(angle) * speed, 
            (float) Math.sin(angle) * speed, 
            interpolateColor(0xFFFFD700, 0xFFFFFFFF, random.nextFloat())));
    }
    
    private void updateParticles(float delta) {
        particles.removeIf(p -> {
            p.update(delta);
            return p.life <= 0;
        });
    }
    
    private void renderParticles(DrawContext context, float delta) {
        for (Particle p : particles) {
            int alpha = (int) (p.life * 255);
            int color = (p.color & 0x00FFFFFF) | (alpha << 24);
            context.fill((int) p.x, (int) p.y, (int) p.x + 3, (int) p.y + 3, color);
        }
    }
    
    private int interpolateColor(int color1, int color2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    private void renderScrollIndicator(DrawContext context, int mouseX, int mouseY, int slideOffset) {
        int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        
        if (maxScroll > 0) {
            int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
            int scrollBarHeight = gridHeight;
            int scrollBarX = width / 2 + (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2 + 10;
            int scrollBarY = gridY;
            
            context.fill(scrollBarX, scrollBarY, scrollBarX + 6, scrollBarY + scrollBarHeight, 0x80000000);
            
            float thumbRatio = VISIBLE_ROWS / (float) totalRows;
            int thumbHeight = (int) (scrollBarHeight * thumbRatio);
            float scrollRatio = scrollOffset / (float) maxScroll;
            int thumbY = scrollBarY + (int) ((scrollBarHeight - thumbHeight) * scrollRatio);
            
            boolean hoveringThumb = mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                                   mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
            
            int thumbColor = (hoveringThumb || isDraggingScrollbar) ? 0xFF8B5CF6 : 0xFF6930c3;
            context.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight, thumbColor);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (slotMachineActive) return false;
        
        // Calculate slide offset to match render
        long elapsed = System.currentTimeMillis() - openTime;
        float animationProgress = Math.min(1f, elapsed / 400f);
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int slideOffset = (int) ((1f - easedProgress) * 30);
        
        // Scrollbar logic
        // Логика полосы прокрутки
        if (filteredItems.size() > ITEMS_PER_ROW * VISIBLE_ROWS) {
            int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            if (maxScroll > 0) {
                 int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
                 int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
                 int scrollBarHeight = gridHeight;
                 int scrollBarX = width / 2 + (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2 + 10;
                 int scrollBarY = gridY;
                 
                 float thumbRatio = VISIBLE_ROWS / (float) totalRows;
                 int thumbHeight = (int) (scrollBarHeight * thumbRatio);
                 float scrollRatio = scrollOffset / (float) maxScroll;
                 int thumbY = scrollBarY + (int) ((scrollBarHeight - thumbHeight) * scrollRatio);
                 
                 if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    isDraggingScrollbar = true;
                    return true;
                }
            }
        }
        
        // Клик по сетке
        // Grid click
        int gridX = width / 2 - (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        if (mouseX >= gridX && mouseX < gridX + gridWidth &&
            mouseY >= gridY && mouseY < gridY + gridHeight) {
            
            int col = (int) ((mouseX - gridX) / (ITEM_SIZE + GRID_PADDING));
            int row = (int) ((mouseY - gridY) / (ITEM_SIZE + GRID_PADDING));
            int index = (scrollOffset + row) * ITEMS_PER_ROW + col;
            
            if (index >= 0 && index < filteredItems.size()) {
                Item selected = filteredItems.get(index);
                MinecraftClient.getInstance().setScreen(new ItemRevealScreen(this, selected));
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (slotMachineActive) return false;
        
        int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount));
        return true;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (slotMachineActive) return false;
        
        if (isDraggingScrollbar) {
            // Calculate slide offset to match render
            long elapsed = System.currentTimeMillis() - openTime;
            float animationProgress = Math.min(1f, elapsed / 400f);
            float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
            int slideOffset = (int) ((1f - easedProgress) * 30);
            
            int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            if (maxScroll > 0) {
                int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
                int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
                int scrollBarHeight = gridHeight;
                int scrollBarY = gridY;
                
                float thumbRatio = VISIBLE_ROWS / (float) totalRows;
                int thumbHeight = (int) (scrollBarHeight * thumbRatio);
                
                float relativeY = (float) (mouseY - scrollBarY - thumbHeight / 2);
                float scrollRatio = relativeY / (scrollBarHeight - thumbHeight);
                scrollRatio = Math.max(0, Math.min(1, scrollRatio));
                
                scrollOffset = (int) (scrollRatio * maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    // Helper class for particles
    // Вспомогательный класс для частиц
    private static class Particle {
        float x, y;
        float vx, vy;
        float life = 1.0f;
        int color;
        
        Particle(float x, float y, float vx, float vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }
        
        void update(float delta) {
            x += vx;
            y += vy;
            vy += 0.1f; // Гравитация
            life -= 0.02f;
        }
    }
}

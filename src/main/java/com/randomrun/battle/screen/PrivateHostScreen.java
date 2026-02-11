package com.randomrun.battle.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PrivateHostScreen extends AbstractRandomRunScreen {
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
    
    private Item selectedItem = null;
    
    // Анимация слот-машины
    private boolean slotMachineActive = false;
    private long slotMachineStartTime;
    private int slotMachineIndex = 0;
    private Item slotMachineResult = null;
    private boolean soundPlayed = false;
    private long lastTickTime = 0;
    private List<Item> slotMachineItems = new ArrayList<>();
    private static final long SLOT_MACHINE_DURATION = 3000;
    private long openTime;

    // Particle effects for slot machine
    private List<Particle> particles = new ArrayList<>();
    
    public PrivateHostScreen(Screen parent) {
        super(Text.literal("Создать комнату"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        boolean allowUnobtainable = RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
        allItems = ItemDifficulty.getAllItems(allowUnobtainable);
        filteredItems = new ArrayList<>(allItems);
        
        if (selectedItem == null && !filteredItems.isEmpty()) {
            selectedItem = filteredItems.get(new Random().nextInt(filteredItems.size()));
        }
        
        int centerX = width / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        // Search field (above grid)
        searchField = new TextFieldStyled(textRenderer, centerX - 100, gridY - 30, 200, 20, Text.translatable("randomrun.search"));
        searchField.setCenteredPlaceholder(Text.translatable("randomrun.search.placeholder"));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);
        
        // Random item button (below grid) - NO REVEAL SCREEN
        addDrawableChild(new ButtonDefault(
            centerX - 100, gridY + gridHeight + 10,
            200, 20,
            Text.translatable("randomrun.button.random_item"),
            button -> startSlotMachine(),
            0, 0.1f
        ));
        
        /* Кнопка "Далее" убрана по запросу пользователя. Выбор предмета из списка сразу открывает экран настройки.
        // Continue button
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 55,
            200, 20,
            Text.literal("Далее"),
            button -> openRevealScreen(),
            1, 0.12f
        ));
        */
        
        // Back button
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30,
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
    
    private void startSlotMachine() {
        // Предварительная генерация случайного предмета для результата
        Item resultItem = ItemDifficulty.getRandomItem(
            RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems()
        );
        
        if (resultItem == null) return;
        
        // Использовать внутреннюю анимацию
        this.slotMachineItems = new ArrayList<>(ItemDifficulty.getAllItems(
            RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems()
        ));
        Collections.shuffle(this.slotMachineItems);
        
        this.slotMachineResult = resultItem;
        this.slotMachineActive = true;
        this.slotMachineStartTime = System.currentTimeMillis();
        this.slotMachineIndex = 0;
        this.soundPlayed = false;
        this.lastTickTime = System.currentTimeMillis();
        this.particles.clear();
    }
    
    private void openRevealScreen() {
        if (selectedItem == null) return;
        
        MinecraftClient.getInstance().setScreen(new PrivateHostScreenItemReveal(this, selectedItem));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Calculate animation progress
        long elapsed = System.currentTimeMillis() - openTime;
        float animationProgress = Math.min(1f, elapsed / 400f);
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int slideOffset = (int) ((1f - easedProgress) * 30);
        
        super.render(context, mouseX, mouseY, delta);
        
        // Рендер сетки предметов или слот-машины
        if (slotMachineActive) {
            renderSlotMachine(context, delta);
            updateParticles(delta);
            
            // Авто-продолжение после завершения
            long elapsedSlot = System.currentTimeMillis() - slotMachineStartTime;
            if (elapsedSlot >= SLOT_MACHINE_DURATION + 1000 && slotMachineResult != null) {
                selectedItem = slotMachineResult;
                slotMachineActive = false;
                openRevealScreen();
            }
        } else {
            // Обновление позиции поля поиска с анимацией
            int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
            searchField.setY(gridY - 30 + slideOffset);
            
            renderItemGrid(context, mouseX, mouseY, slideOffset);
        }
        
        // Рендер индикатора прокрутки
        if (!slotMachineActive && filteredItems.size() > ITEMS_PER_ROW * VISIBLE_ROWS) {
            renderScrollIndicator(context, mouseX, mouseY, slideOffset);
        }
    }
    
    private void renderItemGrid(DrawContext context, int mouseX, int mouseY, int slideOffset) {
        int gridX = width / 2 - (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
        
        // Background for grid
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
            boolean hovered = mouseX >= x && mouseX < x + ITEM_SIZE &&
                             mouseY >= y && mouseY < y + ITEM_SIZE;
            boolean isSelected = item == selectedItem;
            
            // Draw slot background
            int bgColor = isSelected ? 0x8800FF00 : (hovered ? 0xFF6930c3 : 0xFF3a3a5c);
            context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);
            
            // Draw item
            context.drawItem(stack, x + 2, y + 2);
            
            if (hovered) {
                hoveredItem = item;
                hoveredX = mouseX;
                hoveredY = mouseY;
            }
        }
        
        // Render tooltip for hovered item
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
        
        // Пульсирующий фон
        float pulse = (float) Math.sin(elapsed / 200.0) * 0.1f + 0.9f;
        int bgAlpha = (int) (0xCC * pulse) << 24;
        context.fill(centerX - 70, centerY - 60, centerX + 70, centerY + 60, bgAlpha);
        
        // Анимированная граница
        int borderColor = 0xFF6930c3;
        if (progress >= 1.0f) {
            float glow = (float) Math.sin(elapsed / 150.0) * 0.3f + 0.7f;
            borderColor = interpolateColor(0xFF6930c3, 0xFFFFD700, glow);
        }
        context.drawBorder(centerX - 70, centerY - 60, 140, 120, borderColor);
        
        if (progress < 1.0f) {
            // Spinning animation
            long currentTime = System.currentTimeMillis();
            int tickInterval = (int) (50 + progress * 200);
            
            if (currentTime - lastTickTime >= tickInterval) {
                slotMachineIndex = (slotMachineIndex + 1) % slotMachineItems.size();
                lastTickTime = currentTime;
                
                // Spawn particles
                spawnParticles(centerX, centerY);
                
                if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                    float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                    float pitch = 0.8f + progress * 0.4f;
                    MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                            pitch,
                            volume * 0.15f
                        )
                    );
                }
            }
            
            Item displayItem = slotMachineItems.get(slotMachineIndex);
            ItemStack stack = new ItemStack(displayItem);
            
            // Вращающаяся иконка с эффектом размытия
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY, 0);
            
            // Rotation
            float rotation = (currentTime % 1000) / 1000.0f * 360f;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation * progress));
            
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(stack, -8, -8);
            matrices.pop();
            
            // Spinning text
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.slotmachine.spinning"), 
                centerX, centerY + 48, interpolateColor(0xFFFFFF, 0xFFD700, (float) Math.sin(elapsed / 100.0)));
        } else {
            // Результат с празднованием
            if (!soundPlayed && RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                MinecraftClient.getInstance().getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                        net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
                        1.2f,
                        volume * 0.7f
                    )
                );
                soundPlayed = true;
                
                // Спавн праздничных частиц
                Random random = new Random();
                for (int i = 0; i < 30; i++) {
                    spawnCelebrationParticle(centerX, centerY, random);
                }
            }
            
            // Анимация подпрыгивающей иконки
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
        // Grid click
        int gridX = width / 2 - (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2;
        
        // Calculate slide offset to match render
        long elapsed = System.currentTimeMillis() - openTime;
        float animationProgress = Math.min(1f, elapsed / 400f);
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int slideOffset = (int) ((1f - easedProgress) * 30);
        
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        if (!slotMachineActive && mouseX >= gridX && mouseX < gridX + gridWidth &&
            mouseY >= gridY && mouseY < gridY + gridHeight) {
            
            int col = (int) ((mouseX - gridX) / (ITEM_SIZE + GRID_PADDING));
            int row = (int) ((mouseY - gridY) / (ITEM_SIZE + GRID_PADDING));
            int index = (scrollOffset + row) * ITEMS_PER_ROW + col;
            
            if (index >= 0 && index < filteredItems.size()) {
                selectedItem = filteredItems.get(index);
                
                if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                    float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                    MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                            1.0f, volume
                        )
                    );
                }
                
                openRevealScreen();
                return true;
            }
        }
        
        // Scrollbar click
        int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        
        if (!slotMachineActive && maxScroll > 0) {
            int scrollBarX = width / 2 + (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2 + 10;
            int scrollBarY = gridY;
            int scrollBarHeight = gridHeight;
            
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                isDraggingScrollbar = true;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar) {
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
            int scrollBarHeight = gridHeight;
            int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            if (maxScroll > 0) {
                int scrollBarY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
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
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!slotMachineActive) {
            int totalRows = (int) Math.ceil(filteredItems.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // Helper class for particles
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
            vy += 0.1f; // Gravity
            life -= 0.02f;
        }
    }
}
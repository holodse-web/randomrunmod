/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.challenges.advancement.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.HebayterWidget;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.*;

public class AchievementSelectionScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private TextFieldWidget searchField;
    private List<AdvancementLoader.AdvancementInfo> filteredAdvancements = new ArrayList<>();
    private List<AdvancementLoader.AdvancementInfo> allAdvancements = new ArrayList<>();
    
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
    private AdvancementLoader.AdvancementInfo slotMachineResult = null;
    private boolean soundPlayed = false;
    private long lastTickTime = 0;
    private List<AdvancementLoader.AdvancementInfo> slotMachineItems = new ArrayList<>();
    private static final long SLOT_MACHINE_DURATION = 3000;
    
    // Particle effects for slot machine
    // Эффекты частиц для слот-машины
    private List<Particle> particles = new ArrayList<>();
    
    private long openTime;
    
    // Detail modal
    // Модальное окно с деталями
    private AdvancementLoader.AdvancementInfo selectedDetailAdvancement = null;
    
    // Credit widget
    // Виджет кредитов
    private HebayterWidget creditWidget;
    
    public AchievementSelectionScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.achievement_selection.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        // Load advancements
        // Загрузка достижений
        allAdvancements = AdvancementLoader.getAdvancements();
        filteredAdvancements = new ArrayList<>(allAdvancements);
        
        int centerX = width / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        // Search field
        // Поле поиска
        searchField = new TextFieldWidget(textRenderer, centerX - 100, gridY - 30, 200, 20, Text.translatable("randomrun.search"));
        searchField.setPlaceholder(Text.translatable("randomrun.search.placeholder_achievement"));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);
        
        // Credit widget
        // Виджет кредитов
        creditWidget = new HebayterWidget(10, height - 20, Text.translatable("randomrun.credit.idea").getString(), "https://www.tiktok.com/@hebayter?_r=1&_t=ZS-93TaWwDu10z");
        
        // Random achievement button
        // Кнопка случайного достижения
        addDrawableChild(new ButtonDefault(
            centerX - 100, gridY + gridHeight + 10,
            200, 20,
            Text.translatable("randomrun.button.random_achievement"),
            button -> startSlotMachine(),
            0, 0.1f
        ));
        
        // Back button
        // Кнопка назад
        addDrawableChild(new ButtonDefault(
            width / 2 - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            2, 0.15f
        ));
    }
    
    private void onSearchChanged(String text) {
        filteredAdvancements.clear();
        String searchLower = text.toLowerCase();
        
        for (AdvancementLoader.AdvancementInfo adv : allAdvancements) {
            String title = adv.title.getString().toLowerCase();
            String id = adv.id.toString().toLowerCase();
            
            if (title.contains(searchLower) || id.contains(searchLower)) {
                filteredAdvancements.add(adv);
            }
        }
        
        scrollOffset = 0;
    }
    
    private void startSlotMachine() {
        slotMachineActive = true;
        slotMachineStartTime = System.currentTimeMillis();
        lastTickTime = 0;
        soundPlayed = false;
        slotMachineIndex = 0;
        particles.clear();
        
        slotMachineItems.clear();
        Random random = new Random();
        
        if (allAdvancements.isEmpty()) {
            slotMachineActive = false;
            return;
        }



        // Classic slot machine
        // Классическая слот-машина
        for (int i = 0; i < 50; i++) {
            slotMachineItems.add(allAdvancements.get(random.nextInt(allAdvancements.size())));
        }
        slotMachineResult = allAdvancements.get(random.nextInt(allAdvancements.size()));
    }
    
    public void resetSlotMachine() {
        slotMachineActive = false;
        slotMachineResult = null;
        slotMachineIndex = 0;
        soundPlayed = false;
        lastTickTime = 0;
        slotMachineItems.clear();
        particles.clear();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Animation
        // Анимация
        long elapsed = System.currentTimeMillis() - openTime;
        float animationProgress = Math.min(1f, elapsed / 400f);
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int slideOffset = (int) ((1f - easedProgress) * 30);
        
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        
        if (slotMachineActive) {
            renderSlotMachine(context, delta);
            updateParticles(delta);
            
            // Auto-proceed when slot machine finishes
            // Авто-продолжение при завершении работы слот-машины
            long elapsedSlot = System.currentTimeMillis() - slotMachineStartTime;
            if (elapsedSlot >= SLOT_MACHINE_DURATION + 1000 && slotMachineResult != null) {
                MinecraftClient.getInstance().setScreen(new AchievementRevealScreen(this, slotMachineResult));
            }
        } else {
            int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
            searchField.setY(gridY - 30 + slideOffset);
            
            renderGrid(context, mouseX, mouseY, slideOffset);
        }
        
        if (!slotMachineActive && filteredAdvancements.size() > ITEMS_PER_ROW * VISIBLE_ROWS) {
            renderScrollIndicator(context, mouseX, mouseY, slideOffset);
        }
        
        if (selectedDetailAdvancement != null) {
            renderDetailModal(context, mouseX, mouseY);
        }
        
        // Render credit widget
        // Рендер виджета кредитов
        if (creditWidget != null) {
            creditWidget.render(context, mouseX, mouseY, delta);
        }
    }
    
    private void renderDetailModal(DrawContext context, int mouseX, int mouseY) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300); // Move above items
        // Сдвиг поверх элементов

        // Blur background (semi-transparent black overlay)
        // Размытие фона (полупрозрачное черное наложение)
        context.fill(0, 0, width, height, 0xDD000000);
        
        int modalWidth = 220;
        int modalHeight = 180; // Increased height
        int x = (width - modalWidth) / 2;
        int y = (height - modalHeight) / 2;
        
        // Modal background
        // Фон модального окна
        context.fill(x, y, x + modalWidth, y + modalHeight, 0xFF202020);
        context.drawBorder(x, y, modalWidth, modalHeight, 0xFF6930c3);
        
        // Icon
        // Иконка
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + modalWidth / 2, y + 35, 0);
        matrices.scale(4.0f, 4.0f, 1.0f);
        context.drawItem(selectedDetailAdvancement.icon, -8, -8);
        matrices.pop();
        
        // Title
        // Заголовок
        context.drawCenteredTextWithShadow(textRenderer, selectedDetailAdvancement.title, x + modalWidth / 2, y + 70, 0xFFFFD700);
        
        // Description
        // Описание
        int textY = y + 90;
        for (net.minecraft.text.OrderedText line : textRenderer.wrapLines(selectedDetailAdvancement.description, modalWidth - 20)) {
            context.drawCenteredTextWithShadow(textRenderer, line, x + modalWidth / 2, textY, 0xAAAAAA);
            textY += 12;
        }
        
        // Difficulty and Time (moved below description)
        // Сложность и Время (перемещено под описание)
        if (selectedDetailAdvancement.difficulty != null) {
            textY += 10; // Spacing
            String difficultyKey = "randomrun.difficulty." + selectedDetailAdvancement.difficulty.name().toLowerCase();
            Text difficultyText = Text.translatable(difficultyKey);
            Text difficultyLabel = Text.translatable("randomrun.difficulty", difficultyText);
            
            // Format time range
            // Форматирование временного диапазона
            String timeRange = selectedDetailAdvancement.difficulty.getTimeRangeString();
            Text timeText = Text.translatable("randomrun.time_range", timeRange);
            
            context.drawCenteredTextWithShadow(textRenderer, difficultyLabel, x + modalWidth / 2, textY, 0xFF55FF55);
            context.drawCenteredTextWithShadow(textRenderer, timeText, x + modalWidth / 2, textY + 12, 0xFF55FFFF);
        }
        
        // Close hint
        // Подсказка о закрытии
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.button.back"), x + modalWidth / 2, y + modalHeight - 15, 0x666666);
        
        context.getMatrices().pop();
    }
    
    private void renderGrid(DrawContext context, int mouseX, int mouseY, int slideOffset) {
        int gridX = width / 2 - (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2 + slideOffset;
        
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        context.fill(gridX - 5, gridY - 5, gridX + gridWidth + 5, gridY + gridHeight + 5, 0x80000000);
        
        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + ITEMS_PER_ROW * VISIBLE_ROWS, filteredAdvancements.size());
        
        AdvancementLoader.AdvancementInfo hoveredAdv = null;
        int hoveredX = 0, hoveredY = 0;
        
        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int row = localIndex / ITEMS_PER_ROW;
            int col = localIndex % ITEMS_PER_ROW;
            
            int x = gridX + col * (ITEM_SIZE + GRID_PADDING);
            int y = gridY + row * (ITEM_SIZE + GRID_PADDING);
            
            AdvancementLoader.AdvancementInfo adv = filteredAdvancements.get(i);
            
            boolean hovered = mouseX >= x && mouseX < x + ITEM_SIZE &&
                             mouseY >= y && mouseY < y + ITEM_SIZE;
            
            int bgColor = hovered ? 0xFF6930c3 : 0xFF3a3a5c;
            context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);
            
            context.drawItem(adv.icon, x + 2, y + 2);
            
            if (hovered && selectedDetailAdvancement == null) {
                hoveredAdv = adv;
                hoveredX = mouseX;
                hoveredY = mouseY;
            }
        }
        
        if (hoveredAdv != null) {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(hoveredAdv.title);
            tooltip.add(Text.translatable("randomrun.achievement.details_hint"));
            context.drawTooltip(textRenderer, tooltip, hoveredX, hoveredY);
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
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                            pitch,
                            volume * 0.15f
                        )
                    );
                }
            }
            
            AdvancementLoader.AdvancementInfo displayAdv = slotMachineItems.get(slotMachineIndex);
            
            // Rotating icon with motion blur effect
            // Вращающаяся иконка с эффектом размытия в движении
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY, 0);
            
            // Rotation
            // Вращение
            float rotation = (currentTime % 1000) / 1000.0f * 360f;
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rotation * progress));
            
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(displayAdv.icon, -8, -8);
            matrices.pop();
            
            // Spinning text
            // Вращающийся текст
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.achievement.spinning"), 
                centerX, centerY + 48, interpolateColor(0xFFFFFF, 0xFFD700, (float) Math.sin(elapsed / 100.0)));
        } else {
            // Result with celebration
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
            
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY - bounce, 0);
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(slotMachineResult.icon, -8, -8);
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
        int color = random.nextBoolean() ? 0xFFFFD700 : 0xFFFFFFFF;
        particles.add(new Particle(x, y,
            (float) Math.cos(angle) * speed,
            (float) Math.sin(angle) * speed,
            color));
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
        int totalRows = (int) Math.ceil(filteredAdvancements.size() / (float) ITEMS_PER_ROW);
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
        // Check credit widget first
        if (creditWidget != null && creditWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        if (selectedDetailAdvancement != null) {
            selectedDetailAdvancement = null;
            return true;
        }

        if (slotMachineActive) {
            long elapsed = System.currentTimeMillis() - slotMachineStartTime;
            if (elapsed >= SLOT_MACHINE_DURATION && slotMachineResult != null) {
                MinecraftClient.getInstance().setScreen(new AchievementRevealScreen(this, slotMachineResult));
                return true;
            }
            return true;
        }
        
        // Scrollbar logic
        // Логика полосы прокрутки
        if (filteredAdvancements.size() > ITEMS_PER_ROW * VISIBLE_ROWS) {
            int totalRows = (int) Math.ceil(filteredAdvancements.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            if (maxScroll > 0) {
                 int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
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
        
        // Grid click
        // Клик по сетке
        int gridX = width / 2 - (ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        if (mouseX >= gridX && mouseX < gridX + gridWidth &&
            mouseY >= gridY && mouseY < gridY + gridHeight) {
            
            int col = (int) ((mouseX - gridX) / (ITEM_SIZE + GRID_PADDING));
            int row = (int) ((mouseY - gridY) / (ITEM_SIZE + GRID_PADDING));
            int index = (scrollOffset + row) * ITEMS_PER_ROW + col;
            
            if (index >= 0 && index < filteredAdvancements.size()) {
                AdvancementLoader.AdvancementInfo selected = filteredAdvancements.get(index);
                
                if (button == 2) { // Middle click - Средняя кнопка мыши
                    selectedDetailAdvancement = selected;
                    return true;
                }
                
                if (button == 0) { // Left click - Левая кнопка мыши
                    MinecraftClient.getInstance().setScreen(new AchievementRevealScreen(this, selected));
                    return true;
                }
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
            int totalRows = (int) Math.ceil(filteredAdvancements.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            if (maxScroll > 0) {
                int gridY = height / 2 - (VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING)) / 2;
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
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!slotMachineActive) {
            int totalRows = (int) Math.ceil(filteredAdvancements.size() / (float) ITEMS_PER_ROW);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

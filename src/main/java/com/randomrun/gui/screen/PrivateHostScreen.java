package com.randomrun.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.data.ItemDifficulty;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PrivateHostScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private List<Item> filteredItems = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 4;
    private static final int ITEM_SIZE = 20;
    private static final int GRID_PADDING = 2;
    
    private Item selectedItem = null;
    
    // 3D item animation
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    // Mouse drag rotation
    private boolean dragging = false;
    private float dragRotationX = 0f;
    private float dragRotationY = 0f;
    private double lastMouseX, lastMouseY;
    
    // Scrollbar
    private boolean scrollbarDragging = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartOffset = 0;
    
    public PrivateHostScreen(Screen parent) {
        super(Text.literal("Создать комнату"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        boolean allowUnobtainable = RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
        filteredItems = ItemDifficulty.getAllItems(allowUnobtainable);
        
        if (selectedItem == null && !filteredItems.isEmpty()) {
            selectedItem = filteredItems.get(new Random().nextInt(filteredItems.size()));
        }
        
        int centerX = width / 2;
        
        // Кнопка создать комнату - выше кнопки назад
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 70,
            200, 20,
            Text.literal("§a" + Text.translatable("randomrun.battle.create_room").getString()),
            button -> createRoom(),
            0, 0.1f
        ));
        
        // Кнопка назад
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f
        ));
    }
    
    private void createRoom() {
        if (selectedItem == null) return;
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
        BattleManager.getInstance().createPrivateRoom(playerName, selectedItem).thenAccept(roomCode -> {
            if (roomCode != null) {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().setScreen(new BattleWaitingScreen(parent, roomCode, true));
                });
            } else {
                MinecraftClient.getInstance().execute(() -> {
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(
                            Text.literal("§cОшибка создания комнаты"), false
                        );
                    }
                });
            }
        });
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Update animations
        long elapsed = System.currentTimeMillis() - openTime;
        if (!dragging) {
            rotationY += delta * 2f;
            levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
        }
        
        int centerX = width / 2;
        int gridY = height / 2 + 20; // Опустили сетку вниз
        
        RenderSystem.enableBlend();
        
        // Рендерим 3D предмет сверху
        if (selectedItem != null) {
            render3DItem(context, centerX, 80, delta);
            
            // Название предмета под 3D моделью (светло-фиолетовый)
            String itemName = selectedItem.getName().getString();
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§d" + itemName),
                centerX, 150, 0xD946FF);
        }
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§7Выберите предмет:"),
            centerX, gridY - 20, 0xAAAAAA);
        
        renderItemGrid(context, mouseX, mouseY, centerX, gridY);
        renderScrollbar(context, mouseX, mouseY, centerX, gridY);
        
        RenderSystem.disableBlend();
    }
    
    private void render3DItem(DrawContext context, int x, int y, float delta) {
        ItemStack stack = new ItemStack(selectedItem);
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y + levitationOffset, 100);
        context.getMatrices().scale(64f, -64f, 64f);
        
        // Применяем вращение
        context.getMatrices().multiply(new Quaternionf().rotateX((float) Math.toRadians(dragRotationX)));
        context.getMatrices().multiply(new Quaternionf().rotateY((float) Math.toRadians(rotationY + dragRotationY)));
        
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.GUI,
            false,
            context.getMatrices(),
            context.getVertexConsumers(),
            15728880,
            OverlayTexture.DEFAULT_UV,
            model
        );
        
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
    
    private void renderItemGrid(DrawContext context, int mouseX, int mouseY, int centerX, int gridY) {
        gridY = height / 2 + 20; // Обновленная позиция
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int startX = centerX - gridWidth / 2;
        
        int maxRows = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_ROW);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                int index = (row + scrollOffset) * ITEMS_PER_ROW + col;
                if (index >= filteredItems.size()) break;
                
                Item item = filteredItems.get(index);
                int x = startX + col * (ITEM_SIZE + GRID_PADDING);
                int y = gridY + row * (ITEM_SIZE + GRID_PADDING);
                
                boolean isHovered = mouseX >= x && mouseX < x + ITEM_SIZE && 
                                   mouseY >= y && mouseY < y + ITEM_SIZE;
                boolean isSelected = item == selectedItem;
                
                int bgColor = isSelected ? 0x8800FF00 : (isHovered ? 0x88FFFFFF : 0x88000000);
                context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);
                
                context.drawItem(new ItemStack(item), x + 2, y + 2);
                
                if (isHovered) {
                    context.drawItemTooltip(textRenderer, new ItemStack(item), mouseX, mouseY);
                }
            }
        }
    }
    
    private void renderScrollbar(DrawContext context, int mouseX, int mouseY, int centerX, int gridY) {
        gridY = height / 2 + 20;
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        
        int maxRows = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_ROW);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);
        
        if (maxScroll <= 0) return;
        
        int scrollbarX = centerX + gridWidth / 2 + 10;
        int scrollbarWidth = 6;
        
        // Background
        context.fill(scrollbarX, gridY, scrollbarX + scrollbarWidth, gridY + gridHeight, 0x80000000);
        
        // Thumb
        float thumbRatio = (float) VISIBLE_ROWS / maxRows;
        int thumbHeight = (int) (gridHeight * thumbRatio);
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = gridY + (int) ((gridHeight - thumbHeight) * scrollRatio);
        
        // Check if hovering over thumb
        boolean hoveringThumb = mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
                               mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        
        int thumbColor = (hoveringThumb || scrollbarDragging) ? 0xFF8B5CF6 : 0xFF6930c3;
        context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int gridY = height / 2 + 20;
        int gridWidth = ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING);
        int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
        int startX = centerX - gridWidth / 2;
        
        // Проверяем клик по скроллбару
        int scrollbarX = centerX + gridWidth / 2 + 5;
        int scrollbarWidth = 6;
        int maxRows = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_ROW);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);
        
        if (maxScroll > 0 && mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
            mouseY >= gridY && mouseY <= gridY + gridHeight) {
            scrollbarDragging = true;
            scrollbarDragStartY = (int) mouseY;
            scrollbarDragStartOffset = scrollOffset;
            return true;
        }
        
        // Проверяем клик по 3D предмету для вращения
        int itemAreaSize = 100;
        if (mouseX >= centerX - itemAreaSize && mouseX <= centerX + itemAreaSize &&
            mouseY >= 30 && mouseY <= 130) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                int index = (row + scrollOffset) * ITEMS_PER_ROW + col;
                if (index >= filteredItems.size()) break;
                
                int x = startX + col * (ITEM_SIZE + GRID_PADDING);
                int y = gridY + row * (ITEM_SIZE + GRID_PADDING);
                
                if (mouseX >= x && mouseX < x + ITEM_SIZE && 
                    mouseY >= y && mouseY < y + ITEM_SIZE) {
                    selectedItem = filteredItems.get(index);
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        scrollbarDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollbarDragging) {
            int gridHeight = VISIBLE_ROWS * (ITEM_SIZE + GRID_PADDING);
            int maxRows = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_ROW);
            int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);
            
            float visibleRatio = (float) VISIBLE_ROWS / maxRows;
            int thumbHeight = Math.max(20, (int) (gridHeight * visibleRatio));
            int scrollableHeight = gridHeight - thumbHeight;
            
            int deltaMouseY = (int) mouseY - scrollbarDragStartY;
            float scrollDelta = (float) deltaMouseY / scrollableHeight * maxScroll;
            
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollbarDragStartOffset + (int) scrollDelta));
            return true;
        }
        
        if (dragging) {
            dragRotationY += (float) (mouseX - lastMouseX) * 0.5f;
            dragRotationX += (float) (mouseY - lastMouseY) * 0.5f;
            dragRotationX = Math.max(-45, Math.min(45, dragRotationX));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) verticalAmount;
        return true;
    }
}

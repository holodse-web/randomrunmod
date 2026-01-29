package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import com.randomrun.ui.widget.StyledButton2;
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

public class PrivateHostScreenItemReveal extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Item targetItem;
    private final ItemDifficulty.Difficulty difficulty;
    
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private float targetLevitationOffset = 0f;
    private long openTime;
    private long lastDragTime = 0;
    private float rotationSpeed = 0f;
    private float levitationSpeed = 0f;
    private static final long RESUME_DELAY = 500; 
    private static final float RESUME_ACCELERATION = 0.02f;
    
    private boolean dragging = false;
    private float dragRotationX = 0f;
    private float dragRotationY = 0f;
    private double lastMouseX, lastMouseY;
    private float frozenLevitationOffset = 0f;
    
    public PrivateHostScreenItemReveal(Screen parent, Item item) {
        super(Text.literal("Создать комнату"));
        this.parent = parent;
        this.targetItem = item;
        this.difficulty = ItemDifficulty.getDifficulty(item);
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        int buttonY = height - 55;
        
        // Create Room button
        addDrawableChild(new StyledButton2(
            centerX - 100, buttonY,
            200, 20,
            Text.translatable("randomrun.battle.create_room"),
            button -> createRoom(),
            1, 0.12f
        ));
        
        // Back button
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30, 
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
    }
    
    private void createRoom() {
        if (targetItem == null) return;
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
        // We need to pass the parent of PrivateHostScreen (which is the screen before it)
        // or just pass PrivateHostScreen as parent if we want to go back there on failure?
        // Usually we want to go to BattleWaitingScreen.
        
        BattleManager.getInstance().createPrivateRoom(playerName, targetItem).thenAccept(roomCode -> {
            if (roomCode != null) {
                MinecraftClient.getInstance().execute(() -> {
                    // Navigate to waiting screen
                    // We pass 'parent' as the parent of WaitingScreen so if they leave waiting, they go back to...
                    // Actually, usually we want to go back to PrivateHostScreen or MainMenu.
                    // Let's assume 'parent' passed to this screen is PrivateHostScreen.
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
        long elapsed = System.currentTimeMillis() - openTime;
        long timeSinceLastDrag = System.currentTimeMillis() - lastDragTime;
        
        if (dragging) {
            rotationSpeed = 0f;
            levitationSpeed = 0f;
            frozenLevitationOffset = levitationOffset;
        } else if (timeSinceLastDrag > RESUME_DELAY) {
            rotationSpeed = Math.min(rotationSpeed + RESUME_ACCELERATION * delta, 2f);
            levitationSpeed = Math.min(levitationSpeed + RESUME_ACCELERATION * delta, 1f);
            
            rotationY += delta * rotationSpeed;
            targetLevitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
            levitationOffset += (targetLevitationOffset - levitationOffset) * levitationSpeed * delta * 0.1f;
        } else {
            levitationOffset = frozenLevitationOffset;
        }
        
        renderGradientBackground(context);
        
        // Render 3D item (same position/size as ItemRevealScreen)
        render3DItem(context, width / 2, height / 2 - 40);
        
        String itemName = targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, itemName, width / 2, height / 2 + 40, 0xFFFFFF);
        
        // Show difficulty info if applicable (optional, but good for consistency)
        /*
        String difficultyText = difficulty.displayName;
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.difficulty", difficultyText), 
            width / 2, height / 2 + 65, difficulty.color);
        */
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.drag_to_rotate"), 
            width / 2, height / 2 + 50, 0x666666); 
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void render3DItem(DrawContext context, int x, int y) {
        ItemStack stack = new ItemStack(targetItem);
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y + levitationOffset, 100);
        context.getMatrices().scale(96f, -96f, 96f);
        
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
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int itemAreaX = width / 2 - 50;
            int itemAreaY = height / 2 - 90;
            if (mouseX >= itemAreaX && mouseX <= itemAreaX + 100 &&
                mouseY >= itemAreaY && mouseY <= itemAreaY + 100) {
                dragging = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            lastDragTime = System.currentTimeMillis();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            dragRotationY += (float) (mouseX - lastMouseX) * 0.5f;
            dragRotationX += (float) (mouseY - lastMouseY) * 0.5f;
            dragRotationX = Math.max(-90, Math.min(90, dragRotationX));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.data.ItemDifficulty;
import com.randomrun.gui.widget.StyledButton2;
import com.randomrun.world.WorldCreator;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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

public class ItemRevealScreen extends AbstractRandomRunScreen {
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
    
    public ItemRevealScreen(Screen parent, Item item) {
        super(Text.translatable("randomrun.screen.item_reveal.title"));
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
        
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        boolean manualTimeEnabled = RandomRunMod.getInstance().getConfig().isManualTimeEnabled();
        boolean askForSeed = RandomRunMod.getInstance().getConfig().isAskForCustomSeed();
        
        if (timeChallengeEnabled) {
            if (manualTimeEnabled) {
                
                addDrawableChild(new StyledButton2(
                    centerX - 100, buttonY,
                    200, 20,
                    Text.translatable("randomrun.button.start_speedrun"),
                    button -> {
                        int manualTimeSeconds = RandomRunMod.getInstance().getConfig().getManualTimeSeconds();
                        if (askForSeed) {
                            MinecraftClient.getInstance().setScreen(new SeedInputScreen(this, targetItem, manualTimeSeconds * 1000L));
                        } else {
                            startSpeedrun(manualTimeSeconds * 1000L);
                        }
                    }
                ));
            } else {
                
                addDrawableChild(new StyledButton2(
                    centerX - 100, buttonY,
                    200, 20,
                    Text.translatable("randomrun.button.select_time"),
                    button -> MinecraftClient.getInstance().setScreen(new TimeSelectionScreen(this, targetItem))
                ));
            }
        } else {
            
            addDrawableChild(new StyledButton2(
                centerX - 100, buttonY,
                200, 20,
                Text.translatable("randomrun.button.start_speedrun"),
                button -> {
                    if (askForSeed) {
                        MinecraftClient.getInstance().setScreen(new SeedInputScreen(this, targetItem, 0));
                    } else {
                        startSpeedrun(0);
                    }
                }
            ));
        }
        
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30, 
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
    }
    
    public void startSpeedrun(long timeLimitMs) {
        startSpeedrunWithSeed(timeLimitMs, null);
    }
    
    public void startSpeedrunWithSeed(long timeLimitMs, String seed) {
        
        RandomRunMod.getInstance().getRunDataManager().startNewRun(targetItem, timeLimitMs);
        
        
        if (seed != null && !seed.isEmpty()) {
            WorldCreator.createSpeedrunWorld(targetItem, timeLimitMs, seed);
        } else {
            WorldCreator.createSpeedrunWorld(targetItem, timeLimitMs, null);
        }
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
        
        
        render3DItem(context, width / 2, height / 2 - 40);
        
       
        String itemName = targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, itemName, width / 2, height / 2 + 40, 0xFFFFFF);
        
       
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        boolean useDifficulty = RandomRunMod.getInstance().getConfig().isUseItemDifficulty();
        
        if (timeChallengeEnabled && useDifficulty) {
            
            String difficultyText = difficulty.displayName;
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.difficulty", difficultyText), 
                width / 2, height / 2 + 65, difficulty.color);
            
           
            String timeRange = difficulty.getTimeRange();
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.time_range", timeRange), 
                width / 2, height / 2 + 80, 0xAAAAAA);
        }
        
       
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.drag_to_rotate"), 
            width / 2, height / 2 + 50, 0x666666); 
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void render3DItem(DrawContext context, int x, int y) {
        ItemStack stack = new ItemStack(targetItem);
        
        
        long elapsed = System.currentTimeMillis() - openTime;
        
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
            frozenLevitationOffset = levitationOffset;
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
        
        if (parent instanceof SpeedrunScreen) {
            SpeedrunScreen speedrunScreen = (SpeedrunScreen) parent;
            speedrunScreen.resetSlotMachine();
            MinecraftClient.getInstance().setScreen(speedrunScreen);
        } else {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

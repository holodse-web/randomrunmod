package com.randomrun.challenges.advancement.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.challenges.time.screen.TimeSelectionScreen;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.widget.CreditWidget;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

public class AchievementRevealScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    
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
    private CreditWidget creditWidget;
    
    public AchievementRevealScreen(Screen parent, AdvancementLoader.AdvancementInfo advancement) {
        super(Text.translatable("randomrun.screen.achievement_reveal.title"));
        this.parent = parent;
        this.targetAdvancement = advancement;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        int buttonY = height - 55;
        
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        
        if (timeChallengeEnabled) {
            // Time Challenge button
            addDrawableChild(new StyledButton2(
                centerX - 100, buttonY,
                200, 20,
                Text.translatable("randomrun.button.select_time"),
                button -> {
                    MinecraftClient.getInstance().setScreen(new TimeSelectionScreen(this, targetAdvancement));
                }
            ));
        } else {
            // Use Start Speedrun button
            addDrawableChild(new StyledButton2(
                centerX - 100, buttonY,
                200, 20,
                Text.translatable("randomrun.button.start_speedrun"),
                button -> {
                    startSpeedrun(0);
                }
            ));
        }
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30, 
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
        
        creditWidget = new CreditWidget(10, height - 20, Text.translatable("randomrun.credit.idea").getString(), "https://www.tiktok.com/@hebayter?_r=1&_t=ZS-93TaWwDu10z");
    }
    
    public void startSpeedrun(long timeLimitMs) {
        startSpeedrunWithSeed(timeLimitMs, null);
    }
    
    public void startSpeedrunWithSeed(long timeLimitMs, String seed) {
        var runManager = RandomRunMod.getInstance().getRunDataManager();
        runManager.startNewRun(targetAdvancement.id, timeLimitMs);
        
        // Save advancement display info for HUD (before advancement is obtained)
        String displayName = targetAdvancement.title.getString();
        net.minecraft.item.Item iconItem = targetAdvancement.icon.getItem();
        runManager.setAdvancementDisplayInfo(displayName, iconItem);
        
        WorldCreator.createSpeedrunWorld(targetAdvancement.id, timeLimitMs, seed);
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
        
        context.drawCenteredTextWithShadow(textRenderer, targetAdvancement.title, width / 2, height / 2 + 40, 0xFFFFFF);
        // Draw description
        int maxWidth = Math.min(width - 40, 300);
        java.util.List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(targetAdvancement.description, maxWidth);
        int yOffset = height / 2 + 55;
        
        for (net.minecraft.text.OrderedText line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, yOffset, 0xAAAAAA);
            yOffset += textRenderer.fontHeight + 2;
        }
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.drag_to_rotate"), 
            width / 2, yOffset + 15, 0x666666); 
        
        super.render(context, mouseX, mouseY, delta);
        
        if (creditWidget != null) {
            creditWidget.render(context, mouseX, mouseY, delta);
        }
    }
    
    private void render3DItem(DrawContext context, int x, int y) {
        ItemStack stack = targetAdvancement.icon;
        
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
        if (creditWidget != null && creditWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
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
        if (parent instanceof AchievementSelectionScreen) {
            AchievementSelectionScreen screen = (AchievementSelectionScreen) parent;
            screen.resetSlotMachine();
            MinecraftClient.getInstance().setScreen(screen);
        } else {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

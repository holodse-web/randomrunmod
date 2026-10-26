package com.randomrun.ui.widget;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.util.PreviewRenderGeometry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RevealAnimationWidget implements Renderable, GuiEventListener, NarratableEntry {
    private final ItemStack stack;
    private final int x;
    private final int y;
    
    private final PhysicsState physics;
    private final DragState drag;
    
    private static final int HITBOX_SIZE = 100;
    private static final float ITEM_SCALE = 48f;
    private static final float MOUSE_SENSITIVITY = 0.5f;
    private static final float LEVITATION_AMPLITUDE = 5f;
    private static final float LEVITATION_PERIOD = 500f;
    private static final float LERP_SPEED = 0.1f;
    private static final float MAX_ROTATION_SPEED = 2f;
    private static final float MAX_LEVITATION_SPEED = 1f;
    private static final float MAX_DRAG_ROTATION_X = 90f;
    private static final long RESUME_DELAY = 500;
    private static final float RESUME_ACCELERATION = 0.4f;
    private static final float DRAG_DECAY = 0.95f;
    private long lastFrameNanos = System.nanoTime();

    public RevealAnimationWidget(int x, int y, ItemStack stack) {
        this.x = x;
        this.y = y;
        this.stack = stack.copy();
        this.physics = new PhysicsState();
        this.drag = new DragState();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = PreviewRenderGeometry.frameSeconds(now - lastFrameNanos);
        lastFrameNanos = now;
        if (RandomRunMod.getInstance().getConfig().isAnimationsEnabled() || drag.active) updatePhysics(seconds, now);
        render3DItem(context);
    }

    private void updatePhysics(float seconds, long now) {
        long timeSinceLastDrag = (now - drag.lastDragNanos) / 1_000_000L;
        
        if (drag.active) {
            physics.rotationSpeed = 0f;
            physics.levitationSpeed = 0f;
            physics.frozenLevitationOffset = physics.levitationOffset;
        } else {
            applyDragDecay(seconds * 60);
            
            if (timeSinceLastDrag > RESUME_DELAY) {
                updateRotation(seconds);
                updateLevitation(now, seconds);
            } else {
                physics.levitationOffset = physics.frozenLevitationOffset;
            }
        }
    }

    private void applyDragDecay(float deltaTime) {
        if (drag.velocityX != 0f || drag.velocityY != 0f) {
            float decay = (float) Math.pow(DRAG_DECAY, deltaTime);
            float displacement = (1 - decay) / (1 - DRAG_DECAY);
            drag.rotationX += drag.velocityX * displacement;
            drag.rotationY += drag.velocityY * displacement;
            drag.velocityX *= decay;
            drag.velocityY *= decay;
            
            if (Math.abs(drag.velocityX) < 0.01f) drag.velocityX = 0f;
            if (Math.abs(drag.velocityY) < 0.01f) drag.velocityY = 0f;
            
            drag.rotationX = clamp(drag.rotationX, -MAX_DRAG_ROTATION_X, MAX_DRAG_ROTATION_X);
            drag.rotationY = normalizeAngle(drag.rotationY);
        }
    }

    private void updateRotation(float seconds) {
        physics.rotationSpeed = Math.min(
            physics.rotationSpeed + RESUME_ACCELERATION * seconds,
            MAX_ROTATION_SPEED
        );
        physics.rotationY = normalizeAngle(physics.rotationY + physics.rotationSpeed * 20 * seconds);
    }

    private void updateLevitation(long now, float seconds) {
        physics.levitationSpeed = Math.min(
            physics.levitationSpeed + RESUME_ACCELERATION * seconds,
            MAX_LEVITATION_SPEED
        );
        
        double elapsed = (now - physics.openTimeNanos) / 1_000_000.0;
        physics.targetLevitationOffset = (float) Math.sin(elapsed / LEVITATION_PERIOD) * LEVITATION_AMPLITUDE;
        physics.levitationOffset += (physics.targetLevitationOffset - physics.levitationOffset) 
            * (float) (1 - Math.exp(-physics.levitationSpeed * LERP_SPEED * 20 * seconds));
    }

    private void render3DItem(GuiGraphicsExtractor context) {
        com.randomrun.ui.util.ItemPreviewRenderer.extract(context, stack, x, y + physics.levitationOffset,
            ITEM_SCALE, drag.rotationX, physics.rotationY + drag.rotationY, ItemDisplayContext.GUI);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && isMouseInHitbox(mouseX, mouseY)) {
            drag.active = true;
            drag.lastMouseX = mouseX;
            drag.lastMouseY = mouseY;
            drag.velocityX = 0f;
            drag.velocityY = 0f;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && drag.active) {
            drag.active = false;
            drag.lastDragNanos = System.nanoTime();
            physics.frozenLevitationOffset = physics.levitationOffset;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (drag.active) {
            float dx = (float) (mouseX - drag.lastMouseX) * MOUSE_SENSITIVITY;
            float dy = (float) (mouseY - drag.lastMouseY) * MOUSE_SENSITIVITY;
            
            drag.rotationY += dx;
            drag.rotationX += dy;
            
            drag.rotationX = clamp(drag.rotationX, -MAX_DRAG_ROTATION_X, MAX_DRAG_ROTATION_X);
            drag.rotationY = normalizeAngle(drag.rotationY);
            
            drag.velocityY = dx;
            drag.velocityX = dy;
            
            drag.lastMouseX = mouseX;
            drag.lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    private boolean isMouseInHitbox(double mouseX, double mouseY) {
        int itemAreaX = x - HITBOX_SIZE / 2;
        int itemAreaY = y - HITBOX_SIZE / 2;
        return mouseX >= itemAreaX && mouseX <= itemAreaX + HITBOX_SIZE &&
               mouseY >= itemAreaY && mouseY <= itemAreaY + HITBOX_SIZE;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float normalizeAngle(float angle) {
        return angle % 360f;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {}

    private static class PhysicsState {
        float rotationY = 0f;
        float levitationOffset = 0f;
        float targetLevitationOffset = 0f;
        float rotationSpeed = 0f;
        float levitationSpeed = 0f;
        float frozenLevitationOffset = 0f;
        final long openTimeNanos = System.nanoTime();
    }

    private static class DragState {
        boolean active = false;
        float rotationX = 0f;
        float rotationY = 0f;
        float velocityX = 0f;
        float velocityY = 0f;
        double lastMouseX = 0;
        double lastMouseY = 0;
        long lastDragNanos = 0;
    }
}

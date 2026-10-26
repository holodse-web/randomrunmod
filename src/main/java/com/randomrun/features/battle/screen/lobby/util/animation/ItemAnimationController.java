package com.randomrun.features.battle.screen.lobby.util.animation;

public class ItemAnimationController {
    private static final long RESUME_DELAY = 500;
    private static final float RESUME_ACCELERATION = 0.02f;
    private static final float MAX_ROTATION_SPEED = 2f;
    private static final float MAX_LEVITATION_SPEED = 1f;
    
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private float targetLevitationOffset = 0f;
    private long lastDragTime = 0;
    private float rotationSpeed = 0f;
    private float levitationSpeed = 0f;
    
    private boolean dragging = false;
    private float dragRotationX = 0f;
    private float dragRotationY = 0f;
    private double lastMouseX, lastMouseY;
    private float frozenLevitationOffset = 0f;
    
    public void update(long elapsed, float delta) {
        long timeSinceLastDrag = System.currentTimeMillis() - lastDragTime;
        
        if (dragging) {
            rotationSpeed = 0f;
            levitationSpeed = 0f;
            frozenLevitationOffset = levitationOffset;
        } else if (timeSinceLastDrag > RESUME_DELAY) {
            rotationSpeed = Math.min(rotationSpeed + RESUME_ACCELERATION * delta, MAX_ROTATION_SPEED);
            levitationSpeed = Math.min(levitationSpeed + RESUME_ACCELERATION * delta, MAX_LEVITATION_SPEED);
            
            rotationY += delta * rotationSpeed;
            targetLevitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
            levitationOffset += (targetLevitationOffset - levitationOffset) * levitationSpeed * delta * 0.1f;
        } else {
            levitationOffset = frozenLevitationOffset;
        }
    }
    
    public void startDrag(double mouseX, double mouseY) {
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }
    
    public void updateDrag(double mouseX, double mouseY) {
        if (dragging) {
            dragRotationY -= (float) (mouseX - lastMouseX) * 0.5f;
            dragRotationX -= (float) (mouseY - lastMouseY) * 0.5f;
            dragRotationX = Math.max(-90, Math.min(90, dragRotationX));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }
    
    public void stopDrag() {
        if (dragging) {
            dragging = false;
            lastDragTime = System.currentTimeMillis();
            frozenLevitationOffset = levitationOffset;
        }
    }
    
    public boolean isDragging() {
        return dragging;
    }
    
    public float getRotationY() {
        return rotationY;
    }
    
    public float getDragRotationX() {
        return dragRotationX;
    }
    
    public float getDragRotationY() {
        return dragRotationY;
    }
    
    public float getLevitationOffset() {
        return levitationOffset;
    }
}

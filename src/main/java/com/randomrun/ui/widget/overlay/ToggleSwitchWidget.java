package com.randomrun.ui.widget.overlay;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.widget.GlobalParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class ToggleSwitchWidget extends AbstractWidget {
    private static final int SWITCH_WIDTH = 40;
    private static final int SWITCH_HEIGHT = 20;
    private static final int SLIDE_DISTANCE = 50;
    private static final int KNOB_PADDING = 2;
    private static final int LABEL_OFFSET = 2;
    
    private static final float APPEARANCE_DURATION = 400f;
    private static final float TOGGLE_SPEED = 0.15f;
    private static final float HOVER_SPEED = 0.2f;
    private static final float LABEL_SCALE = 0.75f;
    
    private static final int COLOR_BG_OFF = 0xFF802020;
    private static final int COLOR_BG_ON = 0xFF208020;
    private static final int COLOR_BORDER = 0xFFAAAAAA;
    private static final int COLOR_KNOB = 0xFFFFFFFF;
    private static final int COLOR_LABEL_ON = 0x55FF55;
    private static final int COLOR_LABEL_OFF = 0xFF5555;
    private static final int COLOR_PARTICLE_ON = 0x55FF55;
    private static final int COLOR_PARTICLE_OFF = 0xFF5555;
    
    private static final float SOUND_PITCH = 1.8f;
    private static final float SOUND_VOLUME_MULTIPLIER = 0.25f;
    private static final int PARTICLE_COUNT = 20;
    
    private boolean enabled;
    private final Runnable onPress;
    private final long creationTime;
    private final float delay;
    
    private float animProgress = 0f;
    private float hoverProgress = 0f;
    private boolean wasHovered = false;
    
    public ToggleSwitchWidget(int x, int y, boolean initialState, Runnable onPress, float delay) {
        super(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, Component.empty());
        this.enabled = initialState;
        this.onPress = onPress;
        this.animProgress = enabled ? 1.0f : 0.0f;
        this.creationTime = System.currentTimeMillis();
        this.delay = delay;
    }
    
    public ToggleSwitchWidget(int x, int y, boolean initialState, Runnable onPress) {
        this(x, y, initialState, onPress, 0f);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = calculateAppearanceProgress();
        if (alpha <= 0) return;
        
        int animatedX = getX() - (int) ((1f - alpha) * SLIDE_DISTANCE);
        
        updateAnimations(delta);
        
        if (hoverProgress > 0.01f) {
            renderLabel(context, animatedX, alpha);
        }
        
        renderSwitch(context, animatedX, alpha);
    }
    
    private float calculateAppearanceProgress() {
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) return 0f;
        
        float animElapsed = elapsed - delayMs;
        float progress = Math.min(1f, animElapsed / APPEARANCE_DURATION);
        return 1f - (float) Math.pow(1 - progress, 3);
    }
    
    private void updateAnimations(float delta) {
        float target = enabled ? 1.0f : 0.0f;
        animProgress += (target - animProgress) * TOGGLE_SPEED * delta * 5f;
        if (Math.abs(target - animProgress) < 0.001f) animProgress = target;
        
        boolean hovered = isHovered();
        float targetHover = hovered ? 1.0f : 0.0f;
        hoverProgress += (targetHover - hoverProgress) * HOVER_SPEED * delta * 5f;
        if (Math.abs(targetHover - hoverProgress) < 0.001f) hoverProgress = targetHover;
        
        if (hovered && !wasHovered && calculateAppearanceProgress() >= 1f) {
            playHoverSound();
        }
        wasHovered = hovered;
    }
    
    private void playHoverSound() {
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BELL.value(), 
                SOUND_PITCH, 
                volume * SOUND_VOLUME_MULTIPLIER
            )
        );
    }
    
    private void renderLabel(GuiGraphicsExtractor context, int x, float alpha) {
        Minecraft client = Minecraft.getInstance();
        Component labelText = enabled 
            ? com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.online") 
            : com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.offline");
        
        int labelColor = enabled ? COLOR_LABEL_ON : COLOR_LABEL_OFF;
        int labelAlpha = (int)(hoverProgress * 255 * alpha);
        int finalColor = (labelAlpha << 24) | (labelColor & 0x00FFFFFF);
        
        int textWidth = client.font.width(labelText);
        float startY = getY() + height / 2.0f - (client.font.lineHeight * LABEL_SCALE) / 2.0f;
        float endY = getY() + height + LABEL_OFFSET;
        float currentY = startY + (endY - startY) * hoverProgress;
        
        context.pose().pushMatrix();
        context.pose().translate(x + width / 2.0f, currentY);
        context.pose().scale(LABEL_SCALE, LABEL_SCALE);
        com.randomrun.ui.util.GuiText.text(context, client.font, labelText, -textWidth / 2, 0, finalColor);
        context.pose().popMatrix();
    }
    
    private void renderSwitch(GuiGraphicsExtractor context, int x, float alpha) {
        context.pose().pushMatrix();
        context.pose().translate(0, 0);
        
        int borderAlpha = (int)(alpha * 255);
        int finalBorderColor = (borderAlpha << 24) | (COLOR_BORDER & 0x00FFFFFF);
        context.fill(x, getY(), x + width, getY() + height, finalBorderColor);
        
        int bgColor = lerpColor(COLOR_BG_OFF, COLOR_BG_ON, animProgress);
        int bgAlpha = (int)(alpha * 255);
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        context.fill(x + 1, getY() + 1, x + width - 1, getY() + height - 1, finalBgColor);
        
        int knobSize = height - (KNOB_PADDING * 2);
        int knobXStart = x + KNOB_PADDING;
        int knobXEnd = x + width - KNOB_PADDING - knobSize;
        int knobX = (int) (knobXStart + (knobXEnd - knobXStart) * animProgress);
        int knobY = getY() + KNOB_PADDING;
        
        int knobAlpha = (int)(alpha * 255);
        int finalKnobColor = (knobAlpha << 24) | (COLOR_KNOB & 0x00FFFFFF);
        context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, finalKnobColor);
        
        context.pose().popMatrix();
    }
    
    private int lerpColor(int c1, int c2, float p) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * p);
        int g = (int) (g1 + (g2 - g1) * p);
        int b = (int) (b1 + (b2 - b1) * p);
        
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        this.enabled = !this.enabled;
        
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f)
        );
        
        int particleColor = enabled ? COLOR_PARTICLE_ON : COLOR_PARTICLE_OFF;
        GlobalParticleSystem.getInstance().addFastExplosion(
            getX() + width / 2, 
            getY() + height / 2, 
            PARTICLE_COUNT, 
            particleColor
        );
        
        if (onPress != null) {
            onPress.run();
        }
    }
    
    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }
}

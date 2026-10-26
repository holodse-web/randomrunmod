package com.randomrun.features.battle.screen.room.util.rendering;

import org.joml.Matrix3x2fStack;
import com.randomrun.infrastructure.RandomRunMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SlotMachineAnimator {
    private static final long DURATION = 3000;
    private static final int TICK_BASE_INTERVAL = 50;
    private static final int TICK_MAX_INTERVAL = 250;
    
    private final List<Item> items;
    private final Item result;
    private final long startTime;
    private final ParticleSystem particles;
    
    private int currentIndex = 0;
    private long lastTickTime = 0;
    private boolean soundPlayed = false;
    private int lastCenterX, lastCenterY;
    
    public SlotMachineAnimator(List<Item> allItems, Item result) {
        this.items = new ArrayList<>(allItems);
        Collections.shuffle(this.items);
        this.result = result;
        this.startTime = System.currentTimeMillis();
        this.particles = new ParticleSystem();
    }
    
    public void extractRenderState(GuiGraphicsExtractor context, int centerX, int centerY, float delta) {
        this.lastCenterX = centerX;
        this.lastCenterY = centerY;
        long elapsed = System.currentTimeMillis() - startTime;
        float progress = Math.min(1.0f, elapsed / (float) DURATION);
        
        extractBackground(context, centerX, centerY, elapsed, progress);
        
        if (progress < 1.0f) {
            updateSpinning(centerX, centerY);
            renderSpinning(context, centerX, centerY, elapsed, progress);
        } else {
            if (!soundPlayed) playCompletionSound();
            renderResult(context, centerX, centerY, elapsed);
        }
        
        particles.extractRenderState(context, delta);
    }
    
    private void extractBackground(GuiGraphicsExtractor context, int x, int y, long elapsed, float progress) {
        float pulse = (float) Math.sin(elapsed / 200.0) * 0.1f + 0.9f;
        int bgAlpha = (int) (0xCC * pulse);
        int bgColor = (bgAlpha << 24) | 0x1a0b2e;
        context.fill(x - 70, y - 60, x + 70, y + 60, bgColor);
        
        int borderColor = progress >= 1.0f ? 
            interpolateColor(0xFF6930c3, 0xFFFFD700, (float) Math.sin(elapsed / 150.0) * 0.3f + 0.7f) :
            0xFF6930c3;
        context.outline(x - 70, y - 60, 140, 120, borderColor);
    }
    
    private void updateSpinning(int x, int y) {
        long currentTime = System.currentTimeMillis();
        float progress = Math.min(1.0f, (currentTime - startTime) / (float) DURATION);
        int tickInterval = TICK_BASE_INTERVAL + (int) (progress * (TICK_MAX_INTERVAL - TICK_BASE_INTERVAL));
        
        if (currentTime - lastTickTime >= tickInterval) {
            currentIndex = (currentIndex + 1) % items.size();
            lastTickTime = currentTime;
            particles.spawn(x, y, 3, 0xFF6930c3, 0xFFA78BFA);
            playTickSound(progress);
        }
    }
    
    private void renderSpinning(GuiGraphicsExtractor context, int x, int y, long elapsed, float progress) {
        Item displayItem = items.get(currentIndex);
        ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(displayItem);
        
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        
        float rotation = (elapsed % 1000) / 1000.0f * 360f * progress;
        matrices.rotate((float) Math.toRadians(rotation));
        matrices.scale(4.5f, 4.5f);
        
        context.item(stack, -8, -8);
        matrices.popMatrix();
        
        int textColor = interpolateColor(0xFFFFFF, 0xFFD700, (float) Math.sin(elapsed / 100.0));
        com.randomrun.ui.util.GuiText.centeredText(context, 
            Minecraft.getInstance().font,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.slotmachine.spinning"),
            x, y + 48, textColor
        );
    }
    
    private void renderResult(GuiGraphicsExtractor context, int x, int y, long elapsed) {
        float bounceProgress = (elapsed - DURATION) / 1000.0f;
        float bounce = (float) Math.abs(Math.sin(bounceProgress * Math.PI * 4)) * (1 - bounceProgress) * 10;
        
        ItemStack stack = com.randomrun.ui.util.PreviewItems.stack(result);
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y - bounce);
        matrices.scale(4.5f, 4.5f);
        context.item(stack, -8, -8);
        matrices.popMatrix();
    }
    
    private void playTickSound(float progress) {
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        float pitch = 0.8f + progress * 0.4f;
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume * 0.15f
            )
        );
    }
    
    private void playCompletionSound() {
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.PLAYER_LEVELUP, 1.2f, volume * 0.7f
            )
        );
        soundPlayed = true;
        particles.spawnCelebration(lastCenterX, lastCenterY, 30, 0xFFFFD700, 0xFFFFFFFF);
    }
    
    public boolean isComplete() {
        return System.currentTimeMillis() - startTime >= DURATION + 1000;
    }
    
    public Item getResult() {
        return result;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    private static int interpolateColor(int c1, int c2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * ratio);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * ratio);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    private static class ParticleSystem {
        private final List<Particle> particles = new ArrayList<>();
        private final Random random = new Random();
        
        void spawn(int x, int y, int count, int color1, int color2) {
            for (int i = 0; i < count; i++) {
                float angle = random.nextFloat() * (float) Math.PI * 2;
                float speed = 1 + random.nextFloat() * 2;
                int color = interpolateColor(color1, color2, random.nextFloat());
                particles.add(new Particle(x, y, 
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color));
            }
        }
        
        void spawnCelebration(int x, int y, int count, int color1, int color2) {
            for (int i = 0; i < count; i++) {
                float angle = random.nextFloat() * (float) Math.PI * 2;
                float speed = 2 + random.nextFloat() * 4;
                int color = interpolateColor(color1, color2, random.nextFloat());
                particles.add(new Particle(x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color));
            }
        }
        
        void extractRenderState(GuiGraphicsExtractor context, float delta) {
            particles.removeIf(p -> {
                p.update(delta);
                if (p.life > 0) {
                    int alpha = (int) (p.life * 255);
                    int color = (p.color & 0x00FFFFFF) | (alpha << 24);
                    context.fill((int) p.x, (int) p.y, (int) p.x + 3, (int) p.y + 3, color);
                    return false;
                }
                return true;
            });
        }
    }
    
    private static class Particle {
        float x, y, vx, vy, life = 1.0f;
        int color;
        
        Particle(float x, float y, float vx, float vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }
        
        void update(float delta) {
            x += vx * delta;
            y += vy * delta;
            vy += 0.08f * delta;
            life -= 0.02f * delta;
        }
    }
}

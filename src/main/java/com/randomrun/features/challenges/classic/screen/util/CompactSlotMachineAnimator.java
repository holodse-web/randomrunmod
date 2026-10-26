package com.randomrun.features.challenges.classic.screen.util;

import org.joml.Matrix3x2fStack;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.ItemPreviewRenderer;
import com.randomrun.ui.util.PreviewItems;
import com.randomrun.ui.util.PreviewRenderGeometry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CompactSlotMachineAnimator {
    private static final long DURATION = 3000;
    private static final int TICK_BASE_INTERVAL = 50;
    private static final int TICK_MAX_INTERVAL = 250;
    
    private final List<Item> items;
    private final Item result;
    private final long startTime;
    private final ParticleSystem particles;
    private final Map<Item, ItemStack> previews = new HashMap<>();
    private final boolean animated;
    
    private int currentIndex = 0;
    private long lastTickTime = -TICK_BASE_INTERVAL;
    private boolean soundPlayed = false;
    private int lastCenterX, lastCenterY;
    
    public CompactSlotMachineAnimator(List<Item> allItems, Item result) {
        this.items = new ArrayList<>(allItems.isEmpty() ? List.of(result) : allItems);
        Collections.shuffle(this.items);
        this.result = result;
        this.startTime = System.nanoTime();
        this.animated = RandomRunMod.getInstance().getConfig().isAnimationsEnabled();
        this.particles = new ParticleSystem();
    }
    
    public void extractRenderState(GuiGraphicsExtractor context, int centerX, int centerY, float delta) {
        this.lastCenterX = centerX;
        this.lastCenterY = centerY;
        long elapsed = elapsedMillis();
        float progress = Math.min(1.0f, elapsed / (float) DURATION);
        
        if (progress < 1.0f) {
            updateSpinning(centerX, centerY);
            renderSpinning(context, centerX, centerY, elapsed, progress);
        } else {
            if (!soundPlayed) playCompletionSound();
            renderResult(context, centerX, centerY, elapsed);
        }
        
        if (animated) particles.extractRenderState(context);
    }
    
    private void updateSpinning(int x, int y) {
        long currentTime = elapsedMillis();
        float progress = Math.min(1.0f, currentTime / (float) DURATION);
        int tickInterval = TICK_BASE_INTERVAL + (int) (progress * (TICK_MAX_INTERVAL - TICK_BASE_INTERVAL));
        
        if (currentTime - lastTickTime >= tickInterval) {
            currentIndex = (currentIndex + 1) % items.size();
            lastTickTime = currentTime;
            if (animated) particles.spawn(x, y, 3, 0xFF6930c3, 0xFFA78BFA);
            playTickSound(progress);
        }
    }
    
    private void renderSpinning(GuiGraphicsExtractor context, int x, int y, long elapsed, float progress) {
        Item displayItem = items.get(currentIndex);
        ItemStack stack = previews.computeIfAbsent(displayItem, PreviewItems::stack);
        
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        
        float rotation = animated ? elapsed / 1000.0f * 360f * progress : 0;
        matrices.rotate((float) Math.toRadians(rotation));
        ItemPreviewRenderer.extractLargeGui(context, stack, 0, 0, 48);
        matrices.popMatrix();
        int textColor = interpolateColor(0xFFFFFF, 0xFFD700, animated ? (float) Math.sin(elapsed / 100.0) : 0);
        com.randomrun.ui.util.GuiText.text(context, 
            Minecraft.getInstance().font,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.slotmachine.spinning"),
            x + 40, y - 4, textColor
        );
    }
    
    private void renderResult(GuiGraphicsExtractor context, int x, int y, long elapsed) {
        float bounceProgress = Math.clamp((elapsed - DURATION) / 1000.0f, 0, 1);
        float bounce = animated ? (float) Math.abs(Math.sin(bounceProgress * Math.PI * 4)) * (1 - bounceProgress) * 10 : 0;
        ItemStack stack = previews.computeIfAbsent(result, PreviewItems::stack);
        ItemPreviewRenderer.extractLargeGui(context, stack, x, y - bounce, 48);
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
        soundPlayed = true;
        if (animated) particles.spawnCelebration(lastCenterX, lastCenterY, 30, 0xFFFFD700, 0xFFFFFFFF);
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.PLAYER_LEVELUP, 1.2f, volume * 0.7f
            )
        );
    }
    
    public boolean isComplete() {
        return elapsedMillis() >= DURATION + 1000;
    }
    
    public Item getResult() {
        return result;
    }

    private long elapsedMillis() { return (System.nanoTime() - startTime) / 1_000_000L; }
    
    private static int interpolateColor(int c1, int c2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * ratio);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * ratio);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    private static class ParticleSystem {
        private static final int MAX_PARTICLES = 128;
        private final List<Particle> particles = new ArrayList<>();
        private final Random random = new Random();
        private long lastFrameNanos = System.nanoTime();
        
        void spawn(int x, int y, int count, int color1, int color2) {
            reserve(count);
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
            reserve(count);
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
        
        private void reserve(int count) {
            int excess = Math.max(0, particles.size() + count - MAX_PARTICLES);
            if (excess > 0) particles.subList(0, Math.min(excess, particles.size())).clear();
        }

        void extractRenderState(GuiGraphicsExtractor context) {
            long now = System.nanoTime();
            float delta = PreviewRenderGeometry.frameSeconds(now - lastFrameNanos) * 20;
            lastFrameNanos = now;
            if (particles.isEmpty()) return;
            GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(particles.size());
            for (var iterator = particles.iterator(); iterator.hasNext();) {
                Particle p = iterator.next();
                p.update(delta);
                if (p.life > 0) {
                    int alpha = (int) (p.life * 255);
                    int color = (p.color & 0x00FFFFFF) | (alpha << 24);
                    batch.fill((int) p.x, (int) p.y, (int) p.x + 3, (int) p.y + 3, color);
                } else iterator.remove();
            }
            batch.build().extract(context);
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

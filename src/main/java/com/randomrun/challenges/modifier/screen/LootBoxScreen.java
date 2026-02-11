package com.randomrun.challenges.modifier.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.modifier.Modifier;
import com.randomrun.challenges.modifier.ModifierRegistry;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.RevealAnimationWidget;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootBoxScreen extends AbstractRandomRunScreen {
    private final Runnable onFinish;
    private final Modifier modifier;
    
    // Animation State
    private int tickCounter = 0;
    private AnimationPhase phase = AnimationPhase.FALL;
    
    // Visual State (Calculated in render)
    private float tntY = -100f;
    private float tntScale = 0f;
    private float tntRotation = 0f;
    // private boolean tntFlashing = false; // UNUSED
    private float shakeIntensity = 0f;
    
    private float itemX = 0f;
    private float itemY = 0f;
    private float itemScale = 0f;
    private float itemRotation = 0f;
    
    private float flashAlpha = 0f;
    private float textAlpha = 0f;
    
    // Particles
    private final List<LootParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    
    private ButtonDefault continueButton;
    private RevealAnimationWidget revealWidget;
    
    public LootBoxScreen(Runnable onFinish) {
        super(Text.empty());
        this.onFinish = onFinish;
        
        RandomRunMod.LOGGER.info("Создание LootBoxScreen...");
        this.modifier = ModifierRegistry.getRandomModifier();
        
        if (this.modifier == null) {
            RandomRunMod.LOGGER.error("LootBoxScreen создан, но ModifierRegistry вернул NULL! Анимация будет пропущена.");
        } else {
            RandomRunMod.LOGGER.info("LootBoxScreen создан с модификатором: " + this.modifier.getId());
        }
    }
    
    private enum AnimationPhase {
        FALL,           // TNT falls down
        WAIT_FOR_CLICK, // Waits for user interaction
        IGNITED,        // Flashing and shaking
        EXPLOSION,      // Boom
        FLY_OUT,        // Item flies to widget position
        FINAL           // Widget takes over
    }
    
    @Override
    protected void init() {
        super.init();
        
        if (this.modifier == null) {
            onFinish.run();
            return;
        }
        
        if (modifier != null) {
            RandomRunMod.getInstance().getRunDataManager().addActiveModifier(modifier);
            RandomRunMod.LOGGER.info("Добавлен активный модификатор: " + modifier.getId());
        }
        
        int centerX = width / 2;
        int buttonY = height - 50;
        
        continueButton = new ButtonDefault(
            centerX - 100, buttonY,
            200, 20,
            Text.translatable("randomrun.button.continue"),
            button -> onFinish.run(),
            0, 0.1f
        );
        continueButton.visible = false;
        continueButton.active = false;
        addDrawableChild(continueButton);
        
        revealWidget = new RevealAnimationWidget(centerX, height / 2 - 40, modifier.getIcon());
        
        tntY = -100; 
        tntScale = 4.0f;
        phase = AnimationPhase.FALL;
        tickCounter = 0;
    }
    
    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        
        updateLogic();
        updateParticles();
        
        if (phase == AnimationPhase.FINAL) {
            continueButton.visible = true;
            continueButton.active = true;
        }
    }
    
    // Handles state transitions and sounds (Fixed Tick Update 20Hz)
    private void updateLogic() {
        switch (phase) {
            case FALL:
                if (tickCounter >= 15) {
                    MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_WOOD_PLACE, 1.0f));
                    MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_LAND, 0.5f));
                    shakeIntensity = 5f;
                    phase = AnimationPhase.WAIT_FOR_CLICK;
                }
                break;
                
            case WAIT_FOR_CLICK:
                break;
                
            case IGNITED:
                // Slower ignition: 60 ticks (3 seconds)
                float progress = tickCounter / 60f;
                
                if (tickCounter % 10 == 0) { // Slower sound interval
                    MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_TNT_PRIMED, 1.0f + progress * 0.5f));
                }
                
                if (tickCounter >= 60) {
                    phase = AnimationPhase.EXPLOSION;
                    tickCounter = 0;
                }
                break;
                
            case EXPLOSION:
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f));
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.2f));
                
                int centerX = width / 2;
                int centerY = height / 2;
                spawnExplosionParticles(centerX, centerY, modifier.getRarity());
                spawnDustParticles(centerX, centerY, 60);
                spawnTNTFragments(centerX, centerY, 20); // NEW: TNT Fragments
                
                phase = AnimationPhase.FLY_OUT;
                tickCounter = 0;
                break;
                
            case FLY_OUT:
                // 1.5 seconds flight for smoother transition
                if (tickCounter >= 30) {
                    phase = AnimationPhase.FINAL;
                }
                break;
                
            case FINAL:
                break;
        }
    }
    
    // Calculates visual state based on interpolated time (Smooth Render)
    private void calculateVisuals(float delta) {
        float time = tickCounter + delta;
        int centerX = width / 2;
        int centerY = height / 2;
        
        switch (phase) {
            case FALL:
                float t = time / 15f;
                if (t > 1.0f) t = 1.0f;
                // Bounce effect
                tntY = MathHelper.lerp(t * t, -100f, (float)centerY);
                tntScale = 4.0f;
                tntRotation = 0f;
                break;
                
            case WAIT_FOR_CLICK:
                tntY = centerY;
                // Decay shake
                float shakeDecay = Math.max(0, 1f - (time - 15) / 20f); 
                tntRotation = (float)Math.sin(time * 0.5f) * 5f * shakeDecay;
                tntScale = 4.0f;
                break;
                
            case IGNITED:
                tntY = centerY;
                float progress = time / 60f; 
                if (progress > 1f) progress = 1f;
                
                // Slower flashing: every ~8 ticks
                // Use sine wave for smooth flashing opacity instead of on/off
                // float flash = (float)Math.sin(time * 0.5f); // Slower flash
                // tntFlashing = flash > 0;
                
                // Shake increases
                shakeIntensity = 2f + progress * 10f;
                // Slower but growing rotation shake
                tntRotation = (float)Math.sin(time * 0.8f) * shakeIntensity;
                
                // Expand slightly (Swell)
                tntScale = 4.0f + progress * 1.0f;
                break;
                
            case EXPLOSION:
                flashAlpha = 1.0f;
                break;
                
            case FLY_OUT:
                float flyT = time / 30f; // 1.5s
                if (flyT > 1.0f) flyT = 1.0f;
                
                // Ease out cubic
                float easeT = 1f - (1f - flyT) * (1f - flyT) * (1f - flyT);
                
                float startY = centerY;
                float endY = centerY - 40;
                float peakY = centerY - 150; // Higher arc
                
                if (easeT < 0.5f) {
                    float upT = easeT * 2f;
                    itemY = MathHelper.lerp(upT, startY, peakY);
                } else {
                    float downT = (easeT - 0.5f) * 2f;
                    // Smooth sine easing for landing
                    downT = -(float)Math.cos(Math.PI * downT) / 2.0f + 0.5f;
                    itemY = MathHelper.lerp(downT, peakY, endY);
                }
                
                itemX = centerX;
                itemScale = MathHelper.lerp(easeT, 0.0f, 6.0f);
                
                // Rotation: Fast spin -> Slow stop
                // 720 degrees spin
                itemRotation = MathHelper.lerp(easeT, 0f, 720f); 
                
                flashAlpha = Math.max(0, 1.0f - flyT * 2.0f); // Fade out flash quickly
                textAlpha = easeT;
                break;
                
            case FINAL:
                itemY = centerY - 40;
                itemX = centerX;
                itemScale = 6.0f;
                itemRotation = 0f;
                textAlpha = 1.0f;
                flashAlpha = 0f;
                break;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase == AnimationPhase.WAIT_FOR_CLICK && button == 0) {
            int centerX = width / 2;
            int centerY = height / 2;
            int size = (int)(16 * tntScale);
            
            if (mouseX >= centerX - size && mouseX <= centerX + size &&
                mouseY >= centerY - size && mouseY <= centerY + size) {
                
                phase = AnimationPhase.IGNITED;
                tickCounter = 0;
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_TNT_PRIMED, 1.0f));
                
                // Spawn some smoke particles on click
                spawnDustParticles(centerX, centerY, 10);
                return true;
            }
        }
        
        if (phase == AnimationPhase.FINAL && revealWidget != null) {
            if (revealWidget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Calculate smooth visuals first
        calculateVisuals(delta);
        
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0xAA000000); 
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Render TNT
        if (phase == AnimationPhase.FALL || phase == AnimationPhase.WAIT_FOR_CLICK || phase == AnimationPhase.IGNITED) {
            context.getMatrices().push();
            context.getMatrices().translate(centerX, tntY, 0);
            context.getMatrices().scale(tntScale, tntScale, 1f);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tntRotation));
            
            context.drawItem(new ItemStack(Items.TNT), -8, -8);
            
            // Flashing White Overlay
            if (phase == AnimationPhase.IGNITED) {
                // Smooth flash opacity
                float time = tickCounter + delta;
                float flashOpacity = (float)Math.abs(Math.sin(time * 0.2f)) * 0.6f; // 0.0 to 0.6 alpha
                
                if (flashOpacity > 0.1f) {
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, 1); 
                    
                    RenderSystem.enableBlend();
                    int alpha = (int)(flashOpacity * 255);
                    int color = (alpha << 24) | 0xFFFFFF;
                    context.fill(-8, -8, 8, 8, color);
                    RenderSystem.disableBlend();
                    
                    context.getMatrices().pop();
                }
            }
            
            context.getMatrices().pop();
            
            if (phase == AnimationPhase.WAIT_FOR_CLICK) {
                float pulse = (float)Math.sin((tickCounter + delta) / 10.0) * 0.05f + 1.0f;
                context.getMatrices().push();
                context.getMatrices().translate(centerX, centerY - 80, 0);
                context.getMatrices().scale(pulse, pulse, 1f);
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.click_to_open"), 0, 0, 0xFFFFFF);
                context.getMatrices().pop();
            }
        }
        
        // Render Item
        if (phase == AnimationPhase.FLY_OUT) {
            context.getMatrices().push();
            context.getMatrices().translate(itemX, itemY, 100); 
            context.getMatrices().scale(itemScale, itemScale, 1f);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(itemRotation));
            
            context.drawItem(modifier.getIcon(), -8, -8);
            
            context.getMatrices().pop();
        }
        
        if (phase == AnimationPhase.FINAL && revealWidget != null) {
            revealWidget.render(context, mouseX, mouseY, delta);
        }
        
        // Text
        if (phase == AnimationPhase.FLY_OUT || phase == AnimationPhase.FINAL) {
            if (textAlpha > 0) {
                int alpha = (int)(textAlpha * 255);
                int color = (alpha << 24) | 0xFFFFFF;
                int rarityColor = (alpha << 24) | modifier.getRarity().color;
                
                context.getMatrices().push();
                context.getMatrices().translate(centerX, centerY + 60, 0);
                
                float s = 1.0f + (1.0f - textAlpha) * 0.5f; 
                float baseScale = 2.0f;
                context.getMatrices().scale(s * baseScale, s * baseScale, 1f);
                
                Text name = modifier.getName();
                Text rarityName = Text.translatable("randomrun.rarity." + modifier.getRarity().name().toLowerCase()).formatted(modifier.getRarity().formatting);
                
                context.drawCenteredTextWithShadow(textRenderer, name, 0, 0, rarityColor);
                
                context.getMatrices().scale(0.6f, 0.6f, 1f); 
                context.drawCenteredTextWithShadow(textRenderer, rarityName, 0, 15, color);
                
                context.getMatrices().scale(0.8f, 0.8f, 1f);
                
                int maxWidth = 200;
                List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(modifier.getDescription(), maxWidth);
                
                int yOffset = 30;
                for (net.minecraft.text.OrderedText line : lines) {
                    context.drawCenteredTextWithShadow(textRenderer, line, 0, yOffset, 0xDDDDDD | (alpha << 24));
                    yOffset += 10;
                }
                
                context.getMatrices().pop();
            }
        }
        
        // Flash
        if (flashAlpha > 0) {
            int alpha = (int)(flashAlpha * 255);
            int color = (alpha << 24) | 0xFFFFFF;
            context.fill(0, 0, width, height, color);
        }
        
        // Particles (Interpolate positions if we want super smooth particles, but usually they are small enough)
        // Let's implement simple interpolation for particles too
        for (LootParticle p : particles) {
            p.render(context, delta);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (phase == AnimationPhase.FINAL && revealWidget != null) {
            if (revealWidget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (phase == AnimationPhase.FINAL && revealWidget != null) {
            if (revealWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    private enum LootParticleType {
        DUST, SPARK, GLOW, TNT_FRAGMENT
    }
    
    private class LootParticle {
        float x, y, vx, vy;
        float prevX, prevY; // For interpolation
        int size;
        int color;
        float alpha = 1.0f;
        boolean alive = true;
        LootParticleType type;
        
        LootParticle(float x, float y, float vx, float vy, int size, int color, LootParticleType type) {
            this.x = x; this.y = y; 
            this.prevX = x; this.prevY = y;
            this.vx = vx; this.vy = vy;
            this.size = size; this.color = color; this.type = type;
        }
        
        void update() {
            prevX = x;
            prevY = y;
            x += vx;
            y += vy;
            
            if (type == LootParticleType.DUST) {
                vy += 0.1f; // Gravity
                alpha -= 0.05f;
            } else if (type == LootParticleType.SPARK) {
                vx *= 0.9f; vy *= 0.9f;
                alpha -= 0.03f;
            } else if (type == LootParticleType.GLOW) {
                vy -= 0.02f; // Float up
                alpha -= 0.02f;
            } else if (type == LootParticleType.TNT_FRAGMENT) {
                vy += 0.2f; // Heavy gravity
                vx *= 0.95f; // Air resistance
                alpha -= 0.01f; // Last longer
                // Rotate? visual only
            }
            
            if (alpha <= 0) alive = false;
        }
        
        void render(DrawContext context, float delta) {
            float rx = MathHelper.lerp(delta, prevX, x);
            float ry = MathHelper.lerp(delta, prevY, y);
            
            int a = (int)(alpha * 255);
            int c = (a << 24) | (color & 0xFFFFFF);
            context.fill((int)rx, (int)ry, (int)rx + size, (int)ry + size, c);
        }
    }
    
    private void spawnDustParticles(int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new LootParticle(
                x, y,
                (random.nextFloat() - 0.5f) * 4f,
                (random.nextFloat() - 0.5f) * 1f,
                random.nextInt(3) + 2,
                0x888888,
                LootParticleType.DUST
            ));
        }
    }
    
    private void spawnTNTFragments(int x, int y, int count) {
        // Red, White, Black particles
        int[] colors = {0xFF0000, 0xFFFFFF, 0x000000, 0xDD0000};
        
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float)Math.PI * 2;
            float speed = random.nextFloat() * 10f + 5f;
            
            particles.add(new LootParticle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed - 5f, // Upward bias
                random.nextInt(6) + 4, // Larger pieces
                colors[random.nextInt(colors.length)],
                LootParticleType.TNT_FRAGMENT
            ));
        }
    }
    
    private void spawnExplosionParticles(int x, int y, Modifier.Rarity rarity) {
        int color = rarity.particleColor;
        for (int i = 0; i < 50; i++) {
            float angle = random.nextFloat() * (float)Math.PI * 2;
            float speed = random.nextFloat() * 8f + 2f;
            particles.add(new LootParticle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                random.nextInt(4) + 3,
                color,
                LootParticleType.SPARK
            ));
        }
    }
    
    private void updateParticles() {
        particles.removeIf(p -> {
            p.update();
            return !p.alive;
        });
    }
}

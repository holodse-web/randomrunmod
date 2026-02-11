package com.randomrun.challenges.classic.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.battle.screen.MatchReadyScreen;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemSelectionAnimationScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String matchId;
    private final boolean isSolo;
    
    private List<Item> allItems = new ArrayList<>();
    private int currentIndex = 0;
    private long animationStartTime;
    private Item selectedItem = null;
    private boolean animationComplete = false;
    private List<Particle> particles = new ArrayList<>();
    
    private static final long ANIMATION_DURATION = 2500; 
    private static final int INITIAL_TICK_SPEED = 40; 
    private static final int FINAL_TICK_SPEED = 250; 
    
    private long lastTickTime = 0;
    
    private final java.util.function.Consumer<Item> onFinished;

    public ItemSelectionAnimationScreen(Screen parent, String matchId) {
        super(Text.translatable("randomrun.battle.item_selection"));
        this.parent = parent;
        this.matchId = matchId;
        this.isSolo = false;
        this.onFinished = null;
    }

    public ItemSelectionAnimationScreen(Screen parent, Item targetItem) {
        super(Text.translatable("randomrun.battle.item_selection"));
        this.parent = parent;
        this.matchId = null;
        this.selectedItem = targetItem;
        this.isSolo = true;
        this.onFinished = null;
    }
    
    public ItemSelectionAnimationScreen(Screen parent, Item targetItem, java.util.function.Consumer<Item> onFinished) {
        super(Text.translatable("randomrun.battle.item_selection"));
        this.parent = parent;
        this.matchId = null;
        this.selectedItem = targetItem;
        this.isSolo = true;
        this.onFinished = onFinished;
    }
    
    @Override
    protected void init() {
        super.init();
        
        boolean allowUnobtainable = RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
        allItems = ItemDifficulty.getAllItems(allowUnobtainable);
        
        if (isSolo) {
            animationStartTime = System.currentTimeMillis();
            lastTickTime = animationStartTime;
            RandomRunMod.LOGGER.info("Solo Animation started with " + allItems.size() + " items");
        } else {
            animationStartTime = 0;
            lastTickTime = 0;
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        updateParticles(0.05f); // Приблизительная дельта для 20 tps
        
        if (!isSolo && selectedItem == null) {
            BattleRoom room = BattleManager.getInstance().getCurrentRoom();
            if (room != null && room.getTargetItem() != null && !room.getTargetItem().isEmpty()) {
                selectedItem = Registries.ITEM.get(Identifier.of(room.getTargetItem()));
                RandomRunMod.LOGGER.info("Item selected from Firebase: " + room.getTargetItem());
                
                animationStartTime = System.currentTimeMillis();
                lastTickTime = animationStartTime;
                RandomRunMod.LOGGER.info("Animation started with " + allItems.size() + " items");
            }
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        long elapsed = System.currentTimeMillis() - animationStartTime;
        float progress = Math.min(1.0f, elapsed / (float) ANIMATION_DURATION);
        
        // Pulsating background
        // Пульсирующий фон
        float pulse = (float) Math.sin(elapsed / 200.0) * 0.1f + 0.9f;
        int bgAlpha = (int) (0xCC * pulse) << 24;
        context.fill(centerX - 70, centerY - 60, centerX + 70, centerY + 60, bgAlpha);
        
        // Animated border
        // Анимированная рамка
        int borderColor = 0xFF6930c3;
        if (progress >= 1.0f) {
            float glow = (float) Math.sin(elapsed / 150.0) * 0.3f + 0.7f;
            borderColor = interpolateColor(0xFF6930c3, 0xFFFFD700, glow);
        }
        context.drawBorder(centerX - 70, centerY - 60, 140, 120, borderColor);
        
        if (!animationComplete && elapsed < ANIMATION_DURATION) {
            long currentTime = System.currentTimeMillis();
            int tickSpeed = (int) (INITIAL_TICK_SPEED + (FINAL_TICK_SPEED - INITIAL_TICK_SPEED) * progress);
            
            if (currentTime - lastTickTime >= tickSpeed) {
                currentIndex = (currentIndex + 1) % allItems.size();
                lastTickTime = currentTime;
                
                spawnParticles(centerX, centerY);
                
                if (client.player != null) {
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.0f + progress);
                }
            }
            
            Item currentItem = allItems.get(currentIndex);
            
            // Rotating icon with motion blur effect
            // Вращающаяся иконка с эффектом размытия в движении
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY, 0);
            
            // Rotation
            // Вращение
            float rotation = (currentTime % 1000) / 1000.0f * 360f;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation * progress));
            
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(new ItemStack(currentItem), -8, -8);
            matrices.pop();
            
            // Spinning text
            // Вращающийся текст
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.slotmachine.spinning"), 
                centerX, centerY + 48, interpolateColor(0xFFFFFF, 0xFFD700, (float) Math.sin(elapsed / 100.0)));
            
        } else if (selectedItem != null) {
            if (!animationComplete) {
                animationComplete = true;
                
                if (client.player != null) {
                    client.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                
                Random random = new Random();
                for (int i = 0; i < 30; i++) {
                    spawnCelebrationParticle(centerX, centerY, random);
                }
                
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        proceedToNextScreen();
                    } catch (InterruptedException e) {
                        RandomRunMod.LOGGER.error("Задержка анимации прервана", e);
                    }
                }).start();
            }
            
            // Bouncing icon animation
            float bounceProgress = (elapsed - ANIMATION_DURATION) / 1000.0f;
            float bounce = (float) Math.abs(Math.sin(bounceProgress * Math.PI * 4)) * (1 - bounceProgress) * 10;
            
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(centerX, centerY - bounce, 0);
            matrices.scale(4.5f, 4.5f, 1f);
            context.drawItem(new ItemStack(selectedItem), -8, -8);
            matrices.pop();
            
            // Item name
            String itemName = selectedItem.getName().getString();
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e" + itemName),
                centerX, centerY + 48, 0xFFFFFF);
        }
        
        renderParticles(context, delta);
    }
    
    private void proceedToNextScreen() {
        MinecraftClient.getInstance().execute(() -> {
            if (onFinished != null) {
                onFinished.accept(selectedItem);
            } else if (isSolo) {
                MinecraftClient.getInstance().setScreen(
                    new ItemRevealScreen(parent, selectedItem)
                );
            } else {
                MinecraftClient.getInstance().setScreen(
                    new MatchReadyScreen(parent, matchId)
                );
            }
        });
    }
    
    private void spawnParticles(int x, int y) {
        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2;
            float speed = 1 + random.nextFloat() * 2;
            particles.add(new Particle(x, y, 
                (float) Math.cos(angle) * speed, 
                (float) Math.sin(angle) * speed, 
                interpolateColor(0xFF6930c3, 0xFFA78BFA, random.nextFloat())));
        }
    }
    
    private void spawnCelebrationParticle(int x, int y, Random random) {
        float angle = random.nextFloat() * (float) Math.PI * 2;
        float speed = 2 + random.nextFloat() * 4;
        particles.add(new Particle(x, y, 
            (float) Math.cos(angle) * speed, 
            (float) Math.sin(angle) * speed, 
            interpolateColor(0xFFFFD700, 0xFFFFFFFF, random.nextFloat())));
    }
    
    private void updateParticles(float delta) {
        particles.removeIf(p -> {
            p.update(delta);
            return p.life <= 0;
        });
    }
    
    private void renderParticles(DrawContext context, float delta) {
        for (Particle p : particles) {
            int alpha = (int) (p.life * 255);
            int color = (p.color & 0x00FFFFFF) | (alpha << 24);
            context.fill((int) p.x, (int) p.y, (int) p.x + 3, (int) p.y + 3, color);
        }
    }
    
    private int interpolateColor(int color1, int color2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float life = 1.0f;
        int color;
        
        Particle(float x, float y, float vx, float vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }
        
        void update(float delta) {
            x += vx;
            y += vy;
            vy += 0.1f; // Гравитация
            life -= 0.02f;
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
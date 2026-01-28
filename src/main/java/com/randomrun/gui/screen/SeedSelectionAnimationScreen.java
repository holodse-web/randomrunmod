package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Random;

public class SeedSelectionAnimationScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String matchId;
    
    private long currentSeed = 0;
    private String selectedSeed = null;
    private long lastTickTime = 0;
    private long animationStartTime;
    private boolean animationComplete = false;
    
    private static final long ANIMATION_DURATION = 2500; // Ускорено
    private static final int INITIAL_TICK_SPEED = 25; // Ускорено
    private static final int FINAL_TICK_SPEED = 180; // Ускорено
    
    public SeedSelectionAnimationScreen(Screen parent, String matchId) {
        super(Text.translatable("randomrun.battle.seed_generation"));
        this.parent = parent;
        this.matchId = matchId;
    }
    
    @Override
    protected void init() {
        super.init();
        
        animationStartTime = System.currentTimeMillis();
        lastTickTime = animationStartTime;
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room != null) {
            selectedSeed = room.getSeed();
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l§b" + Text.translatable("randomrun.battle.seed_generation").getString().toUpperCase()), 
            centerX, 30, 0xFFFFFF);
        
        long elapsed = System.currentTimeMillis() - animationStartTime;
        
        if (!animationComplete && elapsed < ANIMATION_DURATION) {
            long currentTime = System.currentTimeMillis();
            float progress = (float) elapsed / ANIMATION_DURATION;
            int tickSpeed = (int) (INITIAL_TICK_SPEED + (FINAL_TICK_SPEED - INITIAL_TICK_SPEED) * progress);
            
            if (currentTime - lastTickTime >= tickSpeed) {
                currentSeed = new Random().nextLong();
                lastTickTime = currentTime;
                
                if (client.player != null) {
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 0.8f + progress * 0.4f);
                }
            }
            
            renderSeedDisplay(context, centerX, centerY, currentSeed, false);
            
            int barWidth = 200;
            int barHeight = 10;
            int barX = centerX - barWidth / 2;
            int barY = centerY + 80;
            
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0x88000000);
            context.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF00FFFF);
            
        } else if (selectedSeed != null) {
            if (!animationComplete) {
                animationComplete = true;
                
                if (client.player != null) {
                    client.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
                
                MinecraftClient.getInstance().execute(() -> {
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            MinecraftClient.getInstance().execute(() -> {
                                MinecraftClient.getInstance().setScreen(
                                    new MatchReadyScreen(parent, matchId)
                                );
                            });
                        } catch (InterruptedException e) {
                            RandomRunMod.LOGGER.error("Animation delay interrupted", e);
                        }
                    }).start();
                });
            }
            
            try {
                long seedLong = Long.parseLong(selectedSeed);
                renderSeedDisplay(context, centerX, centerY, seedLong, true);
            } catch (NumberFormatException e) {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§e§l" + selectedSeed),
                    centerX, centerY, 0xFFFFFF);
            }
            
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§a§l✓ " + Text.translatable("randomrun.battle.seed_generated").getString().toUpperCase()),
                centerX, centerY + 80, 0x55FF55);
        }
    }
    
    private void renderSeedDisplay(DrawContext context, int centerX, int centerY, long seed, boolean selected) {
        int boxWidth = 300;
        int boxHeight = 60;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;
        
        int bgColor = selected ? 0xDD00FFFF : 0xDD000000;
        context.fill(boxX - 5, boxY - 5, boxX + boxWidth + 5, boxY + boxHeight + 5, bgColor);
        
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF1a1a1a);
        
        String seedText = String.valueOf(seed);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal(selected ? "§e§l" + seedText : "§7" + seedText),
            centerX, centerY - 5, 0xFFFFFF);
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§7" + Text.translatable("randomrun.battle.world_seed").getString()),
            centerX, centerY + 50, 0xAAAAAA);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}

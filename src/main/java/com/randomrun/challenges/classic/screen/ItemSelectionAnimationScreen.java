package com.randomrun.challenges.classic.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ItemSelectionAnimationScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String matchId;
    
    private List<Item> allItems = new ArrayList<>();
    private int currentIndex = 0;
    private long lastTickTime = 0;
    private long animationStartTime;
    private Item selectedItem = null;
    private boolean animationComplete = false;
    
    private static final long ANIMATION_DURATION = 2500; 
    private static final int INITIAL_TICK_SPEED = 40; 
    private static final int FINAL_TICK_SPEED = 250; 
    
    public ItemSelectionAnimationScreen(Screen parent, String matchId) {
        super(Text.translatable("randomrun.battle.item_selection"));
        this.parent = parent;
        this.matchId = matchId;
    }
    
    @Override
    protected void init() {
        super.init();
        
        
        animationStartTime = 0;
        lastTickTime = 0;
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (selectedItem == null) {
            BattleRoom room = BattleManager.getInstance().getCurrentRoom();
            if (room != null && room.getTargetItem() != null && !room.getTargetItem().isEmpty()) {
                selectedItem = Registries.ITEM.get(new Identifier(room.getTargetItem()));
                RandomRunMod.LOGGER.info("Item selected from Firebase: " + room.getTargetItem());
                
                
                if (animationStartTime == 0) {
                    boolean allowUnobtainable = RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
                    allItems = ItemDifficulty.getAllItems(allowUnobtainable);
                    
                    animationStartTime = System.currentTimeMillis();
                    lastTickTime = animationStartTime;
                    RandomRunMod.LOGGER.info("Animation started with " + allItems.size() + " items");
                }
            }
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l§6" + Text.translatable("randomrun.battle.item_selection").getString().toUpperCase()), 
            centerX, 30, 0xFFFFFF);
        
        long elapsed = System.currentTimeMillis() - animationStartTime;
        
        if (!animationComplete && elapsed < ANIMATION_DURATION) {
            long currentTime = System.currentTimeMillis();
            float progress = (float) elapsed / ANIMATION_DURATION;
            int tickSpeed = (int) (INITIAL_TICK_SPEED + (FINAL_TICK_SPEED - INITIAL_TICK_SPEED) * progress);
            
            if (currentTime - lastTickTime >= tickSpeed) {
                currentIndex = (currentIndex + 1) % allItems.size();
                lastTickTime = currentTime;
                
                if (client.player != null) {
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.0f + progress);
                }
            }
            
            Item currentItem = allItems.get(currentIndex);
            renderItemDisplay(context, centerX, centerY, currentItem, false);
            
            int barWidth = 200;
            int barHeight = 10;
            int barX = centerX - barWidth / 2;
            int barY = centerY + 80;
            
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0x88000000);
            context.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF00FF00);
            
        } else if (selectedItem != null) {
            if (!animationComplete) {
                animationComplete = true;
                
                if (client.player != null) {
                    client.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                
                MinecraftClient.getInstance().execute(() -> {
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            MinecraftClient.getInstance().execute(() -> {
                                MinecraftClient.getInstance().setScreen(
                                    new SeedSelectionAnimationScreen(parent, matchId)
                                );
                            });
                        } catch (InterruptedException e) {
                            RandomRunMod.LOGGER.error("Animation delay interrupted", e);
                        }
                    }).start();
                });
            }
            
            renderItemDisplay(context, centerX, centerY, selectedItem, true);
            
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§a§l✓ " + Text.translatable("randomrun.battle.selected").getString().toUpperCase()),
                centerX, centerY + 80, 0x55FF55);
        }
    }
    
    private void renderItemDisplay(DrawContext context, int centerX, int centerY, Item item, boolean selected) {
        int boxSize = 80;
        int boxX = centerX - boxSize / 2;
        int boxY = centerY - boxSize / 2;
        
        int bgColor = selected ? 0xDD00FF00 : 0xDD000000;
        context.fill(boxX - 5, boxY - 5, boxX + boxSize + 5, boxY + boxSize + 5, bgColor);
        
        context.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF1a1a1a);
        
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(4.0f, 4.0f, 1.0f);
        context.drawItem(new ItemStack(item), -8, -8);
        context.getMatrices().pop();
        
        String itemName = item.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal(selected ? "§e" + itemName : "§7" + itemName),
            centerX, centerY + 50, 0xFFFFFF);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}

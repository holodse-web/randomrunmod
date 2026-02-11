package com.randomrun.challenges.classic.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.challenges.time.screen.TimeSelectionScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.RevealAnimationWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import com.randomrun.challenges.modifier.screen.LootBoxScreen;

public class ItemRevealScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Item targetItem;
    private final ItemDifficulty.Difficulty difficulty;
    
    private RevealAnimationWidget revealWidget;
    
    public ItemRevealScreen(Screen parent, Item item) {
        super(Text.translatable("randomrun.screen.item_reveal.title"));
        this.parent = parent;
        this.targetItem = item;
        this.difficulty = ItemDifficulty.getDifficulty(item);
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int buttonY = height - 55;
        
        revealWidget = new RevealAnimationWidget(centerX, height / 2 - 40, new ItemStack(targetItem));
        addSelectableChild(revealWidget);
        
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        boolean manualTimeEnabled = RandomRunMod.getInstance().getConfig().isManualTimeEnabled();
        boolean askForSeed = RandomRunMod.getInstance().getConfig().isAskForCustomSeed();
        boolean modifiersEnabled = RandomRunMod.getInstance().getConfig().isModifiersEnabled();
        
        Text startButtonText = modifiersEnabled ? Text.translatable("randomrun.button.reveal_modifier") : Text.translatable("randomrun.button.start_speedrun");
        
        if (timeChallengeEnabled) {
            if (manualTimeEnabled) {
                
                addDrawableChild(new ButtonDefault(
                    centerX - 100, buttonY,
                    200, 20,
                    startButtonText,
                    button -> {
                        int manualTimeSeconds = RandomRunMod.getInstance().getConfig().getManualTimeSeconds();
                        if (askForSeed) {
                            MinecraftClient.getInstance().setScreen(new SeedInputScreen(this, targetItem, manualTimeSeconds * 1000L));
                        } else {
                            startSpeedrun(manualTimeSeconds * 1000L);
                        }
                    }
                ));
            } else {
                
                addDrawableChild(new ButtonDefault(
                    centerX - 100, buttonY,
                    200, 20,
                    Text.translatable("randomrun.button.select_time"),
                    button -> MinecraftClient.getInstance().setScreen(new TimeSelectionScreen(this, targetItem))
                ));
            }
        } else {
            
            addDrawableChild(new ButtonDefault(
                centerX - 100, buttonY,
                200, 20,
                startButtonText,
                button -> {
                    if (askForSeed) {
                        MinecraftClient.getInstance().setScreen(new SeedInputScreen(this, targetItem, 0));
                    } else {
                        startSpeedrun(0);
                    }
                }
            ));
        }
        
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30, 
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
    }
    
    public void startSpeedrun(long timeLimitMs) {
        startSpeedrunWithSeed(timeLimitMs, null);
    }

    public void startSpeedrunWithSeed(long timeLimitMs, String seed) {
        // Start run in manager
        // Запуск забега в менеджере
        RandomRunMod.getInstance().getRunDataManager().startNewRun(targetItem, timeLimitMs, "pending");
        
        // Check if modifiers are enabled
        // Проверка, включены ли модификаторы
        if (RandomRunMod.getInstance().getConfig().isModifiersEnabled()) {
             // Open LootBox animation
             // Открытие анимации лутбокса
             RandomRunMod.LOGGER.info("Модификаторы включены, открываем LootBoxScreen...");
             MinecraftClient.getInstance().setScreen(new LootBoxScreen(() -> {
                 RandomRunMod.LOGGER.info("LootBoxScreen завершен, создание мира...");
                 createWorld(targetItem, timeLimitMs, seed);
             }));
        } else {
            RandomRunMod.LOGGER.info("Модификаторы выключены, создаем мир напрямую...");
            createWorld(targetItem, timeLimitMs, seed);
        }
    }
    
    private void createWorld(net.minecraft.item.Item item, long timeLimitMs, String seed) {
        if (seed != null && !seed.isEmpty()) {
            WorldCreator.createSpeedrunWorld(item, timeLimitMs, seed);
        } else {
            WorldCreator.createSpeedrunWorld(item, timeLimitMs, null);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealWidget != null && revealWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (revealWidget != null && revealWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (revealWidget != null && revealWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderGradientBackground(context);
        
        if (revealWidget != null) {
            revealWidget.render(context, mouseX, mouseY, delta);
        }
       
        String itemName = targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, itemName, width / 2, height / 2 + 40, 0xFFFFFF);
        
       
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        boolean useDifficulty = RandomRunMod.getInstance().getConfig().isUseItemDifficulty();
        
        if (timeChallengeEnabled && useDifficulty) {
            
            String difficultyText = difficulty.displayName;
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.difficulty", difficultyText), 
                width / 2, height / 2 + 65, difficulty.color);
            
           
            String timeRange = difficulty.getTimeRange();
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.translatable("randomrun.time_range", timeRange), 
                width / 2, height / 2 + 80, 0xAAAAAA);
        }
        
       
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.drag_to_rotate"), 
            width / 2, height / 2 + 50, 0x666666); 
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void close() {
        
        if (parent instanceof SpeedrunScreen) {
            SpeedrunScreen speedrunScreen = (SpeedrunScreen) parent;
            MinecraftClient.getInstance().setScreen(speedrunScreen);
        } else {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

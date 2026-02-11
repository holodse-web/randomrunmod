package com.randomrun.challenges.classic.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.challenges.advancement.screen.AchievementRevealScreen;

public class SeedInputScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Item targetItem;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    private final long timeLimitMs;
    private TextFieldWidget seedField;
    
    public SeedInputScreen(Screen parent, Item targetItem, long timeLimitMs) {
        super(Text.translatable("randomrun.seed_input.title"));
        this.parent = parent;
        this.targetItem = targetItem;
        this.targetAdvancement = null;
        this.timeLimitMs = timeLimitMs;
    }
    
    public SeedInputScreen(Screen parent, AdvancementLoader.AdvancementInfo advancement, long timeLimitMs) {
        super(Text.translatable("randomrun.seed_input.title"));
        this.parent = parent;
        this.targetItem = null;
        this.targetAdvancement = advancement;
        this.timeLimitMs = timeLimitMs;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Seed input field
        seedField = new TextFieldWidget(textRenderer, centerX - 100, centerY - 10, 200, 20, Text.translatable("randomrun.seed_input.placeholder"));
        seedField.setPlaceholder(Text.translatable("randomrun.seed_input.placeholder"));
        seedField.setMaxLength(100);
        seedField.setText(RandomRunMod.getInstance().getConfig().getCustomSeed());
        addDrawableChild(seedField);
        setInitialFocus(seedField);
        
        // Use seed button
        // Кнопка использования сида
        addDrawableChild(new ButtonDefault(
            centerX - 100, centerY + 20,
            200, 20,
            Text.translatable("randomrun.button.use_seed"),
            button -> {
                String seed = seedField.getText().trim();
                if (!seed.isEmpty()) {
                    RandomRunMod.getInstance().getConfig().setCustomSeed(seed);
                    RandomRunMod.getInstance().saveConfig();
                }
                
                if (parent instanceof ItemRevealScreen itemRevealScreen) {
                    itemRevealScreen.startSpeedrunWithSeed(timeLimitMs, seed);
                } else if (parent instanceof AchievementRevealScreen achievementRevealScreen) {
                    achievementRevealScreen.startSpeedrunWithSeed(timeLimitMs, seed);
                } else if (parent instanceof com.randomrun.challenges.time.screen.TimeSelectionScreen timeSelectionScreen) {
                    // Handle TimeSelectionScreen parent (which delegates back to its parent usually, but we can't easily access it)
                    // Actually, TimeSelectionScreen should probably pass the RevealScreen as parent to SeedInputScreen?
                    // No, TimeSelectionScreen is the current screen.
                    // We need to call start logic on TimeSelectionScreen
                    if (targetAdvancement != null) {
                        timeSelectionScreen.startAdvancementSpeedrunWithTime((int)(timeLimitMs / 1000), seed);
                    } else {
                        timeSelectionScreen.startSpeedrunWithTime((int)(timeLimitMs / 1000), seed);
                    }
                }
            },
            0, 0.1f
        ));
        
        // Back button (aligned with main menu)
        // Кнопка назад (выровнена с главным меню)
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        // Заголовок
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.seed_input.title"), 
            width / 2, height / 2 - 40, 0xFFFFFF);
        
        // Item/Advancement name
        // Имя предмета/достижения
        String name = targetAdvancement != null ? targetAdvancement.title.getString() : targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§d" + name),
            width / 2, height / 2 - 55, 0xD946FF);
    }
}

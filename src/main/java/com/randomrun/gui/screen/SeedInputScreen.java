package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

public class SeedInputScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Item targetItem;
    private final long timeLimitMs;
    private TextFieldWidget seedField;
    
    public SeedInputScreen(Screen parent, Item targetItem, long timeLimitMs) {
        super(Text.translatable("randomrun.seed_input.title"));
        this.parent = parent;
        this.targetItem = targetItem;
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
        addDrawableChild(new StyledButton2(
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
                }
            },
            0, 0.1f
        ));
        
        // Back button (aligned with main menu)
        addDrawableChild(new StyledButton2(
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
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.seed_input.title"), 
            width / 2, height / 2 - 40, 0xFFFFFF);
        
        // Item name
        String itemName = targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§d" + itemName),
            width / 2, height / 2 - 55, 0xD946FF);
    }
}

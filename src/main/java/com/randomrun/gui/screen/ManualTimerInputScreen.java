package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.config.ModConfig;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

public class ManualTimerInputScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Item targetItem; 
    
    private TextFieldWidget minutesField;
    private TextFieldWidget secondsField;
    private String errorMessage = null;
    
    private static final int MIN_SECONDS = 30;
    private static final int MAX_SECONDS = 7200; 
    
    public ManualTimerInputScreen(Screen parent, Item targetItem) {
        super(Text.translatable("randomrun.screen.manual_timer.title"));
        this.parent = parent;
        this.targetItem = targetItem;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int inputY = height / 2 - 30;
        
        ModConfig config = RandomRunMod.getInstance().getConfig();
        int currentSeconds = config.getChallengeDuration();
        int currentMinutes = currentSeconds / 60;
        int currentSecs = currentSeconds % 60;
        
        
        minutesField = new TextFieldWidget(textRenderer, centerX - 60, inputY, 50, 20, Text.literal("Minutes"));
        minutesField.setText(String.valueOf(currentMinutes));
        minutesField.setMaxLength(3);
        minutesField.setChangedListener(this::validateInput);
        addDrawableChild(minutesField);
        
        
        secondsField = new TextFieldWidget(textRenderer, centerX + 10, inputY, 50, 20, Text.literal("Seconds"));
        secondsField.setText(String.format("%02d", currentSecs));
        secondsField.setMaxLength(2);
        secondsField.setChangedListener(this::validateInput);
        addDrawableChild(secondsField);
        
        
        addDrawableChild(new StyledButton2(
            centerX - 100, inputY + 50,
            200, 20,
            Text.translatable("randomrun.button.save"),
            button -> saveTime(),
            0, 0.1f
        ));
        
       
        addDrawableChild(new StyledButton2(
            centerX - 100, inputY + 80,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f
        ));
    }
    
    private void validateInput(String text) {
        errorMessage = null;
        
        try {
            int minutes = minutesField.getText().isEmpty() ? 0 : Integer.parseInt(minutesField.getText());
            int seconds = secondsField.getText().isEmpty() ? 0 : Integer.parseInt(secondsField.getText());
            int totalSeconds = minutes * 60 + seconds;
            
            if (totalSeconds < MIN_SECONDS) {
                errorMessage = Text.translatable("randomrun.error.time_too_short").getString();
            } else if (totalSeconds > MAX_SECONDS) {
                errorMessage = Text.translatable("randomrun.error.time_too_long").getString();
            }
        } catch (NumberFormatException e) {
            errorMessage = Text.translatable("randomrun.error.invalid_number").getString();
        }
    }
    
    private void saveTime() {
        try {
            int minutes = minutesField.getText().isEmpty() ? 0 : Integer.parseInt(minutesField.getText());
            int seconds = secondsField.getText().isEmpty() ? 0 : Integer.parseInt(secondsField.getText());
            int totalSeconds = minutes * 60 + seconds;
            
            if (totalSeconds < MIN_SECONDS || totalSeconds > MAX_SECONDS) {
                return;
            }
            
            ModConfig config = RandomRunMod.getInstance().getConfig();
            config.setChallengeDuration(totalSeconds);
            RandomRunMod.getInstance().saveConfig();
            
            if (targetItem != null && parent instanceof TimeSelectionScreen timeScreen) {
                
                timeScreen.startSpeedrunWithTime(totalSeconds);
            } else {
                
                MinecraftClient.getInstance().setScreen(parent);
            }
        } catch (NumberFormatException e) {
            errorMessage = Text.translatable("randomrun.error.invalid_number").getString();
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderGradientBackground(context);
        
       
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 30, 0xFFFFFF);
        
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("randomrun.manual_timer.instructions"),
            width / 2, 60, 0xAAAAAA);
        
       
        int inputY = height / 2 - 30;
        context.drawTextWithShadow(textRenderer, "Min:", width / 2 - 85, inputY + 6, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, ":", width / 2 - 5, inputY + 6, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Sec", width / 2 + 65, inputY + 6, 0xFFFFFF);
        
       
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("randomrun.manual_timer.range", "0:30", "2:00:00"),
            width / 2, inputY + 30, 0x888888);
        
        
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, inputY + 110, 0xFF5555);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
}

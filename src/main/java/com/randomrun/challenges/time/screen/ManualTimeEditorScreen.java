package com.randomrun.challenges.time.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ManualTimeEditorScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    
    private TextFieldWidget minutesField;
    private TextFieldWidget secondsField;
    
    public ManualTimeEditorScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.manual_time_editor.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int centerY = height / 2;
        
       
        int currentSeconds = config.getManualTimeSeconds();
        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;
        
       
        minutesField = new TextFieldWidget(textRenderer, centerX - 60, centerY - 30, 50, 20, Text.literal("Minutes"));
        minutesField.setText(String.valueOf(minutes));
        minutesField.setMaxLength(3);
        minutesField.setChangedListener(text -> validateInput());
        addDrawableChild(minutesField);
        
       
        secondsField = new TextFieldWidget(textRenderer, centerX + 10, centerY - 30, 50, 20, Text.literal("Seconds"));
        secondsField.setText(String.valueOf(seconds));
        secondsField.setMaxLength(2);
        secondsField.setChangedListener(text -> validateInput());
        addDrawableChild(secondsField);
        
      
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY + 20,
            200, 20,
            Text.translatable("randomrun.button.save"),
            button -> saveAndClose()
        ));
        
      
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY + 45,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
        
        setInitialFocus(minutesField);
    }
    
    private void validateInput() {
      
        String minutesText = minutesField.getText();
        if (!minutesText.isEmpty()) {
            try {
                int minutes = Integer.parseInt(minutesText);
                if (minutes < 0 || minutes > 120) {
                    minutesField.setText(String.valueOf(Math.max(0, Math.min(120, minutes))));
                }
            } catch (NumberFormatException e) {
                minutesField.setText("0");
            }
        }
        
     
        String secondsText = secondsField.getText();
        if (!secondsText.isEmpty()) {
            try {
                int seconds = Integer.parseInt(secondsText);
                if (seconds < 0 || seconds > 59) {
                    secondsField.setText(String.valueOf(Math.max(0, Math.min(59, seconds))));
                }
            } catch (NumberFormatException e) {
                secondsField.setText("0");
            }
        }
    }
    
    private void saveAndClose() {
        try {
            int minutes = minutesField.getText().isEmpty() ? 0 : Integer.parseInt(minutesField.getText());
            int seconds = secondsField.getText().isEmpty() ? 0 : Integer.parseInt(secondsField.getText());
            
            int totalSeconds = minutes * 60 + seconds;
            
          
            if (totalSeconds < 10) {
                totalSeconds = 10;
            }
            
            config.setManualTimeSeconds(totalSeconds);
            RandomRunMod.getInstance().saveConfig();
            
            close();
        } catch (NumberFormatException e) {
       
            close();
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
     
        context.drawCenteredTextWithShadow(textRenderer, title, centerX, 20, 0xFFFFFF);
        
     
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("randomrun.manual_time_editor.instructions"),
            centerX, centerY - 60, 0xAAAAAA);
        
     
        context.drawTextWithShadow(textRenderer, 
            Text.translatable("randomrun.manual_time_editor.minutes"), 
            centerX - 60, centerY - 45, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, 
            Text.translatable("randomrun.manual_time_editor.seconds"), 
            centerX + 10, centerY - 45, 0xFFFFFF);
        
       
        context.drawCenteredTextWithShadow(textRenderer, ":", centerX, centerY - 25, 0xFFFFFF);
        
       
        try {
            int minutes = minutesField.getText().isEmpty() ? 0 : Integer.parseInt(minutesField.getText());
            int seconds = secondsField.getText().isEmpty() ? 0 : Integer.parseInt(secondsField.getText());
            int totalSeconds = minutes * 60 + seconds;
            
            String preview = String.format("%s: %02d:%02d", 
                Text.translatable("randomrun.manual_time_editor.total").getString(),
                totalSeconds / 60, totalSeconds % 60);
            context.drawCenteredTextWithShadow(textRenderer, preview, centerX, centerY + 5, 0x55FF55);
        } catch (NumberFormatException e) {
  
        }
    }
    
    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}

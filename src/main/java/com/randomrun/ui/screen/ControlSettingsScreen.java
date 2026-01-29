package com.randomrun.ui.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ControlSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;
    
    private boolean isBinding = false;
    
    public ControlSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public ControlSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.control_settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = 60;
        int spacing = 30;
        
        // Start Method Button
        String methodText = config.getStartMethod() == ModConfig.StartMethod.COMMAND 
            ? "/go" 
            : Text.translatable("randomrun.control.keybind").getString();
            
        int methodButtonWidth = config.getStartMethod() == ModConfig.StartMethod.KEYBIND ? buttonWidth - 30 : buttonWidth;
        
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY,
            methodButtonWidth, buttonHeight,
            Text.translatable("randomrun.settings.start_method", methodText),
            button -> {
                ModConfig.StartMethod current = config.getStartMethod();
                config.setStartMethod(current == ModConfig.StartMethod.COMMAND 
                    ? ModConfig.StartMethod.KEYBIND 
                    : ModConfig.StartMethod.COMMAND);
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        // Pencil button for Keybind
        if (config.getStartMethod() == ModConfig.StartMethod.KEYBIND) {
            String keyName = InputUtil.fromKeyCode(config.getStartKey(), 0).getLocalizedText().getString();
            if (isBinding) {
                keyName = "> " + keyName + " <";
            }
            
            // We'll show the key name in the pencil button or just a pencil icon?
            // User said: "square small button with a pencil on the right"
            // I'll put a pencil emoji or icon.
            
            addDrawableChild(new StyledButton2(
                centerX + buttonWidth / 2 - 25, startY,
                25, buttonHeight,
                Text.literal("✏️"),
                button -> {
                    isBinding = !isBinding;
                    // If we just enabled binding, we need to repaint to show indicator
                },
                1, 0.15f, isRefreshing
            ));
            
            // Add label showing current key
            // We render this in render() method
        }
        
        // Back button
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, height - 30,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            2, 0.2f, isRefreshing
        ));
    }
    
    private void refreshScreen() {
        MinecraftClient.getInstance().setScreen(new ControlSettingsScreen(parent, true));
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isBinding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isBinding = false;
                return true;
            }
            
            config.setStartKey(keyCode);
            RandomRunMod.getInstance().saveConfig();
            isBinding = false;
            refreshScreen(); // Refresh to update key name display if we had one
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isBinding) {
            // Optional: allow binding to mouse buttons? Minecraft usually allows it.
            // For now, let's stick to keys as requested "key combination".
            // But if user clicks elsewhere, cancel binding?
            // Or maybe binding to mouse buttons is fine.
            // Let's cancel binding if clicked outside.
            // But the button click itself handles the toggle.
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        
        // If Keybind mode, show the current key below or next to it
        if (config.getStartMethod() == ModConfig.StartMethod.KEYBIND) {
            int centerX = width / 2;
            int startY = 60;
            
            String keyName = InputUtil.fromKeyCode(config.getStartKey(), 0).getLocalizedText().getString();
            int color = isBinding ? 0xFFFF00 : 0xAAAAAA;
            String text = isBinding 
                ? Text.translatable("randomrun.settings.press_key").getString() 
                : Text.translatable("randomrun.settings.current_key", keyName).getString();
                
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(text), centerX, startY + 25, color);
        }
    }
}

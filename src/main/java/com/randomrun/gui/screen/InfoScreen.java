package com.randomrun.gui.screen;

import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class InfoScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    
    public InfoScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.info.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent)
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        
       
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§e" + Text.translatable("randomrun.info.in_development").getString()), 
            width / 2, height / 2, 0xFFFF55);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
}

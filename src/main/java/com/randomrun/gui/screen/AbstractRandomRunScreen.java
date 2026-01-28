package com.randomrun.gui.screen;

import com.randomrun.gui.widget.GlobalParticleSystem;
import com.randomrun.gui.widget.ModInfoWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class AbstractRandomRunScreen extends Screen {
    
    protected ModInfoWidget modInfoWidget;
    
    protected AbstractRandomRunScreen(Text title) {
        super(title);
    }
    
    @Override
    protected void init() {
        super.init();
        
        GlobalParticleSystem.getInstance().updateScreenSize(width, height);
       
        modInfoWidget = new ModInfoWidget(width, height, textRenderer);
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        
       
        renderGradientBackground(context);
        
        
        GlobalParticleSystem.getInstance().update();
        GlobalParticleSystem.getInstance().render(context);
        
        
        super.render(context, mouseX, mouseY, delta);
        
       
        if (modInfoWidget != null) {
            modInfoWidget.render(context, mouseX, mouseY, delta);
        }
    }
    
    protected void renderGradientBackground(DrawContext context) {
       
        int topColor = 0xFF000000;    
        int middleColor = 0xFF4a148c;  
        int bottomColor = 0xFF1a1a2e;  
        
       
        context.fillGradient(0, 0, width, height / 2, topColor, middleColor);
       
        context.fillGradient(0, height / 2, width, height, middleColor, bottomColor);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
       
        if (modInfoWidget != null && modInfoWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.gui.widget.GlobalParticleSystem;
import com.randomrun.gui.widget.LanguageDropdownWidget;
import com.randomrun.gui.widget.StyledButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class MainModScreen extends AbstractRandomRunScreen {
    private static final Identifier LOGO_TEXTURE = new Identifier(RandomRunMod.MOD_ID, "textures/gui/logo.png");
    
    
    
    private final Screen parent;
    private float logoScale = 1.0f;
    private float targetLogoScale = 1.0f;
    private boolean logoHovered = false;
    private float fadeProgress = 0f;
    private long openTime;
    private static final float FADE_SPEED = 0.08f;
    
    
    private float borderAnimation = 0f;
    
   
    private static final int LOGO_WIDTH = 100;
    private static final int LOGO_HEIGHT = 50;
    
    public MainModScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.main.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        // Ensure language is correct on screen init
        com.randomrun.util.LanguageManager.ensureLanguage();
        
        super.init();
        openTime = System.currentTimeMillis();
        fadeProgress = 0f;
        
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = height / 2 - 10;
        int spacing = 25;
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
       
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.speedrun"),
            button -> MinecraftClient.getInstance().setScreen(new SpeedrunScreen(this)),
            0, 0.1f
        ));
        
      
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY + spacing,
            buttonWidth, buttonHeight,
            Text.literal(playerName),
            button -> MinecraftClient.getInstance().setScreen(new ResultsScreen(this)),
            1, 0.15f
        ));
        
     
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY + spacing * 2,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.settings"),
            button -> MinecraftClient.getInstance().setScreen(new SettingsScreen(this)),
            2, 0.2f
        ));
        
      
        addDrawableChild(new StyledButton(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            3, 0.25f
        ));
        
     
        addDrawableChild(new StyledButton(
            width - 30, height - 30,
            20, 20,
            Text.literal("ℹ"),
            button -> MinecraftClient.getInstance().setScreen(new InfoScreen(this)),
            4, 0.3f
        ));
        
    
        addDrawableChild(new LanguageDropdownWidget(
            10, height - 30,
            this
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    
        long elapsed = System.currentTimeMillis() - openTime;
        fadeProgress = Math.min(1.0f, elapsed / 1000.0f * (1.0f / FADE_SPEED));
        
   
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        
      
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        
        
        renderGradientBackground(context);
        
       
        GlobalParticleSystem.getInstance().update();
        GlobalParticleSystem.getInstance().render(context);
        
    
        borderAnimation += delta * 0.02f;
        if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        
     
        int logoX = width / 2 - LOGO_WIDTH / 2;
        int logoY = height / 2 - 95;
        int contentTop = logoY - 15;
        int contentBottom = height / 2 + 85;
        int contentLeft = width / 2 - 120;
        int contentRight = width / 2 + 120;
        
       
        for (int i = 0; i < 10; i++) {
            int shadowAlpha = (int) ((10 - i) * 2.5f * fadeProgress);
            context.fill(contentLeft - i, contentTop - i, contentRight + i, contentBottom + i, (shadowAlpha << 24));
        }
        
       
        int bgAlpha = (int) (0x45 * fadeProgress); 
        int purpleTint = 0x1a0a2e; 
        int r = (purpleTint >> 16) & 0xFF;
        int g = (purpleTint >> 8) & 0xFF;
        int b = purpleTint & 0xFF;
        context.fill(contentLeft, contentTop, contentRight, contentBottom, (bgAlpha << 24) | (r << 16) | (g << 8) | b);
        
       
        renderAnimatedBorder(context, contentLeft, contentTop, contentRight, contentBottom, 2);
        
        
        logoHovered = mouseX >= logoX && mouseX <= logoX + LOGO_WIDTH &&
                      mouseY >= logoY && mouseY <= logoY + LOGO_HEIGHT;
        targetLogoScale = logoHovered ? 1.08f : 1.0f;
        logoScale = MathHelper.lerp(delta * 0.2f, logoScale, targetLogoScale);
        renderLogo(context, logoX, logoY);
        
        
        this.children().forEach(child -> {
            if (child instanceof net.minecraft.client.gui.Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        });
        
        
        if (modInfoWidget != null) {
            modInfoWidget.render(context, mouseX, mouseY, delta);
        }
        
       
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    
    private void renderLogo(DrawContext context, int x, int y) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        
        context.getMatrices().push();
        
    
        float centerX = x + LOGO_WIDTH / 2f;
        float centerY = y + LOGO_HEIGHT / 2f;
        
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(logoScale, logoScale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);
        
      
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fadeProgress);
        
      
        try {
            context.drawTexture(LOGO_TEXTURE, x, y, 0, 0, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        } catch (Exception e) {
         
            String title = "RandomRun";
            context.drawCenteredTextWithShadow(textRenderer, title, 
                (int)centerX, (int)centerY, 0x6930c3);
        }
        
        context.getMatrices().pop();
    }
    
    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, int borderWidth) {
        int perimeter = 2 * (right - left) + 2 * (bottom - top);
        
        for (int i = 0; i < perimeter; i += 2) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            int color = lerpBorderColor(progress);
            
            int x, y;
            if (i < (right - left)) {
                x = left + i;
                y = top;
            } else if (i < (right - left) + (bottom - top)) {
                x = right;
                y = top + (i - (right - left));
            } else if (i < 2 * (right - left) + (bottom - top)) {
                x = right - (i - (right - left) - (bottom - top));
                y = bottom;
            } else {
                x = left;
                y = bottom - (i - 2 * (right - left) - (bottom - top));
            }
            
            context.fill(x, y, x + borderWidth, y + borderWidth, color);
        }
    }
    
    private int lerpBorderColor(float t) {
        int alpha = (int)(fadeProgress * 255) << 24;
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        int r = (int) MathHelper.lerp(factor, 105, 255);
        int g = (int) MathHelper.lerp(factor, 48, 255);
        int b = (int) MathHelper.lerp(factor, 195, 255);
        return alpha | (r << 16) | (g << 8) | b;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
     
        if (modInfoWidget != null && modInfoWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

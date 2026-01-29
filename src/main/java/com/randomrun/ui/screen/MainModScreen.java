package com.randomrun.ui.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.LanguageDropdownWidget;
import com.randomrun.ui.widget.ModInfoWidget;
import com.randomrun.ui.widget.StyledButton;
import com.randomrun.ui.widget.ModInfoWidget;
import com.randomrun.ui.screen.ResultsScreen;
import com.randomrun.challenges.advancement.screen.AchievementSelectionScreen;
import com.randomrun.challenges.classic.screen.SpeedrunScreen;
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
        com.randomrun.main.util.LanguageManager.ensureLanguage();
        
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
            button -> {
                if (RandomRunMod.getInstance().getConfig().isAchievementChallengeEnabled()) {
                    MinecraftClient.getInstance().setScreen(new AchievementSelectionScreen(this));
                } else {
                    MinecraftClient.getInstance().setScreen(new SpeedrunScreen(this));
                }
            },
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
        
        addDrawableChild(new LanguageDropdownWidget(
            10, height - 30,
            this
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long elapsed = System.currentTimeMillis() - openTime;
        fadeProgress = Math.min(1.0f, elapsed / 1000.0f * (1.0f / FADE_SPEED));
        
        borderAnimation += delta * 0.02f;
        if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        
        // Render background and widgets via super.render
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render base background (gradient, particles)
        super.renderBackground(context, mouseX, mouseY, delta);
        
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        
        int logoX = width / 2 - LOGO_WIDTH / 2;
        int logoY = height / 2 - 95;
        int contentTop = logoY - 15;
        int contentBottom = height / 2 + 85;
        int contentLeft = width / 2 - 120;
        int contentRight = width / 2 + 120;
        
        // Shadow
        for (int i = 0; i < 10; i++) {
            int shadowAlpha = (int) ((10 - i) * 2.5f * fadeProgress);
            context.fill(contentLeft - i, contentTop - i, contentRight + i, contentBottom + i, (shadowAlpha << 24));
        }
        
        // Background panel
        int bgAlpha = (int) (0x45 * fadeProgress); 
        int purpleTint = 0x1a0a2e; 
        int r = (purpleTint >> 16) & 0xFF;
        int g = (purpleTint >> 8) & 0xFF;
        int b = purpleTint & 0xFF;
        context.fill(contentLeft, contentTop, contentRight, contentBottom, (bgAlpha << 24) | (r << 16) | (g << 8) | b);
        
        // Border
        renderAnimatedBorder(context, contentLeft, contentTop, contentRight, contentBottom, 2);
        
        // Logo
        logoHovered = mouseX >= logoX && mouseX <= logoX + LOGO_WIDTH &&
                      mouseY >= logoY && mouseY <= logoY + LOGO_HEIGHT;
        targetLogoScale = logoHovered ? 1.08f : 1.0f;
        logoScale = MathHelper.lerp(delta * 0.2f, logoScale, targetLogoScale);
        renderLogo(context, logoX, logoY);
        
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
    
    public static void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, int borderWidth) {
        float time = (System.currentTimeMillis() % 2000) / 2000f;
        
        // Draw 4 solid rectangles for the border
        int color = getStaticBorderColor(time);
        
        // Top
        context.fill(left, top, right, top + borderWidth, color);
        // Bottom
        context.fill(left, bottom - borderWidth, right, bottom, color);
        // Left
        context.fill(left, top, left + borderWidth, bottom, color);
        // Right
        context.fill(right - borderWidth, top, right, bottom, color);
    }
    
    private static int getStaticBorderColor(float t) {
        int alpha = 255 << 24;
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        int r = (int) MathHelper.lerp(factor, 105, 255);
        int g = (int) MathHelper.lerp(factor, 48, 255);
        int b = (int) MathHelper.lerp(factor, 195, 255);
        return alpha | (r << 16) | (g << 8) | b;
    }
    
    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, int borderWidth, boolean instance) {
         // Instance version redirect
         renderAnimatedBorder(context, left, top, right, bottom, borderWidth);
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

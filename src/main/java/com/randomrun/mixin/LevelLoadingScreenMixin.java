package com.randomrun.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.main.RandomRunMod;
import com.randomrun.ui.screen.loading.LoadingTips;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.randomrun.ui.widget.GlobalParticleSystem;
import net.minecraft.client.render.RenderLayer;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    private static final Identifier LOGO_TEXTURE = Identifier.of(RandomRunMod.MOD_ID, "textures/gui/logo.png");
    private boolean tipsInitialized = false;

    protected LevelLoadingScreenMixin(Text title) {
        super(title);
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (WorldCreator.isSpeedrunLoading() || WorldCreator.hasPendingRun()) {
            renderCustomBackground(context, mouseX, mouseY, delta);
        } else {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }
    
    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (WorldCreator.isSpeedrunLoading() || WorldCreator.hasPendingRun()) {
            renderTips(context);
        }
    }

    private void renderCustomBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Gradient Background (Deep Purple/Black)
        context.fillGradient(0, 0, width, height, 0xFF0f0518, 0xFF2a0f3d);
        
        // Particles
        GlobalParticleSystem.getInstance().update();
        GlobalParticleSystem.getInstance().render(context);
        
        // Logo
        renderLogo(context);
    }
    
    private void renderLogo(DrawContext context) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        int logoWidth = 100;
        int logoHeight = 50;
        int x = (width - logoWidth) / 2;
        int y = 20; // Margin from top
        
        context.drawTexture(RenderLayer::getGuiTextured, LOGO_TEXTURE, x, y, 0.0f, 0.0f, logoWidth, logoHeight, logoWidth, logoHeight);
        
        RenderSystem.disableBlend();
    }
    
    private void renderTips(DrawContext context) {
        if (!tipsInitialized) {
            LoadingTips.refreshTip();
            tipsInitialized = true;
        }
        
        String tip = LoadingTips.getCurrentTip();
        int y = height - 40;
        
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(tip), width / 2, y, 0xFFFFFF);
    }
}

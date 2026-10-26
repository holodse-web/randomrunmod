package com.randomrun.mixin.ui;

import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.loading.LoadingTips;
import com.randomrun.ui.widget.GlobalParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @org.spongepowered.asm.mixin.Shadow
    private net.minecraft.client.multiplayer.LevelLoadTracker loadTracker;

    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(RandomRunMod.MOD_ID, "textures/gui/logo.png");
    
    private static final int LOGO_WIDTH = 100;
    private static final int LOGO_HEIGHT = 50;
    private static final int PROGRESS_BAR_WIDTH = 300;
    private static final int PROGRESS_BAR_HEIGHT = 4;
    private static final float TIP_FADE_DURATION = 800f;
    
    private static final int COLOR_PROGRESS_BG = 0x40000000;
    private static final int COLOR_PROGRESS_FILL = 0xFF6930c3;
    private static final int COLOR_PROGRESS_GLOW = 0x806930c3;
    private static final int COLOR_VERSION = 0x80FFFFFF;
    
    private boolean tipsInitialized = false;
    private long loadingStartTime = 0;
    private String currentTip = "";
    private String previousTip = "";
    private long tipChangeTime = 0;
    private float simulatedProgress = 0f;
    private boolean completionSoundPlayed = false;

    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }
    
    @Override
    protected void init() {
        super.init();
        loadingStartTime = System.currentTimeMillis();
        GlobalParticleSystem.getInstance().updateScreenSize(width, height);
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (WorldCreator.isSpeedrunLoading() || WorldCreator.hasPendingRun()) {
            renderCustomBackground(context, mouseX, mouseY, delta);
        } else {
            super.extractBackground(context, mouseX, mouseY, delta);
        }
    }
    
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    public void onRenderHead(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (WorldCreator.isSpeedrunLoading() || WorldCreator.hasPendingRun()) {
            renderCustomBackground(context, mouseX, mouseY, delta);
            updateRealProgress();
            renderLoadingUI(context, delta);
            ci.cancel();
        }
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && (WorldCreator.isSpeedrunLoading() || WorldCreator.hasPendingRun())) {
            WorldCreator.getInstance().cancelCreation();
            if (minecraft != null) {
                minecraft.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
            }
            return true;
        }
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }

    private void renderCustomBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF0f0518);
        
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.update(delta * (1 + simulatedProgress));
        particleSystem.extractRenderState(context);
        
        renderStaticLogo(context);
    }
    
    private void renderStaticLogo(GuiGraphicsExtractor context) {
        int x = (width - LOGO_WIDTH) / 2;
        int y = 20;
        
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, x, y, 0.0f, 0.0f, 
            LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
    }
    
    private void renderLoadingUI(GuiGraphicsExtractor context, float delta) {
        renderWorldInfo(context);
        renderProgressBar(context);
        renderLoadingStage(context);
        renderTips(context);
        renderVersion(context);
        renderCancelHint(context);
    }
    
    private void renderWorldInfo(GuiGraphicsExtractor context) {
        Item targetItem = WorldCreator.getPendingTargetItem();
        if (targetItem == null) return;
        
        String itemName = net.minecraft.network.chat.Component.translatable(targetItem.getDescriptionId()).getString();
        String seed = WorldCreator.getPendingSeed();
        
        int centerX = width / 2;
        int y = 90;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            Component.literal("§d" + itemName), centerX, y, 0xFFFFFF);
        
        if (seed != null && !seed.isEmpty()) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal("§7Сид: §f" + seed), centerX, y + 15, 0xAAAAAA);
        }
    }
    
    private void renderProgressBar(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int y = height / 2 - 20;
        int barX = centerX - PROGRESS_BAR_WIDTH / 2;
        
        context.fill(barX - 2, y - 2, barX + PROGRESS_BAR_WIDTH + 2, y + PROGRESS_BAR_HEIGHT + 2, COLOR_PROGRESS_BG);
        
        int fillWidth = (int) (PROGRESS_BAR_WIDTH * simulatedProgress);
        if (fillWidth > 0) {
            context.fill(barX, y, barX + fillWidth, y + PROGRESS_BAR_HEIGHT, COLOR_PROGRESS_FILL);
            
            context.fill(barX, y - 1, barX + fillWidth, y, COLOR_PROGRESS_GLOW);
            context.fill(barX, y + PROGRESS_BAR_HEIGHT, barX + fillWidth, y + PROGRESS_BAR_HEIGHT + 1, COLOR_PROGRESS_GLOW);
        }
        
        String percent = String.format("§l%d%%", (int) (simulatedProgress * 100));
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(percent), centerX, y + 10, 0xFFFFFF);
    }
    
    private void renderLoadingStage(GuiGraphicsExtractor context) {
        String stage = getLoadingStage();
        
        int dots = (int) ((System.currentTimeMillis() / 500) % 4);
        String dotString = ".".repeat(dots);
        
        int centerX = width / 2;
        int y = height / 2 + 10;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            Component.literal("§7" + stage + dotString), centerX, y, 0xAAAAAA);
    }
    
    private String getLoadingStage() {
        if (simulatedProgress < 0.2f) return "Инициализация мира";
        if (simulatedProgress < 0.4f) return "Генерация ландшафта";
        if (simulatedProgress < 0.6f) return "Загрузка структур";
        if (simulatedProgress < 0.8f) return "Подготовка чанков";
        if (simulatedProgress < 0.95f) return "Финализация";
        return "Почти готово";
    }
    
    private void renderTips(GuiGraphicsExtractor context) {
        if (!tipsInitialized) {
            LoadingTips.refreshTip();
            currentTip = LoadingTips.getCurrentTip();
            previousTip = currentTip;
            tipChangeTime = System.currentTimeMillis();
            tipsInitialized = true;
        }
        
        String newTip = LoadingTips.getCurrentTip();
        if (!newTip.equals(currentTip)) {
            previousTip = currentTip;
            currentTip = newTip;
            tipChangeTime = System.currentTimeMillis();
        }
        
        int centerX = width / 2;
        int y = height - 60;
        
        long elapsed = System.currentTimeMillis() - tipChangeTime;
        float halfDuration = TIP_FADE_DURATION / 2f;
        
        if (elapsed < halfDuration) {
            float fadeOut = 1.0f - (elapsed / halfDuration);
            renderTipWithAlpha(context, previousTip, centerX, y, fadeOut);
        } else if (elapsed < TIP_FADE_DURATION) {
            float fadeIn = (elapsed - halfDuration) / halfDuration;
            renderTipWithAlpha(context, currentTip, centerX, y, fadeIn);
        } else {
            com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(currentTip), centerX, y, 0xFFFFFF);
        }
    }
    
    private void renderTipWithAlpha(GuiGraphicsExtractor context, String tip, int x, int y, float alpha) {
        int alphaInt = (int) (alpha * 255);
        int color = (alphaInt << 24) | 0xFFFFFF;
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(tip), x, y, color);
    }
    
    private void renderVersion(GuiGraphicsExtractor context) {
        String version = "§7v" + RandomRunMod.MOD_VERSION;
        int x = width - font.width(version) - 10;
        com.randomrun.ui.util.GuiText.text(context, font, Component.literal(version), x, height - 20, COLOR_VERSION);
    }
    
    private void renderCancelHint(GuiGraphicsExtractor context) {
        String hint = "§7ESC - отменить";
        com.randomrun.ui.util.GuiText.text(context, font, Component.literal(hint), 10, 10, 0xAAAAAA);
    }
    
    private void updateRealProgress() {
        float targetProgress = getRealLoadingProgress();
        
        if (simulatedProgress < targetProgress) {
            simulatedProgress += (targetProgress - simulatedProgress) * 0.15f;
            if (targetProgress - simulatedProgress < 0.002f) {
                simulatedProgress = targetProgress;
            }
        }
        
        simulatedProgress = Math.min(1.0f, simulatedProgress);
        
        if (simulatedProgress >= 1.0f && !completionSoundPlayed) {
            playCompletionSound();
            completionSoundPlayed = true;
        }
    }
    
    private float getRealLoadingProgress() {
        if (loadTracker.isLevelReady()) return 1f;
        if (loadTracker.hasProgress()) {
            return Math.max(0f, Math.min(1f, loadTracker.serverProgress()));
        }
        long elapsed = System.currentTimeMillis() - loadingStartTime;
        float timeProgress = 1.0f - (float) Math.exp(-elapsed / 8000.0);
        return Math.min(0.98f, timeProgress);
    }

    private void playCompletionSound() {
        try {
            if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, volume * 0.5f
                    )
                );
            }
        } catch (Exception e) {
        }
    }
}

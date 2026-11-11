package com.randomrun.mixin.ui;

import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.ui.overlay.LoadingCurtain;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @Shadow
    private LevelLoadTracker loadTracker;

    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void randomrun$coverLoading(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LoadingCurtain.isCovering()) return;
        if (loadTracker != null) {
            if (loadTracker.isLevelReady()) LoadingCurtain.reportWorldProgress(1f);
            else if (loadTracker.hasProgress()) LoadingCurtain.reportWorldProgress(loadTracker.serverProgress());
        }
        context.fill(0, 0, width, height, 0xFF000000);
        ci.cancel();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && WorldCreator.isSpeedrunLoading()) {
            WorldCreator.getInstance().cancelCreation();
            if (minecraft != null) minecraft.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
            return true;
        }
        return super.keyPressed(event);
    }
}

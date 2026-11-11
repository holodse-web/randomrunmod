package com.randomrun.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.randomrun.ui.overlay.LoadingCurtain;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiOverlayMixin {
    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
    private void randomrun$extractCurtain(GuiGraphicsExtractor graphics, Window window, Operation<Void> original) {
        graphics.nextStratum();
        com.randomrun.ui.widget.overlay.CustomNotification.renderOverlay(graphics);
        LoadingCurtain.extract(graphics);
        original.call(graphics, window);
    }
}

package com.randomrun.mixin;

import com.randomrun.ui.overlay.LoadingCurtain;
import com.randomrun.ui.util.TooltipStyle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In-game overlay: loading curtain, notifications, tooltips and HUD hiding. */
public final class HudMixins {
    private HudMixins() {}

    @Mixin(Gui.class)
    public static abstract class GuiOverlayMixin {
        @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
        private void randomrun$extractCurtain(GuiGraphicsExtractor graphics, Window window, Operation<Void> original) {
            graphics.nextStratum();
            com.randomrun.ui.overlay.CustomNotification.renderOverlay(graphics);
            LoadingCurtain.extract(graphics);
            original.call(graphics, window);
        }
    }

    @Mixin(Hud.class)
    public static class EndgameHudHiderMixin {
        @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
        private void randomrun$hideHudForUpgrader(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen()
                    instanceof com.randomrun.features.upgrader.client.UpgraderScreen) {
                ci.cancel();
            }
        }

        @Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)
        private void randomrun$hideDebugForUpgrader(GuiGraphicsExtractor context, CallbackInfo ci) {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen()
                    instanceof com.randomrun.features.upgrader.client.UpgraderScreen) {
                ci.cancel();
            }
        }
        @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
        private void onRenderHotbarHead(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();

            if (client.gui.screen() instanceof com.randomrun.ui.screen.endgame.VictoryScreen ||
                client.gui.screen() instanceof com.randomrun.ui.screen.endgame.EndgameScreen.DefeatScreen ||
                client.gui.screen() instanceof com.randomrun.ui.screen.endgame.EndgameScreen.DrawScreen) {
                ci.cancel();
                return;
            }
        }
    }

    @Mixin(TooltipRenderUtil.class)
    public static class TooltipStyleMixin {
        @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
        private static void randomrun$themedTooltip(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style, CallbackInfo ci) {
            if (TooltipStyle.draw(graphics, x, y, width, height, style)) ci.cancel();
        }
    }

    @Mixin(GuiGraphicsExtractor.class)
    public interface GuiGraphicsAccessor {
        @Invoker("innerBlit")
        void randomrun$innerBlit(RenderPipeline pipeline, GpuTextureView view, GpuSampler sampler,
                                 int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color);
    }
}

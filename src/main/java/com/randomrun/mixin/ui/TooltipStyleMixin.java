package com.randomrun.mixin.ui;

import com.randomrun.ui.util.TooltipStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
public class TooltipStyleMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void randomrun$themedTooltip(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style, CallbackInfo ci) {
        if (TooltipStyle.draw(graphics, x, y, width, height, style)) ci.cancel();
    }
}

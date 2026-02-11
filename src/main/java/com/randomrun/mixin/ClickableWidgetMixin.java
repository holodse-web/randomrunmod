package com.randomrun.mixin;

import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
    
    @Shadow
    protected int width;
    
    @Shadow
    protected int height;
    
    @Shadow
    public abstract int getX();
    
    @Shadow
    public abstract int getY();
}

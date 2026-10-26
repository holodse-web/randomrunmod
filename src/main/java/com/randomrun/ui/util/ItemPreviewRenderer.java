package com.randomrun.ui.util;

import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemPreviewRenderer {
    private ItemPreviewRenderer() {}

    public static void register() {
        PictureInPictureRendererRegistry.register(context -> new LargeItemPreviewRenderer());
    }

    public static void extractLargeGui(GuiGraphicsExtractor graphics, ItemStack stack,
                                       float x, float y, float itemSizeGui) {
        extract(graphics, stack, x, y, itemSizeGui, 0, 0, ItemDisplayContext.GUI);
    }

    public static void extract(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y,
                               float scale, float rotationX, float rotationY, ItemDisplayContext displayContext) {
        LargeItemPreviewRenderer.extract(graphics, stack, x, y, scale, rotationX, rotationY, displayContext);
    }
}

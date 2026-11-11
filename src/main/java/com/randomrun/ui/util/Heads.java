package com.randomrun.ui.util;

import com.randomrun.infrastructure.util.PlayerSkinHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class Heads {
    private Heads() {}

    public static void draw(GuiGraphicsExtractor context, String name, int x, int y, int size, int frameArgb) {
        if (name == null || name.isEmpty()) return;
        Identifier skin = PlayerSkinHelper.getPlayerSkin(name, PlayerSkinHelper.getOfflineUUID(name));
        if (frameArgb != 0) Ui.border(context, x - 1, y - 1, x + size + 1, y + size + 1, 1, frameArgb);
        context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
        context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
    }
}

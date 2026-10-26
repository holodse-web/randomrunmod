package com.randomrun.features.upgrader.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.ArrayList;
import java.util.List;

public final class UpgraderPixelMesh {
    public record Quad(int x0, int y0, int x1, int y1, int colorIndex) {}

    private final List<Quad> quads;
    private final ScreenRectangle localBounds;

    public UpgraderPixelMesh(List<Quad> quads) {
        this.quads = List.copyOf(quads);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (Quad quad : quads) {
            x0 = Math.min(x0, quad.x0());
            y0 = Math.min(y0, quad.y0());
            x1 = Math.max(x1, quad.x1());
            y1 = Math.max(y1, quad.y1());
        }
        localBounds = quads.isEmpty() ? ScreenRectangle.empty() : new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
    }

    public void extract(GuiGraphicsExtractor graphics, int color0, int color1) {
        if (quads.isEmpty()) return;
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.scissorStack.peek();
        ScreenRectangle bounds = localBounds.transformMaxBounds(pose);
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null) return;
        graphics.guiRenderState.addGuiElement(new State(quads, pose, scissor, bounds, color0, color1));
    }

    public static final class Builder {
        private final List<Quad> quads = new ArrayList<>();

        public void fill(int x0, int y0, int x1, int y1, int colorIndex) {
            if (x1 <= x0 || y1 <= y0) return;
            if (!quads.isEmpty()) {
                Quad last = quads.getLast();
                if (last.x0() == x0 && last.x1() == x1 && last.y1() == y0 && last.colorIndex() == colorIndex) {
                    quads.set(quads.size() - 1, new Quad(x0, last.y0(), x1, y1, colorIndex));
                    return;
                }
            }
            quads.add(new Quad(x0, y0, x1, y1, colorIndex));
        }

        public UpgraderPixelMesh build() { return new UpgraderPixelMesh(quads); }
    }

    private record State(List<Quad> quads, Matrix3x2fc pose, ScreenRectangle scissorArea,
                         ScreenRectangle bounds, int color0, int color1) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (Quad quad : quads) {
                int color = quad.colorIndex() == 0 ? color0 : color1;
                consumer.addVertexWith2DPose(pose, quad.x0(), quad.y0()).setColor(color);
                consumer.addVertexWith2DPose(pose, quad.x0(), quad.y1()).setColor(color);
                consumer.addVertexWith2DPose(pose, quad.x1(), quad.y1()).setColor(color);
                consumer.addVertexWith2DPose(pose, quad.x1(), quad.y0()).setColor(color);
            }
        }

        @Override
        public RenderPipeline pipeline() { return RenderPipelines.GUI; }

        @Override
        public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    }
}

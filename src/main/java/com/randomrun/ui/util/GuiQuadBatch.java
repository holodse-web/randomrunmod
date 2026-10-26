package com.randomrun.ui.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Arrays;

public final class GuiQuadBatch {
    private static final int STRIDE = 5;
    private final int[] quads;
    private final ScreenRectangle localBounds;

    private GuiQuadBatch(int[] quads, ScreenRectangle localBounds) {
        this.quads = quads;
        this.localBounds = localBounds;
    }

    public void extract(GuiGraphicsExtractor graphics) {
        if (quads.length == 0) return;
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.scissorStack.peek();
        ScreenRectangle bounds = localBounds.transformMaxBounds(pose);
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null) return;
        graphics.guiRenderState.addGuiElement(new State(quads, pose, scissor, bounds));
    }

    public static final class Builder {
        private int[] quads;
        private int size;
        private int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        private int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;

        public Builder(int expectedQuads) {
            quads = new int[Math.max(4, expectedQuads) * STRIDE];
        }

        public void fill(int x0, int y0, int x1, int y1, int color) {
            if (x1 <= x0 || y1 <= y0) return;
            left = Math.min(left, x0);
            top = Math.min(top, y0);
            right = Math.max(right, x1);
            bottom = Math.max(bottom, y1);
            int last = size - STRIDE;
            if (last >= 0 && quads[last + 4] == color) {
                if (quads[last + 1] == y0 && quads[last + 3] == y1) {
                    if (quads[last + 2] == x0) { quads[last + 2] = x1; return; }
                    if (quads[last] == x1) { quads[last] = x0; return; }
                }
                if (quads[last] == x0 && quads[last + 2] == x1) {
                    if (quads[last + 3] == y0) { quads[last + 3] = y1; return; }
                    if (quads[last + 1] == y1) { quads[last + 1] = y0; return; }
                }
            }
            if (size + STRIDE > quads.length) quads = Arrays.copyOf(quads, quads.length * 2);
            quads[size++] = x0;
            quads[size++] = y0;
            quads[size++] = x1;
            quads[size++] = y1;
            quads[size++] = color;
        }

        public GuiQuadBatch build() {
            ScreenRectangle bounds = size == 0 ? ScreenRectangle.empty()
                : new ScreenRectangle(left, top, right - left, bottom - top);
            return new GuiQuadBatch(Arrays.copyOf(quads, size), bounds);
        }
    }

    private record State(int[] quads, Matrix3x2fc pose, ScreenRectangle scissorArea,
                         ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int index = 0; index < quads.length; index += STRIDE) {
                int x0 = quads[index], y0 = quads[index + 1], x1 = quads[index + 2], y1 = quads[index + 3];
                int color = quads[index + 4];
                consumer.addVertexWith2DPose(pose, x0, y0).setColor(color);
                consumer.addVertexWith2DPose(pose, x0, y1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y0).setColor(color);
            }
        }

        @Override
        public RenderPipeline pipeline() { return RenderPipelines.GUI; }

        @Override
        public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    }
}

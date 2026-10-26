package com.randomrun.ui.util;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LargeItemPreviewRenderer extends PictureInPictureRenderer<LargeItemPreviewRenderer.State> {
    private static final int MAX_CACHED_PREVIEWS = 12;
    private final Map<Key, CachedPreview> previews = new LinkedHashMap<>(16, .75f, true);
    private final Map<ModelKey, Integer> occurrences = new HashMap<>();
    private Object frameToken;

    static void extract(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float size,
                        float rotationX, float rotationY, ItemDisplayContext displayContext) {
        if (stack.isEmpty() || !Float.isFinite(size) || size <= 0
                || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(rotationX) || !Float.isFinite(rotationY)) return;
        Minecraft client = Minecraft.getInstance();
        TrackingItemStackRenderState item = new TrackingItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(item, stack, displayContext, client.level, client.player, 0);
        if (item.isEmpty()) return;
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        float magnification = PreviewRenderGeometry.magnification(pose.m00(), pose.m01(), pose.m10(), pose.m11());
        float renderSize = PreviewRenderGeometry.textureScale(size, magnification, Math.max(1, client.getWindow().getGuiScale()));
        int radius = (int) Math.ceil(renderSize);
        pose.translate(x, y).scale(size / renderSize);
        ScreenRectangle scissor = graphics.scissorStack.peek();
        graphics.guiRenderState.addPicturesInPictureState(new State(graphics, item, displayContext,
            rotationX, rotationY, -radius, -radius, radius, radius, renderSize, pose, scissor,
            PictureInPictureRenderState.getBounds(-radius, -radius, radius, radius, pose, scissor)));
    }

    @Override
    public void prepare(State state, GuiRenderState gui, FeatureRenderDispatcher features, int guiScale) {
        if (frameToken != state.frameToken()) {
            frameToken = state.frameToken();
            occurrences.clear();
            while (previews.size() > MAX_CACHED_PREVIEWS) {
                var oldest = previews.entrySet().iterator();
                oldest.next().getValue().close();
                oldest.remove();
            }
        }
        ModelKey model = new ModelKey(state.item().getModelIdentity(), state.scale(), state.displayContext());
        int occurrence = occurrences.merge(model, 1, Integer::sum) - 1;
        CachedPreview preview = previews.computeIfAbsent(new Key(model, occurrence), ignored -> new CachedPreview());
        preview.prepare(state, gui, features, guiScale);
    }

    @Override public Class<State> getRenderStateClass() { return State.class; }
    @Override protected String getTextureLabel() { return "RandomRun item previews"; }
    @Override protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
        throw new IllegalStateException("Item previews use their cached render targets");
    }

    @Override
    public void close() {
        previews.values().forEach(CachedPreview::close);
        previews.clear();
        occurrences.clear();
        frameToken = null;
        super.close();
    }

    private record ModelKey(Object modelIdentity, float scale, ItemDisplayContext displayContext) {}
    private record Key(ModelKey model, int occurrence) {}

    private static final class CachedPreview extends PictureInPictureRenderer<State> {
        private Object modelOnTexture;
        private float rotationX, rotationY;

        @Override public Class<State> getRenderStateClass() { return State.class; }
        @Override protected String getTextureLabel() { return "RandomRun cached item preview"; }
        @Override protected float getTranslateY(int height, int guiScale) { return height / 2f; }

        @Override
        protected boolean textureIsReadyToBlit(State state) {
            return !state.item().isAnimated() && state.item().getModelIdentity().equals(modelOnTexture)
                && rotationX == state.rotationX() && rotationY == state.rotationY();
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft client = Minecraft.getInstance();
            client.gameRenderer.lighting().setupFor(state.item().usesBlockLight()
                ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            pose.scale(1, -1, -1);
            pose.rotateDegrees(Axis.XP, state.rotationX());
            pose.rotateDegrees(Axis.YP, state.rotationY());
            state.item().submit(pose, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
            modelOnTexture = state.item().getModelIdentity();
            rotationX = state.rotationX();
            rotationY = state.rotationY();
        }
    }

    public record State(Object frameToken, TrackingItemStackRenderState item, ItemDisplayContext displayContext,
                        float rotationX, float rotationY, int x0, int y0, int x1, int y1, float scale,
                        Matrix3x2fc pose, ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {}
}

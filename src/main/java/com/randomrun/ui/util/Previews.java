package com.randomrun.ui.util;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Lifecycle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.EmptyTagLookupWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.UseRemainder;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class Previews {
    private Previews() {
    }

    public final static class ItemPreviewRenderer {
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

    public final static class PreviewRenderGeometry {
        private PreviewRenderGeometry() {}

        public static float magnification(float m00, float m01, float m10, float m11) {
            double first = (double) m00 * m00 + (double) m01 * m01;
            double second = (double) m10 * m10 + (double) m11 * m11;
            double product = (double) m00 * m10 + (double) m01 * m11;
            double largest = (first + second + Math.hypot(first - second, 2 * product)) / 2;
            return Double.isFinite(largest) ? (float) Math.max(1, Math.sqrt(Math.max(0, largest))) : 1;
        }

        public static float frameSeconds(long elapsedNanos) {
            return Math.clamp(elapsedNanos / 1_000_000_000f, 0, .05f);
        }

        public static float textureScale(float size, float magnification, int guiScale) {
            double pixels = (double) size * magnification * guiScale;
            double nearestPixel = Math.rint(pixels);
            if (Math.abs(pixels - nearestPixel) <= Math.max(.0001, Math.ulp((float) pixels) * 2)) pixels = nearestPixel;
            return (float) (Math.ceil(pixels) / guiScale);
        }
    }

    public final static class PreviewItems {
        private PreviewItems() {}

        public static ItemStack stack(Item item) {
            return stack(item, 1);
        }

        public static ItemStack stack(Item item, int count) {
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            if (holder.areComponentsBound()) {
                return new ItemStack(holder, count);
            }
            return new ItemStack(Defaults.ITEMS.getOrThrow(holder.key()), count);
        }

        private static final class Defaults {
            private static final Registry<Item> ITEMS = loadItems();

            private static Registry<Item> loadItems() {
                MappedRegistry<Item> items = new MappedRegistry<>(Registries.ITEM, Lifecycle.stable());
                BuiltInRegistries.ITEM.listElements().forEach(holder ->
                    items.register(holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
                items.freeze();

                HolderLookup.Provider vanilla = VanillaRegistries.createWorldLookup();
                HolderLookup.RegistryLookup<Item> itemLookup = EmptyTagLookupWrapper.wrap(items);
                HolderLookup.Provider lookup = HolderLookup.Provider.create(vanilla.listRegistries().map(registry ->
                    registry.key().equals(Registries.ITEM) ? itemLookup : registry));

                for (DataComponentInitializers.PendingComponents<?> pending :
                        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)) {
                    if (pending.key().equals(Registries.ITEM)) {
                        pending.forEach((holder, values) -> holder.bindComponents(prepareComponents(values, items)));
                    }
                }
                return items;
            }

            private static DataComponentMap prepareComponents(DataComponentMap components, Registry<Item> items) {
                UseRemainder remainder = components.get(DataComponents.USE_REMAINDER);
                if (remainder == null) {
                    return components;
                }
                ItemStackTemplate template = remainder.convertInto();
                Holder.Reference<Item> holder = items.getOrThrow(template.item().value().builtInRegistryHolder().key());
                return DataComponentMap.builder().addAll(components)
                    .set(DataComponents.USE_REMAINDER,
                        new UseRemainder(new ItemStackTemplate(holder, template.count(), template.components())))
                    .build();
            }
        }
    }

    public final static class LargeItemPreviewRenderer extends PictureInPictureRenderer<LargeItemPreviewRenderer.State> {
        private static final int MAX_CACHED_PREVIEWS = 72;
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
            int radius = (int) Math.ceil(renderSize) + 1;
            pose.translate(x, y).scale(size / renderSize);
            // The texture is blitted with nearest filtering, so a sub-pixel position would make a moving item
            // jump pixel by pixel. Snap the blit to whole screen pixels and draw the rest of the offset inside
            // the texture instead, where the model itself moves smoothly.
            int guiScale = Math.max(1, client.getWindow().getGuiScale());
            float screenX = pose.m20() * guiScale, screenY = pose.m21() * guiScale;
            float shiftX = (screenX - Math.round(screenX)) / guiScale, shiftY = (screenY - Math.round(screenY)) / guiScale;
            pose.translateLocal(-shiftX, -shiftY);
            float toModel = 1f / (size * magnification);
            ScreenRectangle scissor = graphics.scissorStack.peek();
            graphics.guiRenderState.addPicturesInPictureState(new State(graphics, item, displayContext,
                rotationX, rotationY, shiftX * toModel, shiftY * toModel, -radius, -radius, radius, radius, renderSize, pose, scissor,
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
            private float rotationX, rotationY, shiftX, shiftY;

            @Override public Class<State> getRenderStateClass() { return State.class; }
            @Override protected String getTextureLabel() { return "RandomRun cached item preview"; }
            @Override protected float getTranslateY(int height, int guiScale) { return height / 2f; }

            @Override
            protected boolean textureIsReadyToBlit(State state) {
                return !state.item().isAnimated() && state.item().getModelIdentity().equals(modelOnTexture)
                    && rotationX == state.rotationX() && rotationY == state.rotationY()
                    && shiftX == state.shiftX() && shiftY == state.shiftY();
            }

            @Override
            protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
                Minecraft client = Minecraft.getInstance();
                client.gameRenderer.lighting().setupFor(state.item().usesBlockLight()
                    ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
                pose.translate(state.shiftX(), state.shiftY(), 0);
                pose.scale(1, -1, -1);
                pose.rotateDegrees(Axis.XP, state.rotationX());
                pose.rotateDegrees(Axis.YP, state.rotationY());
                state.item().submit(pose, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
                modelOnTexture = state.item().getModelIdentity();
                rotationX = state.rotationX();
                rotationY = state.rotationY();
                shiftX = state.shiftX();
                shiftY = state.shiftY();
            }
        }

        public record State(Object frameToken, TrackingItemStackRenderState item, ItemDisplayContext displayContext,
                            float rotationX, float rotationY, float shiftX, float shiftY, int x0, int y0, int x1, int y1, float scale,
                            Matrix3x2fc pose, ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {}
    }

    public static final class Heads {
        private Heads() {}

        public static void draw(GuiGraphicsExtractor context, String name, int x, int y, int size, int frameArgb) {
            if (name == null || name.isEmpty()) return;
            Identifier skin = PlayerSkinHelper.getPlayerSkin(name, PlayerSkinHelper.getOfflineUUID(name));
            if (frameArgb != 0) Ui.border(context, x - 1, y - 1, x + size + 1, y + size + 1, 1, frameArgb);
            context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
            context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
        }
    }

    public static class PlayerSkinHelper {
        private static final int MAX_CACHED_PLAYERS = 512;
        private static final Map<UUID, Identifier> skinCache = boundedCache();
        private static final Map<String, UUID> offlineIds = boundedCache();

        private static <K, V> Map<K, V> boundedCache() {
            return new LinkedHashMap<>(32, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > MAX_CACHED_PLAYERS;
                }
            };
        }

        public static Identifier getPlayerSkin(String playerName, UUID playerUuid) {
            synchronized (skinCache) {
                return skinCache.computeIfAbsent(playerUuid, id -> DefaultPlayerSkin.get(id).body().texturePath());
            }
        }

        public static void clearCache() {
            synchronized (skinCache) { skinCache.clear(); }
            synchronized (offlineIds) { offlineIds.clear(); }
        }

        public static UUID getOfflineUUID(String playerName) {
            synchronized (offlineIds) {
                return offlineIds.computeIfAbsent(playerName,
                    name -> UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
            }
        }
    }
}

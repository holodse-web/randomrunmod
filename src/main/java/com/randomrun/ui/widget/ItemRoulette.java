package com.randomrun.ui.widget;

import com.randomrun.features.speedrun.ItemDifficulty;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Previews.ItemPreviewRenderer;
import com.randomrun.ui.util.Previews.PreviewItems;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemRoulette {
    private static final int REEL_LENGTH = 64;
    private static final int RESULT_INDEX = 56;
    private static final long SPIN_MS = 4600;
    private static final long SETTLE_MS = 380;
    private static final long HOLD_MS = 1300;

    private final List<Item> reel;
    private final Item result;
    private final float jitter;
    private final long startNanos;
    private final boolean animated;
    private final Map<Item, ItemStack> stacks = new HashMap<>();
    private int lastTick = -1;
    private boolean finished;
    private boolean showName = true;

    public ItemRoulette hideName() {
        showName = false;
        return this;
    }

    public ItemRoulette(List<Item> pool, Item result, long seed) {
        this.result = result;
        Random random = new Random(seed ^ BuiltInRegistries.ITEM.getKey(result).hashCode() * 0x9E3779B97F4A7C15L);
        List<Item> source = pool.isEmpty() ? List.of(result) : pool;
        this.reel = new ArrayList<>(REEL_LENGTH);
        for (int i = 0; i < REEL_LENGTH; i++) reel.add(i == RESULT_INDEX ? result : source.get(random.nextInt(source.size())));
        this.jitter = (random.nextFloat() - 0.5f) * 0.7f;
        this.startNanos = System.nanoTime();
        this.animated = RandomRunMod.getInstance().getConfig().isAnimationsEnabled();
    }

    public static long seedFor(String salt) {
        return salt == null ? 0L : salt.hashCode() * 31L + 0x5DEECE66DL;
    }

    private long elapsed() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public boolean isComplete() {
        return elapsed() >= (animated ? SPIN_MS : 0) + SETTLE_MS + HOLD_MS;
    }

    public boolean isSpinning() {
        return elapsed() < (animated ? SPIN_MS : 0);
    }

    public Item result() {
        return result;
    }

    private float offset(int pitch) {
        float time = (System.nanoTime() - startNanos) / 1_000_000f;
        long spin = animated ? SPIN_MS : 0;
        float target = RESULT_INDEX * pitch;
        if (time >= spin) return target;
        float t = time / spin;
        float eased = 1f - (float) Math.pow(1f - t, 4.2);
        float correction = Mth.clamp((t - 0.72f) / 0.28f, 0f, 1f);
        correction = correction * correction * (3f - 2f * correction);
        return eased * target + jitter * pitch * eased * (1f - correction);
    }

    public void extract(GuiGraphicsExtractor graphics, int centerX, int centerY, int viewportWidth, int slot) {
        Theme theme = Theme.current();
        int gap = Math.max(3, slot / 9);
        int pitch = slot + gap;
        int half = viewportWidth / 2;
        int left = centerX - half, right = centerX + half;
        int top = centerY - slot / 2 - 6, bottom = centerY + slot / 2 + 6;
        float offset = offset(pitch);

        if (InterfaceStyle.isClassic()) {
            Ui.bevel(graphics, left - 2, top - 2, right + 2, bottom + 2, 0x505048, 1f, false);
            Ui.slot(graphics, left, top, right, bottom, 1f);

        } else {
            graphics.fill(left - 2, top - 2, right + 2, bottom + 2, Ui.argb(theme.border, 0.9f));
            graphics.fill(left, top, right, bottom, 0xF0080808);

        }
        int center = Math.round(offset / pitch);
        if (center != lastTick && isSpinning()) {
            lastTick = center;
            float progress = Math.min(1f, elapsed() / (float) SPIN_MS);
            Ui.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f + progress * 0.4f, 0.18f);
        }
        if (!finished && !isSpinning()) {
            finished = true;
            Ui.playSound(SoundEvents.PLAYER_LEVELUP, 1.25f, 0.6f);
            GlobalParticleSystem.getInstance().addFastExplosion(centerX, centerY, InterfaceStyle.isClassic() ? 10 : 34, InterfaceStyle.isClassic() ? 0xDCCA82 : 0xFFD700);
        }

        graphics.enableScissor(left, top, right, bottom);
        int first = Math.max(0, (int) Math.floor((offset - half) / pitch) - 1);
        int last = Math.min(reel.size() - 1, (int) Math.ceil((offset + half) / pitch) + 1);
        if (InterfaceStyle.isClassic()) {
            GuiQuadBatch.Builder frames = new GuiQuadBatch.Builder((last - first + 1) * 7);
            int whole = (int) Math.floor(offset);
            for (int i = first; i <= last; i++) {
                int slotX = centerX + i * pitch - whole - slot / 2;
                int rarity = rarityColor(reel.get(i));
                boolean winner = finished && i == RESULT_INDEX;
                int slotTop = centerY - slot / 2, slotBottom = centerY + slot / 2;
                frames.fill(slotX, slotTop, slotX + slot, slotBottom, winner ? 0xFF68634A : 0xFF3C3C35);
                frames.fill(slotX, slotTop, slotX + slot, slotTop + 2, 0xFF151512);
                frames.fill(slotX, slotTop + 2, slotX + 2, slotBottom, 0xFF151512);
                frames.fill(slotX + 2, slotBottom - 2, slotX + slot, slotBottom, 0xFF939386);
                frames.fill(slotX + slot - 2, slotTop + 2, slotX + slot, slotBottom - 2, 0xFF939386);
                frames.fill(slotX + 3, slotBottom - 5, slotX + slot - 3, slotBottom - 3, Ui.argb(rarity, 1f));
        }
        frames.build().extract(graphics);
        for (int i = first; i <= last; i++) {
            float itemX = centerX + i * pitch - whole;
            ItemStack stack = stacks.computeIfAbsent(reel.get(i), PreviewItems::stack);
            ItemPreviewRenderer.extractInSlot(graphics, stack, (int) itemX - slot / 2, centerY - slot / 2, (int) itemX + slot / 2, centerY + slot / 2 - 3, 3);
        }
        } else {
        GuiQuadBatch.Builder frames = new GuiQuadBatch.Builder((last - first + 1) * 3);
        int whole = (int) Math.floor(offset);
        float fraction = offset - whole;
        graphics.pose().pushMatrix();
        graphics.pose().translate(-fraction, 0);
        for (int i = first; i <= last; i++) {
            int slotX = centerX + i * pitch - whole - slot / 2;
            int rarity = rarityColor(reel.get(i));
            boolean winner = finished && i == RESULT_INDEX;
            frames.fill(slotX, centerY - slot / 2, slotX + slot, centerY + slot / 2, winner ? Ui.argb(rarity, 0.35f) : 0xFF141418);
            frames.fill(slotX, centerY + slot / 2 - 3, slotX + slot, centerY + slot / 2, Ui.argb(rarity, winner ? 1f : 0.85f));
            frames.fill(slotX, centerY - slot / 2, slotX + slot, centerY - slot / 2 + 1, 0x18FFFFFF);
        }
        frames.build().extract(graphics);
        for (int i = first; i <= last; i++) {
            int itemX = centerX + i * pitch - whole;
            ItemStack stack = stacks.computeIfAbsent(reel.get(i), PreviewItems::stack);
            ItemPreviewRenderer.extractInSlot(graphics, stack, itemX - slot / 2, centerY - slot / 2, itemX + slot / 2, centerY + slot / 2 - 3, 3);
        }
        graphics.pose().popMatrix();
        }
        graphics.disableScissor();
        graphics.nextStratum();

        int fade = Math.min(40, half / 3);
        drawHorizontalFade(graphics, left, right, top, bottom, fade);

        int markerColor = finished ? (InterfaceStyle.isClassic() ? 0xFFDCCA82 : 0xFFFFD700) : 0xFF000000 | theme.accent;
        graphics.fill(centerX - 4, top - 6, centerX + 4, top - 4, markerColor);
        graphics.fill(centerX - 3, top - 4, centerX + 3, top - 2, markerColor);
        graphics.fill(centerX - 4, bottom + 4, centerX + 4, bottom + 6, markerColor);
        graphics.fill(centerX - 3, bottom + 2, centerX + 3, bottom + 4, markerColor);

        if (finished) {
            float glow = InterfaceStyle.isClassic() ? 1f : 0.5f + 0.5f * (float) Math.sin(elapsed() / 120.0);
            Ui.border(graphics, centerX - slot / 2 - 2, centerY - slot / 2 - 2, centerX + slot / 2 + 2, centerY + slot / 2 + 2, 2, Ui.argb((InterfaceStyle.isClassic() ? 0xDCCA82 : 0xFFD700), 0.6f + 0.4f * glow));
            if (!showName) return;
            String name = Component.translatable(result.getDescriptionId()).getString();
            GuiText.centeredText(graphics, Minecraft.getInstance().font, Ui.ellipsize(Minecraft.getInstance().font, name, viewportWidth), centerX, bottom + 9, (InterfaceStyle.isClassic() ? 0xFFDCCA82 : 0xFFFFD700));
        }
    }

    private static void drawHorizontalFade(GuiGraphicsExtractor graphics, int left, int right, int top, int bottom, int fade) {
        if (fade <= 0) return;
        GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(fade * 2);
        int step = InterfaceStyle.isClassic() ? 4 : 1;
        for (int i = 0; i < fade; i += step) {
            int color = Ui.argb(InterfaceStyle.isClassic() ? 0x24241F : 0x080808, 0.94f * (1f - i / (float) fade));
            batch.fill(left + i, top, left + Math.min(fade, i + step), bottom, color);
            batch.fill(right - Math.min(fade, i + step), top, right - i, bottom, color);
        }
        batch.build().extract(graphics);
    }

    private static int rarityColor(Item item) {
        ItemDifficulty.Difficulty difficulty = ItemDifficulty.getDifficulty(item);
        return difficulty == null ? (InterfaceStyle.isClassic() ? 0xAAAA9E : 0x9CA3AF) : switch (difficulty) {
            case EASY -> (InterfaceStyle.isClassic() ? 0xAAAA9E : 0x9CA3AF);
            case MEDIUM -> (InterfaceStyle.isClassic() ? 0xA5CA7C : 0x4ADE80);
            case HARD -> (InterfaceStyle.isClassic() ? 0x91B3CE : 0x60A5FA);
            case VERY_HARD -> (InterfaceStyle.isClassic() ? 0xB69BCC : 0xC084FC);
            case DARK_NIGHT -> (InterfaceStyle.isClassic() ? 0xDD8D7C : 0xF87171);
        };
    }
}

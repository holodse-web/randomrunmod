package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.DisplayContentsFactory;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

public final class UpgraderStartupVerification {
    private static int checks;
    private UpgraderStartupVerification() {}

    public static int verify() {
        checks = 0;
        ContextMap context = ContextMap.builder().set(SlotDisplayContext.REGISTRIES,
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)).buildAndValidate(SlotDisplayContext.CONTEXT);
        var tag = new SlotDisplay.TagSlotDisplay(HolderSet.direct(
            Items.OAK_PLANKS.builtInRegistryHolder(), Items.BAMBOO_PLANKS.builtInRegistryHolder()));
        List<SlotDisplay> nativeDisplays = List.of(new SlotDisplay.ItemSlotDisplay(Items.IRON_INGOT),
            new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.IRON_INGOT, 3)), tag,
            new SlotDisplay.Composite(List.of(new SlotDisplay.ItemSlotDisplay(Items.COAL), new SlotDisplay.ItemSlotDisplay(Items.CHARCOAL))),
            new SlotDisplay.WithRemainder(new SlotDisplay.ItemSlotDisplay(Items.MILK_BUCKET), new SlotDisplay.ItemSlotDisplay(Items.BUCKET)),
            new SlotDisplay.OnlyWithComponent(new SlotDisplay.ItemSlotDisplay(Items.DIAMOND_SWORD), DataComponents.DAMAGE), SlotDisplay.Empty.INSTANCE);
        UpgraderRecipeDisplayCache cache = new UpgraderRecipeDisplayCache(context);
        for (SlotDisplay display : nativeDisplays) {
            List<ItemStack> expected = display.resolveForStacks(context);
            List<ItemStack> first = cache.resolve(display);
            check(same(first, expected), "memoized native display preserves count, components and alternatives: " + display.getClass().getSimpleName());
            for (int repeat = 0; repeat < 100; repeat++) {
                check(cache.resolve(display) == first, "a repeated stable display reuses its existing stack list");
                check(same(first, expected), "repeated resolution never changes a recipe ingredient or output");
            }
        }
        check(cache.requests() == nativeDisplays.size() * 101 && cache.resolutions() == nativeDisplays.size(),
            "707 native display requests perform seven resolutions");
        UpgraderRecipeDisplayCache repeated = new UpgraderRecipeDisplayCache(context);
        for (int index = 0; index < 4000; index++) repeated.resolve(new SlotDisplay.ItemSlotDisplay(Items.IRON_INGOT));
        check(repeated.requests() == 4000 && repeated.resolutions() == 1,
            "4000 structurally equal recipe slots perform one stack/list expansion instead of 4000");
        UpgraderRecipeDisplayCache nextWorld = new UpgraderRecipeDisplayCache(context);
        check(nextWorld.resolve(tag) != cache.resolve(tag) && nextWorld.resolutions() == 1,
            "a new initialization does not reuse a prior world's tag expansion");

        AtomicInteger customCalls = new AtomicInteger();
        SlotDisplay custom = new SlotDisplay() {
            public Type<? extends SlotDisplay> type() { return SlotDisplay.ItemSlotDisplay.TYPE; }
            public <T> Stream<T> resolve(ContextMap ignored, DisplayContentsFactory<T> factory) {
                return factory instanceof DisplayContentsFactory.ForStacks<T> stacks
                    ? Stream.of(stacks.forStack(new ItemStack(Items.COBBLESTONE, customCalls.incrementAndGet()))) : Stream.empty();
            }
        };
        check(cache.resolve(custom).getFirst().getCount() == 1 && cache.resolve(custom).getFirst().getCount() == 2,
            "unknown custom displays retain their fresh per-call behavior");
        SlotDisplay customComposite = new SlotDisplay.Composite(List.of(custom));
        check(cache.resolve(customComposite).getFirst().getCount() == 3 && cache.resolve(customComposite).getFirst().getCount() == 4,
            "a composite containing a dynamic custom display is never memoized");

        UpgraderValues.initialize(null);
        List<Item> baseline = UpgraderValues.targets();
        List<Long> prices = baseline.stream().map(UpgraderValues::unitValue).toList();
        UpgraderValues.initialize(null);
        check(UpgraderValues.targets() == baseline, "catalog-only resets reuse one immutable sorted base snapshot");
        check(baseline.stream().map(UpgraderValues::unitValue).toList().equals(prices), "base snapshot reuse preserves every price");
        List<Item> sorted = new ArrayList<>(baseline);
        sorted.sort(java.util.Comparator.<Item>comparingLong(UpgraderValues::unitValue)
            .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        check(sorted.equals(baseline), "precomputed registry keys preserve the exact original target ordering");
        return checks;
    }

    private static boolean same(List<ItemStack> actual, List<ItemStack> expected) {
        if (actual.size() != expected.size()) return false;
        for (int index = 0; index < actual.size(); index++) {
            if (actual.get(index).getCount() != expected.get(index).getCount()
                    || !ItemStack.isSameItemSameComponents(actual.get(index), expected.get(index))) return false;
        }
        return true;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

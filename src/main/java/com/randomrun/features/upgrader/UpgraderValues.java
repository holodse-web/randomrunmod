package com.randomrun.features.upgrader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.PotDecorations;
import org.slf4j.Logger;

public final class UpgraderValues {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "/assets/randomrun/upgrader_values.json";
    private static volatile Snapshot snapshot;
    private static Map<Item, Long> catalog;
    private static Map<Item, String> itemKeys;
    private static Snapshot catalogSnapshot;
    private static Set<String> unclassified = Set.of();
    private static int covered;
    private static int excluded;

    private UpgraderValues() {}

    public static synchronized void initialize(MinecraftServer server) {
        ensureCatalog();
        if (server == null) {
            snapshot = catalogSnapshot;
            return;
        }
        Map<Item, Long> values = new HashMap<>(catalog);
        List<Constraint> constraints = collectConstraints(server);
        boolean converged = false;
        for (int pass = 0; pass < 128; pass++) {
            boolean changed = false;
            for (Constraint constraint : constraints) {
                long cost = 0;
                boolean valid = true;
                for (List<Option> group : constraint.ingredients) {
                    long cheapest = Long.MAX_VALUE;
                    for (Option option : group) {
                        if (catalog.getOrDefault(option.item, 0L) <= 0) continue;
                        long optionCost = values.getOrDefault(option.item, 0L) * option.count;
                        if (option.remainder != null) {
                            optionCost -= values.getOrDefault(option.remainder, 0L) * option.remainderCount;
                        }
                        cheapest = Math.min(cheapest, Math.max(0, optionCost));
                    }
                    if (cheapest == Long.MAX_VALUE) {
                        valid = false;
                        break;
                    }
                    cost += cheapest;
                }
                long current = values.getOrDefault(constraint.output, 0L);
                long limit = cost / constraint.count;
                if (valid && current > limit) {
                    values.put(constraint.output, limit);
                    changed = true;
                }
            }
            if (!changed) {
                converged = true;
                break;
            }
        }
        if (!converged) {
            LOGGER.error("[Upgrader] Recipe values did not converge; disabling this world's catalog");
            values.replaceAll((item, value) -> 0L);
        }
        snapshot = createSnapshot(values);
    }

    public static long value(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getCount() > stack.getMaxStackSize()) return 0;
        long unit = unitValue(stack.getItem());
        if (unit <= 0 || hasProtectedContents(stack)) return 0;
        long total = unit * stack.getCount();
        if (stack.isDamageableItem()) {
            int maximum = stack.getMaxDamage();
            if (maximum <= 0) return 0;
            total = total * Math.max(0, maximum - stack.getDamageValue()) / maximum;
        }
        return total;
    }

    public static long unitValue(Item item) {
        return current().values.getOrDefault(item, 0L);
    }

    public static synchronized void installRemoteCatalog(Map<String, Long> prices) {
        ensureCatalog();
        if (prices == null) {
            throw new IllegalArgumentException("Incompatible upgrader price catalog");
        }
        Map<Item, Long> values = new HashMap<>(catalog);
        int expectedCount = 0;
        for (var entry : catalog.entrySet()) {
            var key = BuiltInRegistries.ITEM.getKey(entry.getKey());
            if (!key.getNamespace().equals("minecraft")) continue;
            expectedCount++;
            String id = key.toString();
            Long remote = prices.get(id);
            if (remote == null || remote < 0 || remote > entry.getValue()) {
                throw new IllegalArgumentException("Incompatible upgrader price catalog");
            }
            values.put(entry.getKey(), remote);
        }
        if (prices.size() != expectedCount) throw new IllegalArgumentException("Incompatible upgrader price catalog");
        snapshot = createSnapshot(values);
    }

    public static boolean isAllowed(Item item) {
        return unitValue(item) > 0;
    }

    public static List<Item> targets() {
        return current().targets;
    }

    public static int coveredCount() {
        current();
        return covered;
    }

    public static int excludedCount() {
        current();
        return excluded;
    }

    public static Set<String> unclassifiedVanillaItems() {
        current();
        return unclassified;
    }

    private static Snapshot current() {
        Snapshot result = snapshot;
        if (result != null) return result;
        synchronized (UpgraderValues.class) {
            ensureCatalog();
            return snapshot;
        }
    }

    private static void ensureCatalog() {
        if (catalog != null) return;
        try (InputStream stream = UpgraderValues.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("values");
            Map<Item, Long> values = new HashMap<>();
            Map<Item, String> keys = new HashMap<>();
            Set<String> missing = new LinkedHashSet<>();
            int found = 0;
            int forbidden = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                keys.put(item, id.toString());
                long value = 0;
                if (id.getNamespace().equals("minecraft")) {
                    if (entries.has(id.toString())) {
                        value = entries.get(id.toString()).getAsLong();
                        if (value < 0 || value > 1_000_000_000L) throw new IllegalStateException("Invalid value for " + id);
                        found++;
                        if (value == 0) forbidden++;
                    } else {
                        missing.add(id.toString());
                    }
                }
                values.put(item, value);
            }
            covered = found;
            excluded = forbidden;
            unclassified = Set.copyOf(missing);
            if (!missing.isEmpty()) LOGGER.error("[Upgrader] Unclassified vanilla items are disabled: {}", missing);
            catalog = Map.copyOf(values);
            itemKeys = Map.copyOf(keys);
            catalogSnapshot = createSnapshot(catalog);
            snapshot = catalogSnapshot;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the Upgrader item catalog", exception);
        }
    }

    private static Snapshot createSnapshot(Map<Item, Long> values) {
        Map<Item, Long> immutable = Map.copyOf(values);
        List<Item> targets = immutable.keySet().stream()
                .filter(item -> immutable.get(item) > 0)
                .sorted(Comparator.<Item>comparingLong(immutable::get)
                        .thenComparing(itemKeys::get))
                .toList();
        return new Snapshot(immutable, targets);
    }

    private static boolean hasProtectedContents(ItemStack stack) {
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItems().iterator().hasNext()) return true;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) return true;
        ChargedProjectiles projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles != null && !projectiles.isEmpty()) return true;
        Bees bees = stack.get(DataComponents.BEES);
        if (bees != null && !bees.bees().isEmpty()) return true;
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) return true;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) return true;
        CustomData bucketData = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (bucketData != null && !bucketData.isEmpty()) return true;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) return true;
        if (stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BLOCK_ENTITY_DATA)
                || stack.has(DataComponents.ENTITY_DATA) || stack.has(DataComponents.TRIM)
                || stack.has(DataComponents.LODESTONE_TRACKER)) return true;
        var defaults = stack.getItem().getDefaultInstance();
        if (!Objects.equals(stack.get(DataComponents.BANNER_PATTERNS), defaults.get(DataComponents.BANNER_PATTERNS))
                || !Objects.equals(stack.get(DataComponents.WRITABLE_BOOK_CONTENT), defaults.get(DataComponents.WRITABLE_BOOK_CONTENT))
                || !Objects.equals(stack.get(DataComponents.FIREWORKS), defaults.get(DataComponents.FIREWORKS))
                || !Objects.equals(stack.get(DataComponents.FIREWORK_EXPLOSION), defaults.get(DataComponents.FIREWORK_EXPLOSION))
                || !Objects.equals(stack.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER), defaults.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER))
                || !Objects.equals(stack.get(DataComponents.INSTRUMENT), defaults.get(DataComponents.INSTRUMENT))) return true;
        PotDecorations decorations = stack.get(DataComponents.POT_DECORATIONS);
        return decorations != null && List.of(decorations.back(), decorations.left(), decorations.right(), decorations.front())
                .stream().flatMap(java.util.Optional::stream).anyMatch(item -> item.item().value() != Items.BRICK);
    }

    private static List<Constraint> collectConstraints(MinecraftServer server) {
        List<Constraint> constraints = new ArrayList<>();
        Map<String, Item> byId = new HashMap<>();
        for (Item item : catalog.keySet()) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("minecraft")) byId.put(id.getPath(), item);
        }
        for (var entry : byId.entrySet()) {
            String name = entry.getKey();
            String wax = name.startsWith("waxed_") ? "waxed_" : "";
            String rest = name.substring(wax.length());
            for (String stage : List.of("exposed_", "weathered_", "oxidized_")) {
                if (!rest.startsWith(stage)) continue;
                String baseName = rest.substring(stage.length());
                if (baseName.equals("copper")) baseName = "copper_block";
                Item base = byId.get(wax + baseName);
                if (base != null) {
                    addExchange(constraints, entry.getValue(), base);
                    addExchange(constraints, base, entry.getValue());
                }
            }
            if (name.endsWith("_concrete")) {
                Item powder = byId.get(name + "_powder");
                if (powder != null) addExchange(constraints, entry.getValue(), powder);
            }
            if (name.startsWith("stripped_")) {
                Item original = byId.get(name.substring("stripped_".length()));
                if (original != null) {
                    addExchange(constraints, entry.getValue(), original);
                    addExchange(constraints, original, entry.getValue());
                }
            }
        }
        ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
        UpgraderRecipeDisplayCache displays = new UpgraderRecipeDisplayCache(context);
        for (var holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe
                    || recipe instanceof SingleItemRecipe || recipe instanceof SmithingTransformRecipe
                    || recipe instanceof TransmuteRecipe)) continue;
            for (RecipeDisplay display : recipe.display()) {
                List<ItemStack> results = displays.resolve(display.result());
                if (results.size() != 1) continue;
                ItemStack output = results.getFirst();
                if (output.isEmpty() || catalog.getOrDefault(output.getItem(), 0L) <= 0) continue;
                List<SlotDisplay> slots = ingredientSlots(display);
                if (slots.isEmpty()) continue;
                List<List<Option>> ingredients = new ArrayList<>();
                boolean valid = true;
                for (SlotDisplay slot : slots) {
                    if (slot instanceof SlotDisplay.Empty) continue;
                    List<Option> options = new ArrayList<>();
                    for (ItemStack input : displays.resolve(slot)) {
                        if (input.isEmpty()) continue;
                        ItemStackTemplate remainder = recipe instanceof CraftingRecipe
                                ? input.getItem().getCraftingRemainder() : null;
                        options.add(new Option(input.getItem(), input.getCount(),
                                remainder == null ? null : remainder.item().value(),
                                remainder == null ? 0 : remainder.count()));
                    }
                    if (options.isEmpty()) {
                        valid = false;
                        break;
                    }
                    ingredients.add(List.copyOf(options));
                }
                if (valid && !ingredients.isEmpty()) {
                    constraints.add(new Constraint(output.getItem(), output.getCount(), List.copyOf(ingredients)));
                }
            }
        }
        return constraints;
    }

    private static void addExchange(List<Constraint> constraints, Item output, Item input) {
        if (catalog.getOrDefault(output, 0L) > 0 && catalog.getOrDefault(input, 0L) > 0) {
            constraints.add(new Constraint(output, 1, List.of(List.of(new Option(input, 1, null, 0)))));
        }
    }

    private static List<SlotDisplay> ingredientSlots(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
        if (display instanceof FurnaceRecipeDisplay furnace) return List.of(furnace.ingredient());
        if (display instanceof StonecutterRecipeDisplay stonecutter) return List.of(stonecutter.input());
        if (display instanceof SmithingRecipeDisplay smithing) return List.of(smithing.template(), smithing.base(), smithing.addition());
        return List.of();
    }

    private record Snapshot(Map<Item, Long> values, List<Item> targets) {}
    private record Option(Item item, int count, Item remainder, int remainderCount) {}
    private record Constraint(Item output, int count, List<List<Option>> ingredients) {}
}

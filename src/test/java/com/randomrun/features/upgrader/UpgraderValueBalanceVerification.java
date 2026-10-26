package com.randomrun.features.upgrader;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UpgraderValueBalanceVerification {
    private UpgraderValueBalanceVerification() {}

    public static int verify() {
        int checks = 0;
        for (Item junk : List.of(Items.DEAD_BUSH, Items.LEAF_LITTER, Items.SHORT_GRASS,
                Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN, Items.DRY_SHORT_GRASS,
                Items.DRY_TALL_GRASS, Items.SEAGRASS, Items.NETHER_SPROUTS,
                Items.PALE_HANGING_MOSS, Items.GLOW_LICHEN)) {
            check(v(junk) == 1, "bulk dry foliage stays one point: " + id(junk));
            checks++;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (path.endsWith("_leaves")) {
                check(v(item) == 1 && v(item) < v(Items.DIRT), "leaves below dirt: " + path);
                checks++;
            }
        }
        for (Item common : List.of(Items.DANDELION, Items.POPPY, Items.SUNFLOWER,
                Items.PINK_PETALS, Items.WILDFLOWERS, Items.MOSS_BLOCK, Items.MOSS_CARPET,
                Items.OAK_SAPLING, Items.KELP, Items.NETHERRACK)) {
            check(v(common) > 0 && v(common) <= v(Items.DIRT), "common gathered material <= dirt: " + id(common));
            checks++;
        }
        check(v(Items.DIRT) == 2, "plentiful hand-dug dirt reference value"); checks++;
        check(v(Items.DIRT) < v(Items.STONE) && v(Items.STONE) < v(Items.OAK_PLANKS)
                && v(Items.OAK_PLANKS) < v(Items.OAK_LOG), "earth < mined rock < worked planks < whole timber"); checks++;
        for (Item mineral : List.of(Items.SAND, Items.RED_SAND, Items.GRAVEL, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE)) {
            check(v(mineral) >= v(Items.DIRT) && v(mineral) < v(Items.OAK_PLANKS), "bulk minerals below timber: " + id(mineral)); checks++;
        }
        check(v(Items.WATER_BUCKET) == v(Items.BUCKET) + 1, "water adds one point; bucket keeps its iron"); checks++;
        check(v(Items.MILK_BUCKET) == v(Items.BUCKET) + 12, "renewable milk is not expensive iron"); checks++;
        check(v(Items.OAK_LOG) == 4 * v(Items.OAK_PLANKS), "log/plank conservation"); checks++;
        check(v(Items.STICK) == 1 && v(Items.STICK) < v(Items.OAK_PLANKS), "sticks use their cheap natural leaf/bush source"); checks++;
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "poplar", "crimson", "warped")) {
            check(v(item(wood + "_planks")) == 18, "same usable timber across wood families: " + wood); checks++;
            for (String part : List.of("stairs", "slab", "door", "trapdoor", "fence", "fence_gate", "pressure_plate", "button", "sign", "hanging_sign")) {
                check(v(item(wood + "_" + part)) == v(item("oak_" + part)), "same ingredient quantities across wood variants: " + wood + "_" + part); checks++;
            }
        }
        check(9 * v(Items.BAMBOO) == 2 * v(Items.BAMBOO_PLANKS), "nine bamboo make two planks"); checks++;
        check(v(Items.BAMBOO_PLANKS) == v(Items.OAK_PLANKS), "bamboo does not underprice all generic wood recipes"); checks++;
        check(v(Items.IRON_INGOT) == 9 * v(Items.IRON_NUGGET), "iron nugget conservation"); checks++;
        check(v(Items.IRON_BLOCK) == 9 * v(Items.IRON_INGOT), "iron block conservation"); checks++;
        check(v(Items.COPPER_INGOT) == 9 * v(Items.COPPER_NUGGET), "copper nugget conservation"); checks++;
        check(v(Items.GOLD_INGOT) == 9 * v(Items.GOLD_NUGGET), "gold nugget conservation"); checks++;
        check(v(Items.IRON_PICKAXE) == 3 * v(Items.IRON_INGOT) + 2 * v(Items.STICK), "iron pickaxe real ingredients"); checks++;
        check(v(Items.DIAMOND_PICKAXE) == 3 * v(Items.DIAMOND) + 2 * v(Items.STICK), "diamond pickaxe real ingredients"); checks++;
        check(v(Items.WOODEN_PICKAXE) == 3 * v(Items.OAK_PLANKS) + 2 * v(Items.STICK), "wooden tool keeps its timber cost"); checks++;
        check(v(Items.STONE_PICKAXE) == 3 * v(Items.COBBLESTONE) + 2 * v(Items.STICK), "stone tool reflects cheaper mined materials"); checks++;
        check(v(Items.IRON_INGOT) > v(Items.COPPER_INGOT) && v(Items.DIAMOND) > v(Items.IRON_INGOT), "mining progression"); checks++;
        check(v(Items.EMERALD) < v(Items.DIAMOND), "renewable trading currency below mined diamonds"); checks++;
        check(v(Items.NETHERITE_PICKAXE) > v(Items.DIAMOND_PICKAXE) && v(Items.ELYTRA) > v(Items.NETHERITE_PICKAXE), "endgame progression"); checks++;
        check(v(Items.ENDER_EYE) == v(Items.ENDER_PEARL) + v(Items.BLAZE_POWDER), "speedrun eye ingredients"); checks++;
        check(v(Items.MACE) == v(Items.HEAVY_CORE) + v(Items.BREEZE_ROD), "mace ingredients"); checks++;
        check(v(Items.NETHERITE_PICKAXE) == v(Items.DIAMOND_PICKAXE) + v(Items.NETHERITE_INGOT)
                + v(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), "netherite includes consumed upgrade template"); checks++;
        check(v(Items.DIAMOND_ORE) == v(Items.DIAMOND), "diamond ore does not get an arbitrary Silk Touch premium"); checks++;
        check(v(Items.COPPER_ORE) >= 5 * v(Items.RAW_COPPER), "copper ore covers actual normal 2-5 drops"); checks++;
        check(v(Items.LAPIS_ORE) >= 9 * v(Items.LAPIS_LAZULI), "lapis ore covers actual normal 4-9 drops"); checks++;
        check(v(Items.REDSTONE_ORE) >= 5 * v(Items.REDSTONE), "redstone ore covers actual normal 4-5 drops"); checks++;
        check(v(Items.NETHER_GOLD_ORE) >= v(Items.GOLD_INGOT), "nether gold ore covers full smelting yield"); checks++;
        check(v(Items.BREAD) == 3 * v(Items.WHEAT), "bread uses three wheat"); checks++;
        check(v(Items.COOKED_BEEF) <= v(Items.BEEF) && v(Items.COOKED_COD) <= v(Items.COD), "free cooking does not create upgrade value"); checks++;
        check(v(Items.WHEAT) < v(Items.BEEF) && v(Items.BEEF) < v(Items.ENDER_PEARL), "crop/food/combat acquisition ladder"); checks++;
        check(v(Items.NAME_TAG) == v(Items.PAPER) + v(Items.COPPER_NUGGET), "26.3 name-tag crafting replaces old loot premium"); checks++;
        check(v(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE) > v(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), "trim sources have distinct acquisition scarcity"); checks++;
        check(v(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE) > v(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE), "ominous vault trim has its own progression cost"); checks++;
        check(v(Items.DRAGON_BREATH) > v(Items.IRON_INGOT), "dragon encounter is not generic vegetation"); checks++;
        check(v(Items.CHIPPED_ANVIL) > v(Items.DAMAGED_ANVIL) && v(Items.ANVIL) > v(Items.CHIPPED_ANVIL), "anvil remaining uses"); checks++;
        check(v(Items.MUSHROOM_STEM) < v(Items.OAK_LOG), "mushroom stem is not timber by suffix"); checks++;
        check(v(Items.STRIPPED_BAMBOO_BLOCK) == v(Items.BAMBOO_BLOCK), "stripped bamboo source quantity"); checks++;
        check(v(item("lightning_rod")) == v(item("oxidized_lightning_rod")), "lightning rod copper scraping"); checks++;
        check(v(item("white_concrete")) <= v(item("white_concrete_powder")), "hydration cannot manufacture value"); checks++;
        for (String name : List.of("copper_block", "exposed_copper", "weathered_copper", "oxidized_copper")) {
            check(v(item(name)) == v(item("copper_block")), "oxidation keeps metal content: " + name); checks++;
        }
        int cheapCompensations = 0;
        for (Item item : List.of(Items.STICK, Items.TORCH, Items.ARROW, Items.WHEAT_SEEDS,
                Items.WHEAT, Items.APPLE, Items.BREAD, Items.COAL, Items.IRON_NUGGET,
                Items.COPPER_INGOT, Items.IRON_INGOT)) {
            if (v(item) > 0 && v(item) <= 25) cheapCompensations++;
        }
        check(cheapCompensations >= 2, "small losses have multiple useful affordable compensations"); checks++;
        check(UpgraderValues.value(new ItemStack(Items.DEAD_BUSH, 64)) == 64, "low-value stacks remain useful"); checks++;
        check(UpgraderValues.unclassifiedVanillaItems().isEmpty(), "all vanilla items explicitly covered"); checks++;
        return checks;
    }

    private static long v(Item item) { return UpgraderValues.unitValue(item); }
    private static Item item(String path) { return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", path)); }
    private static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Acquisition balance: " + message);
    }
}

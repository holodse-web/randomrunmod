package com.randomrun.features.challenges.classic.data;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

public class ItemDifficulty {
    public enum Difficulty {
        EASY("Легкая", 30, 120, 0xAAFFAA),
        MEDIUM("Средняя", 180, 540, 0xFFFFAA),
        HARD("Тяжелая", 600, 1200, 0xFFCC66),
        VERY_HARD("Очень тяжелая", 1200, 3000, 0xFF8888),
        DARK_NIGHT("Темная ночь...", 3000, 7200, 0xCC66CC);
        
        public final String displayName;
        public final int minSeconds;
        public final int maxSeconds;
        public final int color;
        
        Difficulty(String displayName, int minSeconds, int maxSeconds, int color) {
            this.displayName = displayName;
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
            this.color = color;
        }
        
        public int getRandomTime() {
            Random random = new Random();
            return minSeconds + random.nextInt(maxSeconds - minSeconds + 1);
        }
        
        public String getTimeRange() {
            return formatTime(minSeconds) + " - " + formatTime(maxSeconds);
        }
        
        private String formatTime(int seconds) {
            if (seconds < 60) {
                return seconds + "s";
            } else if (seconds < 3600) {
                int min = seconds / 60;
                int sec = seconds % 60;
                return sec > 0 ? min + "m " + sec + "s" : min + "m";
            } else {
                int hours = seconds / 3600;
                int min = (seconds % 3600) / 60;
                return min > 0 ? hours + "h " + min + "m" : hours + "h";
            }
        }
    }
    
    private static final Map<Item, Difficulty> ITEM_DIFFICULTIES = new HashMap<>();
    private static final Set<Item> UNOBTAINABLE_ITEMS = new HashSet<>();

    private static List<Item> cachedObtainableItems = null;
    private static List<Item> cachedAllItems = Collections.emptyList();
    private static final Map<Difficulty, List<Item>> cachedDifficultyItems = new EnumMap<>(Difficulty.class);
    
    public static void initialize() {
        initEasyItems();
        initMediumItems();
        initHardItems();
        initVeryHardItems();
        initDarkNightItems();
        initUnobtainableItems();

        cacheItemLists();
    }
    
    private static void cacheItemLists() {
        cachedObtainableItems = new ArrayList<>();
        cachedAllItems = new ArrayList<>();
        for (Difficulty diff : Difficulty.values()) {
            cachedDifficultyItems.put(diff, new ArrayList<>());
        }
        
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            cachedAllItems.add(item);
            if (!isUnobtainable(item)) {
                cachedObtainableItems.add(item);
                Difficulty diff = getDifficulty(item);
                cachedDifficultyItems.get(diff).add(item);
            }
        }
    }
    
    private static void initEasyItems() {
        setDifficulty(Items.DIRT, Difficulty.EASY);
        setDifficulty(Items.COARSE_DIRT, Difficulty.EASY);
        setDifficulty(Items.ROOTED_DIRT, Difficulty.EASY);
        setDifficulty(Items.MUD, Difficulty.EASY);
        setDifficulty(Items.COBBLESTONE, Difficulty.EASY);
        setDifficulty(Items.SAND, Difficulty.EASY);
        setDifficulty(Items.RED_SAND, Difficulty.EASY);
        setDifficulty(Items.GRAVEL, Difficulty.EASY);
        setDifficulty(Items.GRASS_BLOCK, Difficulty.EASY);
        setDifficulty(Items.PODZOL, Difficulty.EASY);
        setDifficulty(Items.MYCELIUM, Difficulty.EASY);
        setDifficulty(Items.STONE, Difficulty.EASY);
        setDifficulty(Items.DIORITE, Difficulty.EASY);
        setDifficulty(Items.ANDESITE, Difficulty.EASY);
        setDifficulty(Items.GRANITE, Difficulty.EASY);
        setDifficulty(Items.DEEPSLATE, Difficulty.EASY);
        setDifficulty(Items.COBBLED_DEEPSLATE, Difficulty.EASY);
        setDifficulty(Items.TUFF, Difficulty.EASY);
        setDifficulty(Items.CALCITE, Difficulty.EASY);
        setDifficulty(Items.DRIPSTONE_BLOCK, Difficulty.EASY);
        setDifficulty(Items.POINTED_DRIPSTONE, Difficulty.EASY);
        setDifficulty(Items.MOSS_BLOCK, Difficulty.EASY);
        setDifficulty(Items.MOSS_CARPET, Difficulty.EASY);
        setDifficulty(Items.CLAY, Difficulty.EASY);
        setDifficulty(Items.TERRACOTTA, Difficulty.EASY);

        Item[] logs = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                       Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
                       Items.PALE_OAK_LOG};
        for (Item log : logs) setDifficulty(log, Difficulty.EASY);
        
        Item[] strippedLogs = {Items.STRIPPED_OAK_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_BIRCH_LOG,
                              Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_DARK_OAK_LOG,
                              Items.STRIPPED_MANGROVE_LOG, Items.STRIPPED_CHERRY_LOG, Items.STRIPPED_PALE_OAK_LOG};
        for (Item log : strippedLogs) setDifficulty(log, Difficulty.EASY);
        
        Item[] woods = {Items.OAK_WOOD, Items.SPRUCE_WOOD, Items.BIRCH_WOOD, Items.JUNGLE_WOOD,
                       Items.ACACIA_WOOD, Items.DARK_OAK_WOOD, Items.MANGROVE_WOOD, Items.CHERRY_WOOD,
                       Items.PALE_OAK_WOOD};
        for (Item wood : woods) setDifficulty(wood, Difficulty.EASY);
        
        Item[] planks = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                        Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS,
                        Items.PALE_OAK_PLANKS, Items.BAMBOO_PLANKS};
        for (Item plank : planks) setDifficulty(plank, Difficulty.EASY);

        setDifficulty(Items.STICK, Difficulty.EASY);
        setDifficulty(Items.CRAFTING_TABLE, Difficulty.EASY);
        setDifficulty(Items.WOODEN_PICKAXE, Difficulty.EASY);
        setDifficulty(Items.WOODEN_SWORD, Difficulty.EASY);
        setDifficulty(Items.WOODEN_AXE, Difficulty.EASY);
        setDifficulty(Items.WOODEN_SHOVEL, Difficulty.EASY);
        setDifficulty(Items.WOODEN_HOE, Difficulty.EASY);
        setDifficulty(Items.STONE_PICKAXE, Difficulty.EASY);
        setDifficulty(Items.STONE_SWORD, Difficulty.EASY);
        setDifficulty(Items.STONE_AXE, Difficulty.EASY);
        setDifficulty(Items.STONE_SHOVEL, Difficulty.EASY);
        setDifficulty(Items.STONE_HOE, Difficulty.EASY);
        setDifficulty(Items.TORCH, Difficulty.EASY);
        setDifficulty(Items.SOUL_TORCH, Difficulty.EASY);
        setDifficulty(Items.FURNACE, Difficulty.EASY);
        setDifficulty(Items.BLAST_FURNACE, Difficulty.EASY);
        setDifficulty(Items.SMOKER, Difficulty.EASY);
        setDifficulty(Items.CHEST, Difficulty.EASY);
        setDifficulty(Items.BARREL, Difficulty.EASY);

        Item[] beds = {Items.BED.white(), Items.BED.orange(), Items.BED.magenta(), Items.BED.lightBlue(),
                      Items.BED.yellow(), Items.BED.lime(), Items.BED.pink(), Items.BED.gray(),
                      Items.BED.lightGray(), Items.BED.cyan(), Items.BED.purple(), Items.BED.blue(),
                      Items.BED.brown(), Items.BED.green(), Items.BED.red(), Items.BED.black()};
        for (Item bed : beds) setDifficulty(bed, Difficulty.EASY);

        Item[] saplings = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
                          Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING,
                          Items.MANGROVE_PROPAGULE, Items.CHERRY_SAPLING, Items.PALE_OAK_SAPLING};
        for (Item sapling : saplings) setDifficulty(sapling, Difficulty.EASY);

        Item[] flowers = {Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM,
                         Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP,
                         Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY,
                         Items.SUNFLOWER, Items.LILAC, Items.ROSE_BUSH, Items.PEONY, Items.TORCHFLOWER,
                         Items.PINK_PETALS};
        for (Item flower : flowers) setDifficulty(flower, Difficulty.EASY);

        setDifficulty(Items.APPLE, Difficulty.EASY);
        setDifficulty(Items.BEEF, Difficulty.EASY);
        setDifficulty(Items.PORKCHOP, Difficulty.EASY);
        setDifficulty(Items.CHICKEN, Difficulty.EASY);
        setDifficulty(Items.MUTTON, Difficulty.EASY);
        setDifficulty(Items.RABBIT, Difficulty.EASY);
        setDifficulty(Items.COD, Difficulty.EASY);
        setDifficulty(Items.SALMON, Difficulty.EASY);
        setDifficulty(Items.TROPICAL_FISH, Difficulty.EASY);
        setDifficulty(Items.PUFFERFISH, Difficulty.EASY);
        setDifficulty(Items.SWEET_BERRIES, Difficulty.EASY);
        setDifficulty(Items.GLOW_BERRIES, Difficulty.EASY);
        setDifficulty(Items.MELON_SLICE, Difficulty.EASY);
        setDifficulty(Items.CARROT, Difficulty.EASY);
        setDifficulty(Items.POTATO, Difficulty.EASY);
        setDifficulty(Items.BEETROOT, Difficulty.EASY);

        Item[] wools = {Items.WOOL.white(), Items.WOOL.orange(), Items.WOOL.magenta(), Items.WOOL.lightBlue(),
                       Items.WOOL.yellow(), Items.WOOL.lime(), Items.WOOL.pink(), Items.WOOL.gray(),
                       Items.WOOL.lightGray(), Items.WOOL.cyan(), Items.WOOL.purple(), Items.WOOL.blue(),
                       Items.WOOL.brown(), Items.WOOL.green(), Items.WOOL.red(), Items.WOOL.black()};
        for (Item wool : wools) setDifficulty(wool, Difficulty.EASY);

        setDifficulty(Items.STRING, Difficulty.EASY);
        setDifficulty(Items.LEATHER, Difficulty.EASY);
        setDifficulty(Items.FEATHER, Difficulty.EASY);
        setDifficulty(Items.BONE, Difficulty.EASY);
        setDifficulty(Items.BONE_MEAL, Difficulty.EASY);
        setDifficulty(Items.FLINT, Difficulty.EASY);
        setDifficulty(Items.SNOWBALL, Difficulty.EASY);
        setDifficulty(Items.EGG, Difficulty.EASY);
        setDifficulty(Items.WHEAT, Difficulty.EASY);
        setDifficulty(Items.WHEAT_SEEDS, Difficulty.EASY);
        setDifficulty(Items.BAMBOO, Difficulty.EASY);
        setDifficulty(Items.SUGAR_CANE, Difficulty.EASY);
        setDifficulty(Items.CACTUS, Difficulty.EASY);
        setDifficulty(Items.KELP, Difficulty.EASY);
        setDifficulty(Items.SEAGRASS, Difficulty.EASY);
        setDifficulty(Items.VINE, Difficulty.EASY);
        setDifficulty(Items.LILY_PAD, Difficulty.EASY);

        Item[] carpets = {Items.CARPET.white(), Items.CARPET.orange(), Items.CARPET.magenta(), Items.CARPET.lightBlue(),
                         Items.CARPET.yellow(), Items.CARPET.lime(), Items.CARPET.pink(), Items.CARPET.gray(),
                         Items.CARPET.lightGray(), Items.CARPET.cyan(), Items.CARPET.purple(), Items.CARPET.blue(),
                         Items.CARPET.brown(), Items.CARPET.green(), Items.CARPET.red(), Items.CARPET.black()};
        for (Item carpet : carpets) setDifficulty(carpet, Difficulty.EASY);

        setDifficulty(Items.GLASS_PANE, Difficulty.EASY);

        Item[] stairs = {Items.OAK_STAIRS, Items.SPRUCE_STAIRS, Items.BIRCH_STAIRS, Items.JUNGLE_STAIRS,
                        Items.ACACIA_STAIRS, Items.DARK_OAK_STAIRS, Items.COBBLESTONE_STAIRS, Items.STONE_STAIRS};
        for (Item stair : stairs) setDifficulty(stair, Difficulty.EASY);
        
        Item[] slabs = {Items.OAK_SLAB, Items.SPRUCE_SLAB, Items.BIRCH_SLAB, Items.JUNGLE_SLAB,
                       Items.ACACIA_SLAB, Items.DARK_OAK_SLAB, Items.COBBLESTONE_SLAB, Items.STONE_SLAB};
        for (Item slab : slabs) setDifficulty(slab, Difficulty.EASY);

        Item[] fences = {Items.OAK_FENCE, Items.SPRUCE_FENCE, Items.BIRCH_FENCE, Items.JUNGLE_FENCE,
                        Items.ACACIA_FENCE, Items.DARK_OAK_FENCE};
        for (Item fence : fences) setDifficulty(fence, Difficulty.EASY);
        
        Item[] gates = {Items.OAK_FENCE_GATE, Items.SPRUCE_FENCE_GATE, Items.BIRCH_FENCE_GATE,
                       Items.JUNGLE_FENCE_GATE, Items.ACACIA_FENCE_GATE, Items.DARK_OAK_FENCE_GATE};
        for (Item gate : gates) setDifficulty(gate, Difficulty.EASY);

        Item[] doors = {Items.OAK_DOOR, Items.SPRUCE_DOOR, Items.BIRCH_DOOR, Items.JUNGLE_DOOR,
                       Items.ACACIA_DOOR, Items.DARK_OAK_DOOR};
        for (Item door : doors) setDifficulty(door, Difficulty.EASY);
        
        Item[] trapdoors = {Items.OAK_TRAPDOOR, Items.SPRUCE_TRAPDOOR, Items.BIRCH_TRAPDOOR,
                           Items.JUNGLE_TRAPDOOR, Items.ACACIA_TRAPDOOR, Items.DARK_OAK_TRAPDOOR};
        for (Item trapdoor : trapdoors) setDifficulty(trapdoor, Difficulty.EASY);

        Item[] buttons = {Items.OAK_BUTTON, Items.SPRUCE_BUTTON, Items.BIRCH_BUTTON, Items.JUNGLE_BUTTON,
                         Items.ACACIA_BUTTON, Items.DARK_OAK_BUTTON, Items.STONE_BUTTON};
        for (Item button : buttons) setDifficulty(button, Difficulty.EASY);
        
        Item[] pressurePlates = {Items.OAK_PRESSURE_PLATE, Items.SPRUCE_PRESSURE_PLATE, Items.BIRCH_PRESSURE_PLATE,
                                Items.JUNGLE_PRESSURE_PLATE, Items.ACACIA_PRESSURE_PLATE, Items.DARK_OAK_PRESSURE_PLATE,
                                Items.STONE_PRESSURE_PLATE};
        for (Item plate : pressurePlates) setDifficulty(plate, Difficulty.EASY);

        Item[] signs = {Items.OAK_SIGN, Items.SPRUCE_SIGN, Items.BIRCH_SIGN, Items.JUNGLE_SIGN,
                       Items.ACACIA_SIGN, Items.DARK_OAK_SIGN};
        for (Item sign : signs) setDifficulty(sign, Difficulty.EASY);
        
        Item[] hangingSigns = {Items.OAK_HANGING_SIGN, Items.SPRUCE_HANGING_SIGN, Items.BIRCH_HANGING_SIGN,
                              Items.JUNGLE_HANGING_SIGN, Items.ACACIA_HANGING_SIGN, Items.DARK_OAK_HANGING_SIGN};
        for (Item sign : hangingSigns) setDifficulty(sign, Difficulty.EASY);
    }
    
    private static void initMediumItems() {
        setDifficulty(Items.IRON_INGOT, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_ORE, Difficulty.MEDIUM);
        setDifficulty(Items.DEEPSLATE_IRON_ORE, Difficulty.MEDIUM);
        setDifficulty(Items.RAW_IRON, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_BLOCK, Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_INGOT, Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_ORE, Difficulty.MEDIUM);
        setDifficulty(Items.DEEPSLATE_COPPER_ORE, Difficulty.MEDIUM);
        setDifficulty(Items.RAW_COPPER, Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_BLOCK.weathering().unaffected(), Difficulty.MEDIUM);
        setDifficulty(Items.COAL, Difficulty.MEDIUM);
        setDifficulty(Items.COAL_ORE, Difficulty.MEDIUM);
        setDifficulty(Items.DEEPSLATE_COAL_ORE, Difficulty.MEDIUM);
        setDifficulty(Items.COAL_BLOCK, Difficulty.MEDIUM);
        setDifficulty(Items.CHARCOAL, Difficulty.MEDIUM);

        setDifficulty(Items.GLASS, Difficulty.MEDIUM);
        Item[] stainedGlass = {Items.STAINED_GLASS.white(), Items.STAINED_GLASS.orange(), Items.STAINED_GLASS.magenta(),
                              Items.STAINED_GLASS.lightBlue(), Items.STAINED_GLASS.yellow(), Items.STAINED_GLASS.lime(),
                              Items.STAINED_GLASS.pink(), Items.STAINED_GLASS.gray(), Items.STAINED_GLASS.lightGray(),
                              Items.STAINED_GLASS.cyan(), Items.STAINED_GLASS.purple(), Items.STAINED_GLASS.blue(),
                              Items.STAINED_GLASS.brown(), Items.STAINED_GLASS.green(), Items.STAINED_GLASS.red(),
                              Items.STAINED_GLASS.black()};
        for (Item glass : stainedGlass) setDifficulty(glass, Difficulty.MEDIUM);
        
        Item[] terracottas = {Items.DYED_TERRACOTTA.white(), Items.DYED_TERRACOTTA.orange(), Items.DYED_TERRACOTTA.magenta(),
                             Items.DYED_TERRACOTTA.lightBlue(), Items.DYED_TERRACOTTA.yellow(), Items.DYED_TERRACOTTA.lime(),
                             Items.DYED_TERRACOTTA.pink(), Items.DYED_TERRACOTTA.gray(), Items.DYED_TERRACOTTA.lightGray(),
                             Items.DYED_TERRACOTTA.cyan(), Items.DYED_TERRACOTTA.purple(), Items.DYED_TERRACOTTA.blue(),
                             Items.DYED_TERRACOTTA.brown(), Items.DYED_TERRACOTTA.green(), Items.DYED_TERRACOTTA.red(),
                             Items.DYED_TERRACOTTA.black()};
        for (Item terracotta : terracottas) setDifficulty(terracotta, Difficulty.MEDIUM);

        setDifficulty(Items.AMETHYST_SHARD, Difficulty.MEDIUM);
        setDifficulty(Items.AMETHYST_BLOCK, Difficulty.MEDIUM);
        setDifficulty(Items.AMETHYST_CLUSTER, Difficulty.MEDIUM);

        setDifficulty(Items.IRON_PICKAXE, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_SWORD, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_AXE, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_SHOVEL, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_HOE, Difficulty.MEDIUM);
        setDifficulty(Items.BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.WATER_BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.LAVA_BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.MILK_BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.POWDER_SNOW_BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.SHIELD, Difficulty.MEDIUM);
        setDifficulty(Items.SHEARS, Difficulty.MEDIUM);
        setDifficulty(Items.COMPASS, Difficulty.MEDIUM);
        setDifficulty(Items.RECOVERY_COMPASS, Difficulty.MEDIUM);
        setDifficulty(Items.CLOCK, Difficulty.MEDIUM);
        setDifficulty(Items.SPYGLASS, Difficulty.MEDIUM);
        setDifficulty(Items.FLINT_AND_STEEL, Difficulty.MEDIUM);
        setDifficulty(Items.FISHING_ROD, Difficulty.MEDIUM);

        setDifficulty(Items.IRON_HELMET, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_CHESTPLATE, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_LEGGINGS, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_BOOTS, Difficulty.MEDIUM);
        setDifficulty(Items.CHAINMAIL_HELMET, Difficulty.MEDIUM);
        setDifficulty(Items.CHAINMAIL_CHESTPLATE, Difficulty.MEDIUM);
        setDifficulty(Items.CHAINMAIL_LEGGINGS, Difficulty.MEDIUM);
        setDifficulty(Items.CHAINMAIL_BOOTS, Difficulty.MEDIUM);

        setDifficulty(Items.LEATHER_HELMET, Difficulty.MEDIUM);
        setDifficulty(Items.LEATHER_CHESTPLATE, Difficulty.MEDIUM);
        setDifficulty(Items.LEATHER_LEGGINGS, Difficulty.MEDIUM);
        setDifficulty(Items.LEATHER_BOOTS, Difficulty.MEDIUM);
        setDifficulty(Items.LEATHER_HORSE_ARMOR, Difficulty.MEDIUM);

        setDifficulty(Items.BREAD, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_BEEF, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_PORKCHOP, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_CHICKEN, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_MUTTON, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_RABBIT, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_COD, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_SALMON, Difficulty.MEDIUM);
        setDifficulty(Items.BAKED_POTATO, Difficulty.MEDIUM);
        setDifficulty(Items.PUMPKIN_PIE, Difficulty.MEDIUM);
        setDifficulty(Items.COOKIE, Difficulty.MEDIUM);
        setDifficulty(Items.SUSPICIOUS_STEW, Difficulty.MEDIUM);
        setDifficulty(Items.CAKE, Difficulty.MEDIUM);
        setDifficulty(Items.MUSHROOM_STEW, Difficulty.MEDIUM);
        setDifficulty(Items.BEETROOT_SOUP, Difficulty.MEDIUM);
        setDifficulty(Items.RABBIT_STEW, Difficulty.MEDIUM);

        Item[] boats = {Items.OAK_BOAT, Items.SPRUCE_BOAT, Items.BIRCH_BOAT, Items.JUNGLE_BOAT,
                       Items.ACACIA_BOAT, Items.DARK_OAK_BOAT, Items.MANGROVE_BOAT, Items.CHERRY_BOAT,
                       Items.PALE_OAK_BOAT, Items.BAMBOO_RAFT};
        for (Item boat : boats) setDifficulty(boat, Difficulty.MEDIUM);
        
        Item[] chestBoats = {Items.OAK_CHEST_BOAT, Items.SPRUCE_CHEST_BOAT, Items.BIRCH_CHEST_BOAT,
                            Items.JUNGLE_CHEST_BOAT, Items.ACACIA_CHEST_BOAT, Items.DARK_OAK_CHEST_BOAT,
                            Items.MANGROVE_CHEST_BOAT, Items.CHERRY_CHEST_BOAT, Items.PALE_OAK_CHEST_BOAT,
                            Items.BAMBOO_CHEST_RAFT};
        for (Item boat : chestBoats) setDifficulty(boat, Difficulty.MEDIUM);
        
        setDifficulty(Items.MINECART, Difficulty.MEDIUM);
        setDifficulty(Items.CHEST_MINECART, Difficulty.MEDIUM);
        setDifficulty(Items.FURNACE_MINECART, Difficulty.MEDIUM);
        setDifficulty(Items.TNT_MINECART, Difficulty.MEDIUM);
        setDifficulty(Items.HOPPER_MINECART, Difficulty.MEDIUM);
        setDifficulty(Items.RAIL, Difficulty.MEDIUM);
        setDifficulty(Items.POWERED_RAIL, Difficulty.MEDIUM);
        setDifficulty(Items.DETECTOR_RAIL, Difficulty.MEDIUM);
        setDifficulty(Items.ACTIVATOR_RAIL, Difficulty.MEDIUM);

        setDifficulty(Items.PAINTING, Difficulty.MEDIUM);
        setDifficulty(Items.ITEM_FRAME, Difficulty.MEDIUM);
        setDifficulty(Items.GLOW_ITEM_FRAME, Difficulty.MEDIUM);
        setDifficulty(Items.ARMOR_STAND, Difficulty.MEDIUM);
        setDifficulty(Items.FLOWER_POT, Difficulty.MEDIUM);
        setDifficulty(Items.DECORATED_POT, Difficulty.MEDIUM);

        setDifficulty(Items.PAPER, Difficulty.MEDIUM);
        setDifficulty(Items.SUGAR, Difficulty.MEDIUM);
        setDifficulty(Items.BOOK, Difficulty.MEDIUM);
        setDifficulty(Items.BOOKSHELF, Difficulty.MEDIUM);
        setDifficulty(Items.CHISELED_BOOKSHELF, Difficulty.MEDIUM);
        setDifficulty(Items.WRITABLE_BOOK, Difficulty.MEDIUM);
        setDifficulty(Items.MAP, Difficulty.MEDIUM);
        setDifficulty(Items.LEAD, Difficulty.MEDIUM);
        setDifficulty(Items.NAME_TAG, Difficulty.MEDIUM);
        setDifficulty(Items.SADDLE, Difficulty.MEDIUM);

        Item[] dyes = {Items.DYE.white(), Items.DYE.orange(), Items.DYE.magenta(), Items.DYE.lightBlue(),
                      Items.DYE.yellow(), Items.DYE.lime(), Items.DYE.pink(), Items.DYE.gray(),
                      Items.DYE.lightGray(), Items.DYE.cyan(), Items.DYE.purple(), Items.DYE.blue(),
                      Items.DYE.brown(), Items.DYE.green(), Items.DYE.red(), Items.DYE.black(), Items.INK_SAC};
        for (Item dye : dyes) setDifficulty(dye, Difficulty.MEDIUM);

        setDifficulty(Items.LOOM, Difficulty.MEDIUM);
        setDifficulty(Items.STONECUTTER, Difficulty.MEDIUM);
        setDifficulty(Items.CARTOGRAPHY_TABLE, Difficulty.MEDIUM);
        setDifficulty(Items.FLETCHING_TABLE, Difficulty.MEDIUM);
        setDifficulty(Items.SMITHING_TABLE, Difficulty.MEDIUM);
        setDifficulty(Items.GRINDSTONE, Difficulty.MEDIUM);
        setDifficulty(Items.ANVIL, Difficulty.MEDIUM);
        setDifficulty(Items.LECTERN, Difficulty.MEDIUM);
        setDifficulty(Items.COMPOSTER, Difficulty.MEDIUM);
        setDifficulty(Items.CAULDRON, Difficulty.MEDIUM);

        setDifficulty(Items.CUT_COPPER.weathering().unaffected(), Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_BLOCK.weathering().exposed(), Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_BLOCK.weathering().weathered(), Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_BLOCK.weathering().oxidized(), Difficulty.MEDIUM);
        setDifficulty(Items.LIGHTNING_ROD.weathering().unaffected(), Difficulty.MEDIUM);

        setDifficultyByString("minecraft:wind_charge", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:crafter", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:copper_grate", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:copper_bulb", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:copper_door", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:copper_trapdoor", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:tuff_bricks", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:polished_tuff", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:chiseled_tuff", Difficulty.MEDIUM);
        setDifficultyByString("minecraft:chiseled_copper", Difficulty.MEDIUM);
    }
    
    private static void initHardItems() {
        setDifficulty(Items.GOLD_INGOT, Difficulty.HARD);
        setDifficulty(Items.GOLD_ORE, Difficulty.HARD);
        setDifficulty(Items.DEEPSLATE_GOLD_ORE, Difficulty.HARD);
        setDifficulty(Items.RAW_GOLD, Difficulty.HARD);
        setDifficulty(Items.GOLD_BLOCK, Difficulty.HARD);
        setDifficulty(Items.REDSTONE, Difficulty.HARD);
        setDifficulty(Items.REDSTONE_ORE, Difficulty.HARD);
        setDifficulty(Items.DEEPSLATE_REDSTONE_ORE, Difficulty.HARD);
        setDifficulty(Items.REDSTONE_BLOCK, Difficulty.HARD);
        setDifficulty(Items.LAPIS_LAZULI, Difficulty.HARD);
        setDifficulty(Items.LAPIS_ORE, Difficulty.HARD);
        setDifficulty(Items.DEEPSLATE_LAPIS_ORE, Difficulty.HARD);
        setDifficulty(Items.LAPIS_BLOCK, Difficulty.HARD);
        setDifficulty(Items.DIAMOND, Difficulty.HARD);
        setDifficulty(Items.DIAMOND_ORE, Difficulty.HARD);
        setDifficulty(Items.DEEPSLATE_DIAMOND_ORE, Difficulty.HARD);
        setDifficulty(Items.EMERALD, Difficulty.HARD);
        setDifficulty(Items.EMERALD_ORE, Difficulty.HARD);
        setDifficulty(Items.DEEPSLATE_EMERALD_ORE, Difficulty.HARD);
        setDifficulty(Items.EMERALD_BLOCK, Difficulty.HARD);

        setDifficulty(Items.GOLDEN_PICKAXE, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_SWORD, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_AXE, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_SHOVEL, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_HOE, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_HELMET, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_CHESTPLATE, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_LEGGINGS, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_BOOTS, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_HORSE_ARMOR, Difficulty.HARD);

        setDifficulty(Items.ENCHANTING_TABLE, Difficulty.HARD);
        setDifficulty(Items.PISTON, Difficulty.HARD);
        setDifficulty(Items.STICKY_PISTON, Difficulty.HARD);
        setDifficulty(Items.DISPENSER, Difficulty.HARD);
        setDifficulty(Items.DROPPER, Difficulty.HARD);
        setDifficulty(Items.HOPPER, Difficulty.HARD);
        setDifficulty(Items.OBSERVER, Difficulty.HARD);
        setDifficulty(Items.COMPARATOR, Difficulty.HARD);
        setDifficulty(Items.REPEATER, Difficulty.HARD);
        setDifficulty(Items.REDSTONE_LAMP, Difficulty.HARD);
        setDifficulty(Items.REDSTONE_TORCH, Difficulty.HARD);
        setDifficulty(Items.DAYLIGHT_DETECTOR, Difficulty.HARD);
        setDifficulty(Items.TRIPWIRE_HOOK, Difficulty.HARD);
        setDifficulty(Items.LEVER, Difficulty.HARD);
        setDifficulty(Items.TNT, Difficulty.HARD);
        setDifficulty(Items.NOTE_BLOCK, Difficulty.HARD);
        setDifficulty(Items.JUKEBOX, Difficulty.HARD);

        setDifficulty(Items.NETHERRACK, Difficulty.HARD);
        setDifficulty(Items.SOUL_SAND, Difficulty.HARD);
        setDifficulty(Items.SOUL_SOIL, Difficulty.HARD);
        setDifficulty(Items.GLOWSTONE, Difficulty.HARD);
        setDifficulty(Items.GLOWSTONE_DUST, Difficulty.HARD);
        setDifficulty(Items.QUARTZ, Difficulty.HARD);
        setDifficulty(Items.NETHER_QUARTZ_ORE, Difficulty.HARD);
        setDifficulty(Items.QUARTZ_BLOCK, Difficulty.HARD);
        setDifficulty(Items.MAGMA_BLOCK, Difficulty.HARD);
        setDifficulty(Items.MAGMA_CREAM, Difficulty.HARD);
        setDifficulty(Items.CRYING_OBSIDIAN, Difficulty.HARD);
        setDifficulty(Items.NETHER_BRICKS, Difficulty.HARD);
        setDifficulty(Items.RED_NETHER_BRICKS, Difficulty.HARD);
        setDifficulty(Items.NETHER_WART_BLOCK, Difficulty.HARD);
        setDifficulty(Items.WARPED_WART_BLOCK, Difficulty.HARD);
        setDifficulty(Items.BASALT, Difficulty.HARD);
        setDifficulty(Items.BLACKSTONE, Difficulty.HARD);
        setDifficulty(Items.GILDED_BLACKSTONE, Difficulty.HARD);
        setDifficulty(Items.ANCIENT_DEBRIS, Difficulty.HARD);

        setDifficulty(Items.CRIMSON_STEM, Difficulty.HARD);
        setDifficulty(Items.WARPED_STEM, Difficulty.HARD);
        setDifficulty(Items.CRIMSON_PLANKS, Difficulty.HARD);
        setDifficulty(Items.WARPED_PLANKS, Difficulty.HARD);
        setDifficulty(Items.CRIMSON_FUNGUS, Difficulty.HARD);
        setDifficulty(Items.WARPED_FUNGUS, Difficulty.HARD);
        setDifficulty(Items.CRIMSON_ROOTS, Difficulty.HARD);
        setDifficulty(Items.WARPED_ROOTS, Difficulty.HARD);
        setDifficulty(Items.SHROOMLIGHT, Difficulty.HARD);
        setDifficulty(Items.WEEPING_VINES, Difficulty.HARD);
        setDifficulty(Items.TWISTING_VINES, Difficulty.HARD);

        setDifficulty(Items.BOW, Difficulty.HARD);
        setDifficulty(Items.CROSSBOW, Difficulty.HARD);
        setDifficulty(Items.ARROW, Difficulty.HARD);
        setDifficulty(Items.SPECTRAL_ARROW, Difficulty.HARD);
        setDifficulty(Items.TIPPED_ARROW, Difficulty.HARD);
        setDifficulty(Items.TRIDENT, Difficulty.HARD);

        setDifficulty(Items.GLASS_BOTTLE, Difficulty.HARD);
        setDifficulty(Items.EXPERIENCE_BOTTLE, Difficulty.HARD);
        setDifficulty(Items.HONEY_BOTTLE, Difficulty.HARD);
        setDifficulty(Items.DRAGON_BREATH, Difficulty.HARD);
        setDifficulty(Items.FERMENTED_SPIDER_EYE, Difficulty.HARD);
        setDifficulty(Items.GLISTERING_MELON_SLICE, Difficulty.HARD);
        setDifficulty(Items.GOLDEN_CARROT, Difficulty.HARD);

        setDifficulty(Items.PRISMARINE, Difficulty.HARD);
        setDifficulty(Items.PRISMARINE_BRICKS, Difficulty.HARD);
        setDifficulty(Items.DARK_PRISMARINE, Difficulty.HARD);
        setDifficulty(Items.PRISMARINE_SHARD, Difficulty.HARD);
        setDifficulty(Items.PRISMARINE_CRYSTALS, Difficulty.HARD);
        setDifficulty(Items.NAUTILUS_SHELL, Difficulty.HARD);
        setDifficulty(Items.TURTLE_HELMET, Difficulty.HARD);
        setDifficulty(Items.TURTLE_SCUTE, Difficulty.HARD);
        setDifficulty(Items.TURTLE_EGG, Difficulty.HARD);

        setDifficulty(Items.SCULK, Difficulty.HARD);
        setDifficulty(Items.SCULK_VEIN, Difficulty.HARD);
        setDifficulty(Items.SCULK_CATALYST, Difficulty.HARD);
        setDifficulty(Items.SCULK_SENSOR, Difficulty.HARD);
        setDifficulty(Items.CALIBRATED_SCULK_SENSOR, Difficulty.HARD);
        setDifficulty(Items.SCULK_SHRIEKER, Difficulty.HARD);
        setDifficulty(Items.ECHO_SHARD, Difficulty.HARD);

        setDifficulty(Items.SLIME_BALL, Difficulty.HARD);
        setDifficulty(Items.SLIME_BLOCK, Difficulty.HARD);
        setDifficulty(Items.HONEY_BLOCK, Difficulty.HARD);
        setDifficulty(Items.HONEYCOMB, Difficulty.HARD);
        setDifficulty(Items.SPIDER_EYE, Difficulty.HARD);
        setDifficulty(Items.ROTTEN_FLESH, Difficulty.HARD);
        setDifficulty(Items.GUNPOWDER, Difficulty.HARD);
        setDifficulty(Items.FIRE_CHARGE, Difficulty.HARD);

        setDifficultyByString("minecraft:trial_key", Difficulty.HARD);
        setDifficultyByString("minecraft:ominous_trial_key", Difficulty.HARD);
        setDifficultyByString("minecraft:breeze_rod", Difficulty.HARD);
        setDifficultyByString("minecraft:trial_spawner", Difficulty.HARD);
        setDifficultyByString("minecraft:vault", Difficulty.HARD);
        setDifficultyByString("minecraft:ominous_bottle", Difficulty.HARD);

        setDifficultyByString("minecraft:coast_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:dune_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:wayfinder_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:raiser_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:shaper_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:host_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:wild_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:ward_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:sentry_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:vex_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:tide_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:snout_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:rib_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:eye_armor_trim_smithing_template", Difficulty.HARD);
        setDifficultyByString("minecraft:spire_armor_trim_smithing_template", Difficulty.HARD);
    }
    
    private static void initVeryHardItems() {
        setDifficulty(Items.ENDER_PEARL, Difficulty.VERY_HARD);
        setDifficulty(Items.ENDER_EYE, Difficulty.VERY_HARD);
        setDifficulty(Items.END_STONE, Difficulty.VERY_HARD);
        setDifficulty(Items.END_STONE_BRICKS, Difficulty.VERY_HARD);
        setDifficulty(Items.PURPUR_BLOCK, Difficulty.VERY_HARD);
        setDifficulty(Items.PURPUR_PILLAR, Difficulty.VERY_HARD);
        setDifficulty(Items.CHORUS_FRUIT, Difficulty.VERY_HARD);
        setDifficulty(Items.POPPED_CHORUS_FRUIT, Difficulty.VERY_HARD);
        setDifficulty(Items.CHORUS_FLOWER, Difficulty.VERY_HARD);
        setDifficulty(Items.CHORUS_PLANT, Difficulty.VERY_HARD);

        setDifficulty(Items.BLAZE_ROD, Difficulty.VERY_HARD);
        setDifficulty(Items.BLAZE_POWDER, Difficulty.VERY_HARD);
        setDifficulty(Items.GHAST_TEAR, Difficulty.VERY_HARD);
        setDifficulty(Items.NETHER_WART, Difficulty.VERY_HARD);
        setDifficulty(Items.NETHER_STAR, Difficulty.VERY_HARD);

        setDifficulty(Items.NETHERITE_SCRAP, Difficulty.VERY_HARD);
        setDifficulty(Items.NETHERITE_INGOT, Difficulty.VERY_HARD);

        setDifficulty(Items.DIAMOND_BLOCK, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_HELMET, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_CHESTPLATE, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_LEGGINGS, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_BOOTS, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_SWORD, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_PICKAXE, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_AXE, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_SHOVEL, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_HOE, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_HORSE_ARMOR, Difficulty.VERY_HARD);

        setDifficulty(Items.OBSIDIAN, Difficulty.VERY_HARD);
        setDifficulty(Items.RESPAWN_ANCHOR, Difficulty.VERY_HARD);
        setDifficulty(Items.LODESTONE, Difficulty.VERY_HARD);

        setDifficulty(Items.ENCHANTED_BOOK, Difficulty.VERY_HARD);
        setDifficulty(Items.POTION, Difficulty.VERY_HARD);
        setDifficulty(Items.SPLASH_POTION, Difficulty.VERY_HARD);
        setDifficulty(Items.LINGERING_POTION, Difficulty.VERY_HARD);
        setDifficulty(Items.BREWING_STAND, Difficulty.VERY_HARD);

        setDifficulty(Items.PHANTOM_MEMBRANE, Difficulty.VERY_HARD);
        setDifficulty(Items.RABBIT_FOOT, Difficulty.VERY_HARD);
        setDifficulty(Items.RABBIT_HIDE, Difficulty.VERY_HARD);

        setDifficulty(Items.SKELETON_SKULL, Difficulty.VERY_HARD);
        setDifficulty(Items.ZOMBIE_HEAD, Difficulty.VERY_HARD);
        setDifficulty(Items.CREEPER_HEAD, Difficulty.VERY_HARD);
        setDifficulty(Items.PIGLIN_HEAD, Difficulty.VERY_HARD);

        Item[] commonDiscs = {Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
                             Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
                             Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
                             Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT};
        for (Item disc : commonDiscs) setDifficulty(disc, Difficulty.VERY_HARD);
        
        setDifficulty(Items.DISC_FRAGMENT_5, Difficulty.VERY_HARD);

        Item[] banners = {Items.BANNER.white(), Items.BANNER.orange(), Items.BANNER.magenta(),
                         Items.BANNER.lightBlue(), Items.BANNER.yellow(), Items.BANNER.lime(),
                         Items.BANNER.pink(), Items.BANNER.gray(), Items.BANNER.lightGray(),
                         Items.BANNER.cyan(), Items.BANNER.purple(), Items.BANNER.blue(),
                         Items.BANNER.brown(), Items.BANNER.green(), Items.BANNER.red(), Items.BANNER.black()};
        for (Item banner : banners) setDifficulty(banner, Difficulty.VERY_HARD);
        
        setDifficulty(Items.CREEPER_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.SKULL_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.FLOWER_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.MOJANG_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.GLOBE_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.PIGLIN_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.FLOW_BANNER_PATTERN, Difficulty.VERY_HARD);
        setDifficulty(Items.GUSTER_BANNER_PATTERN, Difficulty.VERY_HARD);

        setDifficulty(Items.ANGLER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.ARCHER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.ARMS_UP_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.BLADE_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.BREWER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.BURN_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.DANGER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.EXPLORER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.FLOW_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.FRIEND_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.GUSTER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.HEART_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.HEARTBREAK_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.HOWL_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.MINER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.MOURNER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.PLENTY_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.PRIZE_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.SCRAPE_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.SHEAF_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.SHELTER_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.SKULL_POTTERY_SHERD, Difficulty.VERY_HARD);
        setDifficulty(Items.SNORT_POTTERY_SHERD, Difficulty.VERY_HARD);

        setDifficultyByString("minecraft:heavy_core", Difficulty.VERY_HARD);
        setDifficultyByString("minecraft:mace", Difficulty.VERY_HARD);

        setDifficultyByString("minecraft:silence_armor_trim_smithing_template", Difficulty.VERY_HARD);
    }
    
    private static void initDarkNightItems() {
        setDifficulty(Items.TOTEM_OF_UNDYING, Difficulty.DARK_NIGHT);
        setDifficulty(Items.WITHER_SKELETON_SKULL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.ELYTRA, Difficulty.DARK_NIGHT);
        setDifficulty(Items.DRAGON_EGG, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHER_STAR, Difficulty.DARK_NIGHT);
        setDifficulty(Items.BEACON, Difficulty.DARK_NIGHT);
        setDifficulty(Items.TRIDENT, Difficulty.DARK_NIGHT);
        setDifficulty(Items.HEART_OF_THE_SEA, Difficulty.DARK_NIGHT);
        setDifficulty(Items.DRAGON_HEAD, Difficulty.DARK_NIGHT);

        setDifficulty(Items.NETHERITE_HELMET, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_CHESTPLATE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_LEGGINGS, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_BOOTS, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_PICKAXE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_SWORD, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_AXE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_SHOVEL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_HOE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_BLOCK, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Difficulty.DARK_NIGHT);

        Item[] shulkerBoxes = {Items.SHULKER_BOX, Items.DYED_SHULKER_BOX.white(), Items.DYED_SHULKER_BOX.orange(),
                              Items.DYED_SHULKER_BOX.magenta(), Items.DYED_SHULKER_BOX.lightBlue(), Items.DYED_SHULKER_BOX.yellow(),
                              Items.DYED_SHULKER_BOX.lime(), Items.DYED_SHULKER_BOX.pink(), Items.DYED_SHULKER_BOX.gray(),
                              Items.DYED_SHULKER_BOX.lightGray(), Items.DYED_SHULKER_BOX.cyan(), Items.DYED_SHULKER_BOX.purple(),
                              Items.DYED_SHULKER_BOX.blue(), Items.DYED_SHULKER_BOX.brown(), Items.DYED_SHULKER_BOX.green(),
                              Items.DYED_SHULKER_BOX.red(), Items.DYED_SHULKER_BOX.black()};
        for (Item box : shulkerBoxes) setDifficulty(box, Difficulty.DARK_NIGHT);
        
        setDifficulty(Items.SHULKER_SHELL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.END_CRYSTAL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.CONDUIT, Difficulty.DARK_NIGHT);
        setDifficulty(Items.SEA_LANTERN, Difficulty.DARK_NIGHT);
        setDifficulty(Items.SPONGE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.WET_SPONGE, Difficulty.DARK_NIGHT);

        setDifficulty(Items.MUSIC_DISC_PIGSTEP, Difficulty.DARK_NIGHT);
        setDifficulty(Items.MUSIC_DISC_OTHERSIDE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.MUSIC_DISC_5, Difficulty.DARK_NIGHT);
        setDifficulty(Items.MUSIC_DISC_RELIC, Difficulty.DARK_NIGHT);

        setDifficultyByString("minecraft:music_disc_precipice", Difficulty.DARK_NIGHT);
        setDifficultyByString("minecraft:music_disc_creator", Difficulty.DARK_NIGHT);
        setDifficultyByString("minecraft:music_disc_creator_music_box", Difficulty.DARK_NIGHT);
        setDifficultyByString("minecraft:flow_armor_trim_smithing_template", Difficulty.DARK_NIGHT);
        setDifficultyByString("minecraft:bolt_armor_trim_smithing_template", Difficulty.DARK_NIGHT);

        setDifficultyByString("minecraft:silence_armor_trim_smithing_template", Difficulty.DARK_NIGHT);

        setDifficulty(Items.ENCHANTED_GOLDEN_APPLE, Difficulty.DARK_NIGHT);

        setDifficulty(Items.ELYTRA, Difficulty.DARK_NIGHT);
        setDifficulty(Items.END_ROD, Difficulty.DARK_NIGHT);
    }
    
    private static void initUnobtainableItems() {
        markUnobtainable(Items.AIR);
        
        markUnobtainable(Items.COMMAND_BLOCK);
        markUnobtainable(Items.CHAIN_COMMAND_BLOCK);
        markUnobtainable(Items.REPEATING_COMMAND_BLOCK);
        markUnobtainable(Items.COMMAND_BLOCK_MINECART);
        markUnobtainable(Items.BARRIER);
        markUnobtainable(Items.LIGHT);
        markUnobtainable(Items.STRUCTURE_BLOCK);
        markUnobtainable(Items.STRUCTURE_VOID);
        markUnobtainable(Items.JIGSAW);
        markUnobtainable(Items.DEBUG_STICK);
        markUnobtainable(Items.KNOWLEDGE_BOOK);
        markUnobtainable(Items.SPAWNER);
        markUnobtainable(Items.BEDROCK);
        markUnobtainable(Items.END_PORTAL_FRAME);
        
        markUnobtainable(Items.REINFORCED_DEEPSLATE);
        markUnobtainable(Items.BUDDING_AMETHYST);
        markUnobtainable(Items.FARMLAND);
        markUnobtainable(Items.DIRT_PATH);
        markUnobtainable(Items.FROGSPAWN);
        markUnobtainable(Items.CHORUS_PLANT);
        markUnobtainable(Items.TEST_BLOCK);
        markUnobtainable(Items.TEST_INSTANCE_BLOCK);

        Item[] infestedBlocks = {Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE, Items.INFESTED_STONE_BRICKS,
                                Items.INFESTED_MOSSY_STONE_BRICKS, Items.INFESTED_CRACKED_STONE_BRICKS,
                                Items.INFESTED_CHISELED_STONE_BRICKS, Items.INFESTED_DEEPSLATE};
        for (Item block : infestedBlocks) markUnobtainable(block);

        markUnobtainable(Items.PETRIFIED_OAK_SLAB);
        markUnobtainable(Items.PLAYER_HEAD);

        markAllSpawnEggs();

        markUnobtainable(Items.TRIAL_SPAWNER);
        markUnobtainable(Items.VAULT);

        markUnobtainable(Items.SUSPICIOUS_SAND);
        markUnobtainable(Items.SUSPICIOUS_GRAVEL);
    }
    
    private static void markAllSpawnEggs() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem) {
                markUnobtainable(item);
            }
        }
    }
    
    private static void setDifficulty(Item item, Difficulty difficulty) {
        ITEM_DIFFICULTIES.put(item, difficulty);
    }
    
    private static void setDifficultyByString(String itemId, Difficulty difficulty) {
        try {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(itemId);
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                ITEM_DIFFICULTIES.put(BuiltInRegistries.ITEM.getValue(id), difficulty);
            }
        } catch (Exception ignored) {}
    }
    
    private static void markUnobtainable(Item item) {
        UNOBTAINABLE_ITEMS.add(item);
    }
    
    public static Difficulty getDifficulty(Item item) {
        return ITEM_DIFFICULTIES.getOrDefault(item, Difficulty.MEDIUM);
    }
    
    public static boolean isUnobtainable(Item item) {
        return UNOBTAINABLE_ITEMS.contains(item);
    }
    
    public static List<Item> getAllObtainableItems() {
        return cachedObtainableItems != null ? cachedObtainableItems : Collections.emptyList();
    }
    
    public static List<Item> getAllItems(boolean includeUnobtainable) {
        if (!includeUnobtainable) {
            return getAllObtainableItems();
        }
        
        return cachedAllItems;
    }
    
    public static Item getRandomItem(boolean includeUnobtainable) {
        List<Item> items = getAllItems(includeUnobtainable);
        if (items.isEmpty()) return Items.DIRT;
        return items.get(new Random().nextInt(items.size()));
    }

    public static List<Item> getItemsForDifficulty(Difficulty difficulty) {
        return cachedDifficultyItems.getOrDefault(difficulty, Collections.emptyList());
    }
}

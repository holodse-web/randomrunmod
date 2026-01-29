package com.randomrun.challenges.classic.data;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.*;
import java.util.Random;

public class ItemDifficulty {
    
    public enum Difficulty {
        EASY("Легкая", 30, 120, 0x55FF55),              // 30сек - 2мин
        MEDIUM("Средняя", 180, 540, 0xFFFF55),          // 3мин - 9мин
        HARD("Тяжелая", 600, 1200, 0xFFAA00),           // 10мин - 20мин
        VERY_HARD("Очень тяжелая", 1200, 3000, 0xFF5555), // 20мин - 50мин
        DARK_NIGHT("Темная ночь...", 3000, 7200, 0xAA00AA); // 50мин - 2часа
        
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
    
    public static void initialize() {
        
        setDifficulty(Items.DIRT, Difficulty.EASY);
        setDifficulty(Items.COBBLESTONE, Difficulty.EASY);
        setDifficulty(Items.SAND, Difficulty.EASY);
        setDifficulty(Items.GRAVEL, Difficulty.EASY);
        setDifficulty(Items.GRASS_BLOCK, Difficulty.EASY);
        
        
        setDifficulty(Items.OAK_LOG, Difficulty.EASY);
        setDifficulty(Items.SPRUCE_LOG, Difficulty.EASY);
        setDifficulty(Items.BIRCH_LOG, Difficulty.EASY);
        setDifficulty(Items.JUNGLE_LOG, Difficulty.EASY);
        setDifficulty(Items.ACACIA_LOG, Difficulty.EASY);
        setDifficulty(Items.DARK_OAK_LOG, Difficulty.EASY);
        setDifficulty(Items.MANGROVE_LOG, Difficulty.EASY);
        setDifficulty(Items.CHERRY_LOG, Difficulty.EASY);
        
       
        setDifficulty(Items.OAK_PLANKS, Difficulty.EASY);
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
        setDifficulty(Items.TORCH, Difficulty.EASY);
        setDifficulty(Items.FURNACE, Difficulty.EASY);
        setDifficulty(Items.CHEST, Difficulty.EASY);
        
       
        setDifficulty(Items.OAK_SAPLING, Difficulty.EASY);
        setDifficulty(Items.SPRUCE_SAPLING, Difficulty.EASY);
        setDifficulty(Items.BIRCH_SAPLING, Difficulty.EASY);
        setDifficulty(Items.JUNGLE_SAPLING, Difficulty.EASY);
        setDifficulty(Items.ACACIA_SAPLING, Difficulty.EASY);
        setDifficulty(Items.DARK_OAK_SAPLING, Difficulty.EASY);
        setDifficulty(Items.DANDELION, Difficulty.EASY);
        setDifficulty(Items.POPPY, Difficulty.EASY);
        setDifficulty(Items.APPLE, Difficulty.EASY);
        setDifficulty(Items.BEEF, Difficulty.EASY);
        setDifficulty(Items.PORKCHOP, Difficulty.EASY);
        setDifficulty(Items.CHICKEN, Difficulty.EASY);
        setDifficulty(Items.MUTTON, Difficulty.EASY);
        
       
        setDifficulty(Items.WHITE_WOOL, Difficulty.EASY);
        setDifficulty(Items.STRING, Difficulty.EASY);
        
       
        setDifficulty(Items.IRON_INGOT, Difficulty.MEDIUM);
        setDifficulty(Items.COPPER_INGOT, Difficulty.MEDIUM);
        setDifficulty(Items.COAL, Difficulty.MEDIUM);
        setDifficulty(Items.GLASS, Difficulty.MEDIUM);
        
      
        setDifficulty(Items.IRON_PICKAXE, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_SWORD, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_AXE, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_SHOVEL, Difficulty.MEDIUM);
        setDifficulty(Items.IRON_HOE, Difficulty.MEDIUM);
        setDifficulty(Items.BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.WATER_BUCKET, Difficulty.MEDIUM);
        setDifficulty(Items.SHIELD, Difficulty.MEDIUM);
        setDifficulty(Items.SHEARS, Difficulty.MEDIUM);
        setDifficulty(Items.COMPASS, Difficulty.MEDIUM);
        
     
        setDifficulty(Items.BREAD, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_BEEF, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_PORKCHOP, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_CHICKEN, Difficulty.MEDIUM);
        setDifficulty(Items.COOKED_MUTTON, Difficulty.MEDIUM);
        setDifficulty(Items.SUSPICIOUS_STEW, Difficulty.MEDIUM);
        
       
        setDifficulty(Items.OAK_BOAT, Difficulty.MEDIUM);
        setDifficulty(Items.SPRUCE_BOAT, Difficulty.MEDIUM);
        setDifficulty(Items.BIRCH_BOAT, Difficulty.MEDIUM);
        setDifficulty(Items.JUNGLE_BOAT, Difficulty.MEDIUM);
        setDifficulty(Items.ACACIA_BOAT, Difficulty.MEDIUM);
        setDifficulty(Items.DARK_OAK_BOAT, Difficulty.MEDIUM);
        setDifficulty(Items.PAINTING, Difficulty.MEDIUM);
        setDifficulty(Items.PAPER, Difficulty.MEDIUM);
        setDifficulty(Items.SUGAR, Difficulty.MEDIUM);
        
       
        setDifficulty(Items.GOLD_INGOT, Difficulty.HARD);
        setDifficulty(Items.REDSTONE, Difficulty.HARD);
        setDifficulty(Items.LAPIS_LAZULI, Difficulty.HARD);
        setDifficulty(Items.DIAMOND, Difficulty.HARD);
        
        
        setDifficulty(Items.ENCHANTING_TABLE, Difficulty.HARD);
        setDifficulty(Items.PISTON, Difficulty.HARD);
        setDifficulty(Items.DISPENSER, Difficulty.HARD);
        setDifficulty(Items.CLOCK, Difficulty.HARD);
        
        
        setDifficulty(Items.NETHERRACK, Difficulty.HARD);
        setDifficulty(Items.SOUL_SAND, Difficulty.HARD);
        setDifficulty(Items.GLOWSTONE, Difficulty.HARD);
        setDifficulty(Items.QUARTZ, Difficulty.HARD);
        
       
        setDifficulty(Items.ENDER_PEARL, Difficulty.VERY_HARD);
        setDifficulty(Items.BLAZE_ROD, Difficulty.VERY_HARD);
        setDifficulty(Items.ENDER_EYE, Difficulty.VERY_HARD);
        
        
        setDifficulty(Items.NETHERITE_SCRAP, Difficulty.VERY_HARD);
        setDifficulty(Items.NETHERITE_INGOT, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_BLOCK, Difficulty.VERY_HARD);
        setDifficulty(Items.OBSIDIAN, Difficulty.VERY_HARD);
        
       
        setDifficulty(Items.DIAMOND_HELMET, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_CHESTPLATE, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_LEGGINGS, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_BOOTS, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_SWORD, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_PICKAXE, Difficulty.VERY_HARD);
        setDifficulty(Items.DIAMOND_AXE, Difficulty.VERY_HARD);
        setDifficulty(Items.BOW, Difficulty.HARD);
        setDifficulty(Items.ENCHANTED_BOOK, Difficulty.VERY_HARD);
        setDifficulty(Items.CROSSBOW, Difficulty.HARD);
        
      
        setDifficulty(Items.TOTEM_OF_UNDYING, Difficulty.DARK_NIGHT);
        setDifficulty(Items.GHAST_TEAR, Difficulty.VERY_HARD);
        setDifficulty(Items.SHULKER_SHELL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.SHULKER_BOX, Difficulty.DARK_NIGHT);
        
       
        setDifficulty(Items.POTION, Difficulty.VERY_HARD);
        setDifficulty(Items.BREWING_STAND, Difficulty.VERY_HARD);
        setDifficulty(Items.BLAZE_POWDER, Difficulty.VERY_HARD);
        setDifficulty(Items.NETHER_WART, Difficulty.VERY_HARD);
        
        setDifficulty(Items.SKELETON_SKULL, Difficulty.VERY_HARD);
        setDifficulty(Items.WITHER_SKELETON_SKULL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.ZOMBIE_HEAD, Difficulty.VERY_HARD);
        setDifficulty(Items.CREEPER_HEAD, Difficulty.VERY_HARD);
        setDifficulty(Items.PIGLIN_HEAD, Difficulty.VERY_HARD);
        
      
        setDifficulty(Items.ELYTRA, Difficulty.DARK_NIGHT);
        setDifficulty(Items.DRAGON_EGG, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHER_STAR, Difficulty.DARK_NIGHT);
        setDifficulty(Items.BEACON, Difficulty.DARK_NIGHT);
        
      
        setDifficulty(Items.TRIDENT, Difficulty.DARK_NIGHT);
        setDifficulty(Items.HEART_OF_THE_SEA, Difficulty.DARK_NIGHT);
        
      
        setDifficulty(Items.NETHERITE_HELMET, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_CHESTPLATE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_LEGGINGS, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_BOOTS, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_PICKAXE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_SWORD, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_AXE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_SHOVEL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_HOE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.END_CRYSTAL, Difficulty.DARK_NIGHT);
        setDifficulty(Items.CONDUIT, Difficulty.DARK_NIGHT);
        setDifficulty(Items.SMITHING_TABLE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Difficulty.DARK_NIGHT);
        
    
        setDifficulty(Items.NETHERITE_BLOCK, Difficulty.DARK_NIGHT);
        setDifficulty(Items.SEA_LANTERN, Difficulty.DARK_NIGHT);
        setDifficulty(Items.DRAGON_HEAD, Difficulty.DARK_NIGHT);
        
      
        setDifficulty(Items.MUSIC_DISC_PIGSTEP, Difficulty.DARK_NIGHT);
        setDifficulty(Items.MUSIC_DISC_OTHERSIDE, Difficulty.DARK_NIGHT);
        setDifficulty(Items.MUSIC_DISC_5, Difficulty.DARK_NIGHT);
        setDifficulty(Items.DISC_FRAGMENT_5, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_13, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_CAT, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_BLOCKS, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_CHIRP, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_FAR, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_MALL, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_MELLOHI, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_STAL, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_STRAD, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_WARD, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_11, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_WAIT, Difficulty.VERY_HARD);
        setDifficulty(Items.MUSIC_DISC_RELIC, Difficulty.DARK_NIGHT);
        
      
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
        markUnobtainable(Items.INFESTED_STONE);
        markUnobtainable(Items.INFESTED_COBBLESTONE);
        markUnobtainable(Items.INFESTED_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_MOSSY_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_CRACKED_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_CHISELED_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_DEEPSLATE);
        markUnobtainable(Items.PETRIFIED_OAK_SLAB);
        markUnobtainable(Items.PLAYER_HEAD);
        markUnobtainable(Items.WRITTEN_BOOK);
        markUnobtainable(Items.FILLED_MAP);
        markUnobtainable(Items.BUNDLE);
        
        markUnobtainable(Items.COPPER_DOOR);
        markUnobtainable(Items.EXPOSED_COPPER_DOOR);
        markUnobtainable(Items.WEATHERED_COPPER_DOOR);
        markUnobtainable(Items.OXIDIZED_COPPER_DOOR);
        markUnobtainable(Items.WAXED_COPPER_DOOR);
        markUnobtainable(Items.WAXED_EXPOSED_COPPER_DOOR);
        markUnobtainable(Items.WAXED_WEATHERED_COPPER_DOOR);
        markUnobtainable(Items.WAXED_OXIDIZED_COPPER_DOOR);
        
        markUnobtainable(Items.COPPER_TRAPDOOR);
        markUnobtainable(Items.EXPOSED_COPPER_TRAPDOOR);
        markUnobtainable(Items.WEATHERED_COPPER_TRAPDOOR);
        markUnobtainable(Items.OXIDIZED_COPPER_TRAPDOOR);
        markUnobtainable(Items.WAXED_COPPER_TRAPDOOR);
        markUnobtainable(Items.WAXED_EXPOSED_COPPER_TRAPDOOR);
        markUnobtainable(Items.WAXED_WEATHERED_COPPER_TRAPDOOR);
        markUnobtainable(Items.WAXED_OXIDIZED_COPPER_TRAPDOOR);
        
        markUnobtainable(Items.COPPER_BULB);
        markUnobtainable(Items.EXPOSED_COPPER_BULB);
        markUnobtainable(Items.WEATHERED_COPPER_BULB);
        markUnobtainable(Items.OXIDIZED_COPPER_BULB);
        markUnobtainable(Items.WAXED_COPPER_BULB);
        markUnobtainable(Items.WAXED_EXPOSED_COPPER_BULB);
        markUnobtainable(Items.WAXED_WEATHERED_COPPER_BULB);
        markUnobtainable(Items.WAXED_OXIDIZED_COPPER_BULB);
        
        markUnobtainable(Items.CHISELED_COPPER);
        markUnobtainable(Items.EXPOSED_CHISELED_COPPER);
        markUnobtainable(Items.WEATHERED_CHISELED_COPPER);
        markUnobtainable(Items.OXIDIZED_CHISELED_COPPER);
        markUnobtainable(Items.WAXED_CHISELED_COPPER);
        markUnobtainable(Items.WAXED_EXPOSED_CHISELED_COPPER);
        markUnobtainable(Items.WAXED_WEATHERED_CHISELED_COPPER);
        markUnobtainable(Items.WAXED_OXIDIZED_CHISELED_COPPER);
        
        markUnobtainable(Items.COPPER_GRATE);
        markUnobtainable(Items.EXPOSED_COPPER_GRATE);
        markUnobtainable(Items.WEATHERED_COPPER_GRATE);
        markUnobtainable(Items.OXIDIZED_COPPER_GRATE);
        markUnobtainable(Items.WAXED_COPPER_GRATE);
        markUnobtainable(Items.WAXED_EXPOSED_COPPER_GRATE);
        markUnobtainable(Items.WAXED_WEATHERED_COPPER_GRATE);
        markUnobtainable(Items.WAXED_OXIDIZED_COPPER_GRATE);
        
       
        markUnobtainable(Items.ALLAY_SPAWN_EGG);
        markUnobtainable(Items.AXOLOTL_SPAWN_EGG);
        markUnobtainable(Items.BAT_SPAWN_EGG);
        markUnobtainable(Items.BEE_SPAWN_EGG);
        markUnobtainable(Items.BLAZE_SPAWN_EGG);
        markUnobtainable(Items.CAT_SPAWN_EGG);
        markUnobtainable(Items.CAMEL_SPAWN_EGG);
        markUnobtainable(Items.CAVE_SPIDER_SPAWN_EGG);
        markUnobtainable(Items.CHICKEN_SPAWN_EGG);
        markUnobtainable(Items.COD_SPAWN_EGG);
        markUnobtainable(Items.COW_SPAWN_EGG);
        markUnobtainable(Items.CREEPER_SPAWN_EGG);
        markUnobtainable(Items.DOLPHIN_SPAWN_EGG);
        markUnobtainable(Items.DONKEY_SPAWN_EGG);
        markUnobtainable(Items.DROWNED_SPAWN_EGG);
        markUnobtainable(Items.ELDER_GUARDIAN_SPAWN_EGG);
        markUnobtainable(Items.ENDER_DRAGON_SPAWN_EGG);
        markUnobtainable(Items.ENDERMAN_SPAWN_EGG);
        markUnobtainable(Items.ENDERMITE_SPAWN_EGG);
        markUnobtainable(Items.EVOKER_SPAWN_EGG);
        markUnobtainable(Items.FOX_SPAWN_EGG);
        markUnobtainable(Items.FROG_SPAWN_EGG);
        markUnobtainable(Items.GHAST_SPAWN_EGG);
        markUnobtainable(Items.GLOW_SQUID_SPAWN_EGG);
        markUnobtainable(Items.GOAT_SPAWN_EGG);
        markUnobtainable(Items.GUARDIAN_SPAWN_EGG);
        markUnobtainable(Items.HOGLIN_SPAWN_EGG);
        markUnobtainable(Items.HORSE_SPAWN_EGG);
        markUnobtainable(Items.HUSK_SPAWN_EGG);
        markUnobtainable(Items.IRON_GOLEM_SPAWN_EGG);
        markUnobtainable(Items.LLAMA_SPAWN_EGG);
        markUnobtainable(Items.MAGMA_CUBE_SPAWN_EGG);
        markUnobtainable(Items.MOOSHROOM_SPAWN_EGG);
        markUnobtainable(Items.MULE_SPAWN_EGG);
        markUnobtainable(Items.OCELOT_SPAWN_EGG);
        markUnobtainable(Items.PANDA_SPAWN_EGG);
        markUnobtainable(Items.PARROT_SPAWN_EGG);
        markUnobtainable(Items.PHANTOM_SPAWN_EGG);
        markUnobtainable(Items.PIG_SPAWN_EGG);
        markUnobtainable(Items.PIGLIN_SPAWN_EGG);
        markUnobtainable(Items.PIGLIN_BRUTE_SPAWN_EGG);
        markUnobtainable(Items.PILLAGER_SPAWN_EGG);
        markUnobtainable(Items.POLAR_BEAR_SPAWN_EGG);
        markUnobtainable(Items.PUFFERFISH_SPAWN_EGG);
        markUnobtainable(Items.RABBIT_SPAWN_EGG);
        markUnobtainable(Items.RAVAGER_SPAWN_EGG);
        markUnobtainable(Items.SALMON_SPAWN_EGG);
        markUnobtainable(Items.SHEEP_SPAWN_EGG);
        markUnobtainable(Items.SHULKER_SPAWN_EGG);
        markUnobtainable(Items.SILVERFISH_SPAWN_EGG);
        markUnobtainable(Items.SKELETON_SPAWN_EGG);
        markUnobtainable(Items.SKELETON_HORSE_SPAWN_EGG);
        markUnobtainable(Items.SLIME_SPAWN_EGG);
        markUnobtainable(Items.SNIFFER_SPAWN_EGG);
        markUnobtainable(Items.SNOW_GOLEM_SPAWN_EGG);
        markUnobtainable(Items.SPIDER_SPAWN_EGG);
        markUnobtainable(Items.SQUID_SPAWN_EGG);
        markUnobtainable(Items.STRAY_SPAWN_EGG);
        markUnobtainable(Items.STRIDER_SPAWN_EGG);
        markUnobtainable(Items.TADPOLE_SPAWN_EGG);
        markUnobtainable(Items.TRADER_LLAMA_SPAWN_EGG);
        markUnobtainable(Items.TROPICAL_FISH_SPAWN_EGG);
        markUnobtainable(Items.TURTLE_SPAWN_EGG);
        markUnobtainable(Items.VEX_SPAWN_EGG);
        markUnobtainable(Items.VILLAGER_SPAWN_EGG);
        markUnobtainable(Items.VINDICATOR_SPAWN_EGG);
        markUnobtainable(Items.WANDERING_TRADER_SPAWN_EGG);
        markUnobtainable(Items.WARDEN_SPAWN_EGG);
        markUnobtainable(Items.WITCH_SPAWN_EGG);
        markUnobtainable(Items.WITHER_SPAWN_EGG);
        markUnobtainable(Items.WITHER_SKELETON_SPAWN_EGG);
        markUnobtainable(Items.WOLF_SPAWN_EGG);
        markUnobtainable(Items.ZOGLIN_SPAWN_EGG);
        markUnobtainable(Items.ZOMBIE_SPAWN_EGG);
        markUnobtainable(Items.ZOMBIE_HORSE_SPAWN_EGG);
        markUnobtainable(Items.ZOMBIE_VILLAGER_SPAWN_EGG);
        markUnobtainable(Items.ZOMBIFIED_PIGLIN_SPAWN_EGG);

        
        
        markUnobtainableByString("minecraft:trial_key");
        markUnobtainableByString("minecraft:trial_spawner");
        markUnobtainableByString("minecraft:breeze_rod");
        markUnobtainableByString("minecraft:mace");
        markUnobtainableByString("minecraft:crafter");
        markUnobtainableByString("minecraft:vault");
        markUnobtainableByString("minecraft:heavy_core");
        markUnobtainableByString("minecraft:flow_armor_trim_smithing_template");
        markUnobtainableByString("minecraft:bolt_armor_trim_smithing_template");
        markUnobtainableByString("minecraft:music_disc_precipice");
        markUnobtainableByString("minecraft:music_disc_creator");
        markUnobtainableByString("minecraft:music_disc_creator_music_box");
        
       
        markUnobtainable(Items.BEDROCK);
        markUnobtainable(Items.COMMAND_BLOCK);
        markUnobtainable(Items.CHAIN_COMMAND_BLOCK);
        markUnobtainable(Items.REPEATING_COMMAND_BLOCK);
        markUnobtainable(Items.STRUCTURE_BLOCK);
        markUnobtainable(Items.STRUCTURE_VOID);
        markUnobtainable(Items.JIGSAW);
        markUnobtainable(Items.BARRIER);
        markUnobtainable(Items.LIGHT);
        markUnobtainable(Items.SPAWNER);
        markUnobtainable(Items.END_PORTAL_FRAME);
        markUnobtainable(Items.REINFORCED_DEEPSLATE);
        markUnobtainable(Items.BUDDING_AMETHYST);
        markUnobtainable(Items.FARMLAND);
        markUnobtainable(Items.INFESTED_STONE);
        markUnobtainable(Items.INFESTED_COBBLESTONE);
        markUnobtainable(Items.INFESTED_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_MOSSY_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_CRACKED_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_CHISELED_STONE_BRICKS);
        markUnobtainable(Items.INFESTED_DEEPSLATE);
    }
    
    private static void setDifficulty(Item item, Difficulty difficulty) {
        ITEM_DIFFICULTIES.put(item, difficulty);
    }
    
    private static void markUnobtainable(Item item) {
        UNOBTAINABLE_ITEMS.add(item);
    }
    
    private static void markUnobtainableByString(String itemId) {
        try {
            net.minecraft.util.Identifier id = new net.minecraft.util.Identifier(itemId);
            if (Registries.ITEM.containsId(id)) {
                UNOBTAINABLE_ITEMS.add(Registries.ITEM.get(id));
            }
        } catch (Exception ignored) {}
    }
    
    public static Difficulty getDifficulty(Item item) {
        return ITEM_DIFFICULTIES.getOrDefault(item, Difficulty.MEDIUM);
    }
    
    public static boolean isUnobtainable(Item item) {
        return UNOBTAINABLE_ITEMS.contains(item);
    }
    
    public static List<Item> getAllObtainableItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (!isUnobtainable(item)) {
                items.add(item);
            }
        }
        return items;
    }
    
    public static List<Item> getAllItems(boolean includeUnobtainable) {
        List<Item> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (includeUnobtainable || !isUnobtainable(item)) {
                items.add(item);
            }
        }
        return items;
    }
    
    public static Item getRandomItem(boolean includeUnobtainable) {
        List<Item> items = getAllItems(includeUnobtainable);
        if (items.isEmpty()) return Items.DIRT;
        return items.get(new Random().nextInt(items.size()));
    }
}

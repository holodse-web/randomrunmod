package com.randomrun.challenges.advancement.data;

import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AchievementDifficulty {
    
    public enum Difficulty {
        EASY(30, 90),     // 30s - 1.5m
        MEDIUM(60, 180),  // 1m - 3m
        HARD(180, 600);   // 3m - 10m
        
        private final int minSeconds;
        private final int maxSeconds;
        
        Difficulty(int minSeconds, int maxSeconds) {
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
        }
        
        public int getRandomTime() {
            return minSeconds + new Random().nextInt(maxSeconds - minSeconds + 1);
        }
        
        public String getTimeRangeString() {
            return String.format("%ds - %ds", minSeconds, maxSeconds);
        }
    }
    
    private static final Map<String, Difficulty> DIFFICULTY_MAP = new HashMap<>();
    
    static {
        // EASY
        register("minecraft:story/root", Difficulty.EASY);
        register("minecraft:story/mine_stone", Difficulty.EASY);
        register("minecraft:story/upgrade_tools", Difficulty.EASY);
        register("minecraft:story/smelt_iron", Difficulty.EASY);
        register("minecraft:story/lava_bucket", Difficulty.EASY);
        register("minecraft:story/form_obsidian", Difficulty.EASY);
        register("minecraft:story/deflect_arrow", Difficulty.EASY);
        register("minecraft:nether/root", Difficulty.EASY);
        register("minecraft:husbandry/root", Difficulty.EASY);
        register("minecraft:husbandry/breed_an_animal", Difficulty.EASY);
        register("minecraft:husbandry/plant_seed", Difficulty.EASY);
        register("minecraft:husbandry/tame_an_animal", Difficulty.EASY);
        register("minecraft:adventure/root", Difficulty.EASY);
        register("minecraft:adventure/sleep_in_bed", Difficulty.EASY);
        register("minecraft:adventure/shoot_arrow", Difficulty.EASY);
        
        // MEDIUM
        register("minecraft:story/obtain_armor", Difficulty.MEDIUM); // Moved from EASY (Dress Code)
        register("minecraft:story/enter_the_nether", Difficulty.MEDIUM);
        register("minecraft:story/cure_zombie_villager", Difficulty.MEDIUM);
        register("minecraft:story/follow_ender_eye", Difficulty.MEDIUM);
        register("minecraft:story/enter_the_end", Difficulty.MEDIUM);
        register("minecraft:nether/return_to_sender", Difficulty.MEDIUM);
        register("minecraft:nether/find_bastion", Difficulty.MEDIUM);
        register("minecraft:nether/find_fortress", Difficulty.MEDIUM);
        register("minecraft:nether/obtain_crying_obsidian", Difficulty.MEDIUM);
        register("minecraft:nether/distract_piglin", Difficulty.MEDIUM);
        register("minecraft:nether/ride_strider", Difficulty.MEDIUM);
        register("minecraft:nether/obtain_blaze_rod", Difficulty.MEDIUM);
        register("minecraft:end/root", Difficulty.MEDIUM);
        register("minecraft:end/kill_dragon", Difficulty.MEDIUM);
        register("minecraft:end/dragon_breath", Difficulty.MEDIUM);
        register("minecraft:adventure/kill_a_mob", Difficulty.MEDIUM);
        register("minecraft:adventure/trade", Difficulty.MEDIUM);
        register("minecraft:adventure/ol_betsy", Difficulty.MEDIUM);
        register("minecraft:adventure/whos_the_pillager_now", Difficulty.MEDIUM);
        register("minecraft:adventure/arbalistic", Difficulty.MEDIUM);
        
        // HARD
        register("minecraft:story/enchant_item", Difficulty.HARD);
        register("minecraft:nether/challenge/ghast_in_the_tears", Difficulty.HARD);
        register("minecraft:nether/uneasy_alliance", Difficulty.HARD);
        register("minecraft:nether/loot_bastion", Difficulty.HARD);
        register("minecraft:nether/use_lodestone", Difficulty.HARD);
        register("minecraft:nether/netherite_armor", Difficulty.HARD);
        register("minecraft:nether/get_wither_skull", Difficulty.HARD);
        register("minecraft:nether/summon_wither", Difficulty.HARD);
        register("minecraft:nether/brew_potion", Difficulty.HARD);
        register("minecraft:nether/all_potions", Difficulty.HARD);
        register("minecraft:nether/all_effects", Difficulty.HARD);
        register("minecraft:end/respawn_dragon", Difficulty.HARD);
        register("minecraft:end/dragon_egg", Difficulty.HARD);
        register("minecraft:end/elytra", Difficulty.HARD);
        register("minecraft:end/levitate", Difficulty.HARD);
        register("minecraft:end/find_end_city", Difficulty.HARD);
        register("minecraft:adventure/adventuring_time", Difficulty.HARD);
        register("minecraft:adventure/monsters_hunted", Difficulty.HARD);
        register("minecraft:adventure/sniper_duel", Difficulty.HARD);
        register("minecraft:adventure/bullseye", Difficulty.HARD);
        register("minecraft:adventure/hero_of_the_village", Difficulty.HARD);
        register("minecraft:adventure/two_birds_one_arrow", Difficulty.HARD);
        register("minecraft:husbandry/complete_catalogue", Difficulty.HARD);
        register("minecraft:husbandry/balanced_diet", Difficulty.HARD);
        register("minecraft:husbandry/bred_all_animals", Difficulty.HARD);
    }
    
    private static void register(String id, Difficulty difficulty) {
        DIFFICULTY_MAP.put(id, difficulty);
    }
    
    public static Difficulty getDifficulty(Identifier id) {
        return DIFFICULTY_MAP.getOrDefault(id.toString(), Difficulty.MEDIUM);
    }
}

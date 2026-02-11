package com.randomrun.challenges.modifier;

import com.randomrun.main.RandomRunMod;
import net.minecraft.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ModifierRegistry {
    private static final List<Modifier> MODIFIERS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    public static void init() {
        RandomRunMod.LOGGER.info("Инициализация ModifierRegistry...");
        MODIFIERS.clear(); // Ensure we don't duplicate if init called twice
        
        // Common
        register(new Modifier("speed_demon", "randomrun.modifier.speed_demon.name", 
            "randomrun.modifier.speed_demon.desc_short", Items.POTION, Modifier.Rarity.COMMON));
            
        register(new Modifier("featherweight", "randomrun.modifier.featherweight.name", 
            "randomrun.modifier.featherweight.desc_short", Items.FEATHER, Modifier.Rarity.COMMON));

        // Uncommon
        register(new Modifier("butterfingers", "randomrun.modifier.butterfingers.name", 
            "randomrun.modifier.butterfingers.desc_short", Items.SLIME_BALL, Modifier.Rarity.UNCOMMON));
        
        register(new Modifier("minimalist", "randomrun.modifier.minimalist.name", 
            "randomrun.modifier.minimalist.desc_short", Items.CHEST, Modifier.Rarity.UNCOMMON));

        // Rare
        register(new Modifier("mirror_world", "randomrun.modifier.mirror_world.name", 
            "randomrun.modifier.mirror_world.desc_short", Items.GLASS_PANE, Modifier.Rarity.RARE));
            
        register(new Modifier("blind_luck", "randomrun.modifier.blind_luck.name", 
            "randomrun.modifier.blind_luck.desc_short", Items.LEATHER_HELMET, Modifier.Rarity.RARE));

        // Epic
        register(new Modifier("night_owl", "randomrun.modifier.night_owl.name", 
            "randomrun.modifier.night_owl.desc_short", Items.GLOWSTONE_DUST, Modifier.Rarity.EPIC));
            
        register(new Modifier("pacifist", "randomrun.modifier.pacifist.name", 
            "randomrun.modifier.pacifist.desc_short", Items.POPPY, Modifier.Rarity.EPIC));

        // Legendary
        register(new Modifier("chaos_crafter", "randomrun.modifier.chaos_crafter.name", 
            "randomrun.modifier.chaos_crafter.desc_short", Items.CRAFTING_TABLE, Modifier.Rarity.LEGENDARY));
            
        register(new Modifier("hunger_games", "randomrun.modifier.hunger_games.name", 
            "randomrun.modifier.hunger_games.desc_short", Items.ROTTEN_FLESH, Modifier.Rarity.LEGENDARY));
            
        RandomRunMod.LOGGER.info("Зарегистрировано " + MODIFIERS.size() + " модификаторов.");
    }

    public static void register(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    public static List<Modifier> getAll() {
        return new ArrayList<>(MODIFIERS);
    }

    public static Modifier getRandomModifier() {
        if (MODIFIERS.isEmpty()) {
            RandomRunMod.LOGGER.error("ModifierRegistry пуст! Возвращаем null.");
            return null;
        }
        // Simple weighted random or just pure random for now
        // Let's do a simple rarity weight system later if needed. For now, pure random.
        Modifier mod = MODIFIERS.get(RANDOM.nextInt(MODIFIERS.size()));
        RandomRunMod.LOGGER.info("Выбран случайный модификатор: " + mod.getId());
        return mod;
    }
}

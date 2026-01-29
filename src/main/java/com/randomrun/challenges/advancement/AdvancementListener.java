package com.randomrun.challenges.advancement;

import com.randomrun.main.RandomRunMod;

public class AdvancementListener {
    
    public static void register() {
        RandomRunMod.LOGGER.info("AdvancementListener registered (placeholder - advancement checking disabled temporarily)");
        // TODO: Re-implement advancement checking with proper API
        // For now, advancement challenges won't trigger victory automatically
        // This needs to be fixed with the correct Minecraft 1.20.4 advancement API
    }
}

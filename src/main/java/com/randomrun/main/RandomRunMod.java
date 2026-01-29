/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.main;

import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.challenges.advancement.hud.AchievementHudRenderer;
import com.randomrun.challenges.advancement.AdvancementListener;
import com.randomrun.challenges.classic.hud.HudRenderer;
import com.randomrun.battle.command.BattleGoCommand;
import com.randomrun.main.util.TickHandler;
import com.randomrun.battle.util.BattleCleanupHandler;
import com.randomrun.main.version.VersionChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomRunMod implements ClientModInitializer {
    
    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";
    
    public static final String MOD_ID = "randomrun";
    public static final String MOD_VERSION = "26.55-BETA";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static RandomRunMod instance;
    private ModConfig config;
    private RunDataManager runDataManager;
    
    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("RandomRun Mod initializing...");
        
        // Initialize version checker FIRST
        LOGGER.info("Initializing version checker...");
        VersionChecker.getInstance();
        
        // Initialize config
        config = ModConfig.load();
        
        // Initialize run data manager
        runDataManager = new RunDataManager();
        
        // Apply saved language
        if (config.getLanguage() != null && !config.getLanguage().isEmpty()) {
            com.randomrun.main.util.LanguageManager.setLanguage(config.getLanguage());
        }
        
        // Initialize item difficulty system
        ItemDifficulty.initialize();
        
        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            BattleGoCommand.register(dispatcher);
        });
        
        // Register HUD renderer
        HudRenderCallback.EVENT.register(new HudRenderer());
        HudRenderCallback.EVENT.register(new AchievementHudRenderer());
        HudRenderCallback.EVENT.register(new com.randomrun.challenges.advancement.hud.OpponentAchievementHud());
        
        // Register tick handler
        TickHandler.register();
        
        // Register battle cleanup handler
        BattleCleanupHandler.register();
        
        // Register advancement listener
        AdvancementListener.register();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            com.randomrun.battle.BattleManager.getInstance().cleanupOnShutdown();
        }));
        
        LOGGER.info("RandomRun Mod initialized successfully!");
    }
    
    public static RandomRunMod getInstance() {
        return instance;
    }
    
    public ModConfig getConfig() {
        return config;
    }
    
    public RunDataManager getRunDataManager() {
        return runDataManager;
    }
    
    public void saveConfig() {
        config.save();
    }
}

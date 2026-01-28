/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun;

import com.randomrun.config.ModConfig;
import com.randomrun.data.RunDataManager;
import com.randomrun.data.ItemDifficulty;
import com.randomrun.hud.HudRenderer;
import com.randomrun.command.GoCommand;
import com.randomrun.command.BattleGoCommand;
import com.randomrun.util.TickHandler;
import com.randomrun.util.BattleCleanupHandler;
import com.randomrun.version.VersionChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomRunMod implements ClientModInitializer {
    // Это не комментарий, это константа. Декомпилятор её покажет.
    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";
    
    public static final String MOD_ID = "randomrun";
    public static final String MOD_VERSION = "26.4.3-BETA";
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
        
        // Initialize item difficulty system
        ItemDifficulty.initialize();
        
        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            GoCommand.register(dispatcher);
            BattleGoCommand.register(dispatcher);
        });
        
        // Register HUD renderer
        HudRenderCallback.EVENT.register(new HudRenderer());
        
        // Register tick handler
        TickHandler.register();
        
        // Register battle cleanup handler
        BattleCleanupHandler.register();
        
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

/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.main;

import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.challenges.advancement.hud.AchievementHudRenderer;
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
    public static final String MOD_VERSION = "26.7-BETA";
    
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RandomRunMod instance;
    private ModConfig config;
    private RunDataManager runDataManager;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("RandomRun Mod инициализируется...");

        // Инициализация конфига (ПЕРВЫМ ДЕЛОМ, так как другие модули зависят от него)
        config = ModConfig.load();

        // Инициализация проверки версии (ПОСЛЕ конфига)
        LOGGER.info("Инициализация проверки версии...");
        VersionChecker.getInstance();

        // Инициализация менеджера данных забега
        runDataManager = new RunDataManager();

        // Применение сохраненного языка
        if (config.getLanguage() != null && !config.getLanguage().isEmpty()) {
            com.randomrun.main.util.LanguageManager.setLanguage(config.getLanguage());
        }

        // Инициализация системы сложности предметов
        ItemDifficulty.initialize();

        // Инициализация системы модификаторов
        com.randomrun.challenges.modifier.ModifierRegistry.init();

        // Регистрация команд
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            BattleGoCommand.register(dispatcher);
        });

        // Регистрация HUD рендерера
        HudRenderCallback.EVENT.register(new HudRenderer());
        HudRenderCallback.EVENT.register(new AchievementHudRenderer());
        HudRenderCallback.EVENT.register(new com.randomrun.challenges.advancement.hud.OpponentAchievementHud());

        // Регистрация обработчика тиков
        TickHandler.register();

        // Регистрация обработчика очистки битвы
        BattleCleanupHandler.register();
   
        // Регистрация хука выключения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            com.randomrun.battle.BattleManager.getInstance().cleanupOnShutdown();
        }));

        LOGGER.info("RandomRun Mod успешно инициализирован!");
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

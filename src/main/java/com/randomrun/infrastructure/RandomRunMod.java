package com.randomrun.infrastructure;

import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.features.battle.manager.BattleCleanupHandler;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.util.advancement.OpponentAchievementHud;
import com.randomrun.features.challenges.classic.data.ItemDifficulty;
import com.randomrun.features.challenges.classic.hud.HudRenderer;
import com.randomrun.infrastructure.command.GoCommand;
import com.randomrun.infrastructure.handler.TickHandler;
import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.infrastructure.version.VersionChecker;
import com.randomrun.core.init.CoreInitializer;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.data.GlobalStatsManager;
import com.randomrun.ui.widget.overlay.CustomNotification;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class RandomRunMod implements ClientModInitializer {
    public static final String MOD_ID = "randomrun";
    public static final String MOD_VERSION = "27.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RandomRunMod instance;
    private ModConfig config;
    private RunDataManager runDataManager;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

    @Override
    public void onInitializeClient() {
        if (instance != null) {
            CompactLogger.warn("Duplicate mod initialization blocked");
            return;
        }
        
        instance = this;

        try {
            initializeConfig();
            initializeLanguage();
            initializeVersion();
            initializeSecurity();
            initializeDataManager();
            initializeDifficulty();
            registerCommands();
            registerHudElements();
            registerHandlers();
            registerConnectionEvents();
            registerShutdownHook();
            
            CompactLogger.core("Initialization complete");
        } catch (Exception e) {
            CompactLogger.error("Critical initialization error", e);
            throw new RuntimeException("Failed to initialize RandomRun", e);
        }
    }

    private void initializeConfig() {
        config = ModConfig.load();
    }

    private void initializeVersion() {
        VersionChecker.getInstance().checkVersion();
    }
    
    private void initializeSecurity() {
        ModScannerService.getInstance().scan();
    }

    private void initializeDataManager() {
        runDataManager = new RunDataManager();
    }

    private void initializeLanguage() {
        LanguageManager.ensureLanguage();
    }

    private void initializeDifficulty() {
        ItemDifficulty.initialize();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            GoCommand.register(dispatcher);
        });
    }

    private void registerHudElements() {
        com.randomrun.ui.util.ItemPreviewRenderer.register();
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(MOD_ID, "run_hud"), new HudRenderer());
        HudElementRegistry.attachElementAfter(Identifier.fromNamespaceAndPath(MOD_ID, "run_hud"),
            Identifier.fromNamespaceAndPath(MOD_ID, "opponent_hud"), new OpponentAchievementHud());
        HudElementRegistry.attachElementAfter(Identifier.fromNamespaceAndPath(MOD_ID, "opponent_hud"),
            Identifier.fromNamespaceAndPath(MOD_ID, "notifications"), new CustomNotification());
    }

    private void registerHandlers() {
        TickHandler.register();
        com.randomrun.features.upgrader.client.UpgraderClient.register();
        BattleCleanupHandler.register();
    }

    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                String playerName = client.getUser().getName();
                CoreInitializer.initialize(playerName);
            } catch (Exception e) {
                CompactLogger.error("Core initialization failed", e);
            }
        });
    }

    private void registerShutdownHook() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "RandomRun-Cleanup"));
    }

    private void shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        stopResource("battle", () -> BattleManager.getInstance().shutdown());
        stopResource("battle cleanup", BattleCleanupHandler::shutdown);
        if (runDataManager != null) {
            stopResource("run history", runDataManager::shutdown);
        }
        stopResource("global stats", GlobalStatsManager::shutdown);
    }

    private void stopResource(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            CompactLogger.error("Shutdown failed for " + name, e);
        }
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

package com.randomrun.infrastructure;

import com.randomrun.core.data.DataSyncService.CoreInitializer;
import com.randomrun.core.run.RunDataManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleSupport.BattleCleanupHandler;
import com.randomrun.features.speedrun.HudRenderer;
import com.randomrun.features.speedrun.ItemDifficulty;

import com.randomrun.infrastructure.handler.TickHandler;
import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.LanguageManager;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomRunMod implements ClientModInitializer {
    public static final String MOD_ID = "randomrun";
    public static final String MOD_VERSION = "27.2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RandomRunMod instance;
    private ModConfig config;
    private RunDataManager runDataManager;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

    @Override
    public void onInitializeClient() {
        if (instance != null) {
            CompactLogger.warn("[Core] Duplicate initialization skipped");
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

            registerHudElements();
            registerHandlers();
            registerConnectionEvents();
            registerShutdownHook();
            
            CompactLogger.core("[Core] Initialized");
        } catch (Exception e) {
            CompactLogger.error("[Core] Initialization failed", e);
            throw new RuntimeException("Failed to initialize RandomRun", e);
        }
    }

    private void initializeConfig() {
        config = ModConfig.load();
    }

    private void initializeVersion() {
        com.randomrun.network.RunVerifier.ModIntegrity.fingerprint();
        if (config.isOnlineMode()) com.randomrun.network.VersionGate.start(false);
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



    private void registerHudElements() {
        com.randomrun.ui.util.Previews.ItemPreviewRenderer.register();
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(MOD_ID, "run_hud"), new HudRenderer());
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(MOD_ID, "voice_hud"), new com.randomrun.features.voice.ui.VoiceHud());
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(MOD_ID, "speech_bubbles"), new com.randomrun.features.voice.ui.SpeechBubbles());
    }

    private void registerHandlers() {
        TickHandler.register();
        com.randomrun.ui.overlay.LoadingCurtain.register();
        com.randomrun.network.VersionGate.register();
        com.randomrun.features.upgrader.client.UpgraderClient.register();
        BattleCleanupHandler.register();
        com.randomrun.features.voice.control.VoiceController.register();
        com.randomrun.features.voice.VoiceSpeech.register();
        com.randomrun.features.streamer.StreamerNet.register();
        com.randomrun.features.streamer.StreamerPhrases.register();
    }

    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.randomrun.features.voice.VoiceSpeech.clear();
            com.randomrun.features.streamer.StreamerRender.clear();
            com.randomrun.features.streamer.StreamerVoice.clear();
            client.execute(RandomRunMod::closeRunAfterDisconnect);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                String playerName = com.randomrun.network.BackendSession.playerName();
                CoreInitializer.initialize(playerName);
            } catch (Exception e) {
                CompactLogger.error("[Core] Data initialization failed", e);
            }
        });
    }

    private static void closeRunAfterDisconnect() {
        if (BattleManager.getInstance().isInBattle()) return;
        var sessions = com.randomrun.core.run.RunSessionManager.getInstance();
        if (sessions.hasActiveRun()) {
            CompactLogger.warn("[Run] Run cancelled after disconnect");
            sessions.cancelRun();
        }
        var runs = instance.runDataManager;
        if (runs != null && (runs.getStatus() == com.randomrun.core.run.RunDataManager.RunStatus.RUNNING
                || runs.getStatus() == com.randomrun.core.run.RunDataManager.RunStatus.FROZEN)) runs.cancelRun();
    }

    private void registerShutdownHook() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> com.randomrun.ui.util.Previews.PreviewItems.warmUp());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "RandomRun-Cleanup"));
    }

    private void shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        stopResource("battle", () -> BattleManager.getInstance().shutdown());
        stopResource("realtime", () -> com.randomrun.network.RealtimeClient.getInstance().disconnect());
        stopResource("WebRTC", () -> com.randomrun.network.WebRtcTransport.getInstance().shutdown());
        stopResource("battle cleanup", BattleCleanupHandler::shutdown);
        stopResource("voice", () -> com.randomrun.features.voice.engine.VoiceEngine.get().stop());
        if (runDataManager != null) {
            stopResource("run history", runDataManager::shutdown);
        }
    }

    private void stopResource(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            CompactLogger.error("[Core] Shutdown failed for {}", name, e);
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

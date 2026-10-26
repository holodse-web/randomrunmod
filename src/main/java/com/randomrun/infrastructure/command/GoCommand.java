package com.randomrun.infrastructure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.randomrun.infrastructure.audio.RunSoundHelper;
import com.randomrun.infrastructure.handler.StartRunHandler;
import com.randomrun.infrastructure.util.LanguageManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;

public class GoCommand {
    private static final String LOG_TAG = "[GoCommand]";
    
    private static final int SUCCESS = 1;
    private static final int FAILURE = 0;
    private static final long COOLDOWN_MS = 1000;
    
    private static long lastExecutionTime = 0;
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("go")
            .executes(GoCommand::execute));
        
        dispatcher.register(ClientCommands.literal("start")
            .executes(GoCommand::execute));
    }
    
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        if (!isCommandAllowed()) {
            context.getSource().sendFeedback(LanguageManager.text("randomrun.error.use_keybind_only"));
            return FAILURE;
        }
        
        if (!checkCooldown()) {
            return FAILURE;
        }
        
        LocalPlayer player = context.getSource().getPlayer();
        if (player == null) {
            RandomRunMod.LOGGER.warn("{} Player is null", LOG_TAG);
            return FAILURE;
        }
        
        if (com.randomrun.features.battle.manager.BattleManager.getInstance().isInBattle()) {
            return handleBattleMode(context, player);
        }
        
        com.randomrun.infrastructure.data.RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (runManager.getStatus() == com.randomrun.infrastructure.data.RunDataManager.RunStatus.FROZEN) {
            return handleSoloUnfreeze(context, player, runManager);
        }
        
        if (StartRunHandler.tryStartRun(player)) {
            context.getSource().sendFeedback(LanguageManager.text("randomrun.command.go.success"));
            return SUCCESS;
        }
        
        return FAILURE;
    }
    
    private static int handleBattleMode(CommandContext<FabricClientCommandSource> context, LocalPlayer player) {
        com.randomrun.features.battle.manager.BattleManager.getInstance().sendReady();
        RunSoundHelper.playStartSound(player, true);
        return SUCCESS;
    }
    
    private static int handleSoloUnfreeze(CommandContext<FabricClientCommandSource> context, 
                                          LocalPlayer player,
                                          com.randomrun.infrastructure.data.RunDataManager runManager) {
        runManager.unfreezeRun();
        com.randomrun.core.session.RunSessionManager.getInstance().unfreezeRun();
        
        RunSoundHelper.playStartSound(player, false);
        com.randomrun.infrastructure.util.ClientMessages.send(player, net.minecraft.network.chat.Component.literal("§a§lGO!"), true);
        
        context.getSource().sendFeedback(LanguageManager.text("randomrun.command.go.success"));
        return SUCCESS;
    }
    
    private static boolean isCommandAllowed() {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        return config.getStartMethod() != ModConfig.StartMethod.KEYBIND;
    }
    
    private static boolean checkCooldown() {
        long now = System.currentTimeMillis();
        if (now - lastExecutionTime < COOLDOWN_MS) {
            return false;
        }
        lastExecutionTime = now;
        return true;
    }
}

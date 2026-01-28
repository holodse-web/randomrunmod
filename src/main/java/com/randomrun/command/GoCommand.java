package com.randomrun.command;

import com.mojang.brigadier.CommandDispatcher;
import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class GoCommand {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("go")
            .executes(context -> {
                return executeGo(context.getSource());
            })
        );
    }
    
    private static int executeGo(FabricClientCommandSource source) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        RunDataManager.RunStatus status = runManager.getStatus();
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (status == RunDataManager.RunStatus.INACTIVE) {
            source.sendFeedback(Text.literal("§c" + Text.translatable("randomrun.command.no_active_run").getString()));
            return 0;
        }
        
        if (status == RunDataManager.RunStatus.RUNNING) {
            source.sendFeedback(Text.literal("§e" + Text.translatable("randomrun.command.already_started").getString()));
            return 0;
        }
        
        if (status == RunDataManager.RunStatus.FROZEN) {
        
            runManager.unfreezeRun();
            
          
            if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled() && client.player != null) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                client.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, volume, 1.5f);
            }
            
         
            source.sendFeedback(Text.literal("§a§l" + Text.translatable("randomrun.command.start").getString()));
            
            return 1;
        }
        
        return 0;
    }
}

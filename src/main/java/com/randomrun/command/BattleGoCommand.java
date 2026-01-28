package com.randomrun.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.data.RunDataManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class BattleGoCommand {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("go")
            .executes(BattleGoCommand::executeGo));
    }
    
    private static int executeGo(CommandContext<FabricClientCommandSource> context) {
        BattleManager battleManager = BattleManager.getInstance();
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
     
        if (runManager.getStatus() != RunDataManager.RunStatus.FROZEN) {
            context.getSource().sendFeedback(Text.literal("§cВы уже начали или не в спидране!"));
            return 0;
        }
        
     
        if (battleManager.isInBattle()) {
            battleManager.sendReady();
            context.getSource().sendFeedback(Text.literal("§a✓ Готов! Ожидание противника..."));
        } else {
         
            runManager.unfreezeRun();
            
      
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), volume, 1.0f);
            }
        }
        
        return 1;
    }
}

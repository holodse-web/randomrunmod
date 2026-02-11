package com.randomrun.battle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.randomrun.main.util.StartRunHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;

public class BattleGoCommand {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("go")
            .executes(BattleGoCommand::executeGo));
    }
    
    private static int executeGo(CommandContext<FabricClientCommandSource> context) {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        if (config.getStartMethod() == ModConfig.StartMethod.KEYBIND) {
            context.getSource().sendFeedback(Text.translatable("randomrun.error.use_keybind_only"));
            return 0;
        }
        
        // Делегирование общему обработчику
        // Примечание: StartRunHandler отправляет сообщения игроку, поэтому нам не нужно отправлять их здесь,
        // если только игрок не null, что маловероятно в клиентской команде
        boolean success = StartRunHandler.tryStartRun(context.getSource().getPlayer());
        return success ? 1 : 0;
    }
}

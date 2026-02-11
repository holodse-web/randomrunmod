package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.main.RandomRunMod;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerConnectionMixin {

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        BattleManager manager = BattleManager.getInstance();
        BattleRoom room = manager.getCurrentRoom();
        
        if (manager.isHost() && room != null && room.isSharedWorld()) {
            String playerName = player.getName().getString();
            
            // Если игрок был помечен как отключившийся (dd), переводим его в наблюдатели
            if (room.isPlayerDisconnected(playerName)) {
                RandomRunMod.LOGGER.info("Player " + playerName + " rejoined after disconnect. Setting to SPECTATOR.");
                player.changeGameMode(GameMode.SPECTATOR);
                
                // Optional: Send message
                // player.sendMessage(Text.literal("§cВы были отключены и переведены в режим наблюдателя."), false);
                // Note: Sending message here might be too early or fine.
            }
        }
    }
}

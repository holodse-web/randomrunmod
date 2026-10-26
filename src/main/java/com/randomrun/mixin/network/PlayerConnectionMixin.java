package com.randomrun.mixin.network;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerConnectionMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
        BattleManager manager = BattleManager.getInstance();
        BattleRoom room = manager.getCurrentRoom();
        
        if (manager.isHost() && room != null && room.isSharedWorld()) {
            String playerName = player.getName().getString();
            
            if (room.isPlayerDisconnected(playerName)) {
                player.setGameMode(GameType.SPECTATOR);
            }
        }
    }
}

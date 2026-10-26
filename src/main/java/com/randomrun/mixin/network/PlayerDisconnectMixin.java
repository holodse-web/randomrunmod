package com.randomrun.mixin.network;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class PlayerDisconnectMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onPlayerDisconnect(net.minecraft.network.DisconnectionDetails reason, CallbackInfo ci) {
        if (player == null) return;
        
        String playerName = player.getName().getString();
        BattleManager manager = BattleManager.getInstance();
        BattleRoom room = manager.getCurrentRoom();
        
        if (manager.isHost() && room != null && room.isSharedWorld()) {
             manager.handlePlayerDisconnect(playerName);
        }
    }
}

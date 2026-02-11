package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.main.RandomRunMod;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class PlayerDisconnectMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void onPlayerDisconnect(Text reason, CallbackInfo ci) {
        if (player == null) return;
        
        String playerName = player.getName().getString();
        BattleManager manager = BattleManager.getInstance();
        BattleRoom room = manager.getCurrentRoom();
        
        // Check if we are Host and in Shared World
        if (manager.isHost() && room != null && room.isSharedWorld()) {
             RandomRunMod.LOGGER.info("Player disconnected from Battle: " + playerName);
             manager.handlePlayerDisconnect(playerName);
        }
    }
}

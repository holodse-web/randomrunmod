package com.randomrun.mixin.gameplay;

import com.randomrun.features.upgrader.client.UpgraderClient;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class UpgraderRewardLifecycleMixin {
    private UpgraderRewardLifecycleMixin() {}

    @Mixin(PlayerList.class)
    public static class Disconnect {
        @Inject(method = "remove", at = @At("HEAD"))
        private void randomrun$settleBeforeSave(ServerPlayer player, CallbackInfo ci) {
            UpgraderClient.settleForPlayer(player);
        }
    }

    @Mixin(ServerPlayer.class)
    public static class Death {
        @Inject(method = "die", at = @At("HEAD"))
        private void randomrun$settleBeforeDeathDrop(DamageSource source, CallbackInfo ci) {
            UpgraderClient.settleForPlayer((ServerPlayer) (Object) this);
        }
    }
}

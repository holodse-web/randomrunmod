package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class BattleFreezeMixin {
    
    private double frozenX = 0;
    private double frozenY = 0;
    private double frozenZ = 0;
    private float frozenYaw = 0;
    private float frozenPitch = 0;
    private boolean wasFrozen = false;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        BattleManager battleManager = BattleManager.getInstance();
        
        if (battleManager.isFrozen() && battleManager.isInBattle()) {
            // Save position on first freeze
            if (!wasFrozen) {
                frozenX = player.getX();
                frozenY = player.getY();
                frozenZ = player.getZ();
                // Don't save rotation - allow looking around
                wasFrozen = true;
            }
            
            // Lock position only (allow camera rotation)
            player.setVelocity(0, 0, 0);
            player.setPos(frozenX, frozenY, frozenZ);
            
            // Allow looking around by NOT resetting yaw/pitch
            // player.setYaw(frozenYaw);
            // player.setPitch(frozenPitch);
            
            player.prevX = frozenX;
            player.prevY = frozenY;
            player.prevZ = frozenZ;
        } else if (wasFrozen) {
            wasFrozen = false;
        }
    }
}

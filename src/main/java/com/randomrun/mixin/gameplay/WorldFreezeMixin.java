package com.randomrun.mixin.gameplay;

import com.randomrun.core.session.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class WorldFreezeMixin {
    @Mixin(Entity.class)
    public static class EntityTick {
        @Unique
        private boolean wasAiDisabled = false;
        
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void onEntityTick(CallbackInfo ci) {
            Entity entity = (Entity) (Object) this;
            
            if (entity.level().isClientSide() || entity.isAlwaysTicking()) {
                return;
            }
            
            if (entity instanceof Mob || entity instanceof Animal) {
                if (isWorldFrozen()) {
                    ci.cancel();
                    
                    entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
                    
                    if (entity instanceof Mob mob && !mob.isNoAi()) {
                        mob.setNoAi(true);
                        wasAiDisabled = true;
                    }
                } else if (wasAiDisabled) {
                    if (entity instanceof Mob mob) {
                        mob.setNoAi(false);
                        wasAiDisabled = false;
                    }
                }
            }
        }
        
        private boolean isWorldFrozen() {
            RunSessionManager sessionManager = RunSessionManager.getInstance();
            BattleManager battleManager = BattleManager.getInstance();
            
            return sessionManager.isFrozen() || (battleManager.isInBattle() && battleManager.isFrozen());
        }
    }
}

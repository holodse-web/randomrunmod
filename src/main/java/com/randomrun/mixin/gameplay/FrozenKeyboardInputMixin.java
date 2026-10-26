package com.randomrun.mixin.gameplay;

import com.randomrun.core.session.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FrozenKeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void freezeMovement(CallbackInfo ci) {
        BattleManager battle = BattleManager.getInstance();
        if (RunSessionManager.getInstance().isFrozen() || (battle.isInBattle() && battle.isFrozen())) {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }
    }
}

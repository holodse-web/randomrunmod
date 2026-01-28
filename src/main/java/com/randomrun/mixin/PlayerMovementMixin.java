package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class PlayerMovementMixin {
    
    @Unique
    private Vec3d frozenPosition = null;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            
            // Сохраняем позицию в начале тика
            if (frozenPosition == null) {
                frozenPosition = player.getPos();
            }
            
            // Блокируем ввод движения
            player.input.movementForward = 0;
            player.input.movementSideways = 0;
            player.input.jumping = false;
            player.input.sneaking = false;
            
            // Обнуляем скорость полностью
            player.setVelocity(0, 0, 0);
        } else {
            frozenPosition = null;
        }
    }
    
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickEnd(CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN && frozenPosition != null) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            
            // Принудительно возвращаем игрока на сохраненную позицию
            player.setPosition(frozenPosition.x, frozenPosition.y, frozenPosition.z);
            player.setVelocity(0, 0, 0);
        }
    }
}

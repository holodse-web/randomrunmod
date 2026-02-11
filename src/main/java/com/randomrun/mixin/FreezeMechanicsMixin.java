package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Объединяет все механики, связанные с "Заморозкой".
 * Содержит миксины для PlayerEntity, Mouse и InteractionManager.
 */
public class FreezeMechanicsMixin {

    @Mixin(ClientPlayerEntity.class)
    public static class Player {
        @Unique
        private Vec3d frozenPos = null;
        @Unique
        private boolean wasFrozen = false;

        @Inject(method = "tick", at = @At("HEAD"))
        private void onTickStart(CallbackInfo ci) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();

            // Проверка, должен ли игрок быть заморожен (либо RunManager, либо BattleManager)
            boolean isFrozen = runManager.getStatus() == RunDataManager.RunStatus.FROZEN || 
                               (battleManager.isInBattle() && battleManager.isFrozen());

            if (isFrozen) {
                // 1. Захват позиции при первой заморозке
                if (!wasFrozen || frozenPos == null) {
                    frozenPos = player.getPos();
                    
                    // FIX: Принудительная коррекция спавна в Общем Мире, если игрок далеко от центра
                    if (battleManager.isSharedWorld() && battleManager.isInBattle()) {
                        if (frozenPos.squaredDistanceTo(0, frozenPos.y, 0) > 2500) { // > 50 блоков
                             int safeY = player.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                             if (safeY < player.getWorld().getBottomY()) safeY = 80;
                             
                             frozenPos = new Vec3d(0.5, safeY + 1, 0.5);
                             player.setPosition(frozenPos.x, frozenPos.y, frozenPos.z);
                             player.setVelocity(0, 0, 0);
                             RandomRunMod.LOGGER.info("FreezeMixin: Принудительная коррекция спавна к 0,0");
                        }
                    }
                    
                    wasFrozen = true;
                }

                // 2. Блокировка ввода (Движение, Прыжок, Красться)
                player.input.movementForward = 0;
                player.input.movementSideways = 0;
                // player.input.jumping = false; // Cannot find symbol in 1.21.4
                // player.input.sneaking = false; // Cannot find symbol in 1.21.4
                
                // 3. Остановка спринта
                player.setSprinting(false);

                // 4. Обнуление скорости
                player.setVelocity(0, 0, 0);
                
                // 5. Блокировка позиции с поддержкой серверной телепортации
                if (frozenPos != null) {
                    double distSq = player.squaredDistanceTo(frozenPos);
                    
                    if (distSq > 100.0) {
                        frozenPos = player.getPos();
                    } else if (distSq > 0.0001) { // Extremely strict tolerance for online
                        // Force teleport back immediately on client side to prevent visual rubber-banding
                        player.setPosition(frozenPos.x, frozenPos.y, frozenPos.z);
                        player.setVelocity(0, 0, 0);
                        
                        // Также блокируем отправку пакетов движения через input
                        // player.input.movementForward = 0;
                        // player.input.movementSideways = 0;
                        // player.input.jumping = false;
                        // player.input.sneaking = false;
                    }
                }
                
                // 6. Применение эффектов (Slowness + Jump Boost) для "мягкой" блокировки
                // SLOWNESS 255 блокирует движение, JUMP_BOOST 250 (отрицательный/блокирующий) блокирует прыжки
                if (!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS)) {
                    player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 20, 255, false, false));
                }
                if (!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST)) {
                     player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST, 20, 200, false, false));
                }
                
                // Примечание: Мы намеренно НЕ блокируем Yaw/Pitch, чтобы можно было осматриваться.

            } else {
                // Сброс состояния при разморозке
                if (wasFrozen) {
                    wasFrozen = false;
                    frozenPos = null;
                    player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
                    player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST);
                }
            }
        }

        @Inject(method = "tick", at = @At("RETURN"))
        private void onTickEnd(CallbackInfo ci) {
            // Двойная защита: Принудительный возврат позиции (ВОЗВРАЩЕНО)
            if (wasFrozen && frozenPos != null) {
                ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
                RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
                BattleManager battleManager = BattleManager.getInstance();

                boolean isFrozen = runManager.getStatus() == RunDataManager.RunStatus.FROZEN || 
                                   (battleManager.isInBattle() && battleManager.isFrozen());

                if (isFrozen) {
                    if (player.squaredDistanceTo(frozenPos) > 1.0) { // Increased tolerance to reduce jitter
                        player.setPosition(frozenPos.x, frozenPos.y, frozenPos.z);
                        player.setVelocity(0, 0, 0);
                    }
                }
            }
        }
    }

    @Mixin(MinecraftClient.class)
    public static class ClientInput {
        @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
        private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();

            boolean isFrozen = runManager.getStatus() == RunDataManager.RunStatus.FROZEN || 
                               (battleManager.isInBattle() && battleManager.isFrozen());
            
            if (isFrozen) {
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
        private void onDoItemUse(CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();

            boolean isFrozen = runManager.getStatus() == RunDataManager.RunStatus.FROZEN || 
                               (battleManager.isInBattle() && battleManager.isFrozen());
            
            if (isFrozen) {
                ci.cancel();
            }
        }
    }

    // @Mixin(Mouse.class)
    // public static class MouseInput {
    //     @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    //     private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
    //         RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
    //         
    //         // Блокировка кликов мыши при заморозке (кроме GUI)
    //         if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
    //             MinecraftClient client = MinecraftClient.getInstance();
    //             
    //             // Разрешить клики, если открыт экран
    //             if (client.currentScreen != null) {
    //                 return;
    //             }
    //             
    //             // Блокировка атаки и использования (GLFW_PRESS = 1)
    //             if (action == 1) { 
    //                 ci.cancel();
    //             }
    //         }
    //     }
    // }

    @Mixin(ClientPlayerInteractionManager.class)
    public static class BlockInteraction {
        @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
        private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            
            if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                cir.setReturnValue(false);
            }
        }
        
        @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
        private void onUpdateBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            
            if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                cir.setReturnValue(false);
            }
        }
    }

    @Mixin(net.minecraft.client.option.KeyBinding.class)
    public static class KeyBindingMixin {
        @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
        private void onIsPressed(CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();
            
            boolean isFrozen = runManager.getStatus() == RunDataManager.RunStatus.FROZEN || 
                               (battleManager.isInBattle() && battleManager.isFrozen());

            if (isFrozen) {
                net.minecraft.client.option.KeyBinding key = (net.minecraft.client.option.KeyBinding)(Object)this;
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.options.forwardKey == key || 
                    client.options.backKey == key || 
                    client.options.leftKey == key || 
                    client.options.rightKey == key || 
                    client.options.jumpKey == key || 
                    client.options.sneakKey == key || 
                    client.options.sprintKey == key) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}

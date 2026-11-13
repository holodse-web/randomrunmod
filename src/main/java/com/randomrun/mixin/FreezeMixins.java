package com.randomrun.mixin;

import com.randomrun.core.run.RunDataManager;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.RandomRunMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freezing the player and the world while a run is frozen or waiting to start. */
public final class FreezeMixins {
    private FreezeMixins() {}

    @Mixin(LocalPlayer.class)
    public static class PlayerFreezeMixin {
        @Unique
        private Vec3 frozenPos = null;
        @Unique
        private boolean wasFrozen = false;

        @Inject(method = "tick", at = @At("HEAD"))
        private void onTickStart(CallbackInfo ci) {
            LocalPlayer player = (LocalPlayer) (Object) this;

            if (!isFrozen()) {
                player.fallDistance = 0;
                resetFreezeState(player);
                return;
            }

            if (!wasFrozen || frozenPos == null) {
                captureFrozenPosition(player);
            }

            applyFreezeEffects(player);
        }

        @Inject(method = "tick", at = @At("RETURN"))
        private void onTickEnd(CallbackInfo ci) {
            if (!wasFrozen || frozenPos == null) return;
            if (!isFrozen()) return;

            LocalPlayer player = (LocalPlayer) (Object) this;

            if (player.distanceToSqr(frozenPos) > 1.0) {
                player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
                player.setDeltaMovement(0, 0, 0);
            }
        }

        private boolean isFrozen() {
            com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();
            BattleManager battleManager = BattleManager.getInstance();

            return sessionManager.isFrozen() || (battleManager.isInBattle() && battleManager.isFrozen());
        }

        private void captureFrozenPosition(LocalPlayer player) {
            frozenPos = player.position();
            wasFrozen = true;

            player.fallDistance = 0;

            if (player.getHealth() < 20.0f) {
                player.setHealth(20.0f);
            }

            BattleManager battleManager = BattleManager.getInstance();
            if (battleManager.isSharedWorld() && battleManager.isInBattle()) {
                if (frozenPos.distanceToSqr(0, frozenPos.y, 0) > 2500) {
                    int safeY = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                    if (safeY < player.level().getMinY()) safeY = 80;

                    frozenPos = new Vec3(0.5, safeY + 1, 0.5);
                    player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
                    player.setDeltaMovement(0, 0, 0);
                }
            }
        }

        private void applyFreezeEffects(LocalPlayer player) {
            player.fallDistance = 0;

            if (player.getHealth() < 20.0f) {
                player.setHealth(20.0f);
            }

            player.input.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
            player.setSprinting(false);
            player.setDeltaMovement(0, 0, 0);

            if (frozenPos != null) {
                double distSq = player.distanceToSqr(frozenPos);

                if (distSq > 100.0) {
                    frozenPos = player.position();
                } else if (distSq > 0.0001) {
                    player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
                    player.setDeltaMovement(0, 0, 0);
                }
            }

            if (!player.hasEffect(MobEffects.SLOWNESS)) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 255, false, false));
            }
            if (!player.hasEffect(MobEffects.JUMP_BOOST)) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 20, 200, false, false));
            }

            if (!player.hasEffect(MobEffects.REGENERATION)) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 255, false, false));
            }
            if (!player.hasEffect(MobEffects.RESISTANCE)) {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 255, false, false));
            }
        }

        private void resetFreezeState(LocalPlayer player) {
            if (wasFrozen) {
                wasFrozen = false;
                frozenPos = null;
                player.removeEffect(MobEffects.SLOWNESS);
                player.removeEffect(MobEffects.JUMP_BOOST);
                player.removeEffect(MobEffects.REGENERATION);
                player.removeEffect(MobEffects.RESISTANCE);
            }
        }
    }

    @Mixin(Minecraft.class)
    public static class ClientInputFreezeMixin {
        @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
        private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
            if (isFrozen()) {
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
        private void onDoItemUse(CallbackInfo ci) {
            if (isFrozen()) {
                ci.cancel();
            }
        }

        private boolean isFrozen() {
            com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();
            BattleManager battleManager = BattleManager.getInstance();

            return sessionManager.isFrozen() || (battleManager.isInBattle() && battleManager.isFrozen());
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static class BlockInteractionFreezeMixin {
        @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
        private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
            if (isFrozen()) {
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
        private void onUpdateBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
            if (isFrozen()) {
                cir.setReturnValue(false);
            }
        }

        private boolean isFrozen() {
            com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();
            BattleManager battleManager = BattleManager.getInstance();

            return sessionManager.isFrozen() || (battleManager.isInBattle() && battleManager.isFrozen());
        }
    }

    @Mixin(net.minecraft.client.KeyMapping.class)
    public static class KeyBindingFreezeMixin {
        @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
        private void onIsPressed(CallbackInfoReturnable<Boolean> cir) {
            if (!isFrozen()) return;

            net.minecraft.client.KeyMapping key = (net.minecraft.client.KeyMapping)(Object)this;
            Minecraft client = Minecraft.getInstance();

            if (isMovementKey(key, client)) {
                cir.setReturnValue(false);
            }
        }

        private boolean isFrozen() {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();

            return runManager.getStatus() == RunDataManager.RunStatus.FROZEN || 
                   (battleManager.isInBattle() && battleManager.isFrozen());
        }

        private boolean isMovementKey(net.minecraft.client.KeyMapping key, Minecraft client) {
            return client.options.keyUp == key || 
                   client.options.keyDown == key || 
                   client.options.keyLeft == key || 
                   client.options.keyRight == key || 
                   client.options.keyJump == key || 
                   client.options.keyShift == key || 
                   client.options.keySprint == key;
        }
    }

    @Mixin(Entity.class)
    public static class EntityTickFreezeMixin {
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

    @Mixin(KeyboardInput.class)
    public static abstract class FrozenKeyboardInputMixin extends ClientInput {
        @Inject(method = "tick", at = @At("TAIL"))
        private void freezeMovement(CallbackInfo ci) {
            com.randomrun.features.voice.control.VoiceController voice = com.randomrun.features.voice.control.VoiceController.get();
            if (voice.isActive()) {
                keyPresses = voice.input();
                moveVector = voice.moveVector();
            }
            BattleManager battle = BattleManager.getInstance();
            if (RunSessionManager.getInstance().isFrozen() || (battle.isInBattle() && battle.isFrozen())) {
                keyPresses = Input.EMPTY;
                moveVector = Vec2.ZERO;
            }
        }
    }
}

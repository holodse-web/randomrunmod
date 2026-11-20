package com.randomrun.mixin;

import com.randomrun.features.streamer.StreamerPhrases;
import com.randomrun.features.streamer.StreamerRender;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


public final class StreamerMixins {
    private StreamerMixins() {}

    @Mixin(AvatarRenderer.class)
    public static abstract class AvatarRendererMixin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
        private void randomrun$streamerLook(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
            StreamerRender.extract(entity, state);
        }

        @ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true)
        private Identifier randomrun$streamerHand(Identifier skin) {
            return StreamerRender.handTexture(skin);
        }
    }

    @Mixin(PlayerModel.class)
    public static abstract class PlayerModelMixin {
        @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
        private void randomrun$streamerShape(AvatarRenderState state, CallbackInfo ci) {
            StreamerRender.shape((PlayerModel) (Object) this, state);
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static abstract class DropMixin {
        @Inject(method = "dropItem", at = @At("HEAD"))
        private void randomrun$streamerDrop(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
            if (!player.getMainHandItem().isEmpty()) StreamerPhrases.onDrop();
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static abstract class ContainerClickMixin {
        @Inject(method = "handleContainerInput", at = @At("HEAD"))
        private void randomrun$streamerCraft(int containerId, int slotId, int button, net.minecraft.world.inventory.ContainerInput input,
                                             net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
            if (player.containerMenu != null && player.containerMenu.containerId == containerId) StreamerPhrases.onContainerClick(player.containerMenu, slotId);
        }
    }
}

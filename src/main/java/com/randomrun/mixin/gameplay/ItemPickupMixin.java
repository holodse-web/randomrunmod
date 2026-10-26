package com.randomrun.mixin.gameplay;

import com.randomrun.infrastructure.handler.VictoryHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public class ItemPickupMixin {
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void onItemInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Inventory inventory = (Inventory) (Object) this;
        if (inventory.player == null || !inventory.player.level().isClientSide()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (!inventory.player.getUUID().equals(client.player.getUUID())) return;

        com.randomrun.core.session.RunSessionManager sessionManager = com.randomrun.core.session.RunSessionManager.getInstance();

        if (!sessionManager.hasActiveRun() || !sessionManager.isRunning()) return;

        Item targetItem = sessionManager.getTargetItem();
        if (targetItem != null && stack.getItem() == targetItem) {
            VictoryHandler.handleVictory();
        }
    }
}

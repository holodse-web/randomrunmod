package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import com.randomrun.util.VictoryHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class ItemPickupMixin {
    
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void onItemInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            Item pickedItem = stack.getItem();
            
            if (runManager.checkItemPickup(pickedItem)) {
                // Victory!
                VictoryHandler.handleVictory();
            }
        }
    }
}

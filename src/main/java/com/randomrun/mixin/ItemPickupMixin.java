package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.main.util.VictoryHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class ItemPickupMixin {
    
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void onItemInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // Проверка только на стороне клиента
        PlayerInventory inventory = (PlayerInventory) (Object) this;
        if (inventory.player == null || !inventory.player.getWorld().isClient) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Убедиться, что этот инвентарь принадлежит локальному игроку!
        // Если Хост (Сервер+Клиент) обрабатывает инвентарь Гостя, inventory.player != client.player
        if (!inventory.player.getUuid().equals(client.player.getUuid())) {
            return;
        }
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            Item pickedItem = stack.getItem();
            
            if (runManager.checkItemPickup(pickedItem)) {
                // Победа!
                // VictoryHandler.handleVictory(); // УДАЛЕНО: Победа обрабатывается в TickHandler или BattleItemPickupMixin, чтобы не дублировать
                // Кроме того, ItemPickupMixin срабатывает ДО того, как предмет реально попадет в инвентарь (insertStack),
                // что может вызвать проблемы с проверками инвентаря.
                // Лучше полагаться на TickHandler.
            }
        }
    }
}

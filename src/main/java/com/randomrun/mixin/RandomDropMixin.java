package com.randomrun.mixin;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(net.minecraft.world.level.block.Block.class)
public class RandomDropMixin {
    private static final Random RANDOM = new Random();
    private static List<Item> ALL_ITEMS = null;
    private static final float SILK_TOUCH_CHANCE = 0.15f;
    
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;", 
            at = @At("RETURN"), 
            cancellable = true)
    private static void replaceDropWithRandom(BlockState state, ServerLevel world, BlockPos pos, 
                                             net.minecraft.world.level.block.entity.BlockEntity blockEntity, 
                                             Entity entity, net.minecraft.world.item.ItemInstance tool, 
                                             CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        
        boolean isRandomDropActive = false;
        
        BattleManager battleManager = BattleManager.getInstance();
        if (battleManager.isInBattle()) {
            BattleRoom room = battleManager.getCurrentRoom();
            if (room != null && room.getGameMode() == GameMode.RANDOM_DROP) {
                isRandomDropActive = true;
            }
        } else {
            com.randomrun.core.session.RunSessionManager sessionManager = com.randomrun.core.session.RunSessionManager.getInstance();
            if (sessionManager.hasActiveRun()) {
                com.randomrun.core.session.RunSession session = sessionManager.getCurrentSession();
                if (session != null && session.isRandomDrop()) {
                    isRandomDropActive = true;
                }
            }
        }
        
        if (!isRandomDropActive) {
            return;
        }
        
        boolean hasSilkTouch = false;
        try {
            String enchantmentsStr = tool.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).toString().toLowerCase();
            hasSilkTouch = enchantmentsStr.contains("silk_touch");
        } catch (Exception e) {
        }
        
        if (hasSilkTouch) {
            return;
        }
        
        if (ALL_ITEMS == null) {
            initializeItemList();
        }
        
        if (ALL_ITEMS.isEmpty()) {
            return;
        }
        
        Item randomItem = ALL_ITEMS.get(RANDOM.nextInt(ALL_ITEMS.size()));
        ItemStack randomStack = new ItemStack(randomItem, 1);
        
        cir.setReturnValue(Collections.singletonList(randomStack));
    }
    
    private static boolean canHaveSilkTouch(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        
        if (itemId.contains("pickaxe") || itemId.contains("axe") || itemId.contains("shovel") || 
            itemId.contains("hoe") || itemId.contains("shears")) {
            return true;
        }
        
        if (itemId.contains("sword") || itemId.contains("bow") || itemId.contains("crossbow") ||
            itemId.contains("trident")) {
            return true;
        }
        
        if (itemId.contains("helmet") || itemId.contains("chestplate") || 
            itemId.contains("leggings") || itemId.contains("boots")) {
            return true;
        }
        
        return false;
    }
    
    private static void initializeItemList() {
        ALL_ITEMS = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ALL_ITEMS.add(item);
        }
    }
}

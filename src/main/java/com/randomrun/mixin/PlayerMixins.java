package com.randomrun.mixin;

import com.randomrun.core.run.RunDataManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoom.GameMode;
import com.randomrun.features.upgrader.client.UpgraderClient;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.handler.VictoryHandler;
import com.randomrun.ui.screen.endgame.EndgameScreen.DefeatScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player events: picking up the target item, death, random drops and upgrader rewards. */
public final class PlayerMixins {
    private PlayerMixins() {}

    @Mixin(Inventory.class)
    public static class ItemPickupMixin {
        @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
        private void onItemInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
            Inventory inventory = (Inventory) (Object) this;
            if (inventory.player == null || !inventory.player.level().isClientSide()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            if (!inventory.player.getUUID().equals(client.player.getUUID())) return;

            com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();

            if (!sessionManager.hasActiveRun() || !sessionManager.isRunning()) return;

            Item targetItem = sessionManager.getTargetItem();
            if (targetItem != null && stack.getItem() == targetItem) {
                VictoryHandler.handleVictory();
            }
        }
    }

    @Mixin(LivingEntity.class)
    public static class PlayerDeathMixin {
        @Inject(method = "die", at = @At("HEAD"))
        private void onDeath(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (!(entity instanceof LocalPlayer)) return;

            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            if (runManager.getStatus() != RunDataManager.RunStatus.RUNNING) return;

            BattleManager battleManager = BattleManager.getInstance();

            if (battleManager.isInBattle()) {
                handleBattleDeath(runManager, battleManager);
            } else {
                handleSoloDeath(runManager, battleManager);
            }
        }

        private void handleBattleDeath(RunDataManager runManager, BattleManager battleManager) {
            if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                runManager.unfreezeRun();
            }

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            client.player.respawn();
            battleManager.reportElimination();
            client.gui.setScreen(null);
        }

        private void handleSoloDeath(RunDataManager runManager, BattleManager battleManager) {
            long finalTime = runManager.getCurrentTime();
            runManager.failRun();

            if (battleManager.isInBattle()) {
                battleManager.reportDefeat("[Death]");
            }

            Minecraft client = Minecraft.getInstance();
            if (client == null) return;

            if (runManager.getTargetItem() != null) {
                showDefeatScreen(client, runManager, finalTime);
            }
        }

        private void showDefeatScreen(Minecraft client, RunDataManager runManager, long finalTime) {
            String reason = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.defeat.death").getString();

            client.execute(() -> {
                client.gui.setScreen(new DefeatScreen(runManager.getTargetItem(), finalTime, reason));
            });
        }
    }

    @Mixin(net.minecraft.world.level.block.Block.class)
    public static class RandomDropMixin {
        private static final Random RANDOM = new Random();
        private static List<Item> ALL_ITEMS = null;

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
                com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();
                if (sessionManager.hasActiveRun()) {
                    com.randomrun.core.run.RunSession session = sessionManager.getCurrentSession();
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

        private static void initializeItemList() {
            ALL_ITEMS = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ALL_ITEMS.add(item);
            }
        }
    }

    @Mixin(PlayerList.class)
    public static class UpgraderDisconnectMixin {
        @Inject(method = "remove", at = @At("HEAD"))
        private void randomrun$settleBeforeSave(ServerPlayer player, CallbackInfo ci) {
            UpgraderClient.settleForPlayer(player);
        }
    }

    @Mixin(ServerPlayer.class)
    public static class UpgraderDeathMixin {
        @Inject(method = "die", at = @At("HEAD"))
        private void randomrun$settleBeforeDeathDrop(DamageSource source, CallbackInfo ci) {
            UpgraderClient.settleForPlayer((ServerPlayer) (Object) this);
        }
    }
}

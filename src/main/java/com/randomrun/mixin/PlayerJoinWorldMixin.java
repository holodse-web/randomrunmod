package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.challenges.classic.world.WorldCreator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class PlayerJoinWorldMixin {
    
    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void onJoinWorld(GameJoinS2CPacket packet, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        BattleManager battleManager = BattleManager.getInstance();
        
        if (battleManager.isInBattle()) {
            // Получаем targetItem из BattleRoom, если он не установлен в RunDataManager
            Item targetItem = runManager.getTargetItem();
            BattleRoom room = battleManager.getCurrentRoom();
            
            if (targetItem == null && room != null && room.getTargetItem() != null) {
                targetItem = Registries.ITEM.get(new Identifier(room.getTargetItem()));
                runManager.setTargetItem(targetItem);
                RandomRunMod.LOGGER.info("✓ Set targetItem from BattleRoom: " + room.getTargetItem());
            }
            
            if (targetItem == null) {
                RandomRunMod.LOGGER.warn("⚠ No targetItem available for battle!");
                return;
            }
            
            // Always initialize frozen run for battle, regardless of current status
            // This handles cases where player joins a new battle after a previous one
            RunDataManager.RunStatus currentStatus = runManager.getStatus();
            if (currentStatus != RunDataManager.RunStatus.FROZEN) {
                // Cancel any existing run first
                if (currentStatus != RunDataManager.RunStatus.INACTIVE) {
                    runManager.cancelRun();
                }
                runManager.startNewRun(targetItem, 0);
                RandomRunMod.LOGGER.info("✓ Initialized frozen run for battle (was: " + currentStatus + ")");
            }
            
            battleManager.freezePlayer();
            
            // Set player loaded flag - host will transition to FROZEN when both loaded
            battleManager.setPlayerLoaded();
            
            // Clear pending data to prevent memory leaks
            WorldCreator.clearPendingData();
            
            // Teleport to world spawn to ensure same position for both players
            MinecraftClient.getInstance().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.world != null) {
                    // Get world spawn point - this is deterministic based on seed
                    net.minecraft.util.math.BlockPos spawnPos = client.world.getSpawnPos();
                    
                    // Teleport player to spawn point
                    client.player.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    
                    RandomRunMod.LOGGER.info("✓ Teleported to spawn: " + spawnPos.toShortString());
                    
                    client.player.sendMessage(
                        Text.literal("§e§lВы заморожены! Напишите §f/go §e§lкогда будете готовы"), false
                    );
                }
            });
        } else if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
        }
    }
}

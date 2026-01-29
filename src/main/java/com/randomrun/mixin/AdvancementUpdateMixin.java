package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.main.util.VictoryHandler;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = ClientPlayNetworkHandler.class, priority = 1001)
public abstract class AdvancementUpdateMixin {

    @Inject(
        method = "onAdvancements",
        at = @At("RETURN")
    )
    private void onAdvancementPacket(AdvancementUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            // 1. Check for Victory (Target Advancement)
            checkVictory(packet);
            
            // 2. Check for Battle Events (Opponent Achievements)
            checkBattleEvents(packet);
            
        } catch (Exception e) {
            RandomRunMod.LOGGER.debug("Error in advancement check: {}", e.getMessage());
        }
    }

    private void checkVictory(AdvancementUpdateS2CPacket packet) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() != RunDataManager.RunStatus.RUNNING) return;
        if (runManager.getTargetType() != RunDataManager.TargetType.ADVANCEMENT) return;
        
        Identifier targetId = runManager.getTargetAdvancementId();
        if (targetId == null) return;
        
        Map<Identifier, AdvancementProgress> progressMap = packet.getAdvancementsToProgress();
        AdvancementProgress progress = progressMap.get(targetId);
        
        if (progress != null && progress.isDone()) {
             RandomRunMod.LOGGER.info("Achievement {} completed! Triggering victory.", targetId);
             VictoryHandler.handleVictory();
        }
    }

    private void checkBattleEvents(AdvancementUpdateS2CPacket packet) {
        if (!com.randomrun.battle.BattleManager.getInstance().isInBattle()) return;
        if (!com.randomrun.battle.BattleManager.getInstance().getCurrentRoom().isPrivate()) return;
        
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;
        
        Map<Identifier, AdvancementProgress> progressMap = packet.getAdvancementsToProgress();
        
        for (Map.Entry<Identifier, AdvancementProgress> entry : progressMap.entrySet()) {
            Identifier id = entry.getKey();
            AdvancementProgress progress = entry.getValue();
            
            if (progress.isDone()) {
                // Get display info
                AdvancementEntry advEntry = client.getNetworkHandler().getAdvancementHandler().get(id);
                if (advEntry != null && advEntry.value().display().isPresent()) {
                    var display = advEntry.value().display().get();
                    String title = display.getTitle().getString();
                    String iconItem = net.minecraft.registry.Registries.ITEM.getId(display.getIcon().getItem()).toString();
                    
                    com.randomrun.battle.BattleManager.getInstance().reportAchievement(id.toString(), title, iconItem);
                }
            }
        }
    }
}

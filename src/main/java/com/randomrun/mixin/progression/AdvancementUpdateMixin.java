package com.randomrun.mixin.progression;

import com.randomrun.infrastructure.RandomRunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;

@Mixin(value = ClientPacketListener.class, priority = 1001)
public abstract class AdvancementUpdateMixin {
    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("RETURN"))
    private void onAdvancementPacket(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        try {
            checkBattleEvents(packet);
        } catch (Exception e) {
            RandomRunMod.LOGGER.debug("[Advancement] Ошибка проверки", e);
        }
    }

    private void checkBattleEvents(ClientboundUpdateAdvancementsPacket packet) {
        if (!com.randomrun.features.battle.manager.BattleManager.getInstance().isInBattle()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        
        for (Map.Entry<Identifier, AdvancementProgress> entry : packet.progress().entrySet()) {
            if (entry.getValue().isDone()) {
                reportAdvancement(client, entry.getKey());
            }
        }
    }
    
    private void reportAdvancement(Minecraft client, Identifier id) {
        AdvancementHolder advEntry = client.getConnection().getAdvancements().get(id);
        if (advEntry == null || advEntry.value().display().isEmpty()) return;
        
        com.randomrun.features.battle.manager.BattleManager.getInstance().reportAchievement(id.toString());
    }
}

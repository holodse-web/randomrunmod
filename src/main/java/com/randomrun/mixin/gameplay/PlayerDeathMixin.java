package com.randomrun.mixin.gameplay;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PlayerDeathMixin {
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

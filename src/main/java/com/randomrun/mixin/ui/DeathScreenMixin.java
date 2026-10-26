package com.randomrun.mixin.ui;

import com.randomrun.ui.screen.endgame.DefeatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onDeathScreenInit(CallbackInfo ci) {
        com.randomrun.core.session.RunSessionManager sessionManager = com.randomrun.core.session.RunSessionManager.getInstance();
        
        if (sessionManager.hasActiveRun()) {
            if (com.randomrun.features.battle.manager.BattleManager.getInstance().isInBattle()) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                com.randomrun.core.service.RunCompletionService.getInstance()
                    .completeWithDeath()
                    .thenAccept(record -> {
                        if (record != null && client.player != null) {
                            client.execute(() -> {
                                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                    net.minecraft.resources.Identifier.parse(record.getTargetId())
                                );
                                client.gui.setScreen(new DefeatScreen(
                                    item,
                                    record.getDuration(),
                                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.defeat.death").getString()
                                ));
                            });
                        }
                    });
            }
        }
    }
}

package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {
    
    // Внедрено в конструктор DeathScreen
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onDeathScreenInit(Text message, boolean isHardcore, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        // Если есть активный забег, закрыть экран смерти и показать экран поражения
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
            runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            
            // Если мы в битве, не показываем экран поражения (работает авто-возрождение)
            if (com.randomrun.battle.BattleManager.getInstance().isInBattle()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && (runManager.getTargetItem() != null || runManager.getTargetAdvancementId() != null)) {
                // Закрыть экран смерти и показать экран поражения
                client.execute(() -> {
                    long finalTime = runManager.getCurrentTime();
                    runManager.failRun();
                    
                    if (runManager.getTargetItem() != null) {
                        client.setScreen(new DefeatScreen(
                            runManager.getTargetItem(),
                            finalTime,
                            net.minecraft.text.Text.translatable("randomrun.defeat.death").getString()
                        ));
                    } else {
                        client.setScreen(new DefeatScreen(
                            runManager.getTargetAdvancementId(),
                            finalTime,
                            net.minecraft.text.Text.translatable("randomrun.defeat.death").getString()
                        ));
                    }
                });
            }
        }
    }
}

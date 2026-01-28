package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.gui.screen.TimeSelectionScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (com.randomrun.world.WorldCreator.hasPendingRun()) {
            CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
            CreateWorldScreenAccessor screenAccessor = (CreateWorldScreenAccessor) screen;
            
            try {
                net.minecraft.client.gui.screen.world.WorldCreator worldCreator = screenAccessor.getWorldCreator();
                if (worldCreator != null) {
                    String worldName = com.randomrun.world.WorldCreator.generateWorldName(
                        com.randomrun.world.WorldCreator.getPendingTargetItem()
                    );
                    
                    RandomRunMod.LOGGER.info("Setting up speedrun world: " + worldName);
                    
                    worldCreator.setWorldName(worldName);
                    RandomRunMod.LOGGER.info("World name set to: " + worldName);
                    
                    ((WorldCreatorAccessor) worldCreator).invokeSetGameMode(
                        net.minecraft.client.gui.screen.world.WorldCreator.Mode.HARDCORE
                    );
                    RandomRunMod.LOGGER.info("Game mode set to HARDCORE");
                    
                    String pendingSeed = com.randomrun.world.WorldCreator.getPendingSeed();
                    RandomRunMod.LOGGER.info("Pending seed from WorldCreator: " + pendingSeed);
                    
                    if (pendingSeed != null && !pendingSeed.isEmpty()) {
                        worldCreator.setSeed(pendingSeed);
                        com.randomrun.world.WorldCreator.setLastCreatedSeed(pendingSeed);
                        RandomRunMod.LOGGER.info("✓ Seed set to: " + pendingSeed);
                        
                        String actualSeed = worldCreator.getSeed();
                        RandomRunMod.LOGGER.info("✓ Verified seed from worldCreator: " + actualSeed);
                    } else {
                        String generatedSeed = String.valueOf(new java.util.Random().nextLong());
                        worldCreator.setSeed(generatedSeed);
                        com.randomrun.world.WorldCreator.setLastCreatedSeed(generatedSeed);
                        RandomRunMod.LOGGER.info("✓ Random seed " + generatedSeed);
                    }
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to configure speedrun world", e);
            }
            
            MinecraftClient.getInstance().execute(() -> {
                try {
                    ((CreateWorldScreenAccessor) screen).invokeCreateLevel();
                    RandomRunMod.LOGGER.info("World creation started");
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("Failed to start world creation", e);
                }
            });
        }
    }
}
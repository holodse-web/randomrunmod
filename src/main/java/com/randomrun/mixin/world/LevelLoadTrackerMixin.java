package com.randomrun.mixin.world;

import com.randomrun.ui.overlay.LoadingCurtain;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelLoadTracker.class)
public class LevelLoadTrackerMixin {
    @Redirect(method = "startClientLoad", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;CLIENT_WAIT_TIMEOUT_MS:J"))
    private long randomrun$shortClientWait() {
        return LoadingCurtain.isCovering() ? 4000L : 30000L;
    }
}

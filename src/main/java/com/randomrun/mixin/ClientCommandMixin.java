package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import com.randomrun.data.SecurityManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientCommandMixin {
    
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        // Check if run is active
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
            runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            
            // Allow /go command
            if (command.equals("go") || command.startsWith("go ")) {
                return;
            }
            
            // Block cheat commands
            if (SecurityManager.isCommandBlocked(command)) {
                ci.cancel();
                
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.translatable("randomrun.error.command_blocked"),
                        true
                    );
                }
            }
        }
    }
}

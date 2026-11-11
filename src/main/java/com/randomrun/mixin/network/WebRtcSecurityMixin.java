package com.randomrun.mixin.network;

import com.randomrun.network.WebRtcTransport;
import java.net.InetAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class WebRtcSecurityMixin {
    @Mixin(IntegratedServer.class)
    public static abstract class Bind {
        @Shadow private LanServerPinger lanPinger;
        @ModifyArg(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"), index = 0)
        private InetAddress randomrun$bindLocal(InetAddress address) { return WebRtcTransport.getInstance().isPublishing() ? InetAddress.getLoopbackAddress() : address; }
        @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("RETURN"))
        private void randomrun$stopLanAnnouncement(net.minecraft.server.MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> ci) {
            if (WebRtcTransport.getInstance().isPublishing() && lanPinger != null) { lanPinger.interrupt(); lanPinger = null; }
        }
    }
    @Mixin(ServerLoginPacketListenerImpl.class)
    public static abstract class Identity {
        @Shadow @Final private Connection connection;
        @Shadow public abstract void disconnect(Component reason);
        @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
        private void randomrun$requirePeerIdentity(ServerboundHelloPacket packet, CallbackInfo ci) {
            if (!WebRtcTransport.getInstance().isHosting() || connection.isMemoryConnection()) return;
            String expected = WebRtcTransport.getInstance().expectedPlayer(connection.getRemoteAddress());
            if (expected == null || !expected.equalsIgnoreCase(packet.name())) { disconnect(Component.literal("RandomRun: identity does not match the room session")); ci.cancel(); }
        }
    }
}

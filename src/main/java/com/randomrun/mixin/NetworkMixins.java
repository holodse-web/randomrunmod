package com.randomrun.mixin;

import com.randomrun.features.speedrun.WorldCreator;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.network.WebRtcTransport;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;


public final class NetworkMixins {
    private NetworkMixins() {}

    @Mixin(Minecraft.class)
    public static class DisconnectCleanupMixin {
        private static Path pendingWorldPath = null;
        private static String pendingWorldName = null;

        @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
        private void onDisconnectHead(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
            pendingWorldPath = null;
            pendingWorldName = null;

            Minecraft client = Minecraft.getInstance();

            if (client.getSingleplayerServer() == null) {
                return;
            }

            if (!shouldDeleteWorld()) {
                return;
            }

            try {
                Path worldRoot = getWorldPath(client);
                String worldName = worldRoot.getFileName().toString();



                if (!WorldCreator.isRunWorld(worldRoot)) {
                    return;
                }

                pendingWorldPath = worldRoot;
                pendingWorldName = worldName;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("[Cleanup] Could not resolve world path", e);
            }
        }

        @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
        private void onDisconnectTail(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
            if (pendingWorldPath != null && pendingWorldName != null) {
                Path worldRoot = pendingWorldPath;
                String worldName = pendingWorldName;
                pendingWorldPath = null;
                pendingWorldName = null;

                deleteWorldAsync(Minecraft.getInstance().getLevelSource(), worldRoot, worldName);
            }
        }

        private boolean shouldDeleteWorld() {
            ModConfig config = RandomRunMod.getInstance().getConfig();
            BattleManager battle = BattleManager.getInstance();

            return config.isDeleteWorldsAfterSpeedrun() && !battle.isSharedWorld();
        }

        private Path getWorldPath(Minecraft client) {
            Path worldRoot = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);

            if (worldRoot.getFileName().toString().equals(".")) {
                worldRoot = worldRoot.getParent();
            }

            worldRoot = worldRoot.toAbsolutePath().normalize();

            return worldRoot;
        }

        private void deleteWorldAsync(LevelStorageSource storage, Path worldRoot, String worldName) {
            CompletableFuture<Void> cleanupFinished = new CompletableFuture<>();
            Thread cleanupThread = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    if (!storage.getLevelPath(worldName).toAbsolutePath().normalize().equals(worldRoot)
                            || !WorldCreator.isRunWorld(worldRoot)) return;
                    try (var access = storage.validateAndCreateAccess(worldName)) {
                        if (WorldCreator.isRunWorld(worldRoot)) access.deleteLevel();
                    }
                } catch (net.minecraft.util.DirectoryLock.LockException | java.nio.channels.OverlappingFileLockException ignored) {
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("[Cleanup] World deletion failed: {}", worldRoot, e);
                } finally {
                    cleanupFinished.complete(null);
                }
            }, "RandomRun-WorldCleanup");
            cleanupThread.setDaemon(true);
            Thread shutdownHook = new Thread(() -> {
                try {
                    cleanupThread.join(10_000);
                    if (cleanupThread.isAlive()) {
                        RandomRunMod.LOGGER.warn("[Cleanup] World deletion timed out: {}", worldRoot);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "RandomRun-WorldCleanup-Shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            cleanupFinished.thenRun(() -> {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException e) {
                    return;
                }
            });
            cleanupThread.start();
        }

    }

    @Mixin(PlayerList.class)
    public static class PlayerConnectionMixin {
        @Inject(method = "placeNewPlayer", at = @At("TAIL"))
        private void randomrun$onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
            com.randomrun.features.upgrader.UpgraderServer.recoverReward(player);
            BattleManager manager = BattleManager.getInstance();
            BattleRoom room = manager.getCurrentRoom();
            if (manager.isHost() && room != null && room.isSharedWorld() && room.isPlayerDisconnected(player.getName().getString())) {
                player.setGameMode(GameType.SPECTATOR);
            }
        }
    }

    @Mixin(ServerGamePacketListenerImpl.class)
    public static class PlayerDisconnectMixin {
        @Shadow public ServerPlayer player;

        @Inject(method = "onDisconnect", at = @At("HEAD"))
        private void randomrun$onPlayerDisconnect(DisconnectionDetails details, CallbackInfo ci) {
            if (player == null) return;
            BattleManager manager = BattleManager.getInstance();
            BattleRoom room = manager.getCurrentRoom();
            if (manager.isHost() && room != null && room.isSharedWorld()) {
                manager.handlePlayerDisconnect(player.getName().getString());
            }
        }
    }

    @Mixin(IntegratedServer.class)
    public static abstract class WebRtcBindMixin {
        @Shadow private LanServerPinger lanPinger;
        @ModifyArg(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"), index = 0)
        private InetAddress randomrun$bindLocal(InetAddress address) { return WebRtcTransport.getInstance().isPublishing() ? InetAddress.getLoopbackAddress() : address; }
        @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("RETURN"))
        private void randomrun$stopLanAnnouncement(net.minecraft.server.MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> ci) {
            if (WebRtcTransport.getInstance().isPublishing() && lanPinger != null) { lanPinger.interrupt(); lanPinger = null; }
        }
    }

    @Mixin(ServerLoginPacketListenerImpl.class)
    public static abstract class WebRtcIdentityMixin {
        @Shadow @Final private net.minecraft.server.MinecraftServer server;
        @Shadow @Final private Connection connection;
        @Shadow public abstract void disconnect(Component reason);
        @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
        private void randomrun$requirePeerIdentity(ServerboundHelloPacket packet, CallbackInfo ci) {
            if (!WebRtcTransport.getInstance().requiresPeerIdentity(server) || connection.isMemoryConnection()) return;
            String expected = WebRtcTransport.getInstance().expectedPlayer(connection.getRemoteAddress());
            if (expected == null || !expected.equalsIgnoreCase(packet.name())) { disconnect(Component.literal("RandomRun: identity does not match the room session")); ci.cancel(); }
        }
    }
}

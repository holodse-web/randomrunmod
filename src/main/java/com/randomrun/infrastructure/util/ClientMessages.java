package com.randomrun.infrastructure.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;

public final class ClientMessages {
    private ClientMessages() {}

    public static void send(Player player, Component message, boolean actionBar) {
        if (actionBar) {
            player.sendOverlayMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }
}

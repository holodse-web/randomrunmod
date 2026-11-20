package com.randomrun.features.streamer;

import com.randomrun.ui.util.InterfaceStyle;

import com.randomrun.features.streamer.StreamerCharacter.Look.Body;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;


public final class StreamerRender {

    private static final Map<Integer, Body> SHAPES = new ConcurrentHashMap<>();

    private StreamerRender() {}

    public static void extract(Avatar entity, AvatarRenderState state) {
        if (!(entity instanceof Player player)) return;
        StreamerProfile profile = StreamerNet.profileOf(player.getUUID());
        if (profile == null) {
            SHAPES.remove(state.id);
            return;
        }
        StreamerCharacter character = profile.character();
        state.skin = StreamerSkin.skinFor(character);

        state.showHat = true;
        state.showJacket = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        if (state.nameTag != null) {
            if (InterfaceStyle.isClassic()) {
                state.nameTag = Component.literal(character.nickname()).withStyle(ChatFormatting.GOLD);
            } else {
                state.nameTag = Component.literal(character.nickname()).withStyle(ChatFormatting.LIGHT_PURPLE);
            }
        }
        Body body = character.look().body();
        if (body == Body.NORMAL) SHAPES.remove(state.id);
        else SHAPES.put(state.id, body);
    }

    public static void shape(PlayerModel model, AvatarRenderState state) {
        Body body = SHAPES.get(state.id);
        if (body != null) StreamerSkin.shape(model, body);
    }


    public static Identifier handTexture(Identifier original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return original;
        StreamerProfile own = StreamerNet.profileOf(mc.player.getUUID());
        return own == null ? original : StreamerSkin.skinFor(own.character()).body().texturePath();
    }

    public static void clear() {
        SHAPES.clear();
    }
}

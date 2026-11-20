package com.randomrun.features.streamer;

import com.randomrun.features.streamer.StreamerCharacter.Look.Body;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;





public final class StreamerSkin {
    private StreamerSkin() {}

    public static PlayerSkin skinFor(StreamerCharacter character) {
        return character.skin();
    }

    private static Identifier silhouette;


    public static Identifier silhouette() {
        if (silhouette == null) {
            com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setPixel(x, y, (y < 16 && x >= 32) ? 0 : 0xFF1C1C21);
            silhouette = Identifier.fromNamespaceAndPath("randomrun", "streamer/silhouette");
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(silhouette,
                new net.minecraft.client.renderer.texture.DynamicTexture(() -> "streamer silhouette", image));
        }
        return silhouette;
    }

    static PlayerSkin load(String texture) {
        Identifier id = Identifier.fromNamespaceAndPath("randomrun", "streamer/" + texture);
        return new PlayerSkin(new ClientAsset.ResourceTexture(id), null, null, PlayerModelType.WIDE, true);
    }


    public static void shape(PlayerModel model, Body body) {
        switch (body) {
            case CHUBBY -> {
                scale(model.body, 1.32f, 1.0f, 1.6f);
                model.rightArm.x -= 1.3f;
                model.leftArm.x += 1.3f;
                scale(model.rightArm, 1.15f, 1.0f, 1.2f);
                scale(model.leftArm, 1.15f, 1.0f, 1.2f);
                scale(model.rightLeg, 1.2f, 1.0f, 1.3f);
                scale(model.leftLeg, 1.2f, 1.0f, 1.3f);
                model.rightLeg.x -= 0.4f;
                model.leftLeg.x += 0.4f;
                model.head.zScale *= 1.04f;
            }
            case SLIM -> {
                scale(model.body, 0.9f, 1.0f, 0.85f);
                model.rightArm.x += 0.4f;
                model.leftArm.x -= 0.4f;
                scale(model.rightArm, 0.88f, 1.0f, 0.88f);
                scale(model.leftArm, 0.88f, 1.0f, 0.88f);
                scale(model.rightLeg, 0.9f, 1.0f, 0.9f);
                scale(model.leftLeg, 0.9f, 1.0f, 0.9f);
            }
            case STRONG -> {
                scale(model.body, 1.22f, 1.0f, 1.15f);
                model.rightArm.x -= 1.0f;
                model.leftArm.x += 1.0f;
                scale(model.rightArm, 1.28f, 1.0f, 1.22f);
                scale(model.leftArm, 1.28f, 1.0f, 1.22f);
                scale(model.rightLeg, 1.1f, 1.0f, 1.1f);
                scale(model.leftLeg, 1.1f, 1.0f, 1.1f);
            }
            default -> {
            }
        }
    }

    private static void scale(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z) {
        part.xScale *= x;
        part.yScale *= y;
        part.zScale *= z;
    }

}

package com.randomrun.features.streamer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;





public final class StreamerVoice {

    private static final Map<String, SoundInstance> PLAYING = new HashMap<>();
    private static SoundInstance preview;

    private StreamerVoice() {}

    public static void play(String key, Entity speaker) {
        if (speaker == null) return;
        Identifier id = clip(key);
        if (id == null) return;
        start(speaker.getUUID().toString(), new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.PLAYERS,
            1.0f, 1.0f, speaker, speaker.getRandom().nextLong()));
    }

    public static void playFrom(String key, String speakerId, Entity speaker) {
        Minecraft mc = Minecraft.getInstance();
        if (!StreamerNet.canHear(speaker, mc.player)) return;
        Identifier id = clip(key);
        if (id == null) return;
        start(speakerId, new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.PLAYERS,
            1.0f, 1.0f, speaker, speaker.getRandom().nextLong()) {
                @Override
                public void tick() {
                    if (!StreamerNet.canHear(speaker, Minecraft.getInstance().player)) {
                        stop();
                        return;
                    }
                    super.tick();
                }
            });
    }

    private static Identifier clip(String key) {
        if (key == null || key.isEmpty()) return null;
        Identifier id = Identifier.fromNamespaceAndPath("randomrun", "streamer." + key);
        return Minecraft.getInstance().getSoundManager().getSoundEvent(id) == null ? null : id;
    }

    private static void start(String speakerId, SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        SoundInstance previous = PLAYING.remove(speakerId);
        if (previous != null) mc.getSoundManager().stop(previous);
        PLAYING.put(speakerId, sound);
        mc.getSoundManager().play(sound);
    }


    public static void preview(String key) {
        stopPreview();
        Identifier id = clip(key);
        if (id == null) return;
        preview = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0f, 1.0f);
        Minecraft.getInstance().getSoundManager().play(preview);
    }

    public static void stopPreview() {
        if (preview != null) Minecraft.getInstance().getSoundManager().stop(preview);
        preview = null;
    }

    public static void clear() {
        PLAYING.clear();
    }
}

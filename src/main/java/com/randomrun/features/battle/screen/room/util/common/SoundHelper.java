package com.randomrun.features.battle.screen.room.util.common;

import com.randomrun.infrastructure.RandomRunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class SoundHelper {
    public static void playClick() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
    }
    
    public static void playSuccess() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f);
    }
    
    public static void playError() {
        play(SoundEvents.VILLAGER_NO, 1.0f);
    }
    
    public static void playJoin() {
        play(SoundEvents.PLAYER_LEVELUP, 1.2f);
    }
    
    private static void play(SoundEvent sound, float pitch) {
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(sound, pitch, volume * UIConstants.SOUND_VOLUME_MULTIPLIER)
        );
    }
}

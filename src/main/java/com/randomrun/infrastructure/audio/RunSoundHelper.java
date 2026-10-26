package com.randomrun.infrastructure.audio;

import com.randomrun.infrastructure.RandomRunMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

public class RunSoundHelper {
    private static final String LOG_TAG = "[Audio]";
    
    private static final SoundEvent SOUND_START_SOLO = SoundEvents.NOTE_BLOCK_BELL.value();
    private static final SoundEvent SOUND_START_BATTLE = SoundEvents.NOTE_BLOCK_CHIME.value();
    private static final SoundEvent SOUND_ERROR = SoundEvents.VILLAGER_NO;
    
    public static void playStartSound(Player player, boolean isBattle) {
        if (player == null) {
            RandomRunMod.LOGGER.warn("{} Попытка воспроизведения звука для null игрока", LOG_TAG);
            return;
        }
        
        try {
            if (!isSoundEnabled()) {
                return;
            }
            
            float volume = getVolume();
            SoundEvent sound = isBattle ? SOUND_START_BATTLE : SOUND_START_SOLO;
            
            player.playSound(sound, volume, 1.0f);
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("{} Ошибка воспроизведения звука старта", LOG_TAG, e);
        }
    }
    
    public static void playErrorSound(Player player) {
        if (player == null) {
            return;
        }
        
        try {
            if (!isSoundEnabled()) {
                return;
            }
            
            float volume = getVolume();
            player.playSound(SOUND_ERROR, volume * 0.5f, 1.0f);
        } catch (Exception e) {
            RandomRunMod.LOGGER.debug("{} Ошибка воспроизведения звука ошибки", LOG_TAG, e);
        }
    }
    
    private static boolean isSoundEnabled() {
        return RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled();
    }
    
    private static float getVolume() {
        return RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
    }
}

/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.randomrun.RandomRunMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    
    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("randomrun.json");
    
    
    private HudPosition hudPosition = HudPosition.TOP_LEFT; 
    private int customHudX = 10;
    private int customHudY = 10;
    
    
    private boolean soundEffectsEnabled = true; 
    private int soundVolume = 100;
    
    
    private MusicMode musicMode = MusicMode.OFF; 
    private int musicVolume = 70;
    
   
    private boolean timeChallengeEnabled = false; 
    private int challengeDuration = 300; 
    private boolean useItemDifficulty = true;
    private boolean manualTimeEnabled = false; 
    private int manualTimeSeconds = 60; 
    
    
    private String language = "ru_ru";
    
    
    private InterfaceMode interfaceMode = InterfaceMode.YOUTUBE;
    
    
    private boolean allowUnobtainableItems = false; 
    private VictoryScreenMode victoryScreenMode = VictoryScreenMode.SHOW; 
    private boolean deleteWorldsAfterSpeedrun = false; 
    private boolean askForCustomSeed = false; 
    private String customSeed = ""; 
    
    public enum HudPosition {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER, CUSTOM
    }
    
    public enum InterfaceMode {
        YOUTUBE, TIKTOK
    }
    
    public enum MusicMode {
        OFF,        
        MENU_ONLY,  
        EVERYWHERE  
    }
    
    public enum VictoryScreenMode {
        SHOW,              
        HIDE,              
        SHOW_AFTER_10_SECONDS  
    }
    
  
    public HudPosition getHudPosition() {
        return hudPosition;
    }
    
    public void setHudPosition(HudPosition hudPosition) {
        this.hudPosition = hudPosition;
    }
    
    public int getCustomHudX() {
        return customHudX;
    }
    
    public void setCustomHudX(int customHudX) {
        this.customHudX = customHudX;
    }
    
    public int getCustomHudY() {
        return customHudY;
    }
    
    public void setCustomHudY(int customHudY) {
        this.customHudY = customHudY;
    }
    
    public boolean isSoundEffectsEnabled() {
        return soundEffectsEnabled;
    }
    
    public void setSoundEffectsEnabled(boolean soundEffectsEnabled) {
        this.soundEffectsEnabled = soundEffectsEnabled;
    }
    
    public int getSoundVolume() {
        return soundVolume;
    }
    
    public void setSoundVolume(int soundVolume) {
        this.soundVolume = Math.max(0, Math.min(100, soundVolume));
    }
    
    public MusicMode getMusicMode() {
        return musicMode;
    }
    
    public void setMusicMode(MusicMode musicMode) {
        this.musicMode = musicMode;
    }
    
    public int getMusicVolume() {
        return musicVolume;
    }
    
    public void setMusicVolume(int musicVolume) {
        this.musicVolume = Math.max(0, Math.min(100, musicVolume));
    }
    
    public boolean isTimeChallengeEnabled() {
        return timeChallengeEnabled;
    }
    
    public void setTimeChallengeEnabled(boolean timeChallengeEnabled) {
        this.timeChallengeEnabled = timeChallengeEnabled;
    }
    
    public int getChallengeDuration() {
        return challengeDuration;
    }
    
    public void setChallengeDuration(int challengeDuration) {
        this.challengeDuration = Math.max(30, Math.min(7200, challengeDuration));
    }
    
    public boolean isUseItemDifficulty() {
        return useItemDifficulty;
    }
    
    public void setUseItemDifficulty(boolean useItemDifficulty) {
        this.useItemDifficulty = useItemDifficulty;
    }
    
    public boolean isManualTimeEnabled() {
        return manualTimeEnabled;
    }
    
    public void setManualTimeEnabled(boolean manualTimeEnabled) {
        this.manualTimeEnabled = manualTimeEnabled;
    }
    
    public int getManualTimeSeconds() {
        return manualTimeSeconds;
    }
    
    public void setManualTimeSeconds(int manualTimeSeconds) {
        this.manualTimeSeconds = Math.max(10, Math.min(7200, manualTimeSeconds)); 
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public InterfaceMode getInterfaceMode() {
        return interfaceMode;
    }
    
    public void setInterfaceMode(InterfaceMode interfaceMode) {
        this.interfaceMode = interfaceMode;
    }
    
    public boolean isAllowUnobtainableItems() {
        return allowUnobtainableItems;
    }
    
    public void setAllowUnobtainableItems(boolean allowUnobtainableItems) {
        this.allowUnobtainableItems = allowUnobtainableItems;
    }
    
    public VictoryScreenMode getVictoryScreenMode() {
        return victoryScreenMode;
    }
    
    public void setVictoryScreenMode(VictoryScreenMode victoryScreenMode) {
        this.victoryScreenMode = victoryScreenMode;
    }
    
    public boolean isDeleteWorldsAfterSpeedrun() {
        return deleteWorldsAfterSpeedrun;
    }
    
    public void setDeleteWorldsAfterSpeedrun(boolean deleteWorldsAfterSpeedrun) {
        this.deleteWorldsAfterSpeedrun = deleteWorldsAfterSpeedrun;
    }
    
    public boolean isAskForCustomSeed() {
        return askForCustomSeed;
    }
    
    public void setAskForCustomSeed(boolean askForCustomSeed) {
        this.askForCustomSeed = askForCustomSeed;
    }
    
    public String getCustomSeed() {
        return customSeed;
    }
    
    public void setCustomSeed(String customSeed) {
        this.customSeed = customSeed;
    }
    
    
    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to load config", e);
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to save config", e);
        }
    }
}

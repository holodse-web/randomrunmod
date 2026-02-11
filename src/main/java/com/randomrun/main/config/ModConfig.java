/*

 * Copyright (c) 2026 Stanislav Kholod.

 * All rights reserved.

 */

package com.randomrun.main.config;



import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.gson.annotations.SerializedName;
import com.randomrun.main.RandomRunMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    

    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";

    

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("randomrun.json");

    

    

    @SerializedName("hudPosition")

    private HudPosition hudPosition = HudPosition.TOP_LEFT; 

    @SerializedName("customHudX")

    private int customHudX = 10;

    @SerializedName("customHudY")

    private int customHudY = 10;

    

    @SerializedName("soundEffectsEnabled")

    private boolean soundEffectsEnabled = true; 

    @SerializedName("soundVolume")

    private int soundVolume = 100;

    

    @SerializedName("musicMode")

    private MusicMode musicMode = MusicMode.OFF; 

    @SerializedName("musicVolume")

    private int musicVolume = 70;

    

    @SerializedName("timeChallengeEnabled")

    private boolean timeChallengeEnabled = false; 

    @SerializedName("challengeDuration")

    private int challengeDuration = 300; 

    @SerializedName("useItemDifficulty")

    private boolean useItemDifficulty = true;

    @SerializedName("manualTimeEnabled")

    private boolean manualTimeEnabled = false; 

    @SerializedName("manualTimeSeconds")

    private int manualTimeSeconds = 60; 

    

    @SerializedName("language")

    private String language = "ru_ru";

    

    @SerializedName("interfaceMode")

    private InterfaceMode interfaceMode = InterfaceMode.YOUTUBE;

    

    @SerializedName("allowUnobtainableItems")

    private boolean allowUnobtainableItems = false; 

    @SerializedName("victoryScreenMode")

    private VictoryScreenMode victoryScreenMode = VictoryScreenMode.SHOW; 

    @SerializedName("deleteWorldsAfterSpeedrun")

    private boolean deleteWorldsAfterSpeedrun = false; 

    @SerializedName("askForCustomSeed")

    private boolean askForCustomSeed = false; 

    @SerializedName("customSeed")

    private String customSeed = ""; 

    

    // Achievement Challenge

    @SerializedName("achievementChallengeEnabled")
    private boolean achievementChallengeEnabled = false;

    @SerializedName("useAchievementDifficulty")

    private boolean useAchievementDifficulty = true;

    

    @SerializedName("startMethod")

    private StartMethod startMethod = StartMethod.COMMAND;

    

    @SerializedName("startKey")

    private int startKey = 293; // Default F4 (GLFW_KEY_F4 = 293)

    

    @SerializedName("antiCheatWebhook")

    private String antiCheatWebhook = ""; // Discord Webhook URL for Anti-Cheat reports

    

    @SerializedName("scrollVersion")
    private int scrollVersion = 1; // 1 = classic, 2 = carousel

    @SerializedName("carouselDuration")
    private int carouselDuration = 3; // Default 3 seconds

    @SerializedName("onlineMode")
    private boolean onlineMode = false;
    
    @SerializedName("firstRun")
    private boolean firstRun = true;

    @SerializedName("blurEnabled")
    private boolean blurEnabled = false; // Default to false (disabled)

    @SerializedName("modifiersEnabled")
    private boolean modifiersEnabled = false;

    @SerializedName("hardcoreModeEnabled")
    private boolean hardcoreModeEnabled = true;

    public enum StartMethod {

        COMMAND, KEYBIND

    }

    

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

    

    public boolean isAchievementChallengeEnabled() {

        return achievementChallengeEnabled;

    }



    public void setAchievementChallengeEnabled(boolean achievementChallengeEnabled) {

        this.achievementChallengeEnabled = achievementChallengeEnabled;

    }



    public boolean isUseAchievementDifficulty() {

        return useAchievementDifficulty;

    }



    public void setUseAchievementDifficulty(boolean useAchievementDifficulty) {

        this.useAchievementDifficulty = useAchievementDifficulty;

    }



    public StartMethod getStartMethod() {

        return startMethod;

    }



    public void setStartMethod(StartMethod startMethod) {

        this.startMethod = startMethod;

    }



    public int getStartKey() {

        return startKey;

    }



    public void setStartKey(int startKey) {

        this.startKey = startKey;

    }

    public boolean isOnlineMode() {
        // Until the user confirms the first run, online mode is effectively disabled
        if (firstRun) {
            return false;
        }
        return onlineMode;
    }

    public void setOnlineMode(boolean onlineMode) {
        boolean wasOnline = this.onlineMode;
        this.onlineMode = onlineMode;
        
        // If mode changed
        if (wasOnline != onlineMode) {
             // 1. Load/Reload profile (logic inside handles online/offline check)
             try {
                 if (net.minecraft.client.MinecraftClient.getInstance().getSession() != null) {
                     String playerName = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
                     // Chain the reload results to happen AFTER profile is loaded
                     com.randomrun.main.data.PlayerProfile.load(playerName).thenRun(() -> {
                         if (RandomRunMod.getInstance().getRunDataManager() != null) {
                             RandomRunMod.getInstance().getRunDataManager().reloadResults();
                         }
                     });
                 }
             } catch (Exception e) {
                 // Ignore
             }
        }
    }

    public boolean isFirstRun() {
        return firstRun;
    }

    public void setFirstRun(boolean firstRun) {
        this.firstRun = firstRun;
    }

    public boolean isBlurEnabled() {
        return blurEnabled;
    }

    public void setBlurEnabled(boolean blurEnabled) {
        this.blurEnabled = blurEnabled;
    }

    public boolean isModifiersEnabled() {
        return modifiersEnabled;
    }

    public void setModifiersEnabled(boolean modifiersEnabled) {
        this.modifiersEnabled = modifiersEnabled;
    }

    public boolean isHardcoreModeEnabled() {
        return hardcoreModeEnabled;
    }

    public void setHardcoreModeEnabled(boolean hardcoreModeEnabled) {
        this.hardcoreModeEnabled = hardcoreModeEnabled;
    }

    private static final java.util.concurrent.ExecutorService IO_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();

    public void save() {
        // Create a snapshot (deep copy via JSON serialization/deserialization is safest but slow, 
        // or just rely on the fact that primitive fields are copied by value during serialization if we do it fast enough.
        // For config, simple fields are fine. But to be safe against concurrent modification, 
        // we can serialize in the main thread (fast) and write to disk in the background.
        
        try {
            final String json = GSON.toJson(this);
            IO_EXECUTOR.submit(() -> {
                try {
                    Files.createDirectories(CONFIG_PATH.getParent());
                    try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                        writer.write(json);
                    }
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("Failed to save config", e);
                }
            });
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to serialize config for saving", e);
        }
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

    
}


package com.randomrun.infrastructure.security;

import com.randomrun.infrastructure.util.CompactLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.File;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModScannerService {
    private static final Set<String> BLACKLISTED_MODS = Set.of(
        "wurst", "wurstclient", "meteor", "meteorclient",
        "liquidbounce", "impact", "sigma", "aristois",
        "flux", "wolfram", "inertia", "phobos",
        "kami", "salhack", "future", "rusherhack",
        "konas", "ares", "pyro", "catalyst",
        "lambda", "gamesense", "3arthh4ck", "earthhack",
        "creepy-salhack", "creepysalhack", "wwe", "wweclient",
        "bleachhack", "bleach", "atomic", "atomicclient",
        "cornos", "cornosclient", "gopro", "goproclient",
        "kamiblue", "kami-blue",
        
        "xray", "x-ray", "xray-mod", "xraymod",
        "advanced-xray", "advancedxray", "xaero-xray",
        "cavefinder", "cave-finder", "oredetector", "ore-detector",
        
        "voxelmap", "journeymap", "xaeros-minimap", "xaerosminimap",
        "xaeros-worldmap", "xaerosworldmap",
        
        "baritone", "autofish", "auto-fish", "autoclicker", "auto-clicker",
        "automine", "auto-mine", "autofarm", "auto-farm",
        "killaura", "kill-aura", "autocrystal", "auto-crystal",
        
        "freecam", "free-cam", "schematica", "litematica-printer",
        "tweakeroo", "easyplace", "easy-place",
        "scaffold", "tower", "fly", "speed", "bhop",
        "nofallmod", "no-fall", "antiknockback", "anti-knockback"
    );
    
    private static final Set<String> BLACKLISTED_RESOURCE_PACKS = Set.of(
        "xray", "x-ray", "xray-pack", "xraypack",
        "ore-highlighter", "orehighlighter", "ore-outline", "oreoutline",
        "transparent", "see-through", "seethrough",
        "cheat", "hack", "esp", "wallhack", "wall-hack",
        "fullbright", "full-bright", "gamma", "night-vision", "nightvision"
    );

    private static final Set<String> SUSPICIOUS_CLASSES = Set.of(
        "killaura", "xray", "fly", "freecam", "scaffold",
        "automine", "speedhack", "reach", "criticals"
    );

    private static class Holder {
        private static final ModScannerService INSTANCE = new ModScannerService();
    }

    public static ModScannerService getInstance() {
        return Holder.INSTANCE;
    }

    private List<String> suspiciousMods = null;
    private List<String> suspiciousResourcePacks = null;
    private Map<String, String> detectionReasons = new HashMap<>();
    private boolean scanned = false;
    private boolean isTrusted = true;

    public void scan() {
        suspiciousMods = new ArrayList<>();
        suspiciousResourcePacks = new ArrayList<>();
        detectionReasons.clear();
        isTrusted = true;

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId().toLowerCase();
            String displayName = mod.getMetadata().getName() + " (" + modId + ")";

            if (isBlacklisted(modId)) {
                suspiciousMods.add(displayName);
                detectionReasons.put(displayName, "Читерский мод обнаружен");
                isTrusted = false;
                CompactLogger.error("Читерский мод обнаружен: {}", displayName);
                
                try {
                    mod.getOrigin().getPaths().forEach(path -> {
                        File modFile = path.toFile();
                        if (modFile.exists() && modFile.isFile()) {
                            String reason = scanModClasses(modFile, displayName);
                            if (reason != null) {
                                detectionReasons.put(displayName, "Читерский мод: " + reason);
                            }
                        }
                    });
                } catch (UnsupportedOperationException e) {
                    CompactLogger.debug("Skipping nested mod: {}", displayName);
                }
            }
        }

        File resourcePacksDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "resourcepacks");
        if (resourcePacksDir.exists() && resourcePacksDir.isDirectory()) {
            File[] files = resourcePacksDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    boolean isBlacklisted = false;
                    
                    if (file.isFile() && (name.endsWith(".zip") || name.endsWith(".jar"))) {
                        if (isBlacklistedResourcePack(name)) {
                            suspiciousResourcePacks.add(name);
                            detectionReasons.put(name, "Читерский ресурспак обнаружен");
                            isTrusted = false;
                            isBlacklisted = true;
                            CompactLogger.error("Читерский ресурспак обнаружен: {}", name);
                        }
                    } else if (file.isDirectory() && !name.startsWith(".")) {
                        if (isBlacklistedResourcePack(name)) {
                            suspiciousResourcePacks.add(name + "/");
                            detectionReasons.put(name + "/", "Читерский ресурспак обнаружен");
                            isTrusted = false;
                            isBlacklisted = true;
                            CompactLogger.error("Читерский ресурспак (папка) обнаружен: {}", name);
                        }
                    }
                }
            }
        }

        scanned = true;

        if (isTrusted) {
            CompactLogger.security("Trusted mode - читерские моды не обнаружены");
        } else {
            CompactLogger.warn("Untrusted mode активирован: {} читерских модов, {} читерских ресурспаков",
                suspiciousMods.size(), suspiciousResourcePacks.size());
        }
    }

    private boolean isBlacklisted(String modId) {
        if (BLACKLISTED_MODS.contains(modId)) {
            return true;
        }
        
        for (String blacklisted : BLACKLISTED_MODS) {
            if (modId.contains(blacklisted)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean isBlacklistedResourcePack(String packName) {
        String lowerName = packName.toLowerCase()
            .replace(".zip", "")
            .replace(".jar", "")
            .replace("_", "-")
            .replace(" ", "-");
        
        if (BLACKLISTED_RESOURCE_PACKS.contains(lowerName)) {
            return true;
        }
        
        for (String blacklisted : BLACKLISTED_RESOURCE_PACKS) {
            if (lowerName.contains(blacklisted)) {
                return true;
            }
        }
        
        return false;
    }

    private String scanModClasses(File modFile, String modName) {
        try {
            try (JarFile jar = new JarFile(modFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName().toLowerCase();
                    for (String suspicious : SUSPICIOUS_CLASSES) {
                        if (name.contains(suspicious)) {
                            CompactLogger.error("Suspicious class in {}: {}", modName, name);
                            return "Suspicious class: " + suspicious;
                        }
                    }
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("Scan error {}: {}", modName, e.getMessage());
        }
        return null;
    }

    public void rescan() {
        scanned = false;
        scan();
    }

    public boolean isScanned() {
        return scanned;
    }

    public boolean isTrusted() {
        return scanned && isTrusted;
    }

    public boolean hasSuspiciousMods() {
        return scanned && !suspiciousMods.isEmpty();
    }

    public boolean hasSuspiciousResourcePacks() {
        return scanned && !suspiciousResourcePacks.isEmpty();
    }

    public List<String> getSuspiciousMods() {
        return suspiciousMods != null ? suspiciousMods : new ArrayList<>();
    }

    public List<String> getSuspiciousResourcePacks() {
        return suspiciousResourcePacks != null ? suspiciousResourcePacks : new ArrayList<>();
    }

    public String getDetectionReason(String modOrPackName) {
        return detectionReasons.getOrDefault(modOrPackName, "Unknown reason");
    }

    public Map<String, String> getAllDetectionReasons() {
        return new HashMap<>(detectionReasons);
    }
}

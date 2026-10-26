package com.randomrun.core.session;

import com.randomrun.infrastructure.util.CompactLogger;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

public class WorldIntegrityChecker {
    private static final String LOG_TAG = "[Integrity]";
    
    private static final String[] DIMENSION_FOLDERS = {
        "DIM-1",
        "DIM1"
    };
    
    private final Map<String, DimensionSnapshot> snapshots = new HashMap<>();
    private final Path worldPath;
    
    public WorldIntegrityChecker(MinecraftServer server) {
        this.worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
    }
    
    public void captureSnapshot() {
        snapshots.clear();
        
        for (String dimFolder : DIMENSION_FOLDERS) {
            Path dimPath = worldPath.resolve(dimFolder);
            
            if (!Files.exists(dimPath)) {
                snapshots.put(dimFolder, DimensionSnapshot.notGenerated());
                continue;
            }
            
            try {
                DimensionSnapshot snapshot = createSnapshot(dimPath);
                snapshots.put(dimFolder, snapshot);
            } catch (Exception e) {
                CompactLogger.error("{} Ошибка создания снимка {}: {}", LOG_TAG, dimFolder, e.getMessage());
                snapshots.put(dimFolder, DimensionSnapshot.error());
            }
        }
    }
    
    public boolean validateIntegrity() {
        for (String dimFolder : DIMENSION_FOLDERS) {
            Path dimPath = worldPath.resolve(dimFolder);
            DimensionSnapshot originalSnapshot = snapshots.get(dimFolder);
            
            if (originalSnapshot == null) {
                CompactLogger.warn("{} Нет снимка для {}", LOG_TAG, dimFolder);
                return false;
            }
            
            if (!originalSnapshot.wasGenerated) {
                if (Files.exists(dimPath)) {
                    CompactLogger.warn("{} {} появилось во время забега!", LOG_TAG, dimFolder);
                    return false;
                }
                continue;
            }
            
            if (!Files.exists(dimPath)) {
                CompactLogger.warn("{} {} исчезло во время забега!", LOG_TAG, dimFolder);
                return false;
            }
            
            try {
                DimensionSnapshot currentSnapshot = createSnapshot(dimPath);
                
                if (currentSnapshot.fileCount < originalSnapshot.fileCount) {
                    CompactLogger.warn("{} {} - файлов стало меньше ({} -> {})", 
                        LOG_TAG, dimFolder, originalSnapshot.fileCount, currentSnapshot.fileCount);
                    return false;
                }
                
                if (!currentSnapshot.contentHash.equals(originalSnapshot.contentHash)) {
                    CompactLogger.warn("{} {} - изменен хеш содержимого", LOG_TAG, dimFolder);
                    return false;
                }
            } catch (Exception e) {
                CompactLogger.error("{} Ошибка проверки {}: {}", LOG_TAG, dimFolder, e.getMessage());
                return false;
            }
        }
        
        return true;
    }
    
    private DimensionSnapshot createSnapshot(Path dimPath) throws IOException {
        List<Path> regionFiles = new ArrayList<>();
        
        Path regionsPath = dimPath.resolve("region");
        if (Files.exists(regionsPath)) {
            try (Stream<Path> files = Files.walk(regionsPath)) {
                files.filter(p -> p.toString().endsWith(".mca"))
                     .forEach(regionFiles::add);
            }
        }
        
        regionFiles.sort(Comparator.comparing(Path::toString));
        
        StringBuilder hashInput = new StringBuilder();
        for (Path file : regionFiles) {
            hashInput.append(file.getFileName().toString());
            hashInput.append(":");
            hashInput.append(Files.size(file));
            hashInput.append(";");
        }
        
        String hash = computeHash(hashInput.toString());
        
        return new DimensionSnapshot(true, regionFiles.size(), hash);
    }
    
    private String computeHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }
    
    private static class DimensionSnapshot {
        final boolean wasGenerated;
        final int fileCount;
        final String contentHash;
        
        DimensionSnapshot(boolean wasGenerated, int fileCount, String contentHash) {
            this.wasGenerated = wasGenerated;
            this.fileCount = fileCount;
            this.contentHash = contentHash;
        }
        
        static DimensionSnapshot notGenerated() {
            return new DimensionSnapshot(false, 0, "");
        }
        
        static DimensionSnapshot error() {
            return new DimensionSnapshot(true, -1, "error");
        }
    }
}

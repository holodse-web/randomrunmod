package com.randomrun.core.data;

import com.randomrun.core.data.DataSyncService.StorageAdapter;
import com.randomrun.core.run.Run;
import com.randomrun.infrastructure.RandomRunMod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;

public enum LocalStorage implements StorageAdapter {
    INSTANCE;
    
    private static final String LOG_TAG = "[Local]";
    private static final Gson GSON = new Gson();
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "LocalStorage-IO");
        t.setDaemon(true);
        return t;
    });
    
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final int MAX_BACKUPS = 5;
    private static final int MAX_FILE_SIZE_MB = 50;
    private static final long CACHE_TTL_MS = 30_000;
    private static final int MAX_CACHE_SIZE = 100;
    private static final int DATA_VERSION = 1;
    
    private final Path configDir;
    private final Path backupDir;
    private final ReentrantLock writeLock = new ReentrantLock();
    
    private final Map<String, CachedHistory> historyCache = Collections.synchronizedMap(
        new LinkedHashMap<String, CachedHistory>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedHistory> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );
    
    private final AtomicInteger successfulReads = new AtomicInteger(0);
    private final AtomicInteger failedReads = new AtomicInteger(0);
    private final AtomicInteger successfulWrites = new AtomicInteger(0);
    private final AtomicInteger failedWrites = new AtomicInteger(0);
    
    LocalStorage() {
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("randomrun");
        this.backupDir = configDir.resolve("backup");
        
        try {
            Files.createDirectories(configDir);
            Files.createDirectories(backupDir);
            
            if (!Files.isWritable(configDir)) {
                throw new IOException("Storage directory is not writable");
            }
        } catch (IOException e) {
            RandomRunMod.LOGGER.error("{} Initialization failed", LOG_TAG, e);
        }
    }
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IO_EXECUTOR.shutdown();
        }));
    }
    
    @Override
    public CompletableFuture<List<Run>> loadHistory(String playerName) {
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        CachedHistory cached = historyCache.get(playerName);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(new ArrayList<>(cached.records));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            writeLock.lock();
            try {
                List<Run> records = loadAvailableHistory(playerName);
                
                historyCache.put(playerName, new CachedHistory(records));
                
                successfulReads.incrementAndGet();
                return records;
            } catch (Exception e) {
                failedReads.incrementAndGet();
                RandomRunMod.LOGGER.error("{} History load failed: {}", LOG_TAG, e.getMessage());
                return new ArrayList<>();
            } finally {
                writeLock.unlock();
            }
        }, IO_EXECUTOR);
    }
    
    @Override
    public CompletableFuture<Boolean> saveRecord(String playerName, Run record) {
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            writeLock.lock();
            try {
                File historyFile = getHistoryFile(playerName);
                List<Run> records;
                try {
                    records = loadRecordsFromFile(historyFile);
                } catch (IOException unreadable) {
                    historyFile = getRecoveryFile(playerName);
                    records = loadRecordsFromFile(historyFile);
                }
                if (records.stream().anyMatch(saved -> saved.historyKey().equals(record.historyKey()))) return true;
                records.add(record);
                
                if (records.size() > MAX_HISTORY_SIZE) {
                    records = new ArrayList<>(records.subList(records.size() - MAX_HISTORY_SIZE, records.size()));
                }
                
                if (historyFile.exists()) {
                    createBackup(historyFile);
                }
                
                if (!writeRecordsAtomic(historyFile, records)) {
                    failedWrites.incrementAndGet();
                    return false;
                }
                
                historyCache.remove(playerName);
                
                successfulWrites.incrementAndGet();
                return true;
            } catch (Exception e) {
                failedWrites.incrementAndGet();
                RandomRunMod.LOGGER.error("{} Run save failed: {}", LOG_TAG, e.getMessage());
                return false;
            } finally {
                writeLock.unlock();
            }
        }, IO_EXECUTOR);
    }
    
    @Override
    public CompletableFuture<Boolean> saveHistory(String playerName, List<Run> records) {
        if (!validatePlayerName(playerName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            writeLock.lock();
            try {
                File historyFile = getHistoryFile(playerName);
                
                loadRecordsFromFile(historyFile);
                if (historyFile.exists()) createBackup(historyFile);
                
                if (!writeRecordsAtomic(historyFile, records)) {
                    failedWrites.incrementAndGet();
                    return false;
                }
                
                historyCache.remove(playerName);
                
                successfulWrites.incrementAndGet();
                return true;
            } catch (Exception e) {
                failedWrites.incrementAndGet();
                RandomRunMod.LOGGER.error("{} History save failed: {}", LOG_TAG, e.getMessage());
                return false;
            } finally {
                writeLock.unlock();
            }
        }, IO_EXECUTOR);
    }
    
    @Override
    public CompletableFuture<Boolean> isAvailable() {
        return CompletableFuture.completedFuture(Files.isWritable(configDir));
    }
    
    @Override
    public String getType() {
        return "local";
    }
    
    private boolean validatePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            RandomRunMod.LOGGER.warn("{} Missing player name", LOG_TAG);
            return false;
        }
        return true;
    }
    
    private List<Run> loadAvailableHistory(String playerName) {
        List<Run> records = new ArrayList<>();
        Set<Run.Key> keys = new HashSet<>();
        for (File file : List.of(getHistoryFile(playerName), getRecoveryFile(playerName))) {
            try {
                for (Run run : loadRecordsFromFile(file)) {
                    if (keys.add(run.historyKey())) records.add(run);
                }
            } catch (IOException unreadable) {
                failedReads.incrementAndGet();
            }
        }
        records.sort(Comparator.comparingLong(Run::getTimestamp));
        return records;
    }

    private List<Run> loadRecordsFromFile(File historyFile) throws IOException {
        Path path = historyFile.toPath();
        try {
            if (Files.size(path) > MAX_FILE_SIZE_MB * 1024L * 1024L) throw new IOException("History exceeds size limit");
            try (Reader reader = new FileReader(historyFile)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null || !root.has("records")) throw new IOException("Missing history records");
                int version = root.has("version") ? root.get("version").getAsBigDecimal().intValueExact() : 0;
                if (version > DATA_VERSION) throw new IOException("Unsupported history version: " + version);
                List<Run> records = GSON.fromJson(root.get("records"), new TypeToken<List<Run>>(){}.getType());
                if (records == null || records.stream().anyMatch(run ->
                        run == null || run.getTargetId() == null || run.getTargetId().isBlank() || run.getResult() == null)) {
                    throw new IOException("Invalid history record");
                }
                return records;
            }
        } catch (java.nio.file.NoSuchFileException missing) {
            return new ArrayList<>();
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid history file", invalid);
        }
    }

    private boolean writeRecordsAtomic(File targetFile, List<Run> records) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("history_", ".tmp", configDir.toFile());
            
            try (Writer writer = new FileWriter(tempFile)) {
                JsonObject root = new JsonObject();
                root.addProperty("version", DATA_VERSION);
                root.add("records", GSON.toJsonTree(records));
                GSON.toJson(root, writer);
            }
            
            Files.move(tempFile.toPath(), targetFile.toPath(), 
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            return true;
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Atomic write failed: {}", LOG_TAG, e.getMessage());
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return false;
        }
    }
    
    private File getHistoryFile(String playerName) {
        return configDir.resolve("history_" + sanitizeName(playerName) + ".json").toFile();
    }
    
    private File getRecoveryFile(String playerName) {
        return configDir.resolve("history_" + sanitizeName(playerName) + ".recovery.json").toFile();
    }

    private void createBackup(File file) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            File backupFile = backupDir.resolve(file.getName() + "." + timestamp + ".bak.gz").toFile();
            
            try (FileInputStream fis = new FileInputStream(file);
                 FileOutputStream fos = new FileOutputStream(backupFile);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    gzos.write(buffer, 0, len);
                }
            }
            
            cleanupOldBackups(file.getName());
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("{} Backup failed: {}", LOG_TAG, e.getMessage());
        }
    }
    
    private void cleanupOldBackups(String baseName) {
        try {
            File[] backups = backupDir.toFile().listFiles((dir, name) -> 
                name.startsWith(baseName) && (name.endsWith(".bak") || name.endsWith(".bak.gz")));
            
            if (backups != null && backups.length > MAX_BACKUPS) {
                Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
                
                for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
                    if (!backups[i].delete()) {
                        RandomRunMod.LOGGER.warn("{} Could not remove old backup: {}", LOG_TAG, backups[i].getName());
                    }
                }
            }
        } catch (Exception e) {
        }
    }
    
    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    
    @Override
    public CompletableFuture<Map<String, Integer>> getMetrics() {
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("successfulReads", successfulReads.get());
        metrics.put("failedReads", failedReads.get());
        metrics.put("successfulWrites", successfulWrites.get());
        metrics.put("failedWrites", failedWrites.get());
        metrics.put("cacheSize", historyCache.size());
        return CompletableFuture.completedFuture(metrics);
    }
    
    private static class CachedHistory {
        final List<Run> records;
        final long timestamp;
        
        CachedHistory(List<Run> records) {
            this.records = new ArrayList<>(records);
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}

package com.randomrun.core.storage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.randomrun.core.history.Run;
import com.randomrun.infrastructure.RandomRunMod;
import net.fabricmc.loader.api.FabricLoader;

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
                throw new IOException("Директория недоступна для записи");
            }
        } catch (IOException e) {
            RandomRunMod.LOGGER.error("{} Инициализация - ОШИБКА", LOG_TAG, e);
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
            try {
                File historyFile = getHistoryFile(playerName);
                
                if (!historyFile.exists()) {
                    return new ArrayList<Run>();
                }
                
                long fileSizeMB = historyFile.length() / (1024 * 1024);
                if (fileSizeMB > MAX_FILE_SIZE_MB) {
                    RandomRunMod.LOGGER.warn("{} Файл слишком большой: {}MB", LOG_TAG, fileSizeMB);
                    failedReads.incrementAndGet();
                    return new ArrayList<>();
                }
                
                List<Run> records = loadRecordsFromFile(historyFile);
                
                historyCache.put(playerName, new CachedHistory(records));
                
                successfulReads.incrementAndGet();
                return records;
            } catch (Exception e) {
                failedReads.incrementAndGet();
                RandomRunMod.LOGGER.error("{} Загрузка истории - ОШИБКА: {}", LOG_TAG, e.getMessage());
                return new ArrayList<>();
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
                List<Run> records = loadRecordsFromFile(historyFile);
                
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
                RandomRunMod.LOGGER.error("{} Сохранение записи - ОШИБКА: {}", LOG_TAG, e.getMessage());
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
                RandomRunMod.LOGGER.error("{} Сохранение истории - ОШИБКА: {}", LOG_TAG, e.getMessage());
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
            RandomRunMod.LOGGER.warn("{} Пустое имя игрока", LOG_TAG);
            return false;
        }
        return true;
    }
    
    private List<Run> loadRecordsFromFile(File historyFile) {
        if (!historyFile.exists()) {
            return new ArrayList<>();
        }
        
        try (Reader reader = new FileReader(historyFile)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            
            if (root == null || !root.has("records")) {
                return new ArrayList<>();
            }
            
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version > DATA_VERSION) {
                RandomRunMod.LOGGER.warn("{} Неподдерживаемая версия данных: {}", LOG_TAG, version);
                return new ArrayList<>();
            }
            
            List<Run> loaded = GSON.fromJson(root.get("records"), 
                new TypeToken<List<Run>>(){}.getType());
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            RandomRunMod.LOGGER.warn("{} Ошибка чтения файла: {}", LOG_TAG, e.getMessage());
            return new ArrayList<>();
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
            RandomRunMod.LOGGER.error("{} Атомарная запись - ОШИБКА: {}", LOG_TAG, e.getMessage());
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return false;
        }
    }
    
    private File getHistoryFile(String playerName) {
        return configDir.resolve("history_" + sanitizeName(playerName) + ".json").toFile();
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
            RandomRunMod.LOGGER.warn("{} Бэкап - ОШИБКА: {}", LOG_TAG, e.getMessage());
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
                        RandomRunMod.LOGGER.warn("{} Не удалось удалить старый бэкап: {}", LOG_TAG, backups[i].getName());
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

package com.randomrun.infrastructure.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.randomrun.infrastructure.RandomRunMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class PlayerUuidManager {
    private static final String LOG_TAG = "[UUID]";
    
    private static final PlayerUuidManager INSTANCE = new PlayerUuidManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String UUID_FILENAME = "randomrun_uuid.json";
    private static final String UUID_BACKUP_SUFFIX = ".backup";
    private static final String UUID_TEMP_SUFFIX = ".tmp";
    
    private static final Path CONFIG_DIR;
    private static final Path UUID_FILE;
    private static final Path UUID_BACKUP;
    private static final Path UUID_TEMP;
    
    static {
        Path configDir;
        try {
            configDir = FabricLoader.getInstance().getConfigDir();
            if (configDir == null) {
                System.err.println("[UUID] Config directory is null from FabricLoader, using fallback");
                configDir = Path.of(System.getProperty("user.home"), ".minecraft", "config");
            }
        } catch (Exception e) {
            System.err.println("[UUID] Failed to get config directory: " + e.getMessage());
            configDir = Path.of(System.getProperty("user.home"), ".minecraft", "config");
        }
        
        CONFIG_DIR = configDir;
        UUID_FILE = CONFIG_DIR != null ? CONFIG_DIR.resolve(UUID_FILENAME) : Path.of(UUID_FILENAME);
        UUID_BACKUP = CONFIG_DIR != null ? CONFIG_DIR.resolve(UUID_FILENAME + UUID_BACKUP_SUFFIX) : Path.of(UUID_FILENAME + UUID_BACKUP_SUFFIX);
        UUID_TEMP = CONFIG_DIR != null ? CONFIG_DIR.resolve(UUID_FILENAME + UUID_TEMP_SUFFIX) : Path.of(UUID_FILENAME + UUID_TEMP_SUFFIX);
    }
    
    private String playerUuid;
    
    private PlayerUuidManager() {
        try {
            load();
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Критическая ошибка инициализации", LOG_TAG, e);
            try {
                playerUuid = UUID.randomUUID().toString();
            } catch (Exception ex) {
                RandomRunMod.LOGGER.error("{} Не удалось создать аварийный UUID", LOG_TAG, ex);
            }
        }
    }
    
    public static PlayerUuidManager getInstance() {
        return INSTANCE;
    }
    
    public String getPlayerUuid() {
        if (playerUuid == null) {
            generateNewUuid();
        }
        return playerUuid;
    }
    
    public boolean isInitialized() {
        return playerUuid != null;
    }
    
    public void resetUuid() {
        backupCurrentFile();
        generateNewUuid();
    }
    
    private void generateNewUuid() {
        playerUuid = UUID.randomUUID().toString();
        save();
    }
    
    private void load() {
        if (UUID_FILE != null && Files.exists(UUID_FILE)) {
            if (loadFromExistingFile()) {
                return;
            }
        }
        
        generateNewUuid();
    }
    
    private boolean loadFromExistingFile() {
        try {
            UuidStorage storage = loadFromFile();
            if (isValidStorage(storage)) {
                playerUuid = storage.uuid;
                return true;
            }
            RandomRunMod.LOGGER.warn("{} Невалидный UUID в файле", LOG_TAG);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка загрузки из файла", LOG_TAG, e);
        }
        
        return tryRestoreFromBackup();
    }
    
    private UuidStorage loadFromFile() throws IOException {
        try (Reader reader = Files.newBufferedReader(UUID_FILE, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, UuidStorage.class);
        }
    }
    
    private boolean isValidStorage(UuidStorage storage) {
        if (storage == null || storage.uuid == null || storage.uuid.isBlank()) {
            return false;
        }
        
        try {
            UUID.fromString(storage.uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private boolean tryRestoreFromBackup() {
        if (UUID_BACKUP == null || !Files.exists(UUID_BACKUP)) {
            return false;
        }
        
        try {
            Files.copy(UUID_BACKUP, UUID_FILE, StandardCopyOption.REPLACE_EXISTING);
            
            UuidStorage storage = loadFromFile();
            if (isValidStorage(storage)) {
                playerUuid = storage.uuid;
                return true;
            }
            RandomRunMod.LOGGER.warn("{} Backup содержит невалидный UUID", LOG_TAG);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка восстановления из backup", LOG_TAG, e);
        }
        
        return false;
    }
    
    private void save() {
        if (CONFIG_DIR == null || UUID_FILE == null) {
            RandomRunMod.LOGGER.error("{} Невозможно сохранить - пути не инициализированы", LOG_TAG);
            return;
        }
        
        try {
            Files.createDirectories(CONFIG_DIR);
            backupCurrentFile();
            saveToFile();
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Ошибка сохранения", LOG_TAG, e);
        }
    }
    
    private void saveToFile() throws IOException {
        UuidStorage storage = new UuidStorage();
        storage.uuid = playerUuid;
        
        try (FileChannel channel = FileChannel.open(UUID_TEMP, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.TRUNCATE_EXISTING);
             FileLock lock = channel.lock()) {
            try (Writer writer = Files.newBufferedWriter(UUID_TEMP, StandardCharsets.UTF_8)) {
                GSON.toJson(storage, writer);
            }
        }
        
        Files.move(UUID_TEMP, UUID_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private void backupCurrentFile() {
        if (UUID_FILE != null && UUID_BACKUP != null && Files.exists(UUID_FILE)) {
            try {
                Files.copy(UUID_FILE, UUID_BACKUP, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                RandomRunMod.LOGGER.warn("{} Не удалось создать backup", LOG_TAG, e);
            }
        }
    }
    
    private static class UuidStorage {
        String uuid;
    }
}

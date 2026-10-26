package com.randomrun.features.battle.screen.room.util.manager;

import com.randomrun.features.battle.screen.room.util.validation.InputValidator;
import com.randomrun.infrastructure.RandomRunMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RecentDataManager<T> {
    private static final String LOG_TAG = "[RecentData]";
    private static volatile RecentDataManager<String> ipInstance;
    private static volatile RecentDataManager<String> roomInstance;
    
    private final List<T> recentData = new ArrayList<>();
    private final int maxSize;
    private final Path storageFile;
    private final DataSerializer<T> serializer;
    
    private RecentDataManager(int maxSize, Path storageFile, DataSerializer<T> serializer) {
        this.maxSize = maxSize;
        this.storageFile = storageFile;
        this.serializer = serializer;
        load();
    }
    
    public static RecentDataManager<String> getIpInstance() {
        if (ipInstance == null) {
            synchronized (RecentDataManager.class) {
                if (ipInstance == null) {
                    ipInstance = new RecentDataManager<>(5, 
                        Paths.get("config", "randomrun", "recent_ips.txt"),
                        new StringSerializer(InputValidator::isValidIp));
                }
            }
        }
        return ipInstance;
    }
    
    public static RecentDataManager<String> getRoomInstance() {
        if (roomInstance == null) {
            synchronized (RecentDataManager.class) {
                if (roomInstance == null) {
                    roomInstance = new RecentDataManager<>(5,
                        Paths.get("config", "randomrun", "recent_rooms.txt"),
                        new StringSerializer(code -> code.length() == 5 && code.matches("\\d+")));
                }
            }
        }
        return roomInstance;
    }
    
    public void add(T data) {
        if (data == null) return;
        
        recentData.remove(data);
        recentData.add(0, data);
        
        while (recentData.size() > maxSize) {
            recentData.remove(recentData.size() - 1);
        }
        
        if (storageFile != null) save();
    }
    
    public List<T> getRecent() {
        return new ArrayList<>(recentData);
    }
    
    private void load() {
        if (storageFile == null || serializer == null) return;
        
        try {
            if (Files.exists(storageFile)) {
                List<String> lines = Files.readAllLines(storageFile);
                recentData.clear();
                for (String line : lines) {
                    T data = serializer.deserialize(line.trim());
                    if (data != null) {
                        recentData.add(data);
                    }
                }
            }
        } catch (IOException e) {
            RandomRunMod.LOGGER.warn("{} Ошибка загрузки из {}", LOG_TAG, storageFile.getFileName(), e);
        }
    }
    
    private void save() {
        if (storageFile == null || serializer == null) return;
        
        try {
            Files.createDirectories(storageFile.getParent());
            List<String> lines = new ArrayList<>();
            for (T data : recentData) {
                String serialized = serializer.serialize(data);
                if (serialized != null) {
                    lines.add(serialized);
                }
            }
            Files.write(storageFile, lines);
        } catch (IOException e) {
            RandomRunMod.LOGGER.warn("{} Ошибка сохранения в {}", LOG_TAG, storageFile.getFileName(), e);
        }
    }
    
    private interface DataSerializer<T> {
        String serialize(T data);
        T deserialize(String str);
    }
    
    private static class StringSerializer implements DataSerializer<String> {
        private final java.util.function.Predicate<String> validator;
        
        StringSerializer(java.util.function.Predicate<String> validator) {
            this.validator = validator;
        }
        
        @Override
        public String serialize(String data) {
            return data;
        }
        
        @Override
        public String deserialize(String str) {
            return (validator == null || validator.test(str)) ? str : null;
        }
    }
}

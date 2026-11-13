package com.randomrun.features.battle.screen;

import com.randomrun.infrastructure.RandomRunMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class RoomSupport {
    private RoomSupport() {
    }

    public static class CodeFormatter {
        public static String format(String input) {
            if (input == null || input.isEmpty()) return "";

            String digits = input.replaceAll("\\D", "");

            if (digits.length() > 5) {
                digits = digits.substring(0, 5);
            }

            if (digits.length() <= 1) {
                return digits;
            }

            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < digits.length(); i++) {
                if (i > 0) formatted.append("-");
                formatted.append(digits.charAt(i));
            }

            return formatted.toString();
        }

        public static String unformat(String formatted) {
            if (formatted == null) return "";
            return formatted.replaceAll("-", "");
        }
    }

    public static class SoundHelper {
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

    public static class UIConstants {
        public static int BG_NORMAL() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonBase; }
        public static int BG_HOVERED() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
        public static final int BG_SEMI_TRANSPARENT = 0x80000000;

        public static final int BORDER_WHITE = 0xFFFFFFFF;
        public static int BORDER_PURPLE() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
        public static int BORDER_PURPLE_LIGHT() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().accent; }

        public static final int TEXT_WHITE = 0xFFFFFF;
        public static final int TEXT_ERROR = 0xFF5555;
        public static final int TEXT_SUCCESS = 0x55FF55;
        public static final int TEXT_GRAY = 0xAAAAAA;

        public static final long SUCCESS_INDICATOR_DURATION = 2000;
        public static final float SOUND_VOLUME_MULTIPLIER = 0.7f;
    }

    public static class RecentDataManager<T> {
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

    public static class InputValidator {
        public static boolean isValidIp(String ip) {
            if (ip == null || ip.isEmpty()) return false;

            String[] parts = ip.split(":", 2);
            String host = parts[0].trim();
            String portStr = parts.length > 1 ? parts[1].trim() : null;

            String[] octets = host.split("\\.");
            if (octets.length != 4) return false;

            for (String octet : octets) {
                try {
                    int val = Integer.parseInt(octet);
                    if (val < 0 || val > 255) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            if (portStr != null && !portStr.isEmpty()) {
                try {
                    int port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            return true;
        }

        public static ValidationResult validatePassword(String password) {
            if (password == null || password.isEmpty()) {
                return ValidationResult.valid();
            }

            if (password.length() < 4) {
                return ValidationResult.invalid("randomrun.error.password_too_short");
            }

            if (password.length() > 32) {
                return ValidationResult.invalid("randomrun.error.password_too_long");
            }

            if (password.contains(" ")) {
                return ValidationResult.invalid("randomrun.error.password_no_spaces");
            }

            return ValidationResult.valid();
        }

        public static boolean isValidRoomCode(String code) {
            return code != null && code.length() == 5 && code.matches("\\d+");
        }

        public static class ValidationResult {
            private final boolean valid;
            private final String errorKey;

            private ValidationResult(boolean valid, String errorKey) {
                this.valid = valid;
                this.errorKey = errorKey;
            }

            public static ValidationResult valid() {
                return new ValidationResult(true, null);
            }

            public static ValidationResult invalid(String errorKey) {
                return new ValidationResult(false, errorKey);
            }

            public boolean isValid() {
                return valid;
            }

            public String getErrorKey() {
                return errorKey;
            }
        }
    }
}

package com.randomrun.main.util;

public class IdCompressor {

    /**
     * Удаляет префикс "minecraft:" из идентификатора, если он присутствует.
     * Используется для экономии места в БД и трафика.
     *
     * @param fullId Полный идентификатор (например, "minecraft:dirt")
     * @return Сжатый идентификатор (например, "dirt")
     */
    public static String compress(String fullId) {
        if (fullId == null) return null;
        if (fullId.startsWith("minecraft:")) {
            return fullId.substring(10);
        }
        return fullId;
    }

    /**
     * Восстанавливает полный идентификатор, добавляя "minecraft:", если префикс отсутствует.
     *
     * @param compressedId Сжатый идентификатор (например, "dirt")
     * @return Полный идентификатор (например, "minecraft:dirt")
     */
    public static String decompress(String compressedId) {
        if (compressedId == null) return null;
        if (!compressedId.contains(":")) {
            return "minecraft:" + compressedId;
        }
        return compressedId;
    }
}

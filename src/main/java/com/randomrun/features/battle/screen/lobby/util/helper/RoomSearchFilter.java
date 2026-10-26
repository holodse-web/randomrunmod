package com.randomrun.features.battle.screen.lobby.util.helper;

import com.randomrun.features.battle.manager.BattleRoom;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class RoomSearchFilter {
    public static List<BattleRoom> filter(List<BattleRoom> rooms, String query) {
        if (query == null || query.isEmpty()) {
            return rooms;
        }
        
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return rooms.stream()
            .filter(room -> matches(room, lowerQuery))
            .collect(Collectors.toList());
    }
    
    private static boolean matches(BattleRoom room, String query) {
        if (room.isUpgrader() && ("upgrader".contains(query) || "апгрейдер".contains(query))) return true;
        if (room.isRandomDrop() && ("random drop".contains(query) || "rd".equals(query))) return true;
        if (room.getRoomCode() != null && room.getRoomCode().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        
        if (room.getHost() != null && room.getHost().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        
        if (room.getTargetItem() != null) {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(room.getTargetItem()));
                String itemName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
                if (itemName.contains(query)) {
                    return true;
                }
            } catch (Exception e) {
            }
        }
        
        return false;
    }
}

package com.randomrun.features.battle.screen.lobby.util.helper;

import com.randomrun.features.battle.manager.BattleRoom;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RoomSorter {
    public enum SortType {
        PLAYERS_DESC("По игрокам ↓"),
        PLAYERS_ASC("По игрокам ↑"),
        MODE("По режиму"),
        STATUS("По статусу");
        
        private final String displayName;
        
        SortType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static List<BattleRoom> sort(List<BattleRoom> rooms, SortType sortType) {
        if (rooms == null || rooms.isEmpty()) {
            return rooms;
        }
        
        Comparator<BattleRoom> comparator = getComparator(sortType);
        return rooms.stream()
            .sorted(comparator)
            .collect(Collectors.toList());
    }
    
    private static Comparator<BattleRoom> getComparator(SortType sortType) {
        switch (sortType) {
            case PLAYERS_DESC:
                return Comparator.comparingInt((BattleRoom r) -> 
                    r.getPlayers() != null ? r.getPlayers().size() : 0).reversed();
                
            case PLAYERS_ASC:
                return Comparator.comparingInt(r -> 
                    r.getPlayers() != null ? r.getPlayers().size() : 0);
                
            case MODE:
                return Comparator.comparing(r -> 
                    r.getCreationMode() != null ? r.getCreationMode() : "");
                
            case STATUS:
                return Comparator.comparing(BattleRoom::getStatus);
                
            default:
                return Comparator.comparingInt((BattleRoom r) -> 
                    r.getPlayers() != null ? r.getPlayers().size() : 0).reversed();
        }
    }
}

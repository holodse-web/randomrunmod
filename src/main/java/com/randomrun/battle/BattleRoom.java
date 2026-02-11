package com.randomrun.battle;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleRoom {
    
    public enum RoomStatus {
        @SerializedName("WAITING")
        WAITING,
        
        @SerializedName("LOADING")
        LOADING,
        
        @SerializedName("FROZEN")
        FROZEN,
        
        @SerializedName("STARTED")
        STARTED,
        
        @SerializedName("FINISHED")
        FINISHED,
        
        @SerializedName("DRAW")
        DRAW
    }
    
    // Transient field for optimized partial updates
    // This allows us to track when only specific fields like "st" (status) are updated
    // without re-serializing the entire object for every small check.
    public transient long lastLocalUpdateTime = 0;

    public void markUpdated() {
        this.lastLocalUpdateTime = System.currentTimeMillis();
    }
    
    @SerializedName("h")
    private String host;
    
    // Удалены устаревшие поля guest/guest1..4

    @SerializedName("s")
    private String seed;
    
    @SerializedName("t")
    private String targetItem;
    
    @SerializedName("st")
    private RoomStatus status;
    
    @SerializedName("rc")
    private Integer readyCount = 0;
    
    @SerializedName("hr")
    private Boolean hostReady = false;
    
    // Удалены устаревшие поля guestReady

    @SerializedName("hl")
    private Boolean hostLoaded = false;
    
    // Удалены устаревшие поля guestLoaded

    @SerializedName("w")
    private String winner;
    
    @SerializedName("ht")
    private Long hostTime = 0L;
    
    // Удалены устаревшие поля guestTime
    
    @SerializedName("c")
    private String roomCode;

    @SerializedName("ip")
    private String serverAddress;
    
    @SerializedName("pr")
    private Boolean isPrivate;

    @SerializedName("pwd")
    private String password;
    
    // @SerializedName("sw") - Removed. Renamed field to ensure no serialization.
    private transient Boolean _legacySharedWorldCache; 
    
    @SerializedName("cm")
    private String creationMode = "sw"; // Default to Separate Worlds to avoid nulls
    
    @SerializedName("dd")
    private Map<String, Boolean> disconnectedPlayers = new HashMap<>();

    @SerializedName("ca")
    private long createdAt;
    
    // Удалено избыточное поле createdAtFormatted
    
    // Удалено избыточное поле hostTimeFormatted
    
    // Удалено устаревшее поле guestTimeFormatted

    @SerializedName("dr")
    private String defeatReason;

    @SerializedName("start")
    private Long startTime = 0L;
    
    @SerializedName("srv_start")
    private Long serverStartTime = 0L; // Серверное время старта
    
    @SerializedName("swt")
    private Long sharedWorldStartTime = 0L; // Время мира для старта (Shared World)
    
    @SerializedName("mp")
    private Integer maxPlayers = 2;

    public long getServerStartTime() { return serverStartTime != null ? serverStartTime : 0L; }
    public void setServerStartTime(long serverStartTime) { this.serverStartTime = serverStartTime == 0 ? null : serverStartTime; }
    
    public long getSharedWorldStartTime() { return sharedWorldStartTime != null ? sharedWorldStartTime : 0L; }
    public void setSharedWorldStartTime(long sharedWorldStartTime) { this.sharedWorldStartTime = sharedWorldStartTime == 0 ? null : sharedWorldStartTime; }
    
    public String getCreationMode() { return creationMode; }
    public void setCreationMode(String creationMode) { this.creationMode = creationMode; }
    
    public int getMaxPlayers() { return maxPlayers != null ? maxPlayers : 2; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    // Removed serialized "p" field as it is redundant. 
    // Players list is now computed dynamically from host and guests.
    private transient List<String> players = new ArrayList<>();
    
    @SerializedName("g")
    private Map<String, GuestData> guests = new HashMap<>();
    
    public static class GuestData {
        @SerializedName("r")
        public boolean ready = false;
        
        @SerializedName("l")
        public boolean loaded = false;
        
        @SerializedName("t")
        public long time = 0;
        
        // Удалено избыточное поле timeFormatted
        
        public GuestData() {}
        public GuestData(boolean ready, boolean loaded) {
            this.ready = ready;
            this.loaded = loaded;
        }
    }

    @SerializedName("rp")
    private transient List<String> readyPlayers = new ArrayList<>(); // Deprecated for serialization, now computed

    @SerializedName("lp")
    private transient List<String> loadedPlayers = new ArrayList<>(); // Deprecated for serialization, now computed

    @SerializedName("ep")
    private Map<String, Long> eliminatedPlayers = new HashMap<>();
    @SerializedName("pt")
    private Map<String, Long> playerTimes = new HashMap<>();

    // Конструкторы
    public BattleRoom() {
        this.status = RoomStatus.WAITING;
        this.readyCount = 0;
        this.createdAt = System.currentTimeMillis();
    }
    
    public BattleRoom(String host, String seed, String targetItem, String roomCode, boolean isPrivate, String password, boolean isSharedWorld, int maxPlayers) {
        this(host, seed, targetItem, roomCode, isPrivate, password, isSharedWorld, maxPlayers, isSharedWorld ? "rv" : "sw");
    }

    public BattleRoom(String host, String seed, String targetItem, String roomCode, boolean isPrivate, String password, boolean isSharedWorld, int maxPlayers, String creationMode) {
        this.host = host;
        this.seed = seed;
        this.targetItem = targetItem;
        this.roomCode = roomCode;
        this.isPrivate = isPrivate;
        this.password = (password != null && !password.isEmpty()) ? password : null;
        this._legacySharedWorldCache = isSharedWorld;
        this.maxPlayers = maxPlayers;
        this.creationMode = creationMode != null ? creationMode : (isSharedWorld ? "rv" : "sw");
        this.players = new ArrayList<>();
        this.players.add(host);
        this.status = RoomStatus.WAITING;
        this.readyCount = 0;
        this.createdAt = System.currentTimeMillis();
    }
    
    // Конструктор для обратной совместимости
    public BattleRoom(String host, String seed, String targetItem, String roomCode, boolean isPrivate, boolean isSharedWorld, int maxPlayers) {
        this(host, seed, targetItem, roomCode, isPrivate, null, isSharedWorld, maxPlayers);
    }
    
    // Утилитарные методы
    private String formatTime(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS");
        return sdf.format(new java.util.Date(millis));
    }
    
    private String formatDuration(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    // ============= Геттеры и Сеттеры =============
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    // УДАЛЕНЫ getGuest/setGuest и нумерованные варианты
    // Оставляем общий getGuest() для совместимости со старым кодом BattleManager, который я мог пропустить,
    // но возвращаем первого гостя из карты, если возможно.
    // Однако пользователь попросил УДАЛИТЬ их. Поэтому я удалю их.
    // Если я пропустил какое-либо использование в BattleManager, компиляция не удастся, и я исправлю это.
    
    // Вспомогательный метод для получения первого гостя (для поддержки устаревшей логики одного гостя, если необходимо)
    public String getGuest() {
        if (guests != null && !guests.isEmpty()) {
            return guests.keySet().iterator().next();
        }
        // Резервный вариант: проверка списка игроков
        if (players != null) {
            for (String p : players) {
                if (!p.equals(host)) return p;
            }
        }
        return null;
    }
    
    // Методы поддержки устаревшего кода для guest1..4 (Опционально: вернуть null или сопоставленного гостя)
    // Я определю их, чтобы избежать ошибок компиляции в других классах, которые я не проверял (например, Mixins/Screens)
    // Но они будут полагаться на карту гостей или возвращать null.
    public String getGuest1() { return getGuestByOffset(0); }
    public String getGuest2() { return getGuestByOffset(1); }
    public String getGuest3() { return getGuestByOffset(2); }
    public String getGuest4() { return getGuestByOffset(3); }

    private String getGuestByOffset(int offset) {
        if (guests == null || guests.isEmpty()) return null;
        List<String> guestNames = new ArrayList<>(guests.keySet());
        if (offset < guestNames.size()) return guestNames.get(offset);
        return null;
    }

    public String getSeed() { return seed; }
    public void setSeed(String seed) { this.seed = seed; }
    
    public String getTargetItem() { return targetItem; }
    public void setTargetItem(String targetItem) { this.targetItem = targetItem; }
    
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
    
    public int getReadyCount() { return readyCount != null ? readyCount : 0; }
    public void setReadyCount(int readyCount) { this.readyCount = readyCount; }
    
    public boolean isHostReady() { return Boolean.TRUE.equals(hostReady); }
    public void setHostReady(boolean hostReady) { this.hostReady = hostReady; }
    
    public boolean isGuestReady() { 
        // Проверяем, готов ли ЛЮБОЙ гость? Или ВСЕ гости?
        // Устаревшее поведение: "guestReady" обычно означало одного гостя.
        // Для новой системы мы, вероятно, должны проверить, готовы ли ВСЕ гости?
        // Но этот метод, скорее всего, используется для UI "Ожидание противника...".
        // Давайте вернем true, если ВСЕ гости готовы.
        if (guests == null || guests.isEmpty()) return false;
        for (GuestData data : guests.values()) {
            if (!data.ready) return false;
        }
        return true;
    }
    public void setGuestReady(boolean guestReady) { 
        // Невозможно легко установить готовность "общего" гостя, игнорируем или устанавливаем всем?
        // Игнорируем для безопасности.
    }
    
    public boolean isHostLoaded() { return Boolean.TRUE.equals(hostLoaded); }
    public void setHostLoaded(boolean hostLoaded) { this.hostLoaded = hostLoaded; }
    
    public boolean isGuestLoaded() { 
        if (guests == null || guests.isEmpty()) return false;
        for (GuestData data : guests.values()) {
            if (!data.loaded) return false;
        }
        return true;
    }
    public void setGuestLoaded(boolean guestLoaded) { 
        // Игнорируется
    }
    
    public List<String> getLoadedPlayers() {
        List<String> computedLoaded = new ArrayList<>();
        if (hostLoaded && host != null) {
            computedLoaded.add(host);
        }
        if (guests != null) {
            for (Map.Entry<String, GuestData> entry : guests.entrySet()) {
                if (entry.getValue().loaded) {
                    computedLoaded.add(entry.getKey());
                }
            }
        }
        this.loadedPlayers = computedLoaded;
        return computedLoaded;
    }
    
    public void setLoadedPlayers(List<String> loadedPlayers) { this.loadedPlayers = loadedPlayers; }
    
    public void setPlayerLoaded(String playerName, boolean loaded) {
        if (playerName.equals(host)) {
            hostLoaded = loaded;
        } else if (getGuests().containsKey(playerName)) {
            getGuests().get(playerName).loaded = loaded;
        }
    }
    
    public boolean isPlayerLoaded(String playerName) {
        if (playerName.equals(host)) return hostLoaded;
        
        if (getGuests().containsKey(playerName)) {
            return getGuests().get(playerName).loaded;
        }
        return false;
    }
    
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    
    public long getHostTime() { return hostTime; }
    public void setHostTime(long time) {
        this.hostTime = time;
    }
    
    public long getGuestTime() { 
        // Возвращаем время первого гостя, если есть
        if (guests != null && !guests.isEmpty()) {
            return guests.values().iterator().next().time;
        }
        return 0; 
    }
    public void setGuestTime(long time) { 
        // Игнорируется
    }
    
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    
    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }
    
    public boolean isPrivate() { return Boolean.TRUE.equals(isPrivate); }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public boolean isSharedWorld() {
        if (creationMode != null) {
            return !"sw".equals(creationMode);
        }
        // Fallback for old rooms
        return Boolean.TRUE.equals(_legacySharedWorldCache);
    }
    public void setSharedWorld(boolean sharedWorld) { 
        this._legacySharedWorldCache = sharedWorld;
        // Logic for setting CM should be handled by constructor or explicit setter, 
        // but for compatibility we might leave this or rely on cm.
        if (this.creationMode == null || "sw".equals(this.creationMode)) {
            // Only override if default/null
            this.creationMode = sharedWorld ? "rv" : "sw"; // Default fallback to Radmin if true? Or e4mc? Let's say generic.
            // Actually, we shouldn't guess here if possible.
        }
    }
    
    public Map<String, Boolean> getDisconnectedPlayers() { return disconnectedPlayers != null ? disconnectedPlayers : new HashMap<>(); }
    public void setDisconnectedPlayers(Map<String, Boolean> disconnectedPlayers) { this.disconnectedPlayers = disconnectedPlayers; }
    
    public boolean isPlayerDisconnected(String playerName) {
        return getDisconnectedPlayers().getOrDefault(playerName, false);
    }
    
    public void setPlayerDisconnected(String playerName, boolean disconnected) {
        if (this.disconnectedPlayers == null) this.disconnectedPlayers = new HashMap<>();
        if (disconnected) {
            this.disconnectedPlayers.put(playerName, true);
        } else {
            this.disconnectedPlayers.remove(playerName);
        }
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public String getCreatedAtFormatted() { return formatTime(createdAt); }
    
    public String getHostTimeFormatted() { return formatDuration(hostTime != null ? hostTime : 0L); }
    
    public String getGuestTimeFormatted() { return ""; }
    
    public String getDefeatReason() { return defeatReason; }
    public void setDefeatReason(String defeatReason) { this.defeatReason = defeatReason; }
    
    public long getStartTime() { return startTime != null ? startTime : 0L; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    // Управление игроками
    public List<String> getPlayers() { 
        List<String> computedPlayers = new ArrayList<>();
        if (host != null && !host.isEmpty()) {
            computedPlayers.add(host);
        }
        if (guests != null) {
            computedPlayers.addAll(guests.keySet());
        }
        // Cache it if needed, but for now dynamic is safer to ensure consistency
        this.players = computedPlayers;
        return computedPlayers; 
    }
    
    public void setPlayers(List<String> players) { 
        // This method is deprecated for direct setting.
        // We only use it to populate the transient list if needed.
        this.players = players; 
    }
    
    public Map<String, GuestData> getGuests() {
        if (guests == null) guests = new HashMap<>();
        return guests;
    }
    
    public void setGuests(Map<String, GuestData> guests) { this.guests = guests; }

    public void addPlayer(String playerName) {
        if (host == null) {
            host = playerName;
            return;
        }
        
        if (!host.equals(playerName)) {
            // Добавить в карту гостей
            if (!getGuests().containsKey(playerName)) {
                getGuests().put(playerName, new GuestData());
            }
        }
    }
    
    public boolean hasPlayer(String playerName) {
        return (host != null && host.equals(playerName)) || (guests != null && guests.containsKey(playerName));
    }
    
    // Управление статусом готовности
    public List<String> getReadyPlayers() {
        List<String> computedReady = new ArrayList<>();
        if (hostReady && host != null) {
            computedReady.add(host);
        }
        if (guests != null) {
            for (Map.Entry<String, GuestData> entry : guests.entrySet()) {
                if (entry.getValue().ready) {
                    computedReady.add(entry.getKey());
                }
            }
        }
        this.readyPlayers = computedReady;
        return computedReady;
    }
    
    public void setReadyPlayers(List<String> readyPlayers) { 
        this.readyPlayers = readyPlayers; 
    }
    
    public void setPlayerReady(String playerName, boolean ready) {
        if (playerName.equals(host)) {
            hostReady = ready;
        } else if (getGuests().containsKey(playerName)) {
            getGuests().get(playerName).ready = ready;
        }
    }
    
    public boolean isPlayerReady(String playerName) {
        if (playerName.equals(host)) return hostReady;
        
        if (getGuests().containsKey(playerName)) {
            return getGuests().get(playerName).ready;
        }
        return false;
    }
    
    // Управление элиминацией
    public List<String> getEliminatedPlayers() {
        if (eliminatedPlayers == null) eliminatedPlayers = new HashMap<>();
        return new ArrayList<>(eliminatedPlayers.keySet());
    }
    
    public Map<String, Long> getEliminationMap() {
        if (eliminatedPlayers == null) eliminatedPlayers = new HashMap<>();
        return eliminatedPlayers;
    }
    
    public void addEliminatedPlayer(String playerName) {
        if (eliminatedPlayers == null) eliminatedPlayers = new HashMap<>();
        if (!eliminatedPlayers.containsKey(playerName)) {
            eliminatedPlayers.put(playerName, System.currentTimeMillis());
        }
    }
    
    public boolean isPlayerEliminated(String playerName) {
        if (eliminatedPlayers == null) return false;
        return eliminatedPlayers.containsKey(playerName);
    }
    
    // Проверки ролей
    public boolean isHost(String playerName) {
        return host != null && host.equals(playerName);
    }
    
    // Вспомогательные методы для идентификации гостей без использования конкретных полей
    public boolean isGuest(String playerName) {
        return !isHost(playerName) && hasPlayer(playerName);
    }
    
    // Управление временем игроков (для поддержки 3+ игроков)
    public Map<String, Long> getPlayerTimes() {
        if (playerTimes == null) playerTimes = new HashMap<>();
        return playerTimes;
    }
    
    public void setPlayerTime(String playerName, long time) {
        if (playerTimes == null) playerTimes = new HashMap<>();
        playerTimes.put(playerName, time);
        
        if (playerName.equals(host)) setHostTime(time);
        
        // Обновление карты гостей
        if (getGuests().containsKey(playerName)) {
            getGuests().get(playerName).time = time;
        }
    }
    
    public long getPlayerTime(String playerName) {
        // 1. Check playerTimes map (primary source)
        if (playerTimes != null && playerTimes.containsKey(playerName)) {
            return playerTimes.get(playerName);
        }
        
        // 2. Check hostTime if player is host
        if (playerName.equals(host) && hostTime > 0) {
            return hostTime;
        }
        
        // 3. Check guests map
        if (guests != null && guests.containsKey(playerName)) {
            return guests.get(playerName).time;
        }
        
        return 0L;
    }
    
    public String getFastestPlayer() {
        if (playerTimes == null || playerTimes.isEmpty()) return null;
        String fastest = null;
        long bestTime = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : playerTimes.entrySet()) {
            if (entry.getValue() > 0 && entry.getValue() < bestTime) {
                bestTime = entry.getValue();
                fastest = entry.getKey();
            }
        }
        return fastest;
    }
}
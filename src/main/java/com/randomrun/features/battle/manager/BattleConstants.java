package com.randomrun.features.battle.manager;

import java.util.regex.Pattern;

public class BattleConstants {
    public static final int MAX_JOIN_ATTEMPTS = 5;
    public static final int MAX_ROOM_CODE_COLLISIONS = 3;
    public static final int MAX_CONSECUTIVE_ERRORS = 5;
    
    public static final long RETRY_BASE_DELAY_MS = 200;
    public static final long RETRY_JITTER_MS = 300;
    public static final long POLLING_INTERVAL_MS = 10000;
    public static final long POLLING_INTERVAL_FALLBACK_MS = 3000;
    public static final long WORLD_LOAD_DELAY_SEC = 2;
    public static final long GUEST_CONNECTION_DELAY_SEC = 3;
    
    public static final int E4MC_TIMEOUT_MS = 20000;
    public static final int CONNECTION_RETRY_DELAY_MS = 3000;
    public static final int MAX_CONNECTION_RETRY_ATTEMPTS = 5;
    public static final int MONITOR_INTERVAL_MS = 200;
    
    public static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?::\\d+)?$"
    );
    public static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(?::\\d+)?$"
    );
    
    public static final String API_ROOMS_PATH = "/rooms/";
    public static final String API_EVENTS_PATH = "/events";
    public static final int EVENT_MAX_RETRY_ATTEMPTS = 3;
    public static final long EVENT_RETRY_DELAY_MS = 1000;
    public static final long EVENT_TIMEOUT_MS = 5000;
    
    public static final long SYNC_DEFAULT_DELAY_MS = 3000;
    public static final long SYNC_MAX_TICK_DIFF = 600;
    public static final long SYNC_MAX_CLOCK_SKEW_MS = 6000;
    public static final long SYNC_MIN_CLOCK_SKEW_MS = -2000;
    public static final long SYNC_CALIBRATION_INTERVAL_MS = 60000;
    
    public static final long BATTLE_START_DELAY_MS = 3000;
    
    public static final String FIELD_STATUS = "st";
    public static final String FIELD_WINNER = "w";
    public static final String FIELD_SERVER_IP = "ip";
    public static final String FIELD_SERVER_START = "srv_start";
    public static final String FIELD_SHARED_WORLD_TIME = "swt";
    
    public static final String GUEST_LOADED = "l";
    public static final String GUEST_READY = "r";
    public static final String GUEST_DISCONNECTED = "dd";
    
    public static final long API_CACHE_TTL_MS = 2000;
    public static final long API_OPERATION_TIMEOUT_MS = 10000;
    public static final int API_MAX_RETRIES = 3;
    
    public static final long SSE_RECONNECT_DELAY_MS = 2000;
    public static final long SSE_RECONNECT_MAX_DELAY_MS = 30000;
    public static final int SSE_MAX_RECONNECT_ATTEMPTS = 5;
    public static final long SSE_HEALTH_CHECK_INTERVAL_MS = 15000;
    
    public static final long STATE_CONSISTENCY_DELAY_MS = 500;
    
    public static final int COUNTDOWN_START_NUMBER = 3;
    public static final long COUNTDOWN_INTERVAL_MS = 1000;
    
    private BattleConstants() {}
}

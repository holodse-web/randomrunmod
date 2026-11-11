package com.randomrun.network;

public final class ConnectionStatus {
    public enum Stage {
        IDLE, WAITING_HOST, PREPARING, SIGNALING, TUNNEL, JOINING;

        public String key() {
            return "randomrun.curtain.stage." + name().toLowerCase();
        }
    }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile long since = System.currentTimeMillis();

    private ConnectionStatus() {}

    public static void set(Stage value) {
        if (stage == value) return;
        stage = value;
        since = System.currentTimeMillis();
    }

    public static Stage get() {
        return stage;
    }

    public static long since() {
        return since;
    }
}

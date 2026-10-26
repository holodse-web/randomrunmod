package com.randomrun.features.battle.screen.lobby.util.helper;

import java.util.Timer;
import java.util.TimerTask;

public class SearchDebouncer {
    private static final long DEBOUNCE_DELAY = 300;
    
    private Timer timer;
    private final Runnable action;
    private long generation;
    
    public SearchDebouncer(Runnable action) {
        this.action = action;
    }
    
    public synchronized void trigger() {
        if (timer != null) {
            timer.cancel();
        }
        
        long requestGeneration = ++generation;
        timer = new Timer("RandomRun-LobbySearch", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (SearchDebouncer.this) {
                    if (generation == requestGeneration) action.run();
                }
            }
        }, DEBOUNCE_DELAY);
    }
    
    public synchronized void cancel() {
        generation++;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}

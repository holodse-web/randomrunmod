package com.randomrun.features.voice.engine;

import com.randomrun.features.voice.VoicePack;
import com.randomrun.features.voice.VoiceSettings.VoiceLanguage;
import com.randomrun.infrastructure.util.CompactLogger;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Microphone to text, fully offline. Runs on its own daemon thread: reads
 * 16 kHz mono PCM, measures the level for the HUD and feeds Vosk. Results are
 * handed to the listener from that thread, so listeners must hop to the
 * client thread themselves.
 */
public final class VoiceEngine {
    public enum State { OFF, LOADING, LISTENING, ERROR }

    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
    }

    private static final float SAMPLE_RATE = 16000f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final int CHUNK_BYTES = 1600; // 50 ms
    private static final VoiceEngine INSTANCE = new VoiceEngine();

    private volatile State state = State.OFF;
    private volatile String error = "";
    private volatile float level;
    private volatile float peak;
    private volatile String partial = "";
    private volatile long lastVoiceAt;
    private volatile Thread worker;
    private volatile boolean running;
    private volatile Listener listener;
    private volatile String sessionKey = "";

    private VoiceEngine() {}

    public static VoiceEngine get() {
        return INSTANCE;
    }

    public State state() { return state; }
    public String error() { return error; }
    /** Smoothed input level 0..1 for the meter. */
    public float level() { return level; }
    public float peak() { return peak; }
    public String partial() { return partial; }
    public boolean isHearingVoice() { return System.currentTimeMillis() - lastVoiceAt < 350; }
    public boolean isRunning() { return running; }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Starts (or keeps) listening with the given setup. Calling again with the
     * same setup is a no-op, so callers can invoke this every tick.
     */
    public synchronized void start(VoiceLanguage language, String microphone, String grammarJson) {
        String key = language.id + "|" + microphone + "|" + (grammarJson == null ? 0 : grammarJson.hashCode());
        if (running && key.equals(sessionKey)) return;
        stop();
        sessionKey = key;
        running = true;
        state = State.LOADING;
        error = "";
        Thread thread = new Thread(() -> run(language, microphone, grammarJson), "RandomRun-Voice");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        worker = thread;
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        Thread thread = worker;
        worker = null;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        sessionKey = "";
        level = 0;
        peak = 0;
        partial = "";
        if (state != State.ERROR) state = State.OFF;
    }

    private void run(VoiceLanguage language, String microphone, String grammarJson) {
        TargetDataLine line = null;
        VoiceRecognizer recognizer = null;
        try {
            if (!VoicePack.isInstalled(language)) throw new IllegalStateException("not_installed");
            recognizer = VoiceRecognizer.open(VoicePack.nativeDir(), VoicePack.modelDir(language), SAMPLE_RATE, grammarJson);
            line = openLine(microphone);
            line.open(FORMAT, CHUNK_BYTES * 4);
            line.start();
            state = State.LISTENING;
            byte[] buffer = new byte[CHUNK_BYTES];
            String lastPartial = "";
            while (running && !Thread.currentThread().isInterrupted()) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                measure(buffer, read);
                VoiceRecognizer.Result result = recognizer.accept(buffer, read);
                Listener target = listener;
                if (result.isFinal()) {
                    partial = "";
                    lastPartial = "";
                    if (!result.text().isEmpty() && target != null) target.onFinal(result.text());
                } else if (!result.text().equals(lastPartial)) {
                    lastPartial = result.text();
                    partial = lastPartial;
                    if (target != null) target.onPartial(lastPartial);
                }
            }
            state = State.OFF;
        } catch (Throwable t) {
            CompactLogger.warn("[Voice] Engine stopped: " + t);
            error = describe(t);
            state = State.ERROR;
            running = false;
        } finally {
            if (line != null) {
                try { line.stop(); line.close(); } catch (Throwable ignored) {}
            }
            if (recognizer != null) {
                try { recognizer.close(); } catch (Throwable ignored) {}
            }
            level = 0;
        }
    }

    private void measure(byte[] buffer, int length) {
        long sum = 0;
        int samples = length / 2;
        int max = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sum += (long) sample * sample;
            max = Math.max(max, Math.abs(sample));
        }
        double rms = samples == 0 ? 0 : Math.sqrt(sum / (double) samples) / 32768.0;
        // Map roughly -50 dB .. -6 dB onto 0..1 so normal speech fills the bar.
        double db = 20 * Math.log10(Math.max(rms, 1e-5));
        float target = (float) Math.max(0, Math.min(1, (db + 50) / 44));
        level = target > level ? target : level * 0.82f + target * 0.18f;
        peak = Math.max(target, peak * 0.96f);
        if (max > 2200) lastVoiceAt = System.currentTimeMillis();
    }

    private static TargetDataLine openLine(String microphone) throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (microphone != null && !microphone.isBlank()) {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (!mixerInfo.getName().equals(microphone)) continue;
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) return (TargetDataLine) mixer.getLine(info);
            }
        }
        if (!AudioSystem.isLineSupported(info)) throw new IllegalStateException("no_microphone");
        return (TargetDataLine) AudioSystem.getLine(info);
    }

    /** Capture devices that can deliver our format. Empty name list means "system default only". */
    public static List<String> microphones() {
        List<String> names = new ArrayList<>();
        try {
            Line.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                try {
                    if (AudioSystem.getMixer(mixerInfo).isLineSupported(info) && !names.contains(mixerInfo.getName())) {
                        names.add(mixerInfo.getName());
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private static String describe(Throwable t) {
        String message = String.valueOf(t.getMessage());
        if (message.contains("no_microphone") || t instanceof javax.sound.sampled.LineUnavailableException) return "mic";
        if (message.contains("not_installed")) return "not_installed";
        if (message.contains("unsupported_platform")) return "platform";
        if (message.contains("model_load_failed") || message.contains("recognizer_failed")) return "model";
        if (message.contains("native")) return "native";
        return "unknown";
    }
}

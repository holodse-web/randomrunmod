package com.randomrun.features.voice.engine;

import com.randomrun.features.voice.VoicePack;
import com.randomrun.features.voice.VoiceSettings.VoiceLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;







public final class VoiceEngine {
    public enum State { OFF, LOADING, LISTENING, ERROR }

    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
    }

    private static final float SAMPLE_RATE = 16000f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final int CHUNK_BYTES = 1600;
    private static final VoiceEngine INSTANCE = new VoiceEngine();

    private volatile State state = State.OFF;
    private volatile String error = "";
    private volatile float level;
    private volatile float peak;
    private volatile String partial = "";
    private volatile long lastVoiceAt;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RandomRun-Voice");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        return thread;
    });
    private volatile Capture current;
    private Listener listener;

    private record Settings(VoiceLanguage language, String microphone, String grammar) {}

    private static final class Capture {
        private final Settings settings;
        private TargetDataLine line;
        private CompletableFuture<Void> closing;

        private Capture(Settings settings) {
            this.settings = settings;
        }
    }

    private VoiceEngine() {}

    public static VoiceEngine get() {
        return INSTANCE;
    }

    public State state() { return state; }
    public String error() { return error; }

    public float level() { return level; }
    public float peak() { return peak; }
    public String partial() { return partial; }
    public boolean isHearingVoice() { return System.currentTimeMillis() - lastVoiceAt < 350; }
    public boolean isRunning() { return current != null; }

    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }





    public synchronized void start(VoiceLanguage language, String microphone, String grammarJson) {
        Settings settings = new Settings(language, microphone, grammarJson);
        if (current != null && current.settings.equals(settings)) return;
        stop();
        Capture capture = new Capture(settings);
        current = capture;
        state = State.LOADING;
        error = "";
        executor.execute(() -> run(capture));
    }

    public synchronized void stop(Listener owner) {
        if (listener != owner) return;
        listener = null;
        stop();
    }

    public synchronized void stop() {
        Capture capture = current;
        current = null;
        if (capture != null && capture.line != null) {
            TargetDataLine line = capture.line;
            capture.line = null;
            capture.closing = CompletableFuture.runAsync(() -> closeLine(line),
                task -> Thread.ofVirtual().name("RandomRun-Voice-Close").start(task));
        }
        level = 0;
        peak = 0;
        partial = "";
        lastVoiceAt = 0;
        if (state != State.ERROR) state = State.OFF;
    }

    private void run(Capture capture) {
        TargetDataLine line = null;
        VoiceRecognizer recognizer = null;
        boolean published = false;
        try {
            if (current != capture) return;
            Settings settings = capture.settings;
            if (!VoicePack.isInstalled(settings.language())) throw new IllegalStateException("not_installed");
            recognizer = VoiceRecognizer.open(VoicePack.nativeDir(), VoicePack.modelDir(settings.language()), SAMPLE_RATE, settings.grammar());
            if (current != capture) return;
            line = openLine(settings.microphone());
            line.open(FORMAT, CHUNK_BYTES * 4);
            synchronized (this) {
                if (current != capture) return;
                capture.line = line;
                published = true;
            }
            line.start();
            synchronized (this) {
                if (current != capture) return;
                state = State.LISTENING;
            }
            byte[] buffer = new byte[CHUNK_BYTES];
            String lastPartial = "";
            while (current == capture) {
                int read = line.read(buffer, 0, buffer.length);
                if (current != capture) break;
                if (read <= 0) continue;
                VoiceRecognizer.Result result = recognizer.accept(buffer, read);
                synchronized (this) {
                    if (current != capture) break;
                    measure(buffer, read);
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
            }
        } catch (Throwable failure) {
            synchronized (this) {
                if (current == capture) {
                    error = describe(failure);
                    state = State.ERROR;
                }
            }
        } finally {
            CompletableFuture<Void> closing;
            synchronized (this) {
                closing = capture.closing;
                if (published && capture.line == null) line = null;
                capture.line = null;
                if (current == capture) {
                    current = null;
                    level = 0;
                    if (state != State.ERROR) state = State.OFF;
                }
            }
            if (line != null) closeLine(line);
            if (closing != null) closing.join();
            if (recognizer != null) {
                try { recognizer.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void closeLine(TargetDataLine line) {
        try { line.close(); } catch (Throwable ignored) {}
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


    private static volatile List<String> knownMicrophones = List.of();

    public static List<String> knownMicrophones() { return knownMicrophones; }

    public static CompletableFuture<List<String>> refreshMicrophones() {
        return CompletableFuture.supplyAsync(() -> knownMicrophones = List.copyOf(microphones()));
    }

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

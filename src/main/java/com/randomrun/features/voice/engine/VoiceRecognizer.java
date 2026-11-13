package com.randomrun.features.voice.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** One Vosk model plus one recognizer. Not thread-safe: owned by the engine thread. */
final class VoiceRecognizer implements AutoCloseable {
    record Result(String text, boolean isFinal) {}

    private Pointer model;
    private Pointer recognizer;

    private VoiceRecognizer(Pointer model, Pointer recognizer) {
        this.model = model;
        this.recognizer = recognizer;
    }

    static VoiceRecognizer open(Path nativeDir, Path modelDir, float sampleRate, String grammarJson) throws IOException {
        VoskNative.load(nativeDir);
        Pointer model = VoskNative.vosk_model_new(VoskNative.cString(nativePath(modelDir)));
        if (model == null) throw new IOException("model_load_failed");
        Pointer recognizer = grammarJson == null
            ? VoskNative.vosk_recognizer_new(model, sampleRate)
            : VoskNative.vosk_recognizer_new_grm(model, sampleRate, VoskNative.cString(grammarJson));
        if (recognizer == null && grammarJson != null) {
            // Some models reject runtime grammars; fall back to free recognition.
            recognizer = VoskNative.vosk_recognizer_new(model, sampleRate);
        }
        if (recognizer == null) {
            VoskNative.vosk_model_free(model);
            throw new IOException("recognizer_failed");
        }
        return new VoiceRecognizer(model, recognizer);
    }

    Result accept(byte[] data, int length) {
        int done = VoskNative.vosk_recognizer_accept_waveform(recognizer, data, length);
        if (done > 0) return new Result(field(VoskNative.read(VoskNative.vosk_recognizer_result(recognizer)), "text"), true);
        return new Result(field(VoskNative.read(VoskNative.vosk_recognizer_partial_result(recognizer)), "partial"), false);
    }

    void reset() {
        VoskNative.vosk_recognizer_reset(recognizer);
    }

    @Override
    public void close() {
        if (recognizer != null) VoskNative.vosk_recognizer_free(recognizer);
        if (model != null) VoskNative.vosk_model_free(model);
        recognizer = null;
        model = null;
    }

    private static String field(String json, String key) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return object.has(key) ? object.get(key).getAsString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Vosk opens files with narrow-char APIs. On Windows a profile path with
     * Cyrillic letters would break that, so hand it the 8.3 short path instead.
     */
    private static String nativePath(Path dir) {
        String full = dir.toAbsolutePath().toString();
        boolean ascii = full.chars().allMatch(c -> c < 128);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (ascii || !os.contains("win")) return full;
        try {
            char[] buffer = new char[1024];
            int length = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetShortPathName(full, buffer, buffer.length);
            if (length > 0 && length < buffer.length) return new String(buffer, 0, length);
        } catch (Throwable ignored) {
        }
        return full;
    }
}

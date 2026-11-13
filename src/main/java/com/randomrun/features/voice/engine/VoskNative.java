package com.randomrun.features.voice.engine;

import com.randomrun.features.voice.VoicePack;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal JNA direct mapping of the Vosk C API. Strings go in as UTF-8 byte
 * arrays and come back as raw pointers so the platform default charset never
 * gets a say (Windows would otherwise use cp1251 for Cyrillic grammars).
 * Method names must match the C symbols.
 */
final class VoskNative {
    private static volatile boolean loaded;
    private static volatile Throwable loadError;

    private VoskNative() {}

    static native void vosk_set_log_level(int level);
    static native Pointer vosk_model_new(byte[] path);
    static native void vosk_model_free(Pointer model);
    static native Pointer vosk_recognizer_new(Pointer model, float sampleRate);
    static native Pointer vosk_recognizer_new_grm(Pointer model, float sampleRate, byte[] grammar);
    static native int vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int length);
    static native Pointer vosk_recognizer_result(Pointer recognizer);
    static native Pointer vosk_recognizer_partial_result(Pointer recognizer);
    static native Pointer vosk_recognizer_final_result(Pointer recognizer);
    static native void vosk_recognizer_reset(Pointer recognizer);
    static native void vosk_recognizer_free(Pointer recognizer);

    static synchronized void load(Path dir) throws IOException {
        if (loaded) return;
        if (loadError != null) throw new IOException("native load failed earlier", loadError);
        VoicePack.Platform platform = VoicePack.Platform.current();
        if (platform == null) throw new IOException("unsupported_platform");
        try {
            Path main = null;
            for (String file : platformFiles(platform)) {
                Path path = dir.resolve(file).toAbsolutePath();
                if (!Files.exists(path)) throw new IOException("missing " + file);
                if (file.startsWith("libvosk")) {
                    main = path;
                } else {
                    // Preload MinGW runtime so libvosk.dll resolves it from the process.
                    System.load(path.toString());
                }
            }
            NativeLibrary library = NativeLibrary.getInstance(main.toString());
            Native.register(VoskNative.class, library);
            vosk_set_log_level(-1);
            loaded = true;
        } catch (Throwable t) {
            loadError = t;
            throw new IOException("native load failed: " + t.getMessage(), t);
        }
    }

    private static java.util.List<String> platformFiles(VoicePack.Platform platform) {
        return switch (platform) {
            case WINDOWS -> java.util.List.of("libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll", "libvosk.dll");
            case LINUX -> java.util.List.of("libvosk.so");
            case MAC -> java.util.List.of("libvosk.dylib");
        };
    }

    static byte[] cString(String value) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[raw.length + 1];
        System.arraycopy(raw, 0, out, 0, raw.length);
        return out;
    }

    static String read(Pointer pointer) {
        return pointer == null ? "" : pointer.getString(0, StandardCharsets.UTF_8.name());
    }
}

package com.randomrun.features.voice;

import com.randomrun.features.voice.VoiceSettings.VoiceLanguage;
import com.randomrun.infrastructure.util.CompactLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Downloads the speech engine on demand, so players who never use voice runs
 * do not carry it. Two parts: the native Vosk library (taken from the official
 * Maven Central artifact) and one small model per language. Both are pinned by
 * SHA-256; nothing is executed until the hash matches.
 */
public final class VoicePack {
    private static final String NATIVE_URL = "https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar";
    private static final long NATIVE_SIZE = 25861916L;
    private static final String NATIVE_SHA256 = "9e38e96e4448a41d889bb254b2f8424945554945c6e77e1cd97a3d8633fac2ba";
    private static final String MARKER = ".installed";

    public enum Phase { ENGINE, MODEL, EXTRACT, DONE }

    public interface Progress {
        void update(Phase phase, long done, long total);
    }

    public enum Platform {
        WINDOWS("win32-x86-64", List.of("libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll", "libvosk.dll")),
        LINUX("linux-x86-64", List.of("libvosk.so")),
        MAC("darwin", List.of("libvosk.dylib"));

        final String jarFolder;
        /** Load order matters on Windows: dependencies first, libvosk last. */
        final List<String> files;

        Platform(String jarFolder, List<String> files) {
            this.jarFolder = jarFolder;
            this.files = files;
        }

        public static Platform current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
            if (os.contains("win")) return x64 ? WINDOWS : null;
            if (os.contains("mac") || os.contains("darwin")) return MAC; // universal x86_64 + arm64
            if (os.contains("linux")) return x64 ? LINUX : null;
            return null;
        }
    }

    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile boolean cancelled;

    private VoicePack() {}

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("randomrun").resolve("voice");
    }

    public static Path nativeDir() {
        Platform p = Platform.current();
        return root().resolve("native").resolve(p == null ? "unsupported" : p.jarFolder);
    }

    public static Path modelDir(VoiceLanguage lang) {
        return root().resolve("models").resolve(lang.modelName);
    }

    public static boolean isPlatformSupported() {
        return Platform.current() != null;
    }

    public static boolean isEngineInstalled() {
        return isPlatformSupported() && Files.exists(nativeDir().resolve(MARKER));
    }

    public static boolean isModelInstalled(VoiceLanguage lang) {
        return Files.exists(modelDir(lang).resolve(MARKER));
    }

    public static boolean isInstalled(VoiceLanguage lang) {
        return isEngineInstalled() && isModelInstalled(lang);
    }

    /** Bytes still to download for this language (0 when everything is present). */
    public static long pendingBytes(VoiceLanguage lang) {
        return (isEngineInstalled() ? 0 : NATIVE_SIZE) + (isModelInstalled(lang) ? 0 : lang.zipSize);
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    public static void cancel() {
        cancelled = true;
    }

    public static CompletableFuture<Void> install(VoiceLanguage lang, Progress progress) {
        if (!isPlatformSupported()) {
            return CompletableFuture.failedFuture(new IOException("unsupported_platform"));
        }
        if (!BUSY.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IOException("busy"));
        }
        cancelled = false;
        return CompletableFuture.runAsync(() -> {
            try {
                if (!isEngineInstalled()) installEngine(progress);
                if (!isModelInstalled(lang)) installModel(lang, progress);
                progress.update(Phase.DONE, 1, 1);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            } finally {
                BUSY.set(false);
            }
        }, task -> {
            Thread thread = new Thread(task, "RandomRun-VoiceDownload");
            thread.setDaemon(true);
            thread.start();
        });
    }

    private static void installEngine(Progress progress) throws Exception {
        Platform platform = Platform.current();
        Path dir = nativeDir();
        Path download = root().resolve("vosk-native.download");
        fetch(NATIVE_URL, NATIVE_SIZE, NATIVE_SHA256, download, Phase.ENGINE, progress);
        progress.update(Phase.EXTRACT, 0, 1);
        deleteTree(dir);
        Files.createDirectories(dir);
        try (ZipFile zip = new ZipFile(download.toFile())) {
            for (String name : platform.files) {
                ZipEntry entry = zip.getEntry(platform.jarFolder + "/" + name);
                if (entry == null) throw new IOException("missing " + name);
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            Files.deleteIfExists(download);
        }
        Files.writeString(dir.resolve(MARKER), "vosk 0.3.45");
        CompactLogger.core("[Voice] Engine installed for " + platform);
    }

    private static void installModel(VoiceLanguage lang, Progress progress) throws Exception {
        Path dir = modelDir(lang);
        Path download = root().resolve(lang.modelName + ".download");
        fetch(lang.url(), lang.zipSize, lang.sha256, download, Phase.MODEL, progress);
        progress.update(Phase.EXTRACT, 0, 1);
        deleteTree(dir);
        Files.createDirectories(dir);
        Path base = dir.toAbsolutePath().normalize();
        String prefix = lang.modelName + "/";
        try (ZipFile zip = new ZipFile(download.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(prefix)) name = name.substring(prefix.length());
                if (name.isEmpty()) continue;
                Path target = base.resolve(name).normalize();
                if (!target.startsWith(base)) throw new IOException("bad entry " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(download);
        }
        Files.writeString(dir.resolve(MARKER), lang.modelName);
        CompactLogger.core("[Voice] Model installed: " + lang.modelName);
    }

    private static void fetch(String url, long size, String sha256, Path target, Phase phase, Progress progress) throws Exception {
        Files.createDirectories(target.getParent());
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "RandomRun-Mod")
            .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long done = 0;
        long lastReport = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled) throw new IOException("cancelled");
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                if (done - lastReport > 256 * 1024) {
                    progress.update(phase, done, size);
                    lastReport = done;
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(target);
            throw e;
        }
        progress.update(phase, done, size);
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(sha256)) {
            Files.deleteIfExists(target);
            throw new IOException("checksum");
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}

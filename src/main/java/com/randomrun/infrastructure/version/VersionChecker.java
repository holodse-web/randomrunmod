package com.randomrun.infrastructure.version;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.network.HttpClient;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class VersionChecker {
    public interface UpdateListener {
        void onUpdateAvailable(String newVersion);
    }

    private static final long CHECK_COOLDOWN_MS = 3600000;
    public static final long CHECK_TIMEOUT_MILLIS = 10_000;

    private static final Executor WORKER = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService DEADLINES = createDeadlines();

    private final Object lock = new Object();
    private final List<UpdateListener> listeners = new CopyOnWriteArrayList<>();
    private final String currentVersion;
    private final Supplier<CompletableFuture<JsonElement>> fetch;
    private final Executor worker;
    private final ScheduledExecutorService deadlines;
    private final LongSupplier clock;
    private final long timeoutMillis;

    private volatile String latestVersion = null;
    private volatile String releaseNotes = null;
    private volatile boolean updateRequired = false;
    private volatile boolean criticalUpdate = false;
    private volatile boolean checkCompleted = false;
    private volatile long lastCheckTime = 0;
    private Attempt inFlight;

    private VersionChecker() {
        this(RandomRunMod.MOD_VERSION, () -> HttpClient.getInstance().getNoCache("/version"),
            WORKER, DEADLINES, System::currentTimeMillis, CHECK_TIMEOUT_MILLIS);
    }

    VersionChecker(String currentVersion, Supplier<CompletableFuture<JsonElement>> fetch,
                   Executor worker, ScheduledExecutorService deadlines, LongSupplier clock, long timeoutMillis) {
        this.currentVersion = validVersion(currentVersion, "current");
        this.fetch = Objects.requireNonNull(fetch);
        this.worker = Objects.requireNonNull(worker);
        this.deadlines = Objects.requireNonNull(deadlines);
        this.clock = Objects.requireNonNull(clock);
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Invalid check timeout");
        this.timeoutMillis = timeoutMillis;
    }

    private static ScheduledExecutorService createDeadlines() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "RandomRun-VersionDeadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static final class Attempt {
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        CompletableFuture<JsonElement> source;
        ScheduledFuture<?> deadline;
        boolean finished;
    }

    private record Metadata(String display, String notes, boolean critical, boolean required) {}

    private static class Holder {
        private static final VersionChecker INSTANCE = new VersionChecker();
    }

    public static VersionChecker getInstance() {
        return Holder.INSTANCE;
    }

    public CompletableFuture<Boolean> checkVersion() {
        return checkVersion(false);
    }

    public CompletableFuture<Boolean> forceCheck() {
        return checkVersion(true);
    }

    private CompletableFuture<Boolean> checkVersion(boolean force) {
        final Attempt attempt;
        synchronized (lock) {
            if (inFlight != null && !inFlight.finished && !inFlight.result.isDone()) {
                return inFlight.result;
            }
            long age = clock.getAsLong() - lastCheckTime;
            if (!force && checkCompleted && age >= 0 && age < CHECK_COOLDOWN_MS) {
                return CompletableFuture.completedFuture(updateRequired);
            }
            checkCompleted = false;
            attempt = new Attempt();
            inFlight = attempt;
        }
        try {
            ScheduledFuture<?> deadline = deadlines.schedule(() -> {
                Thread.startVirtualThread(() -> failCheck(attempt, new TimeoutException("Version check timed out")));
            }, timeoutMillis, TimeUnit.MILLISECONDS);
            synchronized (lock) {
                attempt.deadline = deadline;
                if (attempt.finished) deadline.cancel(false);
            }
            worker.execute(() -> beginCheck(attempt));
        } catch (Throwable error) {
            failCheck(attempt, error);
        }
        return attempt.result;
    }

    private void beginCheck(Attempt attempt) {
        synchronized (lock) {
            if (attempt != inFlight || attempt.finished || attempt.result.isDone()) return;
        }
        try {
            CompletableFuture<JsonElement> source = Objects.requireNonNull(fetch.get(), "Missing version request");
            synchronized (lock) {
                if (attempt != inFlight || attempt.finished || attempt.result.isDone()) {
                    source.cancel(true);
                    return;
                }
                attempt.source = source;
            }
            source.whenComplete((data, error) -> {
                if (error != null) {
                    failCheck(attempt, error);
                    return;
                }
                try {
                    completeCheck(attempt, parseVersion(data));
                } catch (Throwable failure) {
                    failCheck(attempt, failure);
                }
            });
        } catch (Throwable error) {
            failCheck(attempt, error);
        }
    }

    private Metadata parseVersion(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalStateException("Version metadata is unavailable");
        }
        JsonObject data = element.getAsJsonObject();
        String latest = readVersion(data, "latest");
        String display = data.has("displayVersion") ? readVersion(data, "displayVersion") : latest;
        if (compareVersions(latest, display) != 0) {
            throw new IllegalStateException("Version display does not match latest");
        }
        JsonElement notesValue = data.get("releaseNotes");
        if (notesValue != null && !notesValue.isJsonNull()
            && (!notesValue.isJsonPrimitive() || !notesValue.getAsJsonPrimitive().isString())) {
            throw new IllegalStateException("Invalid release notes");
        }
        JsonElement criticalValue = data.get("critical");
        if (criticalValue != null && !criticalValue.isJsonNull()
            && (!criticalValue.isJsonPrimitive() || !criticalValue.getAsJsonPrimitive().isBoolean())) {
            throw new IllegalStateException("Invalid critical update flag");
        }
        String notes = notesValue == null || notesValue.isJsonNull() ? null : notesValue.getAsString();
        boolean critical = criticalValue != null && !criticalValue.isJsonNull() && criticalValue.getAsBoolean();
        return new Metadata(display, notes, critical, compareVersions(currentVersion, latest) < 0);
    }

    private void completeCheck(Attempt attempt, Metadata metadata) {
        boolean changed;
        synchronized (lock) {
            if (attempt != inFlight || attempt.finished || attempt.result.isDone()) return;
            attempt.finished = true;
            changed = !metadata.display.equals(latestVersion) || updateRequired != metadata.required;
            latestVersion = metadata.display;
            releaseNotes = metadata.notes;
            criticalUpdate = metadata.critical;
            updateRequired = metadata.required;
            lastCheckTime = clock.getAsLong();
            checkCompleted = true;
            cancelDeadline(attempt);
        }
        attempt.result.complete(metadata.required);
        if (changed) {
            dispatchObserver(() -> {
                if (metadata.required) {
                    CompactLogger.warn("Update required: {} -> {}", currentVersion, metadata.display);
                    notifyListeners(metadata.display);
                }
            });
        }
    }

    private static String readVersion(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalStateException("Missing version field: " + key);
        }
        return validVersion(value.getAsString(), key);
    }

    private static String validVersion(String raw, String key) {
        if (raw == null || raw.length() > 128) throw new IllegalStateException("Invalid version field: " + key);
        String version = normalizeVersion(raw);
        if (!version.matches("[0-9]+(?:[.\\-][0-9]+)*")) {
            throw new IllegalStateException("Invalid version field: " + key);
        }
        return version;
    }

    private void failCheck(Attempt attempt, Throwable error) {
        CompletableFuture<JsonElement> source;
        synchronized (lock) {
            if (attempt != inFlight || attempt.finished) return;
            attempt.finished = true;
            checkCompleted = false;
            cancelDeadline(attempt);
            source = attempt.source;
        }
        attempt.result.completeExceptionally(error);
        if (source != null) source.cancel(true);
        dispatchObserver(() -> CompactLogger.warn("Version check failed ({})", error.getClass().getSimpleName()));
    }

    private static void cancelDeadline(Attempt attempt) {
        if (attempt.deadline != null) attempt.deadline.cancel(false);
    }

    private void dispatchObserver(Runnable observer) {
        try {
            worker.execute(() -> {
                try {
                    observer.run();
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        return version.replaceFirst("(?i)[\\s._-]*(BETA|ALPHA|RC|SNAPSHOT).*$", "")
            .trim().replaceFirst("(?i)^v\\s*", "");
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("[.\\-]");
        String[] parts2 = v2.split("[.\\-]");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            BigInteger num1 = i < parts1.length ? new BigInteger(parts1[i]) : BigInteger.ZERO;
            BigInteger num2 = i < parts2.length ? new BigInteger(parts2[i]) : BigInteger.ZERO;
            int comparison = num1.compareTo(num2);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    public void addListener(UpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(UpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String newVersion) {
        for (UpdateListener listener : listeners) {
            try {
                listener.onUpdateAvailable(newVersion);
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isUpdateRequired() {
        return updateRequired;
    }

    public boolean isCriticalUpdate() {
        return criticalUpdate;
    }

    public boolean isCheckCompleted() {
        return checkCompleted;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion != null ? latestVersion : currentVersion;
    }

    public String getReleaseNotes() {
        return releaseNotes != null ? releaseNotes : "";
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }
}

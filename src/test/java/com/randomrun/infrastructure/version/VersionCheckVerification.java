package com.randomrun.infrastructure.version;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class VersionCheckVerification {
    private static int checks;

    public static void main(String[] args) {
        System.out.println("Version check PASS " + verify() + " checks");
    }

    public static int verify() {
        checks = 0;
        checkVersion("26.8-4", "26.9-0", "26.9", true);
        checkVersion("26.9", "26.9-0", "26.9", false);
        checkVersion("27.0", "26.9-0", "26.9", false);
        checkVersion("26.10", "26.9", "26.9", false);
        checkVersion("26.9-1", "26.9-2", "26.9-2", true);
        checkVersion("v27.0 BETA", "27.0.0", "27.0", false);
        checkVersion("27.0", "27.00000000000000000000000000000000000001", null, true);
        checkVersion("27.0", "27.0-0-0", null, false);

        for (String invalid : List.of("null", "[]", "true", "{}", "{\"latest\":null}",
            "{\"latest\":true}", "{\"latest\":\"\"}", "{\"latest\":\"27..0\"}",
            "{\"latest\":\"27.-1\"}", "{\"latest\":\"27.0x\"}",
            "{\"latest\":\"27.0\",\"displayVersion\":\"28.0\"}",
            "{\"latest\":\"27.0\",\"displayVersion\":null}",
            "{\"latest\":\"27.0\",\"critical\":\"false\"}",
            "{\"latest\":\"27.0\",\"critical\":1}",
            "{\"latest\":\"27.0\",\"releaseNotes\":[]}",
            "{\"latest\":\"27.0\",\"releaseNotes\":12}",
            "{\"latest\":\"" + "1".repeat(129) + "\"}")) {
            try (Fixture fixture = new Fixture("27.0", () -> completed(invalid))) {
                CompletableFuture<Boolean> result = fixture.checker.forceCheck();
                fixture.worker.runLast();
                failed(result, "invalid metadata fails closed: " + invalid);
                check(!fixture.checker.isCheckCompleted(), "invalid metadata never validates the gate");
                check(fixture.checker.getLastCheckTime() == 0, "invalid metadata is never cached");
                check(fixture.checker.getLatestVersion().equals("27.0"), "invalid metadata never replaces the version");
            }
        }

        try (Fixture fixture = new Fixture("27.0", () -> completed("{\"latest\":27.0}"))) {
            CompletableFuture<Boolean> result = fixture.checker.forceCheck();
            fixture.worker.runLast();
            check(Boolean.FALSE.equals(result.join()), "legacy numeric JSON version remains supported");
        }

        deduplicationAndCache();
        deadlineAndRetry();
        supplierFailures();
        isolatedObservers();
        isolatedDeadlineConsumers();
        return checks;
    }

    private static void checkVersion(String current, String latest, String display, boolean expectedUpdate) {
        String json = "{\"latest\":\"" + latest + "\""
            + (display == null ? "" : ",\"displayVersion\":\"" + display + "\"")
            + ",\"critical\":true,\"releaseNotes\":\"Notes\"}";
        try (Fixture fixture = new Fixture(current, () -> completed(json))) {
            CompletableFuture<Boolean> result = fixture.checker.forceCheck();
            fixture.worker.runLast();
            check(result.join() == expectedUpdate, "version order: " + current + " vs " + latest);
            check(fixture.checker.isCheckCompleted(), "successful metadata validates the gate");
            check(fixture.checker.isUpdateRequired() == expectedUpdate, "update flag matches the terminal result");
            check(fixture.checker.isCriticalUpdate(), "critical flag applied atomically");
            check(fixture.checker.getReleaseNotes().equals("Notes"), "notes applied atomically");
            check(fixture.checker.getLatestVersion().equals(display == null ? latest : display), "display version is preserved");
        }
    }

    private static void deduplicationAndCache() {
        AtomicInteger requests = new AtomicInteger();
        try (Fixture fixture = new Fixture("27.0", () -> {
            requests.incrementAndGet();
            return completed("{\"latest\":\"27.0\"}");
        })) {
            CompletableFuture<Boolean> first = fixture.checker.checkVersion();
            check(fixture.checker.forceCheck() == first, "concurrent forced request shares the current attempt");
            check(fixture.worker.tasks.size() == 1, "deduplication does not enqueue another transport");
            fixture.worker.runLast();
            check(!first.join(), "current release is accepted");
            check(!fixture.checker.checkVersion().join(), "successful check can be cached");
            check(requests.get() == 1, "cache does not contact transport");
            fixture.worker.tasks.clear();
            CompletableFuture<Boolean> forced = fixture.checker.forceCheck();
            check(!forced.isDone(), "force check bypasses a successful cache");
            check(!fixture.checker.isCheckCompleted(), "fresh check closes the prior validation state");
            fixture.worker.runLast();
            check(!forced.join() && requests.get() == 2, "forced check fetches metadata again");
            fixture.worker.tasks.clear();
            fixture.clock.set(1);
            CompletableFuture<Boolean> rollback = fixture.checker.checkVersion();
            check(!rollback.isDone(), "wall-clock rollback does not extend a cached check indefinitely");
            fixture.worker.runLast();
            check(!rollback.join() && requests.get() == 3, "clock rollback performs a fresh check");
        }
    }

    private static void deadlineAndRetry() {
        AtomicInteger requests = new AtomicInteger();
        try (Fixture fixture = new Fixture("27.0", () -> {
            requests.incrementAndGet();
            return new CompletableFuture<>();
        })) {
            CompletableFuture<Boolean> blockedDispatch = fixture.checker.forceCheck();
            check(fixture.timer.tasks.size() == 1, "deadline exists before the transport is dispatched");
            fixture.timer.fireLatest();
            awaitTerminal(blockedDispatch);
            failed(blockedDispatch, "deadline bounds an executor that never starts transport");
            check(blockedDispatch.handle((value, error) -> error instanceof TimeoutException).join(), "timeout has explicit terminal cause");
            fixture.worker.runFirst();
            check(requests.get() == 0, "expired queued transport is never started");
            check(!fixture.checker.isCheckCompleted(), "timed-out request never validates the gate");
        }

        NonCancellingFuture delayed = new NonCancellingFuture();
        AtomicInteger count = new AtomicInteger();
        try (Fixture fixture = new Fixture("27.0", () -> count.getAndIncrement() == 0
            ? delayed : completed("{\"latest\":\"27.0\",\"releaseNotes\":\"fresh\"}"))) {
            CompletableFuture<Boolean> first = fixture.checker.forceCheck();
            fixture.worker.runLast();
            fixture.timer.fireLatest();
            awaitTerminal(first);
            failed(first, "pending transport reaches a terminal timeout");
            CompletableFuture<Boolean> retry = fixture.checker.forceCheck();
            check(retry != first, "retry creates a new attempt after failure");
            fixture.worker.runLast();
            check(!retry.join(), "retry succeeds after an unresponsive transport");
            delayed.complete(JsonParser.parseString("{\"latest\":\"99.0\",\"critical\":true}"));
            check(fixture.checker.isCheckCompleted(), "late obsolete success cannot invalidate the retry");
            check(!fixture.checker.isUpdateRequired(), "late obsolete success cannot replace the current decision");
            check(fixture.checker.getLatestVersion().equals("27.0"), "late obsolete success cannot replace current metadata");
            check(fixture.checker.getReleaseNotes().equals("fresh"), "late obsolete success cannot overwrite notes");
            fixture.timer.fireLatest();
            check(fixture.checker.isCheckCompleted(), "cancelled deadline cannot revert successful state");
        }

        CompletableFuture<JsonElement> cancellable = new CompletableFuture<>();
        try (Fixture fixture = new Fixture("27.0", () -> cancellable)) {
            CompletableFuture<Boolean> result = fixture.checker.forceCheck();
            fixture.worker.runLast();
            fixture.timer.fireLatest();
            awaitTerminal(result);
            failed(result, "normal pending future times out");
            awaitTerminal(cancellable);
            check(cancellable.isCancelled(), "timeout releases a cancellable pending source");
        }

        NonCancellingFuture lateFailure = new NonCancellingFuture();
        AtomicInteger attempts = new AtomicInteger();
        try (Fixture fixture = new Fixture("27.0", () -> attempts.getAndIncrement() == 0
            ? lateFailure : completed("{\"latest\":\"27.0\"}"))) {
            CompletableFuture<Boolean> abandoned = fixture.checker.forceCheck();
            fixture.worker.runLast();
            abandoned.cancel(false);
            CompletableFuture<Boolean> retry = fixture.checker.forceCheck();
            fixture.worker.runLast();
            check(!retry.join(), "explicitly cancelled check can be retried");
            lateFailure.completeExceptionally(new IllegalStateException("obsolete"));
            check(fixture.checker.isCheckCompleted(), "old failure cannot revert a newer successful check");
        }
    }

    private static void supplierFailures() {
        List<Supplier<CompletableFuture<JsonElement>>> broken = List.of(
            () -> null,
            () -> { throw new IllegalStateException("initialization failed"); },
            () -> { throw new AssertionError("initialization linkage failure"); },
            () -> CompletableFuture.failedFuture(new IllegalStateException("transport failed")),
            () -> CompletableFuture.completedFuture(null));
        for (Supplier<CompletableFuture<JsonElement>> supplier : broken) {
            try (Fixture fixture = new Fixture("27.0", supplier)) {
                CompletableFuture<Boolean> result = fixture.checker.forceCheck();
                fixture.worker.runLast();
                failed(result, "transport initialization/failure always completes the public future");
                check(!fixture.checker.isCheckCompleted(), "transport failure fails closed");
                check(fixture.checker.forceCheck() != result, "failure does not enter the success cache");
            }
        }
        try (ManualScheduler timer = new ManualScheduler()) {
            Executor rejected = task -> { throw new RejectedExecutionException("closed worker"); };
            VersionChecker checker = new VersionChecker("27.0", () -> completed("{}"), rejected, timer, () -> 1, 10);
            failed(checker.forceCheck(), "executor rejection completes the public future");
            check(timer.tasks.getFirst().isCancelled(), "executor rejection cancels its deadline");
        }
        try (ManualScheduler timer = new ManualScheduler()) {
            timer.reject = true;
            ManualExecutor worker = new ManualExecutor();
            VersionChecker checker = new VersionChecker("27.0", () -> completed("{}"), worker, timer, () -> 1, 10);
            failed(checker.forceCheck(), "deadline scheduling rejection completes the public future");
            check(!checker.isCheckCompleted(), "scheduler failure fails closed");
        }
    }

    private static void isolatedObservers() {
        AtomicInteger calls = new AtomicInteger();
        try (Fixture fixture = new Fixture("26.8-4", () -> completed("{\"latest\":\"27.0\"}"))) {
            fixture.checker.addListener(version -> calls.incrementAndGet());
            CompletableFuture<Boolean> result = fixture.checker.forceCheck();
            fixture.worker.runLast();
            check(result.isDone() && result.join(), "gate completes before update listeners execute");
            check(calls.get() == 0, "observers are separately dispatched");
            check(fixture.worker.tasks.size() == 1, "notification cannot occupy transport completion");
        }
    }

    private static void isolatedDeadlineConsumers() {
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        try (ManualScheduler timer = new ManualScheduler()) {
            ManualExecutor worker = new ManualExecutor();
            VersionChecker checker = new VersionChecker("27.0", CompletableFuture::new, worker, timer, () -> 1, 10);
            CompletableFuture<Boolean> first = checker.forceCheck();
            worker.runLast();
            first.whenComplete((value, error) -> {
                consumerEntered.countDown();
                try {
                    releaseConsumer.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            timer.fireLatest();
            try {
                check(consumerEntered.await(2, TimeUnit.SECONDS), "first deadline invokes a dependent consumer");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            CompletableFuture<Boolean> retry = checker.forceCheck();
            worker.runLast();
            timer.fireLatest();
            awaitTerminal(retry);
            failed(retry, "blocked callback on an old future cannot starve later deadlines");
        } finally {
            releaseConsumer.countDown();
        }
    }

    private static void awaitTerminal(CompletableFuture<?> future) {
        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException | java.util.concurrent.CancellationException expectedFailure) {
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } catch (TimeoutException missingCompletion) {
            throw new AssertionError("Gate future remained pending", missingCompletion);
        }
    }

    private static CompletableFuture<JsonElement> completed(String json) {
        return CompletableFuture.completedFuture(JsonParser.parseString(json));
    }

    private static void failed(CompletableFuture<?> future, String message) {
        check(future.isDone() && future.isCompletedExceptionally(), message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture implements AutoCloseable {
        final ManualExecutor worker = new ManualExecutor();
        final ManualScheduler timer = new ManualScheduler();
        final AtomicLong clock = new AtomicLong(1_000);
        final VersionChecker checker;

        Fixture(String current, Supplier<CompletableFuture<JsonElement>> fetch) {
            checker = new VersionChecker(current, fetch, worker, timer, clock::get, 10);
        }

        @Override public void close() { timer.shutdownNow(); }
    }

    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final Thread owner = Thread.currentThread();
        @Override public void execute(Runnable task) {
            if (Thread.currentThread() == owner) tasks.addLast(task);
        }
        void runFirst() { tasks.removeFirst().run(); }
        void runLast() { tasks.removeLast().run(); }
    }

    private static final class NonCancellingFuture extends CompletableFuture<JsonElement> {
        @Override public boolean cancel(boolean interrupt) { return false; }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor implements AutoCloseable {
        final List<Timer> tasks = new ArrayList<>();
        boolean reject;

        ManualScheduler() { super(1); }

        @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            if (reject) throw new RejectedExecutionException("closed scheduler");
            Timer timer = new Timer(task);
            tasks.add(timer);
            return timer;
        }

        void fireLatest() { tasks.getLast().run(); }
        @Override public void close() { shutdownNow(); }
    }

    private static final class Timer extends FutureTask<Void> implements ScheduledFuture<Void> {
        Timer(Runnable task) { super(task, null); }
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(java.util.concurrent.Delayed other) { return 0; }
    }
}

package com.randomrun.network;

import dev.onvoid.webrtc.*;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RtcTcpBridge implements AutoCloseable {
    public static final String CHANNEL_LABEL = "randomrun-minecraft-v2";
    private static final int CHUNK = 16384;
    private static final int WINDOW_MESSAGES = 64;
    private static final long MAX_BUFFERED = 1048576;
    private static final ExecutorService CLEANUP = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "RandomRun-WebRTC-cleanup"); t.setDaemon(true); return t; });
    private final RTCDataChannel channel;
    private final ArrayBlockingQueue<byte[]> incoming = new ArrayBlockingQueue<>(128);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object writable = new Object();
    private final Object nativeAccess = new Object();
    private final CompletableFuture<Void> open = new CompletableFuture<>();
    private final CompletableFuture<Void> disposed = new CompletableFuture<>();
    private volatile Socket socket;
    private final Runnable onClose;
    private long sentMessages, acknowledgedMessages;

    public RtcTcpBridge(RTCDataChannel channel, Runnable onClose) {
        this.channel = channel;
        this.onClose = onClose;
        if (!channel.isReliable() || !channel.isOrdered()) throw new IllegalArgumentException("Minecraft requires an ordered reliable data channel");
        channel.registerObserver(new RTCDataChannelObserver() {
            public void onBufferedAmountChange(long previous) { synchronized (writable) { writable.notifyAll(); } }
            public void onStateChange() {
                if (closed.get()) return;
                if (channel.getState() == RTCDataChannelState.OPEN) open.complete(null);
                else if (channel.getState() == RTCDataChannelState.CLOSED) close();
            }
            public void onMessage(RTCDataChannelBuffer message) {
                if (closed.get()) return;
                if (!message.binary) { acknowledge(message.data); return; }
                if (message.data.remaining() == 0 || message.data.remaining() > CHUNK) { fail("Invalid data frame"); return; }
                byte[] bytes = new byte[message.data.remaining()];
                message.data.get(bytes);
                if (!incoming.offer(bytes)) fail("Peer exceeded the receive window");
            }
        });
        if (channel.getState() == RTCDataChannelState.OPEN) open.complete(null);
    }
    public CompletableFuture<Void> opened() { return open; }
    public CompletableFuture<Void> disposed() { return disposed; }
    private void acknowledge(ByteBuffer data) {
        try {
            if (data.remaining() == 0 || data.remaining() > 20) throw new IOException("Invalid acknowledgement");
            String value = StandardCharsets.US_ASCII.decode(data).toString();
            if (!value.matches("[0-9]{1,19}")) throw new IOException("Invalid acknowledgement");
            long count = Long.parseLong(value);
            synchronized (writable) {
                if (count < acknowledgedMessages || count > sentMessages) throw new IOException("Invalid acknowledgement count");
                acknowledgedMessages = count;
                writable.notifyAll();
            }
        } catch (Exception error) { fail("Invalid receive acknowledgement"); }
    }
    private void reserveMessage() throws Exception {
        long start = System.nanoTime(), deadline = start + TimeUnit.SECONDS.toNanos(30);
        boolean reported = false;
        synchronized (writable) {
            while (!closed.get() && sentMessages - acknowledgedMessages >= WINDOW_MESSAGES) {
                long now = System.nanoTime();
                if (now >= deadline) throw new IOException("WebRTC receive window timeout");
                if (!reported && now - start > TimeUnit.SECONDS.toNanos(4)) { reported = true; com.randomrun.infrastructure.util.CompactLogger.warn("WebRTC peer is not acknowledging data: sent={}, acked={}, state={}", sentMessages, acknowledgedMessages, channel.getState()); }
                writable.wait(250);
            }
            if (closed.get()) throw new IOException("Bridge closed");
            sentMessages++;
        }
        while (!closed.get()) {
            long buffered;
            synchronized (nativeAccess) {
                if (closed.get()) throw new IOException("Bridge closed");
                buffered = channel.getBufferedAmount();
            }
            if (buffered <= MAX_BUFFERED) return;
            if (System.nanoTime() >= deadline) throw new IOException("WebRTC send timeout");
            if (!reported && System.nanoTime() - start > TimeUnit.SECONDS.toNanos(4)) { reported = true; com.randomrun.infrastructure.util.CompactLogger.warn("WebRTC send buffer is not draining: buffered={}", buffered); }
            synchronized (writable) { writable.wait(100); }
        }
        throw new IOException("Bridge closed");
    }
    private void send(ByteBuffer data, boolean binary) throws Exception {
        synchronized (nativeAccess) {
            if (closed.get()) throw new IOException("Bridge closed");
            channel.send(new RTCDataChannelBuffer(data, binary));
        }
    }
    public void attach(Socket socket) throws IOException {
        if (this.socket != null || closed.get()) { socket.close(); throw new IOException("Bridge already attached or closed"); }
        socket.setTcpNoDelay(true); this.socket = socket;
        Thread.ofVirtual().name("RandomRun-WebRTC-read").start(() -> {
            try {
                open.get(30, TimeUnit.SECONDS);
                byte[] buffer = new byte[CHUNK];
                int count;
                while (!closed.get() && (count = socket.getInputStream().read(buffer)) != -1) {
                    if (count == 0) continue;
                    reserveMessage();
                    send(ByteBuffer.wrap(Arrays.copyOf(buffer, count)), true);
                }
            } catch (Exception error) { if (!closed.get()) fail("TCP read: " + error.getClass().getSimpleName()); }
            finally { close(); }
        });
        Thread.ofVirtual().name("RandomRun-WebRTC-write").start(() -> {
            long delivered = 0;
            try {
                while (!closed.get()) {
                    byte[] bytes = incoming.poll(1, TimeUnit.SECONDS);
                    if (bytes != null) {
                        long started = System.nanoTime();
                        socket.getOutputStream().write(bytes);
                        if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(4)) com.randomrun.infrastructure.util.CompactLogger.warn("Minecraft socket accepted data after {} ms", (System.nanoTime() - started) / 1000000);
                        send(ByteBuffer.wrap(Long.toString(++delivered).getBytes(StandardCharsets.US_ASCII)), false);
                    }
                }
            } catch (Exception error) { if (!closed.get()) fail("TCP write: " + error.getClass().getSimpleName()); }
            finally { close(); }
        });
    }
    private void fail(String reason) {
        if (!closed.get()) com.randomrun.infrastructure.util.CompactLogger.warn("WebRTC bridge stopped: {}", reason);
        close();
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        open.completeExceptionally(new IOException("WebRTC connection closed"));
        synchronized (writable) { writable.notifyAll(); }
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        incoming.clear();
        CLEANUP.execute(() -> {
            synchronized (nativeAccess) {
                try { channel.unregisterObserver(); channel.close(); channel.dispose(); }
                catch (Exception ignored) { }
                finally { disposed.complete(null); }
            }
        });
        onClose.run();
    }
}

package com.murakib.attendance.service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class NetworkMonitorService {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "network-monitor");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean online = new AtomicBoolean(false);
    private Consumer<Boolean> listener;

    public void start(Consumer<Boolean> onStatusChange) {
        this.listener = onStatusChange;
        executor.scheduleAtFixedRate(this::check, 0, 15, TimeUnit.SECONDS);
    }

    private void check() {
        boolean wasOnline = online.get();
        boolean nowOnline = ping();
        online.set(nowOnline);
        if (listener != null && wasOnline != nowOnline) {
            listener.accept(nowOnline);
        } else if (listener != null && !wasOnline && nowOnline) {
            listener.accept(true);
        }
    }

    public boolean isOnline() {
        return online.get();
    }

    private boolean ping() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

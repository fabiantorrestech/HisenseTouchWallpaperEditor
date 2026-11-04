package io.github.fabiantorrestech.touchwallpaper.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One shared worker thread, so saves and renders can't overlap. */
final class Background {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private Background() {
    }

    static void run(Runnable task) {
        WORKER.execute(task);
    }
}

package dev.crownforge.manager;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-webhook single-thread executors to serialize requests and avoid burst conflicts.
 */
public final class WebhookQueues {
    private static final ConcurrentMap<String, ExecutorService> QUEUES = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private WebhookQueues() {}

    public static <T> Future<T> submit(String webhookUrl, Callable<T> task) {
        ExecutorService ex = QUEUES.computeIfAbsent(webhookUrl, k ->
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "DiscordWebhook-" + COUNTER.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                })
        );
        return ex.submit(task);
    }

    public static void shutdownAll() {
        for (ExecutorService ex : QUEUES.values()) {
            ex.shutdown();
        }
        QUEUES.clear();
    }
}

package com.ohhapple.monster_conquest.arena.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端操作包的轻量令牌桶，避免单客户端无成本刷包。 */
public final class ArenaRequestLimiter {

    private final double requestsPerSecond;
    private final double burst;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    public ArenaRequestLimiter(double requestsPerSecond, double burst) {
        this.requestsPerSecond = Math.max(0.1, requestsPerSecond);
        this.burst = Math.max(1.0, burst);
    }

    public synchronized boolean allow(UUID playerId) {
        long now = System.nanoTime();
        Bucket bucket = buckets.computeIfAbsent(
                playerId, ignored -> new Bucket(burst, now));
        double elapsedSeconds = Math.max(
                0.0, (now - bucket.lastNanos) / 1_000_000_000.0);
        bucket.tokens = Math.min(
                burst, bucket.tokens + elapsedSeconds * requestsPerSecond);
        bucket.lastNanos = now;
        if (bucket.tokens < 1.0) return false;
        bucket.tokens -= 1.0;
        return true;
    }

    public synchronized void remove(UUID playerId) {
        buckets.remove(playerId);
    }

    public synchronized void clear() {
        buckets.clear();
    }

    private static final class Bucket {
        private double tokens;
        private long lastNanos;

        private Bucket(double tokens, long lastNanos) {
            this.tokens = tokens;
            this.lastNanos = lastNanos;
        }
    }
}

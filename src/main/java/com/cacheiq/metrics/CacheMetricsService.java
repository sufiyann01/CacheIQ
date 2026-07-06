package com.cacheiq.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class CacheMetricsService {

    private final MeterRegistry registry;

    // L1 counters
    private final Counter l1HitCounter;
    private final Counter l1MissCounter;

    // L2 counters
    private final Counter l2HitCounter;
    private final Counter l2MissCounter;

    // DB fallback counter
    private final Counter dbFallbackCounter;

    // Key access tracking (top keys)
    private final ConcurrentHashMap<String, AtomicLong> keyAccessMap = new ConcurrentHashMap<>();

    // API latency timer
    private final Timer cacheReadTimer;
    private final Timer cacheWriteTimer;

    // TTL reclassification counter
    private final Counter ttlReclassifiedCounter;

    public CacheMetricsService(MeterRegistry registry) {
        this.registry = registry;

        this.l1HitCounter = Counter.builder("cacheiq.l1.hits")
                .description("L1 Caffeine cache hits")
                .register(registry);

        this.l1MissCounter = Counter.builder("cacheiq.l1.misses")
                .description("L1 Caffeine cache misses")
                .register(registry);

        this.l2HitCounter = Counter.builder("cacheiq.l2.hits")
                .description("L2 Redis cache hits")
                .register(registry);

        this.l2MissCounter = Counter.builder("cacheiq.l2.misses")
                .description("L2 Redis cache misses")
                .register(registry);

        this.dbFallbackCounter = Counter.builder("cacheiq.db.fallback")
                .description("Database fallback reads")
                .register(registry);

        this.ttlReclassifiedCounter = Counter.builder("cacheiq.ttl.reclassified")
                .description("Keys reclassified by adaptive TTL scheduler")
                .register(registry);

        this.cacheReadTimer = Timer.builder("cacheiq.operation.read")
                .description("Cache read operation latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.cacheWriteTimer = Timer.builder("cacheiq.operation.write")
                .description("Cache write operation latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Gauge for total unique keys
        Gauge.builder("cacheiq.keys.total", keyAccessMap, ConcurrentHashMap::size)
                .description("Total unique keys tracked")
                .register(registry);
    }

    public void recordL1Hit() { l1HitCounter.increment(); }
    public void recordL1Miss() { l1MissCounter.increment(); }
    public void recordL2Hit() { l2HitCounter.increment(); }
    public void recordL2Miss() { l2MissCounter.increment(); }
    public void recordDbFallback() { dbFallbackCounter.increment(); }
    public void recordTtlReclassified() { ttlReclassifiedCounter.increment(); }

    public void recordKeyAccess(String key) {
        keyAccessMap.computeIfAbsent(key, k -> {
            AtomicLong count = new AtomicLong(0);
            Gauge.builder("cacheiq.key.access_count", count, AtomicLong::get)
                    .tag("key", sanitizeKey(key))
                    .description("Access count per cache key")
                    .register(registry);
            return count;
        }).incrementAndGet();
    }

    public Timer getCacheReadTimer() { return cacheReadTimer; }
    public Timer getCacheWriteTimer() { return cacheWriteTimer; }

    public double getL1HitRatio() {
        double hits = l1HitCounter.count();
        double misses = l1MissCounter.count();
        return (hits + misses) == 0 ? 0 : hits / (hits + misses);
    }

    public double getL2HitRatio() {
        double hits = l2HitCounter.count();
        double misses = l2MissCounter.count();
        return (hits + misses) == 0 ? 0 : hits / (hits + misses);
    }

    private String sanitizeKey(String key) {
        // limit label cardinality for Prometheus
        return key.length() > 64 ? key.substring(0, 64) : key;
    }
}

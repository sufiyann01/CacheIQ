package com.cacheiq;

import com.cacheiq.model.CacheEntry;
import com.cacheiq.model.CacheTemperature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheEntryTest {

    @Test
    void hitRatioCalculation() {
        CacheEntry entry = CacheEntry.builder()
                .cacheKey("test-key")
                .cacheValue("test-value")
                .hitCount(80L)
                .missCount(20L)
                .accessCount(100L)
                .build();

        assertEquals(0.8, entry.getHitRatio(), 0.001);
    }

    @Test
    void incrementAccessUpdatesCounters() {
        CacheEntry entry = CacheEntry.builder()
                .cacheKey("k1")
                .cacheValue("v1")
                .accessCount(0L)
                .hitCount(0L)
                .missCount(0L)
                .build();

        entry.incrementAccess(true);
        entry.incrementAccess(false);

        assertEquals(2L, entry.getAccessCount());
        assertEquals(1L, entry.getHitCount());
        assertEquals(1L, entry.getMissCount());
    }

    @Test
    void zeroAccessHitRatioIsZero() {
        CacheEntry entry = CacheEntry.builder()
                .cacheKey("k2")
                .hitCount(0L)
                .missCount(0L)
                .build();
        assertEquals(0.0, entry.getHitRatio());
    }
}

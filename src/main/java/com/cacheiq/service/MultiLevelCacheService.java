package com.cacheiq.service;

import com.cacheiq.config.CacheProperties;
import com.cacheiq.metrics.CacheMetricsService;
import com.cacheiq.model.CacheEntry;
import com.cacheiq.model.CacheTemperature;
import com.cacheiq.repository.CacheEntryRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class MultiLevelCacheService {

    private final Cache<String, String> l1Cache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheEntryRepository repository;
    private final CacheMetricsService metrics;
    private final CacheProperties props;

    private static final String REDIS_PREFIX = "cacheiq:";

    public MultiLevelCacheService(RedisTemplate<String, Object> redisTemplate,
                                   CacheEntryRepository repository,
                                   CacheMetricsService metrics,
                                   CacheProperties props) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
        this.metrics = metrics;
        this.props = props;

        // Build L1 caffeine manually so we can control TTL per-key via eviction
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(props.getL1().getMaxSize())
                .expireAfterWrite(Duration.ofMinutes(props.getL1().getDefaultTtlMinutes()))
                .recordStats()
                .build();
    }

    // ─── READ: L1 → L2 → DB ──────────────────────────────────────────────

    @Transactional
    public Optional<String> get(String key) {
        return metrics.getCacheReadTimer().record(() -> {
            metrics.recordKeyAccess(key);

            // L1 lookup
            String l1Value = l1Cache.getIfPresent(key);
            if (l1Value != null) {
                log.debug("[L1 HIT] key={}", key);
                metrics.recordL1Hit();
                updateStats(key, true);
                return Optional.of(l1Value);
            }
            metrics.recordL1Miss();
            log.debug("[L1 MISS] key={}", key);

            // L2 lookup
            Object l2Value = redisTemplate.opsForValue().get(REDIS_PREFIX + key);
            if (l2Value != null) {
                String value = l2Value.toString();
                log.debug("[L2 HIT] key={}", key);
                metrics.recordL2Hit();
                l1Cache.put(key, value);          // backfill L1
                updateStats(key, true);
                return Optional.of(value);
            }
            metrics.recordL2Miss();
            log.debug("[L2 MISS] key={}", key);

            // DB fallback
            Optional<CacheEntry> dbEntry = repository.findByCacheKey(key);
            if (dbEntry.isPresent()) {
                String value = dbEntry.get().getCacheValue();
                log.debug("[DB HIT] key={}", key);
                metrics.recordDbFallback();
                // Backfill both caches
                CacheTemperature temp = dbEntry.get().getTemperature();
                long ttlMinutes = resolveTtlMinutes(temp);
                l1Cache.put(key, value);
                redisTemplate.opsForValue().set(REDIS_PREFIX + key, value, Duration.ofMinutes(ttlMinutes));
                updateStats(key, false);
                return Optional.of(value);
            }

            log.debug("[TOTAL MISS] key={}", key);
            updateStats(key, false);
            return Optional.empty();
        });
    }

    // ─── WRITE: write-through to L1, L2, DB ──────────────────────────────

    @Transactional
    public void put(String key, String value) {
        metrics.getCacheWriteTimer().record(() -> {
            CacheEntry entry = repository.findByCacheKey(key).orElseGet(() ->
                    CacheEntry.builder()
                            .cacheKey(key)
                            .createdAt(LocalDateTime.now())
                            .build()
            );

            entry.setCacheValue(value);
            entry.setUpdatedAt(LocalDateTime.now());
            CacheTemperature temp = classifyTemperature(entry);
            entry.setTemperature(temp);
            long ttlMinutes = resolveTtlMinutes(temp);
            entry.setTtlMinutes((int) ttlMinutes);
            repository.save(entry);

            l1Cache.put(key, value);
            redisTemplate.opsForValue().set(REDIS_PREFIX + key, value, Duration.ofMinutes(ttlMinutes));

            log.debug("[PUT] key={} temp={} ttl={}m", key, temp, ttlMinutes);
        });
    }

    // ─── DELETE ───────────────────────────────────────────────────────────

    @Transactional
    public void evict(String key) {
        l1Cache.invalidate(key);
        redisTemplate.delete(REDIS_PREFIX + key);
        repository.findByCacheKey(key).ifPresent(repository::delete);
        log.debug("[EVICT] key={}", key);
    }

    // ─── STATS ────────────────────────────────────────────────────────────

    public List<CacheEntry> getTopKeys(int limit) {
        return repository.findTopByAccessCount(
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public List<CacheEntry> getAllEntries() {
        return repository.findAllByOrderByAccessCountDesc();
    }

    // ─── ADAPTIVE TTL ─────────────────────────────────────────────────────

    public CacheTemperature classifyTemperature(CacheEntry entry) {
        long count = entry.getAccessCount();
        double hitRatio = entry.getHitRatio();
        CacheProperties.Ttl ttl = props.getTtl();

        if (count > ttl.getHotThreshold() || hitRatio > ttl.getHotHitRatio()) {
            return CacheTemperature.HOT;
        } else if (count >= ttl.getWarmLower() && count <= ttl.getWarmUpper()) {
            return CacheTemperature.WARM;
        } else {
            return CacheTemperature.COLD;
        }
    }

    public long resolveTtlMinutes(CacheTemperature temp) {
        CacheProperties.Ttl ttl = props.getTtl();
        return switch (temp) {
            case HOT  -> ttl.getHotHours() * 60L;
            case WARM -> ttl.getWarmHours() * 60L;
            case COLD -> ttl.getColdMinutes();
        };
    }

    public Cache<String, String> getL1Cache() { return l1Cache; }

    // ─── HELPERS ─────────────────────────────────────────────────────────

    @Transactional
    protected void updateStats(String key, boolean isHit) {
        repository.findByCacheKey(key).ifPresent(entry -> {
            entry.incrementAccess(isHit);
            repository.save(entry);
        });
    }
}

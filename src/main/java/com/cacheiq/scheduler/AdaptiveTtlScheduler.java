package com.cacheiq.scheduler;

import com.cacheiq.metrics.CacheMetricsService;
import com.cacheiq.model.CacheEntry;
import com.cacheiq.model.CacheTemperature;
import com.cacheiq.repository.CacheEntryRepository;
import com.cacheiq.service.MultiLevelCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdaptiveTtlScheduler {

    private final CacheEntryRepository repository;
    private final MultiLevelCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService metrics;

    private static final String REDIS_PREFIX = "cacheiq:";

    @Scheduled(fixedDelayString = "${cacheiq.scheduler.ttl-update-interval-ms:180000}")
    @Transactional
    public void updateAdaptiveTtl() {
        log.info("[AdaptiveTTL] Starting TTL reclassification pass...");

        List<CacheEntry> allEntries = repository.findAll();
        int reclassified = 0;

        for (CacheEntry entry : allEntries) {
            CacheTemperature oldTemp = entry.getTemperature();
            CacheTemperature newTemp = cacheService.classifyTemperature(entry);

            if (oldTemp != newTemp) {
                entry.setTemperature(newTemp);
                long newTtlMinutes = cacheService.resolveTtlMinutes(newTemp);
                entry.setTtlMinutes((int) newTtlMinutes);
                repository.save(entry);

                // Update Redis TTL
                String redisKey = REDIS_PREFIX + entry.getCacheKey();
                Boolean exists = redisTemplate.hasKey(redisKey);
                if (Boolean.TRUE.equals(exists)) {
                    redisTemplate.expire(redisKey, Duration.ofMinutes(newTtlMinutes));
                }

                metrics.recordTtlReclassified();
                reclassified++;

                log.info("[AdaptiveTTL] key={} {} → {} ttl={}m",
                        entry.getCacheKey(), oldTemp, newTemp, newTtlMinutes);
            }
        }

        log.info("[AdaptiveTTL] Done. Total={} Reclassified={}", allEntries.size(), reclassified);
    }
}

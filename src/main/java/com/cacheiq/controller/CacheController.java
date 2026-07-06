package com.cacheiq.controller;

import com.cacheiq.metrics.CacheMetricsService;
import com.cacheiq.model.CacheEntry;
import com.cacheiq.service.MultiLevelCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
public class CacheController {

    private final MultiLevelCacheService cacheService;
    private final CacheMetricsService metrics;

    // ─── GET ─────────────────────────────────────────────────────────────

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key) {
        Optional<String> value = cacheService.get(key);
        if (value.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "key", key,
                    "value", value.get(),
                    "found", true
            ));
        }
        return ResponseEntity.ok(Map.of(
                "key", key,
                "found", false,
                "message", "Key not found in any cache level"
        ));
    }

    // ─── PUT ─────────────────────────────────────────────────────────────

    @PutMapping("/{key}")
    public ResponseEntity<Map<String, String>> put(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {

        String value = body.get("value");
        if (value == null || value.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
        }
        cacheService.put(key, value);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "status", "stored"
        ));
    }

    // ─── DELETE ──────────────────────────────────────────────────────────

    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, String>> evict(@PathVariable String key) {
        cacheService.evict(key);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "status", "evicted"
        ));
    }

    // ─── STATS ───────────────────────────────────────────────────────────

    @GetMapping("/stats/top")
    public ResponseEntity<List<CacheEntry>> topKeys(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(cacheService.getTopKeys(limit));
    }

    @GetMapping("/stats/all")
    public ResponseEntity<List<CacheEntry>> allEntries() {
        return ResponseEntity.ok(cacheService.getAllEntries());
    }

    @GetMapping("/stats/hit-ratio")
    public ResponseEntity<Map<String, Double>> hitRatios() {
        return ResponseEntity.ok(Map.of(
                "l1_hit_ratio", metrics.getL1HitRatio(),
                "l2_hit_ratio", metrics.getL2HitRatio()
        ));
    }

    // ─── HEALTH ──────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "CacheIQ"));
    }
}

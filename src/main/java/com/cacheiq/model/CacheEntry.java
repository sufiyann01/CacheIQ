package com.cacheiq.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cache_entries", indexes = {
        @Index(name = "idx_cache_key", columnList = "cache_key", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 512)
    private String cacheKey;

    @Column(name = "cache_value", columnDefinition = "TEXT")
    private String cacheValue;

    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private Long accessCount = 0L;

    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private Long hitCount = 0L;

    @Column(name = "miss_count", nullable = false)
    @Builder.Default
    private Long missCount = 0L;

    @Column(name = "ttl_minutes")
    private Integer ttlMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "temperature")
    @Builder.Default
    private CacheTemperature temperature = CacheTemperature.COLD;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_accessed_at")
    @Builder.Default
    private LocalDateTime lastAccessedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public double getHitRatio() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    public void incrementAccess(boolean isHit) {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
        if (isHit) this.hitCount++;
        else this.missCount++;
        this.updatedAt = LocalDateTime.now();
    }
}

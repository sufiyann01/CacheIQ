package com.cacheiq.repository;

import com.cacheiq.model.CacheEntry;
import com.cacheiq.model.CacheTemperature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CacheEntryRepository extends JpaRepository<CacheEntry, Long> {

    Optional<CacheEntry> findByCacheKey(String cacheKey);

    boolean existsByCacheKey(String cacheKey);

    @Modifying
    @Query("UPDATE CacheEntry c SET c.accessCount = c.accessCount + 1, " +
           "c.hitCount = c.hitCount + :hits, c.missCount = c.missCount + :misses " +
           "WHERE c.cacheKey = :key")
    void incrementStats(@Param("key") String key,
                        @Param("hits") long hits,
                        @Param("misses") long misses);

    List<CacheEntry> findByTemperature(CacheTemperature temperature);

    @Query("SELECT c FROM CacheEntry c ORDER BY c.accessCount DESC")
    List<CacheEntry> findTopByAccessCount(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT c FROM CacheEntry c WHERE c.accessCount > :threshold ORDER BY c.accessCount DESC")
    List<CacheEntry> findHotKeys(@Param("threshold") long threshold);

    List<CacheEntry> findAllByOrderByAccessCountDesc();
}

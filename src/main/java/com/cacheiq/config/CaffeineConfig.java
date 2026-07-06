package com.cacheiq.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class CaffeineConfig {

    private final CacheProperties props;

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(caffeineCacheBuilder());
        manager.setAllowNullValues(false);
        return manager;
    }

    @Bean
    public Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(props.getL1().getMaxSize())
                .expireAfterWrite(props.getL1().getDefaultTtlMinutes(), TimeUnit.MINUTES)
                .recordStats();   // enables hit/miss stats
    }
}

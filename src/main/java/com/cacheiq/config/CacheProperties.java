package com.cacheiq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cacheiq")
public class CacheProperties {

    private L1 l1 = new L1();
    private L2 l2 = new L2();
    private Ttl ttl = new Ttl();
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class L1 {
        private int maxSize = 1000;
        private int defaultTtlMinutes = 10;
    }

    @Data
    public static class L2 {
        private int defaultTtlMinutes = 60;
    }

    @Data
    public static class Ttl {
        private int hotHours = 6;
        private int warmHours = 1;
        private int coldMinutes = 15;
        private int hotThreshold = 50;
        private int warmLower = 10;
        private int warmUpper = 50;
        private double hotHitRatio = 0.8;
    }

    @Data
    public static class Scheduler {
        private long ttlUpdateIntervalMs = 180000;
    }
}

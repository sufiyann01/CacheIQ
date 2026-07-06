package com.cacheiq.model;

public enum CacheTemperature {
    HOT,   // access_count > 50 OR hit_ratio > 0.8 → TTL 6h
    WARM,  // access_count 10–50                   → TTL 1h
    COLD   // access_count < 10                    → TTL 15min
}

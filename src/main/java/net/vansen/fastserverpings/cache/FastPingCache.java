package net.vansen.fastserverpings.cache;

import net.vansen.fastserverpings.pipeline.status.Status;

import java.util.concurrent.ConcurrentHashMap;

public final class FastPingCache {

    public static final long TTL_MS = 15_000;

    private static final ConcurrentHashMap<String, CacheEntry> MAP = new ConcurrentHashMap<>();

    public static CacheEntry get(String key) {
        return MAP.get(key);
    }

    public static void put(String key, Status status) {
        MAP.put(key, new CacheEntry(status, System.currentTimeMillis()));
    }

    public static boolean fresh(CacheEntry e) {
        return System.currentTimeMillis() - e.timeMs() < TTL_MS;
    }
}

package net.vansen.fastserverpings.cache;

import net.vansen.fastserverpings.pipeline.status.Status;
import org.jetbrains.annotations.NotNull;

/**
 * A cache entry representing the result of a fast ping.
 *
 * @param status the status of the ping
 * @param timeMs the time taken for the ping in milliseconds
 */
public record CacheEntry(
        @NotNull Status status,
        long timeMs
) {
}

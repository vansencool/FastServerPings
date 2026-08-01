package net.vansen.fastserverpings.pipeline.status;

public enum StatusType {
    /**
     * Fired on packet 0 (status response). Ping is -1.
     */
    EARLY,
    /**
     * Fired on packet 1 (pong). Contains the real ping time.
     */
    COMPLETE
}

package net.vansen.fastserverpings.metrics;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.LongAdder;

public final class PingAvgMetrics {

    private static final LongAdder totalNs = new LongAdder();
    private static final LongAdder count = new LongAdder();
    public static boolean USE_FASTPING = true;

    public static long start() {
        return System.nanoTime();
    }

    public static void end(long startNs) {
        totalNs.add(System.nanoTime() - startNs);
        count.increment();
    }

    public static void toggleEnable() {
        USE_FASTPING = !USE_FASTPING;
    }

    public static void sendAndReset(@NotNull Player p) {
        long c = count.sumThenReset();
        long t = totalNs.sumThenReset();

        if (c == 0) {
            p.sendSystemMessage(Component.literal("[PingAvgMetrics] no data"));
            return;
        }

        double avgMs = (t / (double) c) / 1_000_000.0;

        p.sendSystemMessage(
                Component.literal(
                        "[PingAvgMetrics] " +
                                (USE_FASTPING ? "FASTPING" : "VANILLA") +
                                " avg per-server = " +
                                String.format("%.2f", avgMs) +
                                " ms (" + c + " servers)"
                )
        );
    }
}

package net.vansen.fastserverpings.metrics;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
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

    public static void sendAndReset(@NotNull PlayerEntity p) {
        long c = count.sumThenReset();
        long t = totalNs.sumThenReset();

        if (c == 0) {
            p.sendMessage(Text.literal("[PingAvgMetrics] no data"), false);
            return;
        }

        double avgMs = (t / (double) c) / 1_000_000.0;

        p.sendMessage(
                Text.literal(
                        "[PingAvgMetrics] " +
                                (USE_FASTPING ? "FASTPING" : "VANILLA") +
                                " avg per-server = " +
                                String.format("%.2f", avgMs) +
                                " ms (" + c + " servers)"
                ),
                false
        );
    }
}

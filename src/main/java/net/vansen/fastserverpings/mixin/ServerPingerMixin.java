package net.vansen.fastserverpings.mixin;

import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.vansen.fastserverpings.cache.CacheEntry;
import net.vansen.fastserverpings.cache.FastPingCache;
import net.vansen.fastserverpings.metrics.PingAvgMetrics;
import net.vansen.fastserverpings.pipeline.FastPing;
import net.vansen.fastserverpings.pipeline.status.Status;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(MultiplayerServerListPinger.class)
public abstract class ServerPingerMixin {
    @Unique
    private static final ThreadLocal<Boolean> INVOKE_GUARD = ThreadLocal.withInitial(() -> false); // Guard for invokeAdd, to avoid recursion

    @Invoker("add")
    protected abstract void fastping$invokeAdd(
            ServerInfo entry,
            Runnable saver,
            Runnable pingCallback,
            NetworkingBackend backend
    );

    @Inject(
            method = "add",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fastping$add(
            ServerInfo entry,
            Runnable saver,
            Runnable pingCallback,
            NetworkingBackend backend,
            CallbackInfo ci
    ) {
        if (INVOKE_GUARD.get()) {
            return;
        }

        long startNs = PingAvgMetrics.start();

        if (!PingAvgMetrics.USE_FASTPING) {
            Runnable wrapped = () -> {
                PingAvgMetrics.end(startNs);
                pingCallback.run();
            };

            try {
                INVOKE_GUARD.set(true);
                fastping$invokeAdd(entry, saver, wrapped, backend);
            } finally {
                INVOKE_GUARD.remove();
            }

            ci.cancel();
            return;
        }

        ci.cancel();


        String key = entry.address;
        CacheEntry cached = FastPingCache.get(key);

        /*
        if (cached != null && FastPingCache.fresh(cached)) { // SWR: stale-while-revalidate
            var s = cached.status();

            entry.label = s.motd();
            entry.ping = s.ping();

            entry.playerCountLabel =
                    MultiplayerServerListPinger.createPlayerCountText(
                            s.online(),
                            s.max()
                    );

            entry.players = new ServerMetadata.Players(
                    s.max(),
                    s.online(),
                    List.of()
            );

            entry.version = Text.literal(s.version());
            entry.protocolVersion = s.protocol();
            if (s.favicon() != null) {
                entry.setFavicon(s.favicon().iconBytes());
            }
        }
        else {
            entry.label = Text.translatable("multiplayer.status.pinging");
            entry.ping = -1;
            entry.playerListSummary = Collections.emptyList();
        }

         */
        entry.label = Text.translatable("multiplayer.status.pinging");
        entry.ping = -1;
        entry.playerListSummary = Collections.emptyList();

        try {
            String addr = entry.address;
            String host;
            int port;

            int idx = addr.indexOf(':');
            if (idx == -1) {
                host = addr;
                port = 25565;
            } else {
                host = addr.substring(0, idx);
                port = Integer.parseInt(addr.substring(idx + 1));
            }

            pingWithRetry(host, port, 3, () -> { // Retry callback
                entry.label = Text.literal("Failed to ping server, retrying...").formatted(Formatting.YELLOW);
                entry.ping = -1;
            }).thenAccept(s -> { // Success
                FastPingCache.put(key, s);
                entry.label = s.motd();
                entry.ping = s.ping();

                entry.playerCountLabel =
                        MultiplayerServerListPinger.createPlayerCountText(
                                s.online(),
                                s.max()
                        );

                entry.players = new ServerMetadata.Players(
                        s.max(),
                        s.online(),
                        List.of()
                );

                entry.version = Text.literal(s.version());
                entry.protocolVersion = s.protocol();
                if (s.favicon() != null) {
                    entry.setFavicon(s.favicon().iconBytes());
                }

                PingAvgMetrics.end(startNs);
                pingCallback.run();
            }).exceptionally(e -> {
                entry.label = Text.translatable("multiplayer.status.cannot_connect").withColor(-65536);
                entry.playerCountLabel = ScreenTexts.EMPTY;
                entry.ping = -1;
                PingAvgMetrics.end(startNs);
                return null;
            });
        } catch (Throwable t) {
            entry.label = Text.translatable("multiplayer.status.cannot_connect").withColor(-65536);
            entry.playerCountLabel = ScreenTexts.EMPTY;
            entry.ping = -1;
            PingAvgMetrics.end(startNs);
        }
    }

    @Unique
    private static CompletableFuture<Status> pingWithRetry(
            @NotNull String host,
            int port,
            int attempts, // TODO: make configurable
            @NotNull Runnable onRetry
    ) {
        CompletableFuture<Status> f = new CompletableFuture<>();
        pingAttempt(host, port, attempts, onRetry, f);
        return f;
    }

    @Unique
    private static void pingAttempt(
            @NotNull String host,
            int port,
            int left,
            @NotNull Runnable onRetry,
            @NotNull CompletableFuture<Status> out
    ) {
        FastPing.ping(host, port).whenComplete((s, e) -> {
            if (e == null) {
                out.complete(s);
            } else if (left > 1) {
                onRetry.run();
                pingAttempt(host, port, left - 1, onRetry, out);
            } else {
                out.completeExceptionally(e);
            }
        });
    }
}
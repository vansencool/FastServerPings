package net.vansen.fastserverpings.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.vansen.fastserverpings.cache.CacheEntry;
import net.vansen.fastserverpings.cache.FastPingCache;
import net.vansen.fastserverpings.pipeline.FastPing;
import net.vansen.fastserverpings.pipeline.status.Status;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Mixin(MultiplayerServerListPinger.class)
public abstract class ServerPingerMixin {
    @Unique
    // Map of servers that are being pinged currently to prevent duplicate pings to the same server
    // Causes less rate limiting when spamming refresh, and also doesn't stall for 10 seconds after spamming refresh
    private static final ConcurrentHashMap<String, CompletableFuture<Status>> ACTIVE_PINGS = new ConcurrentHashMap<>();

    @Unique
    private static final ThreadPoolExecutor PINGER =
            new ThreadPoolExecutor(
                    32,
                    32,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(256),
                    new ThreadFactoryBuilder()
                            .setNameFormat("FastPing #%d")
                            .setDaemon(true)
                            .build(),
                    new ThreadPoolExecutor.DiscardPolicy()
            );

    @Unique
    private static CompletableFuture<Status> pingWithRetry(
            @NotNull String host,
            int port,
            @SuppressWarnings("SameParameterValue") int attempts, // TODO: make configurable
            @NotNull Runnable onRetry
    ) {
        return ACTIVE_PINGS.computeIfAbsent(host + ":" + port, k ->
                CompletableFuture.supplyAsync(() -> {
                    Throwable last = null;
                    for (int i = 0; i < attempts; i++) {
                        try {
                            return FastPing.ping(host, port).join();
                        } catch (Throwable t) {
                            last = t;
                            onRetry.run();
                        }
                    }
                    throw new CompletionException(last);
                }, PINGER).whenComplete((r, e) -> ACTIVE_PINGS.remove(k))
        );
    }

    @Inject(
            method = "add",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fastping$add(
            ServerInfo entry, Runnable saver, CallbackInfo ci
    ) {
        ci.cancel();

        String key = entry.address;
        CacheEntry cached = FastPingCache.get(key);

        if (cached != null && FastPingCache.fresh(cached)) { // SWR: stale-while-revalidate
            var s = cached.status();

            entry.label = s.motd();
            entry.ping = s.ping();

            entry.playerCountLabel = Text.literal(Integer.toString(s.online())).append(Text.literal("/").formatted(Formatting.DARK_GRAY)).append(Integer.toString(s.max())).formatted(Formatting.GRAY);

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
        } else {
            entry.label = Text.translatable("multiplayer.status.pinging");
            entry.ping = -1;
            entry.playerListSummary = Collections.emptyList();
        }

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

                entry.playerCountLabel = Text.literal(Integer.toString(s.online())).append(Text.literal("/").formatted(Formatting.DARK_GRAY)).append(Integer.toString(s.max())).formatted(Formatting.GRAY);

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
            }).exceptionally(e -> {
                entry.label = Text.translatable("multiplayer.status.cannot_connect").formatted(Formatting.RED);
                entry.playerCountLabel = ScreenTexts.EMPTY;
                entry.ping = -1;
                return null;
            });
        } catch (Throwable t) {
            entry.label = Text.translatable("multiplayer.status.cannot_connect").formatted(Formatting.RED);
            entry.playerCountLabel = ScreenTexts.EMPTY;
            entry.ping = -1;
        }
    }
}
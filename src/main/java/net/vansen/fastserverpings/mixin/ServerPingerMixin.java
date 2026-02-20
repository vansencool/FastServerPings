package net.vansen.fastserverpings.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.ChatFormatting;
import net.minecraft.DetectedVersion;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.network.EventLoopGroupHolder;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Mixin(ServerStatusPinger.class)
public abstract class ServerPingerMixin {
    @Unique
    private static final ThreadLocal<Boolean> INVOKE_GUARD = ThreadLocal.withInitial(() -> false); // Guard for invokeAdd, to avoid recursion

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

    @Invoker("pingServer")
    protected abstract void fastping$invokeAdd(
            ServerData entry,
            Runnable saver,
            Runnable pingCallback,
            EventLoopGroupHolder backend
    );

    @Inject(
            method = "pingServer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fastping$add(
            ServerData entry,
            Runnable saver,
            Runnable pingCallback,
            EventLoopGroupHolder backend,
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

        String key = entry.ip;
        CacheEntry cached = FastPingCache.get(key);

        if (cached != null && FastPingCache.fresh(cached)) { // SWR: stale-while-revalidate
            var s = cached.status();

            entry.motd = s.motd();
            entry.ping = s.ping();

            entry.status =
                    ServerStatusPinger.formatPlayerCount(
                            s.online(),
                            s.max()
                    );

            entry.players = new ServerStatus.Players(
                    s.max(),
                    s.online(),
                    List.of()
            );

            entry.version = Component.literal(s.version());
            entry.protocol = s.protocol();
            if (s.favicon() != null) {
                entry.setIconBytes(s.favicon().iconBytes());
            }
        } else {
            entry.motd = Component.translatable("multiplayer.status.pinging");
            entry.ping = -1;
            entry.playerList = Collections.emptyList();
        }

        try {
            String addr = entry.ip;
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
                entry.motd = Component.literal("Failed to ping server, retrying...").withStyle(ChatFormatting.YELLOW);
                entry.ping = -1;
            }).thenAccept(s -> { // Success
                FastPingCache.put(key, s);
                entry.motd = s.motd();
                entry.ping = s.ping();

                entry.status =
                        ServerStatusPinger.formatPlayerCount(
                                s.online(),
                                s.max()
                        );

                entry.players = new ServerStatus.Players(
                        s.max(),
                        s.online(),
                        List.of()
                );

                entry.version = Component.literal(s.version());
                try {
                    int protocol = ViaFabricPlus.getImpl().getTargetVersion().getVersion();
                    // To prevent minecraft showing "Outdated Server" for servers that are actually compatible with the client version
                    if (protocol == s.protocol()) entry.protocol = DetectedVersion.tryDetectVersion().protocolVersion();
                    else entry.protocol = s.protocol();
                } catch (Throwable t) {
                    entry.protocol = s.protocol();
                }
                if (s.favicon() != null) {
                    entry.setIconBytes(s.favicon().iconBytes());
                }

                PingAvgMetrics.end(startNs);
                pingCallback.run();
            }).exceptionally(e -> {
                entry.motd = Component.translatable("multiplayer.status.cannot_connect").withColor(-65536);
                entry.status = CommonComponents.EMPTY;
                entry.ping = -1;
                PingAvgMetrics.end(startNs);
                return null;
            });
        } catch (Throwable t) {
            entry.motd = Component.translatable("multiplayer.status.cannot_connect").withColor(-65536);
            entry.status = CommonComponents.EMPTY;
            entry.ping = -1;
            PingAvgMetrics.end(startNs);
        }
    }
}
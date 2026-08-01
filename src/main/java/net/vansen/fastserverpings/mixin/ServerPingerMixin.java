package net.vansen.fastserverpings.mixin;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.MinecraftVersion;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.vansen.fastserverpings.cache.CacheEntry;
import net.vansen.fastserverpings.cache.FastPingCache;
import net.vansen.fastserverpings.metrics.PingAvgMetrics;
import net.vansen.fastserverpings.pipeline.FastPing;
import net.vansen.fastserverpings.pipeline.status.Status;
import net.vansen.fastserverpings.pipeline.status.StatusType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Mixin(MultiplayerServerListPinger.class)
public abstract class ServerPingerMixin {
    @Unique
    private static final ThreadLocal<Boolean> INVOKE_GUARD = ThreadLocal.withInitial(() -> false); // Guard for invokeAdd, to avoid recursion

    @Unique
    // Map of servers that are being pinged currently to prevent duplicate pings to the same server
    // Causes less rate limiting when spamming refresh, and also doesn't stall for 10 seconds after spamming refresh
    private static final ConcurrentHashMap<String, CompletableFuture<Status>> ACTIVE_PINGS = new ConcurrentHashMap<>();

    @Unique
    private static final MethodHandle TRANSLATING_VERSION = fastping$findTranslatingVersionSetter();

    @Unique
    private static MethodHandle fastping$findTranslatingVersionSetter() {
        try {
            Class<?> protocolVersion = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            return MethodHandles.lookup().findVirtual(ServerInfo.class, "viaFabricPlus$setTranslatingVersion", MethodType.methodType(void.class, protocolVersion));
        } catch (Throwable e) {
            return null; // ViaFabricPlus is not present or outdated version
        }
    }

    @Unique
    private static void fastping$setTranslatingVersion(@NotNull ServerInfo entry) {
        if (TRANSLATING_VERSION == null) return;
        try {
            TRANSLATING_VERSION.invoke(entry, ViaFabricPlus.getImpl().getTargetVersion()); // ViaFabricPlus reads this when drawing its version tooltip, and normally sets it from the vanilla pinger we cancel
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static CompletableFuture<Status> pingWithRetry(
            @NotNull String host,
            int port,
            @SuppressWarnings("SameParameterValue") int attempts,
            @NotNull Runnable onRetry,
            @NotNull Consumer<Status> listener
    ) {
        return ACTIVE_PINGS.computeIfAbsent(host + ":" + port, k ->
                CompletableFuture.supplyAsync(() -> {
                    Throwable last = null;
                    for (int i = 0; i < attempts; i++) {
                        try {
                            return FastPing.ping(host, port, listener).join();
                        } catch (Throwable t) {
                            last = t;
                            onRetry.run();
                        }
                    }
                    throw new CompletionException(last);
                }, FastPing.pinger()).whenComplete((r, e) -> ACTIVE_PINGS.remove(k))
        );
    }

    @Unique
    private static List<Text> fastping$buildPlayerListSummary(@NotNull Status s) {
        List<Text> list = new ArrayList<>(s.sample().size() + 1);
        for (var p : s.sample()) {
            list.add(p.equals(MinecraftServer.ANONYMOUS_PLAYER_PROFILE)
                    ? Text.translatable("multiplayer.status.anonymous_player")
                    : Text.literal(p.name()));
        }
        if (s.sample().size() < s.online()) {
            list.add(Text.translatable("multiplayer.status.and_more", s.online() - s.sample().size()));
        }
        return list;
    }

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

        if (cached != null && FastPingCache.isFresh(cached)) { // SWR: stale-while-revalidate
            var s = cached.status();

            entry.label = s.motd();
            entry.ping = s.ping();

            if (s.playersPresent()) {
                entry.playerCountLabel = MultiplayerServerListPinger.createPlayerCountText(s.online(), s.max());

                entry.players = new ServerMetadata.Players(
                        s.max(),
                        s.online(),
                        s.sample()
                );
                if (!s.sample().isEmpty()) entry.playerListSummary = fastping$buildPlayerListSummary(s);
                else entry.playerListSummary = List.of();
            } else {
                entry.playerCountLabel = Text.translatable("multiplayer.status.unknown").formatted(Formatting.DARK_GRAY);
            }

            if (s.version().isEmpty()) {
                entry.version = Text.translatable("multiplayer.status.old");
                entry.protocolVersion = 0;
            } else {
                entry.version = Text.literal(s.version());
                try {
                    int protocol = ViaFabricPlus.getImpl().getTargetVersion().getVersion();
                    // To prevent minecraft showing "Outdated Server" for servers that are actually compatible with the client version
                    if (protocol == s.protocol()) entry.protocolVersion = MinecraftVersion.create().protocolVersion();
                    else entry.protocolVersion = s.protocol();
                } catch (Throwable t) {
                    entry.protocolVersion = s.protocol();
                }
            }
            fastping$setTranslatingVersion(entry);
            if (s.favicon() != null) {
                entry.setFavicon(ServerInfo.validateFavicon(s.favicon().iconBytes()));
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
            }, s -> {
                if (s.type() == StatusType.EARLY) {
                    entry.label = s.motd();

                    if (s.playersPresent()) {
                        entry.playerCountLabel = MultiplayerServerListPinger.createPlayerCountText(s.online(), s.max());

                        entry.players = new ServerMetadata.Players(
                                s.max(),
                                s.online(),
                                s.sample()
                        );
                        if (!s.sample().isEmpty()) entry.playerListSummary = fastping$buildPlayerListSummary(s);
                        else entry.playerListSummary = List.of();
                    } else {
                        entry.playerCountLabel = Text.translatable("multiplayer.status.unknown").formatted(Formatting.DARK_GRAY);
                    }

                    if (s.version().isEmpty()) {
                        entry.version = Text.translatable("multiplayer.status.old");
                        entry.protocolVersion = 0;
                    } else {
                        entry.version = Text.literal(s.version());
                        try {
                            int protocol = ViaFabricPlus.getImpl().getTargetVersion().getVersion();
                            // To prevent minecraft showing "Outdated Server" for servers that are actually compatible with the client version
                            if (protocol == s.protocol())
                                entry.protocolVersion = MinecraftVersion.create().protocolVersion();
                            else entry.protocolVersion = s.protocol();
                        } catch (Throwable t) {
                            entry.protocolVersion = s.protocol();
                        }
                    }
                    fastping$setTranslatingVersion(entry);
                    if (s.favicon() != null) {
                        entry.setFavicon(ServerInfo.validateFavicon(s.favicon().iconBytes()));
                    }

                    pingCallback.run();
                } else if (s.type() == StatusType.COMPLETE) {
                    FastPingCache.put(key, s);
                    entry.ping = s.ping();

                    PingAvgMetrics.end(startNs);
                    pingCallback.run();
                }
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
}
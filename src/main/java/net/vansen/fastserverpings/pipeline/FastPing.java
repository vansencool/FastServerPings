package net.vansen.fastserverpings.pipeline;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.viaversion.viafabricplus.ViaFabricPlus;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import net.minecraft.DetectedVersion;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.players.NameAndId;
import net.vansen.fastserverpings.pipeline.srv.SrvResolver;
import net.vansen.fastserverpings.pipeline.status.Status;
import net.vansen.fastserverpings.pipeline.status.StatusType;
import net.vansen.fastserverpings.pipeline.utils.VarIntUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public final class FastPing {

    private static final EventLoopGroup GROUP = new NioEventLoopGroup(4);
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
    public static boolean DEBUG = false;

    private static void log(String s) {
        if (DEBUG) System.out.println("[FastPing] " + s);
    }

    /**
     * Two-phase ping. The listener fires with {@code EARLY} on packet 0, the future completes with {@code COMPLETE} on packet 1.
     */
    public static CompletableFuture<Status> ping(@NotNull String host, int port, @NotNull Consumer<Status> listener) {
        CompletableFuture<Status> future = new CompletableFuture<>();

        var resolved = SrvResolver.resolve(host, port);
        log("Resolved " + host + ":" + port + " -> " + resolved.host() + ":" + resolved.port());

        if (GROUP.isShutdown()) {
            log("Called ping after group shutdown.");
            return CompletableFuture.failedFuture(new IllegalStateException("EventLoopGroup has been shut down"));
        }
        Bootstrap b = new Bootstrap()
                .group(GROUP)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        log("initChannel");
                        ch.pipeline().addLast(new PingHandler(future, resolved.host(), resolved.port(), listener));
                    }
                });

        b.connect(InetSocketAddress.createUnresolved(resolved.host(), resolved.port())) // Connect to server
                .addListener((ChannelFutureListener) f -> {
                    if (!f.isSuccess()) {
                        log("Connect failed: " + f.cause());
                        future.completeExceptionally(f.cause());
                    } else {
                        log("Connected");
                    }
                });

        return future;
    }

    public static ThreadPoolExecutor pinger() {
        return PINGER;
    }

    public static EventLoopGroup eventLoopGroup() {
        return GROUP;
    }

    private static Status parse(@NotNull String json, long ping, @NotNull StatusType type) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        Component motd = parseMotd(root);

        JsonObject versionObj = root.getAsJsonObject("version");
        String version = versionObj != null && versionObj.has("name")
                ? versionObj.get("name").getAsString()
                : "";

        int protocol = versionObj != null && versionObj.has("protocol")
                ? versionObj.get("protocol").getAsInt()
                : 0;

        JsonObject players = root.getAsJsonObject("players");
        int online = players != null && players.has("online")
                ? players.get("online").getAsInt()
                : 0;

        int max = players != null && players.has("max")
                ? players.get("max").getAsInt()
                : 0;

        List<NameAndId> sample = new ArrayList<>();
        if (players != null && players.has("sample")) {
            for (JsonElement element : players.getAsJsonArray("sample")) {
                JsonObject playerObj = element.getAsJsonObject();
                sample.add(new NameAndId(
                        UUID.fromString(playerObj.has("id") ? playerObj.get("id").getAsString() : UUID.randomUUID().toString()),
                        playerObj.has("name") ? playerObj.get("name").getAsString() : "")
                );
            }
        }

        ServerStatus.Favicon favicon = null;
        if (root.has("favicon")) {
            favicon = ServerStatus.Favicon.CODEC
                    .parse(JsonOps.INSTANCE, root.get("favicon"))
                    .result()
                    .orElse(null);
        }

        return new Status(
                motd,
                online,
                max,
                version,
                protocol,
                ping,
                favicon,
                sample,
                players != null,
                type
        );
    }

    private static Component parseMotd(@NotNull JsonObject root) {
        try {
            JsonElement desc = root.get("description");
            if (desc == null) return Component.literal("");
            return ComponentSerialization.CODEC
                    .parse(JsonOps.INSTANCE, desc)
                    .result()
                    .orElse(Component.empty());
        } catch (Exception e) {
            log("MOTD parse failed");
            return Component.literal("");
        }
    }

    /**
     * Handler for managing the ping process, including sending handshake, status request,
     * handling responses, and calculating ping time.
     */
    private static final class PingHandler extends ChannelInboundHandlerAdapter {

        private final CompletableFuture<Status> future;
        private final String host;
        private final int port;
        private final Consumer<Status> listener;

        private final ByteBuf cumulation = Unpooled.buffer();
        private long pingStart;
        private String statusJson;

        private ScheduledFuture<?> timeout;

        PingHandler(@NotNull CompletableFuture<Status> future, @NotNull String host, int port, @NotNull Consumer<Status> listener) {
            this.future = future;
            this.host = host;
            this.port = port;
            this.listener = listener;
        }

        @Override
        public void channelActive(@NotNull ChannelHandlerContext ctx) {
            log("channelActive");
            //noinspection resource
            timeout = ctx.executor().schedule(() -> {
                if (!future.isDone()) {
                    future.completeExceptionally(new TimeoutException("Ping timeout"));
                    ctx.close();
                }
            }, 7, TimeUnit.SECONDS);

            ByteBuf handshake = handshakeBuf();
            ByteBuf statusReq = statusRequestBuf();

            log("Sending handshake (" + handshake.readableBytes() + " bytes)");
            ctx.write(handshake);

            log("Sending status request (" + statusReq.readableBytes() + " bytes)");
            ctx.writeAndFlush(statusReq);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            cumulation.writeBytes(in);
            in.release();

            while (true) {
                cumulation.markReaderIndex();
                try {
                    int packetLen = VarIntUtils.readVarInt(cumulation); // Read packet length
                    if (cumulation.readableBytes() < packetLen) {
                        cumulation.resetReaderIndex();
                        return;
                    }

                    ByteBuf packet = cumulation.readSlice(packetLen); // Read packet
                    handlePacket(ctx, packet);
                } catch (IndexOutOfBoundsException e) {
                    cumulation.resetReaderIndex();
                    return;
                }
            }
        }

        @Override
        public void channelInactive(@NotNull ChannelHandlerContext ctx) {
            if (!future.isDone()) {
                future.completeExceptionally(new IOException("Channel closed"));
            }
        }

        private void handlePacket(ChannelHandlerContext ctx, ByteBuf packet) {
            int id = VarIntUtils.readVarInt(packet); // Read packet id
            log("Received packet id=" + id);

            if (id == 0) {
                int len = VarIntUtils.readVarInt(packet); // Read JSON length
                byte[] arr = new byte[len];
                packet.readBytes(arr);
                statusJson = new String(arr, StandardCharsets.UTF_8);
                log("Status JSON received");

                listener.accept(parse(statusJson, -1L, StatusType.EARLY)); // Two-phase ping
                log("Early phase fired");

                pingStart = System.nanoTime();
                ctx.writeAndFlush(pingPacket(pingStart)); // Send ping packet
                log("Ping sent");
            } else if (id == 1) {
                packet.readLong(); // Read pong payload
                long ping = (System.nanoTime() - pingStart) / 1_000_000;
                log("Pong received: " + ping + "ms");

                Status s = parse(statusJson, ping, StatusType.COMPLETE);
                if (timeout != null) {
                    timeout.cancel(false);
                }
                listener.accept(s);
                future.complete(s);
                ctx.close();
            } else {
                log("Unknown packet id " + id);
            }
        }

        @Override
        public void exceptionCaught(@NotNull ChannelHandlerContext ctx, @Nullable Throwable cause) {
            log("Exception: " + cause);
            future.completeExceptionally(cause);
            ctx.close();
        }

        private ByteBuf handshakeBuf() {
            ByteBuf inner = Unpooled.buffer(); // Handshake packet
            VarIntUtils.writeVarInt(inner, 0);

            try {
                VarIntUtils.writeVarInt(inner, ViaFabricPlus.getImpl().getTargetVersion().getVersion()); // Compatibility with ViaFabricPlus if present
            } catch (Throwable e) {
                VarIntUtils.writeVarInt(inner, DetectedVersion.tryDetectVersion().protocolVersion()); // Protocol version
            }

            VarIntUtils.writeVarInt(inner, host.length());
            inner.writeCharSequence(host, StandardCharsets.UTF_8);
            inner.writeShort(port);
            VarIntUtils.writeVarInt(inner, 1);
            return frame(inner);
        }

        private ByteBuf statusRequestBuf() {
            ByteBuf inner = Unpooled.buffer();
            VarIntUtils.writeVarInt(inner, 0);
            return frame(inner);
        }

        private ByteBuf pingPacket(long time) {
            ByteBuf inner = Unpooled.buffer();
            VarIntUtils.writeVarInt(inner, 1);
            inner.writeLong(time);
            return frame(inner);
        }

        private ByteBuf frame(@NotNull ByteBuf inner) {
            ByteBuf out = Unpooled.buffer();
            VarIntUtils.writeVarInt(out, inner.readableBytes()); // Packet length
            out.writeBytes(inner);
            return out;
        }
    }
}
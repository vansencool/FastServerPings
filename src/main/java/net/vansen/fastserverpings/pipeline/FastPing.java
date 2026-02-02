package net.vansen.fastserverpings.pipeline;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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
import net.minecraft.MinecraftVersion;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.vansen.fastserverpings.pipeline.srv.SrvResolver;
import net.vansen.fastserverpings.pipeline.status.Status;
import net.vansen.fastserverpings.pipeline.utils.VarIntUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("deprecation")
public final class FastPing {

    public static boolean DEBUG = false;

    private static void log(String s) {
        if (DEBUG) System.out.println("[FastPing] " + s);
    }

    private static final EventLoopGroup GROUP = new NioEventLoopGroup(4); // Backwards compatibility with older Netty versions

    /**
     * Pings a Minecraft server at the given host and port, resolving SRV records if necessary.
     *
     * @param host the server host
     * @param port the server port
     * @return a CompletableFuture that will complete with the server status
     */
    public static CompletableFuture<Status> ping(@NotNull String host, int port) {
        CompletableFuture<Status> future = new CompletableFuture<>();

        var resolved = SrvResolver.resolve(host, port);
        log("Resolved " + host + ":" + port + " -> " + resolved.host() + ":" + resolved.port());

        Bootstrap b = new Bootstrap()
                .group(GROUP)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        log("initChannel");
                        ch.pipeline().addLast(new PingHandler(future, resolved.host(), resolved.port()));
                    }
                });

        b.connect(new InetSocketAddress(resolved.host(), resolved.port())) // Connect to server
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

    /**
     * Handler for managing the ping process, including sending handshake, status request,
     * handling responses, and calculating ping time.
     */
    private static final class PingHandler extends ChannelInboundHandlerAdapter {

        private final CompletableFuture<Status> future;
        private final String host;
        private final int port;

        private final ByteBuf cumulation = Unpooled.buffer();
        private long pingStart;
        private String statusJson;

        /**
         * @param future CompletableFuture to complete with the result
         * @param host the server host
         * @param port the server port
         */
        PingHandler(@Nullable CompletableFuture<Status> future, @NotNull String host, int port) {
            this.future = future;
            this.host = host;
            this.port = port;
        }

        @Override
        public void channelActive(@NotNull ChannelHandlerContext ctx) {
            log("channelActive");

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

        private void handlePacket(ChannelHandlerContext ctx, ByteBuf packet) {
            int id = VarIntUtils.readVarInt(packet); // Read packet id
            log("Received packet id=" + id);

            if (id == 0) {
                int len = VarIntUtils.readVarInt(packet); // Read JSON length
                byte[] arr = new byte[len];
                packet.readBytes(arr);
                statusJson = new String(arr, StandardCharsets.UTF_8);
                log("Status JSON received");

                pingStart = System.nanoTime();
                ctx.writeAndFlush(pingPacket(pingStart)); // Send ping packet
                log("Ping sent");
            }
            else if (id == 1) {
                packet.readLong(); // Read pong payload
                long ping = (System.nanoTime() - pingStart) / 1_000_000;
                log("Pong received: " + ping + "ms");

                Status s = parse(statusJson, ping);
                future.complete(s);
                ctx.close();
            }
            else {
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
            VarIntUtils.writeVarInt(inner, MinecraftVersion.create().protocolVersion()); // Protocol version
            VarIntUtils.writeVarInt(inner, host.length());
            inner.writeCharSequence(host, StandardCharsets.UTF_8);
            inner.writeShort(port);
            VarIntUtils.writeVarInt(inner, 1);
            return frame(inner); // Frame the packet
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
    private static Status parse(@NotNull String json, long ping) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        Text motd = parseMotd(root);

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

        ServerMetadata.Favicon favicon = null;
        if (root.has("favicon")) {
            favicon = ServerMetadata.Favicon.CODEC
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
                favicon
        );
    }

    private static Text parseMotd(@NotNull JsonObject root) {
        try {
            JsonElement desc = root.get("description");
            if (desc == null) return Text.literal("");
            return TextCodecs.CODEC
                    .parse(JsonOps.INSTANCE, desc)
                    .result()
                    .orElse(Text.empty());
        } catch (Exception e) {
            log("MOTD parse failed");
            return Text.literal("");
        }
    }
}
package net.vansen.fastserverpings.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.vansen.fastserverpings.metrics.PingAvgMetrics;
import net.vansen.fastserverpings.pipeline.FastPing;
import net.vansen.fastserverpings.servers.PresetServers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class FastPingCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("fastping")
                        .then(literal("addservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Add predefined servers ").formatted(Formatting.WHITE))
                                                    .append(Text.literal("[Confirm]").formatted(Formatting.GREEN, Formatting.BOLD)
                                                            .styled(s -> s
                                                                    .withClickEvent(new ClickEvent.RunCommand("/fastping addservers confirm"))
                                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                                            Text.literal("Click to add predefined servers").formatted(Formatting.GRAY)
                                                                    )))), false);
                                    return 1;
                                })
                                .then(literal("confirm")
                                        .executes(c -> {
                                            MinecraftClient.getInstance().execute(PresetServers::addServers);
                                            c.getSource().getPlayer().sendMessage(Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Servers added").formatted(Formatting.GREEN)), false);
                                            return 1;
                                        })))
                        .then(literal("debug")
                                .executes(c -> {
                                    FastPing.DEBUG = !FastPing.DEBUG;
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Debug mode is now ").formatted(Formatting.WHITE))
                                                    .append(Text.literal(FastPing.DEBUG ? "ENABLED" : "DISABLED").formatted(FastPing.DEBUG ? Formatting.GREEN : Formatting.RED)), false);
                                    return 1;
                                }))
                        .then(literal("removeservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Remove predefined servers ").formatted(Formatting.WHITE))
                                                    .append(Text.literal("[Confirm]").formatted(Formatting.RED, Formatting.BOLD)
                                                            .styled(s -> s
                                                                    .withClickEvent(new ClickEvent.RunCommand("/fastping removeservers confirm"))
                                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                                            Text.literal("Click to remove predefined servers").formatted(Formatting.GRAY)
                                                                    )))), false);
                                    return 1;
                                })
                                .then(literal("confirm")
                                        .executes(c -> {
                                            MinecraftClient.getInstance().execute(PresetServers::removeServers);
                                            c.getSource().getPlayer().sendMessage(Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Servers removed").formatted(Formatting.RED)), false);
                                            return 1;
                                        })))
                        .then(literal("clearallservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Clear ALL servers ").formatted(Formatting.WHITE))
                                                    .append(Text.literal("[Confirm]").formatted(Formatting.DARK_RED, Formatting.BOLD)
                                                            .styled(s -> s
                                                                    .withClickEvent(new ClickEvent.RunCommand("/fastping clearallservers confirm"))
                                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                                            Text.literal("This will delete every server").formatted(Formatting.RED)
                                                                    )))), false);
                                    return 1;
                                })
                                .then(literal("confirm")
                                        .executes(c -> {
                                            MinecraftClient.getInstance().execute(() -> {
                                                ServerList list = new ServerList(MinecraftClient.getInstance());
                                                list.loadFile();
                                                for (int i = list.size() - 1; i >= 0; i--) list.remove(list.get(i));
                                                list.saveFile();
                                            });
                                            c.getSource().getPlayer().sendMessage(Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("All servers cleared").formatted(Formatting.DARK_RED)), false);
                                            return 1;
                                        })))
                        .then(literal("togglefastping")
                                .executes(c -> {
                                    if (PingAvgMetrics.BENCHMARKING) {
                                        c.getSource().getPlayer().sendMessage(
                                                Text.literal("[PingAvgMetrics] ").formatted(Formatting.LIGHT_PURPLE)
                                                        .append(Text.literal("Cannot toggle while a benchmark is running").formatted(Formatting.RED)), false);
                                        return 1;
                                    }
                                    PingAvgMetrics.toggleEnable();
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[PingAvgMetrics] ").formatted(Formatting.LIGHT_PURPLE)
                                                    .append(Text.literal("Now using ").formatted(Formatting.WHITE))
                                                    .append(Text.literal(PingAvgMetrics.USE_FASTPING ? "FastPing" : "Vanilla").formatted(PingAvgMetrics.USE_FASTPING ? Formatting.GREEN : Formatting.YELLOW))
                                                    .append(Text.literal(" for server pinging").formatted(Formatting.WHITE)), false);
                                    return 1;
                                }))
                        .then(literal("benchmark")
                                .then(argument("count", IntegerArgumentType.integer(1, PresetServers.SERVERS.length))
                                        .executes(c -> {
                                            var player = c.getSource().getPlayer();

                                            if (PingAvgMetrics.BENCHMARKING) {
                                                player.sendMessage(Text.literal("[Benchmark] ").formatted(Formatting.LIGHT_PURPLE)
                                                        .append(Text.literal("A benchmark is already running").formatted(Formatting.RED)), false);
                                                return 1;
                                            }

                                            int total = IntegerArgumentType.getInteger(c, "count");
                                            boolean fastping = PingAvgMetrics.USE_FASTPING;
                                            PingAvgMetrics.BENCHMARKING = true;

                                            player.sendMessage(Text.literal("[Benchmark] ").formatted(Formatting.LIGHT_PURPLE)
                                                    .append(Text.literal("Pinging " + total + " servers using ").formatted(Formatting.WHITE))
                                                    .append(Text.literal(fastping ? "FastPing" : "Vanilla").formatted(fastping ? Formatting.GREEN : Formatting.YELLOW)), false);

                                            AtomicInteger done = new AtomicInteger();
                                            AtomicInteger failed = new AtomicInteger();
                                            AtomicBoolean reported = new AtomicBoolean();
                                            long startNs = System.nanoTime();

                                            Runnable report = () -> {
                                                if (!reported.compareAndSet(false, true)) return;
                                                long wallMs = (System.nanoTime() - startNs) / 1_000_000;
                                                int finished = done.get();
                                                PingAvgMetrics.BENCHMARKING = false;
                                                player.sendMessage(Text.literal("[Benchmark] ").formatted(Formatting.LIGHT_PURPLE)
                                                        .append(Text.literal(fastping ? "FastPing" : "Vanilla").formatted(fastping ? Formatting.GREEN : Formatting.YELLOW))
                                                        .append(Text.literal(" pinged " + finished + "/" + total + " servers in ").formatted(Formatting.WHITE))
                                                        .append(Text.literal(wallMs + " ms").formatted(Formatting.AQUA))
                                                        .append(Text.literal(" (" + failed.get() + " failed)").formatted(Formatting.GRAY)), false);
                                            };

                                            FastPing.eventLoopGroup().schedule(report, 60, TimeUnit.SECONDS); // Never leave the benchmark wedged

                                            for (int i = 0; i < total; i++) {
                                                String[] s = PresetServers.SERVERS[i];
                                                FastPing.ping(s[0], Integer.parseInt(s[1]), st -> {
                                                }).orTimeout(5, TimeUnit.SECONDS).whenComplete((st, e) -> {
                                                    if (e != null) failed.incrementAndGet();
                                                    if (done.incrementAndGet() == total) report.run();
                                                });
                                            }
                                            return 1;
                                        })))
                        .then(literal("pingmetrics")
                                .executes(c -> {
                                    PingAvgMetrics.sendAndReset(c.getSource().getPlayer());
                                    return 1;
                                }))
        ));
    }
}

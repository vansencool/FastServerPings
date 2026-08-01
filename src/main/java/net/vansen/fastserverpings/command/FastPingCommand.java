package net.vansen.fastserverpings.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.vansen.fastserverpings.metrics.PingAvgMetrics;
import net.vansen.fastserverpings.pipeline.FastPing;
import net.vansen.fastserverpings.servers.PresetServers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class FastPingCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("fastping")
                        .then(literal("addservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendSystemMessage(
                                            Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Add predefined servers ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal("[Confirm]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                                            .withStyle(s -> s
                                                                    .withClickEvent(new ClickEvent.RunCommand("/fastping addservers confirm"))
                                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                                            Component.literal("Click to add predefined servers").withStyle(ChatFormatting.GRAY)
                                                                    ))))
                                    );
                                    return 1;
                                })
                                .then(literal("confirm")
                                        .executes(c -> {
                                            Minecraft.getInstance().execute(PresetServers::addServers);
                                            c.getSource().getPlayer().sendSystemMessage(Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Servers added").withStyle(ChatFormatting.GREEN)));
                                            return 1;
                                        })))
                        .then(literal("debug")
                                .executes(c -> {
                                    FastPing.DEBUG = !FastPing.DEBUG;
                                    c.getSource().getPlayer().sendSystemMessage(
                                            Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Debug mode is now ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal(FastPing.DEBUG ? "ENABLED" : "DISABLED").withStyle(FastPing.DEBUG ? ChatFormatting.GREEN : ChatFormatting.RED))
                                    );
                                    return 1;
                                }))
                        .then(literal("removeservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendSystemMessage(
                                            Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Remove predefined servers ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal("[Confirm]").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                                            .withStyle(s -> s
                                                                    .withClickEvent(new ClickEvent.RunCommand("/fastping removeservers confirm"))
                                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                                            Component.literal("Click to remove predefined servers").withStyle(ChatFormatting.GRAY)
                                                                    ))))
                                    );
                                    return 1;
                                })
                                .then(literal("confirm")
                                        .executes(c -> {
                                            Minecraft.getInstance().execute(PresetServers::removeServers);
                                            c.getSource().getPlayer().sendSystemMessage(Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Servers removed").withStyle(ChatFormatting.RED)));
                                            return 1;
                                        })))
                        .then(literal("clearallservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendSystemMessage(
                                            Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Clear ALL servers ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal("[Confirm]").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                                                            .withStyle(s -> s
                                                                    .withClickEvent(new ClickEvent.RunCommand("/fastping clearallservers confirm"))
                                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                                            Component.literal("This will delete every server").withStyle(ChatFormatting.RED)
                                                                    ))))
                                    );
                                    return 1;
                                })
                                .then(literal("confirm")
                                        .executes(c -> {
                                            Minecraft.getInstance().execute(() -> {
                                                ServerList list = new ServerList(Minecraft.getInstance());
                                                list.load();
                                                for (int i = list.size() - 1; i >= 0; i--) list.remove(list.get(i));
                                                list.save();
                                            });
                                            c.getSource().getPlayer().sendSystemMessage(Component.literal("[FastPing] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("All servers cleared").withStyle(ChatFormatting.DARK_RED)));
                                            return 1;
                                        })))
                        .then(literal("togglefastping")
                                .executes(c -> {
                                    if (PingAvgMetrics.BENCHMARKING) {
                                        c.getSource().getPlayer().sendSystemMessage(
                                                Component.literal("[PingAvgMetrics] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                                        .append(Component.literal("Cannot toggle while a benchmark is running").withStyle(ChatFormatting.RED)));
                                        return 1;
                                    }
                                    PingAvgMetrics.toggleEnable();
                                    c.getSource().getPlayer().sendSystemMessage(
                                            Component.literal("[PingAvgMetrics] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                                    .append(Component.literal("Now using ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal(PingAvgMetrics.USE_FASTPING ? "FastPing" : "Vanilla").withStyle(PingAvgMetrics.USE_FASTPING ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                                                    .append(Component.literal(" for server pinging").withStyle(ChatFormatting.WHITE))
                                    );
                                    return 1;
                                }))
                        .then(literal("benchmark")
                                .then(argument("count", IntegerArgumentType.integer(1, PresetServers.SERVERS.length))
                                        .executes(c -> {
                                            var player = c.getSource().getPlayer();

                                            if (PingAvgMetrics.BENCHMARKING) {
                                                player.sendSystemMessage(Component.literal("[Benchmark] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                                        .append(Component.literal("A benchmark is already running").withStyle(ChatFormatting.RED)));
                                                return 1;
                                            }

                                            int total = IntegerArgumentType.getInteger(c, "count");
                                            boolean fastping = PingAvgMetrics.USE_FASTPING;
                                            PingAvgMetrics.BENCHMARKING = true;

                                            player.sendSystemMessage(Component.literal("[Benchmark] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                                    .append(Component.literal("Pinging " + total + " servers using ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal(fastping ? "FastPing" : "Vanilla").withStyle(fastping ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));

                                            AtomicInteger done = new AtomicInteger();
                                            AtomicInteger failed = new AtomicInteger();
                                            AtomicBoolean reported = new AtomicBoolean();
                                            long startNs = System.nanoTime();

                                            Runnable report = () -> {
                                                if (!reported.compareAndSet(false, true)) return;
                                                long wallMs = (System.nanoTime() - startNs) / 1_000_000;
                                                int finished = done.get();
                                                PingAvgMetrics.BENCHMARKING = false;
                                                player.sendSystemMessage(Component.literal("[Benchmark] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                                        .append(Component.literal(fastping ? "FastPing" : "Vanilla").withStyle(fastping ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                                                        .append(Component.literal(" pinged " + finished + "/" + total + " servers in ").withStyle(ChatFormatting.WHITE))
                                                        .append(Component.literal(wallMs + " ms").withStyle(ChatFormatting.AQUA))
                                                        .append(Component.literal(" (" + failed.get() + " failed)").withStyle(ChatFormatting.GRAY)));
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

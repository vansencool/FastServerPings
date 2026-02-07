package net.vansen.fastserverpings;

import net.fabricmc.api.ClientModInitializer;
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

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class FastServerPings implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("fastping")
                        .then(literal("addservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Add predefined servers ").formatted(Formatting.WHITE))
                                                    .append(Text.literal("[Confirm]").formatted(Formatting.GREEN, Formatting.BOLD)
                                                            .styled(s -> s
                                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fastping addservers confirm"))
                                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                            Text.literal("Click to add predefined servers").formatted(Formatting.GRAY)
                                                                    )))),
                                            false
                                    );
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
                                                    .append(Text.literal(FastPing.DEBUG ? "ENABLED" : "DISABLED").formatted(FastPing.DEBUG ? Formatting.GREEN : Formatting.RED)),
                                            false
                                    );
                                    return 1;
                                }))
                        .then(literal("removeservers")
                                .executes(c -> {
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[FastPing] ").formatted(Formatting.AQUA)
                                                    .append(Text.literal("Remove predefined servers ").formatted(Formatting.WHITE))
                                                    .append(Text.literal("[Confirm]").formatted(Formatting.RED, Formatting.BOLD)
                                                            .styled(s -> s
                                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fastping removeservers confirm"))
                                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                            Text.literal("Click to remove predefined servers").formatted(Formatting.GRAY)
                                                                    )))),
                                            false
                                    );
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
                                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fastping clearallservers confirm"))
                                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                            Text.literal("This will delete every server").formatted(Formatting.RED)
                                                                    )))),
                                            false
                                    );
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
                                    PingAvgMetrics.toggleEnable();
                                    c.getSource().getPlayer().sendMessage(
                                            Text.literal("[PingAvgMetrics] ").formatted(Formatting.LIGHT_PURPLE)
                                                    .append(Text.literal("Now using ").formatted(Formatting.WHITE))
                                                    .append(Text.literal(PingAvgMetrics.USE_FASTPING ? "FastPing" : "Vanilla").formatted(PingAvgMetrics.USE_FASTPING ? Formatting.GREEN : Formatting.YELLOW))
                                                    .append(Text.literal(" for server pinging").formatted(Formatting.WHITE)),
                                            false
                                    );
                                    return 1;
                                }))
                        .then(literal("pingmetrics")
                                .executes(c -> {
                                    PingAvgMetrics.sendAndReset(c.getSource().getPlayer());
                                    return 1;
                                }))
                        .then(literal("help")
                                .executes(c -> {
                                    var p = c.getSource().getPlayer();
                                    p.sendMessage(Text.literal("/fastping addservers").formatted(Formatting.GRAY), false);
                                    p.sendMessage(Text.literal("/fastping removeservers").formatted(Formatting.GRAY), false);
                                    p.sendMessage(Text.literal("/fastping clearallservers").formatted(Formatting.GRAY), false);
                                    p.sendMessage(Text.literal("/fastping togglefastping").formatted(Formatting.GRAY), false);
                                    p.sendMessage(Text.literal("/fastping pingmetrics").formatted(Formatting.GRAY), false);
                                    return 1;
                                }))
        ));

    }
}

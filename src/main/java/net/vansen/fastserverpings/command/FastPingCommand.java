package net.vansen.fastserverpings.command;

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
                                    PingAvgMetrics.toggleEnable();
                                    c.getSource().getPlayer().sendSystemMessage(
                                            Component.literal("[PingAvgMetrics] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                                    .append(Component.literal("Now using ").withStyle(ChatFormatting.WHITE))
                                                    .append(Component.literal(PingAvgMetrics.USE_FASTPING ? "FastPing" : "Vanilla").withStyle(PingAvgMetrics.USE_FASTPING ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                                                    .append(Component.literal(" for server pinging").withStyle(ChatFormatting.WHITE))
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
                                    p.sendSystemMessage(Component.literal("/fastping addservers").withStyle(ChatFormatting.GRAY));
                                    p.sendSystemMessage(Component.literal("/fastping removeservers").withStyle(ChatFormatting.GRAY));
                                    p.sendSystemMessage(Component.literal("/fastping clearallservers").withStyle(ChatFormatting.GRAY));
                                    p.sendSystemMessage(Component.literal("/fastping togglefastping").withStyle(ChatFormatting.GRAY));
                                    p.sendSystemMessage(Component.literal("/fastping pingmetrics").withStyle(ChatFormatting.GRAY));
                                    return 1;
                                }))
        ));
    }
}

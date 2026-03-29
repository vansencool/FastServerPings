package net.vansen.fastserverpings.pipeline.status;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A record representing the status of a Minecraft server.
 *
 * @param motd           the message of the day (MOTD) of the server
 * @param online         the number of online players
 * @param max            the maximum number of players
 * @param version        the version name of the server
 * @param protocol       the protocol version of the server
 * @param ping           the ping time to the server in milliseconds
 * @param favicon        the server's favicon
 * @param sample         the sample player list shown on hover
 * @param playersPresent whether the players field was present in the server response
 */
public record Status(
        @NotNull Component motd,
        int online,
        int max,
        @NotNull String version,
        int protocol,
        long ping,
        @Nullable ServerStatus.Favicon favicon,
        @NotNull List<NameAndId> sample,
        boolean playersPresent
) {
}

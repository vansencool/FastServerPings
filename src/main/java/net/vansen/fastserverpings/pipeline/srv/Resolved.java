package net.vansen.fastserverpings.pipeline.srv;

import org.jetbrains.annotations.NotNull;

public record Resolved(@NotNull String host, int port) {
}

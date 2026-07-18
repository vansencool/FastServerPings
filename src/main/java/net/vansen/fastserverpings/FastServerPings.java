package net.vansen.fastserverpings;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.vansen.fastserverpings.pipeline.FastPing;

import java.util.concurrent.TimeUnit;

public class FastServerPings implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            // Optional command that relies on Fabric API. If Fabric API is not present, the command will simply not be registered.
            Class.forName("net.vansen.fastserverpings.command.FastPingCommand")
                    .getMethod("register")
                    .invoke(null);
        } catch (Throwable ignored) {
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            FastPing.eventLoopGroup()
                    .shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
                    .syncUninterruptibly();
            FastPing.pinger().shutdownNow();
        });
    }
}

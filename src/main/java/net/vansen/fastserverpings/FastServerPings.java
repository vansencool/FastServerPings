package net.vansen.fastserverpings;

import net.fabricmc.api.ClientModInitializer;

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
    }
}

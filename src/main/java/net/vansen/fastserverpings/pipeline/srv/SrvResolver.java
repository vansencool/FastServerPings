package net.vansen.fastserverpings.pipeline.srv;

import org.jetbrains.annotations.NotNull;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

public final class SrvResolver {

    public static final long TTL_MS = 300_000;

    private static final ConcurrentHashMap<String, Cached> CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves a hostname and port, applying Minecraft SRV record lookup when the port is 25565.
     *
     * @param host the hostname to resolve
     * @param port the port to use
     * @return the resolved hostname and port
     */
    public static Resolved resolve(@NotNull String host, int port) {
        if (port != 25565) {
            return new Resolved(host, port);
        }

        Cached hit = CACHE.get(host);
        if (hit != null && System.currentTimeMillis() - hit.timeMs() < TTL_MS) {
            return hit.resolved();
        }

        try {
            Attribute srv = new InitialDirContext(new Hashtable<>() {{
                put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory"); // Use the DNS service provider
            }}
            ).getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("SRV"); // Lookup the SRV record

            Resolved resolved;
            if (srv == null) {
                resolved = new Resolved(host, port);
            } else {
                String[] p = srv.get().toString().split(" ");
                String h = p[3];
                resolved = new Resolved(
                        h.charAt(h.length() - 1) == '.' ? h.substring(0, h.length() - 1) : h, // Trim trailing dot if present
                        Integer.parseInt(p[2]) // Port
                );
            }

            CACHE.put(host, new Cached(resolved, System.currentTimeMillis()));
            return resolved;
        } catch (Exception e) {
            return new Resolved(host, port);
        }
    }

    private record Cached(Resolved resolved, long timeMs) {
    }
}
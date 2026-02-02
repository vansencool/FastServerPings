package net.vansen.fastserverpings.pipeline.srv;

import org.jetbrains.annotations.NotNull;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

public final class SrvResolver {

    /**
     * Resolves a hostname and port, applying Minecraft SRV record lookup when the port is 25565.
     * <p>
     * If the port is {@code 25565}, this method attempts to resolve the {@code _minecraft._tcp}
     * SRV record for the given host. When present, the SRV target host and port are returned.
     * If no SRV record exists or resolution fails, the original host and port are used.
     *
     * @param host the hostname to resolve
     * @param port the port to use
     * @return the resolved hostname and port
     */
    public static Resolved resolve(@NotNull String host, int port) {
        if (port != 25565) {
            return new Resolved(host, port);
        }

        try {
            Attribute srv = new InitialDirContext(new Hashtable<>() {{
                put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory"); // Use the DNS service provider
            }}
            ).getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("SRV"); // Lookup the SRV record

            if (srv == null) {
                return new Resolved(host, port);
            }

            String[] p = srv.get().toString().split(" ");
            String h = p[3];
            return new Resolved(
                    h.charAt(h.length() - 1) == '.' ? h.substring(0, h.length() - 1) : h, // Trim trailing dot if present
                    Integer.parseInt(p[2]) // Port
            );
        } catch (Exception e) {
            return new Resolved(host, port);
        }
    }
}
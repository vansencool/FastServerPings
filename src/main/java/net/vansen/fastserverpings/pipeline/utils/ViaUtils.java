package net.vansen.fastserverpings.pipeline.utils;

import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Utility class for accessing ViaFabricPlus through reflection, so the same jar works with multiple ViaFabricPlus versions.
 */
public class ViaUtils {
    private static final MethodHandle TARGET_VERSION = findTargetVersion();
    private static final MethodHandle PROTOCOL = findProtocol();
    private static final MethodHandle TRANSLATING_VERSION = findTranslatingVersionSetter();

    private static MethodHandle findTranslatingVersionSetter() {
        try {
            Class<?> protocolVersion = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            return MethodHandles.lookup().findVirtual(ServerData.class, "viaFabricPlus$setTranslatingVersion", MethodType.methodType(void.class, protocolVersion))
                    .asType(MethodType.methodType(void.class, ServerData.class, Object.class));
        } catch (Throwable e) {
            return null;
        }
    }

    private static MethodHandle findTargetVersion() {
        try {
            Class<?> via = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Class<?> protocolVersion = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle impl;
            MethodHandle target;
            try { // ViaFabricPlus newer API
                Class<?> api = Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusAPI");
                impl = lookup.findStatic(via, "api", MethodType.methodType(api));
                target = lookup.findVirtual(api, "targetVersion", MethodType.methodType(protocolVersion));
            } catch (Throwable e) { // ViaFabricPlus older API
                Class<?> base = Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase");
                impl = lookup.findStatic(via, "getImpl", MethodType.methodType(base));
                target = lookup.findVirtual(base, "getTargetVersion", MethodType.methodType(protocolVersion));
            }
            return MethodHandles.filterReturnValue(impl, target).asType(MethodType.methodType(Object.class));
        } catch (Throwable e) {
            return null;
        }
    }

    private static MethodHandle findProtocol() {
        try {
            Class<?> protocolVersion = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            return MethodHandles.publicLookup().findVirtual(protocolVersion, "getVersion", MethodType.methodType(int.class))
                    .asType(MethodType.methodType(int.class, Object.class));
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets ViaFabricPlus's target ProtocolVersion.
     *
     * @return the target ProtocolVersion
     * @throws IllegalStateException if ViaFabricPlus is not present
     */
    public static @NotNull Object targetVersion() throws Throwable {
        if (TARGET_VERSION == null) throw new IllegalStateException("ViaFabricPlus is not present");
        return (Object) TARGET_VERSION.invokeExact();
    }

    /**
     * Gets ViaFabricPlus's target protocol number.
     *
     * @return the target protocol number
     * @throws IllegalStateException if ViaFabricPlus is not present
     */
    public static int targetProtocol() throws Throwable {
        Object version = targetVersion();
        if (PROTOCOL == null) throw new IllegalStateException("ViaFabricPlus is not present");
        return (int) PROTOCOL.invokeExact(version);
    }

    /**
     * Sets ViaFabricPlus's translating version on the given server entry to the current target version.
     * ViaFabricPlus reads this when drawing its version tooltip, and normally sets it from the vanilla pinger we cancel.
     * Does nothing if ViaFabricPlus is not present.
     *
     * @param entry the server entry to update
     */
    public static void setTranslatingVersion(@NotNull ServerData entry) {
        if (TRANSLATING_VERSION == null) return;
        try {
            TRANSLATING_VERSION.invokeExact(entry, targetVersion());
        } catch (Throwable ignored) {
        }
    }
}

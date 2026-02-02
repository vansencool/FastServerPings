package net.vansen.fastserverpings.pipeline.utils;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for reading and writing VarInts to/from ByteBuf.
 */
public class VarIntUtils {

    /**
     * Writes a VarInt to the given ByteBuf.
     *
     * @param b the ByteBuf to write to
     * @param i the integer to write as a VarInt
     */
    public static void writeVarInt(@NotNull ByteBuf b, int i) {
        while ((i & -128) != 0) {
            b.writeByte(i & 127 | 128);
            i >>>= 7;
        }
        b.writeByte(i);
    }

    /**
     * Reads a VarInt from the given ByteBuf.
     *
     * @param b the ByteBuf to read from
     * @return the integer read as a VarInt
     */
    public static int readVarInt(@NotNull ByteBuf b) {
        int n = 0, s = 0;
        byte c;
        do {
            c = b.readByte();
            n |= (c & 127) << s;
            s += 7;
        } while ((c & 128) != 0);
        return n;
    }
}

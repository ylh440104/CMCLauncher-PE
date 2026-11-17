package org.jackhuang.hmcl.util;

import java.io.IOException;

public final class Hex {

    private static final byte[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static int toDigit(char ch) throws IOException {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }

        throw new IOException("Illegal hexadecimal character " + ch);
    }

    public static byte[] decodeHex(String str) throws IOException {
        if ((str.length() & 0x1) != 0)
            throw new IOException("Odd number of characters.");

        int len = str.length() >> 1;
        byte[] out = new byte[len];

        for (int i = 0; i < len; i++) {
            int j = i << 1;
            int f = (toDigit(str.charAt(j)) << 4) | toDigit(str.charAt(j + 1));

            out[i] = (byte) f;
        }

        return out;
    }

    @SuppressWarnings("deprecation")
    public static String encodeHex(byte[] data) {
        int l = data.length;
        byte[] out = new byte[l << 1];

        for (int i = 0; i < l; i++) {
            byte b = data[i];

            int j = i << 1;
            out[j] = DIGITS_LOWER[((0xF0 & b) >>> 4)];
            out[j + 1] = DIGITS_LOWER[(0xF & b)];
        }
        return new String(out, 0, 0, out.length);
    }

    private Hex() {
    }
}

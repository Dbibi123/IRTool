package com.example.irtool;

public class IRUtils {
    public static int[] buildNecPattern(byte[] data, int frequency) {
        int[] header = {9000, 4500};
        int[] bits = new int[data.length * 8 * 2 + 1];
        int index = 0;
        for (byte b : data) {
            for (int i = 0; i < 8; i++) {          // LSB First
                boolean bit = ((b >> i) & 1) == 1;
                bits[index++] = 560;
                bits[index++] = bit ? 1690 : 560;
            }
        }
        bits[index] = 560;
        int[] pattern = new int[header.length + bits.length];
        System.arraycopy(header, 0, pattern, 0, header.length);
        System.arraycopy(bits, 0, pattern, header.length, bits.length);
        return pattern;
    }
}

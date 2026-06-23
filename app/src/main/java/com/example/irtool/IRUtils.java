package com.example.irtool;

public class IRUtils {
    /**
     * 构建 NEC 协议的波形（LSB First）
     * @param data 字节数组，按发送顺序：[地址低, 地址高, 命令, 命令反码]
     * @param frequency 载波频率（Hz），固定 38000
     * @return 微秒级脉冲交替数组
     */
    public static int[] buildNecPattern(byte[] data, int frequency) {
        int[] header = {9000, 4500};  // 引导码
        int[] bits = new int[data.length * 8 * 2 + 1];  // 每字节8位，每位2个边沿，末尾1个停止位
        int index = 0;

        for (byte b : data) {
            for (int i = 0; i < 8; i++) {      // LSB First: 从 bit0 到 bit7
                boolean bit = ((b >> i) & 1) == 1;
                bits[index++] = 560;             // 载波脉冲
                bits[index++] = bit ? 1690 : 560; // 间隔
            }
        }
        bits[index] = 560;  // 停止位（最后一个载波脉冲）

        int[] pattern = new int[header.length + bits.length];
        System.arraycopy(header, 0, pattern, 0, header.length);
        System.arraycopy(bits, 0, pattern, header.length, bits.length);
        return pattern;
    }
}

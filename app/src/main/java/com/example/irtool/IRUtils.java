package com.example.irtool;

public class IRUtils {
    /**
     * 构建自定义4字节红外码的发射波形（NEC-like）
     * @param data 4字节数组，顺序[互补码, 码值, 头码高, 头码低] 对应发送顺序
     * @param frequency 载波频率（Hz），通常 38000
     * @return 微秒级脉冲交替数组（第一个为载波时长，第二个为间隔，...）
     */
    public static int[] buildCustomPattern(byte[] data, int frequency) {
        // 引导码：9ms 载波，4.5ms 无载波
        int[] header = {9000, 4500};
        // 数据部分：每字节8位，每位2个边沿，最后一位后跟1个停止位(560us 载波)
        int[] bits = new int[4 * 8 * 2 + 1];
        int index = 0;
        for (byte b : data) {
            for (int i = 7; i >= 0; i--) {
                boolean bit = ((b >> i) & 1) == 1;
                bits[index++] = 560; // 载波脉冲
                bits[index++] = bit ? 1690 : 560; // 间隔
            }
        }
        bits[index] = 560; // 停止位

        // 合并头部和数据
        int[] pattern = new int[header.length + bits.length];
        System.arraycopy(header, 0, pattern, 0, header.length);
        System.arraycopy(bits, 0, pattern, header.length, bits.length);
        return pattern;
    }
}

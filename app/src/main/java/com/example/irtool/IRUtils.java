package com.example.irtool;

public class IRUtils {
    // 构建 NEC 协议波形 (单位微秒)
    public static int[] buildNECPattern(int address, int command) {
        int[] pattern = new int[2 + 64 + 1]; // 引导码 + 32位数据（每比特两个边沿） + 结尾burst
        int index = 0;
        // 引导码：9ms 低(38k载波) + 4.5ms 高(无载波)
        pattern[index++] = 9000;
        pattern[index++] = 4500;
        long data = ((long)(address & 0xFF) << 24) | ((long)((~address) & 0xFF) << 16)
                  | ((command & 0xFF) << 8) | ((~command) & 0xFF);
        for (int i = 31; i >= 0; i--) {
            if ((data & (1L << i)) != 0) {
                pattern[index++] = 560;   // 载波脉冲
                pattern[index++] = 1690;  // 间隔
            } else {
                pattern[index++] = 560;
                pattern[index++] = 560;
            }
        }
        pattern[index] = 560; // 停止位
        return pattern;
    }
}

package com.example.irtool;

import java.util.List;

public class IRDecoder {
    public static class DecodedSignal {
        public String protocol;
        public int fullCode;   // 完整的4字节整数（BE414040）
        public int head;       // 头码（后2字节，如0x4040）
        public int command;    // 码值（第2字节，如0x41）
        public int complement; // 互补码（第1字节，如0xBE）

        @Override
        public String toString() {
            return "完整码: 0x" + String.format("%08X", fullCode).toUpperCase() + "\n"
                 + "头码: 0x" + String.format("%04X", head).toUpperCase() + "\n"
                 + "码值: 0x" + String.format("%02X", command).toUpperCase() + "\n"
                 + "互补码: 0x" + String.format("%02X", complement).toUpperCase() + "\n"
                 + (protocol != null ? "协议: " + protocol : "");
        }
    }

    /**
     * 解码自定义4字节红外波形
     */
    public static DecodedSignal decode(List<Integer> timings) {
        if (timings.size() < 68) return null; // 至少引导码+64边沿+停止位

        // 检查引导码：~9000us低（载波）, ~4500us高（空闲）
        int leadLow = timings.get(0);
        int leadHigh = timings.get(1);
        if (leadLow < 8000 || leadLow > 10000 || leadHigh < 4000 || leadHigh > 5000) {
            return null;
        }

        StringBuilder bits = new StringBuilder();
        for (int i = 2; i < timings.size() - 1; i += 2) {
            int space = timings.get(i);   // 载波长度（通常560us）
            int mark = timings.get(i + 1); // 间隔长度
            if (space > 2000 || mark > 3000) break; // 异常数据
            if (mark > 1000) {
                bits.append('1');
            } else if (mark > 200) {
                bits.append('0');
            }
            if (bits.length() >= 32) break;
        }

        if (bits.length() != 32) return null;

        // 组成4字节，顺序：发送顺序（互补码、码值、头码高、头码低）
        byte[] data = new byte[4];
        for (int i = 0; i < 4; i++) {
            data[i] = (byte) Integer.parseInt(bits.substring(i * 8, (i + 1) * 8), 2);
        }

        // 按用户格式解析：互补码 = data[0], 码值 = data[1], 头码 = (data[2] << 8) | data[3]
        int complement = data[0] & 0xFF;
        int command = data[1] & 0xFF;
        int head = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int fullCode = (complement << 24) | (command << 16) | (head & 0xFFFF); // 其实前面就是 fullCode = 四个字节组合

        DecodedSignal sig = new DecodedSignal();
        sig.protocol = "Custom 4-Byte";
        sig.fullCode = (complement << 24) | (command << 16) | (head & 0xFFFF);
        sig.head = head;
        sig.command = command;
        sig.complement = complement;
        return sig;
    }
}

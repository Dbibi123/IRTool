package com.example.irtool;

import java.util.List;

public class IRDecoder {
    public static class DecodedSignal {
        public String protocol;
        public int address;
        public int command;
        public String details;

        @Override
        public String toString() {
            return "协议: " + protocol + "\n"
                 + "地址: 0x" + Integer.toHexString(address).toUpperCase()
                 + " (" + address + ")\n"
                 + "命令: 0x" + Integer.toHexString(command).toUpperCase()
                 + " (" + command + ")\n"
                 + "附加: " + details;
        }
    }

    // 简单的NEC解码，timings为高低电平交替，第一个值一般是引导码低电平时间
    public static DecodedSignal decode(List<Integer> timings) {
        if (timings.size() < 66) return null; // NEC至少66个边沿

        // 期望引导码: 9000us低, 4500us高
        int lowLead = timings.get(0);
        int highLead = timings.get(1);
        if (lowLead > 8000 && lowLead < 10000 && highLead > 4000 && highLead < 5000) {
            // 可能是NEC
            StringBuilder bits = new StringBuilder();
            for (int i = 2; i < timings.size() - 1; i += 2) {
                int space = timings.get(i);   // 低电平时间（burst），NEC里固定560us左右的低脉冲
                int mark = timings.get(i + 1); // 高电平时间（间隔）
                // 典型值: 逻辑0 560us low + 560us high, 逻辑1 560us low + 1690us high
                if (mark > 1000) {
                    bits.append('1');
                } else if (mark > 200) {
                    bits.append('0');
                }
                if (bits.length() >= 32) break;
            }
            if (bits.length() == 32) {
                String addrStr = bits.substring(0, 8);   // 地址
                String addrInvStr = bits.substring(8, 16); // 地址反码
                String cmdStr = bits.substring(16, 24);  // 命令
                String cmdInvStr = bits.substring(24, 32); // 命令反码
                int address = Integer.parseInt(addrStr, 2);
                int addrInv = Integer.parseInt(addrInvStr, 2);
                int command = Integer.parseInt(cmdStr, 2);
                int cmdInv = Integer.parseInt(cmdInvStr, 2);
                if ((address ^ addrInv) == 0xFF && (command ^ cmdInv) == 0xFF) {
                    DecodedSignal sig = new DecodedSignal();
                    sig.protocol = "NEC";
                    sig.address = address;
                    sig.command = command;
                    sig.details = "地址反码校验正确";
                    return sig;
                } else {
                    DecodedSignal sig = new DecodedSignal();
                    sig.protocol = "NEC (扩展)";
                    sig.address = address;
                    sig.command = command;
                    sig.details = "校验失败, 原始地址:" + addrStr + " 命令:" + cmdStr;
                    return sig;
                }
            }
        }
        // 可扩展更多协议识别……
        return null;
    }
}

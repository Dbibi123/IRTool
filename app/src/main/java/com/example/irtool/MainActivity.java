package com.example.irtool;

import android.app.Activity;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText etHeadCode, etCommand;
    private TextView tvComplement, tvFullCode;
    private Button btnSend;
    private ConsumerIrManager irManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etHeadCode = findViewById(R.id.et_head_code);
        etCommand = findViewById(R.id.et_command);
        tvComplement = findViewById(R.id.tv_complement);
        tvFullCode = findViewById(R.id.tv_full_code);
        btnSend = findViewById(R.id.btn_send);

        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);

        TextWatcher codeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        };
        etHeadCode.addTextChangedListener(codeWatcher);
        etCommand.addTextChangedListener(codeWatcher);

        btnSend.setOnClickListener(v -> sendNecCode());
    }

    /** 解析输入（支持 0x 前缀或无前缀十六进制） */
    private int parseHex(String s) throws NumberFormatException {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Integer.decode(s);
        } else {
            return Integer.parseInt(s, 16);
        }
    }

    /** 实时预览完整码 */
    private void updatePreview() {
        try {
            int head = parseHex(etHeadCode.getText().toString());
            int command = parseHex(etCommand.getText().toString());
            int reversedHead = ((head & 0xFF) << 8) | ((head >> 8) & 0xFF); // 字节交换
            int complement = (~command) & 0xFF;
            // 完整码显示顺序：互补码 | 码值 | 头码高字节 | 头码低字节
            int fullCode = (complement << 24) | (command << 16) | (reversedHead & 0xFFFF);
            tvComplement.setText(String.format("互补码：0x%02X (自动计算)", complement));
            tvFullCode.setText(String.format("完整码：0x%08X", fullCode));
        } catch (NumberFormatException e) {
            tvComplement.setText("互补码：--");
            tvFullCode.setText("完整码：--");
        }
    }

    /** 按 NEC 协议发送 */
    private void sendNecCode() {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(this, "此设备不支持红外发射", Toast.LENGTH_LONG).show();
            return;
        }
        String headStr = etHeadCode.getText().toString().trim();
        String cmdStr = etCommand.getText().toString().trim();
        if (headStr.isEmpty() || cmdStr.isEmpty()) {
            Toast.makeText(this, "请输入头码和码值", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int head = parseHex(headStr);
            int command = parseHex(cmdStr);
            if (command < 0 || command > 0xFF) {
                Toast.makeText(this, "码值需在 0~FF 之间", Toast.LENGTH_SHORT).show();
                return;
            }

            int reversedHead = ((head & 0xFF) << 8) | ((head >> 8) & 0xFF); // 字节交换
            int complement = (~command) & 0xFF;

            // NEC 发送顺序：地址低字节、地址高字节、命令、命令反码
            byte[] data = new byte[] {
                (byte) (reversedHead & 0xFF),          // 地址低字节
                (byte) ((reversedHead >> 8) & 0xFF),   // 地址高字节
                (byte) command,                        // 命令
                (byte) complement                      // 命令反码
            };

            int[] pattern = IRUtils.buildNecPattern(data, 38000);
            irManager.transmit(38000, pattern);

            int fullCode = (complement << 24) | (command << 16) | (reversedHead & 0xFFFF);
            Toast.makeText(this, "已发送: 0x" + String.format("%08X", fullCode).toUpperCase(),
                    Toast.LENGTH_LONG).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "输入格式错误，请使用十六进制（如 41FB）", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

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

        // 实时监听输入更新预览
        TextWatcher codeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updatePreview();
            }
        };
        etHeadCode.addTextChangedListener(codeWatcher);
        etCommand.addTextChangedListener(codeWatcher);

        btnSend.setOnClickListener(v -> sendIRCode());
    }

    private void updatePreview() {
        try {
            int head = Integer.decode(etHeadCode.getText().toString().trim());
            int command = Integer.decode(etCommand.getText().toString().trim());
            // 头码字节反转
            int reversedHead = ((head & 0xFF) << 8) | ((head >> 8) & 0xFF);
            int complement = (~command) & 0xFF;
            int fullCode = (reversedHead << 16) | (complement << 8) | command;
            tvComplement.setText(String.format("互补码：0x%02X (自动计算)", complement));
            tvFullCode.setText(String.format("完整码：0x%08X", fullCode));
        } catch (NumberFormatException e) {
            tvComplement.setText("互补码：--");
            tvFullCode.setText("完整码：--");
        }
    }

    private void sendIRCode() {
        if (irManager == null) {
            Toast.makeText(this, "红外管理器不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!irManager.hasIrEmitter()) {
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
            int head = Integer.decode(headStr);
            int command = Integer.decode(cmdStr);
            if (command < 0 || command > 0xFF) {
                Toast.makeText(this, "码值需在 0~FF 之间", Toast.LENGTH_SHORT).show();
                return;
            }
            // 头码字节反转
            int reversedHead = ((head & 0xFF) << 8) | ((head >> 8) & 0xFF);
            int complement = (~command) & 0xFF;

            // 数据顺序：头码高字节、头码低字节、互补码、码值
            byte[] data = new byte[]{
                (byte) ((reversedHead >> 8) & 0xFF),
                (byte) (reversedHead & 0xFF),
                (byte) complement,
                (byte) command
            };

            int[] pattern = IRUtils.buildCustomPattern(data, 38000);
            irManager.transmit(38000, pattern);

            int fullCode = (reversedHead << 16) | (complement << 8) | command;
            Toast.makeText(this, "已发送: 0x" + String.format("%08X", fullCode).toUpperCase(), Toast.LENGTH_LONG).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "输入格式错误，请使用十六进制（如 0x609F）或十进制", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

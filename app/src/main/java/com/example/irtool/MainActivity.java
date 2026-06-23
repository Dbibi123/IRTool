package com.example.irtool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText etHeadCode, etCommand;
    private TextView tvComplement, tvFullCode;
    private Button btnSend;
    private Button[] btnPresets = new Button[5];
    private TextView tvLog;
    private ConsumerIrManager irManager;
    private SharedPreferences prefs;
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etHeadCode = findViewById(R.id.et_head_code);
        etCommand = findViewById(R.id.et_command);
        tvComplement = findViewById(R.id.tv_complement);
        tvFullCode = findViewById(R.id.tv_full_code);
        btnSend = findViewById(R.id.btn_send);
        btnPresets[0] = findViewById(R.id.btn_preset_0);
        btnPresets[1] = findViewById(R.id.btn_preset_1);
        btnPresets[2] = findViewById(R.id.btn_preset_2);
        btnPresets[3] = findViewById(R.id.btn_preset_3);
        btnPresets[4] = findViewById(R.id.btn_preset_4);
        tvLog = findViewById(R.id.tv_log);

        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        prefs = getSharedPreferences("ir_presets", Context.MODE_PRIVATE);

        // 手动输入实时预览
        TextWatcher codeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        };
        etHeadCode.addTextChangedListener(codeWatcher);
        etCommand.addTextChangedListener(codeWatcher);

        btnSend.setOnClickListener(v -> {
            int[] data = getCurrentCode();
            if (data != null) {
                sendNecCode(data[0], data[1]);
            }
        });

        // 初始化预设按钮
        for (int i = 0; i < 5; i++) {
            final int index = i;
            Button btn = btnPresets[i];
            // 从 SharedPreferences 读取名字和码值
            String name = prefs.getString("preset_name_" + index, "按键" + (index + 1));
            String code = prefs.getString("preset_code_" + index, "");
            updatePresetButton(index, name, code);

            // 短按：发送预设码值（使用当前输入框的头码）
            btn.setOnClickListener(v -> {
                String cmdHex = prefs.getString("preset_code_" + index, "");
                if (cmdHex.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请先长按设置此按键的码值", Toast.LENGTH_SHORT).show();
                    return;
                }
                int head;
                try {
                    head = parseHex(etHeadCode.getText().toString().trim());
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "头码输入错误", Toast.LENGTH_SHORT).show();
                    return;
                }
                int command;
                try {
                    command = parseHex(cmdHex);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "预设码值格式错误", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendNecCode(head, command);
            });

            // 长按：编辑名字和码值
            btn.setOnLongClickListener(v -> {
                showPresetEditDialog(index);
                return true; // 消费长按事件，不再触发 onClick
            });
        }
    }

    /** 获取当前输入框的头码和码值，解析失败返回 null */
    private int[] getCurrentCode() {
        try {
            int head = parseHex(etHeadCode.getText().toString().trim());
            int cmd = parseHex(etCommand.getText().toString().trim());
            if (cmd < 0 || cmd > 0xFF) {
                Toast.makeText(this, "码值需在 0~FF 之间", Toast.LENGTH_SHORT).show();
                return null;
            }
            return new int[]{head, cmd};
        } catch (NumberFormatException e) {
            Toast.makeText(this, "输入格式错误，请使用十六进制", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /** 解析十六进制字符串（支持 0x 前缀或无前缀） */
    private int parseHex(String s) throws NumberFormatException {
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Integer.decode(s);
        } else {
            return Integer.parseInt(s, 16);
        }
    }

    /** 更新手动输入下方的互补码和完整码预览 */
    private void updatePreview() {
        try {
            int head = parseHex(etHeadCode.getText().toString());
            int command = parseHex(etCommand.getText().toString());
            if (command < 0 || command > 0xFF) {
                tvComplement.setText("互补码：--");
                tvFullCode.setText("完整码：--");
                return;
            }
            int reversedHead = ((head & 0xFF) << 8) | ((head >> 8) & 0xFF);
            int complement = (~command) & 0xFF;
            int fullCode = (complement << 24) | (command << 16) | (reversedHead & 0xFFFF);
            tvComplement.setText(String.format("互补码：0x%02X (自动计算)", complement));
            tvFullCode.setText(String.format("完整码：0x%08X", fullCode));
        } catch (NumberFormatException e) {
            tvComplement.setText("互补码：--");
            tvFullCode.setText("完整码：--");
        }
    }

    /** NEC 协议发送 */
    private void sendNecCode(int head, int command) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(this, "此设备不支持红外发射", Toast.LENGTH_LONG).show();
            return;
        }
        int reversedHead = ((head & 0xFF) << 8) | ((head >> 8) & 0xFF);
        int complement = (~command) & 0xFF;

        byte[] data = new byte[]{
            (byte) (reversedHead & 0xFF),          // 地址低
            (byte) ((reversedHead >> 8) & 0xFF),   // 地址高
            (byte) command,
            (byte) complement
        };

        int[] pattern = IRUtils.buildNecPattern(data, 38000);
        irManager.transmit(38000, pattern);

        int fullCode = (complement << 24) | (command << 16) | (reversedHead & 0xFFFF);
        String msg = String.format("已发送: 0x%08X", fullCode);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        // 记录日志
        appendLog(fullCode, head, command);
    }

    /** 更新日志区并自动滚动 */
    private void appendLog(int fullCode, int head, int command) {
        String time = sdf.format(new Date());
        String line = String.format("%s  发送 0x%08X（头码:%04X 码值:%02X）\n",
                time, fullCode, head, command);
        tvLog.append(line);
        // 自动滚动到底部（日志区的 ScrollView）
        View parent = (View) tvLog.getParent();
        if (parent instanceof ScrollView) {
            ScrollView logScroll = (ScrollView) parent;
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** 更新预设按钮文字 */
    private void updatePresetButton(int index, String name, String code) {
        Button btn = btnPresets[index];
        if (code == null || code.isEmpty()) {
            btn.setText(name + "\n(未设码)");
        } else {
            btn.setText(name + "\n0x" + code.toUpperCase());
        }
    }

    /** 显示长按编辑对话框（纯代码生成，无需 XML） */
    private void showPresetEditDialog(final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑按键 " + (index + 1));

        // 动态创建布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText etName = new EditText(this);
        etName.setHint("按键名称（如：电源）");
        etName.setText(prefs.getString("preset_name_" + index, "按键" + (index + 1)));
        layout.addView(etName);

        final EditText etCode = new EditText(this);
        etCode.setHint("码值（十六进制，如：DB）");
        etCode.setText(prefs.getString("preset_code_" + index, ""));
        etCode.setTypeface(etCommand.getTypeface()); // monospace
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeParams.topMargin = 24;
        etCode.setLayoutParams(codeParams);
        layout.addView(etCode);

        builder.setView(layout);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();
            if (name.isEmpty()) name = "按键" + (index + 1);
            if (code.isEmpty()) {
                Toast.makeText(MainActivity.this, "码值留空，按键将不可用", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    int cmd = parseHex(code);
                    if (cmd < 0 || cmd > 0xFF) {
                        Toast.makeText(MainActivity.this, "码值需在 0~FF 之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "码值格式错误", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            prefs.edit().putString("preset_name_" + index, name)
                    .putString("preset_code_" + index, code)
                    .apply();
            updatePresetButton(index, name, code);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}

package com.example.irtool;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.ConsumerIrManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView tvResult;
    private EditText etHeadCode, etCommand;
    private TextView tvComplement, tvFullCode;
    private Button btnStart, btnStop, btnSend;
    private AudioRecord audioRecord;
    private volatile boolean isReceiving = false;
    private static final int SAMPLE_RATE = 44100;
    private static final int REQUEST_RECORD_AUDIO = 1;
    private ConsumerIrManager irManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tv_result);
        etHeadCode = findViewById(R.id.et_head_code);
        etCommand = findViewById(R.id.et_command);
        tvComplement = findViewById(R.id.tv_complement);
        tvFullCode = findViewById(R.id.tv_full_code);
        btnStart = findViewById(R.id.btn_start_recv);
        btnStop = findViewById(R.id.btn_stop_recv);
        btnSend = findViewById(R.id.btn_send);

        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }

        // 实时监听输入更新互补码和完整码
        TextWatcher codeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updatePreview();
            }
        };
        etHeadCode.addTextChangedListener(codeWatcher);
        etCommand.addTextChangedListener(codeWatcher);

        btnStart.setOnClickListener(v -> startReceiving());
        btnStop.setOnClickListener(v -> stopReceiving());
        btnSend.setOnClickListener(v -> sendCustomCode());
    }

    private void updatePreview() {
        try {
            int head = Integer.decode(etHeadCode.getText().toString().trim());
            int command = Integer.decode(etCommand.getText().toString().trim());
            int complement = (~command) & 0xFF;
            int fullCode = (complement << 24) | (command << 16) | (head & 0xFFFF);
            tvComplement.setText(String.format("互补码：0x%02X (自动计算)", complement));
            tvFullCode.setText(String.format("完整码：0x%08X", fullCode));
        } catch (NumberFormatException e) {
            tvComplement.setText("互补码：--");
            tvFullCode.setText("完整码：--");
        }
    }

    private void startReceiving() {
        if (isReceiving) return;
        if (audioRecord != null) audioRecord.release();
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "无法启动录音，请检查耳机孔外接接收头", Toast.LENGTH_LONG).show();
            return;
        }
        isReceiving = true;
        audioRecord.startRecording();
        new Thread(this::readAudioData).start();
        tvResult.setText("正在接收红外信号...\n");
    }

    private void stopReceiving() {
        isReceiving = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void readAudioData() {
        short[] buffer = new short[4096];
        List<Integer> timings = new ArrayList<>();
        int lastState = 0;
        int sampleCount = 0;
        int threshold = 500;

        while (isReceiving) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read < 0) break;
            for (int i = 0; i < read; i++) {
                int value = Math.abs(buffer[i]);
                int state = (value > threshold) ? 1 : 0;
                if (state == lastState) {
                    sampleCount++;
                } else {
                    if (sampleCount > 0) {
                        int durationUs = (int) (sampleCount * 1_000_000L / SAMPLE_RATE);
                        timings.add(durationUs);
                    }
                    lastState = state;
                    sampleCount = 1;
                }
            }
            if (timings.size() > 70) break;
        }

        final String result;
        if (timings.size() > 10) {
            IRDecoder.DecodedSignal decoded = IRDecoder.decode(timings);
            if (decoded != null) {
                result = decoded.toString();
            } else {
                result = "未能识别有效红外码\n原始脉冲数: " + timings.size();
            }
        } else {
            result = "未检测到足够红外脉冲";
        }
        runOnUiThread(() -> tvResult.setText(result));
    }

    private void sendCustomCode() {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(this, "此设备不支持红外发射", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int head = Integer.decode(etHeadCode.getText().toString().trim());
            int command = Integer.decode(etCommand.getText().toString().trim());
            int complement = (~command) & 0xFF;
            byte[] data = new byte[]{
                (byte) complement,
                (byte) command,
                (byte) ((head >> 8) & 0xFF),
                (byte) (head & 0xFF)
            };
            int[] pattern = IRUtils.buildCustomPattern(data, 38000);
            irManager.transmit(38000, pattern);
            int fullCode = (complement << 24) | (command << 16) | (head & 0xFFFF);
            Toast.makeText(this, "已发送: 0x" + String.format("%08X", fullCode).toUpperCase(), Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopReceiving();
    }
}

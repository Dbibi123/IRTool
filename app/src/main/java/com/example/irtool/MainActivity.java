package com.example.irtool;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.ConsumerIrManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView tvResult;
    private EditText etSendCode;
    private Button btnStart, btnStop, btnSend;
    private AudioRecord audioRecord;
    private volatile boolean isReceiving = false;
    private static final int SAMPLE_RATE = 44100; // 麦克风采样率
    private static final int REQUEST_RECORD_AUDIO = 1;
    private ConsumerIrManager irManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tv_result);
        etSendCode = findViewById(R.id.et_send_code);
        btnStart = findViewById(R.id.btn_start_recv);
        btnStop = findViewById(R.id.btn_stop_recv);
        btnSend = findViewById(R.id.btn_send);

        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);

        // 检查录音权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }

        btnStart.setOnClickListener(v -> startReceiving());
        btnStop.setOnClickListener(v -> stopReceiving());
        btnSend.setOnClickListener(v -> sendIRCode());
    }

    private void startReceiving() {
        if (isReceiving) return;
        if (audioRecord != null) {
            audioRecord.release();
        }
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
        List<Integer> timings = new ArrayList<>();  // 微秒时间
        int lastState = 0;  // 0=低, 1=高
        int sampleCount = 0;
        int threshold = 500;  // 阈值，根据实际情况调整

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
            // 每读取一块数据后检查是否收到完整一组脉冲
            if (timings.size() > 2 && !isReceiving) break;
        }

        // 处理采集到的时序
        if (timings.size() > 2) {
            StringBuilder raw = new StringBuilder("原始脉冲宽度(us):\n");
            for (int t : timings) raw.append(t).append(" ");
            final String rawData = raw.toString();
            // 尝试解码
            IRDecoder.DecodedSignal decoded = IRDecoder.decode(timings);
            final String result = rawData + "\n\n解码结果:\n" + (decoded != null ? decoded.toString() : "未能识别协议");

            runOnUiThread(() -> tvResult.setText(result));
        } else {
            runOnUiThread(() -> tvResult.setText("未检测到有效红外信号"));
        }
    }

    private void sendIRCode() {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Toast.makeText(this, "此设备不支持红外发射", Toast.LENGTH_SHORT).show();
            return;
        }
        String input = etSendCode.getText().toString().trim();
        if (input.isEmpty()) return;
        // 格式: NEC,地址,命令  支持十六进制 0x开头 或十进制
        String[] parts = input.split(",");
        if (parts.length < 3) {
            Toast.makeText(this, "格式错误，例: NEC,0x00FF,0x45", Toast.LENGTH_SHORT).show();
            return;
        }
        String protocol = parts[0].trim();
        int address = Integer.decode(parts[1].trim());
        int command = Integer.decode(parts[2].trim());

        if ("NEC".equalsIgnoreCase(protocol)) {
            int freq = 38000;
            int[] pattern = IRUtils.buildNECPattern(address, command);
            if (pattern != null) {
                irManager.transmit(freq, pattern);
                Toast.makeText(this, "NEC码已发送", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "当前仅支持NEC协议发送", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopReceiving();
    }
}

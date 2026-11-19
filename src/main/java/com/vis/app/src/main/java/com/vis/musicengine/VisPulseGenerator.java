package com.vis.musicengine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.audiofx.Visualizer;

public class VisPulseGenerator {
    private static final int SR = 44100;
    // Mức VIS 0..4 -> biên độ xung tối đa (đã giới hạn để an toàn)
    private static final float[] GAIN = {0f, 0.12f, 0.25f, 0.42f, 0.60f};
    private static final float PULSE_HZ = 55f;
    private static final int PULSE_MS = 90;
    private static final int COOLDOWN_MS = 110;
    private static final float BASS_LO = 30f, BASS_HI = 140f;

    private Visualizer visualizer;
    private AudioTrack track;
    private Thread writer;
    private volatile boolean running;
    private volatile int vis = 0;
    private volatile float pendingAmp = 0f;
    private double avg = 0;
    private long lastPulse = 0;
    private final float[] base;

    public VisPulseGenerator() {
        int len = SR * PULSE_MS / 1000;
        base = new float[len];
        double tau = PULSE_MS / 1000.0 / 3.0;
        int fade = SR * 3 / 1000; // fade-in 3ms chống tiếng click
        for (int i = 0; i < len; i++) {
            double t = i / (double) SR;
            double v = Math.sin(2 * Math.PI * PULSE_HZ * t) * Math.exp(-t / tau);
            if (i < fade) v *= i / (double) fade;
            base[i] = (float) v;
        }
    }

    public void setVis(int level) { vis = Math.max(0, Math.min(4, level)); }

    /** Gọi sau khi MediaPlayer đã prepare; cần quyền RECORD_AUDIO. */
    public void start(int audioSessionId) {
        stop();
        int min = AudioTrack.getMinBufferSize(SR,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(min)
                .setTransferMode(AudioTrack.MODE_STREAM).build();
        track.play();
        running = true;
        writer = new Thread(this::writeLoop, "VisPulseWriter");
        writer.start();

        visualizer = new Visualizer(audioSessionId);
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int r) {}
            @Override public void onFftDataCapture(Visualizer v, byte[] fft, int rateMilliHz) {
                onFft(fft, rateMilliHz / 1000f);
            }
        }, Visualizer.getMaxCaptureRate(), false, true);
        visualizer.setEnabled(true);
    }

    public void stop() {
        running = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Exception ignored) {}
            visualizer.release(); visualizer = null;
        }
        if (writer != null) {
            try { writer.join(300); } catch (InterruptedException ignored) {}
            writer = null;
        }
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) {}
            track.release(); track = null;
        }
        avg = 0; pendingAmp = 0;
    }

    private void onFft(byte[] fft, float hz) {
        int n = fft.length;
        double e = 0;
        for (int k = 1; k < n / 2; k++) {
            float f = k * hz / n;
            if (f < BASS_LO) continue;
            if (f > BASS_HI) break;
            double re = fft[2 * k], im = fft[2 * k + 1];
            e += re * re + im * im;
        }
        e = Math.sqrt(e);
        if (avg == 0) avg = e;
        avg = avg * 0.9 + e * 0.1;

        long now = System.currentTimeMillis();
        int level = vis;
        if (level > 0 && e > 20 && e > avg * 1.5 && now - lastPulse > COOLDOWN_MS) {
            float strength = (float) Math.max(0.3, Math.min(1.0, (e / avg - 1.2) / 1.8));
            pendingAmp = GAIN[level] * strength;
            lastPulse = now;
        }
    }

    private void writeLoop() {
        short[] buf = new short[256];
        int pos = -1;
        float amp = 0;
        while (running) {
            float a = pendingAmp;
            if (a > 0) { pendingAmp = 0; amp = a; pos = 0; }
            for (int i = 0; i < buf.length; i++) {
                if (pos >= 0 && pos < base.length) {
                    buf[i] = (short) (base[pos++] * amp * 32767f);
                } else { buf[i] = 0; pos = -1; }
            }
            track.write(buf, 0, buf.length);
        }
    }
}

package com.vis.musicengine;

import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.media.AudioManager;

public class MainActivity extends Activity {

    private MediaPlayer player;
    private Visualizer visualizer;
    private TextView status, fileName, bassText;
    private Button playButton;
    private SeekBar volume;
    private Uri audio;
    private boolean prepared = false;

    private void text(LinearLayout layout, String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 12, 0, 12);
        layout.addView(t);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(28, 35, 28, 25);
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("VIS MUSIC ENGINE");
        title.setTextSize(25);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        text(root, "AUDIO ANALYSIS • ALPHA 0.2", 11, 0xFFA078FF);

        fileName = new TextView(this);
        fileName.setText("NO AUDIO SELECTED");
        fileName.setTextColor(Color.WHITE);
        fileName.setPadding(0, 30, 0, 15);
        root.addView(fileName);

        Button importButton = new Button(this);
        importButton.setText("IMPORT AUDIO");
        importButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("audio/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, 100);
        });
        root.addView(importButton);

        playButton = new Button(this);
        playButton.setText("PLAY");
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlayback());
        root.addView(playButton);

        text(root, "OUTPUT VOLUME", 14, Color.WHITE);

        volume = new SeekBar(this);
        volume.setMax(70);
        volume.setProgress(35);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (player != null) {
                    float x = p / 100f;
                    player.setVolume(x, x);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(volume);

        text(root, "VIS AUDIO ANALYZER", 18, Color.WHITE);

        status = new TextView(this);
        status.setText("ANALYZER: WAITING");
        status.setTextColor(0xFFA078FF);
        root.addView(status);

        bassText = new TextView(this);
        bassText.setText("BASS ENERGY: --");
        bassText.setTextColor(Color.WHITE);
        root.addView(bassText);

        text(root, "FFT ANALYSIS: REAL-TIME", 12, Color.GRAY);
        text(root, "STEM SEPARATION: NOT ACTIVE", 12, Color.GRAY);
        text(root, "VIS PULSE GENERATOR: NOT ACTIVE", 12, Color.GRAY);

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);

        if (request == 100 && result == RESULT_OK && data != null) {
            audio = data.getData();
            fileName.setText(audio.getLastPathSegment());
            releasePlayer();

            try {
                player = new MediaPlayer();
                player.setDataSource(this, audio);
                player.setVolume(0.35f, 0.35f);

                player.setOnPreparedListener(mp -> {
                    prepared = true;
                    playButton.setEnabled(true);
                    status.setText("AUDIO READY");
                });

                player.setOnCompletionListener(mp -> {
                    stopAnalyzer();
                    playButton.setText("PLAY");
                    status.setText("PLAYBACK COMPLETED");
                });

                player.prepareAsync();
                status.setText("LOADING AUDIO...");
            } catch (Exception e) {
                status.setText("AUDIO LOAD FAILED");
            }
        }
    }

    private void togglePlayback() {
        if (player == null || !prepared) return;

        if (player.isPlaying()) {
            player.pause();
            stopAnalyzer();
            playButton.setText("PLAY");
            status.setText("PAUSED");
        } else {
            player.start();
            playButton.setText("PAUSE");

            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startAnalyzer();
            } else {
                requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO}, 200
                );
                status.setText("WAITING FOR AUDIO PERMISSION");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);

        if (request == 200 && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startAnalyzer();
        } else {
            status.setText("MICROPHONE PERMISSION DENIED");
        }
    }

    private void startAnalyzer() {
        if (player == null || !player.isPlaying()) return;

        stopAnalyzer();

        try {
            visualizer = new Visualizer(player.getAudioSessionId());
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

            visualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    public void onWaveFormDataCapture(
                            Visualizer v, byte[] waveform,
                            int rate) {}

                    public void onFftDataCapture(
                            Visualizer v, byte[] fft,
                            int rate) {

                        double total = 0;
                        int count = 0;
                        double sampleRate = rate / 1000.0;

                        for (int i = 1; i < fft.length / 2; i++) {
                            double frequency =
                                i * sampleRate / fft.length;

                            if (frequency >= 20 && frequency <= 250) {
                                double real = fft[2 * i];
                                double imaginary = fft[2 * i + 1];
                                total += Math.sqrt(
                                    real * real + imaginary * imaginary
                                );
                                count++;
                            }
                        }

                        final int energy = count == 0 ? 0 :
                            (int) Math.min(100, total / count * 2);

                        runOnUiThread(() -> {
                            bassText.setText("BASS ENERGY: " + energy + "%");
                            status.setText("FFT ANALYZER: ACTIVE");
                        });
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            );

            visualizer.setEnabled(true);
            status.setText("FFT ANALYZER: ACTIVE");

        } catch (Exception e) {
            status.setText("FFT ANALYZER: UNAVAILABLE");
            stopAnalyzer();
        }
    }

    private void stopAnalyzer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    private void releasePlayer() {
        stopAnalyzer();

        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }

        prepared = false;
        if (playButton != null) {
            playButton.setEnabled(false);
            playButton.setText("PLAY");
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
                            }
private VisPulseGenerator pulse = new VisPulseGenerator();

// 1. Xin quyền RECORD_AUDIO lúc runtime (Visualizer bắt buộc).
// 2. Sau khi mediaPlayer.prepare() / sẵn sàng phát:
pulse.start(mediaPlayer.getAudioSessionId());

// 3. Điều khiển VIS 0-4 (ví dụ SeekBar max=4):
pulse.setVis(progress);

// 4. Trong onDestroy() và khi đổi bài:
pulse.stop();

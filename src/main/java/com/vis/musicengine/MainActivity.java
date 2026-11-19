
package com.vis.musicengine;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 100;
    private static final int AUDIO_PERMISSION = 200;

    private MediaPlayer player;
    private Visualizer visualizer;

    private TextView statusText;
    private TextView fileText;
    private TextView bassText;
    private TextView fftText;

    private Button playButton;
    private SeekBar volumeSlider;

    private Uri selectedAudio;
    private boolean prepared = false;

    private final int BLACK = Color.BLACK;
    private final int PURPLE = Color.rgb(160, 120, 255);
    private final int WHITE = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createInterface();
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 35, 28, 28);
        root.setBackgroundColor(BLACK);

        TextView title = new TextView(this);
        title.setText("VIS MUSIC ENGINE");
        title.setTextColor(WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 15, 0, 8);

        TextView subtitle = new TextView(this);
        subtitle.setText("AUDIO PROCESSING SYSTEM • 0.2");
        subtitle.setTextColor(PURPLE);
        subtitle.setTextSize(10);
        subtitle.setGravity(Gravity.CENTER);

        fileText = new TextView(this);
        fileText.setText("NO AUDIO SELECTED");
        fileText.setTextColor(WHITE);
        fileText.setPadding(0, 35, 0, 15);

        Button importButton = new Button(this);
        importButton.setText("IMPORT AUDIO");
        importButton.setOnClickListener(v -> openAudioPicker());

        playButton = new Button(this);
        playButton.setText("PLAY");
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlayback());

        statusText = new TextView(this);
        statusText.setText("SYSTEM READY");
        statusText.setTextColor(PURPLE);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 20, 0, 20);

        TextView volumeLabel = new TextView(this);
        volumeLabel.setText("OUTPUT VOLUME");
        volumeLabel.setTextColor(WHITE);

        volumeSlider = new SeekBar(this);
        volumeSlider.setMax(70);
        volumeSlider.setProgress(35);

        volumeSlider.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser
                ) {
                    if (player != null) {
                        float volume = progress / 100.0f;
                        player.setVolume(volume, volume);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            }
        );

        TextView analyzerTitle = new TextView(this);
        analyzerTitle.setText("VIS AUDIO ANALYZER");
        analyzerTitle.setTextColor(WHITE);
        analyzerTitle.setTextSize(18);
        analyzerTitle.setPadding(0, 25, 0, 15);

        fftText = new TextView(this);
        fftText.setText("FFT: WAITING");
        fftText.setTextColor(PURPLE);
        fftText.setTextSize(14);

        bassText = new TextView(this);
        bassText.setText("BASS ENERGY: 0%");
        bassText.setTextColor(WHITE);
        bassText.setTextSize(16);
        bassText.setPadding(0, 15, 0, 8);

        TextView info = new TextView(this);
        info.setText(
            "FFT ANALYSIS\n" +
            "Frequency range: 20 Hz - 250 Hz\n\n" +
            "Stem Separation: NOT ACTIVE\n" +
            "VIS Pulse Generator: NOT ACTIVE"
        );
        info.setTextColor(Color.GRAY);
        info.setTextSize(12);
        info.setPadding(0, 15, 0, 0);

        root.addView(title);
        root.addView(subtitle);
        root.addView(fileText);
        root.addView(importButton);
        root.addView(playButton);
        root.addView(statusText);
        root.addView(volumeLabel);
        root.addView(volumeSlider);
        root.addView(analyzerTitle);
        root.addView(fftText);
        root.addView(bassText);
        root.addView(info);

        setContentView(root);
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO &&
            resultCode == RESULT_OK &&
            data != null) {

            selectedAudio = data.getData();

            if (selectedAudio != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                        selectedAudio,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {}

                String name = selectedAudio.getLastPathSegment();

                fileText.setText(
                    name != null ? name : "AUDIO SELECTED"
                );

                releasePlayer();
                preparePlayer();
            }
        }
    }

    private void preparePlayer() {
        try {
            prepared = false;
            playButton.setEnabled(false);
            statusText.setText("LOADING AUDIO...");

            player = new MediaPlayer();
            player.setDataSource(this, selectedAudio);

            float volume = volumeSlider.getProgress() / 100.0f;
            player.setVolume(volume, volume);

            player.setOnPreparedListener(mp -> {
                prepared = true;
                playButton.setEnabled(true);
                statusText.setText("AUDIO READY");
                startAnalyzerOrRequestPermission();
            });

            player.setOnCompletionListener(mp -> {
                playButton.setText("PLAY");
                statusText.setText("PLAYBACK COMPLETED");
            });

            player.setOnErrorListener((mp, what, extra) -> {
                statusText.setText("PLAYBACK ERROR");
                releasePlayer();
                return true;
            });

            player.prepareAsync();

        } catch (Exception e) {
            statusText.setText("AUDIO LOAD FAILED");
            releasePlayer();
        }
    }

    private void startAnalyzerOrRequestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                AUDIO_PERMISSION
            );

            statusText.setText("AUDIO READY • PERMISSION REQUIRED");
            return;
        }

        startAnalyzer();
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] grantResults
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        );

        if (requestCode == AUDIO_PERMISSION) {
            if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startAnalyzer();

            } else {
                statusText.setText(
                    "PLAYBACK READY • ANALYZER PERMISSION DENIED"
                );
                fftText.setText("FFT: DISABLED");
            }
        }
    }

    private void startAnalyzer() {
        releaseAnalyzer();

        if (player == null || !prepared) return;

        try {
            visualizer = new Visualizer(player.getAudioSessionId());

            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = range[1];

            visualizer.setCaptureSize(captureSize);

            visualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {

                    @Override
                    public void onWaveFormDataCapture(
                        Visualizer v, byte[] waveform,
                        int samplingRate
                    ) {}

                    @Override
                    public void onFftDataCapture(
                        Visualizer v, byte[] fft,
                        int samplingRate
                    ) {
                        final int level = calculateBassEnergy(
                            fft, samplingRate
                        );

                        runOnUiThread(() -> {
                            if (bassText != null) {
                                bassText.setText(
                                    "BASS ENERGY: " + level + "%"
                                );
                                fftText.setText("FFT: ACTIVE");
                            }
                        });
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            );

            visualizer.setEnabled(true);

            statusText.setText("AUDIO ANALYZER ACTIVE");

        } catch (Exception e) {
            releaseAnalyzer();
            fftText.setText("FFT: INITIALIZATION FAILED");
            statusText.setText("ANALYZER ERROR");
        }
    }

    private int calculateBassEnergy(
        byte[] fft, int samplingRateMilliHz
    ) {
        if (fft == null || fft.length < 4) return 0;

        double sampleRate = samplingRateMilliHz / 1000.0;
        int fftSize = fft.length;
        int binCount = fft.length / 2;

        double total = 0;
        int count = 0;

        for (int i = 1; i < binCount; i++) {
            double frequency =
                (double) i * sampleRate / fftSize;

            if (frequency >= 20 && frequency <= 250) {
                double real = fft[i * 2];
                double imaginary = fft[i * 2 + 1];

                double magnitude = Math.sqrt(
                    real * real + imaginary * imaginary
                );

                total += magnitude;
                count++;
            }
        }

        if (count == 0) return 0;

        double average = total / count;

        double db = 20.0 * Math.log10(
            Math.max(average, 0.001) / 128.0
        );

        int percentage = (int) (
            ((db + 60.0) / 60.0) * 100.0
        );

        return Math.max(0, Math.min(100, percentage));
    }

    private void togglePlayback() {
        if (player == null || !prepared) return;

        try {
            if (player.isPlaying()) {
                player.pause();
                playButton.setText("PLAY");
                statusText.setText("PAUSED");
            } else {
                player.start();
                playButton.setText("PAUSE");

                statusText.setText(
                    visualizer != null
                    ? "AUDIO ANALYZER ACTIVE"
                    : "PLAYING • ANALYZER INACTIVE"
                );
            }
        } catch (Exception e) {
            statusText.setText("PLAYBACK ERROR");
        }
    }

    private void releaseAnalyzer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}

            visualizer = null;
        }
    }

    private void releasePlayer() {
        prepared = false;

        releaseAnalyzer();

        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {}

            player.release();
            player = null;
        }

        if (playButton != null) {
            playButton.setText("PLAY");
            playButton.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
        }
        

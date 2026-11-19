
package com.vis.musicengine;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.media.MediaPlayer;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Toast;

public class MainActivity extends Activity {

    private MediaPlayer player;
    private TextView statusText;
    private TextView fileText;
    private Button playButton;
    private SeekBar volumeSlider;

    private Uri selectedAudio;

    private boolean prepared = false;

    private final int BLACK = Color.rgb(0, 0, 0);
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
        root.setPadding(28, 40, 28, 28);
        root.setBackgroundColor(BLACK);

        TextView title = new TextView(this);
        title.setText("VIS MUSIC ENGINE");
        title.setTextColor(WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 20, 0, 8);

        TextView subtitle = new TextView(this);
        subtitle.setText("AUDIO PROCESSING SYSTEM • ALPHA 0.1");
        subtitle.setTextColor(PURPLE);
        subtitle.setTextSize(10);
        subtitle.setGravity(Gravity.CENTER);

        fileText = new TextView(this);
        fileText.setText("NO AUDIO SELECTED");
        fileText.setTextColor(WHITE);
        fileText.setTextSize(14);
        fileText.setPadding(0, 45, 0, 20);

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
        statusText.setPadding(0, 25, 0, 25);

        TextView volumeLabel = new TextView(this);
        volumeLabel.setText("OUTPUT VOLUME");
        volumeLabel.setTextColor(WHITE);
        volumeLabel.setTextSize(15);
        volumeLabel.setPadding(0, 20, 0, 5);

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
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            }
        );

        TextView visTitle = new TextView(this);
        visTitle.setText("VIS EFFECT");
        visTitle.setTextColor(WHITE);
        visTitle.setTextSize(18);
        visTitle.setPadding(0, 25, 0, 8);

        TextView visInfo = new TextView(this);
        visInfo.setText(
            "VIS - Vibration Intensity Scale\n\n" +
            "Audio processing: NOT ACTIVE\n\n" +
            "Bass / Vocal / Drums separation\n" +
            "Planned for future versions."
        );
        visInfo.setTextColor(Color.GRAY);
        visInfo.setTextSize(12);
        visInfo.setPadding(0, 10, 0, 0);

        root.addView(title);
        root.addView(subtitle);
        root.addView(fileText);
        root.addView(importButton);
        root.addView(playButton);
        root.addView(statusText);
        root.addView(volumeLabel);
        root.addView(volumeSlider);
        root.addView(visTitle);
        root.addView(visInfo);

        setContentView(root);
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 &&
            resultCode == RESULT_OK &&
            data != null) {

            selectedAudio = data.getData();

            if (selectedAudio != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                        selectedAudio,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                    // Some document providers do not support persistence.
                }

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
            });

            player.setOnCompletionListener(mp -> {
                playButton.setText("PLAY");
                statusText.setText("PLAYBACK COMPLETED");
            });

            player.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                playButton.setEnabled(false);
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

    private void togglePlayback() {
        if (player == null || !prepared) {
            Toast.makeText(
                this,
                "Import an audio file first",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {
            if (player.isPlaying()) {
                player.pause();
                playButton.setText("PLAY");
                statusText.setText("PAUSED");
            } else {
                player.start();
                playButton.setText("PAUSE");
                statusText.setText("PLAYING");
            }
        } catch (Exception e) {
            statusText.setText("PLAYBACK ERROR");
        }
    }

    private void releasePlayer() {
        prepared = false;

        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }

            player.release();
            player = null;
        }

        playButton.setText("PLAY");
        playButton.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
  }
              

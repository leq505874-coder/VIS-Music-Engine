
package com.vis.musicengine;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.media.MediaPlayer;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
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

    private Uri selectedAudio;

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
        fileText.setText("No audio selected");
        fileText.setTextColor(WHITE);
        fileText.setTextSize(14);
        fileText.setPadding(0, 45, 0, 20);

        Button importButton = new Button(this);
        importButton.setText("IMPORT AUDIO");
        importButton.setOnClickListener(v -> openAudioPicker());

        playButton = new Button(this);
        playButton.setText("PLAY");
        playButton.setOnClickListener(v -> togglePlayback());

        statusText = new TextView(this);
        statusText.setText("SYSTEM READY");
        statusText.setTextColor(PURPLE);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 25, 0, 25);

        TextView visLabel = new TextView(this);
        visLabel.setText("VIS EFFECT");
        visLabel.setTextColor(WHITE);
        visLabel.setTextSize(18);
        visLabel.setPadding(0, 20, 0, 10);

        SeekBar visSlider = new SeekBar(this);
        visSlider.setMax(100);
        visSlider.setProgress(30);

        TextView note = new TextView(this);
        note.setText(
            "VIS: Vibration Intensity Scale\n" +
            "Audio-to-haptic processing: NOT ACTIVE\n\n" +
            "Bass / Vocal / Drums separation\n" +
            "Planned for future versions."
        );
        note.setTextColor(Color.GRAY);
        note.setTextSize(12);
        note.setPadding(0, 25, 0, 0);

        root.addView(title);
        root.addView(subtitle);
        root.addView(fileText);
        root.addView(importButton);
        root.addView(playButton);
        root.addView(statusText);
        root.addView(visLabel);
        root.addView(visSlider);
        root.addView(note);

        setContentView(root);
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/mpeg");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            selectedAudio = data.getData();

            if (selectedAudio != null) {
                getContentResolver().takePersistableUriPermission(
                    selectedAudio,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                fileText.setText(selectedAudio.getLastPathSegment());
                statusText.setText("AUDIO LOADED");
                releasePlayer();
                preparePlayer();
            }
        }
    }

    private void preparePlayer() {
        try {
            player = new MediaPlayer();
            player.setDataSource(this, selectedAudio);
            player.setVolume(0.7f, 0.7f);

            player.setOnPreparedListener(mp -> {
                statusText.setText("AUDIO READY");
                playButton.setEnabled(true);
            });

            player.setOnCompletionListener(mp -> {
                playButton.setText("PLAY");
                statusText.setText("PLAYBACK COMPLETED");
            });

            player.setOnErrorListener((mp, what, extra) -> {
                statusText.setText("PLAYBACK ERROR");
                return true;
            });

            player.prepareAsync();
            statusText.setText("LOADING AUDIO...");
            playButton.setEnabled(false);

        } catch (Exception e) {
            statusText.setText("AUDIO LOAD FAILED");
        }
    }

    private void togglePlayback() {
        if (player == null) {
            Toast.makeText(this, "Import an audio file first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (player.isPlaying()) {
            player.pause();
            playButton.setText("PLAY");
            statusText.setText("PAUSED");
        } else {
            player.start();
            playButton.setText("PAUSE");
            statusText.setText("PLAYING");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
          }

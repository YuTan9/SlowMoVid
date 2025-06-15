package edu.sjsu.android.videoplayer;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_VIDEO = 101;
    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final long FRAME_SEEK_INTERVAL_MS = 100;
    private static final String TAG = "VideoPlayer";

    private PlayerView playerView;
    private ExoPlayer player;

    private ImageButton playPauseButton, nextFrameButton, prevFrameButton, selectVideoButton, editMenuButton;
    private SeekBar speedSeekBar;
    private TextView speedLabel, modeText;
    private View controlOverlay, topBar;
    private DrawingView drawingView;

    private SeekBar videoSeekBar;
    private TextView currentTimeText, totalDurationText;

    private boolean controlsVisible = true;
    private float previousSpeed = 1.0f;
    private boolean isInEditMode = false;
    private final Handler frameSeekHandler = new Handler(Looper.getMainLooper());
    private final Handler seekBarHandler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBarRunnable;
    private boolean isSeekingForward = false;
    private boolean isSeekingBackward = false;

    private final Runnable seekNextFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (isSeekingForward) {
                stepFrames(1);
                frameSeekHandler.postDelayed(this, FRAME_SEEK_INTERVAL_MS);
            }
        }
    };

    private final Runnable seekPrevFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (isSeekingBackward) {
                stepFrames(-1);
                frameSeekHandler.postDelayed(this, FRAME_SEEK_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        setContentView(R.layout.activity_main);

        bindViews();
        initializePlayer();
        setListeners();
        pickVideo();
    }

    private void bindViews() {
        playerView = findViewById(R.id.playerView);
        playPauseButton = findViewById(R.id.playPauseButton);
        nextFrameButton = findViewById(R.id.nextFrameButton);
        prevFrameButton = findViewById(R.id.prevFrameButton);
        selectVideoButton = findViewById(R.id.selectVideoButton);
        editMenuButton = findViewById(R.id.editMenuButton);
        speedSeekBar = findViewById(R.id.speedSeekBar);
        speedLabel = findViewById(R.id.speedLabel);
        controlOverlay = findViewById(R.id.controlOverlay);
        topBar = findViewById(R.id.topBar);
        drawingView = findViewById(R.id.drawingView);
        modeText = findViewById(R.id.modeText);
        videoSeekBar = findViewById(R.id.videoSeekBar);
        currentTimeText = findViewById(R.id.currentTime);
        totalDurationText = findViewById(R.id.totalDuration);
    }

    private void setListeners() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());

        nextFrameButton.setOnClickListener(v -> stepFrames(1));
        prevFrameButton.setOnClickListener(v -> stepFrames(-1));

        nextFrameButton.setOnLongClickListener(v -> {
            isSeekingForward = true;
            frameSeekHandler.post(seekNextFrameRunnable);
            return true;
        });
        nextFrameButton.setOnTouchListener(createSeekTouchListener(() -> isSeekingForward = false));

        prevFrameButton.setOnLongClickListener(v -> {
            isSeekingBackward = true;
            frameSeekHandler.post(seekPrevFrameRunnable);
            return true;
        });
        prevFrameButton.setOnTouchListener(createSeekTouchListener(() -> isSeekingBackward = false));

        selectVideoButton.setOnClickListener(v -> pickVideo());

        speedSeekBar.setProgress(3);
        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = SPEEDS[progress];
                speedLabel.setText(String.format("%s%s%s", getString(R.string.speedText), speed, getString(R.string.timesText)));
                if (player != null) {
                    player.setPlaybackParameters(new PlaybackParameters(speed));
                }
                previousSpeed = speed;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying()) {
                    int currentPos = (int) player.getCurrentPosition();
                    videoSeekBar.setProgress(currentPos);
                }
                seekBarHandler.postDelayed(this, 1000);
            }
        };

        playerView.setOnClickListener(v -> {
            toggleControls();
        });

        setupEditMenu();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                pickVideo();
            }
        });
    }

    private void togglePlayPause() {
        if (player.isPlaying()) {
            player.pause();
            playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24);
        } else {
            player.play();
            playPauseButton.setImageResource(R.drawable.baseline_pause_24);
        }
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        int visibility = controlsVisible ? View.VISIBLE : View.GONE;
        Log.d(TAG, "toggleControls: setting visibility to " + controlsVisible);
        controlOverlay.setVisibility(visibility);
        topBar.setVisibility(visibility);
    }

    private View.OnTouchListener createSeekTouchListener(Runnable onStop) {
        return (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                onStop.run();
                frameSeekHandler.removeCallbacksAndMessages(null);
            }
            return false;
        };
    }

    private void stepFrames(int direction) {
        if (player != null && player.getPlaybackState() == Player.STATE_READY) {
            player.pause();
            long pos = player.getCurrentPosition();
            long newPos = pos + direction * 33L;
            player.seekTo(Math.max(newPos, 0));
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_PICK_VIDEO);
    }

    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        long durationMs = player.getDuration();
                        totalDurationText.setText(formatTime(durationMs));
                        videoSeekBar.setMax((int) durationMs);
                    }
                }
            });
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            initializePlayer();
            Uri videoUri = data.getData();
            MediaItem item = MediaItem.fromUri(videoUri);
            player.setMediaItem(item);
            player.prepare();
            playPauseButton.setImageResource(R.drawable.baseline_play_arrow_24);
            seekBarHandler.post(updateSeekBarRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            seekBarHandler.removeCallbacks(updateSeekBarRunnable);
            player.release();
            player = null;
        }
    }

    private void setupEditMenu() {
        editMenuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v);
            menu.getMenu().add("Enter Draw Mode").setOnMenuItemClickListener(item -> {
                isInEditMode = true;
                updateModeLabel();
                showDrawOptions(drawingView);
                drawingView.setVisibility(View.VISIBLE);
                drawingView.setDrawingEnabled(true);
                return true;
            });
            menu.getMenu().add("Eraser").setOnMenuItemClickListener(item -> {
                if (isInEditMode) drawingView.setTool(DrawingView.Tool.ERASER);
                return true;
            });
            menu.getMenu().add("Erase All").setOnMenuItemClickListener(item -> {
                if (isInEditMode) drawingView.clearAll();
                return true;
            });
            menu.getMenu().add("Exit Edit Mode").setOnMenuItemClickListener(item -> {
                isInEditMode = false;
                updateModeLabel();
                drawingView.setDrawingEnabled(false);
                return true;
            });
            menu.show();
        });
    }

    private void updateModeLabel() {
        modeText.setText(isInEditMode ? "Draw" : "Play");
    }

    private void showDrawOptions(@NonNull DrawingView view) {
        view.setTool(DrawingView.Tool.DRAW);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_draw_options, null);

        SeekBar strokeSeek = dialogView.findViewById(R.id.strokeSeek);
        Spinner colorSpinner = dialogView.findViewById(R.id.colorSpinner);
        Spinner shapeSpinner = dialogView.findViewById(R.id.shapeSpinner);

        colorSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Red", "Green", "Blue", "Black", "Yellow"}));

        shapeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Line", "Free", "Circle"}));

        strokeSeek.setProgress(10);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Draw Settings")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    switch ((String) colorSpinner.getSelectedItem()) {
                        case "Red": view.setColor(Color.RED); break;
                        case "Green": view.setColor(Color.GREEN); break;
                        case "Blue": view.setColor(Color.BLUE); break;
                        case "Black": view.setColor(Color.BLACK); break;
                        case "Yellow": view.setColor(Color.YELLOW); break;
                    }

                    switch ((String) shapeSpinner.getSelectedItem()) {
                        case "Free": view.setShape(DrawingView.Shape.FREE); break;
                        case "Line": view.setShape(DrawingView.Shape.LINE); break;
                        case "Circle": view.setShape(DrawingView.Shape.CIRCLE); break;
                    }

                    view.setStrokeWidth(strokeSeek.getProgress());
                })
                .show();
    }
}
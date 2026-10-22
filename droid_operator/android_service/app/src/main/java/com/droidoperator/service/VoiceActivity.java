package com.droidoperator.service;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class VoiceActivity extends Activity {

    private static final String TAG = "DroidVoiceActivity";
    private SpeechRecognizer speechRecognizer;
    private TextView statusTextView;
    private TextView recognizedTextView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Modern Siri / Gemini floating translucent card
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        // Tap background to dismiss
        root.setOnClickListener(v -> finish());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(60, 50, 60, 80);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F012141C")); // Sleek AMOLED dark
        bg.setCornerRadii(new float[]{60, 60, 60, 60, 0, 0, 0, 0});
        card.setBackground(bg);

        statusTextView = new TextView(this);
        statusTextView.setText("Dinliyorum...");
        statusTextView.setTextColor(Color.parseColor("#00E5FF")); // Cyan glow
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(0, 0, 0, 15);
        card.addView(statusTextView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16));
        card.addView(progressBar);

        recognizedTextView = new TextView(this);
        recognizedTextView.setText("");
        recognizedTextView.setTextColor(Color.WHITE);
        recognizedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        recognizedTextView.setGravity(Gravity.CENTER);
        recognizedTextView.setPadding(0, 25, 0, 10);
        card.addView(recognizedTextView);

        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        startListeningImmediately();
    }

    private void startListeningImmediately() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusTextView.setText("Ses tanıma servisi bulunamadı");
            statusTextView.postDelayed(this::finish, 2000);
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                statusTextView.setText("Sizi dinliyorum...");
            }

            @Override
            public void onBeginningOfSpeech() {
                statusTextView.setText("Algılanıyor...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                statusTextView.setText("İşleniyor...");
            }

            @Override
            public void onError(int error) {
                Log.w(TAG, "SpeechRecognizer error: " + error);
                statusTextView.setText("Ses anlaşılamadı");
                statusTextView.postDelayed(VoiceActivity.this::finish, 1500);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String query = matches.get(0);
                    Log.i(TAG, "Recognized user command: " + query);
                    recognizedTextView.setText("\"" + query + "\"");
                    statusTextView.setText("Yürütülüyor...");

                    if (DroidAccessibilityService.INSTANCE != null) {
                        DroidAccessibilityService.INSTANCE.executeAutonomousVoiceCommand(query);
                    } else {
                        Toast.makeText(VoiceActivity.this, "Erişilebilirlik servisi aktif değil", Toast.LENGTH_SHORT).show();
                    }

                    // Dismiss immediately so underlying screen is accessible and visible
                    statusTextView.postDelayed(VoiceActivity.this::finish, 350);
                } else {
                    statusTextView.setText("Komut alınamadı");
                    statusTextView.postDelayed(VoiceActivity.this::finish, 1200);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}

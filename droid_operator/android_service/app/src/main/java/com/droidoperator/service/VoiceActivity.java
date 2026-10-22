package com.droidoperator.service;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceActivity extends Activity {

    private static final String TAG = "DroidVoiceActivity";
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private TextView statusTextView;
    private TextView recognizedTextView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Build a sleek, modern Siri/Gemini style translucent bottom card UI
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(60, 60, 60, 80);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#EE1A1C23")); // Deep dark modern translucent
        bg.setCornerRadii(new float[]{60, 60, 60, 60, 0, 0, 0, 0});
        card.setBackground(bg);

        statusTextView = new TextView(this);
        statusTextView.setText("Dinliyorum...");
        statusTextView.setTextColor(Color.parseColor("#4DEEEA"));
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(0, 0, 0, 20);
        card.addView(statusTextView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 20));
        card.addView(progressBar);

        recognizedTextView = new TextView(this);
        recognizedTextView.setText("");
        recognizedTextView.setTextColor(Color.WHITE);
        recognizedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        recognizedTextView.setGravity(Gravity.CENTER);
        recognizedTextView.setPadding(0, 30, 0, 10);
        card.addView(recognizedTextView);

        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // Initialize Text-To-Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("tr", "TR"));
            }
        });

        // Initialize Speech Recognizer
        startListeningImmediately();
    }

    private void startListeningImmediately() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusTextView.setText("Ses tanıma servisi bulunamadı");
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
                statusTextView.setText("Ses anlaşılamadı (Hata: " + error + ")");
                rootFinishDelay(2000);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String query = matches.get(0);
                    recognizedTextView.setText("\"" + query + "\"");
                    executeAutonomousCommand(query);
                } else {
                    statusTextView.setText("Komut alınamadı");
                    rootFinishDelay(1500);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    /**
     * Autonomous Agent Decision Pipeline
     */
    private void executeAutonomousCommand(String query) {
        String lower = query.toLowerCase(Locale.forLanguageTag("tr-TR")).trim();
        statusTextView.setText("Eylem yürütülüyor...");

        new Thread(() -> {
            try {
                DroidAccessibilityService service = DroidAccessibilityService.INSTANCE;

                // 1. WhatsApp Intent & Automation
                if (lower.contains("whatsapp") || lower.contains("mesaj")) {
                    speak("WhatsApp açılıyor...");
                    handleWhatsAppCommand(lower);
                    return;
                }

                // 2. Navigation Commands
                if (lower.equals("ana ekran") || lower.equals("ana ekrana dön") || lower.equals("ev")) {
                    if (service != null) service.performGlobal("home");
                    speak("Ana ekrana dönüldü");
                    rootFinishDelay(1000);
                    return;
                }

                if (lower.equals("geri") || lower.equals("geri dön")) {
                    if (service != null) service.performGlobal("back");
                    rootFinishDelay(1000);
                    return;
                }

                if (lower.contains("bildirim") || lower.contains("bildirimleri aç")) {
                    if (service != null) service.performGlobal("notifications");
                    rootFinishDelay(1000);
                    return;
                }

                // 3. General App Launcher ("... uygulamasını aç")
                if (lower.startsWith("aç ") || lower.endsWith(" aç") || lower.endsWith(" başlat")) {
                    String appName = lower.replace("aç", "").replace("başlat", "").replace("uygulamasını", "").trim();
                    launchAppByName(appName);
                    return;
                }

                // 4. Default Agent Flow: Search on-screen element and click
                if (service != null) {
                    String uiJson = service.dumpUiJson();
                    JSONArray array = new JSONArray(uiJson);
                    boolean clicked = false;
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String text = obj.optString("text", "").toLowerCase(Locale.forLanguageTag("tr-TR"));
                        String desc = obj.optString("desc", "").toLowerCase(Locale.forLanguageTag("tr-TR"));

                        if ((text.length() > 2 && lower.contains(text)) || (desc.length() > 2 && lower.contains(desc))) {
                            int cx = obj.getInt("centerX");
                            int cy = obj.getInt("centerY");
                            service.click(cx, cy);
                            speak(text + " tıklandı");
                            clicked = true;
                            break;
                        }
                    }

                    if (!clicked) {
                        speak("Ekran incelendi, ilgili öğe bulunamadı.");
                    }
                }

                rootFinishDelay(2000);

            } catch (Exception e) {
                Log.e(TAG, "Command execution error", e);
                rootFinishDelay(1500);
            }
        }).start();
    }

    private void handleWhatsAppCommand(String lower) {
        // e.g. "whatsapptan orhana ne yapıyorsun yaz"
        String targetContact = "";
        String messageText = "";

        // Extract contact and message
        String[] words = lower.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].contains("orhan")) {
                targetContact = "Orhan";
            }
        }

        if (targetContact.isEmpty() && words.length > 1) {
            targetContact = words[1]; // fallback second word
        }

        // Launch WhatsApp
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage("com.whatsapp");
        if (launchIntent != null) {
            startActivity(launchIntent);
        }

        speak(targetContact + " bulunuyor...");

        // Autonomous steps via Accessibility Service
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                DroidAccessibilityService service = DroidAccessibilityService.INSTANCE;
                if (service != null) {
                    // Tap search bar (center X=720, Y=442)
                    service.click(720, 442);
                    Thread.sleep(800);

                    // Type contact
                    service.typeText(targetContact);
                    Thread.sleep(1200);

                    // Click first result card (around Y=576)
                    service.click(351, 576);
                    Thread.sleep(1000);

                    // If message exists, type it
                    // service.typeText("Selam");
                    speak("Sohbet açıldı.");
                }
                rootFinishDelay(500);
            } catch (Exception e) {
                Log.e(TAG, "WhatsApp automation error", e);
            }
        }).start();
    }

    private void launchAppByName(String appName) {
        speak(appName + " açılıyor...");
        PackageManager pm = getPackageManager();
        for (android.content.pm.ApplicationInfo app : pm.getInstalledApplications(0)) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.forLanguageTag("tr-TR"));
            if (label.contains(appName)) {
                Intent intent = pm.getLaunchIntentForPackage(app.packageName);
                if (intent != null) {
                    startActivity(intent);
                    rootFinishDelay(1000);
                    return;
                }
            }
        }
        speak("Uygulama bulunamadı");
        rootFinishDelay(1500);
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id");
        }
    }

    private void rootFinishDelay(long ms) {
        runOnUiThread(() -> {
            statusTextView.postDelayed(this::finish, ms);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}

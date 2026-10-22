package com.droidoperator.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DroidAccessibilityService extends AccessibilityService {

    private static final String TAG = "DroidOperatorService";
    public static DroidAccessibilityService INSTANCE = null;
    private LocalApiServer apiServer = null;
    private TextToSpeech tts = null;
    private Handler mainHandler = null;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        mainHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "DroidOperator Accessibility Service Connected!");

        // Start Localhost API server (127.0.0.1:8765)
        try {
            apiServer = new LocalApiServer(8765, this);
            apiServer.start();
            Log.i(TAG, "LocalApiServer started on port 8765");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start LocalApiServer", e);
        }

        // Initialize Text-To-Speech for service-level voice feedback
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(new Locale("tr", "TR"));
                    Log.i(TAG, "TTS initialized successfully");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "TTS init error", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        Log.w(TAG, "DroidOperator Accessibility Service Interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        INSTANCE = null;
        Log.i(TAG, "DroidOperator Accessibility Service Destroyed");
    }

    public void speak(String text) {
        Log.i(TAG, "TTS Speak: " + text);
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "droid_tts_" + System.currentTimeMillis());
        }
        if (mainHandler != null) {
            mainHandler.post(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show());
        }
    }

    // =========================================================================
    // Core Gesture & Interaction Primitives
    // =========================================================================

    public boolean click(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return dispatchGesture(builder.build(), null, null);
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 100));
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return dispatchGesture(builder.build(), null, null);
    }

    public boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Try direct ACTION_CLICK
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }

        // Try parent click up to 4 levels
        AccessibilityNodeInfo current = node.getParent();
        for (int i = 0; i < 4 && current != null; i++) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            current = current.getParent();
        }

        // Fallback: Physical coordinate tap at center of bounds
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.width() > 0 && bounds.height() > 0) {
            return click(bounds.centerX(), bounds.centerY());
        }
        return false;
    }

    public boolean setText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        if (!ok) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            return typeText(text);
        }
        return ok;
    }

    public boolean typeText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            traverseTree(root, nodes);
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isEditable()) {
                    focused = n;
                    break;
                }
            }
        }

        if (focused != null && focused.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }
        return false;
    }

    public boolean performGlobal(String action) {
        if ("back".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_BACK);
        } else if ("home".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_HOME);
        } else if ("recents".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_RECENTS);
        } else if ("notifications".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
        return false;
    }

    // =========================================================================
    // Node Finding & Polling
    // =========================================================================

    public AccessibilityNodeInfo findNodeById(String targetId) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(targetId);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public AccessibilityNodeInfo findNodeByTextOrDesc(String query) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        String qLower = query.toLowerCase(Locale.forLanguageTag("tr-TR")).trim();

        List<AccessibilityNodeInfo> all = new ArrayList<>();
        traverseTree(root, all);
        for (AccessibilityNodeInfo n : all) {
            if (n.getText() != null) {
                String t = n.getText().toString().toLowerCase(Locale.forLanguageTag("tr-TR"));
                if (t.contains(qLower)) return n;
            }
            if (n.getContentDescription() != null) {
                String d = n.getContentDescription().toString().toLowerCase(Locale.forLanguageTag("tr-TR"));
                if (d.contains(qLower)) return n;
            }
        }
        return null;
    }

    public AccessibilityNodeInfo waitForNode(String id, String textQuery, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (id != null) {
                AccessibilityNodeInfo n = findNodeById(id);
                if (n != null) return n;
            }
            if (textQuery != null) {
                AccessibilityNodeInfo n = findNodeByTextOrDesc(textQuery);
                if (n != null) return n;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
        }
        return null;
    }

    public String dumpUiJson() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "[]";

        JSONArray array = new JSONArray();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        traverseTree(root, nodes);

        int index = 0;
        for (AccessibilityNodeInfo node : nodes) {
            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (bounds.width() <= 0 || bounds.height() <= 0) continue;

                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                CharSequence viewId = node.getViewIdResourceName();
                CharSequence className = node.getClassName();

                boolean isClickable = node.isClickable();
                boolean isEditable = node.isEditable();
                boolean isCheckable = node.isCheckable();
                boolean hasContent = (text != null && text.length() > 0) || (desc != null && desc.length() > 0);

                if (!isClickable && !isEditable && !isCheckable && !hasContent) continue;

                JSONObject obj = new JSONObject();
                obj.put("index", index++);
                obj.put("class", className != null ? className.toString() : "");
                obj.put("text", text != null ? text.toString() : "");
                obj.put("desc", desc != null ? desc.toString() : "");
                obj.put("id", viewId != null ? viewId.toString() : "");
                obj.put("clickable", isClickable);
                obj.put("editable", isEditable);
                obj.put("checked", node.isChecked());

                JSONArray boundsArray = new JSONArray();
                boundsArray.put(bounds.left);
                boundsArray.put(bounds.top);
                boundsArray.put(bounds.right);
                boundsArray.put(bounds.bottom);
                obj.put("bounds", boundsArray);

                obj.put("centerX", bounds.centerX());
                obj.put("centerY", bounds.centerY());

                array.put(obj);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing node", e);
            }
        }
        return array.toString();
    }

    private void traverseTree(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseTree(child, list);
            }
        }
    }

    // =========================================================================
    // Autonomous Execution Engine (WhatsApp, Apps, Gestures, On-screen)
    // =========================================================================

    public void executeAutonomousVoiceCommand(String rawQuery) {
        new Thread(() -> {
            try {
                String query = rawQuery.toLowerCase(Locale.forLanguageTag("tr-TR")).trim();
                Log.i(TAG, "Autonomous Engine Processing: " + query);

                // 1. Navigation Commands
                if (query.equals("ana ekran") || query.equals("ana ekrana dön") || query.equals("ev") || query.equals("home")) {
                    performGlobal("home");
                    speak("Ana ekrana dönüldü");
                    return;
                }
                if (query.equals("geri") || query.equals("geri dön") || query.equals("back")) {
                    performGlobal("back");
                    return;
                }
                if (query.contains("bildirim") || query.contains("bildirimleri aç")) {
                    performGlobal("notifications");
                    return;
                }
                if (query.contains("son uygulamalar") || query.contains("açık uygulamalar")) {
                    performGlobal("recents");
                    return;
                }

                // 2. WhatsApp Messaging Intent
                // Examples:
                // "whatsapptan orhana ne yapıyorsun yaz"
                // "orhan'a ne yapıyorsun yaz"
                // "orhana mesaj at ne yapıyorsun"
                // "orhanı bul"
                if (query.contains("whatsapp") || query.contains("orhan") || query.contains("mesaj") || query.endsWith("yaz") || query.endsWith("gönder")) {
                    String contact = extractContact(query);
                    String message = extractMessage(query, contact);
                    executeWhatsAppFlow(contact, message);
                    return;
                }

                // 3. App Launch Command
                if (query.startsWith("aç ") || query.endsWith(" aç") || query.endsWith(" başlat") || query.endsWith(" uygulamasını aç")) {
                    String appName = query.replace("uygulamasını aç", "")
                            .replace("uygulamayı aç", "")
                            .replace("aç", "")
                            .replace("başlat", "")
                            .trim();
                    launchAppByName(appName);
                    return;
                }

                // 4. Default Agent: Scan Screen and Click matching element
                AccessibilityNodeInfo targetNode = findNodeByTextOrDesc(query);
                if (targetNode != null) {
                    clickNode(targetNode);
                    speak(query + " tıklandı");
                } else {
                    speak("Anlaşıldı: " + query);
                }

            } catch (Exception e) {
                Log.e(TAG, "Command execution failed", e);
                speak("İşlem sırasında bir sorun oluştu.");
            }
        }).start();
    }

    private String extractContact(String query) {
        if (query.contains("orhan")) return "Orhan";

        // Try extracting words before suffix
        String[] words = query.split("\\s+");
        for (String w : words) {
            if (w.equals("whatsapp") || w.equals("whatsapptan") || w.equals("mesaj") || w.equals("yaz") || w.equals("bul") || w.equals("ara")) continue;
            // Clean suffix
            String clean = w.replaceAll("('a|'e|'ı|'i|'ya|'ye|a|e|ı|i)$", "");
            if (clean.length() >= 3) {
                return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
            }
        }
        return "Orhan";
    }

    private String extractMessage(String query, String contact) {
        String contactLower = contact.toLowerCase(Locale.forLanguageTag("tr-TR"));
        String msg = query;

        // Strip prefix (whatsapptan, orhana, vb.)
        int idx = msg.indexOf(contactLower);
        if (idx != -1) {
            msg = msg.substring(idx + contactLower.length());
        } else {
            msg = msg.replace("whatsapptan", "").replace("whatsapp", "");
        }

        // Clean suffixes at start: "'a", "'e", "a", "e", "ı", "i"
        msg = msg.replaceAll("^('a|'e|'ı|'i|'ya|'ye|a|e|ı|i)\\s*", "").trim();
        // Clean leading words like "mesaj at", "mesaj yaz", "de", "diye"
        msg = msg.replaceAll("^(mesaj at|mesaj yaz|mesaj|de|yaz)\\s*", "").trim();

        // Strip suffix command words: "yaz", "gönder", "mesaj at", "söyle"
        msg = msg.replaceAll("\\s*(yaz|gönder|mesajı at|mesaj at|söyle|ilet)$", "").trim();

        return msg;
    }

    public void executeWhatsAppFlow(String contact, String message) {
        try {
            speak("WhatsApp açılıyor...");
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage("com.whatsapp");
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
            }

            // Step 1: Wait for WhatsApp to be in the foreground
            Thread.sleep(1500);

            // Step 2: Open Search Bar
            AccessibilityNodeInfo searchBar = waitForNode("com.whatsapp:id/search_bar_inner_layout", "Ara", 3000);
            if (searchBar != null) {
                clickNode(searchBar);
            } else {
                // Fallback top search bar area (centerX ~ 720, centerY ~ 442)
                click(720, 442);
            }
            Thread.sleep(800);

            // Step 3: Type Contact Name
            AccessibilityNodeInfo searchInput = waitForNode("com.whatsapp:id/search_input", null, 2500);
            if (searchInput != null) {
                setText(searchInput, contact);
            } else {
                typeText(contact);
            }

            speak(contact + " aranıyor...");
            Thread.sleep(1500);

            // Step 4: Click matching contact in results
            AccessibilityNodeInfo contactNode = waitForNode(null, contact, 3000);
            boolean contactClicked = false;
            if (contactNode != null) {
                contactClicked = clickNode(contactNode);
            }
            if (!contactClicked) {
                AccessibilityNodeInfo row = findNodeById("com.whatsapp:id/contact_row_container");
                if (row != null) {
                    contactClicked = clickNode(row);
                }
            }
            if (!contactClicked) {
                // Fallback to first search result row center (centerX=720, centerY=622)
                click(720, 622);
            }

            Thread.sleep(1200);

            // Step 5: If message text is provided, enter message and send
            if (message != null && !message.trim().isEmpty()) {
                speak("Mesaj yazılıyor...");
                AccessibilityNodeInfo entry = waitForNode("com.whatsapp:id/entry", null, 3000);
                if (entry != null) {
                    setText(entry, message);
                } else {
                    typeText(message);
                }

                Thread.sleep(800);

                // Click Send Button
                AccessibilityNodeInfo sendBtn = waitForNode("com.whatsapp:id/send", "Gönder", 2000);
                boolean sendClicked = false;
                if (sendBtn != null) {
                    sendClicked = clickNode(sendBtn);
                }
                if (!sendClicked) {
                    // Send button coordinates on 1440x3120: (1315, 2830)
                    click(1315, 2830);
                }

                speak("Mesaj gönderildi.");
            } else {
                speak(contact + " ile sohbet açıldı.");
            }

        } catch (Exception e) {
            Log.e(TAG, "WhatsApp autonomous execution error", e);
            speak("WhatsApp işlemi tamamlanamadı.");
        }
    }

    private void launchAppByName(String appName) {
        speak(appName + " açılıyor...");
        PackageManager pm = getPackageManager();
        String q = appName.toLowerCase(Locale.forLanguageTag("tr-TR")).trim();

        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.forLanguageTag("tr-TR"));
            if (label.contains(q)) {
                Intent intent = pm.getLaunchIntentForPackage(app.packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }
            }
        }
        speak(appName + " uygulaması bulunamadı.");
    }
}

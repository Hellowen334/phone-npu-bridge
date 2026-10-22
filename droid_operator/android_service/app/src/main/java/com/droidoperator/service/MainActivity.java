package com.droidoperator.service;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private Button btnEnableAccessibility;
    private Button btnEnableOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simple programmatic layout - zero dependency
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 100, 60, 60);

        TextView title = new TextView(this);
        title.setText("DroidOperator Engine");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 40);
        layout.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 60);
        layout.addView(statusText);

        btnEnableAccessibility = new Button(this);
        btnEnableAccessibility.setText("1. Erişilebilirlik İznini Aç");
        btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnEnableAccessibility);

        btnEnableOverlay = new Button(this);
        btnEnableOverlay.setText("2. Diğer Uygulamaların Üzerinde Göster");
        btnEnableOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        layout.addView(btnEnableOverlay);

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean isServiceRunning = DroidAccessibilityService.INSTANCE != null;
        if (isServiceRunning) {
            statusText.setText("DURUM: AKTİF (Port 8765 Dinleniyor)\n\nYapay Zeka motoru artık telefonu bağımsız yönetebilir.");
            statusText.setTextColor(0xFF00AA00);
        } else {
            statusText.setText("DURUM: BEKLENİYOR\n\nLütfen aşağıdaki butondan Erişilebilirlik iznini verin.");
            statusText.setTextColor(0xFFAA0000);
        }
    }
}

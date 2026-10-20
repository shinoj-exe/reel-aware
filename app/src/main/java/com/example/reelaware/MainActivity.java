package com.example.reelaware;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private ReelCounterManager counterManager;
    private EditText etLimit;
    
    private Button btnAccessibility;
    private TextView tvAccessStatus;
    
    private Button btnOverlay;
    private TextView tvOverlayStatus;
    
    private Button btnNotif;

    private View settingsLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        counterManager = new ReelCounterManager(this);

        // --- Init Views ---
        btnAccessibility = findViewById(R.id.btnOpenSettings);
        tvAccessStatus = findViewById(R.id.tvAccessibilityStatus);
        
        btnOverlay = findViewById(R.id.btnOverlayPermission);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);
        
        btnNotif = findViewById(R.id.btnNotificationPermission);
        settingsLayout = findViewById(R.id.settingsLayout);

        etLimit = findViewById(R.id.etLimit);
        etLimit.setText(String.valueOf(counterManager.getLimit()));

        // --- Listeners ---
        btnAccessibility.setOnClickListener(v -> {
            startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        
        btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        });

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            btnNotif.setOnClickListener(v -> {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            });
        }
        
        android.widget.EditText etBatch = findViewById(R.id.etBatchSize);
        etBatch.setText(String.valueOf(counterManager.getBatchSize()));

        findViewById(R.id.btnSaveLimit).setOnClickListener(v -> {
            String txtLimit = etLimit.getText().toString();
            String txtBatch = etBatch.getText().toString();
            
            try {
                if (!txtLimit.isEmpty()) {
                    int val = Integer.parseInt(txtLimit);
                    counterManager.setLimit(val);
                }
                
                if (!txtBatch.isEmpty()) {
                    int val = Integer.parseInt(txtBatch);
                    if (val > 0) {
                        counterManager.setBatchSize(val);
                    }
                }
                
                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
            }
        });

        android.widget.Switch switchNotif = findViewById(R.id.switchNotifMode);
        switchNotif.setChecked(counterManager.isNotificationMode());
        switchNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            counterManager.setNotificationMode(isChecked);
        });

        findViewById(R.id.btnResetCount).setOnClickListener(v -> {
            counterManager.reset();
            Toast.makeText(this, "Count reset to 0", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUI();
    }

    private void updatePermissionUI() {
        // 1. Accessibility Check
        boolean isServiceEnabled = isAccessibilityServiceEnabled();
        if (isServiceEnabled) {
            btnAccessibility.setText("Service Enabled");
            btnAccessibility.setEnabled(false);
            tvAccessStatus.setText("Status: Granted ✅");
            tvAccessStatus.setTextColor(0xFF4CAF50); // Green
        } else {
            btnAccessibility.setText("Enable Service");
            btnAccessibility.setEnabled(true);
            tvAccessStatus.setText("Status: Not Granted ❌");
            tvAccessStatus.setTextColor(0xFFD32F2F); // Red
        }

        // 2. Overlay Check
        boolean isOverlayGranted = Settings.canDrawOverlays(this);
        if (isOverlayGranted) {
            btnOverlay.setText("Permission Granted");
            btnOverlay.setEnabled(false);
            tvOverlayStatus.setText("Status: Granted ✅");
            tvOverlayStatus.setTextColor(0xFF4CAF50); // Green
        } else {
            btnOverlay.setText("Enable Overlay");
            btnOverlay.setEnabled(true);
            tvOverlayStatus.setText("Status: Not Granted ❌");
            tvOverlayStatus.setTextColor(0xFFD32F2F); // Red
        }

        // 3. Notification Check (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            boolean isNotifGranted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (!isNotifGranted) {
                btnNotif.setVisibility(android.view.View.VISIBLE);
            } else {
                btnNotif.setVisibility(android.view.View.GONE);
            }
        }

        // 4. Enable settings layout only if both core permissions are granted
        
        boolean allGood = isServiceEnabled && isOverlayGranted;
        settingsLayout.setAlpha(allGood ? 1.0f : 0.5f);

        etLimit.setEnabled(allGood);
        findViewById(R.id.btnSaveLimit).setEnabled(allGood);
    }


    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + MyService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    this.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // ignored
        }
        android.text.TextUtils.SimpleStringSplitter mStringColonSplitter = new android.text.TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
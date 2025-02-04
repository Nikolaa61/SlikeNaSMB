package com.example.slikenasmb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 100;
    private TextView statusTextView;
    private final List<String> messageQueue = new ArrayList<>();
    private final StringBuilder logText = new StringBuilder();
    private static final int MAX_LINES = 5;

    private final Handler handler = new Handler();

    @Override
    protected void onStart() {
        super.onStart();

        if (!checkPermissions()) {
            logMessage("Dozvole nisu dodeljene!");
        } else {
            logMessage("Dozvole su dodeljene, pokrećem servis.");
        }
        // Tek kada imamo dozvole, pokreni servis
        if (checkPermissions()) {
            Intent intent = new Intent(this, UploadService.class);
            startService(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);


        requestPermissions();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE);
            }
        }
    }

    // Metoda za proveru dozvola
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6+
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void logMessage(String message) {
        Log.d("MainActivity", message);
        runOnUiThread(() -> {
            if (logText.length() > 500) { // Ograničavamo veličinu teksta
                logText.delete(0, logText.indexOf("\n") + 1);
            }
            logText.append(message).append("\n");
            statusTextView.setText(logText.toString());
        });
    }

    private BroadcastReceiver uploadStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            logMessage(message);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(uploadStatusReceiver, new IntentFilter("UPLOAD_STATUS"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(uploadStatusReceiver);
    }
}
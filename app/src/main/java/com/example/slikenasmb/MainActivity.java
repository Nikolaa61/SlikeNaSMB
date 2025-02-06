package com.example.slikenasmb;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 100;
    private static final String PREFS_NAME = "SmbSettings";
    private static final String KEY_SMB_URL = "smb_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private String smbUrl = "smb://192.168.1.99/Seagate_Expansion_1_d662/Slike sa telefona/";
    private String username = "vodafone";
    private String password = "P6gJtZfH";

    private TextView statusTextView;
    private final StringBuilder logText = new StringBuilder();


    @Override
    protected void onStart() {
        super.onStart();


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        loadSettings(); // Učitavamo sačuvane vrednosti

        statusTextView = findViewById(R.id.statusTextView);

        if (!checkPermissions()) {
            logMessage("Dozvole nisu dodeljene!");
        } else {
            logMessage("Dozvole su dodeljene, pokrećem servis.");
        }
        // Tek kada imamo dozvole, pokreni servis
        if (checkPermissions()) {
            showSettingsDialog(); // Prikazujemo dijalog za potvrdu
//            Intent intent = new Intent(this, UploadService.class);
//            startService(intent);
        }

    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Podešavanja SMB konekcije");
        builder.setMessage("Da li želite da koristite podrazumevane vrednosti za SMB konekciju?");

        builder.setPositiveButton("Da", (dialog, which) -> startUploadService());
        builder.setNegativeButton("Ne", (dialog, which) -> showInputDialog());

        builder.setCancelable(false);
        builder.show();
    }

    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Unesite SMB parametre");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText urlInput = new EditText(this);
        urlInput.setHint("SMB URL");
        urlInput.setText(smbUrl);
        layout.addView(urlInput);

        final EditText usernameInput = new EditText(this);
        usernameInput.setHint("Korisničko ime");
        usernameInput.setText(username);
        layout.addView(usernameInput);

        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("Lozinka");
        passwordInput.setText(password);
        layout.addView(passwordInput);

        builder.setView(layout);

        builder.setPositiveButton("Sačuvaj", (dialog, which) -> {
            smbUrl = urlInput.getText().toString();
            username = usernameInput.getText().toString();
            password = passwordInput.getText().toString();
            saveSettings();
            startUploadService();
        });

        builder.setNegativeButton("Otkaži", (dialog, which) -> finish());

        builder.setCancelable(false);
        builder.show();
    }

    private void startUploadService() {
        Intent intent = new Intent(this, UploadService.class);
        intent.putExtra("SMB_URL", smbUrl);
        intent.putExtra("USERNAME", username);
        intent.putExtra("PASSWORD", password);
        startService(intent);
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SMB_URL, smbUrl);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        smbUrl = prefs.getString(KEY_SMB_URL, smbUrl);
        username = prefs.getString(KEY_USERNAME, username);
        password = prefs.getString(KEY_PASSWORD, password);
    }


    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_CODE);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_CODE);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
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
            if (logText.length() > 400) { // Ograničavamo veličinu teksta
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
        IntentFilter filter = new IntentFilter("UPLOAD_STATUS");
        LocalBroadcastManager.getInstance(this).registerReceiver(uploadStatusReceiver, filter);

    }


    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadStatusReceiver);
    }
}
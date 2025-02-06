package com.example.slikenasmb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

public class UploadService extends Service {
    private static  String SMB_URL = "smb://192.168.1.99/Seagate_Expansion_1_d662/Slike sa telefona/";
    private static  String USERNAME = "vodafone";
    private static  String PASSWORD = "P6gJtZfH";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "UploadServiceChannel";

    private UploadDatabaseHelper dbHelper;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        initNotification(); // Pokreni notifikaciju u foreground modu
//        startForegroundService();

        SMB_URL = intent.getStringExtra("SMB_URL");
        USERNAME = intent.getStringExtra("USERNAME");
        PASSWORD = intent.getStringExtra("PASSWORD");

        sendStatusUpdate("UploadService je pokrenut.");
        dbHelper = new UploadDatabaseHelper(getApplicationContext());
        new Thread(this::uploadImages).start();
        return START_STICKY;
    }

    private void initNotification() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Upload Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Kreiraj Intent za prekid servisa
        Intent stopIntent = new Intent(this, StopServiceReceiver.class);
        stopIntent.setAction("STOP_SERVICE");  // Dodajemo eksplicitnu akciju
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Upload u toku")
                .setContentText("Priprema fajlova za slanje...")
                .setSmallIcon(R.drawable.ic_upload2)
                .setOngoing(true)
                .setProgress(100, 0, true) // Progress bar bez definisanog napretka
                .addAction(R.drawable.x, "Zaustavi", stopPendingIntent); // Dugme za prekid


        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateNotification(int progress, int totalFiles) {
        notificationBuilder
                .setContentText("Prebacivanje fajlova: " + progress + "/" + totalFiles)
                .setProgress(totalFiles, progress, false);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Upload Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Upload u toku")
                .setContentText("Aplikacija prebacuje fajlove na server...")
                .setSmallIcon(R.drawable.ic_upload2) // Dodaj ikonicu u res/drawable/ic_upload.png
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void completeNotification() {
        notificationBuilder
                .setContentText("Upload završen")
                .setProgress(0, 0, false)
                .setOngoing(false);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void uploadImages() {
        List<File> images = getAllImages();
        int totalFiles = images.size();
        int uploadedFiles = 0;

        for (File file : images) {
            if (!dbHelper.isFileUploaded(file.getName())) {
                sendImageToSMB(file);
                uploadedFiles++;
                updateNotification(uploadedFiles, totalFiles); // Ažuriraj napredak
            } else {
                uploadedFiles++;
                sendStatusUpdate("File already uploaded, skipping: " + file.getName());
            }
        }

        sendStatusUpdate("Zavrsen upload slika");
        completeNotification(); // Završna notifikacija
        stopSelf();
    }

    private List<File> getAllImages() {
        sendStatusUpdate("Pokrenuto učitavanje slika...");

        List<File> imageFiles = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                sendStatusUpdate("Nemamo dozvolu za čitanje slika i videa!");
                return imageFiles;
            }
        } else { // Android 12 i niže
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                sendStatusUpdate("Nemamo dozvolu za čitanje slika!");
                return imageFiles;
            }
        }
        Uri uri = MediaStore.Files.getContentUri("external");

        String[] projection = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE};

        // Filtriramo samo slike i video fajlove
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
                MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
        String[] selectionArgs = {
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        };
        try (Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
                    File file = new File(filePath);
                    if (file.exists()) {
                        Log.d("UploadSe  rvice", "Pronađena slika: " + filePath);
                        imageFiles.add(file);
                    }
                }
            }
        } catch (Exception e) {
            sendStatusUpdate("Error loading images " + e);
        }
        sendStatusUpdate("Završeno učitavanje slika, pronađeno: " + imageFiles.size() + " slika.");


        return imageFiles;
    }

    private void sendImageToSMB(File file) {
        sendStatusUpdate("Pokušavam da pošaljem: " + file.getName());
        if (!file.exists()) {
            Log.e("UploadService", "FAJL NE POSTOJI: " + file.getAbsolutePath());
        }
        try {
            String smbPath = SMB_URL + file.getName();
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", USERNAME, PASSWORD);
            SmbFile smbFile = new SmbFile(smbPath, auth);
        /*    Properties properties = new Properties();
            properties.setProperty("jcifs.smb.client.minVersion", "SMB202");
            properties.setProperty("jcifs.smb.client.maxVersion", "SMB311");

            CIFSContext baseContext = new BaseContext(new PropertyConfiguration(properties));
            CIFSContext authContext = baseContext.withCredentials(new NtlmPasswordAuthenticator(USERNAME, PASSWORD));

            SmbFile smbFile = new SmbFile(smbPath, authContext);*/

            if (smbFile.exists()) {
                sendStatusUpdate("File already exists on SMB, skipping: " + file.getName());
                Log.d("UploadService", "File already exists on SMB, skipping: " + file.getName());
                return;
            }
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = new SmbFileOutputStream(smbFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            sendStatusUpdate("File uploaded successfully: " + file.getName());
            Log.d("UploadService", "File uploaded successfully: " + file.getName());
            dbHelper.markFileAsUploaded(file.getName());

        } catch (IOException e) {
            sendStatusUpdate("Error uploading file to SMB " + e);
            Log.e("UploadService", "Error uploading file to SMB", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendStatusUpdate(String message) {
        Intent intent = new Intent("UPLOAD_STATUS");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d("UploadService", "Status update sent: " + message);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        sendStatusUpdate("Upload servis zaustavljen.");
    }
}
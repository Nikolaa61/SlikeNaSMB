package com.example.slikenasmb;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
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
    private static final String SMB_URL = "smb://192.168.1.99/Seagate_Expansion_1_d662/Slike sa telefona/";
    private static final String USERNAME = "vodafone";
    private static final String PASSWORD = "P6gJtZfH";

    private UploadDatabaseHelper dbHelper;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sendStatusUpdate("UploadService je pokrenut.");
        dbHelper = new UploadDatabaseHelper(getApplicationContext());
        new Thread(this::uploadImages).start();
        return START_STICKY;
    }

    private void uploadImages() {
        List<File> images = getAllImages();
        for (File file : images) {
            if (!dbHelper.isFileUploaded(file.getName())) {
                sendImageToSMB(file);
            } else {
                sendStatusUpdate("File already uploaded, skipping: " + file.getName());
            }
        }
    }

    private List<File> getAllImages() {
        sendStatusUpdate("Pokrenuto učitavanje slika...");

        List<File> imageFiles = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                sendStatusUpdate("Nemamo dozvolu za čitanje slika!");
                return imageFiles;
            }
        } else { // Android 12 i niže
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                sendStatusUpdate("Nemamo dozvolu za čitanje slika!");
                return imageFiles;
            }
        }
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media.DATA};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    File file = new File(imagePath);
                    if (file.exists()) {
                        Log.d("UploadService", "Pronađena slika: " + imagePath);
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
        sendBroadcast(intent);
    }
}
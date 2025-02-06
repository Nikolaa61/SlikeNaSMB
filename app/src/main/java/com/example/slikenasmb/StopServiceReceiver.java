package com.example.slikenasmb;

import android.content.BroadcastReceiver;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class StopServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            Toast.makeText(context, "Gasim servis",Toast.LENGTH_LONG).show();
            context.stopService(new Intent(context, UploadService.class));
        }else{
            Toast.makeText(context, "Greska u stopiranju",Toast.LENGTH_LONG).show();
        }
    }
}
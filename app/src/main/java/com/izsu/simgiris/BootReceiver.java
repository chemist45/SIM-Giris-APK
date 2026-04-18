package com.izsu.simgiris;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Telefon yeniden başladığında SMS servisini otomatik başlatır.
 * Android 12+ için BOOT_COMPLETED sonrası foreground service başlatmak
 * açıkça izin verilen durumlardan biridir.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        try {
            Intent servis = new Intent(context, SmsService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(servis);
            } else {
                context.startService(servis);
            }
            Log.d(TAG, "SmsService boot sonrası başlatıldı");
        } catch (Exception e) {
            // Android 15 bazı OEM'lerde boot sonrası kısıtlama uygulayabilir
            Log.e(TAG, "Boot sonrası servis başlatılamadı: " + e.getMessage());
        }
    }
}

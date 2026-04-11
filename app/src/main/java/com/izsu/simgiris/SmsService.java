package com.izsu.simgiris;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * SMS alımını arka planda canlı tutar.
 * Telefon ekranı kapalı olsa bile SMS'ler GAS'a iletilir.
 */
public class SmsService extends Service {
    private static final String KANAL_ID = "SimGirisSmsKanal";
    private static final int BILDIRIM_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        bildirimKanaliOlustur();
        startForeground(BILDIRIM_ID, bildirimOlustur());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Sistem tarafından öldürülürse otomatik yeniden başla
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void bildirimKanaliOlustur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel kanal = new NotificationChannel(
                KANAL_ID,
                "SMS İletim",
                NotificationManager.IMPORTANCE_LOW
            );
            kanal.setDescription("SİM OTP SMS'lerini arka planda iletir");
            kanal.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(kanal);
        }
    }

    private Notification bildirimOlustur() {
        return new NotificationCompat.Builder(this, KANAL_ID)
            .setContentTitle("SİM Giriş")
            .setContentText("SMS iletim aktif ✓")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }
}

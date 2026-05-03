package com.izsu.simgiris;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

/**
 * SMS alımını arka planda canlı tutar.
 * Telefon ekranı kapalı olsa bile SMS'ler GAS'a iletilir.
 */
public class SmsService extends Service {
    private static final String KANAL_ID = "SimGirisSmsKanal";
    private static final int BILDIRIM_ID = 1001;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        bildirimKanaliOlustur();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ : dataSync artık 6-saatlik limit getiriyor.
                // SMS OTP forward özel kullanım — specialUse tipi sınırsız.
                startForeground(BILDIRIM_ID, bildirimOlustur(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(BILDIRIM_ID, bildirimOlustur(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(BILDIRIM_ID, bildirimOlustur());
            }
        } catch (Exception e) {
            // Android 12+ ForegroundServiceStartNotAllowedException yakalanırsa servis yine de yaşamaya devam eder
            android.util.Log.e("SmsService", "startForeground hata: " + e.getMessage());
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimGiris:SmsWakeLock");
            wakeLock.setReferenceCounted(false);
            // Android 16: zaman aşımı zorunlu — 24 saatte bir servis yeniden başlar (START_STICKY)
            wakeLock.acquire(24 * 60 * 60 * 1000L);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
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

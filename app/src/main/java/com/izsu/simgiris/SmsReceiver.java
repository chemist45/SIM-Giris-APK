package com.izsu.simgiris;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    // GAS URL — tüm tesisler için aynı
    private static final String GAS_URL =
        "https://script.google.com/macros/s/AKfycbwF8vf2dxdhVEuhGDum0wl4KQMkuosUh_qoxOPOXxlRa0LPQhYITLSmg5haTVBDc6_F/exec";

    // Firebase Realtime Database — SMS log (kurallar: sms_log .write: true)
    private static final String RTDB_URL =
        "https://sim-giris-yarimada-default-rtdb.firebaseio.com/sms_log.json";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Android 14+'ta SMS broadcast'ı işleme süresi kısıtlı (10s ANR).
        // goAsync ile receiver'ın yaşam süresini HTTP çağrıları bitene kadar uzat.
        final PendingResult pending = goAsync();

        try {
            Bundle bundle = intent.getExtras();
            if (bundle == null) { pending.finish(); return; }

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) { pending.finish(); return; }

            String format = bundle.getString("format");
            StringBuilder tamMesaj = new StringBuilder();
            String gonderen = "";

            for (Object pdu : pdus) {
                SmsMessage msg;
                if (format != null) {
                    msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    msg = SmsMessage.createFromPdu((byte[]) pdu);
                }
                if (msg == null) continue;
                tamMesaj.append(msg.getMessageBody());
                gonderen = msg.getOriginatingAddress();
            }

            final String icerik = tamMesaj.toString();
            final String gonderenFinal = gonderen != null ? gonderen : "";
            Log.d(TAG, "SMS alındı | Gönderen: " + gonderenFinal + " | İçerik: " + icerik);

            SharedPreferences prefs = context.getSharedPreferences(AndroidBridge.PREFS, Context.MODE_PRIVATE);
            final String aktivTesis = prefs.getString("activeTesis", "");

            // Her iki ağ çağrısı da bitince pending.finish() çağrılır.
            final AtomicInteger kalan = new AtomicInteger(2);
            final Runnable bitir = () -> { if (kalan.decrementAndGet() == 0) pending.finish(); };

            gasaGonder(icerik, gonderenFinal, aktivTesis, bitir);
            rtdbYaz(icerik, gonderenFinal, aktivTesis, bitir);

        } catch (Throwable t) {
            Log.e(TAG, "onReceive hata: " + t.getMessage());
            pending.finish();
        }
    }

    private void gasaGonder(String icerik, String gonderen, String aktivTesis, Runnable done) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject json = new JSONObject();
                json.put("action", "smsWebhook");
                json.put("body", icerik);
                json.put("from", gonderen);
                json.put("tesis", aktivTesis);

                URL url = new URL(GAS_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);

                byte[] veri = json.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(veri);
                }

                int yanit = conn.getResponseCode();
                Log.d(TAG, "GAS yanıtı: " + yanit + " | Tesis: " + aktivTesis);

            } catch (Exception e) {
                Log.e(TAG, "GAS iletme hatası: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
                done.run();
            }
        }).start();
    }

    private void rtdbYaz(String icerik, String gonderen, String tesisId, Runnable done) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject json = new JSONObject();
                json.put("tesis",     tesisId);
                json.put("body",      icerik);
                json.put("from",      gonderen);
                json.put("timestamp", System.currentTimeMillis());

                URL url = new URL(RTDB_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                byte[] veri = json.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(veri);
                }

                int yanit = conn.getResponseCode();
                Log.d(TAG, "RTDB yanıtı: " + yanit);

            } catch (Exception e) {
                Log.e(TAG, "RTDB yazma hatası: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
                done.run();
            }
        }).start();
    }
}

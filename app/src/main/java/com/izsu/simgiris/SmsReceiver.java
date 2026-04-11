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

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    // GAS URL — tüm tesisler için aynı
    private static final String GAS_URL =
        "https://script.google.com/macros/s/AKfycbwEbdaQ1MXdJT3Wf7NI1OQB6Om67nup5e5x3RPdKzxdXJNYbg34iYkEkFnXEmuhI2Z6aQ/exec";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

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
            tamMesaj.append(msg.getMessageBody());
            gonderen = msg.getOriginatingAddress();
        }

        String icerik = tamMesaj.toString();
        Log.d(TAG, "SMS alındı | Gönderen: " + gonderen + " | İçerik: " + icerik);

        // GAS'a ilet
        gasaGonder(context, icerik, gonderen != null ? gonderen : "");
    }

    private void gasaGonder(Context context, String icerik, String gonderen) {
        SharedPreferences prefs = context.getSharedPreferences(AndroidBridge.PREFS, Context.MODE_PRIVATE);
        String aktivTesis = prefs.getString("activeTesis", "");

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "smsWebhook");
                json.put("body", icerik);
                json.put("from", gonderen);
                json.put("tesis", aktivTesis);

                URL url = new URL(GAS_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "GAS iletme hatası: " + e.getMessage());
            }
        }).start();
    }
}

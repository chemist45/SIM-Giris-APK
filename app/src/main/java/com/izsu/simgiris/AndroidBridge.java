package com.izsu.simgiris;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AndroidBridge {
    private final Context context;
    static final String PREFS = "SimGirisPrefs";

    public AndroidBridge(Context context) {
        this.context = context;
    }

    // HTML sayfası açıldığında hangi tesisin aktif olduğunu kaydeder
    @JavascriptInterface
    public void setActiveTesis(String tesisId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString("activeTesis", tesisId).apply();
    }

    @JavascriptInterface
    public String getActiveTesis() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString("activeTesis", "");
    }

    // Android uygulaması içinde olduğunu HTML'e bildirir
    @JavascriptInterface
    public boolean isAndroidApp() {
        return true;
    }

    @JavascriptInterface
    public void toast(String mesaj) {
        Toast.makeText(context, mesaj, Toast.LENGTH_SHORT).show();
    }

    // GAS bağlantı testi — Java'dan HTTP isteği atar (CORS yok)
    @JavascriptInterface
    public String pingGas(String gasUrl) {
        try {
            URL url = new URL(gasUrl + "?action=ping");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            if (code == 200) return "{\"ok\":true}";
            return "{\"ok\":false,\"error\":\"HTTP " + code + "\"}";
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // GAS'a POST isteği atar ve yanıtı döner — file:// CORS sorununu aşar
    @JavascriptInterface
    public String gasPost(String gasUrl, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(gasUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);

            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(data); }

            int code = conn.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 400)
                ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "{\"ok\":false,\"error\":\"bos yanit\"}";

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + e.getMessage() + "\"}";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

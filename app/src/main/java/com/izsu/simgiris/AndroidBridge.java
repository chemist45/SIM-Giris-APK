package com.izsu.simgiris;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;

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
}

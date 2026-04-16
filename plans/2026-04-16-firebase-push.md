# Firebase Push Notification — İmplementasyon Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SMS geldiğinde hem APK hem de web sitesini açık tutan kullanıcılara Firebase üzerinden push notification göndermek; aynı zamanda Firebase Realtime Database'e SMS log yazmak.

**Architecture:**
APK SMS alır → (1) Mevcut GAS POST korunur → GAS, FCM HTTP API ile kayıtlı web tokenlarına push gönderir. Aynı anda (2) APK, Firebase Realtime Database REST API'ye ayrı bir HTTP POST ile log yazar. Web sitesi ilk açıldığında FCM token alır, GAS'a kaydeder.

**Tech Stack:** Firebase Realtime Database REST API, Firebase Cloud Messaging (Legacy HTTP API), Firebase JS SDK v9 (compat), Web Push API (VAPID), Android Java (HttpURLConnection), Google Apps Script

---

## ÖNCE: Firebase Console Kurulumu (Manuel — Bilgisayardan Yapılacak)

Bu adımlar kod yazmadan önce tamamlanmalıdır. Sonuçta 4 değer elde edilecek.

- [ ] **Adım A: Firebase Projesi Oluştur**

  1. https://console.firebase.google.com/ adresine git
  2. "Proje ekle" → Proje adı: `sim-giris-yarimada`
  3. Google Analytics: "Şimdilik etkinleştirme" → "Proje oluştur"

- [ ] **Adım B: Web Uygulaması Kaydet → firebaseConfig Al**

  1. Projeye gir → Genel Bakış sayfasında `</>` (Web) ikonuna tıkla
  2. Uygulama adı: `sim-giris-web` → "Bu uygulama için Firebase Hosting'i de ayarla" → **işaretleme**
  3. "Uygulamayı kaydet" → Sayfada görünen `firebaseConfig` nesnesini kopyala ve bir yere kaydet:
     ```js
     const firebaseConfig = {
       apiKey: "...",
       authDomain: "...",
       projectId: "...",
       storageBucket: "...",
       messagingSenderId: "...",
       appId: "..."
     };
     ```

- [ ] **Adım C: FCM Server Key + VAPID Key Al**

  1. Firebase Console → Sol menü: "Build" → "Cloud Messaging"
  2. Sayfa yüklenince üstte "Cloud Messaging API (Legacy)" bölümü görünür
     - Eğer "Etkinleştir" linki varsa tıkla → birkaç saniye bekle → sayfayı yenile
  3. "Sunucu anahtarı" satırındaki değeri kopyala → bu **FCM_SERVER_KEY** (örn: `AAAA...`)
  4. Aynı sayfada aşağı kaydır → "Web yapılandırması" → "Anahtar çifti oluştur" (daha önce oluşturulmadıysa)
  5. Oluşturulan **VAPID anahtarını** kopyala (uzun bir base64 string)

- [ ] **Adım D: Realtime Database Oluştur + Secret Al**

  1. Sol menü: "Build" → "Realtime Database" → "Veritabanı oluştur"
  2. Konum: "United States (us-central1)" → "Sonraki"
  3. "Kilitli modda başlat" → "Etkinleştir"
  4. Veritabanı oluşunca URL'yi not et: `https://sim-giris-yarimada-default-rtdb.firebaseio.com`
     (Tam URL sayfanın üstünde gösterilir)
  5. Kuralları düzenle: "Kurallar" sekmesi → aşağıdaki kuralı yapıştır → "Yayınla":
     ```json
     {
       "rules": {
         "sms_log": {
           ".read": false,
           ".write": true
         }
       }
     }
     ```
  6. Database Secret al: Sol altta ⚙ (Proje ayarları) → "Hizmet hesapları" sekmesi →
     En alt "Veritabanı gizli anahtarları" bölümü → "Gizli anahtar ekle" → değeri kopyala
     Bu **RTDB_SECRET** (örn: `xxxxxxxxxxx`)

**Bu adımların sonunda elinizde şunlar olmalı:**
- `firebaseConfig` nesnesi (apiKey, projectId, messagingSenderId, appId dahil)
- `FCM_SERVER_KEY` (sunucu anahtarı)
- `VAPID_KEY` (web push key)
- `RTDB_URL` (örn: `https://sim-giris-yarimada-default-rtdb.firebaseio.com`)
- `RTDB_SECRET`

---

## Task 1: GAS — FcmTokens Sayfası + saveFcmToken Action

**Files:**
- Modify: `C:\Users\HP\Documents\SİM Giriş (openai-codex)\gas-kod.js`

**Amaç:** Web sitesi bildirim izni verince FCM token + tesisId'yi GAS Spreadsheet'e kaydeder. Böylece SMS gelince GAS hangi tokenlara push atacağını bilir.

- [ ] **Step 1: gas-kod.js — doPost içine `saveFcmToken` action ekle**

  `doPost` fonksiyonundaki `else result = { ok: false, error: 'Unknown action' };` satırından **önce** şu satırı ekle:

  ```js
  else if (action === 'saveFcmToken')     result = saveFcmToken(data);
  ```

  Yani bu satırdan önce:
  ```js
  else result = { ok: false, error: 'Unknown action' };
  ```
  Bu şekle getir:
  ```js
  else if (action === 'saveFcmToken')     result = saveFcmToken(data);
  else result = { ok: false, error: 'Unknown action' };
  ```

- [ ] **Step 2: gas-kod.js — saveFcmToken fonksiyonunu ekle**

  `getLatestCode` fonksiyonundan **önce** şu fonksiyonu ekle:

  ```js
  // ── FCM Token Kaydet (Web Push) ───────────────────────────────
  function saveFcmToken(data) {
    if (!data.token || !data.tesisId) return { ok: false, error: 'token ve tesisId gerekli' };
    const ss = SpreadsheetApp.openById(SHEET_ID);
    let sheet = ss.getSheetByName('FcmTokens');
    if (!sheet) {
      sheet = ss.insertSheet('FcmTokens');
      sheet.appendRow(['token', 'tesisId', 'timestamp']);
      sheet.getRange(1, 1, 1, 3).setFontWeight('bold').setBackground('#0ea5e9').setFontColor('#ffffff');
    }
    // Aynı token varsa güncelle (satırını bul)
    const rows = sheet.getDataRange().getValues();
    for (let i = 1; i < rows.length; i++) {
      if (rows[i][0] === data.token) {
        sheet.getRange(i + 1, 2).setValue(data.tesisId);
        sheet.getRange(i + 1, 3).setValue(new Date().toISOString());
        return { ok: true, updated: true };
      }
    }
    // Yeni satır ekle
    sheet.appendRow([data.token, data.tesisId, new Date().toISOString()]);
    return { ok: true, created: true };
  }
  ```

- [ ] **Step 3: GAS Script Properties'e FCM_SERVER_KEY kaydet**

  1. Google Apps Script editörünü aç (https://script.google.com)
  2. SİM Giriş projesini seç → Sol menü: "Proje ayarları" (⚙)
  3. "Komut dosyası özellikleri" → "Özellik ekle":
     - Özellik: `FCM_SERVER_KEY` → Değer: Console'dan aldığın sunucu anahtarı
  4. Kaydet

- [ ] **Step 4: GAS Script Properties'e RTDB_URL ve RTDB_SECRET kaydet**

  Aynı "Komut dosyası özellikleri" sayfasında 2 özellik daha ekle:
  - `RTDB_URL` → Değer: `https://sim-giris-yarimada-default-rtdb.firebaseio.com` (kendi URL'nle değiştir)
  - `RTDB_SECRET` → Değer: Firebase Console'dan aldığın database secret

---

## Task 2: GAS — smsWebhook'a FCM Push Ekleme

**Files:**
- Modify: `C:\Users\HP\Documents\SİM Giriş (openai-codex)\gas-kod.js`

**Amaç:** SMS gelince GAS, kayıtlı FCM tokenlarına web push notification gönderir.

- [ ] **Step 1: sendFcmPush fonksiyonunu ekle**

  `sendNtfy` fonksiyonundan **önce** şu fonksiyonu ekle:

  ```js
  // ── FCM Web Push Gönder ───────────────────────────────────────
  function sendFcmPush(tesisId, baslik, mesaj) {
    try {
      const serverKey = PropertiesService.getScriptProperties().getProperty('FCM_SERVER_KEY');
      if (!serverKey) return false;
      const ss = SpreadsheetApp.openById(SHEET_ID);
      const sheet = ss.getSheetByName('FcmTokens');
      if (!sheet) return false;
      const rows = sheet.getDataRange().getValues();
      const tokens = [];
      for (let i = 1; i < rows.length; i++) {
        if (rows[i][1] === tesisId && rows[i][0]) {
          tokens.push(rows[i][0]);
        }
      }
      if (tokens.length === 0) return false;
      let anySuccess = false;
      for (const token of tokens) {
        try {
          const payload = {
            to: token,
            notification: {
              title: baslik,
              body: mesaj,
              icon: '/icons/icon-192.png',
              badge: '/icons/icon-96.png',
              vibrate: [200, 100, 200]
            },
            data: {
              tesisId: tesisId,
              timestamp: new Date().toISOString()
            }
          };
          const res = UrlFetchApp.fetch('https://fcm.googleapis.com/fcm/send', {
            method: 'POST',
            headers: {
              'Authorization': 'key=' + serverKey,
              'Content-Type': 'application/json'
            },
            payload: JSON.stringify(payload),
            muteHttpExceptions: true
          });
          const code = res.getResponseCode();
          if (code === 200) anySuccess = true;
          // Geçersiz token: 400 veya yanıtta "invalid-registration" varsa sil
          if (code === 400 || res.getContentText().indexOf('NotRegistered') > -1 ||
              res.getContentText().indexOf('InvalidRegistration') > -1) {
            // Token'ı FcmTokens sayfasından sil
            const allRows = sheet.getDataRange().getValues();
            for (let r = 1; r < allRows.length; r++) {
              if (allRows[r][0] === token) { sheet.deleteRow(r + 1); break; }
            }
          }
        } catch (tokenErr) { /* tek token hatası diğerlerini durdurmaz */ }
      }
      return anySuccess;
    } catch (e) {
      Logger.log('FCM push hatası: ' + e.message);
      return false;
    }
  }
  ```

- [ ] **Step 2: handleSmsWebhook içine sendFcmPush çağrısı ekle**

  `handleSmsWebhook` fonksiyonunda `sonuclar.ntfy = sendNtfy(r.tesis, baslik, mesaj);` satırından **önce** ekle:

  ```js
  // FCM Web Push bildirimi
  sonuclar.fcm = sendFcmPush(r.tesis, baslik, mesaj);
  ```

  Ve `const basarili = ...` satırını şu şekilde güncelle:
  ```js
  const basarili = sonuclar.fcm || sonuclar.ntfy || sonuclar.telegram || sonuclar.whatsapp || sonuclar.sms;
  ```

- [ ] **Step 3: GAS'ı yeni deploy et**

  1. Apps Script editöründe: "Dağıt" → "Dağıtımları Yönet"
  2. Mevcut deploy'u seç → düzenle (kalem ikonu) → Sürüm: "Yeni sürüm"
  3. "Dağıt" → URL değişmez, bozulma yok ✅

---

## Task 3: APK — SmsReceiver.java'ya Firebase RTDB Yazma Ekle

**Files:**
- Modify: `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\java\com\izsu\simgiris\SmsReceiver.java`

**Amaç:** SMS alınca Firebase Realtime Database'e JSON log yazar. Mevcut `gasaGonder` bozulmadan korunur. RTDB URL ve secret APK'ya hardcode edilir (GAS_URL gibi).

- [ ] **Step 1: SmsReceiver.java'ya RTDB sabitlerini ekle**

  `GAS_URL` sabitinin hemen altına ekle:

  ```java
  // Firebase Realtime Database — SMS log
  // Firebase Console'dan aldığınız URL ve secret ile değiştirin
  private static final String RTDB_URL =
      "https://sim-giris-yarimada-default-rtdb.firebaseio.com/sms_log.json";
  private static final String RTDB_SECRET = "BURAYA_DATABASE_SECRET_YAPISTIR";
  ```

- [ ] **Step 2: onReceive içinde rtdbYaz çağrısı ekle**

  `onReceive` metodunun sonunda `gasaGonder(...)` çağrısından **sonra** ekle:

  ```java
  // Firebase RTDB'ye log yaz (mevcut GAS çağrısı bozulmaz)
  rtdbYaz(context, icerik, gonderen != null ? gonderen : "", aktivTesis);
  ```

  Ama `aktivTesis` burada tanımlı değil — `onReceive` içinde prefs'i okuyup geçirelim. `onReceive` metodunu şöyle güncelleyin:

  ```java
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
      String gonderenFinal = gonderen != null ? gonderen : "";
      Log.d(TAG, "SMS alındı | Gönderen: " + gonderenFinal + " | İçerik: " + icerik);

      SharedPreferences prefs = context.getSharedPreferences(AndroidBridge.PREFS, Context.MODE_PRIVATE);
      String aktivTesis = prefs.getString("activeTesis", "");

      // GAS'a ilet (mevcut, bozulmuyor)
      gasaGonder(context, icerik, gonderenFinal, aktivTesis);

      // Firebase RTDB'ye log yaz
      rtdbYaz(icerik, gonderenFinal, aktivTesis);
  }
  ```

  Not: `gasaGonder` metodunun imzasını da güncelleyeceğiz (Step 3).

- [ ] **Step 3: gasaGonder metodunu güncelle (aktivTesis parametresi ekle)**

  Mevcut `gasaGonder(Context context, String icerik, String gonderen)` imzasını şöyle değiştir:

  ```java
  private void gasaGonder(Context context, String icerik, String gonderen, String aktivTesis) {
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
  ```

  (Context parametresi artık metot içinde kullanılmıyor, çıkarılabilir ama uyumluluk için bırakıyoruz.)

- [ ] **Step 4: rtdbYaz metodunu ekle**

  `gasaGonder` metodundan sonra şu metodu ekle:

  ```java
  private void rtdbYaz(String icerik, String gonderen, String tesisId) {
      if (RTDB_SECRET.equals("BURAYA_DATABASE_SECRET_YAPISTIR")) return; // Henüz ayarlanmamış
      new Thread(() -> {
          try {
              JSONObject json = new JSONObject();
              json.put("tesis", tesisId);
              json.put("body", icerik);
              json.put("from", gonderen);
              json.put("timestamp", System.currentTimeMillis());

              // ?auth=secret parametresi ile kimlik doğrulaması
              URL url = new URL(RTDB_URL + "?auth=" + RTDB_SECRET);
              HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
              conn.disconnect();

          } catch (Exception e) {
              Log.e(TAG, "RTDB yazma hatası: " + e.getMessage());
          }
      }).start();
  }
  ```

- [ ] **Step 5: Aynı değişiklikleri SAİS APK'ya da uygula**

  SAİS APK projesindeki `SmsReceiver.java` aynı şekilde güncellenir:
  - Dosya: `C:\Users\HP\Documents\SIM-Giris-SAIS-APK\app\src\main\java\com\izsu\simgiris\sais\SmsReceiver.java`
  - Aynı `RTDB_URL`, `RTDB_SECRET` sabitleri
  - Aynı `onReceive`, `gasaGonder`, `rtdbYaz` metodları

- [ ] **Step 6: APK build et ve push et**

  GitHub Actions ile otomatik build tetiklenir. `C:\Users\HP\Documents\SIM-Giris-APK\` dizininde:
  ```bash
  git add app/src/main/java/com/izsu/simgiris/SmsReceiver.java
  git commit -m "feat: Firebase RTDB SMS log ve gasaGonder refactor"
  git push
  ```
  Aynısını SAİS APK için de:
  ```bash
  cd C:\Users\HP\Documents\SIM-Giris-SAIS-APK
  git add app/src/main/java/com/izsu/simgiris/sais/SmsReceiver.java
  git commit -m "feat: Firebase RTDB SMS log"
  git push
  ```

---

## Task 4: Web — Service Worker Oluştur (firebase-messaging-sw.js)

**Files:**
- Create: `C:\Users\HP\Documents\SIM-Giris-APK\docs\firebase-messaging-sw.js`

**Amaç:** Web Push notification arka planda (uygulama kapalıyken) göstermek için service worker gerekli. Firebase'in kuralı: bu dosya site kökünde `/firebase-messaging-sw.js` adıyla olmalı.

- [ ] **Step 1: firebase-messaging-sw.js dosyasını oluştur**

  `C:\Users\HP\Documents\SIM-Giris-APK\docs\firebase-messaging-sw.js` dosyasını oluştur:

  ```js
  // Firebase Messaging Service Worker
  // Bu dosya site kökünde (/firebase-messaging-sw.js) olmalı

  // Firebase SDK'yı service worker içinde import et
  importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
  importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

  // firebaseConfig'i buraya yapıştır (index.html'deki ile aynı)
  firebase.initializeApp({
    apiKey: "BURAYA_API_KEY",
    authDomain: "BURAYA_AUTH_DOMAIN",
    projectId: "BURAYA_PROJECT_ID",
    storageBucket: "BURAYA_STORAGE_BUCKET",
    messagingSenderId: "BURAYA_MESSAGING_SENDER_ID",
    appId: "BURAYA_APP_ID"
  });

  const messaging = firebase.messaging();

  // Arka planda bildirim gelince göster
  messaging.onBackgroundMessage(function(payload) {
    const title = payload.notification?.title || 'Yarımada SİM Giriş';
    const body  = payload.notification?.body  || 'Yeni mesaj';
    const icon  = payload.notification?.icon  || '/icons/icon-192.png';
    return self.registration.showNotification(title, {
      body:    body,
      icon:    icon,
      badge:   '/icons/icon-96.png',
      vibrate: [200, 100, 200],
      data:    payload.data || {}
    });
  });

  // Bildirime tıklanınca uygulamayı aç
  self.addEventListener('notificationclick', function(event) {
    event.notification.close();
    event.waitUntil(
      clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
        for (const client of clientList) {
          if ('focus' in client) return client.focus();
        }
        if (clients.openWindow) return clients.openWindow('/');
      })
    );
  });
  ```

  **Önemli:** `BURAYA_*` alanlarını Firebase Console'dan aldığın `firebaseConfig` değerleriyle doldur.

---

## Task 5: Web — index.html Firebase Push Entegrasyonu

**Files:**
- Modify: `C:\Users\HP\Documents\SIM-Giris-APK\docs\index.html`

**Amaç:** Web sitesi açılınca:
1. Service worker kaydedilir
2. Bildirim izni istenir (ilk açılışta bir kez, pes ederse 7 gün sonra tekrar)
3. FCM token alınır → GAS'a kaydedilir (aktif tesis ile birlikte)
4. iOS için "Ana Ekrana Ekle" yönlendirmesi gösterilir
5. Android zaten APK ile çalışır; web modunda push notification yedek olarak devreye girer

- [ ] **Step 1: index.html `<head>` bölümüne Firebase SDK ekle**

  `</head>` etiketinden **hemen önce** ekle:

  ```html
  <!-- Firebase SDK (Web Push için) -->
  <script src="https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js"></script>
  <script src="https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js"></script>
  ```

- [ ] **Step 2: index.html `</body>` etiketinden önce Firebase push script bloğunu ekle**

  Dosyanın en sonuna, `</body>` kapanış etiketinden **hemen önce** bu script bloğunu ekle:

  ```html
  <script>
  // ═══════════════════════════════════════════════════════
  //  Firebase Web Push Notification
  // ═══════════════════════════════════════════════════════
  (function() {
    // Bu değerleri Firebase Console'dan aldığınla değiştir
    const FIREBASE_CONFIG = {
      apiKey:            "BURAYA_API_KEY",
      authDomain:        "BURAYA_AUTH_DOMAIN",
      projectId:         "BURAYA_PROJECT_ID",
      storageBucket:     "BURAYA_STORAGE_BUCKET",
      messagingSenderId: "BURAYA_MESSAGING_SENDER_ID",
      appId:             "BURAYA_APP_ID"
    };
    const VAPID_KEY = "BURAYA_VAPID_KEY"; // Firebase Console → Cloud Messaging → Web Push certificates
    const GAS_URL   = "https://script.google.com/macros/s/AKfycbwixLKgFPdHTHqhA9ABe7ol2DO4e5yUb6fau0wQ0uet5fUat25lD3UDc7JsrT5A1mU6gA/exec";

    // APK modunda web push çalıştırma (APK zaten FCM'e sahip değil; web push APK'da gereksiz)
    if (window.AndroidNative) return;

    // Service Worker ve Notification API destekleniyor mu?
    if (!('serviceWorker' in navigator) || !('Notification' in window)) {
      console.log('[Push] Bu tarayıcı web push desteklemiyor.');
      return;
    }

    // iOS Safari: Web Push sadece "Ana Ekrana Ekle" sonrası çalışır (iOS 16.4+)
    const isIOS = /iPhone|iPad|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches
                      || window.navigator.standalone === true;

    if (isIOS && !isStandalone) {
      // Ana ekranda değil → "Ekle" banner göster, push kurma
      gosterIOSBanner();
      return;
    }

    // İzin durumu: daha önce reddedildiyse 7 gün bekle
    const reddTarih = localStorage.getItem('pushReddTarih');
    if (reddTarih && (Date.now() - parseInt(reddTarih)) < 7 * 24 * 3600 * 1000) return;

    // Firebase başlat (çoklu init önle)
    let app;
    try {
      app = firebase.app();
    } catch(e) {
      app = firebase.initializeApp(FIREBASE_CONFIG);
    }
    const messaging = firebase.messaging(app);

    // Service Worker kaydet
    navigator.serviceWorker.register('/firebase-messaging-sw.js').then(function(reg) {
      // İzin iste (daha önce verilmemişse)
      if (Notification.permission === 'default') {
        // Kullanıcı bir tesis seçtikten sonra sor (daha az rahatsız edici)
        document.addEventListener('tesisSecildi', function(e) {
          isteBildirimIzni(messaging, reg, e.detail.tesisId);
        }, { once: true });
        // Eğer tesis zaten seçiliyse hemen sor
        const mevcutTesis = localStorage.getItem('activeTesis');
        if (mevcutTesis) {
          isteBildirimIzni(messaging, reg, mevcutTesis);
        }
      } else if (Notification.permission === 'granted') {
        // İzin zaten verilmiş → token al
        const mevcutTesis = localStorage.getItem('activeTesis') || 'genel';
        tokenAlVeKaydet(messaging, mevcutTesis);
      }
    }).catch(function(err) {
      console.log('[Push] Service worker kaydı başarısız:', err);
    });

    function isteBildirimIzni(messaging, reg, tesisId) {
      Notification.requestPermission().then(function(permission) {
        if (permission === 'granted') {
          tokenAlVeKaydet(messaging, tesisId);
        } else {
          localStorage.setItem('pushReddTarih', Date.now().toString());
        }
      });
    }

    function tokenAlVeKaydet(messaging, tesisId) {
      messaging.getToken({ vapidKey: VAPID_KEY }).then(function(token) {
        if (!token) return;
        // GAS'a kaydet
        fetch(GAS_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'saveFcmToken', token: token, tesisId: tesisId })
        }).then(function(r) { return r.json(); })
          .then(function(d) { console.log('[Push] Token kaydedildi:', d); })
          .catch(function(err) { console.log('[Push] Token kayıt hatası:', err); });
        // Ön planda bildirim al
        messaging.onMessage(function(payload) {
          const title = payload.notification?.title || 'Yarımada SİM Giriş';
          const body  = payload.notification?.body  || '';
          if (Notification.permission === 'granted') {
            new Notification(title, {
              body:  body,
              icon:  '/icons/icon-192.png',
              badge: '/icons/icon-96.png'
            });
          }
        });
      }).catch(function(err) {
        console.log('[Push] Token alınamadı:', err);
      });
    }

    // Tesis seçilince token'ı güncelle
    // index.html'deki tesis seçim fonksiyonu "tesisSecildi" event'i dispatch etmeli (Task 6'da eklenir)
    document.addEventListener('tesisSecildi', function(e) {
      if (Notification.permission === 'granted') {
        tokenAlVeKaydet(messaging, e.detail.tesisId);
      }
    });
  })();

  // iOS "Ana Ekrana Ekle" Banner
  function gosterIOSBanner() {
    if (document.getElementById('ios-banner')) return; // Tekrar gösterme
    if (localStorage.getItem('iosBannerKapat')) return;
    const banner = document.createElement('div');
    banner.id = 'ios-banner';
    banner.innerHTML = `
      <div style="
        position:fixed;bottom:0;left:0;right:0;z-index:9999;
        background:linear-gradient(135deg,#041428,#062040);
        border-top:2px solid #0ea5e9;padding:16px 20px;
        display:flex;align-items:flex-start;gap:12px;
        font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
      ">
        <div style="font-size:28px;flex-shrink:0;">📲</div>
        <div style="flex:1;">
          <div style="font-weight:700;color:#fff;font-size:14px;margin-bottom:4px;">
            Bildirim almak için Ana Ekrana Ekle
          </div>
          <div style="color:#94a3b8;font-size:13px;line-height:1.5;">
            Ekranın altındaki 
            <span style="display:inline-block;background:#1e3a5f;border-radius:4px;padding:1px 6px;">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#0ea5e9" stroke-width="2" stroke-linecap="round" style="vertical-align:middle">
                <path d="M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8"/>
                <polyline points="16 6 12 2 8 6"/>
                <line x1="12" y1="2" x2="12" y2="15"/>
              </svg>
            </span>
            paylaş butonuna → <strong style="color:#fff;">"Ana Ekrana Ekle"</strong>
          </div>
        </div>
        <button onclick="localStorage.setItem('iosBannerKapat','1');document.getElementById('ios-banner').remove();"
          style="background:none;border:none;color:#64748b;font-size:22px;cursor:pointer;padding:0;line-height:1;">✕</button>
      </div>
    `;
    document.body.appendChild(banner);
  }
  </script>
  ```

  **Önemli:** `BURAYA_*` alanlarını Firebase Console değerleriyle doldur.

- [ ] **Step 3: index.html — tesis seçim fonksiyonuna event dispatch ekle**

  `index.html` içindeki tesis seçme fonksiyonunu bul (muhtemelen `tesisAc(id)` veya benzeri bir fonksiyon). Tesis seçimi yapıldığında `tesisSecildi` custom event dispatch etmesi gerekiyor.

  Fonksiyonu bul ve tesis id kaydedilen satırdan **hemen sonra** ekle:

  ```js
  // Push notification token'ını güncelle
  document.dispatchEvent(new CustomEvent('tesisSecildi', { detail: { tesisId: id } }));
  ```

  Hangi fonksiyona ekleneceğini görmek için index.html'de `activeTesis` veya `localStorage.setItem` araması yap.

- [ ] **Step 4: docs/index.html'i docs/ klasöründe güncelle, assets'i de güncelle**

  Bu dosya iki yerde var:
  - `docs/index.html` ← şu an değiştirdiğimiz yer
  - `app/src/main/assets/sim-giris.html` ← APK için kopya

  APK modunda (`window.AndroidNative` varsa) push notification script'i zaten çalışmaz (erken return). Bu nedenle `app/src/main/assets/sim-giris.html`'e Firebase SDK script'lerini eklemek **gerekmez** — bant genişliği israfı olur. İki dosyayı farklı tutabiliriz: docs/ web push'lu, assets/ push'suz.

  Eğer her iki dosyayı senkron tutmak istiyorsak, APK'daki kopyaya da script bloğunu ekle; `if (window.AndroidNative) return;` satırı APK'da devre dışı bırakır.

---

## Task 6: Web — icons/ Klasörü (PWA için)

**Files:**
- Create: `C:\Users\HP\Documents\SIM-Giris-APK\docs\icons\icon-192.png`
- Create: `C:\Users\HP\Documents\SIM-Giris-APK\docs\icons\icon-96.png`
- Create (optional): `C:\Users\HP\Documents\SIM-Giris-APK\docs\manifest.json`

**Amaç:** Push notification için icon/badge dosyaları gerekli. Yoksa tarayıcı varsayılan icon gösterir.

- [ ] **Step 1: İcon dosyaları oluştur veya kopyala**

  Seçenekler:
  - **A)** Mevcut APK mipmap ikonunu kullan: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` → docs/icons/ altına kopyala
  - **B)** Basit bir PNG kullan (192x192 ve 96x96 px, şeffaf arka plan, İZSU veya kilit ikonu)

  Eğer ikon yoksa push bildirim yine gönderilir, sadece ikon gösterilmez. Bu adım opsiyoneldir.

- [ ] **Step 2: manifest.json oluştur (iOS "Ana Ekrana Ekle" için)**

  `C:\Users\HP\Documents\SIM-Giris-APK\docs\manifest.json`:

  ```json
  {
    "name": "Yarımada SİM Giriş Sistemi",
    "short_name": "SİM Giriş",
    "description": "İZSU Yarımada Tesisler SİM Giriş Sistemi",
    "start_url": "/",
    "display": "standalone",
    "background_color": "#020c18",
    "theme_color": "#0ea5e9",
    "orientation": "portrait",
    "icons": [
      { "src": "/icons/icon-96.png",  "sizes": "96x96",   "type": "image/png" },
      { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
      { "src": "/icons/icon-192.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable" }
    ]
  }
  ```

- [ ] **Step 3: index.html `<head>` bölümüne manifest link ekle**

  `</head>` etiketinden önce ekle:

  ```html
  <link rel="manifest" href="/manifest.json">
  <link rel="apple-touch-icon" href="/icons/icon-192.png">
  ```

---

## Task 7: Commit ve Deploy

- [ ] **Step 1: docs/ klasörünü commit et**

  ```bash
  cd "C:/Users/HP/Documents/SIM-Giris-APK"
  git add docs/firebase-messaging-sw.js docs/manifest.json docs/icons/
  git add docs/index.html
  git commit -m "feat: Firebase Web Push notification + iOS Ana Ekrana Ekle + manifest"
  git push
  ```

- [ ] **Step 2: GitHub Pages deploy sonucunu doğrula**

  https://github.com/chemist45/SIM-Giris-APK/actions adresinden son workflow run'ı kontrol et.
  Başarılıysa: https://chemist45.github.io/SIM-Giris-APK/ adresini aç.
  
  Tarayıcı Developer Tools → Console → `[Push]` loglarını kontrol et.

- [ ] **Step 3: Uçtan uca test**

  1. https://chemist45.github.io/SIM-Giris-APK/ aç
  2. Bir tesis seç (örn: Çeşme)
  3. Bildirim izni popup'ı görünmeli → "İzin Ver"
  4. Console'da `[Push] Token kaydedildi: {ok: true}` görünmeli
  5. GAS Spreadsheet'te "FcmTokens" sayfasında token satırı oluşmalı
  6. Test push: GAS editöründe şu fonksiyonu çalıştır:
     ```js
     function testPush() {
       sendFcmPush('cesme', 'Test Bildirimi', 'Bu bir test mesajıdır.');
     }
     ```
  7. Tarayıcıda veya telefonda bildirim görünmeli ✅

---

## Özet: Değiştirilen / Oluşturulan Dosyalar

| Dosya | İşlem | Not |
|-------|-------|-----|
| `gas-kod.js` | Modify | saveFcmToken action + sendFcmPush + smsWebhook güncelleme |
| `SIM-Giris-APK/.../SmsReceiver.java` | Modify | RTDB yazma + gasaGonder refactor |
| `SIM-Giris-SAIS-APK/.../SmsReceiver.java` | Modify | Aynı değişiklikler |
| `docs/firebase-messaging-sw.js` | Create | FCM service worker |
| `docs/index.html` | Modify | Firebase SDK + push script + manifest link |
| `docs/manifest.json` | Create | PWA manifest |
| `docs/icons/icon-192.png` | Create | PWA/push ikon |
| `docs/icons/icon-96.png` | Create | Badge ikon |

## Dikkat: BURAYA_* Alanları

Aşağıdaki dosyalarda `BURAYA_*` ile işaretlenmiş alanlar Firebase Console'dan alınan gerçek değerlerle doldurulmalıdır:
- `firebase-messaging-sw.js` → firebaseConfig
- `docs/index.html` → FIREBASE_CONFIG + VAPID_KEY
- `SmsReceiver.java` → RTDB_URL + RTDB_SECRET
- GAS Script Properties → FCM_SERVER_KEY + RTDB_URL + RTDB_SECRET

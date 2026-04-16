// Firebase Messaging Service Worker
// Bu dosya site kökünde (/firebase-messaging-sw.js) olmalı

importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey:            "AIzaSyDcjPa8ipd_WYg0GtAXA_HXl24ExJSQzu8",
  authDomain:        "sim-giris-yarimada.firebaseapp.com",
  projectId:         "sim-giris-yarimada",
  storageBucket:     "sim-giris-yarimada.firebasestorage.app",
  messagingSenderId: "573899770471",
  appId:             "1:573899770471:web:f0eef370fe6513c86c22c2"
});

const messaging = firebase.messaging();

// Arka planda (uygulama kapalıyken) bildirim gelince göster
messaging.onBackgroundMessage(function(payload) {
  const title = (payload.notification && payload.notification.title) || 'Yarımada SİM Giriş';
  const body  = (payload.notification && payload.notification.body)  || 'Yeni mesaj';
  const icon  = (payload.notification && payload.notification.icon)  || '/icons/icon-192.png';
  return self.registration.showNotification(title, {
    body:    body,
    icon:    icon,
    badge:   '/icons/icon-96.png',
    vibrate: [200, 100, 200],
    data:    payload.data || {}
  });
});

// Bildirime tıklanınca uygulamayı aç / öne getir
self.addEventListener('notificationclick', function(event) {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
      for (var i = 0; i < clientList.length; i++) {
        if ('focus' in clientList[i]) return clientList[i].focus();
      }
      if (clients.openWindow) return clients.openWindow('/');
    })
  );
});

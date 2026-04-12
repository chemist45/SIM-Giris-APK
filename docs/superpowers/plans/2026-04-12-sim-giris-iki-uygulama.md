# SİM Giriş — İki Ayrı Uygulama Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SAİS Sorumlusu ve Alıcılar için iki ayrı sadeleştirilmiş HTML uygulaması oluşturmak; GAS'a `getCode` aksiyonu eklemek.

**Architecture:** `alici-app.html` tesis seçimi + SİM giriş + GAS'tan kod çekme sağlar. `sais-app.html` SMS aktif durumu + iOS kurulum rehberi gösterir. GAS `handleSmsWebhook`'a kod saklama, yeni `getCode` aksiyonu eklenir.

**Tech Stack:** Vanilla HTML/CSS/JS, Google Apps Script, Android WebView (APK assets)

---

## Dosya Haritası

| İşlem | Dosya |
|-------|-------|
| Modify | `C:\Users\HP\Documents\SİM Giriş (openai-codex)\gas-kod.js` |
| Create | `C:\Users\HP\Documents\SİM Giriş (openai-codex)\alici-app.html` |
| Create | `C:\Users\HP\Documents\SİM Giriş (openai-codex)\sais-app.html` |
| Copy → Modify | `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\assets\alici-app.html` |
| Copy → Modify | `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\assets\sais-app.html` |
| Modify | `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\assets\index.html` |

---

## Task 1: GAS — getCode aksiyonu ve kod saklama

**Files:**
- Modify: `C:\Users\HP\Documents\SİM Giriş (openai-codex)\gas-kod.js`

- [ ] **Step 1: `handleSmsWebhook` fonksiyonuna kod saklama satırı ekle**

`gas-kod.js` dosyasında `handleSmsWebhook` fonksiyonunun içinde, `const mesaj = ...` satırının hemen altına ekle:

```javascript
  // Kodu in-app getCode için sakla
  PropertiesService.getScriptProperties().setProperty(
    'lastCode_' + r.tesis,
    JSON.stringify({ body: data.body, timestamp: new Date().toISOString() })
  );
```

- [ ] **Step 2: `getLatestCode` fonksiyonunu ekle**

`gas-kod.js` dosyasında `handleAdminLogin` fonksiyonunun hemen üstüne ekle:

```javascript
// ── Kod Getir (alıcı uygulaması) ─────────────────────────────
function getLatestCode(data) {
  const s = PropertiesService.getScriptProperties().getProperty('lastCode_' + data.tesis);
  if (!s) return { ok: false, message: 'Henüz kod yok' };
  const c = JSON.parse(s);
  const ageSec = Math.round((Date.now() - new Date(c.timestamp).getTime()) / 1000);
  if (ageSec > 300) return { ok: false, expired: true, ageSec };
  return { ok: true, body: c.body, ageSec };
}
```

- [ ] **Step 3: `doPost` içine `getCode` dalı ekle**

`doPost` fonksiyonunda `else if (action === 'adminLogin')` satırının hemen altına ekle:

```javascript
    else if (action === 'getCode')         result = getLatestCode(data);
```

- [ ] **Step 4: GAS editöründe deploy et**

1. script.google.com → projeyi aç
2. Mevcut kodu tamamen sil → güncel `gas-kod.js` içeriğini yapıştır
3. **Dağıt → Dağıtımları Yönet → ✏️ Düzenle → Yeni Sürüm → Dağıt**
4. URL değişmez — aynı URL geçerli

- [ ] **Step 5: Bağlantı testini doğrula**

Tarayıcıda şu URL'yi aç (GET ping testi):
```
https://script.google.com/macros/s/AKfycbwixLKgFPdHTHqhA9ABe7ol2DO4e5yUb6fau0wQ0uet5fUat25lD3UDc7JsrT5A1mU6gA/exec
```
Beklenen: `SIM Giris API v2` metni görünür

---

## Task 2: `alici-app.html` oluştur

**Files:**
- Create: `C:\Users\HP\Documents\SİM Giriş (openai-codex)\alici-app.html`

- [ ] **Step 1: Dosyayı oluştur**

Aşağıdaki tam içeriği `alici-app.html` olarak kaydet:

```html
<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>SİM Giriş — Alıcı Paneli</title>
<style>
:root{--bg:#020c18;--surf:#041428;--brd:#1e293b;--pri:#0ea5e9;--ok:#22c55e;--warn:#f59e0b;--err:#ef4444;--txt:#e2e8f0;--mut:#94a3b8;}
*{box-sizing:border-box;margin:0;padding:0;}
body{background:var(--bg);color:var(--txt);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;min-height:100vh;}
.hdr{background:linear-gradient(135deg,#041428,#020c18);border-bottom:1px solid var(--brd);padding:16px;text-align:center;}
.hdr-brand{font-size:11px;font-weight:800;color:var(--pri);letter-spacing:3px;margin-bottom:2px;}
.hdr-title{font-size:15px;font-weight:700;}
.hdr-sub{font-size:11px;color:var(--mut);margin-top:2px;}
.scr{display:none;padding:16px;max-width:480px;margin:0 auto;}
.scr.on{display:block;}
.scr-ttl{font-size:12px;font-weight:700;color:var(--mut);text-transform:uppercase;letter-spacing:1px;margin-bottom:14px;}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;}
.kart{background:var(--surf);border:1px solid var(--brd);border-radius:12px;padding:18px 12px;cursor:pointer;text-align:center;transition:transform .12s;}
.kart:active{transform:scale(.96);}
.kart .ikon{font-size:26px;margin-bottom:8px;}
.kart .ad{font-size:13px;font-weight:700;}
.back{background:none;border:none;color:var(--mut);font-size:13px;cursor:pointer;display:flex;align-items:center;gap:6px;margin-bottom:14px;padding:4px 0;}
.badge{display:inline-flex;align-items:center;gap:8px;background:var(--surf);border:1px solid var(--brd);border-radius:20px;padding:7px 16px;margin-bottom:16px;font-size:13px;font-weight:700;}
.card{background:var(--surf);border:1px solid var(--brd);border-radius:12px;padding:14px;margin-bottom:12px;}
.card-ttl{font-size:11px;font-weight:700;color:var(--mut);text-transform:uppercase;letter-spacing:1px;margin-bottom:10px;}
.crow{display:flex;justify-content:space-between;align-items:center;padding:9px 0;border-bottom:1px solid var(--brd);cursor:pointer;}
.crow:last-child{border-bottom:none;}
.clbl{font-size:10px;color:var(--mut);text-transform:uppercase;letter-spacing:.5px;}
.cval{font-size:14px;font-weight:700;font-family:monospace;}
.btn{width:100%;padding:14px;border-radius:10px;border:none;font-size:14px;font-weight:700;cursor:pointer;margin-bottom:8px;transition:opacity .15s;}
.btn:active{opacity:.85;}
.btn-pri{background:linear-gradient(135deg,#0ea5e9,#0284c7);color:#fff;}
.btn-sec{background:var(--surf);border:1px solid var(--brd);color:var(--txt);}
.kod-panel{background:var(--surf);border:1px solid var(--brd);border-radius:12px;padding:16px;margin-bottom:12px;}
.kod-hdr{font-size:13px;font-weight:700;color:var(--mut);text-align:center;margin-bottom:12px;}
.kod-box{background:#020c18;border:2px solid var(--brd);border-radius:10px;padding:22px;text-align:center;margin-bottom:10px;transition:border-color .3s;}
.kod-num{font-size:34px;font-weight:800;letter-spacing:8px;font-family:monospace;transition:color .3s;}
.kod-yas{font-size:11px;color:var(--mut);margin-top:8px;}
.yesil .kod-box{border-color:#22c55e;} .yesil .kod-num{color:#22c55e;}
.sari  .kod-box{border-color:#f59e0b;} .sari  .kod-num{color:#f59e0b;}
.krmz  .kod-box{border-color:#ef4444;} .krmz  .kod-num{color:#ef4444;}
.bos{color:var(--mut);text-align:center;padding:22px;font-size:13px;line-height:1.7;}
.toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:#1e293b;color:#e2e8f0;padding:10px 22px;border-radius:8px;font-size:13px;z-index:9999;opacity:0;transition:opacity .2s;pointer-events:none;white-space:nowrap;}
.toast.on{opacity:1;}
</style>
</head>
<body>

<div class="hdr">
  <div class="hdr-brand">YARIMADA SİM</div>
  <div class="hdr-title">Alıcı Girişi</div>
  <div class="hdr-sub">2FA Kod Yönetimi</div>
</div>

<div class="scr on" id="s1">
  <div class="scr-ttl">Tesisini Seç</div>
  <div class="grid" id="tesisGrid"></div>
</div>

<div class="scr" id="s2">
  <button class="back" onclick="geri()">← Geri</button>
  <div class="badge" id="badge"></div>

  <div class="card">
    <div class="card-ttl">Giriş Bilgileri</div>
    <div class="crow" onclick="kopyala('user')">
      <div><div class="clbl">Kullanıcı Adı</div><div class="cval" id="cUser">—</div></div>
      <span>📋</span>
    </div>
    <div class="crow" onclick="kopyala('sifre')">
      <div><div class="clbl">Şifre</div><div class="cval" id="cSifre">—</div></div>
      <span>📋</span>
    </div>
  </div>

  <button class="btn btn-pri" onclick="simGiris()">🔐 SİM GİRİŞ YAP</button>

  <div class="kod-panel" id="kodPanel" style="display:none">
    <div class="kod-hdr">📬 2FA Kodu</div>
    <div id="kodIcerik" class="bos">⏳ Kod bekleniyor...</div>
    <button class="btn btn-sec" onclick="kodGetir()" style="margin-top:4px">🔄 Kodu Yenile</button>
    <div id="sonGun" style="font-size:11px;color:var(--mut);text-align:center;margin-top:6px"></div>
  </div>
</div>

<div class="toast" id="toast"></div>

<script>
const GAS_URL='https://script.google.com/macros/s/AKfycbwixLKgFPdHTHqhA9ABe7ol2DO4e5yUb6fau0wQ0uet5fUat25lD3UDc7JsrT5A1mU6gA/exec';
const T={
  cesme:      {ad:'Çeşme AAT',      k:'izmir_izsu_cesme', s:'izsu_1135',        r:'#0ea5e9',i:'💧'},
  doganbey:   {ad:'Doğanbey AAT',   k:'t.dululoglu',      s:'urla_izsu',        r:'#8b5cf6',i:'🌊'},
  urla:       {ad:'Urla AAT',       k:'k.dinler',         s:'urla_izsu',        r:'#22c55e',i:'🌿'},
  ozdere:     {ad:'Özdere AAT',     k:'merve.barin',      s:'izsu_ozdere',      r:'#f59e0b',i:'🏖️'},
  seferihisar:{ad:'Seferihisar AAT',k:'gamze.tokdemir',   s:'izsu_seferihisar', r:'#ef4444',i:'🏛️'},
  mordogan:   {ad:'Mordoğan AAT',   k:'efurkan.100',      s:'5414211516',       r:'#ec4899',i:'⚓'}
};
let secili=null,poll=null;

(function(){
  const g=document.getElementById('tesisGrid');
  Object.entries(T).forEach(([id,t])=>{
    const d=document.createElement('div');
    d.className='kart';
    d.style.borderColor=t.r+'55';
    d.innerHTML=`<div class="ikon">${t.i}</div><div class="ad" style="color:${t.r}">${t.ad}</div>`;
    d.onclick=()=>tesisSeç(id);
    g.appendChild(d);
  });
})();

function tesisSeç(id){
  secili=id;
  const t=T[id];
  document.getElementById('badge').innerHTML=`<span>${t.i}</span><span style="color:${t.r}">${t.ad}</span>`;
  document.getElementById('cUser').textContent=t.k;
  document.getElementById('cSifre').textContent=t.s;
  document.getElementById('kodPanel').style.display='none';
  document.getElementById('kodIcerik').className='bos';
  document.getElementById('kodIcerik').innerHTML='⏳ Kod bekleniyor...';
  stopPoll();
  document.getElementById('s1').classList.remove('on');
  document.getElementById('s2').classList.add('on');
}

function geri(){
  stopPoll();secili=null;
  document.getElementById('s2').classList.remove('on');
  document.getElementById('s1').classList.add('on');
}

async function simGiris(){
  if(!secili)return;
  try{await navigator.clipboard.writeText(T[secili].s);}catch(e){}
  window.open('https://sim.csb.gov.tr','_blank');
  document.getElementById('kodPanel').style.display='block';
  toast('Şifre kopyalandı — SİM açılıyor');
  await kodGetir();
  poll=setInterval(kodGetir,15000);
}

async function kodGetir(){
  if(!secili)return;
  const el=document.getElementById('kodIcerik');
  try{
    const res=await fetch(GAS_URL,{method:'POST',headers:{'Content-Type':'text/plain'},body:JSON.stringify({action:'getCode',tesis:secili})});
    const d=await res.json();
    document.getElementById('sonGun').textContent='Son güncelleme: '+new Date().toLocaleTimeString('tr-TR');
    if(d.ok){
      const dk=Math.floor(d.ageSec/60),sn=d.ageSec%60;
      const cls=d.ageSec<120?'yesil':d.ageSec<240?'sari':'krmz';
      const m=d.body.match(/\d{4,8}/);
      const kod=m?m[0]:d.body;
      el.className=cls;
      el.innerHTML=`<div class="kod-box"><div class="kod-num">${kod}</div><div class="kod-yas">${dk>0?dk+' dk ':' '}${sn} sn önce geldi</div></div>`;
    }else if(d.expired){
      el.className='';el.innerHTML='<div class="bos">🔴 Kodun süresi doldu — yeniden giriş yapın</div>';
    }else{
      el.className='';el.innerHTML='<div class="bos">⏳ Henüz kod gelmedi<br><small>SAİS sorumlusunun telefonu aktif mi?</small></div>';
    }
  }catch(e){el.className='';el.innerHTML='<div class="bos">❌ GAS bağlantı hatası</div>';}
}

function stopPoll(){if(poll){clearInterval(poll);poll=null;}}

function kopyala(tip){
  if(!secili)return;
  const v=tip==='user'?T[secili].k:T[secili].s;
  const lbl=tip==='user'?'Kullanıcı adı':'Şifre';
  try{navigator.clipboard.writeText(v).then(()=>toast(lbl+' kopyalandı'));}
  catch(e){toast('Kopyalanamadı');}
}

let tt;
function toast(msg){
  const el=document.getElementById('toast');
  el.textContent=msg;el.className='toast on';
  if(tt)clearTimeout(tt);
  tt=setTimeout(()=>el.className='toast',2500);
}
</script>
</body>
</html>
```

- [ ] **Step 2: Tarayıcıda test et**

`alici-app.html` dosyasını Chrome'da aç:
- Tesis kartları görünüyor mu?
- Tesis seçince kullanıcı adı/şifre görünüyor mu?
- "SİM GİRİŞ YAP" butonuna basınca sim.csb.gov.tr açılıyor mu?
- "Kodu Yenile" butonuna basınca GAS'a istek gidiyor mu (F12 → Network)?

---

## Task 3: `sais-app.html` oluştur

**Files:**
- Create: `C:\Users\HP\Documents\SİM Giriş (openai-codex)\sais-app.html`

- [ ] **Step 1: Dosyayı oluştur**

```html
<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>SİM Giriş — SAİS Sorumlusu</title>
<style>
:root{--bg:#020c18;--surf:#041428;--brd:#1e293b;--pri:#0ea5e9;--ok:#22c55e;--err:#ef4444;--txt:#e2e8f0;--mut:#94a3b8;}
*{box-sizing:border-box;margin:0;padding:0;}
body{background:var(--bg);color:var(--txt);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;min-height:100vh;}
.hdr{background:linear-gradient(135deg,#041428,#020c18);border-bottom:1px solid var(--brd);padding:16px;text-align:center;}
.hdr-brand{font-size:11px;font-weight:800;color:var(--pri);letter-spacing:3px;margin-bottom:2px;}
.hdr-title{font-size:15px;font-weight:700;}
.hdr-sub{font-size:11px;color:var(--mut);margin-top:2px;}
.scr{display:none;padding:16px;max-width:480px;margin:0 auto;}
.scr.on{display:block;}
.scr-ttl{font-size:12px;font-weight:700;color:var(--mut);text-transform:uppercase;letter-spacing:1px;margin-bottom:14px;}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;}
.kart{background:var(--surf);border:1px solid var(--brd);border-radius:12px;padding:18px 12px;cursor:pointer;text-align:center;transition:transform .12s;}
.kart:active{transform:scale(.96);}
.kart .ikon{font-size:26px;margin-bottom:8px;}
.kart .ad{font-size:13px;font-weight:700;}
.back{background:none;border:none;color:var(--mut);font-size:13px;cursor:pointer;display:flex;align-items:center;gap:6px;margin-bottom:14px;padding:4px 0;}
.badge{display:inline-flex;align-items:center;gap:8px;background:var(--surf);border:1px solid var(--brd);border-radius:20px;padding:7px 16px;margin-bottom:16px;font-size:13px;font-weight:700;}
.card{background:var(--surf);border:1px solid var(--brd);border-radius:12px;padding:16px;margin-bottom:12px;}
.aktif-banner{background:#0f2818;border:1px solid #22c55e;border-radius:12px;padding:20px;margin-bottom:12px;text-align:center;}
.btn{width:100%;padding:13px;border-radius:10px;border:none;font-size:14px;font-weight:700;cursor:pointer;margin-bottom:8px;transition:opacity .15s;}
.btn:active{opacity:.85;}
.btn-test{background:var(--surf);border:1px solid var(--brd);color:var(--txt);}
.btn-ios{background:linear-gradient(135deg,#22c55e,#16a34a);color:#fff;}
.test-sonuc{font-size:13px;padding:10px;border-radius:8px;margin-top:8px;display:none;}
.ios-rehber{background:#020c18;border-radius:8px;padding:12px;font-size:12px;color:var(--mut);line-height:2.2;margin-top:10px;}
.toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:#1e293b;color:#e2e8f0;padding:10px 22px;border-radius:8px;font-size:13px;z-index:9999;opacity:0;transition:opacity .2s;pointer-events:none;white-space:nowrap;}
.toast.on{opacity:1;}
</style>
</head>
<body>

<div class="hdr">
  <div class="hdr-brand">YARIMADA SİM</div>
  <div class="hdr-title">SAİS Sorumlusu Paneli</div>
  <div class="hdr-sub">SMS İletim Yönetimi</div>
</div>

<div class="scr on" id="s1">
  <div class="scr-ttl">Tesisini Seç</div>
  <div class="grid" id="tesisGrid"></div>
</div>

<div class="scr" id="s2">
  <button class="back" onclick="geri()">← Geri</button>
  <div class="badge" id="badge"></div>

  <!-- SMS Aktif Banner -->
  <div class="aktif-banner">
    <div style="font-size:36px;margin-bottom:10px">✅</div>
    <div style="font-size:15px;font-weight:700;color:#22c55e;margin-bottom:6px">SMS İletimi Aktif</div>
    <div style="font-size:12px;color:#86efac;line-height:1.7" id="aktifAciklama">
      Uygulama SMS'leri otomatik olarak GAS'a iletiyor.
    </div>
  </div>

  <!-- Bağlantı Testi -->
  <div class="card">
    <div style="font-size:12px;font-weight:700;color:var(--mut);text-transform:uppercase;letter-spacing:1px;margin-bottom:10px">Backend Bağlantısı</div>
    <button class="btn btn-test" onclick="baglantiTest()">🔗 Bağlantı Testi</button>
    <div id="testSonuc" class="test-sonuc"></div>
  </div>

  <!-- iOS Kurulum (sadece iPhone'da gösterilir) -->
  <div class="card" id="iosKart" style="display:none">
    <div style="font-size:12px;font-weight:700;color:#22c55e;text-transform:uppercase;letter-spacing:1px;margin-bottom:10px">📱 iPhone Kurulumu</div>
    <p style="font-size:12px;color:var(--mut);margin-bottom:12px;line-height:1.6">
      iPhone kullanıyorsanız SMS'leri GAS'a iletmek için iOS Kısayollar kurulumu gereklidir. <strong style="color:var(--txt)">Tek seferlik.</strong>
    </p>
    <button class="btn btn-ios" id="iosIndirBtn" onclick="iosIndir()">📥 iOS Kısayolunu İndir</button>
    <div class="ios-rehber">
      <div>1️⃣ Yukarıdaki butona bas → dosyayı aç → <strong style="color:var(--txt)">Kısayollar'a Ekle</strong></div>
      <div>2️⃣ Kısayollar → <strong style="color:var(--txt)">Otomasyon → + → Kişisel Otomasyon → Mesaj</strong></div>
      <div style="padding-left:16px;font-size:11px">İçerik içerir: <code style="color:var(--pri);background:#041428;padding:1px 6px;border-radius:4px">sim.csb.gov.tr</code> → <strong style="color:var(--txt)">Kısayolu Çalıştır</strong> → indirilen kısayolu seç</div>
      <div>3️⃣ <strong style="color:#f59e0b)">Çalıştırmadan Önce Sor</strong> → <strong style="color:#ef4444">KAPAT</strong> ✅</div>
    </div>
  </div>
</div>

<div class="toast" id="toast"></div>

<script>
const GAS_URL='https://script.google.com/macros/s/AKfycbwixLKgFPdHTHqhA9ABe7ol2DO4e5yUb6fau0wQ0uet5fUat25lD3UDc7JsrT5A1mU6gA/exec';
const T={
  cesme:      {ad:'Çeşme AAT',      r:'#0ea5e9',i:'💧'},
  doganbey:   {ad:'Doğanbey AAT',   r:'#8b5cf6',i:'🌊'},
  urla:       {ad:'Urla AAT',       r:'#22c55e',i:'🌿'},
  ozdere:     {ad:'Özdere AAT',     r:'#f59e0b',i:'🏖️'},
  seferihisar:{ad:'Seferihisar AAT',r:'#ef4444',i:'🏛️'},
  mordogan:   {ad:'Mordoğan AAT',   r:'#ec4899',i:'⚓'}
};
let secili=null;
const isIos=/iP(hone|ad|od)/.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
const isApk=!!window.AndroidNative;

(function(){
  const g=document.getElementById('tesisGrid');
  Object.entries(T).forEach(([id,t])=>{
    const d=document.createElement('div');
    d.className='kart';
    d.style.borderColor=t.r+'55';
    d.innerHTML=`<div class="ikon">${t.i}</div><div class="ad" style="color:${t.r}">${t.ad}</div>`;
    d.onclick=()=>tesisSeç(id);
    g.appendChild(d);
  });
})();

function tesisSeç(id){
  secili=id;
  const t=T[id];
  document.getElementById('badge').innerHTML=`<span>${t.i}</span><span style="color:${t.r}">${t.ad}</span>`;
  // Açıklama güncelle
  if(isApk){
    document.getElementById('aktifAciklama').textContent='Uygulama SMS\'leri otomatik olarak GAS\'a iletiyor. Başka uygulama gerekmez.';
  } else if(isIos){
    document.getElementById('aktifAciklama').textContent='iOS Kısayollar kurulumu tamamlandıktan sonra SMS\'ler otomatik iletilir.';
    document.getElementById('iosKart').style.display='block';
    document.getElementById('iosIndirBtn').textContent='📥 iOS Kısayolunu İndir — '+t.ad;
  } else {
    document.getElementById('aktifAciklama').textContent='Bu paneli SAİS sorumlusunun telefonunda açın.';
  }
  document.getElementById('s1').classList.remove('on');
  document.getElementById('s2').classList.add('on');
}

function geri(){
  secili=null;
  document.getElementById('s2').classList.remove('on');
  document.getElementById('s1').classList.add('on');
  document.getElementById('testSonuc').style.display='none';
  document.getElementById('iosKart').style.display='none';
}

async function baglantiTest(){
  const el=document.getElementById('testSonuc');
  el.style.display='block';
  el.style.background='rgba(251,191,36,.12)';
  el.style.color='#fbbf24';
  el.textContent='⏳ Test ediliyor...';
  try{
    const res=await fetch(GAS_URL,{method:'POST',headers:{'Content-Type':'text/plain'},body:JSON.stringify({action:'ping'})});
    const d=await res.json();
    if(d.ok){el.style.background='rgba(34,197,94,.12)';el.style.color='#22c55e';el.textContent='✅ '+d.message;}
    else{el.style.background='rgba(239,68,68,.12)';el.style.color='#ef4444';el.textContent='❌ GAS hatası';}
  }catch(e){el.style.background='rgba(239,68,68,.12)';el.style.color='#ef4444';el.textContent='❌ Bağlantı hatası: '+e.message;}
}

function iosIndir(){
  if(!secili)return;
  const plist=`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>WFWorkflowClientVersion</key><string>1300.0.1</string>
  <key>WFWorkflowHasShortcutInputVariables</key><true/>
  <key>WFWorkflowIcon</key>
  <dict>
    <key>WFWorkflowIconStartColor</key><integer>-2071028991</integer>
    <key>WFWorkflowIconGlyphNumber</key><integer>59783</integer>
  </dict>
  <key>WFWorkflowInputContentItemClasses</key>
  <array><string>WFStringContentItem</string></array>
  <key>WFWorkflowActions</key>
  <array>
    <dict>
      <key>WFWorkflowActionIdentifier</key>
      <string>is.workflow.actions.downloadurl</string>
      <key>WFWorkflowActionParameters</key>
      <dict>
        <key>WFHTTPMethod</key><string>POST</string>
        <key>WFURL</key><string>${GAS_URL}</string>
        <key>WFHTTPBodyType</key><string>JSON</string>
        <key>WFJSONValues</key>
        <dict>
          <key>Value</key>
          <dict>
            <key>WFDictionaryFieldValueItems</key>
            <array>
              <dict>
                <key>WFItemType</key><integer>0</integer>
                <key>WFKey</key><dict><key>Value</key><dict><key>string</key><string>action</string></dict><key>WFSerializationType</key><string>WFTextTokenString</string></dict>
                <key>WFValue</key><dict><key>Value</key><dict><key>string</key><string>smsWebhook</string></dict><key>WFSerializationType</key><string>WFTextTokenString</string></dict>
              </dict>
              <dict>
                <key>WFItemType</key><integer>0</integer>
                <key>WFKey</key><dict><key>Value</key><dict><key>string</key><string>body</string></dict><key>WFSerializationType</key><string>WFTextTokenString</string></dict>
                <key>WFValue</key>
                <dict>
                  <key>Value</key>
                  <dict>
                    <key>attachmentsByRange</key>
                    <dict><key>{0, 1}</key><dict><key>Type</key><string>ExtensionInput</string></dict></dict>
                    <key>string</key><string>&#xFFFC;</string>
                  </dict>
                  <key>WFSerializationType</key><string>WFTextTokenString</string>
                </dict>
              </dict>
              <dict>
                <key>WFItemType</key><integer>0</integer>
                <key>WFKey</key><dict><key>Value</key><dict><key>string</key><string>tesis</string></dict><key>WFSerializationType</key><string>WFTextTokenString</string></dict>
                <key>WFValue</key><dict><key>Value</key><dict><key>string</key><string>${secili}</string></dict><key>WFSerializationType</key><string>WFTextTokenString</string></dict>
              </dict>
            </array>
          </dict>
          <key>WFSerializationType</key><string>WFDictionaryFieldValue</string>
        </dict>
      </dict>
    </dict>
  </array>
  <key>WFWorkflowMinimumClientVersion</key><integer>900</integer>
  <key>WFWorkflowMinimumClientVersionString</key><string>900</string>
  <key>WFWorkflowOutputContentItemClasses</key><array/>
  <key>WFWorkflowImportQuestions</key><array/>
  <key>WFWorkflowName</key><string>SİM SMS İlet - ${T[secili].ad}</string>
</dict>
</plist>`;
  const blob=new Blob([plist],{type:'application/octet-stream'});
  const url=URL.createObjectURL(blob);
  const a=document.createElement('a');
  a.href=url;a.download='sim-sms-ilet-'+secili+'.shortcut';
  document.body.appendChild(a);a.click();document.body.removeChild(a);
  setTimeout(()=>URL.revokeObjectURL(url),1000);
  toast('Kısayol indirildi — dosyayı aç → Ekle');
}

let tt;
function toast(msg){
  const el=document.getElementById('toast');
  el.textContent=msg;el.className='toast on';
  if(tt)clearTimeout(tt);
  tt=setTimeout(()=>el.className='toast',2500);
}
</script>
</body>
</html>
```

- [ ] **Step 2: Tarayıcıda test et**

`sais-app.html` dosyasını Chrome'da aç:
- Tesis kartları görünüyor mu?
- Tesis seçince "✅ SMS İletimi Aktif" banner görünüyor mu?
- "Bağlantı Testi" butonu `✅ GAS bağlantısı başarılı` yazıyor mu?
- iPhone'da açıldığında iOS Kısayol kartı görünüyor mu? (Chrome DevTools → mobil simülatör)

---

## Task 4: APK `index.html` güncelle

**Files:**
- Modify: `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\assets\index.html`

- [ ] **Step 1: Mevcut index.html'i oku ve iki mod seçeneği ekle**

`index.html` dosyasını aç. Ana içeriğe (tesis listesinin üstüne) şu bloğu ekle:

```html
<!-- Rol Seçimi -->
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:20px;">
  <a href="sais-app.html" style="display:block;background:#041428;border:1px solid #1e293b;border-radius:12px;padding:18px;text-align:center;text-decoration:none;color:#e2e8f0;">
    <div style="font-size:28px;margin-bottom:8px">📡</div>
    <div style="font-size:13px;font-weight:700;color:#0ea5e9">SAİS Sorumlusu</div>
    <div style="font-size:11px;color:#64748b;margin-top:4px">SMS iletim paneli</div>
  </a>
  <a href="alici-app.html" style="display:block;background:#041428;border:1px solid #1e293b;border-radius:12px;padding:18px;text-align:center;text-decoration:none;color:#e2e8f0;">
    <div style="font-size:28px;margin-bottom:8px">🔐</div>
    <div style="font-size:13px;font-weight:700;color:#22c55e">Alıcı Girişi</div>
    <div style="font-size:11px;color:#64748b;margin-top:4px">SİM giriş & kod alma</div>
  </a>
</div>
```

---

## Task 5: APK assets'e kopyala ve push et

**Files:**
- Create: `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\assets\alici-app.html`
- Create: `C:\Users\HP\Documents\SIM-Giris-APK\app\src\main\assets\sais-app.html`

- [ ] **Step 1: İki dosyayı APK assets klasörüne kopyala**

```bash
cp "C:/Users/HP/Documents/SİM Giriş (openai-codex)/alici-app.html" \
   "C:/Users/HP/Documents/SIM-Giris-APK/app/src/main/assets/alici-app.html"

cp "C:/Users/HP/Documents/SİM Giriş (openai-codex)/sais-app.html" \
   "C:/Users/HP/Documents/SIM-Giris-APK/app/src/main/assets/sais-app.html"
```

- [ ] **Step 2: Commit ve push**

```bash
cd "C:/Users/HP/Documents/SIM-Giris-APK"
git add app/src/main/assets/alici-app.html \
        app/src/main/assets/sais-app.html \
        app/src/main/assets/index.html
git commit -m "Feat: alici-app ve sais-app eklendi, index iki mod ile guncellendi"
git push origin main
```

Beklenen çıktı: `main -> main` push başarılı

- [ ] **Step 3: GitHub Actions'ı kontrol et**

https://github.com/chemist45/SIM-Giris-APK/actions adresine git.
Yeşil ✅ görününce → son build → Artifacts → `SIM-Giris-APK.zip` indir.

- [ ] **Step 4: Yeni APK'yı test et**

1. APK'yı Android telefona yükle
2. Uygulama açılınca "SAİS Sorumlusu" ve "Alıcı Girişi" kartları görünüyor mu?
3. "Alıcı Girişi" → tesis seç → giriş bilgileri görünüyor mu?
4. "SAİS Sorumlusu" → tesis seç → "SMS İletimi Aktif" görünüyor mu?
5. Bağlantı Testi → `✅ GAS bağlantısı başarılı` görünüyor mu?

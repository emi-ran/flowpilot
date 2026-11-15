This patch keeps notification automations alive after an in-place update or OEM process kill, stops HyperOS volume-key noise from firing unfiltered rules, and makes a stale speech template explain itself instead of silently blocking Save.

---

## 🔔 Notification Reliability

- Android can leave notification access enabled while the listener is unbound, observed on HyperOS after an in-place APK update. The engine requests a system rebind during reconciliation when enabled rules need notification triggers; OEM process kills can also leave the listener unbound.
- Rebind requests are throttled to at most one attempt every 30 seconds and are skipped while the listener is connected or access is not granted. A check without access does not consume the retry window; Android controls when the listener actually reconnects.
- After `onListenerDisconnected`, a rebind check runs after approximately one second, subject to the same throttle and access checks.
- HyperOS posts silent, ongoing volume/headphone notifications from `com.miui.misound`. Those are now ignored by the listener, so pressing a volume key no longer fires an unfiltered notification rule. Matching is exact: unrelated packages such as `com.miui.misound.extra` are unaffected.

## ✍️ Editor Feedback

- The spoken-message editor now names the variables that no longer apply when the rule's triggers change. Instead of leaving **Save** disabled with no explanation, it lists the offending tokens — for example `Spoken message uses variables that do not apply to the selected triggers: ${notification.text}` — next to the preview result, in the same place as existing template errors.
- The message is derived from the current template and trigger selection, so it clears as soon as the template is fixed or a matching trigger is added back. No rule data, notification content, or rendered text is stored or logged for this check.

## 🧪 Verification

- The local JDK 17 release gate passed: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`, plus all eight SDK-free contract scripts. No device was connected; installation, launch, and the 2.2.1 fixes were not verified on a device. The release workflow requires the exact release commit `{{RELEASE_COMMIT}}` to be current `main` with a successful GitHub `Build & Test` run.
- The release workflow then re-runs every SDK-free contract script, debug unit tests, Android lint, debug APK assembly, and signed release APK assembly before publishing, and it verifies the APK package ID `com.flowpilot.app`, version code 8, version name 2.2.1, the signer certificate digest, and the SHA-256 checksum.
- Repository unit coverage for these fixes lives in `NotificationListenerRebindGateTest` (throttle window, denied access, revocation, connect/disconnect transitions) and `NotificationPackageFilterTest` (own build, sibling debug build, and `com.miui.misound` filtering).

## 📥 Installation

1. Download **`{{APK_NAME}}`** from Assets below.
2. Download **`{{APK_NAME}}.sha256`** or copy the checksum below.
3. Verify the APK checksum before installation.
4. Install on Android 8.0+ (API 26–36). Configure Shizuku only for privileged system actions.

### SHA-256 Checksum

```text
SHA256 ({{APK_NAME}}) = {{SHA256}}
```

## 📱 Compatibility

Developed and tested primarily on Xiaomi HyperOS (Xiaomi 15T Pro). Android OEM background restrictions can differ. Background NFC discovery requires explicit confirmation; foreground ReaderMode scans remain automatic. Privileged system actions require active Shizuku permission. Upgrading from 2.2.0 keeps existing rules, exports, encrypted backups, and speech templates readable; no rule migration is required for this patch. TTS cache files remain app-private and are excluded from backups. If notifications still do not arrive after updating, confirm notification access is granted to FlowPilot in system settings.

---

## 🇹🇷 Türkçe Özet

- Bu yama; yerinde güncelleme veya OEM süreç sonlandırması sonrasında bildirim tetikleyicilerinin sessizce ölmesini engelliyor, HyperOS ses tuşu bildirimlerinin filtresiz kuralları tetiklemesini durduruyor ve eskimiş konuşma şablonunun neden kaydedilemediğini açıklıyor.
- Android bildirim erişimini açık bıraktığı hâlde dinleyiciyi bağlamamış durumda bırakabiliyor; bu durum HyperOS'ta yerinde APK güncellemesi sonrasında gözlendi. Motor, bildirim tetikleyicisi kullanan etkin kuralları uzlaştırırken sistemden yeniden bağlanma isteğinde bulunuyor. OEM süreç sonlandırması da dinleyiciyi bağlı değil durumunda bırakabilir.
- İstekler 30 saniyede en fazla bir kez deneniyor; dinleyici bağlıyken veya erişim verilmemişken istek yapılmıyor. Erişim olmadan yapılan kontrol bekleme penceresini tüketmiyor; gerçek yeniden bağlanma zamanını Android belirliyor.
- `onListenerDisconnected` sonrasında yaklaşık bir saniye içinde aynı sınır ve izin kontrollerine tabi bir yeniden bağlanma kontrolü yapılıyor.
- HyperOS'un `com.miui.misound` paketinden gönderdiği sessiz, sürekli ses/kulaklık bildirimleri artık yok sayılıyor; ses tuşuna basmak filtresiz bir bildirim kuralını tetiklemiyor. Eşleşme tam ada göre yapılıyor; `com.miui.misound.extra` gibi diğer paketler etkilenmiyor.
- Konuşma mesajı düzenleyicisi, kuralın tetikleyicileri değiştiğinde artık geçersiz kalan değişkenleri adlarıyla gösteriyor. **Kaydet** düğmesi sessizce devre dışı kalmak yerine, önizleme hatasıyla aynı yerde örneğin `Seslendirilecek mesaj seçili tetikleyicilerle uyuşmayan değişkenler içeriyor: ${notification.text}` uyarısı görünüyor.
- Bu uyarı mevcut şablon ve tetikleyici seçiminden türetiliyor; şablon düzeltildiğinde veya uygun tetikleyici geri eklendiğinde kendiliğinden kayboluyor. Bu kontrol için kural verisi, bildirim içeriği veya üretilen metin saklanmıyor ya da loglanmıyor.
- Yerel JDK 17 release kontrolü geçti: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` ve sekiz SDK'sız sözleşme betiğinin tamamı. Bağlı cihaz olmadığından kurulum, açılış ve 2.2.1 düzeltmeleri cihazda doğrulanmadı. Release iş akışı tam release commit'inin `{{RELEASE_COMMIT}}` güncel `main` olmasını ve GitHub `Build & Test` kontrolünün başarılı olmasını gerektirir.
- Release iş akışı yayından önce tüm SDK'sız sözleşme betiklerini, birim testlerini, Android lint'i, debug APK ve imzalı release APK derlemesini yeniden çalıştırıyor; paket kimliği `com.flowpilot.app`, sürüm kodu 8, sürüm adı 2.2.1, imza sertifikası özeti ve SHA-256 değerini doğruluyor.
- Bu düzeltmelerin birim kapsamı `NotificationListenerRebindGateTest` (bekleme penceresi, reddedilen izin, izin kaldırma, bağlanma/ayrılma geçişleri) ve `NotificationPackageFilterTest` (kendi derleme, yan yana debug derleme ve `com.miui.misound` filtresi) içinde bulunuyor.
- Kurulumdan önce `{{APK_NAME}}` dosyasının SHA-256 değerini doğrulayın. Android 8.0+ (API 26–36) gerekir; Shizuku yalnızca ayrıcalıklı sistem eylemleri için yapılandırılmalıdır.
- Xiaomi HyperOS birincil hedeftir; OEM arka plan kısıtlamaları farklılık gösterebilir. Arka plan NFC keşfi açık onay ister, ön plan ReaderMode taraması otomatiktir. Ayrıcalıklı eylemler etkin Shizuku izni gerektirir. 2.2.0'dan yükseltme mevcut kuralları, dışa aktarmaları, şifreli yedekleri ve konuşma şablonlarını korur; bu yama için kural taşıma adımı gerekmez. TTS cache dosyaları uygulamaya özeldir ve yedeklere girmez. Güncellemeden sonra bildirimler hâlâ gelmiyorsa FlowPilot'a sistem ayarlarından bildirim erişimi verildiğini doğrulayın.

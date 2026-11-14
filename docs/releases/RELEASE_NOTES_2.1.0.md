This release adds optional in-app update checks, Android 16 Wi-Fi hotspot actions, and a separate FlowPilot Dev build, together with a Shizuku startup fallback and faster CI.

---

## 🔄 In-App Updates

- Settings > Updates can check the public GitHub Releases API at most once every 24 hours or on demand. A newer stable release opens a notes dialog with Download & Install, Open release page, Remind me later, and Skip this version; automatic checks can be disabled.
- Downloads are limited to HTTPS assets under `github.com/emi-ran/flowpilot/releases/`. The APK is handed to Android's DownloadManager, accepted only when the completion id and success status match the saved request, and installed only after the user confirms the system installer. There is no silent install path.
- Skipped versions and reminder deadlines persist, manual checks ignore them, and no account or device identifier is sent. Debug builds hide the Updates card and skip release checks because a debug-signed package cannot be replaced by the release APK.

## 📶 Hotspot & Shizuku Reliability

- New `HOTSPOT_ON` and `HOTSPOT_OFF` actions target Android 16+: FlowPilot starts a shell-side `HotspotBridge` through Shizuku's `newProcess` as `app_process` against its own APK, registers as the tethering owner (`com.android.shell`), and toggles tethering through the public `TetheringManager` API. Android 15 and older report the action as unsupported.
- Verified on Xiaomi 15T Pro (Android 16): the bridge toggled tethering off and on with the screen off, and a targeted instrumentation run exercised the full app → Shizuku → bridge path. The R8-minified release APK retains `HotspotBridge`.
- When Shizuku's `UserService` cannot start on Android 16 (`LoadedApk.makeApplicationInner` crash, Shizuku issue #2541), `ShizukuShell` now falls back to Shizuku's allowlisted `newProcess` shell execution with the same 10-second timeout instead of failing permanently. The UserService is retried after 60 seconds or a binder restart.

## 🧑‍💻 Developer Build Separation

- Debug builds install as **FlowPilot Dev** with application ID `com.flowpilot.app.debug`, a `-dev` version name, and a green/mint launcher icon. A development APK no longer replaces or uninstalls the signed release app, and both can stay installed side by side.
- Release builds keep `com.flowpilot.app`, the existing dark/blue icon, and the release update channel. Shizuku, notification access, and other privileged permissions must be granted to the debug package separately.

## ⚙️ Build & CI

- Android CI and the tag-release workflow restore and save the Gradle cache with `gradle/actions/setup-gradle` v4.4.4, use the Gradle build cache, and run tests, lint, debug assembly, and release validation in a single Gradle invocation.
- The Android-change detector now fails open when the base commit cannot be resolved, and only pull-request runs cancel in progress, so the main push run used by release validation is not replaced by a newer push.

## 🧪 Verification

- Required GitHub `Build & Test` completed successfully for exact release commit `{{RELEASE_COMMIT}}`.
- The release workflow re-runs resource contracts, debug unit tests, Android lint, debug APK assembly, and signed release APK assembly, then verifies package ID, version code/name, signer certificate digest, and SHA-256 before publishing.
- Local release gate passed with JDK 17: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease -PreleaseSigningRequired=false`, plus `python scripts/test_lint_resource_contracts.py` (EN/TR parity).
- Unit coverage includes update version parsing, release/URL trust, prompt policy, notes formatting, filename sanitization, hotspot action routing, and the Shizuku shell fallback path.
- Physical-device checks passed for the update preview flow (v2.0.0 prompt, Skip, manual re-check, trusted download, and system installer confirmation), Android 16 hotspot toggling, and launching the separate FlowPilot Dev build. Shizuku and notification permissions for the debug package were not granted during that smoke test.
- Not verified: v2.1.0 itself was not exercised as an in-app update target; the update prompt smoke test used the published v2.0.0 release.

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

Developed and tested primarily on Xiaomi HyperOS (Xiaomi 15T Pro). Android OEM background restrictions can differ. Background NFC discovery requires explicit confirmation; foreground ReaderMode scans remain automatic. Privileged system actions require active Shizuku permission. Wi-Fi hotspot actions are available only on Android 16+ with Shizuku. Upgrading from 2.0.0 keeps existing rules, exports, and encrypted backups readable.

---

## 🇹🇷 Türkçe Özet

- Ayarlar > Güncellemeler bölümü genel GitHub Releases API'sini günde en fazla bir kez veya elle kontrol edebilir. Yeni kararlı sürüm için notlar penceresi; İndir ve Kur, Sürüm sayfasını aç, Daha sonra hatırlat ve Bu sürümü atla seçeneklerini sunar; otomatik kontrol kapatılabilir.
- İndirme yalnızca `github.com/emi-ran/flowpilot/releases/` altındaki HTTPS APK'larıyla sınırlıdır. Dosya Android DownloadManager'a verilir, tamamlanma kimliği ve başarı durumu eşleşirse kabul edilir ve yalnızca kullanıcı sistem yükleyicisini onayladıktan sonra kurulur; sessiz kurulum yolu yoktur.
- Atlanan sürüm ve hatırlatma tarihi saklanır; elle kontrol bunları yok sayar. Hesap veya cihaz kimliği gönderilmez. Debug derlemeleri Güncellemeler kartını gizler ve kontrol yapmaz.
- Android 16+ için `HOTSPOT_ON` / `HOTSPOT_OFF` eylemleri eklendi: FlowPilot, Shizuku `newProcess` ile kendi APK'sına karşı `app_process` olarak `HotspotBridge` çalıştırır, `com.android.shell` tethering sahibi olarak kaydolur ve genel `TetheringManager` API'siyle açıp kapatır. Android 15 ve altı eylemi desteklenmiyor olarak bildirir.
- Xiaomi 15T Pro (Android 16) üzerinde doğrulandı: köprü ekran kapalıyken tethering'i kapatıp açtı ve hedefli instrumentation çalışması uygulama → Shizuku → köprü yolunu denedi. R8 ile küçültülen release APK'sı `HotspotBridge` sınıfını korur.
- Shizuku `UserService` Android 16'da başlatılamadığında (`LoadedApk.makeApplicationInner` çökmesi, Shizuku #2541), `ShizukuShell` kalıcı hata vermek yerine aynı 10 saniyelik zaman aşımıyla Shizuku'nun izinli `newProcess` kabuk çalıştırmasına geçer; UserService 60 saniye veya binder yeniden başlatmasından sonra yeniden denenir.
- Debug derlemeleri **FlowPilot Dev** adıyla, `com.flowpilot.app.debug` kimliğiyle, `-dev` sürüm adıyla ve yeşil/mint simgeyle kurulur. Geliştirme APK'sı imzalı release uygulamasını artık değiştirmez veya kaldırmaz; iki uygulama yan yana kalabilir. Release `com.flowpilot.app`, mevcut koyu/mavi simge ve güncelleme kanalı olarak kalır. Shizuku ve bildirim izinleri debug paketine ayrıca verilmelidir.
- Android CI ve etiket release iş akışı Gradle önbelleğini `gradle/actions/setup-gradle` v4.4.4 ile yönetir, build cache kullanır ve test, lint, debug derleme ile release doğrulamasını tek Gradle çağrısında çalıştırır. Değişiklik algılayıcı temel commit çözülemediğinde derlemeye devam eder ve yalnızca pull-request çalışmalarını iptal eder; release doğrulamasında kullanılan main push çalışması yeni bir push ile iptal edilmez.
- Gerekli GitHub `Build & Test` işi tam release commit'i `{{RELEASE_COMMIT}}` için başarıyla tamamlandı.
- Release iş akışı kaynak sözleşmelerini, birim testlerini, Android lint'i, debug APK ve imzalı release APK derlemesini yeniden çalıştırır; yayından önce paket kimliği, sürüm kodu/adı, imza sertifikası özeti ve SHA-256 değerini doğrular.
- Yerel release kapısı JDK 17 ile geçti: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease -PreleaseSigningRequired=false` ve `python scripts/test_lint_resource_contracts.py` (EN/TR eşliği).
- Birim kapsamı güncelleme sürüm ayrıştırma, sürüm/URL güveni, istem politikası, not biçimlendirme, dosya adı temizleme, hotspot eylem yönlendirmesi ve Shizuku kabuk geri dönüş yolunu içerir.
- Fiziksel cihaz kontrolleri güncelleme önizleme akışı (v2.0.0 bildirimi, Bu sürümü atla, elle yeniden kontrol, güvenilir indirme ve sistem yükleyici onayı), Android 16 hotspot aç/kapat ve ayrı FlowPilot Dev derlemesinin açılması için geçti. Debug paketinin Shizuku ve bildirim izinleri bu smoke test sırasında verilmedi.
- Doğrulanmayan: v2.1.0'ın kendisi uygulama içi güncelleme hedefi olarak denenmedi; güncelleme bildirimi smoke testi yayınlanmış v2.0.0 sürümüyle yapıldı.
- Kurulumdan önce `{{APK_NAME}}` dosyasının SHA-256 değerini doğrulayın.

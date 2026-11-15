This release adds safer rule management, strengthens automation trust boundaries, and improves privacy-safe English and Turkish background behavior.

---

## ✨ Safer Rule Management

- Duplicate any rule from Home into a disabled, immediately editable copy with a new identity and reset runtime state.
- Duplicated webhook secrets receive fresh Android Keystore ciphertext; cached TTS audio is copied independently with failure-safe cleanup.
- Conflict warnings detect opposing state actions before saving or enabling a rule.
- Warnings distinguish likely and possible conflicts, preserve the pending operation, allow inspection of the conflicting rule, and require deliberate override.

## 🛡️ Automation Security

- Background NFC discovery now requires visible confirmation before a matching automation can run; trusted foreground ReaderMode scans remain automatic.
- Webhook connections pin initial delivery to prevalidated public IP addresses while preserving TLS hostname verification.
- Rendered webhook headers reject unsafe input, and HTTP/1.1 response parsing is bounded.
- SMS and notification events are accepted only while the engine is enabled, freshness-limited, bounded, and reauthorized immediately before execution.
- Durable execution leases and rule-revision checks prevent cooldown races and revoke queued work after a rule changes.

## 🔒 History Privacy

- Execution-history rule names, trigger snapshots, action arguments, and failure messages are sanitized before persistence and during legacy migration.
- Raw provider errors, private URIs, local paths, credentials, phone numbers, and embedded synthetic markers are not retained in history.
- Executors and dispatcher failures use stable privacy-safe outcomes instead of persisting raw exception text.

## 🌐 Language & Background Notifications

- Engine and startup-failure notifications follow saved English, Turkish, or system-language selection across boot, service restart, process recreation, and task removal.
- Changing language refreshes active notification text, channel metadata, and widget state immediately.
- System-language mode no longer remains stuck on a previously selected app language.

## 🌍 Project Site & Release Integrity

- Project site gains improved mobile layout, browser-language selection, accessible brand navigation, honest network/runtime claims, and v1.1.0 installation guidance.
- Release automation requires exact version/tag alignment, current `main`, successful `Build & Test` for exact release commit, prepared notes, verified APK identity/signature, and signed APK output.

## 🧪 Verification

- Required GitHub `Build & Test` completed successfully for exact release commit `{{RELEASE_COMMIT}}`.
- Release workflow completed resource contracts, debug unit tests, Android lint, debug APK assembly, and signed release APK assembly.
- Physical-device checks passed for safe rule duplication, conflict warnings, background NFC confirmation, and language switching.
- Release workflow verified APK SHA-256 before publication.

## 📥 Installation

1. Download **`{{APK_NAME}}`** from Assets below.
2. Download **`{{APK_NAME}}.sha256`** or copy checksum below.
3. Verify APK checksum before installation.
4. Install on Android 8.0+ (API 26–36). Configure Shizuku only for privileged system actions.

### SHA-256 Checksum

```text
SHA256 ({{APK_NAME}}) = {{SHA256}}
```

## 📱 Compatibility

Developed and tested primarily on Xiaomi HyperOS (Xiaomi 15T Pro). Android OEM background restrictions can differ. Background NFC discovery requires explicit confirmation; foreground ReaderMode scans remain automatic. Privileged system actions require active Shizuku permission.

---

## 🇹🇷 Türkçe Özet

- Ana ekrandan kurallar güvenli biçimde çoğaltılabilir; kopya devre dışı oluşturulur, hemen düzenlemeye açılır ve çalışma durumu sıfırlanır.
- Kural kaydedilirken veya etkinleştirilirken karşıt işlemler için olası çakışma uyarıları gösterilir.
- Arka plan NFC keşfi otomasyonu çalıştırmadan önce görünür kullanıcı onayı ister; ön plandaki ReaderMode taramaları otomatik çalışmaya devam eder.
- Webhook bağlantıları doğrulanmış genel IP adreslerine sabitlenir; TLS ana makine doğrulaması korunur ve güvenli olmayan başlıklar reddedilir.
- SMS ve bildirim olayları yalnız motor etkinken, süre ve boyut sınırlarıyla kabul edilir; çalıştırmadan hemen önce yeniden yetkilendirilir.
- Çalıştırma geçmişindeki hata, argüman, kural adı ve tetikleyici verileri kaydedilmeden önce gizlilik için temizlenir.
- Motor bildirimleri, başlangıç hata bildirimleri ve kanal bilgileri seçilen uygulama dilini kullanır; dil değişikliği etkin bildirimlere hemen uygulanır.
- Kural çoğaltma, çakışma uyarısı, NFC onayı ve dil değişikliği fiziksel cihazda doğrulandı.
- Kurulumdan önce `{{APK_NAME}}` dosyasının SHA-256 değerini doğrulayın.

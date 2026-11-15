FlowPilot 2.3.0 brings JSON-driven automation, more precise notification filters, media controls, calendar events and alarm improvements, with clearer feedback about what Android has actually confirmed.

---

## 🤖 JSON Commands & Notifications

- **Run JSON command** accepts a version 1 JSON object from a configured template, including notification text or an SMS body. It supports normal FlowPilot actions without bypassing their existing permissions and capability checks. See [JSON commands](https://github.com/emi-ran/flowpilot/blob/v2.3.0/docs/JSON_COMMANDS.md) for the exact schema, supported actions, limits and an installable agent skill.
- For example, an external AI agent could send JSON through ntfy; a separately configured ntfy notification and FlowPilot rule could then control the phone. FlowPilot does not embed an AI agent or an ntfy integration. Notification content must contain the complete JSON object, and delivery and execution depend on Android and the rule configuration.
- Notification rules can combine the selected app with independent title and message filters using contains or exact matching. Existing keyword rules keep their earlier OR matching behavior. **Filters route messages; they do not authenticate senders.** Anyone able to provide matching content may trigger actions, including calls, SMS or disruptive device changes. Use trusted sources, restrictive filters and a local recovery path; obtain explicit permission before potentially billable calls or SMS.
- Subsequent actions can use the current command's transient status and message, for example in a separately configured HTTPS webhook reply. A webhook's successful HTTP response is not proof of subscriber delivery. JSON source templates are encrypted on the device; normal exports clear them, while encrypted exports preserve them.

## 🎵 Media, Calendar & Alarms

- Control media sessions and react to playback or track changes, with optional app selection. Notification access and an active session are required for media controls.
- Search Spotify or YouTube Music and open supported song links. The target app and Android determine whether the request opens or plays; background launches can be blocked. Optional display-over-other-apps access may help on supported devices but grants overlay capability and guarantees neither launch nor playback.
- Create events in an explicitly selected writable calendar, with recurrence and reminders up to four weeks before the event. Calendar read/write permissions and an available provider are required.
- Alarm requests support optional repeat weekdays and templated labels. The clock app controls the actual alarm behavior and interface.

## 🛡️ Reliability & Safety

- Activity launches are reported as **unverified** when Android accepts a request without confirming the outcome; older JSON history is conservatively marked unverified where the nested action is unknown.
- Permission status distinguishes notification access from an active listener connection. Android controls reconnection; retries do not guarantee recovery or replay missed notifications.
- More actionable, content-free diagnostics help explain failures without retaining notification text, URLs, phone numbers or raw errors. Saved-rule shortcuts recheck enabled state and revisions; alphanumeric SMS sender filters use case-insensitive exact matching, not sender authentication.
- Remote connectivity changes and device locking can interrupt access. JSON commands do not bypass Android permissions, Shizuku authorization, background restrictions or device capabilities; command IDs do not authenticate or deduplicate requests.

## 🧪 Verification

- The release workflow requires successful main-branch CI for `{{RELEASE_COMMIT}}`, runs unit tests and lint, and verifies the signed APK's package, version, signing certificate and checksum before publication. Historical development-device observations below do not imply that the signed release APK was tested on a device.
- On a Xiaomi 15T Pro, the user reported ntfy-driven torch on/off and toggle, vibration, notification, speech, sound and an alarm working in specific trials. A manually run JSON command also passed its status to a subsequent notification. These observations are partial: they do not verify every action, device or build, automatic filter rejection, webhook/SMS delivery, long notification payloads or listener recovery after disconnection. One alarm attempt did not create an alarm; its cause was not established.
- The user reported that the calendar flow and a custom reminder worked, but the exact reminder-delivery scope was not independently established. An earlier background music launch experiment succeeded under specific device conditions; it does not establish general playback or background-launch reliability. Development and device verification will continue.

## 📥 Installation

1. Download **`{{APK_NAME}}`** and its accompanying checksum from the release assets when available.
2. Compare the APK's SHA-256 with the published checksum before installing. A matching checksum alone does not establish signer identity; see [supply-chain verification](https://github.com/emi-ran/flowpilot/blob/v2.3.0/docs/SUPPLY_CHAIN.md).
3. Install on Android 8.0+ (API 26 and later). Configure Shizuku only for actions that require privileged access, and grant other permissions only as needed.

### SHA-256 Checksum

```text
SHA256 ({{APK_NAME}}) = {{SHA256}}
```

## 📱 Compatibility

Android behavior varies by device, OS version, permissions, installed apps and OEM background restrictions. Physical-device observations above are limited to the specific Xiaomi HyperOS trials described; they do not imply coverage of all supported Android versions. Music, alarm and other activity requests can be blocked in the background or accepted without confirming their outcome.

---

## 🇹🇷 Türkçe Özet

- JSON komutları, bildirim veya SMS gibi mevcut tetikleyicilerden alınan tam bir nesneyle FlowPilot eylemlerini çalıştırabilir. Örneğin harici bir AI ajanı ntfy üzerinden komut gönderebilir; ayrıca yapılandırılmış ntfy bildirimi ve FlowPilot kuralı telefonu kontrol edebilir. Uygulamaya AI ajanı veya ntfy entegrasyonu gömülü değildir.
- Uygulama, başlık ve mesaj filtreleri birlikte kullanılabilir; filtreler **gönderenin kimliğini doğrulamaz**. Eşleşen içerik gönderebilen kişiler eylem başlatabilir. Güvenilir kaynaklar ve dar filtreler kullanın, yerel kurtarma yolu bırakın; ücret doğurabilecek arama veya SMS öncesinde açık izin alın. Android izinleri ve Shizuku sınırları geçerlidir.
- Medya kontrolleri ve tetikleyicileri, Spotify/YouTube Music arama ve bağlantıları, seçilen yazılabilir takvime etkinlik ve hatırlatma, haftanın günleriyle tekrar eden alarm istekleri eklendi. Android ve hedef uygulama açılmayı veya oynatmayı garanti etmez; ekran üstü erişimi isteğe bağlıdır ve overlay yetkisi verir.
- Android'in sonucunu doğrulamadığı activity istekleri doğrulanmamış gösterilir. Bildirim erişimi ile canlı dinleyici bağlantısı ayrıdır; yeniden bağlanma veya kaçırılan bildirimlerin tekrarı garanti edilmez.
- Yayın akışı `{{RELEASE_COMMIT}}` için başarılı main CI gerektirir; birim testlerini ve lint kontrolünü çalıştırır, yayınlamadan önce imzalı APK'nın paketini, sürümünü, imza sertifikasını ve checksum değerini doğrular. Aşağıdaki cihaz gözlemleri imzalı yayın APK'sının cihazda test edildiği anlamına gelmez.
- Xiaomi 15T Pro'da kullanıcı bazı ntfy JSON fener, titreşim, bildirim, konuşma, ses ve alarm denemelerinin çalıştığını bildirdi. Takvim ve özel hatırlatma için kullanıcı gözlemleri de kısmi; tüm eylemler, cihazlar, uzun bildirimler, webhook/SMS teslimi ve kopma sonrası dinleyici kurtarma doğrulanmış değildir. Geliştirme ve cihaz doğrulaması sürecek.
- Kurulumdan önce `{{APK_NAME}}` dosyasının SHA-256 değerini `{{SHA256}}` ile karşılaştırın; checksum tek başına imzalayanın kimliğini doğrulamaz.

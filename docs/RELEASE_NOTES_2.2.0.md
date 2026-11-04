This release turns action text into live templates, moves offline speech to on-demand synthesis with a shared cache, adds a first-launch guided tour, and gives every automation a confirmation-gated home-screen shortcut.

---

## 🗣️ Dynamic Speech & Templates

- `${variable}` placeholders now render from the actual notification, SMS, call, app, Wi-Fi, Bluetooth, NFC, shake, light, geofence, screen, and power events. The speech editor's trigger-aware searchable picker inserts only variables that apply to the rule's configured triggers and shows filter examples.
- Safe template filters (`trim`, `stripPrefix`, `drop`, `truncate`, `lower`, `upper`, `default`, `urlencode`, `jsonescape`), quote-aware parsing, one-pass rendering, and `$${` escaping are supported. Unknown variables fail speech rendering; other action fields keep unknown tokens unchanged.
- The trigger-aware picker is available from every template-capable action field: notification, URL, alarm, timer, webhook, phone, SMS, and speech. SMS now uses the same searchable picker instead of a fixed quick-variable chip row.
- Offline TTS synthesizes on demand instead of requiring a separate Generate step. A content-addressed cache is shared across rules, with atomic writes, a 7-day TTL, a 50 MB cap, LRU eviction, periodic cleanup, and a manual Settings clear action.

## 🧭 Guided First Launch

- A six-step interactive spotlight tour introduces the welcome screen, ready-made presets, rule creation, engine status, Settings, and the final hand-off to presets. It dims the screen, highlights the real anchor for each step, supports Back/Next, and can be skipped at any point.
- Completion is persisted in DataStore, so the tour opens automatically only once. Settings gains a **Replay tutorial** row to run it again on demand.
- The create step is skipped automatically when the rule list is non-empty, and anchor-less steps do not affect the progress count.

## 🏠 Per-Rule Home Shortcuts

- Edit → Advanced can pin or remove a launcher shortcut for one automation. The shortcut carries only the rule id; `RuleShortcutActivity` reloads the current rule and always asks for confirmation before running.
- Missing or disabled rules never run. An enabled shortcut run bypasses trigger, conditions, cooldown, and the engine pause state without changing the rule's enabled state or last-triggered time, and it records a redacted manual history entry.
- On HyperOS, a denied Home screen shortcuts app-op is detected before the pin request. The editor opens the MIUI permission page instead of failing silently, with an app-details fallback when the intent is unavailable.

## 🔒 Privacy & Reliability

- Notification, SMS, call, app, Wi-Fi, Bluetooth, NFC, shake, light, geofence, screen, and power values enter only a transient template context; they are not written to rule fields, execution history, logs, or exports.
- Rendered TTS audio stays in the app-private cache, is excluded from backups, and rendered text is never logged.
- FlowPilot's own engine and status notifications, including those from a side-by-side debug build, are ignored by the notification listener. A rule with no app filter can no longer trigger on FlowPilot's ongoing notification and repeatedly speak its own status.
- New TTS actions no longer adopt the alphabetically first offline voice when none was chosen. An unset voice uses the phone's default TTS voice, shows as **System default**, and can be restored from the voice picker. The editor preview uses a demo sentence in the selected voice's language.
- Import/export carries speech templates instead of audio. Missing legacy audio is reported after import, and dynamic rules synthesize on first play. Notification matching keeps primary text and expanded text separate for `${notification.text}` and `${notification.bigText}` while still matching both.

## 🧪 Verification

- Required GitHub `Build & Test` completed successfully for exact release commit `{{RELEASE_COMMIT}}`.
- The release workflow re-runs resource contracts, debug unit tests, Android lint, debug APK assembly, and signed release APK assembly, then verifies package ID, version code/name, signer certificate digest, and SHA-256 before publishing.
- Local release gate passed with JDK 17: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease -PreleaseSigningRequired=false`, plus `python scripts/test_lint_resource_contracts.py` (EN/TR parity 7/7).
- Unit coverage includes template parsing and filters, picker insertion at the cursor, SMS/global variable availability, TTS cache lifecycle, shortcut pin/permission decisions, manual-run history, and rule import validation.
- Physical Xiaomi 15T Pro / HyperOS 3 checks passed for the first-launch tutorial, shortcut permission/pin/run/disabled flow, notification TTS, voice picker, System default, and language-aware preview.
- Not verified on a device: SMS speech and shared-picker insertion after the latest build; those paths remain covered by unit and build checks only.

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

Developed and tested primarily on Xiaomi HyperOS (Xiaomi 15T Pro). Android OEM background restrictions can differ. Background NFC discovery requires explicit confirmation; foreground ReaderMode scans remain automatic. Privileged system actions require active Shizuku permission. Upgrading from 2.1.0 keeps existing rules, exports, and encrypted backups readable. TTS cache files remain app-private and are excluded from backups. SMS speech remains device-pending.

---

## 🇹🇷 Türkçe Özet

- `${...}` yer tutucuları artık bildirim, SMS, arama, uygulama, Wi-Fi, Bluetooth, NFC, sarsıntı, ışık, konum, ekran ve güç olaylarından canlı değerlerle doluyor. Konuşma düzenleyicisindeki tetikleyiciye duyarlı aranabilir seçici yalnızca kuralın tetikleyicilerine uyan değişkenleri ekliyor.
- `trim`, `stripPrefix`, `drop`, `truncate`, `lower`, `upper`, `default`, `urlencode` ve `jsonescape` filtreleri, tırnak farkındalıklı ayrıştırma, tek geçişli render ve `$${` kaçışı eklendi. Bilinmeyen değişken konuşmayı başarısız kılar; diğer eylem alanları bilinmeyen token'ları korur.
- Değişken seçici bildirim, URL, alarm, zamanlayıcı, webhook, telefon, SMS ve konuşma alanlarında ortak. SMS artık sabit hızlı değişken satırı yerine aynı aranabilir seçiciyi kullanıyor.
- Çevrimdışı TTS önceden ses üretme adımı olmadan istek üzerine sentezleniyor. Kurallar arasında paylaşılan içerik-adresli cache; atomik yazma, 7 gün TTL, 50 MB sınır, LRU tahliye, periyodik temizlik ve Ayarlar'dan manuel temizleme sunuyor.
- İlk açılışta altı adımlı spotlight turu; karşılama, hazır şablonlar, kural oluşturma, motor durumu, Ayarlar ve şablonlara geçiş adımlarını gösteriyor. Ekranı karartıp gerçek bağlantı noktasını vurguluyor, geri/ileri destekliyor ve istenirse atlanabiliyor.
- Tur tamamlanması DataStore'da saklanıyor; yalnızca ilk açılışta otomatik gösteriliyor. Ayarlar'daki **Eğitimi yeniden oynat** satırıyla tekrar çalıştırılabiliyor. Kural listesi doluysa oluşturma adımı otomatik atlanıyor.
- Düzenle → Gelişmiş bölümünden kurala özel ana ekran kısayolu sabitlenip kaldırılabiliyor. Kısayol yalnızca kural kimliğini taşıyor; `RuleShortcutActivity` güncel kuralı yükleyip çalıştırmadan önce her zaman onay istiyor.
- Silinmiş veya devre dışı kural çalışmıyor. Etkin kısayol çalıştırması tetikleyici, koşullar, cooldown ve motor duraklatma durumunu atlıyor; kuralın etkin durumunu ve son tetiklenme zamanını değiştirmiyor ve redakte edilmiş manuel geçmiş kaydı ekliyor.
- HyperOS'ta ana ekran kısayolu izni reddedilmişse bu durum pin isteğinden önce algılanıyor; düzenleyici sessiz başarısızlık yerine MIUI izin sayfasını açıyor, intent yoksa uygulama ayrıntılarına yönlendiriyor.
- Bildirim, SMS, arama, uygulama, Wi-Fi, Bluetooth, NFC, sarsıntı, ışık, konum, ekran ve güç değerleri yalnızca geçici şablon bağlamında tutuluyor; kural alanlarına, geçmişe, loglara veya dışa aktarmaya yazılmıyor.
- Üretilen TTS sesi uygulama özel cache'inde kalıyor, yedeklere girmiyor ve render edilen metin loglanmıyor. FlowPilot'un kendi motor/durum bildirimleri, yan yana kurulu debug derlemesi dahil, tetikleyici olarak yok sayılıyor.
- Yeni TTS eylemleri ses seçilmediğinde alfabetik ilk sesi zorla seçmiyor; telefonun varsayılan sesi **Sistem varsayılanı** olarak kullanılıyor ve ses seçiciden geri alınabiliyor. Önizleme, seçilen sesin dilinde örnek cümle kullanıyor.
- İçe/dışa aktarma ses yerine konuşma şablonlarını taşıyor; eski ses dosyası yoksa içe aktarma sonrası bilgi veriliyor ve dinamik kurallar ilk oynatmada sentezleniyor. `${notification.text}` ile `${notification.bigText}` ayrı tutulurken ikisi de eşleştirmeye devam ediyor.
- Gerekli GitHub `Build & Test` işi tam release commit'i `{{RELEASE_COMMIT}}` için başarıyla tamamlandı. Release iş akışı kaynak sözleşmelerini, birim testlerini, Android lint'i, debug APK ve imzalı release APK derlemesini yeniden çalıştırıyor; yayından önce paket kimliği, sürüm kodu/adı, imza sertifikası özeti ve SHA-256 değerini doğruluyor.
- Yerel release kapısı JDK 17 ile geçti: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease -PreleaseSigningRequired=false` ve `python scripts/test_lint_resource_contracts.py` (EN/TR eşliği 7/7).
- Fiziksel Xiaomi 15T Pro / HyperOS 3 kontrolleri ilk açılış turu, kısayol izni/sabitleme/çalıştırma/devre dışı akışı, bildirim TTS'i, ses seçici, Sistem varsayılanı ve dile duyarlı önizleme için geçti.
- Cihazda doğrulanmayan: son derlemeden sonra SMS konuşması ve ortak değişken seçicinin imlece eklenmesi; bu yollar yalnızca birim ve derleme kontrolleriyle kapsanıyor.
- Kurulumdan önce `{{APK_NAME}}` dosyasının SHA-256 değerini doğrulayın. Android 8.0+ (API 26–36) gerekir; Shizuku yalnızca ayrıcalıklı sistem eylemleri için yapılandırılmalıdır.
- Xiaomi HyperOS birincil hedeftir; OEM arka plan kısıtlamaları farklılık gösterebilir. Arka plan NFC keşfi açık onay ister, ön plan ReaderMode taraması otomatiktir. Ayrıcalıklı eylemler etkin Shizuku izni gerektirir. 2.1.0'dan yükseltme mevcut kuralları, dışa aktarmaları ve şifreli yedekleri korur. TTS cache dosyaları uygulamaya özeldir ve yedeklere girmez. SMS konuşması cihazda beklemededir.

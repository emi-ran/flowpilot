# FlowPilot Agent Rules

## Structure

- Tek Android modülü: `:app`. Başlangıç: `FlowPilotApp`; Compose UI: `MainActivity`; automation runtime: `engine/AutomationService` foreground service.
- Rules DataStore/JSON ile saklanır. Webhook sırları Android Keystore ile şifrelenir; normal export sanitization veya encrypted restore mutation-öncesi doğrulaması zayıflatılmaz.

## Verification

- Subagentlar Gradle compile, test, build, APK install veya cihaz launch komutu çalıştırmaz.
- Ana ajan Gradle build/test doğrulamasını çalıştırır.
- Ana ajan build başarılıysa debug APK'yı bağlı hedef cihaza yükler ve `com.flowpilot.app` uygulamasını açar.
- Resource/manifest veya localization değişikliğinde Android SDK gerektirmeyen `python scripts/test_lint_resource_contracts.py` çalıştırılır. Bu test EN/TR string parity kontrol eder.
- Ana ajan release gate: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`.
- `connectedDebugAndroidTest` yalnız manual CI dispatch ile API 35 emulator üzerinde çalışır; fiziksel cihaz/OEM smoke testinin yerine geçmez.

## Feature Constraints

- Yeni kullanıcıya görünen string hem `app/src/main/res/values/strings.xml` hem `app/src/main/res/values-tr/strings.xml` içinde eklenir.
- Background sensor/listener yalnız etkin rule ihtiyaç duyduğunda register edilir; ihtiyaç kalmayınca unregister edilir.
- Privileged action capability kontrolü, kullanıcı açıklaması ve mümkünse state readback içerir.
- Phone number, webhook URL/header/body, credential veya raw sensitive error log, history veya UI'ya yazılmaz.

## Releases

- `app/build.gradle.kts` sürümü ile tag `vX.Y.Z` eşleşir.
- Signed local release için `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` gerekir. Unsigned APK publish edilmez.
- GitHub Release notları: Sabit body yapısı kullanılır; release title body içinde tekrar edilmez. Sıra: kısa English özet, `---`, emoji section başlıklı değişiklikler, `## 🧪 Verification`, `## 📥 Installation`, `### SHA-256 Checksum`, `## 📱 Compatibility`, `---`, sonra en sonda `## 🇹🇷 Türkçe Özet`. Her release APK asset checksum değeri body’deki code block ile aynı olmalıdır. Release Markdown ve GitHub API güncellemelerinde UTF-8 korunur; PowerShell `-f` ile Unicode body/title yazılmaz, UTF-8 JSON payload kullanılır.
- Yeni GitHub Release oluşturmadan veya release notlarını güncellemeden önce mevcut release notları okunur; sabit yapı, ton, asset adları ve checksum formatı önceki release’lerle karşılaştırılır.
- User-visible feature, permission veya backup davranışı değişirse `README.md`, `README.tr.md`, `docs/STATUS.md` ve gerektiğinde `docs/IMPLEMENTATION.md` güncellenir. GitHub Pages kaynağı `docs/index.html`; release download URL, sürüm ve privacy/network claimleri release öncesi doğrulanır.

This major release adds OR-combined multi-trigger rules, nested AND / OR / NOT conditions, and an expanded preset library, together with the supply-chain and rule-safety hardening collected since 1.1.0.

---

## 🔀 Multi-Trigger Rules

- A rule can now combine up to five independently configured triggers with OR semantics: add, remove, and reorder each source in Create/Edit. All sources share the rule cooldown and run the same ordered action list.
- History records the actual matched source and one-based source index, so two triggers on one rule stay distinguishable.
- Existing single-trigger JSON is unchanged. Normal export/share and encrypted backup/restore preserve multi-trigger lists, and listeners reconcile from all enabled definitions across restart and disabled-rule toggles.

## 🧩 Nested AND / OR / NOT Logic

- `Automation.conditionTree` adds recursive condition groups with a per-group combinator, group negation, explicit empty-group semantics, and a 4-level depth guard. Rules without a tree keep the flat-AND behavior and the exact legacy shape.
- The editor keeps flat rules flat: one "Add condition" row, a Match AND/OR selector, and a separate "Nested groups" entry. Inside a group, the header collapses to an "All of / Any of" menu plus NOT, unavailable add actions are hidden, and single-child groups left by older drafts flatten on open.
- Import rejects trees deeper than 4 group levels, wider than 32 leaves, or containing empty groups before any store mutation; legacy payloads still import unchanged.

## ⚡ Presets & Editor Polish

- Five new presets exercise the new logic: Morning Wake Up, Night Quiet, Morning Commute, Focus / Work Mode, and Night Screen Dimming. Preset cards now show the real trigger count (`+N`) and an AND/OR marker when a template ships a condition tree.
- Battery Saver Emergency now runs only while unplugged; below-15% charging no longer turns the phone into a low-power state that fights the charger.
- Shake strength is configured per rule (6–32 m/s² above gravity) with hysteresis re-arming, so one continuous shake fires once, and the flashlight preset toggles the torch instead of only turning it on.
- Rule, condition, and preset surfaces share a neutral, lower-noise visual language: bordered low surfaces, compact group headers, per-trigger accents, outlined secondary actions, and one filled primary action per card.

## 🛡️ Trust, Supply Chain & Reliability

- Privileged actions verify platform state instead of trusting exit codes: NFC and Battery Saver read the live adapter/setting back and fail honestly, and Lock Screen reports verified completion only when the display is actually non-interactive.
- Release verification now requires an exact tag/version match, the current `main` commit, a successful `Build & Test` run for that commit, prepared notes, verified APK identity and signature, and a clean signing environment. Signed APKs carry a GitHub build provenance attestation.
- Gradle dependency verification covers plugins, metadata, and the app dependency graph in strict mode; `google()` is content-filtered and every workflow action is pinned to a full commit SHA.
- Imports are validated before any DataStore mutation, and geofence coordinates are reused only while fresh (under 60 seconds) or refetched at dispatch.

## 🧪 Verification

- Required GitHub `Build & Test` completed successfully for exact release commit `{{RELEASE_COMMIT}}`.
- The release workflow re-runs resource contracts, debug unit tests, Android lint, debug APK assembly, and signed release APK assembly, then verifies package ID, version code/name, signer certificate digest, and SHA-256 before publishing.
- Local release gate passed: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease -PreleaseSigningRequired=false`, plus `python scripts/test_lint_resource_contracts.py` (EN/TR parity).
- Unit coverage includes multi-trigger normalization and payload cloning, condition-tree flattening and import rejection, preset structure, per-rule shake filtering, and torch-state resolution.
- Not verified on a physical device in this cycle: app + Wi-Fi multi-trigger smoke, engine restart and disabled-rule behavior, and hands-on nested-condition editing. The wireless ADB target stayed offline, so those checks remain open and are not claimed here.

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

Developed and tested primarily on Xiaomi HyperOS (Xiaomi 15T Pro). Android OEM background restrictions can differ. Background NFC discovery requires explicit confirmation; foreground ReaderMode scans remain automatic. Privileged system actions require active Shizuku permission. Upgrading from 1.x keeps existing rules, exports, and encrypted backups readable.

---

## 🇹🇷 Türkçe Özet

- Bir kural artık OR mantığıyla en fazla beş bağımsız tetikleyiciyi birleştirebilir; kaynaklar eklenip sıralanabilir, ortak cooldown ve aynı eylem listesi kullanılır. Geçmiş, gerçekleşen kaynağı ve sırasını kaydeder.
- Koşullar iç içe AND / OR / NOT gruplarıyla kurulabilir; grup başına birleştirici, NOT, boş grup anlamı ve 4 seviye derinlik sınırı vardır. Ağaç olmayan kurallar eski düz AND davranışını ve JSON biçimini korur.
- Koşul düzenleyici sadeleşti: düz kurallar düz kalır, grup başlıkları "Hepsi / Herhangi biri" menüsü + NOT'a indirgenir, tek çocuklu eski gruplar açılışta düzleşir.
- Beş yeni şablon eklendi: Sabah Uyanışı, Gece Sessizliği, Sabah Yola Çıkış, Odak / Çalışma Modu ve Gece Ekran Kısma. Şablon kartları gerçek tetikleyici sayısını (`+N`) ve AND/OR işaretini gösterir.
- Acil Pil Tasarrufu artık yalnızca şarjda değilken çalışır; sallama gücü kural başına ayarlanır ve fener şablonu ışığı aç/kapat yapar.
- Ayrıcalıklı eylemler komut çıkış koduna güvenmek yerine platform durumunu doğrular; sürüm hattı APK kimliği, imzası, SHA-256'sı ve build provenance attestation'ı doğrulanmadan yayın yapmaz.
- Bu sürümde fiziksel cihaz smoke testi yapılamadı: kablosuz ADB hedefi çevrimdışı kaldığı için uygulama + Wi-Fi çoklu tetikleyici, motor yeniden başlatma, devre dışı kural ve elle iç içe koşul düzenleme kontrolleri açık kaldı; bunlar doğrulanmış gibi sunulmuyor.
- Kurulumdan önce `{{APK_NAME}}` dosyasının SHA-256 değerini doğrulayın.

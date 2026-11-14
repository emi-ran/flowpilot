# FlowPilot — Dynamic TTS & General Template Variables Plan

Status: implemented (historical design record; current behavior is tracked in `docs/STATUS.md`)  
Base: `main` @ `b90956b` (v2.1.0)  
Date: 2026-11-03  
Owner: main/integration agent  
Audience: implementation subagents

> The baseline sections below describe the state before implementation. They are kept for design history, not as current documentation.

---

## 0. TL;DR

FlowPilot currently requires a manually generated TTS WAV before a rule can be saved, and variables only work in webhook/SMS actions. Target design:

1. TTS is synthesized on demand at runtime or preview; no separate "Generate" step.
2. Templates use `${...}` in most user-facing action fields.
3. Variables are populated from the actual trigger payload (notification, SMS, call, Wi-Fi, etc.).
4. TTS cache is content-addressed, reused across rules, and cleaned by TTL/LRU.
5. Export/import carries templates, not audio; first play synthesizes if needed.
6. Variable picker only shows variables that make sense for the rule's triggers.

Example target behavior:

```text
Rule: Notification received from WhatsApp, keyword "tts"
Action: Speak text
Template: ${notification.text|stripPrefix:"tts"|trim}

Incoming notification: "TTS bugün nasılsın"
Rendered: "bugün nasılsın"
Result: synthesized and spoken without pressing Generate.
```

---

## 1. Current state (evidence)

| Area | Current behavior | File |
|---|---|---|
| Action execution | `ActionExecutor.execute()` is synchronous. | `app/src/main/java/com/flowpilot/app/actions/ActionExecutor.kt` |
| TTS | Executor only plays an existing cache file. | `.../actions/TtsExecutor.kt` |
| TTS cache key | `SHA-256(ruleId|text|voice|rate)`, filename includes rule ID. | `.../actions/TtsManager.kt` |
| TTS UI | Separate Generate & Preview; save is blocked without matching cache. | `.../ui/components/TtsSettings.kt`, `CreateScreen.kt`, `DetailScreen.kt` |
| TTS import | `remapTts()` requires a local source cache with the old filename; otherwise audio is dropped and counted missing. | `.../data/AutomationRepository.kt` |
| Cache cleanup | Deletes unreferenced files on rule changes; no TTL or size limit. | `.../data/AutomationRepository.kt` |
| Variables | Renderer exists but only Webhook and SMS executors use it. | `.../actions/WebhookTemplateRenderer.kt`, `WebhookExecutor.kt`, `SmsExecutor.kt` |
| Context | Context has trigger/time/battery/Wi-Fi/SMS/location; no notification or call data. | `.../actions/WebhookTemplateRenderer.kt` |
| Notification event | Listener captures title/text; `text` and `bigText` are combined. Event payload is not passed to `executeAll()`. | `.../engine/FlowPilotNotificationListener.kt`, `AutomationEngine.kt` |
| MessagingStyle | `EXTRA_MESSAGES` / `MessagingStyle` is not parsed. | `.../engine/FlowPilotNotificationListener.kt` |
| SMS | Sender/body are passed to `executeAll()`; OTP is derived by renderer. | `.../engine/AutomationEngine.kt`, `SmsEventTracker.kt` |
| Phone state | `CallTransition` carries a transient number, but it is not passed to actions. | `.../engine/CallStateTracker.kt` |
| Privacy | `allowBackup="false"`; raw notification text is not persisted. | `app/src/main/AndroidManifest.xml` |

Confirmed constraints:

- `minSdk = 26`, so `java.nio.file.Files` and atomic move are available.
- Existing backups are JSON-based; TTS audio is not a portable asset today.
- Existing user rules must keep working.

---

## 2. Goals

1. Preview-only TTS: one button synthesizes and plays; runtime synthesizes when needed.
2. Dynamic text: TTS and other action fields can use trigger data.
3. Correct variable availability: no notification variables in a Wi-Fi-only rule.
4. Reusable cache: same text/voice/rate shares one file across rules.
5. Bounded storage: TTL + size cap + LRU.
6. Portable rules: export/import works without TTS audio; first play regenerates.
7. Backward compatibility: old rules, old cache files, old backups.
8. Privacy: no raw notification/SMS content in logs, history, or exports.

## 3. Non-goals

- Inbound HTTP server / direct ntfy push receiver.
- Reading notification content that Android/app hides.
- Cloud TTS or online voices.
- Audio encryption.
- Persistent notification/message history.
- Arbitrary code, shell commands, or script evaluation in templates.
- Changing unrelated action semantics.

---

## 4. Locked design decisions

These are defaults for implementation. Do not silently change them.

- **D1 — General context:** replace `WebhookTemplateContext` with `TemplateContext`; keep legacy variable names as aliases.
- **D2 — Central rendering:** render all supported fields in `ActionParametersFactory`; executors must not render a second time.
- **D3 — Async action path:** add `suspend fun executeAsync(...)` with a default implementation calling `execute(...)`; only TTS overrides it. Dispatcher, engine, and manual run use `executeAsync`.
- **D4 — Content-addressed TTS cache:** filename is derived from rendered text, voice, rate, and engine; not rule ID.
- **D5 — Cache policy:** default 7 days since last use; 50 MB cap; LRU eviction; atomic writes.
- **D6 — No audio requirement:** rule save/import does not require a pre-generated file.
- **D7 — Trigger-aware UI:** variable picker filters by the union of configured triggers; per-trigger variables are marked when they only fill on that trigger.
- **D8 — Strict TTS rendering:** unknown variables, malformed templates, or empty rendered text fail the TTS action with a clear result.
- **D9 — One-pass rendering:** replacement values are never recursively rendered. `$${` escapes a literal `${`.
- **D10 — Regex is phase 3:** implement built-in safe filters first. Regex support must have pattern/input limits and import validation. Do not add a new dependency without integration-owner approval.
- **D11 — Notification text fields:** keep primary `text` and `bigText` separate for variables; do not expose the combined matching text as `notification.text`.
- **D12 — Privacy:** dynamic audio is sensitive; keep it app-private, exclude from backup, and never log rendered text.

---

## 5. Target architecture

```text
Trigger source (notification / SMS / call / Wi-Fi / …)
        │
        ▼
TriggerPayload (transient, in memory)
        │
        ▼
TemplateContext (global + event values)
        │
        ▼
ActionParametersFactory (one-pass render of supported fields)
        │
        ▼
ActionDispatcher.executeAsync()
        │
        ├── normal executors: execute()
        └── TtsExecutor.executeAsync()
                │
                ▼
        TtsManager.ensureSpeech(text, voice, rate)
                │
                ├── cache hit → touch + play
                └── cache miss → synthesize temp → atomic move → play
```

---

## 6. Template engine

### 6.1 New files

Create:

- `app/src/main/java/com/flowpilot/app/actions/TemplateContext.kt`
- `app/src/main/java/com/flowpilot/app/actions/TemplateRenderer.kt`
- `app/src/main/java/com/flowpilot/app/actions/TemplateParser.kt` (if parser is separate)
- `app/src/main/java/com/flowpilot/app/actions/ActionParametersFactory.kt`
- Tests under `app/src/test/java/com/flowpilot/app/actions/`

Rename/remove:

- `WebhookTemplateContext` → `TemplateContext`
- `WebhookTemplateRenderer` → `TemplateRenderer`
- `ActionParameters.webhookTemplateContext` → removed (or kept only as a temporary compatibility field; prefer clean cutover)

### 6.2 Context shape

Recommended:

```kotlin
data class TemplateContext(
    val trigger: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val timeProvider: () -> String = { ... },
    val values: Map<String, String> = emptyMap(),
    val locationLat: Double? = null,
    val locationLng: Double? = null,
)
```

Compatibility aliases must continue to resolve:

- `${batteryPercent}` and `${battery.percent}`
- `${smsSender}` and `${sms.sender}`
- `${smsBody}` and `${sms.body}`
- `${smsOtp}` and `${sms.otp}`
- `${locationLat}` / `${location.lat}`, `${locationLng}` / `${location.lng}`
- `${trigger.index}`

Do not expose nullable values as `"null"`; use empty string.

### 6.3 Syntax

```text
${name}
${name|filter}
${name|filter:"argument"|filter2}
```

Examples:

```text
${notification.title}
${notification.text}
${notification.text|stripPrefix:"tts"|trim}
${notification.text|drop:4|trim}
${notification.text|truncate:300}
${notification.text|default:"Yeni bildirim"}
${notification.text|jsonescape}
${notification.text|urlencode}
${notification.text|regex:"(?i)^.*?\\btts\\b\\s*(.*)$"|group:1|trim}
```

### 6.4 Parser requirements

The existing placeholder regex is insufficient. It breaks on:

- Regex quantifiers containing `}` such as `\d{4,6}`.
- Alternation containing `|` such as `^a|b$`.
- Quoted arguments containing spaces or braces.

Implement a quote/escape-aware scanner:

1. Find `${`.
2. Scan until the matching `}` outside quotes and outside escapes.
3. Split the inner expression on `|` only outside quotes.
4. Parse filters as `name` or `name:argument`.
5. Support quoted arguments with `\"`, `\\`, and literal braces/pipes.
6. Reject malformed expressions with a typed parse error; do not partially render.

Rules:

- Maximum template length: 4000 characters.
- Maximum filters per expression: 8.
- Maximum variable name length: 64.
- One-pass only; no recursive expansion.
- `$${` produces literal `${`.

### 6.5 Render modes

```kotlin
enum class RenderMode { LENIENT, STRICT }

data class RenderResult(
    val text: String,
    val unknownVariables: Set<String>,
    val parseErrors: List<String>,
)
```

- `LENIENT`: unknown variables remain unchanged (backward compatible for webhook/SMS).
- `STRICT`: unknown variables or parse errors fail TTS.
- TTS always uses `STRICT`.
- Notification/SMS/webhook/other actions use `LENIENT` unless explicitly documented.

### 6.6 Filters (phase 1)

| Filter | Behavior |
|---|---|
| `trim` | Remove leading/trailing whitespace. |
| `stripPrefix:"text"` | If value starts with text (case-insensitive), remove it and leading whitespace. |
| `drop:N` | Drop first N Unicode code points. |
| `truncate:N` | Keep at most N Unicode code points. |
| `lower` / `upper` | Locale-independent case conversion. |
| `default:"text"` | Replace empty value with fallback. |
| `urlencode` | Percent-encode for URLs; spaces as `%20`. |
| `jsonescape` | Escape quotes, backslashes, control chars for JSON string content. |

Do not implement `replace`, `split`, or arbitrary transformations in phase 1 unless needed by a test.

### 6.7 Filters (phase 3)

- `regex:"pattern"` — first match; empty if none.
- `group:N` — group from the previous regex match; empty if unavailable.
- `replace:"from":"to"` — optional; only if parser can support two quoted args cleanly.

Regex safety:

- Pattern length ≤ 200.
- Input length ≤ 2048.
- At most 2 regex filters per template.
- Cache compiled patterns.
- Import validation must reject malformed patterns.
- ReDoS risk remains with `java.util.regex`; if imported backups are treated as untrusted, use RE2J or explicitly document/guard the residual risk. Do not add a dependency without approval.

---

## 7. Variable catalog and availability

### 7.1 Global variables

Available for every trigger:

| Variable | Value |
|---|---|
| `${trigger}` | Trigger event name, e.g. `NOTIFICATION_RECEIVED` |
| `${trigger.index}` | 1-based matched trigger source index |
| `${time}` | ISO-8601 event time |
| `${timestamp}` | Epoch milliseconds |
| `${batteryPercent}`, `${battery.percent}` | 0–100 |
| `${isCharging}` | `true` / `false` |
| `${wifiSsid}` | Current SSID if known |
| `${rule.name}` | Rule display name |

### 7.2 Event variables

| Variable | Filled when | Source |
|---|---|---|
| `${notification.title}` | `NOTIFICATION_RECEIVED` | `EXTRA_TITLE` / MessagingStyle |
| `${notification.text}` | `NOTIFICATION_RECEIVED` | primary text / latest MessagingStyle message |
| `${notification.bigText}` | `NOTIFICATION_RECEIVED` | `EXTRA_BIG_TEXT` |
| `${notification.package}` | `NOTIFICATION_RECEIVED` | notification package |
| `${notification.app}` | `NOTIFICATION_RECEIVED` | app label, fallback package |
| `${sms.sender}` / `${smsSender}` | `SMS_RECEIVED` | sender |
| `${sms.body}` / `${smsBody}` | `SMS_RECEIVED` | message body |
| `${sms.otp}` / `${smsOtp}` | `SMS_RECEIVED` | first 4–8 digit sequence |
| `${call.number}` | call triggers | transient phone number, may be empty on Android 12+ |
| `${call.state}` | call triggers | `RINGING`, `ANSWERED`, `OUTGOING`, `ENDED` |
| `${app.package}` | app triggers | foreground/closed package |
| `${app.name}` | app triggers | resolved app label, fallback package |
| `${wifi.ssid}` | Wi-Fi triggers | transition SSID |
| `${bluetooth.name}` | Bluetooth triggers | cached name |
| `${bluetooth.address}` | Bluetooth triggers | device address |
| `${nfc.tag}` | `NFC_TAG_SCANNED` | normalized tag ID |
| `${shake.magnitude}` | `DEVICE_SHAKE` | m/s² above gravity |
| `${light.lux}` | `LIGHT_BELOW` / `LIGHT_ABOVE` | lux |
| `${geofence.name}` | geofence triggers | configured name |
| `${location.lat}` / `${location.lng}` | location available | coordinates |
| `${location.maps_url}` | location available | Google Maps URL |
| `${screen.on}` | screen triggers | `true` / `false` |
| `${charger.connected}` | charger triggers | `true` / `false` |

Do not add clipboard, contacts, full notification history, or other apps' private data.

### 7.3 Availability rules

- Build availability from `rule.triggerViews()` (supports `triggerDefinitions` and legacy `triggerEvent`).
- A rule with `NOTIFICATION_RECEIVED` + `WIFI_CONNECTED` shows both sets, but each set only fills when that trigger actually runs.
- A Wi-Fi-only rule must not show notification variables in the picker.
- Global variables are always shown.
- If a variable has no value at runtime, render it as empty (strict TTS then fails if the whole result is empty).
- UI must label multi-trigger-only variables: “only fills when X triggers”.

Suggested availability groups:

| Trigger group | Variables |
|---|---|
| Always | global |
| Notification | notification.* |
| SMS | sms.* |
| Call | call.* |
| App | app.* |
| Wi-Fi | wifi.* |
| Bluetooth | bluetooth.* |
| NFC | nfc.* |
| Motion | shake.* |
| Light | light.* |
| Location | geofence.*, location.* |
| Screen | screen.* |
| Charger/Battery | charger.*, battery.* |

---

## 8. Event payload plumbing

### 8.1 New transient event data

Extend notification event without persisting:

```kotlin
data class TransientNotificationEvent(
    val packageName: String,
    val postTime: Long,
    val key: String,
    val title: String,
    val text: String,
    val bigText: String = "",
    val appLabel: String = "",
)
```

Optional later: parse `Notification.EXTRA_MESSAGES` / `MessagingStyle`:

- Prefer latest message text for `notification.text`.
- Keep `bigText` separate.
- Never persist raw messages.
- Keep existing keyword matching behavior using title/text; add a helper if combined matching is needed.

### 8.2 Engine changes

Change `AutomationEngine.executeAll()` to accept event data, for example:

```kotlin
executeAll(
    rules = matches,
    liveState = liveState,
    eventContext = TemplateContext(...),
    eventId = ...,
)
```

Call sites to update:

- `pollNotificationEvents` → notification title/text/bigText/package/app
- `pollSmsEvents` → sender/body/OTP/time
- `pollCallTransitions` → call number/state
- `pollWifiTransitions` → SSID
- `pollBluetoothTransitions` → name/address
- `pollNfcTagEvents` → tag
- `pollShakeEvents` → magnitude
- `pollLightTransitions` → lux
- `pollGeofenceEvents` → name/coords
- app open/close → package/name
- charger/battery/screen/schedule → corresponding values
- manual run → `trigger = "MANUAL"`, event variables empty

Do not put event values in persistent rule fields.

---

## 9. Action parameter factory

Create `ActionParametersFactory` and use it from both:

- `AutomationEngine.actionParameters(...)`
- `AppViewModel.runRuleNow(...)`

Responsibilities:

1. Start from rule fields.
2. Render supported string fields with `TemplateRenderer`.
3. Apply `STRICT` to `ttsText`; `LENIENT` to other fields.
4. Preserve null/empty semantics.
5. Return `ActionParameters` with no template context.

Supported fields:

| Action / field | Template support |
|---|---|
| TTS `ttsText` | Yes, strict |
| Notification title/body | Yes |
| Webhook headers/body | Yes |
| Webhook URL | Phase 2 only, with URL validation and `urlencode` requirement |
| SMS recipient/message | Yes |
| OPEN_URL `url` | Yes |
| Alarm/timer message | Yes |
| Phone number | Yes, with privacy warning |
| Launch package, force-stop package, sound URI | No |
| Numeric fields | No |

Remove rendering from `WebhookExecutor` and `SmsExecutor` to avoid double rendering. Update their tests to pass already-rendered parameters or use the factory.

Security validation must happen **after** rendering:

- Webhook headers: reject control characters / forbidden names.
- Webhook URL: require HTTPS, validate host after render.
- SMS recipient: validate after render.
- OPEN_URL: allow only http/https.
- Never render package names or file paths.

---

## 10. TTS runtime

### 10.1 Async action path

Add to `ActionExecutor`:

```kotlin
suspend fun executeAsync(action: ActionType, parameters: ActionParameters): ActionResult =
    execute(action, parameters)
```

Add to `ActionDispatcher`:

```kotlin
suspend fun executeAsync(action: ActionType, parameters: ActionParameters): ActionResult
```

Use `executeAsync` in:

- `AutomationEngine`
- `AppViewModel.runRuleNow`

Keep `execute()` for existing sync executors and existing tests. `TtsExecutor.execute()` may remain cache-only; `executeAsync()` is the dynamic path.

### 10.2 TTS manager API

Refactor toward:

```kotlin
interface TtsEngine {
    suspend fun availableVoices(): List<TtsVoiceOption>
    suspend fun synthesize(text: String, voiceName: String, rate: Float, target: File): Boolean
}

class TtsManager(
    context: Context,
    private val engine: TtsEngine = AndroidTtsEngine(context),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun ensureSpeech(text: String, voiceName: String, rate: Float): File?
    fun getCacheFile(fileName: String): File?
    fun touch(file: File)
    fun cleanup()
}
```

`AndroidTtsEngine` wraps `TextToSpeech`; tests use a fake. This makes runtime synthesis testable without Android TTS.

### 10.3 Execution flow

```text
TtsExecutor.executeAsync(action, params):
  if action != SPEAK_TEXT -> failure
  if params.ttsText is not blank:
      text = normalize(params.ttsText)
      if text blank -> failure "empty rendered text"
      file = ttsManager.ensureSpeech(text, voice, rate)
      if file == null -> failure "synthesis failed"
      play(file)
  else if params.ttsAudioFileName is valid + exists:
      play(legacy file)
  else:
      failure "missing text or audio"
```

Do not use `runBlocking`. Do not synthesize on the main thread.

### 10.4 Cache key and filename

```text
key = SHA-256(
  normalizedText + "\u0000" +
  voiceName + "\u0000" +
  rate + "\u0000" +
  enginePackageName
)
filename = "tts_" + key.take(32) + ".wav"
```

- `normalizedText`: trim and collapse repeated whitespace only; do not remove punctuation.
- Include engine package to avoid wrong voice after engine changes.
- Keep legacy filename recognition: `tts_<ruleId>_<16hex>.wav`.
- Content-addressed files are shared across rules; cloning a rule does not copy audio.

### 10.5 Atomic write

```text
target = tts_cache/tts_<hash>.wav
temp   = tts_cache/tts_<hash>.wav.tmp
synthesize(temp)
if temp valid:
    Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)
    verify target exists and length > 0
else:
    delete temp; failure
```

Never delete a valid target before a successful move. Check the move result.

### 10.6 Timeouts and limits

- TTS init timeout: keep 6s; retry once if cold start fails? Optional.
- Synthesis timeout: `max(15s, 1s per 100 characters)` capped at 60s.
- Maximum rendered text: 2000 characters. Longer text fails with a clear error; user can add `truncate:...`.
- Serialize synthesis with a mutex to avoid duplicate concurrent synthesis of the same key.

### 10.7 Cache lifecycle

- Directory: `filesDir/tts_cache`.
- On cache hit: `setLastModified(now)` before/after playback start.
- TTL: 7 days since last modification/use.
- Size cap: 50 MB.
- Eviction: oldest `lastModified` first.
- Cleanup triggers:
  - `TtsManager` init (background)
  - after every N successful syntheses (e.g. 10)
  - Settings “Clear TTS cache”
- Skip:
  - `.tmp` files
  - files modified in the last 10 minutes
  - files currently being played (in-memory active set)
- Old filenames and new filenames both count toward size.

### 10.8 Backward compatibility

- If `ttsText` is nonblank, dynamic path is authoritative.
- If dynamic synth fails and a legacy file for the same rule/text/voice/rate exists, optionally play it; do not play a stale file if text changed.
- Old rules with blank `ttsText` and a valid file continue to play.
- Old cache files may be migrated to content-addressed names when used, or left for LRU cleanup.

---

## 11. UI / UX

### 11.1 TTS settings

Replace:

- `Generate & Preview`
- separate `Preview`
- cache-required save message

With:

- `Preview`: synthesize current rendered sample and play.
- `Stop`: stop preview.
- Status: “Cached” / “Will synthesize on first play”.
- Error text localized.

Remove/repurpose strings:

- `tts_btn_generate`, `tts_btn_regenerate`, `tts_cached_ready`, `tts_needs_synthesis`
- keep `tts_btn_preview`, `tts_btn_stop`, but update translations.

### 11.2 Variable picker

Create a shared `VariablePickerDialog` / `VariableCatalog`:

- Grouped by trigger category.
- Only relevant groups shown.
- Multi-trigger rules show union; add badge for event-only variables.
- Clicking inserts token; phase 1 may append at end, phase 2 should use `TextFieldValue` and insert at cursor.
- Show filter examples in the dialog.

### 11.3 Preview sample data

If template contains variables, preview must use sample values:

```text
notification.title = "Alice"
notification.text  = "TTS bugün nasılsın"
sms.sender         = "+90 555 000 00 00"
sms.body           = "Kod: 123456"
wifi.ssid          = "Home_5G"
```

- Show “Using sample data”.
- Optionally allow editing sample fields.
- WiFi-only rule must not show notification sample fields.
- Preview must not persist sample values.

### 11.4 Manual run

- Use `trigger = "MANUAL"`.
- Event variables are empty.
- If TTS renders empty, show a clear error and suggest Preview sample data or adding a `default` filter.
- Do not silently speak `${notification.text}`.

---

## 12. Persistence, backup, import

### 12.1 Rule model

- `ttsText` stores the template.
- `ttsAudioFileName` becomes optional/legacy.
- Do not add dynamic audio to backup/export.
- Consider `@EncodeDefault(NEVER)` for empty `ttsAudioFileName`; optional.

### 12.2 Save validation

For `SPEAK_TEXT`:

- Require nonblank `ttsText` **or** a valid legacy cache file.
- Do not require exact filename match.
- Validate template syntax.
- Strict TTS templates must have no unknown variables.
- Speech rate 0.5–2.0.

### 12.3 Import/export

- Export carries templates, no audio.
- Import must not fail when audio is missing.
- Remove `missingTtsCount` from user-facing behavior, or keep it as an informational count for legacy files only.
- `ImportValidator`:
  - speech rate 0.5–2.0
  - `ttsAudioFileName` optional and safe when present
  - `ttsText` length limit
  - template syntax valid
  - unknown TTS variables rejected
- `AutomationRepository.remapTts`:
  - dynamic rules: leave `ttsAudioFileName` empty; no file copy
  - legacy rules: if file exists, optionally copy; if missing, clear reference
  - never throw solely because audio is missing
- `clone`:
  - do not copy TTS audio for dynamic rules
  - legacy copy only if file exists
- Old backups remain readable.

---

## 13. Security and privacy

- Raw notification/SMS/call content must never be logged, written to history, or exported.
- Rendered values must not be logged.
- TTS audio is sensitive:
  - app-private `filesDir`
  - `allowBackup=false` already
  - TTL/LRU
  - clear-cache control
- Webhook URL with notification/SMS variables can leak content into server logs:
  - phase 2 only
  - require `urlencode`
  - validate HTTPS after render
  - show warning in UI
- JSON body: require `jsonescape` for inserted text; otherwise quotes/newlines break JSON.
- Header values: render then validate control characters; existing WebhookExecutor validation must run after render.
- Phone number variables: transient, masked in logs/history; UI warning if used in webhook.
- Regex: limits and import validation; ReDoS residual risk documented.
- No recursive rendering; no arbitrary code.

---

## 14. Testing plan

### 14.1 Unit tests (JVM/Robolectric)

Template:
- Existing placeholders still work.
- Alias compatibility.
- Unknown variable lenient vs strict.
- `$${` escape.
- Filter parsing with quotes, braces, pipes, backslashes.
- `stripPrefix`, `drop`, `truncate`, `default`, `urlencode`, `jsonescape`.
- Regex quantifiers do not break parser.
- No recursive expansion.
- Limits.

Variables:
- Trigger availability matrix.
- Multi-trigger union and per-event empty values.
- Notification title/text/bigText/package/app.
- SMS sender/body/OTP.
- Call number/state.
- WiFi/NFC/shake/light/geofence/app.
- Manual run empty values.

Action factory:
- TTS strict rendering.
- Notification/webhook/SMS fields rendered once.
- No double render when value contains `${...}`.
- URL/header validation after render.
- Unsupported fields remain untouched.

TTS:
- Cache key deterministic; voice/rate/engine changes produce different keys.
- Cache hit does not synthesize.
- Cache miss synthesizes once.
- Atomic move failure returns failure and does not delete valid target.
- TTL deletes old file; LRU deletes oldest; size cap.
- Legacy filename plays/migrates.
- Empty rendered text fails.
- Synthesis timeout/failure.
- Concurrent same-text synthesis not duplicated.

Import/export:
- Dynamic rule imports with no audio.
- Legacy rule with missing audio imports and clears reference.
- Old backup compatibility.
- Rate bounds and template validation.

### 14.2 Device smoke (main agent)

- WhatsApp/ntfy notification with “TTS bugün nasılsın” → speaks “bugün nasılsın”.
- Second identical notification uses cache (no synth delay; cache mtime changes).
- SMS OTP rule uses `${sms.otp}`.
- Wi-Fi-only rule: variable picker has no notification variables.
- Notification rule with hidden content: meaningful error, not `${...}`.
- Export, clear app data, import: first TTS play synthesizes; no missing-audio error.
- Cache cleanup: old files removed; active playback not interrupted.
- No raw notification text in logcat/history.

### 14.3 Build gate

Subagents must not run Gradle. Main/integration agent runs:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-17'
$env:ANDROID_HOME='C:/Users/emiran/AppData/Local/Android/Sdk'
./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false
python scripts/test_lint_resource_contracts.py
```

Device install/launch: main agent only, per project AGENTS.md.

---

## 15. Work breakdown for subagents

### Phase 0 — Contracts (integration owner, before parallel work)

Owner: main/integration agent.

Deliverables:

1. Add `TemplateContext`, `TemplateRenderer` API signatures (skeleton allowed).
2. Add `ActionExecutor.executeAsync` default and `ActionDispatcher.executeAsync`.
3. Add `ActionParametersFactory` skeleton.
4. Add `TtsEngine`/`TtsManager.ensureSpeech` signatures.
5. Build once to confirm the skeleton compiles.

Do not let parallel agents invent competing interfaces.

### Phase 1 — Parallel slices

#### Slice A — Template engine

Owner: one agent.  
Write scope:

- `actions/TemplateContext.kt`
- `actions/TemplateRenderer.kt`
- `actions/TemplateParser.kt`
- `actions/WebhookTemplateRenderer.kt` (compatibility wrapper or removal)
- `actions/WebhookTemplateContext.kt` (compatibility wrapper or removal)
- `app/src/test/java/com/flowpilot/app/actions/Template*Test.kt`
- update `WebhookTemplateRendererTest.kt` / `MultipleTriggersTest.kt` only as needed

Deliverables:

- Parser + filters + render modes.
- Legacy aliases.
- No dependency on engine/TTS.
- Unit tests.

Do not touch `AutomationEngine`, `TtsManager`, UI, repository.

#### Slice B — TTS engine/cache

Owner: one agent.  
Write scope:

- `actions/TtsManager.kt`
- `actions/TtsEngine.kt` (new)
- `actions/AndroidTtsEngine.kt` (new)
- `actions/TtsExecutor.kt`
- `actions/ActionDispatcher.kt` (only TTS async wiring)
- `app/src/test/java/com/flowpilot/app/actions/Tts*Test.kt`

Deliverables:

- `ensureSpeech`, content-addressed cache, atomic write, TTL/LRU, legacy fallback.
- `executeAsync` dynamic path.
- Fake engine tests.
- No UI/repository changes.

Do not change `ActionParameters` or `AutomationEngine`.

#### Slice C — Event payload + action factory

Owner: one agent.  
Write scope:

- `actions/ActionParameters.kt`
- `actions/ActionParametersFactory.kt` (if not already created in Phase 0)
- `engine/AutomationEngine.kt`
- `engine/FlowPilotNotificationListener.kt`
- `engine/CallStateTracker.kt` (only if context needs it)
- `engine/SmsEventTracker.kt` (only if timestamp needed)
- `engine/RuleEvaluator.kt` (only if notification payload shape changes)
- `ui/AppViewModel.kt` (manual run factory)
- tests for engine/factory/notification payload

Depends on Slice A interfaces.  
Deliverables:

- `executeAll` event context.
- Notification/SMS/call/WiFi/NFC/shake/light/geofence/app payload.
- Central `ActionParametersFactory`.
- Remove executor-level render from Webhook/SMS? Coordinate with Slice D; one owner must edit those executors.

Do not edit TTS cache internals or UI screens.

#### Slice D — UI, strings, persistence

Owner: one agent.  
Write scope:

- `ui/components/TtsSettings.kt`
- `ui/screens/CreateScreen.kt`
- `ui/screens/DetailScreen.kt`
- `ui/components/ActionCardItem.kt` (variable picker entry points)
- `ui/components/VariablePicker.kt` (new)
- `data/AutomationRepository.kt`
- `data/backup/ImportValidator.kt`
- `res/values/strings.xml`
- `res/values-tr/strings.xml`
- `docs/STATUS.md`, `docs/IMPLEMENTATION.md`, `CHANGELOG.md` (final integration owner may handle)

Depends on Slice B/C interfaces.  
Deliverables:

- Preview-only TTS UI.
- Trigger-aware variable picker.
- Save validation relaxed.
- Import/export no audio requirement.
- EN/TR parity.

Do not edit engine/cache internals.

### Phase 2 — Integration

Integration owner:

1. Merge interfaces and slices.
2. Resolve shared-file conflicts.
3. Update docs/changelog.
4. Run full gate.
5. Device smoke.
6. Commit only when asked.

### Shared-file ownership

| File | Owner |
|---|---|
| `ActionExecutor.kt` | Phase 0 / integration |
| `ActionDispatcher.kt` | Phase 0 + Slice B |
| `ActionParameters.kt` | Slice C |
| `TemplateRenderer.kt` | Slice A |
| `TtsManager.kt`, `TtsExecutor.kt` | Slice B |
| `AutomationEngine.kt` | Slice C |
| `AppViewModel.kt` | Slice C |
| `WebhookExecutor.kt`, `SmsExecutor.kt` | Slice C (rendering removal) |
| `TtsSettings.kt`, Create/Detail | Slice D |
| `AutomationRepository.kt`, `ImportValidator.kt` | Slice D |
| strings | Slice D |
| docs/changelog | integration owner |

If another slice must touch a shared file, coordinate through the integration owner; do not edit concurrently.

---

## 16. Acceptance criteria

The feature is done only when:

1. A user can create a notification rule with TTS text `${notification.text|stripPrefix:"tts"|trim}` and save without generating audio.
2. When WhatsApp/ntfy posts “TTS bugün nasılsın”, FlowPilot speaks “bugün nasılsın”.
3. The same rendered text uses cache on the next occurrence.
4. A Wi-Fi-only rule does not offer notification variables in the picker.
5. A multi-trigger rule marks variables that only fill on a specific trigger.
6. Export/import of a dynamic TTS rule works without audio; first play synthesizes.
7. Legacy TTS rules/cache still play.
8. Cache TTL/LRU and manual clear work; active playback is not deleted.
9. No raw notification/SMS text appears in logs, history, or backups.
10. `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease -PreleaseSigningRequired=false`, resource contract tests pass on JDK 17.
11. Device smoke covers notification, SMS, cache, import/export, and variable availability.

---

## 17. Risks and open decisions

| Risk / decision | Default |
|---|---|
| Async interface ripple | Default `executeAsync` method; only TTS overrides. |
| Regex ReDoS | Phase 3; limits + import validation; RE2J only with approval. |
| URL variables leak | Phase 2; `urlencode` + HTTPS validation + warning. |
| Cache TTL/size | 7 days / 50 MB; constants first, settings later. |
| Manual run with variables | `MANUAL` context; empty values; clear error; preview sample data. |
| WhatsApp hidden content | Cannot read; show meaningful failure. |
| ntfy direct push | Not supported; only ntfy Android notification via listener. |
| Multi-trigger variable ambiguity | Union in UI; per-event empty at runtime; badge. |
| TTS engine unavailable | Fail clearly; no network/voice fallback. |
| Old static cache | Optional legacy fallback; LRU cleanup. |
| Imported old backup | Accept; clear missing audio reference; synthesize later. |

---

## 18. Suggested PR split

1. **PR 1 — Template foundation + dynamic TTS core**
   - Parser/renderer/filters.
   - Notification/SMS payload + factory.
   - `executeAsync` + runtime TTS + content cache.
   - Save gate removal.
2. **PR 2 — Cache lifecycle + UI + import/export**
   - TTL/LRU/atomic hardening.
   - Preview + variable picker + sample data.
   - Import/export and legacy migration.
3. **PR 3 — Broad variables + regex + docs**
   - Call/app/WiFi/NFC/shake/light/geofence variables.
   - MessagingStyle parsing.
   - Regex with limits or approved regex engine.
   - Docs/strings/device smoke.

Do not mix PR 3 into PR 1; it increases review and regression risk.

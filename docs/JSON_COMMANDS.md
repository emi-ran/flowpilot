# JSON commands (unreleased)

The configured source template is limited to 4,000 characters by `TemplateRenderer`; the rendered JSON has the separate 16,384 UTF-16-unit parser limit below. The configured source is encrypted with Android Keystore. Normal single-rule and full exports clear it, so re-enter the source after normal import; encrypted exports preserve it.

## Installing the agent skill

Copy the complete `docs/skills/flowpilot-json-command` directory into a skills directory supported by your agent, for example `~/.agents/skills/flowpilot-json-command/`, then reload the agent according to its instructions. The directory must contain `SKILL.md`. Consult the full contract here when using the skill in this repository; a standalone copy includes the essential version-1 rules.

The **Run JSON command** action renders its configured source template once, then parses the entire resulting text as one JSON object. Surrounding JSON whitespace is accepted; prefixes, suffixes, Markdown fences, root arrays, duplicate keys, nested objects beyond `params`, and trailing text are rejected. It does not extract a JSON fragment. A trigger can supply `${sms.body}`, `${notification.text}`, or `${notification.bigText}`; choose the field actually containing the complete object. A manual run does not have an incoming notification or SMS payload. Filters such as `trim` and `stripPrefix` can prepare a source before parsing; values inside the parsed command are not rendered again as templates.

## Envelope and limits

```json
{"version":1,"id":"request-42","action":"TORCH_ON","params":{}}
```

Only `version`, `id`, `action`, and `params` are allowed at the root. `version` is required and must be the JSON number written exactly `1` (not `1.0`, `1e0`, or a string). `id` is optional: if present it is a nonblank string of at most 128 UTF-16 code units. `action` is a required case-sensitive string; `params` is a required object. Input is limited to 16,384 UTF-16 code units before parsing and each string value in `params` to 4,096 UTF-16 code units. Unknown keys, types, actions, versions, missing fields, malformed JSON, and duplicate keys fail rather than being ignored. Arrays are accepted only for alarm `days`; arbitrary nested objects are not accepted. There is no arbitrary intent or shell action. `EXECUTE_JSON_COMMAND` is explicitly rejected to prevent recursive JSON execution.

## Actions and parameters

All `ActionType` actions except `EXECUTE_JSON_COMMAND` are supported. Names and enum values are case-sensitive; extra parameters are rejected. Strings cannot be null and are bounded by the global 4,096 UTF-16-unit limit unless a smaller limit is specified. Integers must be JSON integer literals, not strings, fractions or exponent notation, and must fit signed 64-bit range. Ranges are inclusive. Optional fields use the stated defaults only when omitted; a present value must have the correct type.

The following actions require exactly `params: {}`:

| Category | Actions |
| --- | --- |
| Bluetooth, NFC | `BLUETOOTH_ON`, `BLUETOOTH_OFF`, `NFC_ON`, `NFC_OFF` |
| Power and appearance | `BATTERY_SAVER_ON`, `BATTERY_SAVER_OFF`, `DARK_THEME_ON`, `DARK_THEME_OFF`, `AUTO_ROTATE_ON`, `AUTO_ROTATE_OFF`, `AUTO_BRIGHTNESS_ON`, `AUTO_BRIGHTNESS_OFF` |
| Sound and interruption | `DND_ON`, `DND_OFF`, `SOUND_PROFILE_NORMAL`, `SOUND_PROFILE_VIBRATE`, `SOUND_PROFILE_SILENT` |
| Connectivity | `MOBILE_DATA_ON`, `MOBILE_DATA_OFF`, `WIFI_ON`, `WIFI_OFF`, `AIRPLANE_MODE_ON`, `AIRPLANE_MODE_OFF`, `HOTSPOT_ON`, `HOTSPOT_OFF`, `LOCATION_ON`, `LOCATION_OFF` |
| Device and phone | `TORCH_ON`, `TORCH_OFF`, `TORCH_TOGGLE`, `LOCK_SCREEN`, `OPEN_DIALER` |

All other supported actions use these fields:

| Action | Required `params` | Optional `params` and defaults |
| --- | --- | --- |
| `VIBRATE` | none | `pattern`: `PULSE` (default), `DOUBLE_TAP`, `ALERT`, `HEARTBEAT`, `TRIPLE_TAP`, `SOS`; integer `durationMs` 80..800 (220); integer `amplitude` 1..255 (180) |
| `PLAY_SOUND` | `uri` for `CUSTOM` | `preset`: `NOTIFICATION` (default), `ALARM`, `RINGTONE`, `CUSTOM`; integer `durationMs` 1000..60000 (3000). For `CUSTOM`, `uri` is a string with `content` or `android.resource` scheme and nonblank authority; for other presets `uri` must be omitted. Actual file access is checked at execution. |
| `SET_MEDIA_VOLUME`, `SET_SCREEN_BRIGHTNESS` | integer `percent` 0..100 | none |
| `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_TOGGLE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS` | none | string `targetPackage` (default empty, selecting an active session) |
| `SPEAK_TEXT` | nonblank string `text`, at most 2,000 UTF-16 units | string `voiceName` (default empty); finite JSON number `speechRate` 0.5..2.0 (default 1.0) |
| `CREATE_ALARM` | integer `hour` 0..23, integer `minute` 0..59 | `days`: array of up to seven distinct integers 1..7 (default `[]`); string `message` (default empty) |
| `START_TIMER` | integer `durationSeconds` 1..86400 | string `message` (default empty) |
| `LAUNCH_APP`, `FORCE_STOP_APP` | nonblank string `package` | none |
| `OPEN_URL` | nonblank string `url`, trimmed for validation, with `http` or `https` scheme and nonblank host | none |
| `HTTP_WEBHOOK` | string `url` with HTTPS scheme and host | string `method` (default `POST`), string `headers` and `body` (default empty), integer `timeoutSeconds` 1..60 (default 10); see webhook constraints below |
| `DIAL_NUMBER`, `CALL_NUMBER` | nonblank string `number` | none |
| `SEND_SMS` | nonblank strings `recipient`, `message` | none |
| `DRAFT_SMS` | none individually; at least one of `recipient` or `message` must be nonblank | strings `recipient`, `message` (each defaults to empty) |
| `MUSIC_SEARCH` | string `appPackage`, nonblank string `query` | none |
| `MUSIC_PLAY_LINK` | string `appPackage`, nonblank valid string `link` | none |
| `SHOW_NOTIFICATION` | strings `title`, `body` (may be empty at parsing) | none |
| `CREATE_CALENDAR_EVENT` | nonblank strings `title`, `accountName`, `accountType`, `calendarName`; integers `startMillis`, `endMillis` | strings `description`, `location` (default empty); string `repeat` (default `NONE`); integer `reminderMinutes` (default `-1`) |

Alarm `days` uses Android `Calendar` numbering: Sunday = 1, Monday = 2, through Saturday = 7; an empty list requests a one-time alarm. The clock app controls the actual alarm UI and behavior.
For `SPEAK_TEXT`, omit `voiceName` to use the platform default if it is offline. If it is missing or requires a network, FlowPilot selects the highest-quality offline voice in the default voice's language (or the device locale's language if there is no default), then the highest-quality offline voice in any language. The text's language is not detected, so the fallback may speak in another language. To select a specific voice, open the offline voice picker in FlowPilot's speech action and use the exact, case-sensitive identifier in parentheses; preview it there first. A requested missing or network-only voice fails rather than silently falling back. Voice availability depends on the TTS engine, installed voice data and device. For example, `tr-tr-x-cfs-local` was confirmed by the user on a Xiaomi 15T Pro, but is not guaranteed elsewhere:

```json
{"version":1,"action":"SPEAK_TEXT","params":{"text":"Merhaba, Türkçe ses denemesi.","voiceName":"tr-tr-x-cfs-local","speechRate":0.9}}
```
For a device-specific, partial inventory of observed voice names, see [Xiaomi 15T Pro offline voices](XIAOMI_15T_PRO_OFFLINE_VOICES.md).


Webhook methods, after trimming and uppercasing, are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`; a nonempty body is permitted only for `POST`, `PUT` or `PATCH`. `headers` is a string of newline-separated `Name: value` lines, not a JSON object; blank and `#` comment lines are allowed. Header syntax and forbidden transport headers are validated. The HTTPS destination is subject to the executor's address safety checks, TLS verification and no redirects; an HTTP 2xx response indicates request success, not downstream delivery. JSON string bounds also apply to URL, headers and body.

## Permissions and operational risks

Parsing a command does not bypass Android permissions, device capabilities, Shizuku authorization, provider availability or background-launch restrictions. Bluetooth, NFC, dark theme, mobile data, Wi-Fi, airplane mode, hotspot, location, force stop and screen locking can require a running, authorized Shizuku service; Bluetooth also needs Bluetooth permission where applicable. Battery saver may require privileged settings access or Shizuku. Brightness and auto-rotation require Modify system settings; DND and some sound-profile changes require notification-policy access. Media controls need notification-listener access and an active session; notifications need notification permission where applicable; calendar needs read/write calendar access. Alarm, timer, dialer, browser, music and app launch depend on suitable handlers and system policies. These are runtime requirements, not guarantees of success.

`CALL_NUMBER` can place a real call and requires call permission; `SEND_SMS` sends a real message and requires SMS permission. Calls and SMS may incur carrier charges: obtain the user's explicit permission before any potentially billable call or SMS, including tests. `DIAL_NUMBER` and `OPEN_DIALER` open a dialer, and `DRAFT_SMS` opens a draft rather than directly sending it; the user can still subsequently place a billable call or send a message.

Connectivity actions can disconnect the very channel used to send commands or replies. Airplane mode, Wi-Fi/mobile-data/hotspot changes, force-stopping apps and `LOCK_SCREEN` can interrupt remote access, listeners or recovery and leave the device inaccessible until local intervention. Plan a safe local recovery path before enabling remote execution. Repeated commands can cause repeated side effects; `id` is correlation data, not a deduplication or authorization mechanism.

Music packages are exactly `com.spotify.music` and `com.google.android.apps.youtube.music`. Links require lowercase `https`, no userinfo, explicit port or fragment. Spotify accepts host `open.spotify.com` and path `/track/` followed by one or more ASCII letters/digits, with optional trailing slash. YouTube Music accepts `youtu.be/` plus an 11-character ASCII letter/digit/underscore/hyphen video ID (optional trailing slash), or `music.youtube.com/watch` with exactly one `v=` parameter containing such an ID. Hosts are case-insensitive; other query parameters are permitted. YouTube links are trimmed at their ends; Spotify links are not. The selected app must handle the intent: success means the music app was opened, **not** that audio playback started.

Calendar timestamps are integer JSON numbers in Unix epoch **milliseconds**, fitting signed 64-bit range; `startMillis` must be positive and `endMillis` strictly later. Fractional or exponent forms are not integers for this parser. The event uses the device's default time-zone ID; convert the desired local time and time zone to epoch milliseconds before sending. `repeat` is one of `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`; `reminderMinutes` is `-1` for none or `0..40320` minutes. Explicit account name, account type and calendar name must resolve to a writable calendar; there is no implicit calendar fallback. Calendar permissions and provider availability are still checked during execution, and each execution can create another event or series.

```json
{"version":1,"action":"MUSIC_SEARCH","params":{"appPackage":"com.spotify.music","query":"Example song"}}
```

```json
{"version":1,"action":"MUSIC_PLAY_LINK","params":{"appPackage":"com.google.android.apps.youtube.music","link":"https://music.youtube.com/watch?v=abcdefghijk"}}
```

```json
{"version":1,"id":"calendar-1","action":"CREATE_CALENDAR_EVENT","params":{"title":"Meeting","accountName":"your-account","accountType":"your-account-type","calendarName":"your-calendar","startMillis":1800000000000,"endMillis":1800003600000,"repeat":"NONE","reminderMinutes":15}}
```

Replace calendar identity with the actual selected writable calendar's values and timestamps with the intended instants.
The calendar selector shows display names and may show an account label or account type to disambiguate, but does not reliably expose all three technical values. `calendarName` is the provider's calendar `NAME`, which can differ from its displayed name. Obtain the exact `accountName`, `accountType` and `NAME` from the calendar provider through an authorized method; do not guess from labels or share account information unnecessarily. If they cannot be obtained, the JSON command cannot be constructed reliably.


```json
{"version":1,"action":"SHOW_NOTIFICATION","params":{"title":"FlowPilot","body":"Command received"}}
```

## Sources, filtering and optional replies

For a notification rule, grant notification access, choose the source app, and set the JSON action's source to `${notification.text}` or `${notification.bigText}`. Title and message filters can independently use case-insensitive contains or exact matching; configured app, title and message filters combine with AND. The message filter matches either text or bigText separately. For example, app ntfy + exact title `myotodelitimci` + message contains `playmusic` matches only when all enabled filters match; choose a source/template that resolves to the full JSON object, not just `playmusic`. Legacy keyword rules retain their title OR text OR bigText matching semantics. Filters restrict routing; they do **not** authenticate the sender.

For SMS, grant receive permission, configure the sender/filter, and use `${sms.body}`. Treat SMS and notification content as untrusted: anyone able to send matching content may cause the configured action. There is no mandatory signature, pairing, or dedicated ntfy integration. Use trusted sources and restrictive filters, avoid loops, and do not include secrets in commands or examples. Android/OEM background restrictions, notification truncation and availability, rule conditions and cooldown can prevent delivery or execution.

Following actions in the same run can use `${command.success}`, `${command.status}`, `${command.message}`, `${command.id}`, `${command.result}`. Values are transient strings, scoped to this run; the last JSON action replaces the previous outcome. `command.result` currently equals `command.message`, not a structured result. An absent ID is empty; an invalid command has an empty ID even if it contained one. Status/message pairs are:

| status | success | message |
| --- | --- | --- |
| `invalid_command` | `false` | `Invalid or unsupported command` |
| `permission_required` | `false` | `Permission required` |
| `execution_failed` | `false` | `Command execution failed` |
| `success` | `true` | `Command completed` (or `Music app opened; playback not verified` for music) |

The command outcome is available to subsequent actions, including after ordinary command failure; it is not an acknowledgement that an external recipient received anything. Payload and command ID are not stored in command history. Configured webhook/SMS actions can deliberately send the outcome or other selected values externally.

To reply via ntfy, add a subsequent HTTPS webhook POST to your ntfy server/topic, with a plain text body such as `id=${command.id} status=${command.status} message=${command.message}` and any required authorization header configured privately. Do not publish credentials or use a public topic for private data. A successful webhook means an HTTP 2xx response, not that a subscriber received or displayed it. When constructing a JSON webhook body, apply `jsonescape` to each interpolated string, e.g. `{"id":"${command.id|jsonescape}","status":"${command.status|jsonescape}"}`. Configure your server and topic separately; FlowPilot embeds no ntfy SDK.

Alternatively add a subsequent SMS action with `Command ${command.id}: ${command.status} — ${command.message}`. Sending requires Android SMS permission and can incur carrier charges; **obtain the user's explicit permission before sending any potentially billable SMS**, including tests. Successful submission is not proof of recipient delivery. Flashlight requires suitable hardware; notifications require Android notification permission where applicable; calendars need read/write permission; music depends on the target app and Android background-launch policy. These checks are not bypassed by JSON.

Implementation sources: `JsonCommandParser.kt`, `JsonCommandExecution.kt`, `CalendarExecutor.kt`, `MusicExecutor.kt`. This describes the working-tree implementation, not a claim of release or device verification.

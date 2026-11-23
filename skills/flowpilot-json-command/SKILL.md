---
name: flowpilot-json-command
description: Create, validate, or explain FlowPilot version 1 JSON commands for any supported ActionType, including device controls, media, alarms, communications, webhooks, calendar and notifications. Use for payloads, notification or SMS automation recipes and command replies. Respect permissions, privacy and costs; never invent actions or claim delivery or playback.
---

# FlowPilot JSON commands

Generate a single JSON object without Markdown or extra text when a ready-to-send payload is requested. Identify the intended action and collect all required details rather than guessing, especially a writable calendar identity, times, destination or recipient. Use placeholders only for an explicitly requested template. Never request real credentials. Ask for explicit permission before any potentially billable SMS or other paid operation, including tests; generating a payload is not permission to execute it.

In the FlowPilot repository, consult `skills/flowpilot-json-command/references/json-commands.md` and the current `JsonCommandParser.kt` and `JsonCommandExecution.kt`; the parser is authoritative if the documentation differs. The schema below summarizes the contract; the bundled [full reference](references/json-commands.md) provides details; check the installed FlowPilot version and do not assume unreleased actions exist in it. If a version's schema cannot be verified, say so rather than inventing a payload.

## Routing and envelope

Choose the exact case-sensitive action matching the user's intent from the tables below, then supply only its allowed parameters. Do not equate opening a system or external app with completing the requested operation. `EXECUTE_JSON_COMMAND` is excluded to prevent recursion; no arbitrary shell or intent action is supported.

`{"version":1,"id":"optional-id","action":"TORCH_ON","params":{}}`. Only these four root keys are allowed; `id` is optional, nonblank and at most 128 UTF-16 units. `version` is the literal JSON number `1`. Entire input is at most 16,384 UTF-16 units and each string parameter at most 4,096. Surrounding whitespace is allowed; reject extra keys, duplicate keys, wrong types, nested objects, unexpected arrays and surrounding prose. Only `CREATE_ALARM.days` accepts an array. Integers are signed 64-bit JSON integer literals, not fractions or exponent notation. All parameter names and enums are case-sensitive. Optional string defaults are empty unless stated otherwise; null is not a string. Parser acceptance does not bypass runtime capability or permission checks.

## Action schemas

Use `{}` for each of these actions (no parameters):

- `TORCH_ON`, `TORCH_OFF`, `TORCH_TOGGLE`; `BLUETOOTH_ON`, `BLUETOOTH_OFF`; `NFC_ON`, `NFC_OFF`; `BATTERY_SAVER_ON`, `BATTERY_SAVER_OFF`; `DARK_THEME_ON`, `DARK_THEME_OFF`; `AUTO_ROTATE_ON`, `AUTO_ROTATE_OFF`; `DND_ON`, `DND_OFF`.
- `SOUND_PROFILE_NORMAL`, `SOUND_PROFILE_VIBRATE`, `SOUND_PROFILE_SILENT`; `OPEN_DIALER`; `MOBILE_DATA_ON`, `MOBILE_DATA_OFF`; `WIFI_ON`, `WIFI_OFF`; `AIRPLANE_MODE_ON`, `AIRPLANE_MODE_OFF`; `HOTSPOT_ON`, `HOTSPOT_OFF`; `AUTO_BRIGHTNESS_ON`, `AUTO_BRIGHTNESS_OFF`; `LOCK_SCREEN`; `LOCATION_ON`, `LOCATION_OFF`.

| Action | Required parameters | Optional parameters and defaults |
| --- | --- | --- |
| `VIBRATE` | none | `pattern`: `PULSE` (default), `DOUBLE_TAP`, `ALERT`, `HEARTBEAT`, `TRIPLE_TAP`, `SOS`; integer `durationMs` 80–800 (220); integer `amplitude` 1–255 (180) |
| `PLAY_SOUND` | none, except `uri` for `CUSTOM` | `preset`: `NOTIFICATION` (default), `ALARM`, `RINGTONE`, `CUSTOM`; integer `durationMs` 1000–60000 (3000); `uri` only with `CUSTOM`, a `content` or `android.resource` URI with nonblank authority |
| `SET_MEDIA_VOLUME`, `SET_NOTIFICATION_VOLUME`, `SET_ALARM_VOLUME`, `SET_SCREEN_BRIGHTNESS` | integer `percent` 0–100 | none |
| `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_TOGGLE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS` | none | string `targetPackage` |
| `SPEAK_TEXT` | nonblank `text`, at most 2000 UTF-16 units | string `voiceName`; JSON number `speechRate`, converted to finite Float in 0.5–2.0 (1.0) |
| `CREATE_ALARM` | integer `hour` 0–23, integer `minute` 0–59 | `days`: array of up to seven distinct integers 1–7 (default `[]`); string `message` |
| `START_TIMER` | integer `durationSeconds` 1–86400 | string `message` |
| `LAUNCH_APP`, `FORCE_STOP_APP` | nonblank string `package` | none |
| `OPEN_URL` | nonblank `url`: HTTP or HTTPS URI with host | none |
| `HTTP_WEBHOOK` | `url`: HTTPS URI with host | strings `method` (default `POST`), `headers`, `body`; integer `timeoutSeconds` 1–60 (10). Method, header and body must also satisfy webhook validation. |
| `DIAL_NUMBER`, `CALL_NUMBER` | nonblank string `number` | none |
| `SEND_SMS` | nonblank strings `recipient`, `message` | none |
| `DRAFT_SMS` | at least one of `recipient`, `message` must be nonblank | strings `recipient`, `message` |
| `MUSIC_SEARCH` | strings `appPackage`, nonblank `query` | none |
| `MUSIC_PLAY_LINK` | strings `appPackage`, nonblank valid `link` | none |
| `SHOW_NOTIFICATION` | strings `title`, `body` (may be empty at parsing) | none |
| `CREATE_CALENDAR_EVENT` | nonblank strings `title`, `accountName`, `accountType`, `calendarName`; integer `startMillis`, `endMillis` | strings `description`, `location`, `repeat` (default `NONE`); integer `reminderMinutes` (default `-1`) |

`SET_NOTIFICATION_VOLUME` and `SET_ALARM_VOLUME` set the corresponding Android stream, for example `{"version":1,"action":"SET_NOTIFICATION_VOLUME","params":{"percent":35}}` or `{"version":1,"action":"SET_ALARM_VOLUME","params":{"percent":50}}`. The percentage is converted to a rounded device volume step, so the resulting level may not be the exact percentage. An alarm request below the device-reported minimum fails; the minimum varies by device, and on Android before 9 the executor assumes zero. Notification and ringtone volumes may be linked by the device. If the level cannot be read back or differs from the requested step, the command fails; a failed readback does not prove the setting was unchanged. Permission or Do Not Disturb restrictions can also prevent a change.

For `SPEAK_TEXT`, if the user accepts the default, omit `voiceName`: FlowPilot uses the platform default if offline, otherwise the highest-quality offline voice in the default voice's language (or device locale's language if no default), then the highest-quality offline voice in any language. It does not infer language from the text. For a specific language or voice, ask for the exact case-sensitive identifier in parentheses in FlowPilot's offline voice picker; the user can preview it there. Explicit missing or network-only voices fail rather than falling back. Availability depends on the device and TTS engine. `tr-tr-x-cfs-local` worked on one Xiaomi 15T Pro, not necessarily elsewhere; never assume it exists. Example when confirmed available: `{"version":1,"action":"SPEAK_TEXT","params":{"text":"Merhaba","voiceName":"tr-tr-x-cfs-local","speechRate":0.9}}`.

Music packages: `com.spotify.music` and `com.google.android.apps.youtube.music`. Links require lowercase `https`, no userinfo, port or fragment. Spotify: `open.spotify.com/track/` followed by ASCII alphanumerics, optional trailing slash. YouTube: `youtu.be/` plus exactly 11 ASCII letters/digits/`_`/`-`, optional trailing slash, or `music.youtube.com/watch` with exactly one `v=` of the same 11-character form. Other query parameters are allowed. Hosts are case-insensitive. YouTube links are trimmed at ends, Spotify links are not. Intent dispatch does not prove playback.

Calendar timestamps are Unix epoch milliseconds: `startMillis` must be positive and `endMillis` later. Convert the desired local time and zone to instants; the event uses the device default time zone. `repeat` is `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, or `YEARLY`; `reminderMinutes` is `-1` or 0–40320. Obtain the actual writable calendar's technical `accountName`, `accountType` and provider `NAME` through an authorized method; do not invent them or assume a fallback. The picker displays calendar labels, which may differ from provider `NAME`, and does not reliably expose all three values. If they cannot be obtained, do not construct the command. Repeated execution can create multiple events.

Webhook `method` is trimmed and case-normalized to `GET`, `POST`, `PUT`, `PATCH`, `DELETE` or `HEAD`; only POST, PUT and PATCH permit a nonempty body. Headers are newline-separated `Name: Value` strings; after trimming, names use token characters and names and values contain no controls or connection-managed headers such as `Host` or `Content-Length`. Blank lines and lines beginning with `#` are ignored. Never put real credentials in examples or disclose private URLs, headers, bodies, phone numbers or notification content. Sending a webhook can disclose data to its destination. Calling and messaging can incur charges or expose information; obtain the user's permission for paid operations, and distinguish opening a dialer or draft from actually placing a call or sending a message.

## Configure and reply safely

Use `${notification.text}` or `${notification.bigText}` for a notification trigger, `${sms.body}` for SMS. The source must resolve to the entire JSON object; use source filters such as `trim` or `stripPrefix` where needed. The configured source template has a separate 4,000-character limit; it is encrypted at rest, cleared in normal exports and preserved in encrypted exports. Parsed parameter values are not rendered again as templates. Manual runs have no incoming notification or SMS payload. Notification app, enabled title and message filters combine with AND; each field supports contains/exact matching, ignoring case. Message matches text or bigText independently. Filters are not sender authentication: no mandatory signature or pairing exists, so anyone able to provide matching content may trigger actions. Trust and restrict the source, avoid loops, and never treat filters as authorization. SMS requires receiving permission and notification triggers require notification access. Target action permissions, hardware, app availability, privileged capabilities and Android background restrictions still apply.

A following action may use `${command.success}`, `${command.status}`, `${command.message}`, `${command.id}`, `${command.result}`. These are transient strings from the most recent command in that run; result equals message. Status is `invalid_command`, `permission_required`, `execution_failed`, `success`, or `unverified`. For `unverified`, `command.success` is empty: a request was sent but its final outcome is unknown. No ID is retained on parse failure. For a JSON webhook body use `jsonescape` on every interpolated string. An HTTPS webhook POST to a user-configured ntfy topic can send a response; HTTP 2xx does not prove subscriber delivery.

An example command:

```json
{"version":1,"action":"SHOW_NOTIFICATION","params":{"title":"FlowPilot","body":"Hello"}}
```

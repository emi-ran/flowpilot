---
name: flowpilot-json-command
description: Create or explain FlowPilot version 1 JSON commands and notification or SMS automation recipes. Use whenever a user wants to remotely trigger FlowPilot flashlight, music, calendar, or notification actions, construct a command payload, or return command status by webhook or SMS. Do not invent supported actions or claim delivery or playback.
---

# FlowPilot JSON commands

Generate a single JSON object, not Markdown or extra text when the user needs a payload ready to send. Ask for missing action details, calendar identity or times rather than guessing. Use placeholders only when the user explicitly wants a template. Never request real credentials or send potentially billable SMS without explicit permission.

When working in the FlowPilot repository, read `docs/JSON_COMMANDS.md` and the current `JsonCommandParser.kt` and `JsonCommandExecution.kt`; source is authoritative if versions differ. For a standalone installation, use this version-1 summary and tell the user to check their installed FlowPilot version.

## Payload

`{"version":1,"id":"optional-id","action":"TORCH_ON","params":{}}`. Only these four root keys are allowed; `id` is optional, nonblank and at most 128 UTF-16 units. `version` is the literal JSON number `1`. Entire input is at most 16,384 UTF-16 units and each string parameter at most 4,096. Reject extra keys, duplicate keys, wrong types, nested objects, arrays and surrounding prose. Supported actions and exact parameters:

- `TORCH_ON`, `TORCH_OFF`: empty `params`.
- `MUSIC_SEARCH`: `appPackage`, nonblank `query` strings.
- `MUSIC_PLAY_LINK`: `appPackage`, valid nonblank `link` strings.
- `SHOW_NOTIFICATION`: `title`, `body` strings.
- `CREATE_CALENDAR_EVENT`: nonblank strings `title`, `accountName`, `accountType`, `calendarName`; JSON integer `startMillis` and `endMillis` (signed 64-bit, start positive and end later). Optional strings `description`, `location`, `repeat`; optional integer `reminderMinutes`. Repeat defaults to `NONE` and can be `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`; reminder defaults to `-1`, or use `0..40320` minutes. Times are Unix epoch milliseconds and the event uses the device default time zone. Obtain the actual writable calendar identity; do not invent it.

Music packages: `com.spotify.music` and `com.google.android.apps.youtube.music`. Links require lowercase `https`, no userinfo, port or fragment. Spotify: `open.spotify.com/track/` followed by ASCII alphanumerics, optional trailing slash. YouTube: `youtu.be/` plus exactly 11 ASCII letters/digits/`_`/`-`, optional trailing slash, or `music.youtube.com/watch` with exactly one `v=` of the same 11-character form. Other query parameters are allowed. Hosts are case-insensitive. YouTube links are trimmed at ends, Spotify links are not. Intent dispatch does not prove playback.

## Configure and reply safely

Use `${notification.text}` or `${notification.bigText}` for a notification trigger, `${sms.body}` for SMS. The source must resolve to the entire JSON object; use source filters such as `trim` or `stripPrefix` where needed. Notification app, enabled title and message filters combine with AND; each field supports contains/exact matching, ignoring case. Message matches text or bigText independently. Filters are not sender authentication; trust and restrict the source. SMS requires receiving permission and notification triggers require notification access. Target action permissions, hardware, app availability and Android background restrictions still apply.

A following action may use `${command.success}`, `${command.status}`, `${command.message}`, `${command.id}`, `${command.result}`. These are transient strings from the most recent command in that run; result equals message. Status is `invalid_command`, `permission_required`, `execution_failed`, or `success`. No ID is retained on parse failure. For a JSON webhook body use `jsonescape` on every interpolated string. An HTTPS webhook POST to a user-configured ntfy topic can send a response; HTTP 2xx does not prove subscriber delivery. An SMS reply can incur charges; request explicit permission before sending, including tests. Avoid exposing secrets, private notification text, or phone numbers.

An example command:

```json
{"version":1,"action":"SHOW_NOTIFICATION","params":{"title":"FlowPilot","body":"Hello"}}
```

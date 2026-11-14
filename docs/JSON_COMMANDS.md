# JSON commands (unreleased)

The configured source template is limited to 4,000 characters by `TemplateRenderer`; the rendered JSON has the separate 16,384 UTF-16-unit parser limit below. The configured source is encrypted with Android Keystore. Normal single-rule and full exports clear it, so re-enter the source after normal import; encrypted exports preserve it.

## Installing the agent skill

Copy the complete `docs/skills/flowpilot-json-command` directory into a skills directory supported by your agent, for example `~/.agents/skills/flowpilot-json-command/`, then reload the agent according to its instructions. The directory must contain `SKILL.md`. Consult the full contract here when using the skill in this repository; a standalone copy includes the essential version-1 rules.

The **Run JSON command** action renders its configured source template once, then parses the entire resulting text as one JSON object. Surrounding JSON whitespace is accepted; prefixes, suffixes, Markdown fences, arrays, duplicate keys, nested objects beyond `params`, and trailing text are rejected. It does not extract a JSON fragment. A trigger can supply `${sms.body}`, `${notification.text}`, or `${notification.bigText}`; choose the field actually containing the complete object. A manual run does not have an incoming notification or SMS payload. Filters such as `trim` and `stripPrefix` can prepare a source before parsing; values inside the parsed command are not rendered again as templates.

## Envelope and limits

```json
{"version":1,"id":"request-42","action":"TORCH_ON","params":{}}
```

Only `version`, `id`, `action`, and `params` are allowed at the root. `version` is required and must be the JSON number written exactly `1` (not `1.0`, `1e0`, or a string). `id` is optional: if present it is a nonblank string of at most 128 UTF-16 code units. `action` is a required case-sensitive string; `params` is a required object. Input is limited to 16,384 UTF-16 code units before parsing and each string value in `params` to 4,096 UTF-16 code units. Unknown keys, types, actions, versions, missing fields, malformed JSON, and duplicate keys fail rather than being ignored. There is no arbitrary intent, shell, or nested JSON-command action.

## Actions and parameters

Only the following actions are accepted. Parameter names and enum values are case-sensitive; extra parameters are rejected. All string values are JSON strings, not null.

| Action | Required `params` | Optional `params` |
| --- | --- | --- |
| `TORCH_ON`, `TORCH_OFF` | empty object `{}` | none |
| `MUSIC_SEARCH` | `appPackage`, nonblank `query` | none |
| `MUSIC_PLAY_LINK` | `appPackage`, nonblank valid `link` | none |
| `SHOW_NOTIFICATION` | `title`, `body` (strings, may be empty at parsing) | none |
| `CREATE_CALENDAR_EVENT` | nonblank `title`, `accountName`, `accountType`, `calendarName`; integer `startMillis`, `endMillis` | string `description`, `location` (default empty); `repeat` (default `NONE`); integer `reminderMinutes` (default `-1`) |

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

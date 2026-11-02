# How Long Since?

A small Android app that tracks how long it has been since something last happened, such as watering the plants, changing the toothbrush or the last haircut.

Each item is a **counter**: a label, some tags, and a log of every time it was set or reset. The app shows the time since the latest reset and can remind you when a counter has gone too long. An NFC sticker can reset a counter with a single tap.

## Features

- **Counters**
  - Each counter has a label and comma-separated tags.
  - Cards show the elapsed time as a large number ("12 days") with the date of the last reset underneath.
- **Reset log**
  - Every create or reset is stored, marked `create`, `manual` or `nfc`.
  - Each counter's detail page shows the log as a timeline, with the gap between resets.
  - A small bar chart shows the recent intervals and their average.
- **Undo**: every reset shows an "Undo" snackbar, and Undo removes that log entry.
- **Reminders**
  - Optionally set "notify after" in hours, days or weeks (or minutes in Debug mode).
  - A ring around the reset button fills up as the reminder time gets closer and turns red when overdue.
  - Tapping the notification opens that counter's page.
- **NFC tags**
  - Pair a tag from the counter's detail page. The app writes `howlongsince://reset/<id>` to the tag and remembers the tag's ID.
  - Tapping the tag resets the counter, even when the app is closed.
  - Tags that aren't paired are rejected, and Unpair or Re-pair makes an old tag stop working.
- **Tags**: a filter row at the top of the list, with a stable color for each tag.
- **Settings** (☰ menu)
  - Dark theme switch. It follows the system until you change it.
  - Debug mode, which allows minute-based reminders.
  - JSON export, and an import that replaces all data. The file is checked and you confirm before anything is overwritten.
- **Look**: Material 3 with colors from your wallpaper (Android 12+) and a large title bar that collapses as you scroll.

## Tech

- Kotlin and Jetpack Compose (Material 3), in a single Activity. Android 8.0 (API 26) or later.
- **Room** stores data in two tables: `Counter` and `Event`. Elapsed time is calculated from the latest `Event` and never stored.
- **WorkManager** runs one delayed job per counter, and it is replaced on every change.
- The **Android NFC API**: NDEF URI records for tapping, and reader mode while pairing.
- The built-in `org.json` library for export and import.

## Code layout

```
app/src/main/java/eu/etrunon/howlongsince/
  Data.kt          Room entities, database queries, time and tag formatting helpers
  MainActivity.kt  List screen, edit dialog, settings, handling of notification and NFC intents
  Detail.kt        Detail screen: timeline, interval chart, NFC pairing
  Notify.kt        Reminder scheduling and the notification worker
  Nfc.kt           Tag writing and reading of the reset URI
  Export.kt        JSON export, import and restore
  Theme.kt         Dynamic color theme and tag colors
app/src/test/.../SinceTest.kt  Unit tests (formatting, tags, reminder delay, NFC URI, backup round trip)
```

## Build

Requires JDK 21 (Gradle 8.11 does not run on newer JDKs) and the Android SDK. Set `sdk.dir` in `local.properties`, or set `ANDROID_HOME`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew test          # unit tests
./gradlew installDebug  # build and install on a connected phone (USB debugging on)
```

NFC needs a real phone and writable NDEF tags (for example NTAG213/215/216).

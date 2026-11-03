# How Long Since?

**English** | [Italiano](README-IT.md)

<img src="logo.svg" width="96" alt="">

A small Android app that tracks how long it has been since something last happened, such as watering the plants, changing the toothbrush or the last haircut.

Each item is a **counter**: a label, some tags, and a log of every time it was set or reset. The app shows the time since the latest reset and can remind you when a counter has gone too long. An NFC sticker can reset a counter with a single tap.

An optional self-hosted server keeps the counters in sync between the phone and a web app that works in any browser.

<img src="docs/app-list.png" width="240" alt="Counter list"> <img src="docs/app-detail.png" width="240" alt="Counter detail with reset log">

## Features

- **Counters**
  - Each counter has a label and comma-separated tags.
  - Cards show the elapsed time as a large number ("12 days") with the date of the last reset underneath.
- **Reset log**
  - Every create or reset is stored, marked `create`, `manual` or `nfc`.
  - Each counter's detail page shows the log as a timeline, with the gap between resets.
  - A small bar chart shows the recent intervals and their average.
- **Reset at a past time**: "Reset at…" on the detail page, for when you forgot to log it.
- **Notes**: "Reset…" on the detail page takes an optional note (e.g. "new filter brand"), shown in the timeline.
- **Archive**: hide a counter without losing its history; archived counters have no reminders and sit behind an "Archived" chip.
- **Search and sort**: search labels and tags; sort by most overdue, recently reset or name (app and web).
- **Home-screen widget**: the counters most in need of a reset, each with a reset button. Times refresh on every change and every 30 minutes.
- **Use my average**: the edit dialog can set "notify after" to your average interval between resets.
- **Overdue first**: counters past their reminder are listed at the top (app and web).
- **Launcher shortcuts**: long-press the app icon to reset the 4 counters that need it most.
- **Undo**: every reset shows an "Undo" snackbar, and Undo removes that log entry.
- **Reminders**
  - Optionally set "notify after" in hours, days or weeks (or minutes in Debug mode).
  - A ring around the reset button fills up as the reminder time gets closer and turns red when overdue.
  - Tapping the notification opens that counter's page. Its buttons reset the counter or snooze for a day.
- **NFC tags**
  - Pair a tag from the counter's detail page. The app writes `howlongsince://reset/<uuid>` (the counter's sync id, so the tag works on every synced phone) to the tag and remembers the tag's ID.
  - Tapping the tag resets the counter, even when the app is closed.
  - Tags that aren't paired are rejected, and Unpair or Re-pair makes an old tag stop working.
  - Or "Copy URL" and write the tag with any other NFC app; the first tap of that tag then pairs it.

  <img src="docs/nfc-pairing.png" width="240" alt="Pair dialog: copy the URL or pair now">

- **Groups** (☰ menu → Groups): a group is a collection of counters that is synced, or not, on its own. The home screen shows one foldable section per group, the menu opens a single group, and a counter's edit dialog moves it between groups. Counters without a group stay on the phone. Deleting a group moves its counters back to "Ungrouped".
  - **Sync is per group**: tap a group, enter your server URL (and token), and turn sync on. The phone makes a random key and encrypts the group before sending it; the server only stores data it can't read.
  - **Share** (the share icon on a synced group): a link and a QR code that hold the server and the key. On another phone, open Groups → Join and paste the link (or open a `howlongsince://join…` link); it shows the group's name and asks before joining. The link is also your backup of the key, so keep it. Anyone who has it can read and change the group, and access can't be taken back: to cut someone off, make a new group and move the counters into it.
- **Tags**: a filter row at the top of the list, with a stable color for each tag.
- **Settings** (☰ menu)
  - Dark theme switch. It follows the system until you change it.
  - Debug mode, which allows minute-based reminders.
  - JSON export, and an import. The file is checked and you confirm before anything is overwritten. "Replace" swaps the groups of the same name for the file's, "Keep both" imports them as new groups. In a synced group, a replace reaches everyone in it.
- **Languages**: English and Italian, following the phone or browser language. On Android 13+ the app's language can also be set on its own in the system settings (Settings → Apps → How Long Since? → Language).
- **Look**: Material 3 with colors from your wallpaper (Android 12+) and a large title bar that collapses as you scroll.

## Tech

- Kotlin and Jetpack Compose (Material 3), in a single Activity. Android 8.0 (API 26) or later.
- **Room** stores data in two tables: `Counter` and `Event`. Elapsed time is calculated from the latest `Event` and never stored.
- **WorkManager** runs one delayed job per counter, and it is replaced on every change.
- The **Android NFC API**: NDEF URI records for tapping, and reader mode while pairing.
- The built-in `org.json` library for export, import and sync, and `HttpURLConnection` for sync.

## Code layout

```
app/src/main/java/eu/etrunon/howlongsince/
  Data.kt          Room entities, database queries, time and tag formatting helpers
  MainActivity.kt  List screen, edit dialog, settings, handling of notification and NFC intents
  Detail.kt        Detail screen: timeline, interval chart, NFC pairing
  Notify.kt        Reminder scheduling and the notification worker
  Nfc.kt           Tag writing and reading of the reset URI
  Export.kt        JSON export, import and restore
  Sync.kt          Sync with the server: JSON format, merge and the HTTP calls
  Crypto.kt        Key derivation and encryption of a group
  Groups.kt        Groups screen: create, rename, delete, sync and share
  Join.kt          Share link, QR code and joining a group
  Widget.kt        Home-screen widget
  Theme.kt         Dynamic color theme and tag colors
app/src/test/.../SinceTest.kt  Unit tests (formatting, tags, reminder delay, NFC URI, backup and sync JSON round trips)
server/
  main.go          Sync server: encrypted blob store and HTTP handlers
  main_test.go     Blob store test (runs during docker build)
  web.test.mjs     Web app logic tests: node --test server/web.test.mjs
  index.html       Web app, built into the server binary
  Dockerfile, compose.yml
logo.svg           Logo (the app icon and the web favicon are drawn from it)
```

## Build

Requires JDK 21 (Gradle 8.11 does not run on newer JDKs) and the Android SDK. Set `sdk.dir` in `local.properties`, or set `ANDROID_HOME`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew test          # unit tests
./gradlew installDebug  # build and install on a connected phone (USB debugging on)
```

NFC needs a real phone and writable NDEF tags (for example NTAG213/215/216).

## Sync server and web app (optional)

`server/` is a small Go server that stores each synced group as one encrypted file and serves the web app. The Docker image is a single binary of about 8 MB. Each person runs their own copy; the app has no built-in server address. The server can't read your counters: encryption and decryption happen on the phone and in the browser.

![Web app](docs/web.png)

**Web app**: everything the phone does except what needs the phone itself.
- **Groups**: the page shows every group it knows as a foldable section (plus "Ungrouped", which lives only in this browser's local storage), each with its own sync status. A sidebar (a drawer opened with ☰ on narrow screens, like the phone's) lists All, Ungrouped and each group, then New group and Join, the selected group's Share (QR code, link, copy, the browser's share sheet), Rename, Forget and Open in app, and the settings. You can also move a counter between groups from its edit form (leaving a synced group asks first). The key is in the part of the link after `#`, which the browser never sends; the groups you opened are remembered in this browser's local storage, and an "Open in app" link hands one to the phone. It needs https (or localhost) for the browser's crypto.
- **Counters**: add (the + button at the bottom right, like the phone's, opens a form for label, tags, group and reminder), reset (now, with a note, or at a past time), undo, archive and delete. Opening one shows the reset log, the interval chart and a form for its label, tags, group and "notify after" reminder (the reminder itself fires on the phone). A paired NFC tag can be unpaired, but pairing needs the phone. Search, tag filter and sort work inside each section. The times update every minute.
- **Export and import** use the phone's JSON file, so a file moves between the phone and the web: Replace swaps the groups of the same name for the file's, Keep both imports them as new groups.
- **Settings** in the sidebar: a dark theme switch (follows the system until you use it), a debug switch that allows minutes in "notify after", and the language (English or Italian, following the browser).
- **Not on the web**: NFC pairing, reminders and notifications, the widget, and groups that live on a different server than the page.

**Run it**
```bash
cd server
echo HLS_TOKEN=$(openssl rand -hex 16) > .env   # the token clients need
mkdir -p data                                   # must be writable by uid 1000
docker compose up -d --build                    # listens on 127.0.0.1:13387
```
Put a reverse proxy with HTTPS in front of it. The server speaks plain HTTP only; the proxy handles the certificate (Let's Encrypt, your own, …), rate-limits token guessing, and adds browser security headers. nginx does all of this without plugins:
```nginx
# in the http {} block
limit_req_zone $binary_remote_addr zone=hls:1m rate=30r/m;

server {
    listen 443 ssl;
    server_name hls.example.com;
    ssl_certificate     /etc/letsencrypt/live/hls.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/hls.example.com/privkey.pem;

    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy no-referrer always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'" always;

    location /s/ {
        limit_req zone=hls burst=20 nodelay;   # ~30 syncs/min per IP, bursts allowed
        proxy_pass http://127.0.0.1:13387;
    }
    location / {
        proxy_pass http://127.0.0.1:13387;
    }
}
```
With Caddy, the same headers go in a `header` block; rate limiting needs the `caddy-ratelimit` plugin. `tailscale serve` gives HTTPS but can't add headers or limits, which is fine when only your tailnet can reach it.

The Android app needs an `https://` URL with a certificate that Android trusts by default (Let's Encrypt, Tailscale, a commercial CA). Self-signed or private-CA certificates installed on the phone are not trusted by the app.

Then:
- **Web**: open a group's share link. If the server needs a token and the link doesn't carry it, the page asks for it (it works with password managers) and keeps it in the browser.
- **Install the web app** (own window, own icon):
  - Chrome / Chromium / Edge (any OS): the install icon on the right of the address bar.
  - Firefox on Windows (143+): the "add to taskbar" icon in the address bar pins it to the Windows taskbar. If missing, set `browser.taskbarTabs.enabled` to `true` in `about:config`.
  - Firefox on Android: menu → "Add to Home screen".
  - Firefox on Linux: no install option; use a pinned tab or Chromium.
- **Phone**: Groups → tap a group → enter the URL and the token → Turn on sync. An existing global server from an older version becomes a group called "Synced" the first time the new app syncs.

**Configuration** (environment variables): `TOKEN` (bearer token, required: the server won't start without it), `INSECURE=1` (run without a token, only on a trusted network), `DATA_DIR` (default `/data`, blobs go in `s/` inside it), `ADDR` (default `:8080`). `compose.yml` maps `HLS_TOKEN` from `.env` to `TOKEN`.

**API**: `GET /` web app, `GET /health` returns `ok`, `GET /s/{id}` returns `{etag, ciphertext}` (404 if there is none), `PUT /s/{id}` takes `{ciphertext}` and `If-Match: <etag>` and answers 409 if someone else wrote first (no `If-Match` means create only). `{id}` is 32 hex characters derived from the group's secret. All of it takes the bearer token.

**How sync works**
- The key: each synced group has a random 32-byte secret made on the phone. HKDF-SHA256 turns it into the id the server sees and an AES-256-GCM key; the id is also authenticated data, so the server can't hand one group's blob out as another's. The secret is kept in a separate preferences file that backups skip. The server sees an anonymous id, the blob size, timing and your IP address.
- A sync is: get the blob, decrypt, merge with the local group, encrypt, put it back with `If-Match`; on a 409 it starts again. The merge is done by the phone and the web app, never the server.
- Counters and resets have a UUID. The phone keeps its local ids for reminders; NFC tags hold the UUID.
- Counter edits: the newer `updatedAt` wins. Resets are merged by id. Moving a counter to another group keeps its UUID and writes a tombstone in the old group, so it disappears there for everyone.
- Deletes are tombstones (`deleted: true`), so they reach every device.
- The app syncs when it opens, shortly after every change, and every 15 minutes in the background (the Android minimum). The web app syncs every 15 minutes and when its tab comes back into view.
- Deleted counters are dropped for good after 90 days. A device that has been offline for longer could bring one back. A server that serves an old blob could do the same.
- If every phone holding a group's key is lost, its data is gone. Keep the share link.
- The server drops slow or stuck connections (10 s to send headers, 30 s per request) and writes blobs atomically.

**Several phones on one server**
- A group is shared with whoever has its link, which suits a household sharing chores; other groups stay private to a phone or go to other people. One server can hold many groups.
- Resets are never lost: each has a UUID and the server keeps them all, even when two phones reset at the same moment.
- Edits are whole-counter: the newer `updatedAt` replaces label, tags and reminder together, so two people changing different fields at almost the same time lose one change.
- Edit order uses each phone's clock; a phone whose clock is off by minutes can win or lose an edit it shouldn't.
- Reminders fire on every phone that has the counter. A tag paired on one phone works on all of them: the tag holds the counter's sync id, and the pairing syncs.
- Each sync carries the full state of the group, which is fine for a few phones and hundreds of counters (requests are capped at 10 MB).

## License

GPL-3.0, see [LICENSE](LICENSE). You can use, change and share it; shared changes must stay under the GPL.

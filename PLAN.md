# Plan: groups and end-to-end encrypted sync

Agreed 2026-11-03. Each phase ships on its own.

## Goals

- Users create groups. The home screen shows all counters in collapsible sections, one per group. The drawer opens a page with one group's counters.
- Each group is synced on its own, or not at all. For example, a "Personal" group never leaves the phone while a "Family" group is synced.
- The sync server only stores encrypted data. The key is made on the phone and never sent to the server. One app can sync different groups to different servers.
- A group is shared with a link (or a QR code of it) that holds the server host and the key.

## Core design change

Today the server merges data and the web app reads plaintext. With end-to-end encryption the server can do neither, so:

- **Server:** a dumb blob store (`/sync`, `merge` and `prune` were deleted in Phase 4). `GET /s/{id}` returns `{etag, ciphertext}`. `PUT /s/{id}` takes `If-Match: <etag>` and returns 409 on a conflict. The ETag is a content hash, and each blob is one file.
- **Client sync:** GET, decrypt, merge (the existing `Dao.merge`, limited to one group), encrypt, PUT. On a 409, start again from the GET. Tombstone pruning (90 days) is done on the client.
- **Crypto:** each synced group has a random 32-byte secret. HKDF derives two values from it: the space id the server sees, and an AES-256-GCM key. The space id is passed as associated data, so the server can't swap blobs between spaces. Only `javax.crypto` and WebCrypto are used, no new dependencies.
- **What the server still sees:** an anonymous space id, blob size, timing and IP address.
- **`TOKEN`:** instance-wide, to stop strangers from storing blobs on the server. When set, the share link carries it. Superseded by per-device tokens (see "Plan: group access control").

## Phase 1: groups (local only)

- `CounterGroup(id, name, nameUpdatedAt, deleted)` table. A nullable `Counter.groupId`, where null means "Ungrouped", which is never synced. Room v4 migration.
- **Home:** one `LazyColumn` with a sticky, foldable header per group. Which sections are folded is saved in prefs.
- **Drawer:** one entry per group. It opens the same list filtered to that group.
- **Group settings:** create, rename and delete. The counter edit dialog gets a group picker.
- **Import and "Delete all":** limited to one group. Without this they would delete shared data for every member.
- **Unaffected:** widget, reminders and NFC (they use local ids and counter uids).

## Phase 2: per-group encrypted sync

- `CounterGroup` gets a nullable `syncUrl` column. The key and token live in a separate `sync_keys` prefs file (a database column can't be left out of a backup). Groups don't need a uid, because the blob *is* the group. The name is stored inside the blob and merged by `nameUpdatedAt`, newest wins.
- `sync()` runs once per synced group, and each group shows its own status. Local-only groups never touch the network.
- **Group-limited merge:** only rows in that group are matched by uid.
- **Moving a counter between groups** keeps its uid, so NFC tags keep working. When the outgoing blob is built, a counter that is in the remote blob but now sits in another local group with a newer `updatedAt` is written as a tombstone. The UI asks for confirmation first, because the move removes the counter for the other members.
- **Leaving a group or stopping its sync:** drop the key locally and tombstone nothing.
- **Backups:** `sync_keys.xml` is excluded through `dataExtractionRules` and `fullBackupContent`. The JSON export never contains keys.
- **Migration:** if the old global `syncUrl` is set, existing counters move into a new "Synced" group. The old global URL and token settings are removed, and the old `/sync` protocol is dropped (breaking change).

## Phase 3: share and join

- The group settings screen has a Share button that shows the link `https://host/#k=<base64url secret>[&t=<token>]` and a QR code of it.
- Opened in a browser, the link is the web app for that group. Its "Open in app" button launches `howlongsince://join?h=<host>#k=…`. This avoids App Links domain verification for self-hosted domains.
- The Groups screen also has a "Join" button that takes a pasted link (web or `howlongsince://` form), so joining works before the web app does (Phase 4). The QR code uses `zxing-core`, the only new dependency.
- The app has no QR scanner of its own: the phone's camera app opens the link.
- Joining asks "Join «name» on host?", then creates the group and runs the first sync.
- "Copy link" in the group settings doubles as the key backup.

## Phase 4: web app

- The web app decrypts in the browser with WebCrypto. The key comes from the URL fragment, which is never sent to the server.
- A JS port of the group-limited merge.
- Known groups (key, token, name) are kept in `localStorage`. The host is the server that serves the page, so it isn't stored.
- The browser does the merge, pushing with `If-Match` and starting over on a 409. Needs https or localhost for WebCrypto.

## Phase 5: web app parity

Agreed 2026-11-03. Done 2026-11-03 (steps 5a to 5f). The web app can open and edit a group someone shared, but can't create, share or manage them. Goal: everything the phone does, except what needs the phone. No server change: groups are blobs, so every step is client code in `server/index.html`. Each step ships on its own.

Decisions: a vendored QR generator (about 20 KB, MIT, pinned version, pasted inline), counters kept only in the browser ("Ungrouped" in `localStorage`, like the phone), and foldable sections for all groups like the phone.

- **5a. Many groups at once.**
  - State becomes one entry per group (`secret` → state, etag, keys, status) plus the local Ungrouped state in `localStorage`. All groups are fetched in parallel on load, on tab focus and every 15 minutes. Writes queue per group, so a slow group never blocks another.
  - Home: one sticky, foldable section per group ("Name · count"), Ungrouped first, empty sections hidden, a search opens every section. Folded sections are kept in `localStorage`. The switcher becomes a filter: All, Ungrouped, or one group (headers are dropped for a single group). The card shows the group name when there are at least two groups. Tags, search, sort and archived keep working inside each section.
  - Each section header shows that group's status ("Synced 12:30", or the error with a retry), instead of one global error line.
- **5b. Create, rename, forget, join.**
  - New group: asks for a name, makes a random 32-byte secret in the browser, and uploads the first blob with a create-only `PUT` (name and `nameUpdatedAt`). The host is the page's own server; the token is the one from the login form.
  - Rename: writes the new name with `nameUpdatedAt = now` into the blob, so the newest rename wins on the phone too.
  - Forget: confirms, offers the link first as a key backup, and drops the key from this browser (the blob stays).
  - Join: pasting a link reads the blob, shows "Join «name»?", and says "already joined" for a known key. A link to another server can't be fetched (no CORS), so the page says to open it on that server's page.
- **5c. Share.** A share icon on each synced group (like the phone) opens a dialog with the QR code, the link, Copy link, the browser's Share (when it exists), and the same warning as the phone. Opening a link in a new browser then works end to end. "Open in app" stays.
- **5d. Move a counter between groups.** The edit form gets a group picker (Ungrouped included). Moving writes the counter, with its events and `updatedAt = now`, into the destination first, then a tombstone with the same `updatedAt` and no events into the source. If the second write fails, the counter is in both and the newest copy wins on the next sync, as on the phone. Leaving a synced group asks first: "It will be removed for everyone else in that group."
- **5e. Import and export.** Export writes the phone's version 1 JSON (all groups, group name per counter), so a file moves between phone and web. Import has the same checks and the same choice: Replace swaps the groups of the same name for the file's, Keep both creates new groups renamed if the name is taken, with fresh uids and no NFC tag. New groups are uploaded with create-only `PUT`s.
- **5f. Settings.** A dark theme switch (follows the system until changed) and a debug switch that allows minutes in "Notify after", like the phone. Language and sort exist already.
- **Tests.** The pure functions (merge, move, link parsing, export/import, key derivation) sit between marked comments, and `server/web.test.mjs` loads that part and runs it with `node --test`. It shares test vectors with `SinceTest.kt` for link parsing and for a blob sealed in the browser. It isn't part of the Docker build, which stays Go only.
- **Docs.** README (English and Italian) and the screenshot get updated when the last step ships.

Can't be done on the web: NFC pairing (unpair stays), reminders and notifications (they fire on the phone), the widget, and groups that live on a different server than the page.

## Plan: format v2 and group access control

Agreed 2026-11-03, not started. Replaces the instance-wide `TOKEN`, which rides in share links, is one per server and can't be revoked. Each phase ships on its own.

**Phase 0: blob format v2.** One key per group for everything. Only sensitive text is encrypted, per field (AES-GCM, associated data = group id + record uid + field name): group name, counter `label`, `tags` and `nfcTagId`, event `note` and `deviceId`, device name. Readable by the server: counter `uid`, `updatedAt`, `deleted`, `archived`, `notifyAfterMs`; event `uid`, counter, `at`, `source`, `deleted`; device `id`. The document carries an integrity field: SHA-256 of a canonical byte form of the whole document (records sorted by uid, fixed field order, length-prefixed values, ciphertexts as stored), encrypted with the group key. Clients verify it on every read, so a server without the key can't edit anything unnoticed. It doesn't stop a rollback to an older valid document. Each field has a random nonce, so the "nothing new to say" check compares decrypted content, not bytes. Clients read v1 and v2 and write v2 (a `v` field), so this goes first and migration happens once. What the server can now see: counters, every reset time, reminder intervals, but not which device did a reset. Shared test vectors (`SinceTest.kt` and `web.test.mjs`) cover the canonical form, since both clients must produce identical bytes.

**Phase 1: server access control and stats** (Go, standard library only).
- Per group, in the existing metadata next to the blob: `devices[{id, tokenHash, created, lastSeen}]`, `invites[{nonceHash, expires}]`. Credentials are keyed by `deviceId`, never by the editable name. There is no admin role or password: every enrolled device can invite, revoke and list.
- Create: `PUT /s/{id}` without `If-Match` takes `{ciphertext, deviceId}` and returns `{etag, token}`. Sync: `GET`/`PUT /s/{id}` with the device's random token as Bearer. Only its hash is stored. `lastSeen` is written only if the stored value is more than 1 h old, so reads don't write to disk.
- `POST /s/{id}/invite` returns a single-use nonce valid for 24 h, and the link is `#k=…&n=…`. `POST /s/{id}/enroll` takes `{nonce, deviceId}` without a Bearer and returns a token. `POST /s/{id}/revoke {deviceId}` and `GET /s/{id}/devices` (ids, created, last seen) need a device token. `POST /s/{id}/leave` removes the caller. When no device is left, the group is deleted. `join` goes away.
- Legacy groups (no `devices`) keep working with `TOKEN`. The first client to sync one calls `POST /s/{id}/claim {deviceId}` with `TOKEN` and gets a device token. Other devices then need an invite. `TOKEN` becomes optional; without it, legacy groups can't be claimed.
- Group creation stays open, limited by caps: blob size, groups per IP per day (`CREATES_PER_IP_DAY`), total groups (`MAX_GROUPS`), and a per-IP backoff on 401s and failed enrolls. These counters live in memory (a map reset hourly) and aren't persisted.
- A daily cleanup deletes groups not synced for 365 days, or for 180 days with fewer than 3 live counters (counted from the readable `deleted` flags).
- `GET /stats` with `TOKEN` returns totals computed from the readable v2 fields: groups, devices, live counters, and resets in the last 7 and 30 days.
- Go tests: create, enroll (nonce spent, expired), revoke, claim, caps, cleanup.

**Phase 2: web.** Each group keeps its device token. A link with `n` enrolls the browser and spends the nonce. Group options get a device list (names come from the document), Invite (a fresh link and QR code) and revoke. The token login form and `&t=` go. Old groups claim themselves on the first sync. `web.test.mjs` covers link parsing.

**Phase 3: Android.** Same flows. The token lives in `sync_keys`, which backups skip. Share mints a fresh invite for the link and the QR code. The Groups screen gets a device list and revoke. The per-group token field goes.

**Phase 4: docs.** READMEs (English and Italian) and this file.

Limits that stay: a revoked device keeps the group secret and what it already fetched, so to cut out someone who shouldn't see the group at all, move to a new group. Any member can revoke any other member. An older client can't read v2.

## Known limits

- **Lost key:** if every phone holding a group's key is lost, its data is gone. "Copy link" is the backup of the key. Once access control ships, a backup link can't enroll on its own: rejoining needs an invite from a device that is still enrolled.
- **Revoking is partial:** a revoked device can't sync any more, but it keeps the key and what it already fetched. To cut someone off completely, create a new group and move the counters into it.
- **Rollback:** a server that serves an older blob can bring back a deleted counter. Events merge by union, so nothing else is lost.
- **Blob size** is visible to the server. There is no padding for now.
- **Web keys** live in the browser's `localStorage`: clearing site data loses them, and so does an Ungrouped counter that exists only there. "Copy link" is the backup for groups; Export is the backup for Ungrouped.

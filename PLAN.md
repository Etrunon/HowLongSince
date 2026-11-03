# Plan: groups and end-to-end encrypted sync

Agreed 2026-11-03. Each phase ships on its own.

## Goals

- Users create groups. The home screen shows all counters in collapsible sections, one per group. The drawer opens a page with one group's counters.
- Each group is synced on its own, or not at all. For example, a "Personal" group never leaves the phone while a "Family" group is synced.
- The sync server only stores encrypted data. The key is made on the phone and never sent to the server. One app can sync different groups to different servers.
- A group is shared with a link (or a QR code of it) that holds the server host and the key.

## Core design change

Today the server merges data and the web app reads plaintext. With end-to-end encryption the server can do neither, so:

- **Server:** a dumb blob store (the old `/sync` and its plaintext web app stay until Phase 4, which deletes them and `merge`/`prune`). `GET /s/{id}` returns `{etag, ciphertext}`. `PUT /s/{id}` takes `If-Match: <etag>` and returns 409 on a conflict. The ETag is a content hash, and each blob is one file. `merge`/`prune` in `main.go` are deleted.
- **Client sync:** GET, decrypt, merge (the existing `Dao.merge`, limited to one group), encrypt, PUT. On a 409, start again from the GET. Tombstone pruning (90 days) is done on the client.
- **Crypto:** each synced group has a random 32-byte secret. HKDF derives two values from it: the space id the server sees, and an AES-256-GCM key. The space id is passed as associated data, so the server can't swap blobs between spaces. Only `javax.crypto` and WebCrypto are used, no new dependencies.
- **What the server still sees:** an anonymous space id, blob size, timing and IP address.
- **`TOKEN`:** optional and instance-wide, to stop strangers from storing blobs on the server. When set, the share link carries it.

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
- The app has no QR scanner of its own: the phone's camera app opens the link.
- Joining asks "Join «name» on host?", then creates the group and runs the first sync.
- "Copy link" in the group settings doubles as the key backup.

## Phase 4: web app

- The web app decrypts in the browser with WebCrypto. The key comes from the URL fragment, which is never sent to the server.
- A JS port of the group-limited merge.
- Known groups (host, key, token) are kept in `localStorage`.

## Known limits

- **Lost key:** if every phone holding a group's key is lost, its data is gone. "Copy link" is the backup.
- **No revoking access:** anyone with the link has the key. To cut someone off, create a new group and move the counters into it.
- **Rollback:** a server that serves an older blob can bring back a deleted counter. Events merge by union, so nothing else is lost.
- **Blob size** is visible to the server. There is no padding for now.

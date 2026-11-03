# Plan: categories and end-to-end encrypted sync

Agreed 2026-11-03. Each phase ships on its own.

## Goals

- Users create categories. The home screen shows all counters in collapsible sections, one per category. The drawer opens a page with one category's counters.
- Each category is synced on its own, or not at all. For example, a "Personal" category never leaves the phone while a "Family" category is synced.
- The sync server only stores encrypted data. The key is made on the phone and never sent to the server. One app can sync different categories to different servers.
- A category is shared with a link (or a QR code of it) that holds the server host and the key.

## Core design change

Today the server merges data and the web app reads plaintext. With end-to-end encryption the server can do neither, so:

- **Server:** a dumb blob store. `GET /s/{id}` returns `{etag, ciphertext}`. `PUT /s/{id}` takes `If-Match: <etag>` and returns 409 on a conflict. The ETag is a content hash, and each blob is one file. `merge`/`prune` in `main.go` are deleted.
- **Client sync:** GET, decrypt, merge (the existing `Dao.merge`, limited to one category), encrypt, PUT. On a 409, start again from the GET. Tombstone pruning (90 days) is done on the client.
- **Crypto:** each synced category has a random 32-byte secret. HKDF derives two values from it: the space id the server sees, and an AES-256-GCM key. The space id is passed as associated data, so the server can't swap blobs between spaces. Only `javax.crypto` and WebCrypto are used, no new dependencies.
- **What the server still sees:** an anonymous space id, blob size, timing and IP address.
- **`TOKEN`:** optional and instance-wide, to stop strangers from storing blobs on the server. When set, the share link carries it.

## Phase 1: categories (local only)

- `Category(id, name, nameUpdatedAt, deleted)` table. A nullable `Counter.categoryId`, where null means "Uncategorized", which is never synced. Room v4 migration.
- **Home:** one `LazyColumn` with a sticky, foldable header per category. Which sections are folded is saved in prefs.
- **Drawer:** one entry per category. It opens the same list filtered to that category.
- **Category settings:** create, rename and delete. The counter edit dialog gets a category picker.
- **Import and "Delete all":** limited to one category. Without this they would delete shared data for every member.
- **Unaffected:** widget, reminders and NFC (they use local ids and counter uids).

## Phase 2: per-category encrypted sync

- `Category` gets nullable `syncUrl` and `syncKey` columns. Categories don't need a uid, because the blob *is* the category. The name is stored inside the blob and merged by `nameUpdatedAt`, newest wins.
- `sync()` runs once per synced category, and each category shows its own status. Local-only categories never touch the network.
- **Category-limited merge:** only rows in that category are matched by uid.
- **Moving a counter between categories** keeps its uid, so NFC tags keep working. When the outgoing blob is built, a counter that is in the remote blob but now sits in another local category with a newer `updatedAt` is written as a tombstone. The UI asks for confirmation first, because the move removes the counter for the other members.
- **Leaving a category or stopping its sync:** drop the key locally and tombstone nothing.
- **Backups:** keys are excluded through `dataExtractionRules` and `fullBackupContent`. The JSON export never contains keys.
- **Migration:** if the old global `syncUrl` is set, existing counters move into a new "Synced" category. The old global URL and token settings are removed, and the old `/sync` protocol is dropped (breaking change).

## Phase 3: share and join

- The category settings screen has a Share button that shows the link `https://host/#k=<base64url secret>[&t=<token>]` and a QR code of it.
- Opened in a browser, the link is the web app for that category. Its "Open in app" button launches `howlongsince://join?h=<host>#k=…`. This avoids App Links domain verification for self-hosted domains.
- The app has no QR scanner of its own: the phone's camera app opens the link.
- Joining asks "Join «name» on host?", then creates the category and runs the first sync.
- "Copy link" in the category settings doubles as the key backup.

## Phase 4: web app

- The web app decrypts in the browser with WebCrypto. The key comes from the URL fragment, which is never sent to the server.
- A JS port of the category-limited merge.
- Known categories (host, key, token) are kept in `localStorage`.

## Known limits

- **Lost key:** if every phone holding a category's key is lost, its data is gone. "Copy link" is the backup.
- **No revoking access:** anyone with the link has the key. To cut someone off, create a new category and move the counters into it.
- **Rollback:** a server that serves an older blob can bring back a deleted counter. Events merge by union, so nothing else is lost.
- **Blob size** is visible to the server. There is no padding for now.

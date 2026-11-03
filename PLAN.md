# Plan: format v2 and group access control

Agreed 2026-11-03, not started. Groups, encrypted sync, sharing and the web app (earlier phases 1–5) are done. Each phase below ships on its own.

## Goal

The server never sees a text a user typed (names, labels, tags, notes, NFC ids) or which device made which change. It may see counter metadata: how many counters, when they were reset, reminder intervals. Server access moves from the instance-wide `TOKEN` (it rides in share links, is one per server and can't be revoked) to per-device tokens with single-use invites.

## Phase 0: blob format v2 (done)

- One key per group, from the existing HKDF secret. Clients read v1 and v2, and write v2 (a `v` field). This ships first, so migration happens once.
- **Encrypted per field** (AES-GCM, random nonce, associated data = group id + record uid + field name): group name, counter `label`, `tags`, `nfcTagId`, event `note` and `deviceId`, device name.
- **Readable:** counter `uid`, `updatedAt`, `deleted`, `archived`, `notifyAfterMs`; event `uid`, counter, `at`, `source`, `deleted`; device `id`.
- **Integrity:** the client serializes the document once and sends it as a string, `{doc: "<json>", mac: seal(SHA-256(doc bytes))}`. The server stores and serves `doc` byte for byte, and may parse it but never re-serializes it. Readers check the MAC against the bytes they received. A server without the key can't edit anything unnoticed, and since the hash covers raw bytes, Kotlin and JS don't need a canonical form. It doesn't stop a rollback to an older valid document.
- Because nonces are random, the "nothing new to say" check compares decrypted content, not bytes.
- Tests: `SinceTest.kt` and `web.test.mjs` share one v2 document sealed by each side, and each opens the other's.

## Phase 1: server access control and stats (Go, standard library only)

- **Roles:** a device is an *admin* or a *member*. The creator's device is an admin. Only admins invite, revoke and list devices. Members just sync and leave. Story: Alice creates the group (admin), invites Bob and Carl as members, and can revoke either of them; they can't revoke her. When Alice gets a new phone, she opens her backup link ("Copy link") and types the recovery password, and the new phone becomes an admin that can revoke her lost one. This works even if she has lost every device.
- **Storage:** per group, in the blob's metadata file: `devices[{id, tokenHash, admin, created, lastSeen}]`, `invites[{nonceHash, expires}]`, `recoveryHash`. Credentials are keyed by `deviceId`, never by the editable name. The current `Devices` count becomes `len(devices)`; files that still have the count are treated as legacy groups.
- **Create:** `PUT /s/{id}` without `If-Match` takes `{doc, mac, deviceId, recovery}` and returns `{etag, token}`. If the group exists, it returns 409 as today.
- **Sync:** `GET`/`PUT /s/{id}` with the device token as Bearer. Only `sha256(token)` is stored, compared in constant time. `lastSeen`/`lastSynced` are written only when the stored value is more than 1 h old, so reads stop writing to disk (today every GET does).
- **Recovery password:** the creator picks it. The client sends `recovery = PBKDF2-SHA256(password, salt = group id, 600k iterations)` (javax.crypto and WebCrypto both have this), and the server stores `sha256(recovery)`. The password never leaves the device, and the server only ever sees a stretched value. An admin can change it with `POST /s/{id}/recovery {recovery}`.
- **Invite (admin):** `POST /s/{id}/invite` returns a single-use nonce valid for 24 h. Invited devices are members. The link is `#k=…&n=…`. Expired invites are pruned whenever the file is written.
- **Enroll:** `POST /s/{id}/enroll {deviceId, nonce | recovery}`, without a Bearer, returns a token. A nonce makes a member and is spent. A correct recovery makes an admin.
- **Promote:** `POST /s/{id}/promote {recovery}` with a member's Bearer makes that device an admin.
- **Guessing:** at most 5 failed recovery attempts per group per hour, on top of the per-IP backoff.
- **Revoke (admin):** `POST /s/{id}/revoke {deviceId}` removes any device, other admins included (Alice's lost phone). `GET /s/{id}/devices` (admin) lists id, role, created and last seen.
- **Leave:** `POST /s/{id}/leave` removes the caller. When no device is left, the group is deleted. `join` goes away.
- **Legacy groups** (no `devices`): `GET`/`PUT` keep working with `TOKEN`. A device holding `TOKEN` calls `POST /s/{id}/claim {deviceId}` and gets its own token. Claimed devices are members. The first one is asked to set a recovery password and promotes itself. Claim stops working once a recovery password is set, so after that `TOKEN` can't add anyone. Once every client has upgraded, the operator can unset `TOKEN`. `TOKEN` becomes optional; the `INSECURE` switch goes.
- **Caps:** group creation stays open, with limits on blob size (10 MB, as today), groups per IP per hour (`CREATES_PER_IP_HOUR`), total groups (`MAX_GROUPS`), and a per-IP backoff on 401s and failed enrolls. One in-memory map, reset hourly, not persisted.
- **Cleanup:** once a day, delete groups not synced for 365 days, or for 180 days with fewer than 3 live counters (counted from the readable `deleted` flags).
- **Stats:** `GET /stats` with `TOKEN` returns totals from the readable fields: groups, devices, live counters, and resets in the last 7 and 30 days.
- **Go tests:** create, enroll (spent and expired nonce), roles (a member can't invite, revoke or list), recovery (enroll and promote with the right and wrong password, attempt limit), claim (closed once a password is set), leave, caps, cleanup.

## Phase 2: web

- Each group keeps its device token in `localStorage`.
- A link with `n` enrolls the browser and spends the nonce.
- For admins, group options get a device list (names from the document, role shown), Invite and revoke. Members see none of these, only "Become admin" (asks for the recovery password).
- Create asks for a recovery password. Opening a link without `n` offers "Join with recovery password".
- The token login form and `&t=` go away. Old groups claim themselves on the first sync, using the `TOKEN` the browser already has.
- `web.test.mjs` covers link parsing.

## Phase 3: Android

- The same flows. The token lives in `sync_keys`, which backups skip.
- For admins, Share mints a fresh invite for the link and the QR code. Create, join and "Become admin" work as on the web.
- For admins, the Groups screen gets a device list and revoke. The per-group token field goes away.

## Phase 4: docs

READMEs (English and Italian) and this file.

## Known limits

- **What the server sees:** counter count, every reset time, reminder intervals, blob size, timing and IP. It doesn't see any text or which device reset what.
- **Recovery = link + password:** anyone who has both is an admin. Admins are equal, so an admin can revoke another admin. A forgotten password with no admin device left means nobody can invite or revoke again; members still sync, and the way back is a new group.
- **Weak passwords:** someone with the link can try passwords online, but only 5 per hour. The server operator can try them offline against `recoveryHash`, slowed by PBKDF2.
- **Revoking is partial:** a revoked device can't sync any more, but it keeps the key and what it already fetched. To cut someone off completely, create a new group and move the counters into it.
- **Rollback:** a server that serves an older valid document can bring back a deleted counter. Events merge by union, so nothing else is lost.
- **Old clients** can't read v2.
- **Web keys** live in `localStorage`: clearing site data loses them, along with any Ungrouped counter that exists only there. "Copy link" backs up groups; Export backs up Ungrouped.

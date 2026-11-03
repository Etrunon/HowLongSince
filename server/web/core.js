// Pure logic (no page, no network): tests load this file, see web.test.mjs.
const b64u = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
const b64 = u => { let s = ''; for (const b of u) s += String.fromCharCode(b); return btoa(s); };
const hex = u => [...u].map(b => b.toString(16).padStart(2, '0')).join('');
const validSecret = s => { try { return b64u(s).length === 32; } catch { return false; } };

// Same scheme as the app (Crypto.kt): HKDF-SHA256 with a zero salt makes the server-side id and the AES-256-GCM key; the id is also the authenticated data.
const enc = new TextEncoder(), dec = new TextDecoder();
async function deriveKeys(secret) {
  const ikm = await crypto.subtle.importKey('raw', b64u(secret), 'HKDF', false, ['deriveBits']);
  const bits = (info, n) => crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info: enc.encode(info) }, ikm, n);
  return { id: hex(new Uint8Array(await bits('hls id', 128))), key: await crypto.subtle.importKey('raw', await bits('hls key', 256), 'AES-GCM', false, ['encrypt', 'decrypt']) };
}
async function sealBytes(k, bytes, ad = enc.encode(k.id)) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  return Uint8Array.from([...iv, ...new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: ad }, k.key, bytes))]);
}
const openBytes = async (k, blob, ad = enc.encode(k.id)) => new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: blob.slice(0, 12), additionalData: ad }, k.key, blob.slice(12)));
// Format v4: typed text is sealed per field (authenticated with group id, record uid and field), the rest stays readable for the server's stats.
// Each counter carries its own events (identity: counter id + `at`, no event ids). A note's uid is `<counter id>@<at>`.
// Device ids are sealed once for the whole doc as `who` ({d: distinct ids, i: per event index into d or -1, in doc order}), not per event: random bytes don't compress.
// The envelope is {v: 4, doc: '<json>', mac}; the mac seals SHA-256 of the exact doc bytes, so nobody without the key can edit anything.
const SEALED = { counters: ['label', 'tags', 'nfcTagId'], devices: ['name'] };
const fieldAd = (k, uid, field) => enc.encode(`${k.id}\0${uid}\0${field}`);
const sha256 = async b => new Uint8Array(await crypto.subtle.digest('SHA-256', b));
async function mapFields(k, o, f) {
  const jobs = [];
  if (o.name != null) jobs.push(f(o.name, fieldAd(k, '', 'name')).then(v => o.name = v));
  for (const [list, fields] of Object.entries(SEALED)) for (const r of o[list] ?? []) for (const fl of fields)
    if (r[fl] != null) jobs.push(f(r[fl], fieldAd(k, r.id, fl)).then(v => r[fl] = v));
  for (const c of o.counters ?? []) for (const x of c.events) if (x.note != null) jobs.push(f(x.note, fieldAd(k, `${c.id}@${x.at}`, 'note')).then(v => x.note = v));
  await Promise.all(jobs);
  return o;
}
async function seal(k, state) {
  const byC = new Map((state.counters ?? []).map(c => [c.id, new Map()]));
  for (const x of state.events ?? []) { // same counter + at is one event: deleted if either is, with a note if either has one
    const m = byC.get(x.counterId), o = m?.get(x.at);
    if (m) m.set(x.at, o ? { ...x, deleted: x.deleted || o.deleted, note: x.note ?? o.note } : x);
  }
  const d = [], i = [];
  const counters = [...state.counters].sort((a, b) => a.id < b.id ? -1 : a.id > b.id ? 1 : 0).map(c => ({ ...c, events: [...byC.get(c.id).values()].sort((a, b) => a.at - b.at).map(({ id, counterId, deviceId, note, ...x }) => {
    i.push(deviceId == null ? -1 : (d.includes(deviceId) || d.push(deviceId), d.indexOf(deviceId)));
    return note == null ? x : { ...x, note };
  }) }));
  const { events, ...rest } = state;
  const o = await mapFields(k, structuredClone({ ...rest, counters }), async (v, ad) => b64(await sealBytes(k, enc.encode(v), ad)));
  o.who = b64(await sealBytes(k, enc.encode(JSON.stringify({ d, i })), fieldAd(k, '', 'who')));
  const doc = JSON.stringify(o);
  return enc.encode(JSON.stringify({ v: 4, doc, mac: b64(await sealBytes(k, await sha256(enc.encode(doc)))) }));
}
/** The recovery password as the server gets it: PBKDF2-SHA256 salted with the group id (same as the app), so the password never leaves the device. */
async function stretch(password, id) {
  const k = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  return b64(new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: enc.encode(id), iterations: 600000 }, k, 256)));
}
/** A group link, whole (web or howlongsince:) or just its `k=...` part: { server (null: this one), secret, nonce, token (legacy) }, or null. */
function parseLink(link) {
  link = link.trim();
  let server = null;
  if (link.startsWith('howlongsince:')) { try { server = new URL(link).searchParams.get('h'); } catch { return null; } if (!server) return null; }
  else if (/^https?:/.test(link)) server = link.split('#')[0];
  const p = new URLSearchParams(link.includes('#') ? link.split('#')[1] : link), secret = p.get('k');
  if (!secret || !validSecret(secret)) return null;
  return { server: server && server.replace(/\/$/, ''), secret, nonce: p.get('n'), token: p.get('t') };
}
/** A v4 blob to the group state; throws an OperationError if anything was altered. */
async function unseal(k, blob) {
  let env; try { env = JSON.parse(dec.decode(blob)); } catch {}
  if (env?.v !== 4) throw new Error('unsupported format');
  if (b64(await openBytes(k, b64u(env.mac))) !== b64(await sha256(enc.encode(env.doc)))) throw new DOMException('tampered', 'OperationError');
  const o = await mapFields(k, JSON.parse(env.doc), async (v, ad) => dec.decode(await openBytes(k, b64u(v), ad)));
  const { d, i } = JSON.parse(dec.decode(await openBytes(k, b64u(o.who), fieldAd(k, '', 'who'))));
  delete o.who;
  o.events = o.counters.flatMap(c => { const evs = c.events; delete c.events; return evs.map(x => ({ id: `${c.id}@${x.at}`, counterId: c.id, ...x })); });
  if (i.length !== o.events.length) throw new DOMException('tampered', 'OperationError');
  o.events.forEach((x, n) => { if (i[n] >= 0) x.deviceId = d[i[n]]; });
  return o;
}
const UNITS = [['minutes', 6e4], ['hours', 36e5], ['days', 864e5], ['weeks', 6048e5]];
/** Like the phone: minutes only in debug mode, or if this counter already uses them (else it couldn't be shown). */
const unitsFor = (ms, debug) => debug || (ms ?? 0) % 36e5 !== 0 ? UNITS : UNITS.slice(1);
const tagList = s => [...new Set(s.split(',').map(t => t.trim().toLowerCase()).filter(Boolean))]; // same as the app
const uniqueName = (name, taken) => { let n = name, i = 1; while (taken.some(x => x.toLowerCase() === n.toLowerCase())) n = `${name} (${++i})`; return n; };

// The server can't merge encrypted data, so the browser does: counters by newest updatedAt, events by union (deletion is sticky), old tombstones dropped.
// `inn.drop` lists counters whose existing events go (they moved to another group, or an import replaces them).
function merge(s, inn) {
  const cs = new Map();
  for (const c of [...s.counters, ...inn.counters]) { const o = cs.get(c.id); if (!o || c.updatedAt > o.updatedAt) cs.set(c.id, c); }
  const ds = new Map(); // device names: newest rename wins
  for (const d of [...(s.devices ?? []), ...(inn.devices ?? [])]) { const o = ds.get(d.id); if (!o || d.updatedAt > o.updatedAt) ds.set(d.id, d); }
  const dropped = new Set(inn.drop ?? []), es = new Map();
  for (let e of [...s.events.filter(e => !dropped.has(e.counterId)), ...inn.events]) {
    const o = es.get(e.id);
    if (o) { e = { ...e, deleted: e.deleted || o.deleted }; if (!e.note && o.note) e.note = o.note; }
    es.set(e.id, e);
  }
  const cutoff = Date.now() - 90 * 864e5, byId = (a, b) => a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  const counters = [...cs.values()].filter(c => !c.deleted || c.updatedAt >= cutoff).sort(byId);
  const live = new Set(counters.map(c => c.id));
  const rename = inn.name != null && inn.nameUpdatedAt > (s.nameUpdatedAt ?? 0); // newest rename wins, like the phone
  return { name: rename ? inn.name : s.name, nameUpdatedAt: rename ? inn.nameUpdatedAt : s.nameUpdatedAt, devices: [...ds.values()].sort(byId), counters, events: [...es.values()].filter(e => live.has(e.counterId)).sort(byId) };
}

/** The phone's export file (version 1) for parts = [{ group: name or null, counters, events }]. */
function toBackup(parts, now = new Date()) {
  let n = 0;
  const counters = parts.flatMap(({ group, counters, events }) => counters.filter(c => !c.deleted).flatMap(c => {
    const ev = events.filter(e => e.counterId === c.id && !e.deleted).sort((a, b) => a.at - b.at);
    if (!ev.length) return []; // the phone refuses a counter without events
    return [{ id: ++n, label: c.label, tags: tagList(c.tags), notifyAfterMs: c.notifyAfterMs ?? null, nfcTagId: c.nfcTagId ?? null, uid: c.id,
      updatedAt: c.updatedAt, archived: !!c.archived, group: group ?? null,
      events: ev.map(e => ({ at: e.at, time: new Date(e.at).toISOString(), source: e.source, uid: e.id, ...(e.note && { note: e.note }) })) }];
  }));
  return { version: 1, exportedAt: now.toISOString(), counters };
}

/** Reads and checks an export file; throws a readable message on anything wrong. Items are { group, counter, events } with web-shaped rows. */
function parseBackup(text) {
  const root = JSON.parse(text);
  if (root.version !== 1) throw new Error('Unsupported file version');
  if (!Array.isArray(root.counters)) throw new Error('No counters in the file');
  const ids = new Set();
  return root.counters.map((o, i) => {
    const label = String(o.label ?? '').trim();
    if (!label) throw new Error(`Counter #${i + 1} has no label`);
    if (ids.has(o.id)) throw new Error('Duplicate counter ids');
    ids.add(o.id);
    if (!Array.isArray(o.events) || !o.events.length) throw new Error(`"${label}" has no events`);
    const uid = o.uid || crypto.randomUUID(); // older exports have none
    const counter = { id: uid, label, tags: tagList(Array.isArray(o.tags) ? o.tags.join(',') : String(o.tags ?? '')).join(','), notifyAfterMs: o.notifyAfterMs ?? null,
      nfcTagId: o.nfcTagId ?? null, updatedAt: o.updatedAt ?? Date.now(), deleted: false, archived: !!o.archived };
    const events = o.events.map(e => {
      if (typeof e.at !== 'number') throw new Error(`"${label}" has an event without a time`);
      const at = Math.floor(e.at / 1000) * 1000;
      return { id: `${uid}@${at}`, counterId: uid, at, source: e.source || 'manual', deleted: false, ...(e.note && { note: e.note }) };
    });
    return { group: typeof o.group === 'string' && o.group.trim() ? o.group.trim() : null, counter, events };
  });
}

/**
 * Where an import goes. replace: each file group replaces the local group of the same name (made if missing; no group = Ungrouped).
 * Otherwise every file group becomes a new group, renamed if the name is taken, with fresh ids so it never merges with the originals.
 * Returns [{ name (null = Ungrouped), isNew, items }].
 */
function planImport(items, replace, groupNames, fallbackName = 'Imported') {
  const byGroup = new Map();
  for (const it of items) byGroup.set(it.group, [...(byGroup.get(it.group) ?? []), it]);
  const taken = [...groupNames], out = [];
  const fresh = ({ counter, events, group }) => { const id = crypto.randomUUID();
    return { group, counter: { ...counter, id, nfcTagId: null }, events: events.map(e => ({ ...e, id: `${id}@${e.at}`, counterId: id })) }; };
  for (const [name, list] of byGroup) {
    if (replace && name === null) { out.push({ name: null, isNew: false, items: list }); continue; }
    const old = replace ? groupNames.find(n => n.toLowerCase() === name.toLowerCase()) : undefined;
    if (old !== undefined) { out.push({ name: old, isNew: false, items: list }); continue; }
    const made = uniqueName(name ?? fallbackName, taken);
    taken.push(made);
    out.push({ name: made, isNew: true, items: replace ? list : list.map(fresh) });
  }
  return out;
}

// Runs the page's pure logic (the part of index.html between "// <core>" and "// </core>"): node --test server/
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8');
const core = html.slice(html.indexOf('// <core>'), html.indexOf('// </core>'));
const { UNITS, unitsFor, tagList, uniqueName, merge, toBackup, parseBackup, planImport, deriveKeys, seal, unseal, sealBytes, b64, b64u, stretch, parseLink } =
  new Function(core + '; return { UNITS, unitsFor, tagList, uniqueName, merge, toBackup, parseBackup, planImport, deriveKeys, seal, unseal, sealBytes, b64, b64u, stretch, parseLink };')();

const c = (id, label, updatedAt = 1, extra = {}) => ({ id, label, tags: '', notifyAfterMs: null, nfcTagId: null, updatedAt, deleted: false, archived: false, ...extra });
const e = (id, counterId, at = 1, extra = {}) => ({ id, counterId, at, source: 'manual', deleted: false, ...extra });
const empty = { name: null, nameUpdatedAt: 0, counters: [], events: [] };

// The same file as in SinceTest.kt (BackupTest.fixture): what the phone writes.
const FIXTURE = `{"version":1,"exportedAt":"2026-11-03T10:00:00Z","counters":[
{"id":1,"label":"Water plants","tags":["home","plants"],"notifyAfterMs":259200000,"nfcTagId":null,"uid":"11111111-1111-4111-8111-111111111111","updatedAt":1700000000000,"archived":false,"group":"Home","events":[{"at":1700000000000,"time":"2023-11-14T22:13:20Z","source":"create","uid":"e1"},{"at":1700003600000,"time":"2023-11-14T23:13:20Z","source":"manual","uid":"e2","note":"new filter"}]},
{"id":2,"label":"Run","tags":[],"notifyAfterMs":null,"nfcTagId":"04ab","uid":"22222222-2222-4222-8222-222222222222","updatedAt":1700000000000,"archived":true,"group":null,"events":[{"at":1700000000000,"time":"2023-11-14T22:13:20Z","source":"create","uid":"e3"}]}]}`;

test('tags and names', () => {
  assert.deepEqual(tagList(' Home, health ,home,, '), ['home', 'health']);
  assert.equal(uniqueName('Family', ['Home']), 'Family');
  assert.equal(uniqueName('Family', ['family', 'Family (2)']), 'Family (3)');
});

test('merge: newest edit wins, events union, deletion sticks, notes kept', () => {
  const s = { ...empty, counters: [c('a', 'old', 1)], events: [e('e1', 'a', 1, { deleted: true }), e('e2', 'a', 2, { note: 'hi' })] };
  const out = merge(s, { counters: [c('a', 'new', 2), c('b', 'b')], events: [e('e1', 'a'), e('e2', 'a', 2), e('e3', 'b')] });
  assert.equal(out.counters.find(x => x.id === 'a').label, 'new');
  assert.equal(out.events.length, 3);
  assert.equal(out.events.find(x => x.id === 'e1').deleted, true);
  assert.equal(out.events.find(x => x.id === 'e2').note, 'hi');
  assert.equal(merge(out, { counters: [c('a', 'older', 1)], events: [] }).counters[0].label, 'new');
  assert.deepEqual(merge(out, empty), out); // merging nothing changes nothing
});

test('merge: renames, drops and old tombstones', () => {
  const s = { ...empty, name: 'A', nameUpdatedAt: 5, counters: [c('a', 'a'), c('old', 'x', 1, { deleted: true })], events: [e('e1', 'a'), e('e2', 'old')] };
  assert.equal(merge(s, { counters: [], events: [], name: 'B', nameUpdatedAt: 4 }).name, 'A');
  assert.equal(merge(s, { counters: [], events: [], name: 'B', nameUpdatedAt: 6 }).name, 'B');
  const out = merge(s, { counters: [], events: [], drop: ['a'] });
  assert.equal(out.events.length, 0); // 'old' is a tombstone from 1970: gone with its events; a's events dropped
  assert.deepEqual(out.counters.map(x => x.id), ['a']);
  const re = merge(s, { counters: [c('a', 'a', 9)], events: [e('e9', 'a')], drop: ['a'] }); // replaced: old events go, imported stay
  assert.deepEqual(re.events.map(x => x.id), ['e9']);
});

test('merge: the newest device rename wins, old events keep the id', () => {
  const s = { ...empty, devices: [{ id: 'd1', name: 'Old', updatedAt: 1 }], events: [e('e1', 'a', 1, { deviceId: 'd1' })] };
  const out = merge(s, { counters: [], events: [], devices: [{ id: 'd1', name: 'New', updatedAt: 2 }, { id: 'd2', name: 'Other', updatedAt: 1 }] });
  assert.deepEqual(out.devices.map(d => d.name), ['New', 'Other']);
  assert.equal(merge(out, { counters: [], events: [], devices: [{ id: 'd1', name: 'Stale', updatedAt: 1 }] }).devices[0].name, 'New');
});

test('reads what the phone writes', () => {
  const items = parseBackup(FIXTURE);
  assert.deepEqual(items.map(i => [i.counter.label, i.group, i.events.length]), [['Water plants', 'Home', 2], ['Run', null, 1]]);
  assert.equal(items[0].counter.tags, 'home,plants');
  assert.equal(items[0].counter.notifyAfterMs, 259200000);
  assert.equal(items[0].events[1].note, 'new filter');
  assert.equal(items[1].counter.nfcTagId, '04ab');
  assert.equal(items[1].counter.archived, true);
});

test('export then import gives the same data', () => {
  const items = parseBackup(FIXTURE);
  const parts = [{ group: null, counters: [items[1].counter], events: items[1].events }, { group: 'Home', counters: [items[0].counter], events: items[0].events }];
  const back = parseBackup(JSON.stringify(toBackup(parts)));
  assert.deepEqual(back.map(i => i.counter), items.map(i => i.counter).reverse());
  assert.deepEqual(back.map(i => i.group), [null, 'Home']);
  assert.deepEqual(back.flatMap(i => i.events), [...items[1].events, ...items[0].events]);
  assert.equal(toBackup([{ group: 'G', counters: [c('z', 'no events')], events: [] }]).counters.length, 0); // the phone would refuse it
});

test('bad files are refused', () => {
  const fails = s => assert.throws(() => parseBackup(s));
  fails('not json'); fails('{"version":2,"counters":[]}'); fails('{"version":1}');
  fails('{"version":1,"counters":[{"id":1,"label":"x","events":[]}]}');
  fails('{"version":1,"counters":[{"id":1,"label":" ","events":[{"at":1}]}]}');
  const ok = '{"id":1,"label":"x","events":[{"at":1}]}';
  fails(`{"version":1,"counters":[${ok},${ok}]}`);
});

test('import plans', () => {
  const items = parseBackup(FIXTURE);
  const replace = planImport(items, true, ['home', 'Work']);
  assert.deepEqual(replace.map(p => [p.name, p.isNew]), [['home', false], [null, false]]); // same name, any case; no group = Ungrouped
  assert.equal(replace[0].items[0].counter.id, items[0].counter.id); // ids kept, so it replaces
  assert.deepEqual(planImport(items, true, ['Work']).map(p => [p.name, p.isNew]), [['Home', true], [null, false]]); // missing group is made
  const keep = planImport(items, false, ['Home'], 'Imported');
  assert.deepEqual(keep.map(p => [p.name, p.isNew]), [['Home (2)', true], ['Imported', true]]);
  assert.notEqual(keep[0].items[0].counter.id, items[0].counter.id); // fresh ids: never merges with the originals
  assert.equal(keep[1].items[0].counter.nfcTagId, null);
  assert.equal(keep[0].items[0].events[1].counterId, keep[0].items[0].counter.id);
});

test('minutes only in debug mode, or when the counter already uses them', () => {
  const names = u => u.map(([n]) => n).join();
  assert.equal(names(unitsFor(null, false)), 'hours,days,weeks');
  assert.equal(names(unitsFor(3 * 36e5, false)), 'hours,days,weeks');
  assert.equal(names(unitsFor(null, true)), 'minutes,hours,days,weeks');
  assert.equal(names(unitsFor(90 * 6e4, false)), 'minutes,hours,days,weeks'); // 90 minutes can't be shown in hours
});

// Format v2. KOTLIN_V2 was sealed by the phone (SinceTest.kt, DocV2Test) with the same secret.
const SECRET = 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8';
const STRETCHED = 'PHwhiIx1cylSWJVPIVMmYTP3aZR2NDTPX7TPSB8+mWo='; // PBKDF2-SHA256, 600k, by node:crypto
const KOTLIN_V2 = 'eyJ2IjoyLCJkb2MiOiJ7XCJjb3VudGVyc1wiOlt7XCJuZmNUYWdJZFwiOm51bGwsXCJhcmNoaXZlZFwiOmZhbHNlLFwibm90aWZ5QWZ0ZXJNc1wiOm51bGwsXCJkZWxldGVkXCI6ZmFsc2UsXCJpZFwiOlwiYzFcIixcImxhYmVsXCI6XCJzc0tEWVc1R3E2SkF3L1dmRjZFMzBlZTY2RVNQT2FibmpFWXJtdEdpOXFaZkM4SVFSWHpsbGc9PVwiLFwidGFnc1wiOlwiQyt4cEJSYm42c05lK0F5OXllNE1lVi9EUldYOWJwdk0wZFhwcGpQREd0eWdPUT09XCIsXCJ1cGRhdGVkQXRcIjo1fV0sXCJkZXZpY2VzXCI6W3tcIm5hbWVcIjpcInMrU2ZIVHVVNHM5QzZtUGVId1VnNFg2YXBQZi84amJSSW5TQ3J6M0xaRXdlK2g0PVwiLFwiaWRcIjpcImQxXCIsXCJ1cGRhdGVkQXRcIjoxfV0sXCJuYW1lXCI6XCJQNXpMZng0b2FNaGM1OUdhSC9laHlPRnB2SVV6REdjZW9nV1NDbTJ5MTVVNHVhN1NcIixcIm5hbWVVcGRhdGVkQXRcIjo3LFwiZXZlbnRzXCI6W3tcIm5vdGVcIjpcIml0Z2tNY1BIb3ZERGFDMjVEanhuK28xOGk1aGlmZFp3RnRRbXhZVmRzVTNTcnFYWXg3RT1cIixcImF0XCI6NSxcImRlbGV0ZWRcIjpmYWxzZSxcImlkXCI6XCJlMVwiLFwic291cmNlXCI6XCJtYW51YWxcIixcImRldmljZUlkXCI6XCIyTGpHSEJRYmc5a2lrV0F0R0F0WExwTFBac1BQL0RwZ0NJU056Y21CXCIsXCJjb3VudGVySWRcIjpcImMxXCJ9XX0iLCJtYWMiOiJpUVRweGErNUtmaGxKS1ZtS25iWmlmb0ppZUhyWnlrSkx1anJnT3YvYVI4b04vWm9relkzdndrUTdrMGNpSTBNVjk5bWNrL0V0Y3ltRWFUSiJ9';
const ST = { name: 'Família', nameUpdatedAt: 7, counters: [c('c1', 'Water plants', 5, { tags: 'garden' })],
  events: [e('e1', 'c1', 5, { note: 'new filter', deviceId: 'd1' })], devices: [{ id: 'd1', name: 'Pixel 9', updatedAt: 1 }] };

test('v2: round trip hides typed text, keeps metadata readable', async () => {
  const k = await deriveKeys(SECRET), blob = await seal(k, ST), text = new TextDecoder().decode(blob);
  assert.deepEqual(await unseal(k, blob), ST);
  for (const s of ['Water plants', 'garden', 'new filter', 'Pixel 9', 'Família']) assert.ok(!text.includes(s), s);
  assert.ok(text.includes('updatedAt'));
});

test('v2: reads v1, refuses edits', async () => {
  const k = await deriveKeys(SECRET);
  assert.deepEqual(await unseal(k, await sealBytes(k, new TextEncoder().encode(JSON.stringify(ST)))), ST);
  const env = JSON.parse(new TextDecoder().decode(await seal(k, ST)));
  env.doc = env.doc.replace('"at":5', '"at":6');
  await assert.rejects(unseal(k, new TextEncoder().encode(JSON.stringify(env))));
});

test('v2: opens what the phone sealed', async () => {
  const s = await unseal(await deriveKeys(SECRET), b64u(KOTLIN_V2));
  assert.deepEqual([s.name, s.counters[0].label, s.counters[0].tags, s.events[0].note, s.events[0].deviceId, s.devices[0].name],
    ['Família', 'Water plants', 'garden', 'new filter', 'd1', 'Pixel 9']);
});

test('links: invite, backup, legacy token, app form', () => {
  assert.deepEqual(parseLink(`https://hls.example.com/sub/#k=${SECRET}&n=abc`), { server: 'https://hls.example.com/sub', secret: SECRET, nonce: 'abc', token: null });
  assert.deepEqual(parseLink(`k=${SECRET}`), { server: null, secret: SECRET, nonce: null, token: null });
  assert.equal(parseLink(`https://x.example/#k=${SECRET}&t=old`).token, 'old');
  assert.equal(parseLink(`howlongsince://join?h=https%3A%2F%2Fhls.example.com%2Fsub%2F#k=${SECRET}&n=abc`).server, 'https://hls.example.com/sub');
  for (const bad of ['', 'hello', 'https://x.example/', 'https://x.example/#k=abc', `howlongsince://join#k=${SECRET}`]) assert.equal(parseLink(bad), null, bad);
});

test('recovery password stretch (same vector as SinceTest.kt)', async () => {
  assert.equal(await stretch('correct horse', '972a5ac5604d28f549b06e0ee52d7e3a'), STRETCHED);
});

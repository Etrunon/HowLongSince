// Runs the page's pure logic (the part of index.html between "// <core>" and "// </core>"): node --test server/
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8');
const core = html.slice(html.indexOf('// <core>'), html.indexOf('// </core>'));
const { tagList, uniqueName, merge, toBackup, parseBackup, planImport } =
  new Function(core + '; return { tagList, uniqueName, merge, toBackup, parseBackup, planImport };')();

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

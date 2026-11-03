const $ = s => document.querySelector(s);
const esc = s => s.replace(/[&<>"]/g, c => `&#${c.charCodeAt(0)};`);

// English and Italian, picked from the browser; same wording as the app's strings.xml.
const T = {
  en: { login: 'Log in', new_counter: 'New counter', undo: 'Undo', search: 'Search labels and tags',
    sort_due: 'Most overdue', sort_recent: 'Recently reset', sort_name: 'Name',
    unreachable: 'Server unreachable, last change not saved. Retrying every minute.', sync_failed: 'Sync failed: %s',
    label: 'Label', tags: 'Tags', comma: 'comma separated', notify_after: 'Notify after', never: 'never',
    minutes: 'minutes', hours: 'hours', days: 'days', weeks: 'weeks', use_average: 'Use my average (%s)',
    reset_note: 'Reset with a note, or at a past time', note: 'note (optional)', reset: 'Reset',
    nfc_paired: 'NFC tag paired', unpair: 'Unpair', save: 'Save', archive: 'Archive', unarchive: 'Unarchive',
    intervals: 'Intervals · avg %s', archived: 'Archived', delete_counter: 'Delete counter', confirm_delete: 'Delete this counter?',
    reset_label: 'Reset %s', pick_past: 'Pick a time in the past', create: 'created', manual: 'manual', nfc: 'via NFC', d: 'd',
    open_app: 'Open in app', forget: 'Forget', paste_link: 'Paste a group link', bad_link: 'That isn\'t a group link',
        no_data: 'Nothing found for this link on this server', decrypt_failed: 'Can\'t decrypt: wrong key?', needs_https: 'This page needs https to decrypt (or localhost).', busy: 'Busy, try again', all_groups: 'All', ungrouped: 'Ungrouped', synced: 'Synced %s', group: 'Group', move_title: 'Move out of a synced group?', move_text: 'The counter will be removed for everyone else in the group it leaves.', new_group: 'New group', group_name: 'Group name', rename: 'Rename', join: 'Join', join_question: 'Join «%s»?',
    already_joined: 'Already joined as «%s»', other_server: 'That link is for %s. Open it on that server\'s page.', token: 'Token', share: 'Share', copy_link: 'Copy link', link_copied: 'Copied', close: 'Close', new_: 'New', settings: 'Settings', language: 'Language', menu: 'Menu', dark_theme: 'Dark theme', debug_mode: 'Debug mode', debug_hint: 'Allow "notify after" in minutes', device_name: 'Device name', device_name_hint: 'Shown next to the resets you log, on every device in a synced group', export_data: 'Export data', import_data: 'Import data', replace: 'Replace', keep_both: 'Keep both', cancel: 'Cancel', imported_name: 'Imported', cant_read: 'Can\'t read file: %s', done_imported: 'Imported %s counters',
    import_q: 'The file has %s counters. Replace: groups with the same name are deleted and replaced by the file\'s. Keep both: they are imported as new groups, renamed if a name is taken. Replacing can\'t be undone.',
    share_warning: 'A single-use invite, valid for 24 hours. Whoever joins with it can read and change this group until an admin revokes them; they keep what they already saw.',
    forget_backup: 'Copy this link first: with the recovery password it is the backup of the key. OK forgets the group in this browser (the data stays on the server), Cancel keeps it.',
    devices: 'Devices', change_password: 'Change recovery password', become_admin: 'Become admin', revoke: 'Revoke', admin: 'admin', member: 'member', this_device: 'this device',
    last_seen: 'last seen %s', confirm_revoke: 'Revoke «%s»? It stops syncing this group, but keeps what it already has.',
    recovery_new: 'Recovery password for «%s». With the group link, it lets you rejoin as an admin (new phone, lost devices). Keep it safe: it can\'t be reset.',
    recovery_join: 'This link has no invite. Recovery password, to join as an admin:', recovery_ask: 'Recovery password:',
    set_recovery: '«%s» has no recovery password yet. Set one now to become its admin (Cancel: ask again later)', wrong_password: 'Wrong password, or too many attempts: try later', pw_short: 'The recovery password needs at least 6 characters',
    join_here: 'Join this group in this browser? Cancel opens the phone app instead (the invite works once).',
    need_invite: 'No access: ask an admin for an invite', no_access: 'This device was removed from the group: ask an admin for an invite',
    share_backup: 'Only admins can invite. This link is the backup of the key: joining with it needs the recovery password.' },
  it: { login: 'Accedi', new_counter: 'Nuovo contatore', undo: 'Annulla', search: 'Cerca tra etichette e tag',
    sort_due: 'Più in ritardo', sort_recent: 'Azzerati di recente', sort_name: 'Nome',
    unreachable: 'Server irraggiungibile, ultima modifica non salvata. Nuovo tentativo ogni minuto.', sync_failed: 'Sincronizzazione non riuscita: %s',
    label: 'Etichetta', tags: 'Tag', comma: 'separati da virgole', notify_after: 'Avvisa dopo', never: 'mai',
    minutes: 'minuti', hours: 'ore', days: 'giorni', weeks: 'settimane', use_average: 'Usa la mia media (%s)',
    reset_note: 'Azzera con una nota, o in un momento passato', note: 'nota (facoltativa)', reset: 'Azzera',
    nfc_paired: 'Tag NFC abbinato', unpair: 'Disabbina', save: 'Salva', archive: 'Archivia', unarchive: 'Ripristina',
    intervals: 'Intervalli · media %s', archived: 'Archiviati', delete_counter: 'Elimina contatore', confirm_delete: 'Eliminare questo contatore?',
    reset_label: 'Azzera %s', pick_past: 'Scegli un momento nel passato', create: 'creato', manual: 'manuale', nfc: 'via NFC', d: 'g',
    open_app: 'Apri nell\'app', forget: 'Dimentica', paste_link: 'Incolla un link di gruppo', bad_link: 'Questo non è un link di gruppo',
        no_data: 'Niente trovato per questo link su questo server', decrypt_failed: 'Impossibile decifrare: chiave sbagliata?', needs_https: 'Questa pagina richiede https per decifrare (o localhost).', busy: 'Occupato, riprova', all_groups: 'Tutti', ungrouped: 'Senza gruppo', synced: 'Sincronizzato alle %s', group: 'Gruppo', move_title: 'Spostare da un gruppo sincronizzato?', move_text: 'Il contatore verrà rimosso per tutti gli altri nel gruppo che lascia.', new_group: 'Nuovo gruppo', group_name: 'Nome del gruppo', rename: 'Rinomina', join: 'Unisciti', join_question: 'Unirti a «%s»?',
    already_joined: 'Già unito come «%s»', other_server: 'Quel link è per %s. Aprilo sulla pagina di quel server.', token: 'Token', share: 'Condividi', copy_link: 'Copia link', link_copied: 'Copiato', close: 'Chiudi', new_: 'Nuovo', settings: 'Impostazioni', language: 'Lingua', menu: 'Menu', dark_theme: 'Tema scuro', debug_mode: 'Modalità debug', debug_hint: 'Consenti "avvisa dopo" in minuti', device_name: 'Nome del dispositivo', device_name_hint: 'Mostrato accanto agli azzeramenti che registri, su ogni dispositivo di un gruppo sincronizzato', export_data: 'Esporta dati', import_data: 'Importa dati', replace: 'Sostituisci', keep_both: 'Tieni entrambi', cancel: 'Annulla', imported_name: 'Importati', cant_read: 'Impossibile leggere il file: %s', done_imported: 'Importati %s contatori',
    import_q: 'Il file contiene %s contatori. Sostituisci: i gruppi con lo stesso nome vengono eliminati e sostituiti da quelli del file. Tieni entrambi: vengono importati come nuovi gruppi, rinominati se un nome è già usato. Sostituire non si può annullare.',
    share_warning: 'Un invito monouso, valido 24 ore. Chi entra con esso può leggere e modificare questo gruppo finché un amministratore non lo revoca; tiene ciò che ha già visto.',
    forget_backup: 'Copia prima questo link: con la password di recupero è il backup della chiave. OK dimentica il gruppo in questo browser (i dati restano sul server), Annulla lo tiene.',
    devices: 'Dispositivi', change_password: 'Cambia password di recupero', become_admin: 'Diventa amministratore', revoke: 'Revoca', admin: 'amministratore', member: 'membro', this_device: 'questo dispositivo',
    last_seen: 'visto %s', confirm_revoke: 'Revocare «%s»? Smette di sincronizzare questo gruppo, ma tiene ciò che ha già.',
    recovery_new: 'Password di recupero per «%s». Con il link del gruppo ti fa rientrare come amministratore (nuovo telefono, dispositivi persi). Conservala: non si può reimpostare.',
    recovery_join: 'Questo link non ha un invito. Password di recupero, per entrare come amministratore:', recovery_ask: 'Password di recupero:',
    set_recovery: '«%s» non ha ancora una password di recupero. Impostala ora per diventarne amministratore (Annulla: lo chiede più tardi)', wrong_password: 'Password errata, o troppi tentativi: riprova più tardi', pw_short: 'La password di recupero deve avere almeno 6 caratteri',
    join_here: 'Unirsi a questo gruppo in questo browser? Annulla apre invece l\'app sul telefono (l\'invito vale una volta).',
    need_invite: 'Nessun accesso: chiedi un invito a un amministratore', no_access: 'Questo dispositivo è stato rimosso dal gruppo: chiedi un invito a un amministratore',
    share_backup: 'Solo gli amministratori possono invitare. Questo link è il backup della chiave: per unirsi serve la password di recupero.' },
};
let saved = null; try { saved = localStorage.getItem('lang'); } catch {}
const lang = saved || ((navigator.languages?.[0] || navigator.language).toLowerCase().startsWith('it') ? 'it' : 'en'); // chosen, else first preferred language
document.documentElement.lang = lang;
const t = (k, ...a) => (T[lang][k] ?? T.en[k]).replace(/%s/g, () => a.shift());
document.querySelectorAll('[data-i18n]').forEach(el => el.textContent = t(el.dataset.i18n));
document.querySelectorAll('[data-i18n-ph]').forEach(el => el.placeholder = t(el.dataset.i18nPh));
document.querySelectorAll('[data-i18n-label]').forEach(el => el.ariaLabel = t(el.dataset.i18nLabel));

// Groups this browser knows: { secret, token, name }. The secret never leaves the browser; the server only sees an id derived from it.
let groups = []; try { groups = JSON.parse(localStorage.getItem('groups') || '[]'); } catch {}
const saveGroups = () => localStorage.setItem('groups', JSON.stringify(groups));
const base = location.origin + location.pathname.replace(/[^/]*$/, '').replace(/\/$/, ''); // this server, also under a sub-path




const emptyState = () => ({ name: null, nameUpdatedAt: 0, counters: [], events: [], devices: [] });
// Who logs resets: a random id made once per browser (no registration) and an editable name; the name travels in each group's blob, so a rename reaches old events.
const deviceId = localStorage.getItem('device') || (id => (localStorage.setItem('device', id), id))(crypto.randomUUID());
let deviceName = localStorage.getItem('deviceName') || 'Web', deviceNameAt = +localStorage.getItem('deviceNameAt') || 0;
const myDevice = () => ({ id: deviceId, name: deviceName, updatedAt: deviceNameAt });
const nameOfDevice = id => id && (id === deviceId ? deviceName : [...stores.values()].flatMap(s => s.state.devices ?? []).find(d => d.id === id)?.name) || '';
// One store per group (keyed by its secret), decrypted, plus '' for Ungrouped, which never leaves this browser.
const stores = new Map();
const store = k => stores.get(k) ?? stores.set(k, { state: emptyState(), etag: null, keys: null, status: '', failed: false, queue: Promise.resolve() }).get(k);
try { Object.assign(store('').state, JSON.parse(localStorage.getItem('local') || '{}')); } catch {}
// Only live counters: a moved one leaves a tombstone behind in its old store.
const ownerOf = id => [...stores].find(([, s]) => s.state.counters.some(c => c.id === id && !c.deleted))?.[0];
const ownerOfEvent = id => [...stores].find(([, s]) => s.state.events.some(e => e.id === id && s.state.counters.some(c => c.id === e.counterId && !c.deleted)))?.[0];
const counterOf = id => stores.get(ownerOf(id))?.state.counters.find(c => c.id === id);
let view = 'all'; // what the list shows: 'all', '' (Ungrouped) or a group's secret
const fail = msg => { $('#err').textContent = msg; return false; };
// Each group has this browser's own device token (g.device); g.admin says whether it may invite and revoke.
const headers = g => g.device ? { Authorization: 'Bearer ' + g.device } : {};
const api = async (g, path, body) => fetch(`${base}/s/${(await deriveKeys(g.secret)).id}/${path}`,
  { method: body ? 'POST' : 'GET', headers: headers(g), ...(body && { body: JSON.stringify(body) }) });

/** A group from before device tokens: get one with the old server TOKEN, then offer to set the recovery password (the first device to do so is admin). */
async function claim(g) {
  const legacy = g.token || localStorage.getItem('token');
  if (!legacy) return false;
  const r = await fetch(`${base}/s/${(await deriveKeys(g.secret)).id}/claim`,
    { method: 'POST', headers: { Authorization: 'Bearer ' + legacy }, body: JSON.stringify({ deviceId }) });
  if (!r.ok) return false;
  g.device = (await r.json()).token; g.admin = false; delete g.token; saveGroups();
  setTimeout(() => setRecovery(g, true));
  return true;
}

/** Sets (or, for an admin, changes) the group's recovery password; the device becomes admin. */
async function setRecovery(g, first) {
  const pw = prompt(first ? t('set_recovery', g.name || '…') : t('recovery_ask'));
  if (!pw) return;
  if (pw.length < 6) return alert(t('pw_short'));
  const r = await api(g, 'recovery', { recovery: await stretch(pw, (await deriveKeys(g.secret)).id) });
  if (r.ok) { g.admin = true; saveGroups(); render(); } else if (!first) alert(t('sync_failed', await r.text()));
}

/** Fetches and decrypts group [k] into its store. False (the reason is its status) if it couldn't. */
async function pull(k) {
  const g = groups.find(g => g.secret === k), s = store(k);
  if (!g) return false;
  const bad = msg => { s.status = msg; s.failed = true; render(); return false; };
  if (!crypto.subtle) return bad(t('needs_https'));
  s.keys ??= await deriveKeys(k);
  let r;
  try {
    if (!g.device && !g.fresh && !await claim(g)) return bad(t('need_invite'));
    r = await fetch(`${base}/s/${s.keys.id}`, { headers: headers(g) });
  } catch { return bad(t('unreachable')); }
  if (r.status === 401) return bad(t('no_access'));
  if (r.status === 404) {
    if (!g.fresh) { s.etag = null; return bad(t('no_data')); }
    s.etag = null; if (!s.state.name) s.state = { ...emptyState(), name: g.name, nameUpdatedAt: g.nameUpdatedAt }; // made here, not uploaded yet
    return true;
  }
  if (!r.ok) return bad(t('sync_failed', await r.text()));
  const j = await r.json();
  try { s.state = await unseal(s.keys, b64u(j.ciphertext)); } catch { return bad(t('decrypt_failed')); }
  s.etag = j.etag; s.failed = false; s.status = t('synced', new Date().toLocaleTimeString([], { timeStyle: 'short' }));
  if (g.name !== s.state.name) { g.name = s.state.name || ''; saveGroups(); }
  return true;
}

// One change at a time per store: pull, merge, push, and a 409 (someone else wrote in between) starts over. Ungrouped is just a local merge.
const commit = (k, change = { counters: [], events: [] }) => {
  const s = store(k);
  return s.queue = s.queue.then(() => run(k, change)).catch(e => { s.status = String(e); s.failed = true; render(); });
};
async function run(k, change) {
  const s = store(k), own = myDevice();
  change = { ...change, devices: [own] };
  const has = change.counters.length || change.events.length || change.name != null || !s.state.devices?.some(d => d.id === own.id && d.updatedAt >= own.updatedAt);
  if (k === '') { if (has) { s.state = merge(s.state, change); localStorage.setItem('local', JSON.stringify(s.state)); } return render(); }
  for (let i = 0; i < 5; i++) {
    if (!await pull(k)) return;
    const g = groups.find(g => g.secret === k);
    if (!has && !g.fresh) return render();
    const next = merge(s.state, change), mine = s.etag;
    const r = await fetch(`${base}/s/${s.keys.id}`, { method: 'PUT', headers: { ...headers(g), ...(mine && { 'If-Match': mine }) },
      body: JSON.stringify({ ciphertext: b64(await seal(s.keys, next)), ...(!mine && { deviceId, recovery: g.recovery }) }) });
    if (r.ok) {
      const j = await r.json();
      s.state = next; s.etag = j.etag;
      if (j.token) { g.device = j.token; g.admin = true; delete g.recovery; }
      if (g.fresh) { delete g.fresh; saveGroups(); }
      return render();
    }
    if (r.status !== 409) { s.status = t('sync_failed', await r.text()); s.failed = true; return render(); }
  }
  s.status = t('busy'); s.failed = true; render();
}
const syncAll = () => Promise.all(['', ...groups.map(g => g.secret)].map(k => commit(k)));

const shareLink = (g, nonce) => `${base}/#k=${g.secret}${nonce ? '&n=' + nonce : ''}`;
const appLink = (secret, nonce) => `howlongsince://join?h=${encodeURIComponent(base)}#k=${secret}${nonce ? '&n=' + nonce : ''}`;
const newSecret = () => b64(crypto.getRandomValues(new Uint8Array(32))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
/** A fresh single-use invite (admins only), or null. */
const invite = async g => { const r = await api(g, 'invite', {}).catch(() => null); if (r?.status === 403) { g.admin = false; saveGroups(); } return r?.ok ? (await r.json()).nonce : null; };
const byName = (a, b) => (a.name || '￿').localeCompare(b.name || '￿');
function fillGroups() {
  const opts = [];
  if (groups.length) { // like the phone's drawer: the views only matter once there is a group
    opts.push(['all', t('all_groups')]);
    if (store('').state.counters.some(c => !c.deleted)) opts.push(['', t('ungrouped')]);
    [...groups].sort(byName).forEach(g => opts.push([g.secret, g.name || '…']));
  }
  if (!opts.some(o => o[0] === view)) view = 'all';
  $('#views').innerHTML = opts.map(([v, n]) => `<button data-view="${esc(v)}" class="${v === view ? 'on' : ''}">${esc(n)}</button>`).join('');
  const g = groups.find(g => g.secret === view);
  $('#groupopts').hidden = !g;
  if (g) {
    $('#groupname').textContent = g.name || '…';
    $('#devices').hidden = $('#setpw').hidden = !g.admin;
    $('#promote').hidden = !!g.admin || !g.device;
  }
  $('#title').textContent = view === 'all' ? 'How Long Since?' : view === '' ? t('ungrouped') : g?.name || '…';
}

/** Joins the group in a link: with an invite (n) as a member, with an old server token (t) by claiming, else with the recovery password as an admin. */
async function join(link) {
  const p = parseLink(link);
  if (!p) return alert(t('bad_link'));
  if (p.server && p.server !== base) return alert(t('other_server', p.server));
  const known = groups.find(g => g.secret === p.secret);
  if (known) { view = p.secret; render(); return alert(t('already_joined', known.name || '…')); }
  if (!crypto.subtle) return alert(t('needs_https'));
  if (p.nonce && !confirm(t('join_here'))) { location.href = appLink(p.secret, p.nonce); return; }
  const g = { secret: p.secret, name: '' }, id = (await deriveKeys(p.secret)).id;
  try {
    if (p.token && !p.nonce) { g.token = p.token; if (!await claim(g)) return alert(t('need_invite')); }
    else {
      const pw = p.nonce ? null : prompt(t('recovery_join'));
      if (!p.nonce && !pw) return;
      const r = await api(g, 'enroll', { deviceId, ...(p.nonce ? { nonce: p.nonce } : { recovery: await stretch(pw, id) }) });
      if (r.status === 404) return alert(t('no_data'));
      if (r.status === 401 || r.status === 429) return alert(p.nonce ? t('need_invite') : t('wrong_password'));
      if (!r.ok) throw new Error(t('sync_failed', await r.text()));
      g.device = (await r.json()).token; g.admin = !p.nonce;
    }
    groups.push(g); view = g.secret; saveGroups(); render(); commit(g.secret);
  } catch (e) { alert(e instanceof TypeError ? t('unreachable') : e.message); }
}

/** A new group: a random key and a recovery password made here; the first blob is uploaded (and this browser enrolled as admin) as soon as the server answers. */
async function makeGroup(name) {
  const pw = prompt(t('recovery_new', name));
  if (!pw) return null;
  if (pw.length < 6) { alert(t('pw_short')); return null; }
  const secret = newSecret();
  const g = { secret, name, nameUpdatedAt: Date.now(), fresh: true, recovery: await stretch(pw, (await deriveKeys(secret)).id) };
  groups.push(g); saveGroups();
  return g;
}

async function newGroup() {
  const name = prompt(t('group_name'))?.trim();
  if (!name) return;
  if (!crypto.subtle) return alert(t('needs_https'));
  const g = await makeGroup(uniqueName(name, groups.map(g => g.name)));
  if (!g) return;
  view = g.secret; render(); commit(g.secret);
}

/** The link and a QR code of it: a fresh invite for admins, the key backup (needs the recovery password) for members. */
async function share(secret) {
  const g = groups.find(g => g.secret === secret);
  if (!g) return;
  const nonce = g.admin ? await invite(g) : null;
  const link = shareLink(g, nonce), q = qrcode(0, 'M');
  $('#share [data-i18n=share_warning]').textContent = t(nonce ? 'share_warning' : 'share_backup');
  q.addData(link); q.make();
  $('#sharename').textContent = g.name || '…';
  $('#qr').innerHTML = q.createSvgTag({ cellSize: 4, margin: 8, scalable: true });
  $('#sharelink').value = link;
  $('#copy').textContent = t('copy_link');
  $('#nativeshare').hidden = !navigator.share;
  $('#nativeshare').onclick = () => navigator.share({ title: g.name, text: link }).catch(() => {});
  $('#copy').onclick = async () => { await navigator.clipboard.writeText(link); $('#copy').textContent = t('link_copied'); };
  $('#share').showModal();
}

function renameGroup() {
  const g = groups.find(g => g.secret === view), name = prompt(t('group_name'), g.name)?.trim();
  if (!name || name === g.name) return;
  g.name = uniqueName(name, groups.filter(x => x !== g).map(x => x.name)); saveGroups(); render();
  commit(g.secret, { counters: [], events: [], name: g.name, nameUpdatedAt: Date.now() });
}

function forgetGroup() {
  const g = groups.find(g => g.secret === view);
  if (prompt(t('forget_backup'), shareLink(g)) === null) return; // a text box, so the link can be copied
  if (g.device) api(g, 'leave', {}).catch(() => {}); // the server deletes the group once no device is left
  groups = groups.filter(x => x !== g); stores.delete(g.secret); view = 'all'; saveGroups(); render();
}

/** Admins: every device of the group, named from the group data, with revoke. */
async function showDevices() {
  const g = groups.find(g => g.secret === view);
  const r = await api(g, 'devices').catch(() => null);
  if (!r?.ok) return alert(r?.status === 403 ? t('need_invite') : t('unreachable'));
  const list = await r.json(), named = store(g.secret).state.devices ?? [];
  const name = d => d.id === deviceId ? `${deviceName} (${t('this_device')})` : named.find(x => x.id === d.id)?.name || d.id.slice(0, 8);
  $('#devlist').innerHTML = list.map(d => `<p><b>${esc(name(d))}</b> · ${t(d.admin ? 'admin' : 'member')} · ${esc(t('last_seen', new Date(d.lastSeen * 1000).toLocaleDateString()))}
    ${d.id === deviceId ? '' : `<button data-revoke="${esc(d.id)}">${t('revoke')}</button>`}</p>`).join('');
  $('#devlist').onclick = async e => {
    const id = e.target.dataset?.revoke, d = list.find(d => d.id === id);
    if (!d || !confirm(t('confirm_revoke', name(d)))) return;
    await api(g, 'revoke', { deviceId: id });
    showDevices();
  };
  if (!$('#devdlg').open) $('#devdlg').showModal();
}

async function promote() {
  const g = groups.find(g => g.secret === view), pw = prompt(t('recovery_ask'));
  if (!pw) return;
  const r = await api(g, 'promote', { recovery: await stretch(pw, (await deriveKeys(g.secret)).id) });
  if (!r.ok) return alert(t('wrong_password'));
  g.admin = true; saveGroups(); render();
}

function since(ms) {
  const s = Math.max(0, (Date.now() - ms) / 1000), d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60);
  return d ? `${d}${t('d')} ${h}h` : h ? `${h}h ${m}m` : `${m}m`;
}

const event = (counterId, source, at = Date.now()) => ({ id: crypto.randomUUID(), counterId, at, source, deleted: false, deviceId });


function editForm(c, avg, k) {
  const ms = c.notifyAfterMs, units = unitsFor(ms, debug);
  // Largest unit that divides the stored value evenly, like the app.
  const [unit, size] = ms ? units.filter(([, n]) => ms % n === 0).pop() ?? units[0] : UNITS[2];
  return `<form data-edit="${c.id}">
    <label>${t('label')} <input name="label" value="${esc(c.label)}" required></label>
    <label>${t('tags')} <input name="tags" value="${esc(tagList(c.tags).join(', '))}" placeholder="${t('comma')}"></label>
    ${groups.length ? `<label>${t('group')} <select name="group">${[['', t('ungrouped')], ...[...groups].sort(byName).map(g => [g.secret, g.name || '…'])]
      .map(([v, n]) => `<option value="${esc(v)}" ${v === k ? 'selected' : ''}>${esc(n)}</option>`).join('')}</select></label>` : ''}
    <label>${t('notify_after')} <span class="row"><input name="amount" type="number" min="1" value="${ms ? ms / size : ''}" placeholder="${t('never')}">
      <select name="unit">${units.map(([u]) => `<option value="${u}" ${u === unit ? 'selected' : ''}>${t(u)}</option>`).join('')}</select>
      ${avg ? `<button type="button" data-avg="${avg}">${t('use_average', since(Date.now() - avg))}</button>` : ''}</span></label>
    <label>${t('reset_note')} <span class="row"><input name="note" placeholder="${t('note')}"><input type="datetime-local" name="at"><button type="button" data-reset-at="${c.id}">${t('reset')}</button></span></label>
    ${c.nfcTagId ? `<p class="muted">${t('nfc_paired')} <button type="button" data-unpair="${c.id}">${t('unpair')}</button></p>` : ''}
    <span class="row"><button>${t('save')}</button><button type="button" data-archive="${c.id}">${t(c.archived ? 'unarchive' : 'archive')}</button></span>
  </form>`;
}

/** Bars of the intervals between resets, oldest left, last 30; same as the app's IntervalChart. */
function chart(ev) {
  const gaps = ev.slice(0, -1).map((e, i) => e.at - ev[i + 1].at).reverse().slice(-30);
  if (gaps.length < 2) return '';
  const max = Math.max(...gaps, 1), w = 100 / gaps.length;
  const avg = gaps.reduce((a, b) => a + b) / gaps.length;
  return `<p class="muted">${t('intervals', since(Date.now() - avg))}</p>
    <svg class="chart" viewBox="0 0 100 56" preserveAspectRatio="none">${gaps.map((g, i) => {
      const h = Math.max(g / max * 56, 1.5);
      return `<rect x="${i * w + w * .15}" y="${56 - h}" width="${w * .7}" height="${h}"><title>${since(Date.now() - g)}</title></rect>`;
    }).join('')}</svg>`;
}

// Same hue as the app's tagColor: Java String.hashCode() mod 360.
const tagHue = t => { let h = 0; for (const ch of t) h = Math.imul(h, 31) + ch.charCodeAt(0) | 0; return (h % 360 + 360) % 360; };
const dot = t => `<i class="dot" style="background:hsl(${tagHue(t)} 55% var(--tag-l))"></i>`;
let debug = localStorage.getItem('debug') === '1';
let filter = null, showArchived = false;
let folded = new Set(); try { folded = new Set(JSON.parse(localStorage.getItem('folded') || '[]')); } catch {}
$('#sort').value = localStorage.getItem('sort') || 'due';

const rowsOf = k => { const { counters, events } = store(k).state; return counters.filter(c => !c.deleted).map(c => {
  const ev = events.filter(e => e.counterId === c.id && !e.deleted).sort((a, b) => b.at - a.at), last = ev[0]?.at ?? 0;
  return { c, ev, last, k, overdue: c.notifyAfterMs != null && Date.now() - last >= c.notifyAfterMs };
}); };
const groupName = k => k === '' ? t('ungrouped') : groups.find(g => g.secret === k)?.name || '…';

function rowHtml({ c, ev, last, k, overdue }) {
  return `
    <details data-id="${c.id}" class="${overdue ? 'overdue' : ''}" ${openIds.has(c.id) ? 'open' : ''} style="border-left-color:${tagList(c.tags).length ? `hsl(${tagHue(tagList(c.tags)[0])} 55% var(--tag-l))` : 'var(--card)'}">
      <summary><span><b>${esc(c.label)}</b><br>${k && groups.length >= 2 ? `<span class="muted">${esc(groupName(k))}</span><br>` : ''}<span class="muted">${tagList(c.tags).map(t => '#' + esc(t)).join(' ')}${c.notifyAfterMs ? ' · ⏰' : ''}</span></span>
        <span class="since">${since(last)}</span><button data-reset="${c.id}">↻ ${t('reset')}</button></summary>
      ${chart(ev)}
      ${editForm(c, ev.length >= 3 ? (ev[0].at - ev.at(-1).at) / (ev.length - 1) : 0, k)}
      <ul>${ev.map(e => `<li>${new Date(e.at).toLocaleString()} <span class="muted">${t(e.source)}${nameOfDevice(e.deviceId) ? ' · ' + esc(nameOfDevice(e.deviceId)) : ''}</span>${ev.length > 1 ? `<button data-del-event="${e.id}">✕</button>` : ''}${e.note ? `<span class="note">${esc(e.note)}</span>` : ''}</li>`).join('')}</ul>
      <button data-del="${c.id}">${t('delete_counter')}</button>
    </details>`;
}

let openIds = new Set();
function render() {
  if (document.activeElement?.closest('#list form')) return; // don't wipe an edit in progress
  fillGroups();
  openIds = new Set([...document.querySelectorAll('details[open]')].map(d => d.dataset.id));
  const pool = (view === 'all' ? ['', ...groups.map(g => g.secret)] : [view]).flatMap(rowsOf);
  const hasArchived = pool.some(r => r.c.archived);
  if (!hasArchived) showArchived = false;
  const live = pool.filter(r => !!r.c.archived === showArchived);
  const allTags = [...new Set(live.flatMap(r => tagList(r.c.tags)))].sort();
  if (!allTags.includes(filter)) filter = null;
  $('#tags').innerHTML = allTags.map(t => `<button data-tag="${esc(t)}" class="${t === filter ? 'on' : ''}">${dot(t)}${esc(t)}</button>`).join('')
    + (hasArchived ? `<button data-archived class="${showArchived ? 'on' : ''}">${t('archived')}</button>` : '');
  const q = $('#q').value.trim().toLowerCase(), sort = $('#sort').value;
  const shown = live.filter(r => !filter || tagList(r.c.tags).includes(filter))
    .filter(r => !q || r.c.label.toLowerCase().includes(q) || r.c.tags.includes(q));
  const order = sort === 'name' ? (a, b) => a.c.label.localeCompare(b.c.label)
    : sort === 'recent' ? (a, b) => b.last - a.last
    : (a, b) => b.overdue - a.overdue || a.last - b.last; // overdue first, then oldest reset
  // Sections (one per group, Ungrouped first) while looking at everything and at least one group exists; sorted within each.
  $('#fab').hidden = view === 'all' && groups.length > 0; // with sections, each header has its own +
  $('#list').innerHTML = !(view === 'all' && groups.length) ? shown.sort(order).map(rowHtml).join('')
    : ['', ...[...groups].sort(byName).map(g => g.secret)].map(k => {
      const rows = shown.filter(r => r.k === k).sort(order), s = store(k);
      if (!rows.length && !s.failed) return ''; // empty sections stay hidden, unless there is an error to show
      const fold = folded.has(k) && !q; // a search opens every section
      return `<div class="sec" data-fold="${esc(k)}"><b>${esc(groupName(k))} · ${rows.length}</b><span class="muted ${s.failed ? 'bad' : ''}">${esc(s.status)}</span><button data-add="${esc(k)}" title="${t('new_')}">+</button>${k ? `<button data-share="${esc(k)}" title="${t('share')}">⤴</button>` : ''}<span>${fold ? '▸' : '▾'}</span></div>`
        + (fold ? '' : rows.map(rowHtml).join(''));
    }).join('');
}


// The + button, like the phone's: a form for a new counter, into the group being looked at unless you pick another.
const openNew = grp => {
  const f = $('#newform'); f.reset();
  f.elements.group.innerHTML = [['', t('ungrouped')], ...[...groups].sort(byName).map(g => [g.secret, g.name || '…'])]
    .map(([v, n]) => `<option value="${esc(v)}">${esc(n)}</option>`).join('');
  f.elements.group.value = grp;
  $('#newgroupl').hidden = !groups.length;
  f.elements.unit.innerHTML = unitsFor(null, debug).map(([u]) => `<option value="${u}" ${u === 'days' ? 'selected' : ''}>${t(u)}</option>`).join('');
  $('#newdlg').showModal();
  f.elements.label.focus();
};
$('#fab').onclick = () => openNew(view === 'all' ? '' : view);
$('#newcancel').onclick = () => $('#newdlg').close();
$('#newform').onsubmit = e => {
  e.preventDefault();
  const f = new FormData(e.target), amount = Number(f.get('amount'));
  const c = { id: crypto.randomUUID(), label: f.get('label').trim(), tags: tagList(f.get('tags')).join(','), nfcTagId: null, updatedAt: Date.now(), deleted: false, archived: false,
    notifyAfterMs: amount > 0 ? amount * UNITS.find(([u]) => u === f.get('unit'))[1] : null };
  $('#newdlg').close();
  commit(f.get('group'), { counters: [c], events: [event(c.id, 'create')] });
};
addEventListener('scroll', () => document.body.classList.toggle('scrolled', scrollY > 0), { passive: true });

$('#tags').onclick = e => {
  if (e.target.closest('[data-archived]')) { showArchived = !showArchived; render(); }
  const t = e.target.closest('[data-tag]')?.dataset.tag;
  if (t) { filter = filter === t ? null : t; render(); }
};

/** Moves a counter, with its events, to another group (or Ungrouped): written to the new place first, then a tombstone in the old one. */
async function moveCounter(from, to, edited) {
  const events = store(from).state.events.filter(e => e.counterId === edited.id);
  await commit(to, { counters: [edited], events });
  if (to !== '' && store(to).failed) return; // not saved there, so it stays where it was; the section's status says why
  await commit(from, { counters: [{ ...edited, deleted: true }], events: [], drop: [edited.id] });
}

$('#list').onsubmit = e => {
  e.preventDefault();
  const f = new FormData(e.target), amount = Number(f.get('amount')), id = e.target.dataset.edit, from = ownerOf(id), to = f.get('group') ?? from;
  const edited = { ...counterOf(id), label: f.get('label').trim(), tags: tagList(f.get('tags')).join(','),
    notifyAfterMs: amount > 0 ? amount * UNITS.find(([u]) => u === f.get('unit'))[1] : null, updatedAt: Date.now() };
  document.activeElement.blur();
  if (to === from) return commit(from, { counters: [edited], events: [] });
  if (from !== '' && !confirm(t('move_title') + '\n\n' + t('move_text'))) return; // leaving a synced group removes it for everyone in it
  moveCounter(from, to, edited);
};

$('#list').onclick = e => {
  const ad = e.target.closest('[data-add]');
  if (ad) return openNew(ad.dataset.add);
  const sh = e.target.closest('[data-share]');
  if (sh) return share(sh.dataset.share);
  const sec = e.target.closest('[data-fold]');
  if (sec) {
    const k = sec.dataset.fold;
    folded.has(k) ? folded.delete(k) : folded.add(k);
    localStorage.setItem('folded', JSON.stringify([...folded]));
    return render();
  }
  const d = e.target.dataset;
  if (d.reset) {
    e.preventDefault();
    const ev = event(d.reset, 'manual'), k = ownerOf(d.reset);
    commit(k, { counters: [], events: [ev] });
    showUndo(t('reset_label', counterOf(d.reset).label), ev, k);
  }
  if (d.resetAt) {
    const f = e.target.closest('form').elements, v = f.at.value, at = v ? new Date(v).getTime() : Date.now();
    if (at > Date.now()) return alert(t('pick_past'));
    commit(ownerOf(d.resetAt), { counters: [], events: [{ ...event(d.resetAt, 'manual', at), ...(f.note.value.trim() && { note: f.note.value.trim() }) }] });
    document.activeElement.blur();
  }
  if (d.avg) {
    const f = e.target.closest('form').elements, a = Number(d.avg);
    const [u, n] = a < 36e5 && f.unit.querySelector('[value=minutes]') ? UNITS[0] : a < 1728e5 ? UNITS[1] : UNITS[2]; // minutes (debug only) under an hour, hours under 2 days, else days
    f.unit.value = u; f.amount.value = Math.max(1, Math.round(a / n));
  }
  if (d.archive) {
    const c = counterOf(d.archive);
    document.activeElement.blur();
    commit(ownerOf(d.archive), { counters: [{ ...c, archived: !c.archived, updatedAt: Date.now() }], events: [] });
  }
  if (d.delEvent) { const k = ownerOfEvent(d.delEvent); commit(k, { counters: [], events: [{ ...store(k).state.events.find(x => x.id === d.delEvent), deleted: true }] }); }
  if (d.unpair) commit(ownerOf(d.unpair), { counters: [{ ...counterOf(d.unpair), nfcTagId: null, updatedAt: Date.now() }], events: [] });
  if (d.del && confirm(t('confirm_delete')))
    commit(ownerOf(d.del), { counters: [{ ...counterOf(d.del), deleted: true, updatedAt: Date.now() }], events: [] });
};

let undoTimer;
function showUndo(text, ev, k) {
  $('#undo span').textContent = text; $('#undo').hidden = false;
  $('#undo button').onclick = () => { $('#undo').hidden = true; commit(k, { counters: [], events: [{ ...ev, deleted: true }] }); };
  clearTimeout(undoTimer); undoTimer = setTimeout(() => $('#undo').hidden = true, 8000);
}
$('#q').oninput = render;
$('#lang').value = saved || '';
$('#lang').onchange = () => { try { $('#lang').value ? localStorage.setItem('lang', $('#lang').value) : localStorage.removeItem('lang'); } catch {} location.reload(); };
const dark = matchMedia('(prefers-color-scheme: dark)');
function applyTheme() { // follows the system until the switch is used
  const v = localStorage.getItem('theme');
  v ? document.documentElement.dataset.theme = v : delete document.documentElement.dataset.theme;
  $('#dark').checked = v ? v === 'dark' : dark.matches;
}
dark.addEventListener('change', applyTheme);
$('#dark').onchange = () => { localStorage.setItem('theme', $('#dark').checked ? 'dark' : 'light'); applyTheme(); };
$('#debug').checked = debug;
$('#debug').onchange = () => { debug = $('#debug').checked; localStorage.setItem('debug', debug ? '1' : '0'); render(); };
$('#devname').value = deviceName;
$('#devname').onchange = () => {
  deviceName = $('#devname').value.trim() || 'Web'; deviceNameAt = Date.now(); $('#devname').value = deviceName;
  localStorage.setItem('deviceName', deviceName); localStorage.setItem('deviceNameAt', deviceNameAt);
  render(); syncAll();
};
applyTheme();
$('#sort').onchange = () => { localStorage.setItem('sort', $('#sort').value); render(); };
$('#views').onclick = e => { const b = e.target.closest('[data-view]'); if (b) { view = b.dataset.view; render(); } };
// The sidebar is always there on wide screens; on narrow ones it slides in over the page, like the phone's drawer.
$('#menu').onclick = () => document.body.classList.add('drawer');
$('#scrim').onclick = () => document.body.classList.remove('drawer');
document.addEventListener('keydown', e => e.key === 'Escape' && document.body.classList.remove('drawer'));
$('#drawer').addEventListener('click', e => { if (e.target.closest('[data-view], #newgroup, #addgroup, #shareview, #rename, #forget, #devices, #setpw, #promote, #app, #export, #import')) document.body.classList.remove('drawer'); });
/** Applies one entry of an import plan: the group's counters are replaced by the file's (moved here from other places if they lived there). */
async function applyImport({ name, isNew, items }) {
  let k = '';
  if (name !== null) {
    let g = groups.find(g => !isNew && g.name.toLowerCase() === name.toLowerCase());
    if (!g) { g = await makeGroup(name); if (!g) return; }
    k = g.secret;
  }
  const now = Date.now(), ids = new Set(items.map(it => it.counter.id));
  const gone = c => ({ ...c, deleted: true, updatedAt: now });
  const mine = store(k).state.counters.filter(c => !c.deleted && !ids.has(c.id)); // the rest of the group goes, as on the phone
  await commit(k, { counters: [...items.map(it => ({ ...it.counter, updatedAt: now })), ...mine.map(gone)], events: items.flatMap(it => it.events), drop: [...ids, ...mine.map(c => c.id)] });
  for (const [o, s] of stores) { // an imported counter that lived in another group moves, it doesn't copy
    const there = s.state.counters.filter(c => o !== k && !c.deleted && ids.has(c.id));
    if (there.length) await commit(o, { counters: there.map(gone), events: [], drop: there.map(c => c.id) });
  }
}

$('#export').onclick = () => {
  const parts = [{ group: null, ...store('').state }, ...[...groups].sort(byName).map(g => ({ group: g.name, ...store(g.secret).state }))];
  const a = Object.assign(document.createElement('a'), { download: `howlongsince-${new Date().toISOString().slice(0, 10)}.json`,
    href: URL.createObjectURL(new Blob([JSON.stringify(toBackup(parts), null, 2)], { type: 'application/json' })) });
  a.click(); URL.revokeObjectURL(a.href);
};
$('#import').onclick = () => $('#file').click();
$('#file').onchange = async () => {
  const f = $('#file').files[0]; $('#file').value = '';
  if (!f) return;
  let items;
  try { items = parseBackup(await f.text()); } catch (e) { return alert(t('cant_read', e.message)); }
  $('#importtext').textContent = t('import_q', items.length);
  const go = async replace => {
    $('#importdlg').close();
    for (const entry of planImport(items, replace, groups.map(g => g.name), t('imported_name'))) await applyImport(entry);
    alert(t('done_imported', items.length));
  };
  $('#doreplace').onclick = () => go(true); $('#dokeep').onclick = () => go(false); $('#nogo').onclick = () => $('#importdlg').close();
  $('#importdlg').showModal();
};

$('#newgroup').onclick = newGroup;
$('#addgroup').onclick = () => { const link = prompt(t('paste_link')); if (link) join(link); };
$('#rename').onclick = renameGroup;
$('#shareview').onclick = () => share(view);
$('#closeshare').onclick = () => $('#share').close();
$('#forget').onclick = forgetGroup;
$('#devices').onclick = showDevices;
$('#closedev').onclick = () => $('#devdlg').close();
$('#setpw').onclick = () => setRecovery(groups.find(g => g.secret === view), false);
$('#promote').onclick = promote;
$('#app').onclick = async e => { // admins hand the app a fresh invite; members open it with the key only (it asks for the recovery password)
  e.preventDefault();
  const g = groups.find(g => g.secret === view);
  location.href = appLink(g.secret, g.admin ? await invite(g) : null);
};

if (location.hash.length > 1) { // opened from a share link: remember the group, then hide the key from the address bar
  const frag = location.hash.slice(1);
  history.replaceState(null, '', location.pathname + location.search);
  join(frag);
}
render();
syncAll();
setInterval(syncAll, 15 * 60000); // pick up changes from the phone
setInterval(render, 60000); // keep the times current between syncs
document.addEventListener('visibilitychange', () => document.hidden || syncAll()); // fresh data when coming back to the tab

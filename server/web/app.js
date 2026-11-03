const $ = s => document.querySelector(s);
const esc = s => s.replace(/[&<>"]/g, c => `&#${c.charCodeAt(0)};`);

// English and Italian, picked from the browser; same wording as the app's strings.xml.
const T = {
  en: { new_counter: 'New counter', edit_counter: 'Edit counter', undo: 'Undo', search: 'Search labels and tags',
    sort_due: 'Due', sort_recent: 'Recent', sort_name: 'Name',
    unreachable: 'Server unreachable, last change not saved. Retrying every minute.', sync_failed: 'Sync failed: %s',
    label: 'Label', tags: 'Tags', comma: 'comma separated', notify_after: 'Notify after', never: 'never',
    minutes: 'minutes', hours: 'hours', days: 'days', weeks: 'weeks', use_average: 'Use average (%s)',
    note: 'Note (optional)', reset: 'Reset', now: 'Now', earlier: 'Earlier…', entries: '%s entries', after: 'after %s',
    nfc_paired: 'NFC tag paired', unpair: 'Unpair', save: 'Save', archive: 'Archive', unarchive: 'Unarchive',
    intervals: 'Intervals (avg %s)', archived: 'Archived', archived_hint: 'This counter is archived', delete: 'Delete', confirm_delete: 'Delete this counter?',
    reset_label: 'Reset %s', pick_past: 'Pick a time in the past', create: 'created', manual: 'manual', nfc: 'via NFC', d: 'd', y: 'y',
    open_app: 'Open in app', forget: 'Forget', move_to: 'Move to', n_selected: '%s selected', forget_keep: 'Move to Ungrouped', forget_purge: 'Remove counters', paste_link: 'Paste a group link', bad_link: 'That isn\'t a group link', next: 'Next', ok: 'OK',
    no_data: 'Nothing found for this link on this server', decrypt_failed: 'Can\'t decrypt: wrong key?', needs_https: 'This page needs https to decrypt (or localhost).', busy: 'Busy, try again',
    all_groups: 'All', ungrouped: 'Ungrouped', synced: 'Synced %s', group: 'Group', move_title: 'Move out of a synced group?', move_text: 'The counter will be removed for everyone else in the group it leaves.',
    new_group: 'New group', group_name: 'Group name', name: 'Name', join: 'Join', join_group: 'Join a group', join_on: 'Join a group on %s?', password: 'Recovery password',
    already_joined: 'Already joined as «%s»', share: 'Share', copy_link: 'Copy link', link_copied: 'Copied', close: 'Close',
    check_limits: 'Check my limits', default_server: 'Default server %s', custom_server: 'Custom server',
    admin_of: 'Admin of', admin_count: '%s / %s groups', admin_limit: 'You are already admin of %s groups, the maximum',
    new_: 'New', settings: 'Settings', group_settings: 'Group settings', back: 'Back', edit: 'Edit', language: 'Language', menu: 'Menu', dark_theme: 'Dark theme', debug_mode: 'Debug mode',
    debug_hint: 'Allow "notify after" in minutes', device_name: 'Device name', device_name_hint: 'Shown next to the resets you log, on every device in a synced group',
    export_data: 'Export', import_data: 'Import', replace: 'Replace', keep_both: 'Keep both', cancel: 'Cancel', imported_name: 'Imported', cant_read: 'Can\'t read file: %s', done_imported: 'Imported %s counters',
    import_q: 'The file has %s counters. Replace: groups with the same name are deleted and replaced by the file\'s. Keep both: they are imported as new groups, renamed if a name is taken. Replacing can\'t be undone.',
    share_warning: 'A **single-use** invite, valid for **24 hours**.\n\nWhoever joins with it can read and change this group until an admin revokes them; they keep what they already saw.',
    forget_backup: 'Copy this link first: with the recovery password it is the backup of the key. Forget removes the group from this browser (the data stays on the server). Its counters here can move to Ungrouped or be removed.',
    sync: 'Sync', server: 'Server: %s', sync_now: 'Sync now', devices: 'Devices', change_password: 'Change password', become_admin: 'Become admin',
    pw_join: 'Recovery password, to join as an admin', revoke: 'Revoke', admin: 'Admin', member: 'Member', this_device: 'This device',
    last_seen: 'last seen %s', confirm_revoke: 'Revoke «%s»? It stops syncing this group, but keeps what it already has.',
    recovery_new: 'Recovery password for «%s». With the group link, it lets you rejoin as an admin (new phone, lost devices). Keep it safe: it can\'t be reset.',
    set_recovery: '«%s» has no recovery password yet. Set one now to become its admin (Cancel: ask again later)', wrong_password: 'Wrong password, or too many attempts: try later', pw_short: 'The recovery password needs at least 6 characters',
    need_invite: 'No access: ask an admin for an invite', no_access: 'This device was removed from the group: ask an admin for an invite',
    share_backup: 'Only admins can invite. This link is the backup of the key: joining with it needs the recovery password.',
    empty_title: 'Nothing to count yet', empty_hint: 'Add a counter with + New, then reset it each time the thing happens.' },
  it: { new_counter: 'Nuovo contatore', edit_counter: 'Modifica contatore', undo: 'Annulla', search: 'Cerca tra etichette e tag',
    sort_due: 'Più in ritardo', sort_recent: 'Azzerati di recente', sort_name: 'Nome',
    unreachable: 'Server irraggiungibile, ultima modifica non salvata. Nuovo tentativo ogni minuto.', sync_failed: 'Sincronizzazione non riuscita: %s',
    label: 'Etichetta', tags: 'Tag', comma: 'separati da virgole', notify_after: 'Avvisa dopo', never: 'mai',
    minutes: 'minuti', hours: 'ore', days: 'giorni', weeks: 'settimane', use_average: 'Usa la mia media (%s)',
    note: 'Nota (facoltativa)', reset: 'Azzera', now: 'Ora', earlier: 'Prima…', entries: '%s voci', after: 'dopo %s',
    nfc_paired: 'Tag NFC abbinato', unpair: 'Disabbina', save: 'Salva', archive: 'Archivia', unarchive: 'Ripristina',
    intervals: 'Intervalli (media %s)', archived: 'Archiviati', archived_hint: 'Questo contatore è archiviato', delete: 'Elimina', confirm_delete: 'Eliminare questo contatore?',
    reset_label: 'Azzerato %s', pick_past: 'Scegli un momento nel passato', create: 'creato', manual: 'manuale', nfc: 'via NFC', d: 'g', y: 'a',
    open_app: 'Apri nell\'app', forget: 'Dimentica', move_to: 'Sposta in', n_selected: '%s selezionati', forget_keep: 'Sposta in Senza gruppo', forget_purge: 'Elimina contatori', paste_link: 'Incolla un link di gruppo', bad_link: 'Questo non è un link di gruppo', next: 'Avanti', ok: 'OK',
    no_data: 'Nessun gruppo trovato per questo link su questo server', decrypt_failed: 'Impossibile decifrare: chiave sbagliata?', needs_https: 'Questa pagina richiede https per decifrare (o localhost).', busy: 'Occupato, riprova',
    all_groups: 'Tutti', ungrouped: 'Senza gruppo', synced: 'Sincronizzato alle %s', group: 'Gruppo', move_title: 'Spostare da un gruppo sincronizzato?', move_text: 'Il contatore sparirà per tutti gli altri membri del gruppo che stai lasciando.',
    new_group: 'Nuovo gruppo', group_name: 'Nome del gruppo', name: 'Nome', join: 'Unisciti', join_group: 'Unisciti a un gruppo', join_on: 'Vuoi unirti a un gruppo su %s?', password: 'Password di recupero',
    already_joined: 'Ne fai già parte, come «%s»', share: 'Condividi', copy_link: 'Copia link', link_copied: 'Copiato', close: 'Chiudi',
    check_limits: 'Controlla i miei limiti', default_server: 'Server predefinito %s', custom_server: 'Server personalizzato',
    admin_of: 'Amministratore di', admin_count: '%s / %s gruppi', admin_limit: 'Hai raggiunto il numero massimo di gruppi che puoi amministrare su questo server (%s)',
    new_: 'Nuovo', settings: 'Impostazioni', group_settings: 'Impostazioni gruppo', back: 'Indietro', edit: 'Modifica', language: 'Lingua', menu: 'Menu', dark_theme: 'Tema scuro', debug_mode: 'Modalità debug',
    debug_hint: 'Permetti di impostare l\'avviso in minuti', device_name: 'Nome del dispositivo', device_name_hint: 'Compare accanto agli azzeramenti che registri, su tutti i dispositivi di un gruppo sincronizzato',
    export_data: 'Esporta', import_data: 'Importa', replace: 'Sostituisci', keep_both: 'Tieni entrambi', cancel: 'Annulla', imported_name: 'Importati', cant_read: 'Impossibile leggere il file: %s', done_imported: 'Importati %s contatori',
    import_q: 'Il file contiene %s contatori. «Sostituisci» elimina i gruppi con lo stesso nome e li rimpiazza con quelli del file; «Tieni entrambi» li importa come nuovi gruppi, rinominati se un nome è già in uso. Sostituire non si può annullare.',
    share_warning: 'Un invito **monouso**, valido **24 ore**.\n\nChi entra con questo invito può leggere e modificare il gruppo finché un amministratore non lo revoca, e conserva ciò che ha già visto.',
    forget_backup: 'Copia prima questo link: con la password di recupero è il backup della chiave. Dimentica toglie il gruppo da questo browser (i dati restano sul server). I suoi contatori qui possono passare a Senza gruppo o essere eliminati.',
    sync: 'Sincronizzazione', server: 'Server: %s', sync_now: 'Sincronizza ora', devices: 'Dispositivi', change_password: 'Cambia password', become_admin: 'Diventa amministratore',
    pw_join: 'Password di recupero, per entrare come amministratore', revoke: 'Revoca', admin: 'Amministratore', member: 'Membro', this_device: 'Questo dispositivo',
    last_seen: 'visto %s', confirm_revoke: 'Revocare «%s»? Non potrà più sincronizzare il gruppo, ma conserverà ciò che ha già.',
    recovery_new: 'Password di recupero per «%s». Insieme al link del gruppo ti permette di rientrare come amministratore (nuovo telefono, dispositivi persi). Conservala: non si può reimpostare.',
    set_recovery: '«%s» non ha ancora una password di recupero. Impostala ora per diventarne amministratore (Annulla: lo chiede più tardi)', wrong_password: 'Password errata o troppi tentativi: riprova più tardi', pw_short: 'La password di recupero deve avere almeno 6 caratteri',
    need_invite: 'Nessun accesso: chiedi un invito a un amministratore', no_access: 'Questo dispositivo è stato rimosso dal gruppo: chiedi un invito a un amministratore',
    share_backup: 'Solo gli amministratori possono invitare. Questo link è il backup della chiave: per unirsi serve la password di recupero.',
    empty_title: 'Ancora nessun contatore', empty_hint: 'Tocca + Nuovo per iniziare, poi azzera il contatore ogni volta che succede.' },
};
let saved = null; try { saved = localStorage.getItem('lang'); } catch {}
const lang = saved || ((navigator.languages?.[0] || navigator.language).toLowerCase().startsWith('it') ? 'it' : 'en'); // chosen, else first preferred language
document.documentElement.lang = lang;
const t = (k, ...a) => (T[lang][k] ?? T.en[k]).replace(/%s/g, () => a.shift());
document.querySelectorAll('[data-i18n]').forEach(el => el.textContent = t(el.dataset.i18n));
document.querySelectorAll('[data-i18n-ph]').forEach(el => el.placeholder = t(el.dataset.i18nPh));
document.querySelectorAll('[data-i18n-label]').forEach(el => el.ariaLabel = t(el.dataset.i18nLabel));

// Material icons (24px paths).
const ICONS = {
  arrow: 'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z',
  menu: 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  gear: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z',
  share: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11A2.99 2.99 0 0 0 21 5a3 3 0 1 0-5.91.7L8.04 9.81A2.99 2.99 0 0 0 3 12a3 3 0 0 0 5.04 2.19l7.12 4.16c-.05.21-.08.43-.08.65A2.92 2.92 0 1 0 18 16.08z',
  reset: 'M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z',
  sort: 'M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3 5 6.99h3V14h2V6.99h3L9 3z',
  up: 'M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z',
  down: 'M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z',
  close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  login: 'M11 7 9.6 8.4l2.6 2.6H2v2h10.2l-2.6 2.6L11 17l5-5-5-5zm9 12h-8v2h8a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2h-8v2h8v14z',
  newfolder: 'M20 6h-8l-2-2H4a2 2 0 0 0-1.99 2L2 18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2zm-1 8h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z',
  list: 'M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z',
  folder: 'M10 4H4a2 2 0 0 0-1.99 2L2 18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8l-2-2z',
  inbox: 'M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm0 12h-4a3 3 0 0 1-6 0H5V5h14v10z',
};
const icon = n => `<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[n]}"/></svg>`;
document.querySelectorAll('[data-icon]').forEach(el => el.insertAdjacentHTML('afterbegin', icon(el.dataset.icon)));

// Snackbar, and a small modal in place of prompt/confirm.
let toastTimer;
function toast(text, action, fn) {
  const el = $('#toast'), b = el.querySelector('button');
  el.querySelector('span').textContent = text; b.textContent = action || '';
  b.onclick = () => { el.hidden = true; fn?.(); };
  el.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => el.hidden = true, action ? 8000 : 5000);
}
/** Resolves to the typed text (input), true (no input) or null (cancelled). */
const ALT = Symbol('alt'); // what ask() resolves to when the extra button is pressed
function ask(text, { input, value = '', ok = t('ok'), alt, danger, info } = {}) {
  const d = $('#ask'), i = $('#askinput');
  $('#asktext').textContent = text; $('#askcancel').hidden = !!info; // info: just a message to close
  i.hidden = !input; i.type = input || 'text'; i.value = value;
  $('#askalt').hidden = !alt; $('#askalt').textContent = alt || ''; $('#askalt').classList.toggle('danger', !!danger);
  $('#askok').textContent = ok; $('#askok').classList.toggle('danger', !!danger);
  d.returnValue = ''; d.showModal(); if (input) { i.focus(); i.select(); }
  return new Promise(res => d.onclose = () => res(d.returnValue === 'alt' ? ALT : d.returnValue === 'ok' ? (input ? i.value : true) : null));
}

// Groups this browser knows: { secret, token, name }. The secret never leaves the browser; the server only sees an id derived from it.
let groups = []; try { groups = JSON.parse(localStorage.getItem('groups') || '[]'); } catch {}
const saveGroups = () => localStorage.setItem('groups', JSON.stringify(groups));
const base = location.origin + location.pathname.replace(/[^/]*$/, '').replace(/\/$/, ''); // this server, also under a sub-path

const emptyState = () => ({ name: null, nameUpdatedAt: 0, counters: [], events: [], devices: [] });
// Who logs resets: a random id made once per browser (no registration) and an editable name; the name travels in each group's blob, so a rename reaches old events.
const DEFAULT_SERVER = 'https://howlongsince.etrunon.eu/app';
const withScheme = s => (/^https?:\/\//i.test(s) ? s : 'https://' + s).replace(/\/+$/, ''); // "host:port" alone would parse as a scheme
const srv = g => g.server ? withScheme(g.server) : base; // groups from before servers per group live on this page's server
const host = s => s.replace(/^https?:\/\//, '');
// Each server's MAX_ADMIN_GROUPS, from its GET /limits: '…' while asking, '?' if it can't be reached.
const limits = new Map();
function limitOf(s) {
  if (!limits.has(s)) {
    limits.set(s, '…');
    fetch(`${s}/limits`).then(r => r.json()).then(j => limits.set(s, j.maxAdminGroups)).catch(() => limits.set(s, '?')).then(fillGroups);
  }
  return limits.get(s);
}
const adminLine = s => t('admin_count', groups.filter(g => g.admin && srv(g) === s).length, limitOf(s));
const deviceId =localStorage.getItem('device') || (id => (localStorage.setItem('device', id), id))(crypto.randomUUID());
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
const groupOf = k => groups.find(g => g.secret === k);
const selected = new Set(); // ids of the cards picked for a move (long-press, or Ctrl/Cmd-click)
let view = 'all'; // what the list shows: 'all', '' (Ungrouped) or a group's secret
// Each group has this browser's own device token (g.device); g.admin says whether it may invite and revoke.
const headers = g => g.device ? { Authorization: 'Bearer ' + g.device } : {};
const gz = s => new Response(new Blob([s]).stream().pipeThrough(new CompressionStream('gzip'))).arrayBuffer();
const api = async (g, path, body) => fetch(`${srv(g)}/s/${(await deriveKeys(g.secret)).id}/${path}`,
  { method: body ? 'POST' : 'GET', headers: { ...headers(g), ...(body && { 'Content-Encoding': 'gzip' }) }, ...(body && { body: await gz(JSON.stringify(body)) }) });
const stretchFor = async (g, pw) => stretch(pw, (await deriveKeys(g.secret)).id);

/** A group from before device tokens: get one with the old server TOKEN, then offer to set the recovery password (the first device to do so is admin). */
async function claim(g) {
  const legacy = g.token || localStorage.getItem('token');
  if (!legacy) return false;
  const r = await fetch(`${srv(g)}/s/${(await deriveKeys(g.secret)).id}/claim`,
    { method: 'POST', headers: { Authorization: 'Bearer ' + legacy, 'Content-Encoding': 'gzip' }, body: await gz(JSON.stringify({ deviceId })) });
  if (!r.ok) return false;
  g.device = (await r.json()).token; g.admin = false; delete g.token; saveGroups();
  setTimeout(async () => { const pw = await ask(t('set_recovery', g.name || '…'), { input: 'password' }); if (pw) setRecovery(g, pw); });
  return true;
}

/** Sets (or, for an admin, changes) the group's recovery password; the device becomes admin. */
async function setRecovery(g, pw) {
  if (pw.length < 6) return toast(t('pw_short'));
  const r = await api(g, 'recovery', { recovery: await stretchFor(g, pw) });
  if (!r.ok) return toast(r.status === 402 ? t('admin_limit', limitOf(srv(g))) : t('sync_failed', await r.text()));
  g.admin = true; saveGroups(); render(); return true;
}
async function promote(g, pw) {
  const r = await api(g, 'promote', { recovery: await stretchFor(g, pw) });
  if (!r.ok) return toast(r.status === 402 ? t('admin_limit', limitOf(srv(g))) : t('wrong_password'));
  g.admin = true; saveGroups(); render(); return true;
}
/** Enrolls this browser in [g] with an invite nonce (member) or the recovery password (admin); throws a readable message. */
async function enroll(g, nonce, pw) {
  const r = await api(g, 'enroll', { deviceId, ...(nonce ? { nonce } : { recovery: await stretchFor(g, pw) }) });
  if (r.status === 404) throw new Error(t('no_data'));
  if (r.status === 401 || r.status === 429) throw new Error(nonce ? t('need_invite') : t('wrong_password'));
  if (r.status === 402) throw new Error(t('admin_limit', limitOf(srv(g))));
  if (!r.ok) throw new Error(t('sync_failed', await r.text()));
  g.device = (await r.json()).token; g.admin = !nonce;
}

/** Fetches and decrypts group [k] into its store. False (the reason is its status) if it couldn't. */
async function pull(k) {
  const g = groupOf(k), s = store(k);
  if (!g) return false;
  const bad = msg => { s.status = msg; s.failed = true; render(); return false; };
  if (!crypto.subtle) return bad(t('needs_https'));
  s.keys ??= await deriveKeys(k);
  let r;
  try {
    if (!g.device && !g.fresh && !await claim(g)) return bad(t('need_invite'));
    r = await fetch(`${srv(g)}/s/${s.keys.id}`, { headers: headers(g) });
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
    const g = groupOf(k);
    if (!has && !g.fresh) return render();
    const next = merge(s.state, change), mine = s.etag;
    const r = await fetch(`${srv(g)}/s/${s.keys.id}`, { method: 'PUT', headers: { ...headers(g), 'Content-Encoding': 'gzip', ...(mine && { 'If-Match': mine }) },
      body: await gz(JSON.stringify({ ciphertext: b64(await seal(s.keys, next)), ...(!mine && { deviceId, recovery: g.recovery }) })) });
    if (r.ok) {
      const j = await r.json();
      s.state = next; s.etag = j.etag;
      if (j.token) { g.device = j.token; g.admin = true; delete g.recovery; }
      if (g.fresh) { delete g.fresh; saveGroups(); }
      return render();
    }
    if (r.status !== 409) { s.status = r.status === 402 ? t('admin_limit', limitOf(srv(g))) : t('sync_failed', await r.text()); s.failed = true; return render(); }
  }
  s.status = t('busy'); s.failed = true; render();
}
const syncAll = () => Promise.all(['', ...groups.map(g => g.secret)].map(k => commit(k)));

const shareLink = (g, nonce) => `${srv(g)}/#k=${g.secret}${nonce ? '&n=' + nonce : ''}`;
const appLink = (g, nonce) => `howlongsince://join?h=${encodeURIComponent(srv(g))}#k=${g.secret}${nonce ? '&n=' + nonce : ''}`;
const newSecret = () => b64(crypto.getRandomValues(new Uint8Array(32))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
/** A fresh single-use invite (admins only), or null. */
const invite = async g => { const r = await api(g, 'invite', {}).catch(() => null); if (r?.status === 403) { g.admin = false; saveGroups(); } return r?.ok ? (await r.json()).nonce : null; };
const byName = (a, b) => (a.name || '￿').localeCompare(b.name || '￿');
const cut = s => s.length > 20 ? s.slice(0, 20) + '…' : s;

function fillGroups() {
  const opts = [];
  if (groups.length) { // like the phone's drawer: the views only matter once there is a group
    opts.push(['all', t('all_groups'), 'list']);
    if (store('').state.counters.some(c => !c.deleted)) opts.push(['', t('ungrouped'), 'inbox']);
    [...groups].sort(byName).forEach(g => opts.push([g.secret, g.name || '…', 'folder']));
  }
  if (!opts.some(o => o[0] === view)) view = 'all';
  $('#views').innerHTML = opts.map(([v, n, i]) => `<button class="nav ${v === view ? 'on' : ''}" data-view="${esc(v)}">${icon(i)}<span>${esc(n)}</span></button>`).join('');
  const g = groupOf(view);
  $('#title').textContent = view === 'all' ? 'How Long Since?' : cut(view === '' ? t('ungrouped') : g?.name || '…');
  $('#gearhead').hidden = !g;
  $('#sharehead').hidden = !g?.device;
  $('#selclear').hidden = $('#movehead').hidden = !selected.size;
  if (selected.size) { $('#title').textContent = t('n_selected', selected.size); $('#gearhead').hidden = $('#sharehead').hidden = true; }
  const ss = servers();
  $('#adminof').innerHTML = ss.length === 1 ? `${esc(t('admin_of'))}<br>${esc(adminLine(ss[0]))}` : `<button class="text" id="limitsbtn">${esc(t('check_limits'))}</button>`;
}
const servers = () => groups.length ? [...new Set(groups.map(srv))].sort() : [DEFAULT_SERVER];
$('#adminof').onclick = e => {
  if (e.target.closest('#limitsbtn')) ask(servers().map(s => `${host(s)}\n${t('admin_of')} ${adminLine(s)}`).join('\n\n'), { ok: t('close'), info: true });
}

/* ---------- Joining, new groups, sharing ---------- */

/** Step 1 of joining: a usable link not joined yet, or null (with a message). */
function checkLink(link) {
  const p = parseLink(link);
  if (!p) return toast(t('bad_link')), null;
  p.server ||= base; // a bare k=... is for this page's server
  const known = groupOf(p.secret);
  if (known) { view = p.secret; render(); return toast(t('already_joined', known.name || '…')), null; }
  if (!crypto.subtle) return toast(t('needs_https')), null;
  return p;
}
let joining = null; // the parsed link of step 2
function openJoin(p = null) {
  joining = p;
  $('#jlink').hidden = !!p; $('#jlink').value = ''; $('#jlink').required = !p;
  $('#jtext').hidden = !p; $('#jtext').textContent = p ? t('join_on', host(p.server)) : '';
  const needPw = p && !p.nonce && !p.token; // no invite: the recovery password makes this browser an admin
  $('#jpw').hidden = !needPw; $('#jpw').required = !!needPw; $('#jpw').value = '';
  $('#japp').hidden = !p?.nonce;
  $('#jgo').textContent = t(p ? 'join' : 'next');
  if (!$('#joindlg').open) $('#joindlg').showModal();
  (p ? $('#jpw') : $('#jlink')).focus();
}
$('#jform').onsubmit = async e => {
  e.preventDefault();
  if (!joining) { const p = checkLink($('#jlink').value); return p ? openJoin(p) : $('#joindlg').close(); }
  const p = joining, g = { secret: p.secret, name: '', server: p.server };
  $('#joindlg').close();
  try {
    if (p.token && !p.nonce) { g.token = p.token; if (!await claim(g)) return toast(t('need_invite')); }
    else await enroll(g, p.nonce, $('#jpw').value);
    groups.push(g); view = g.secret; saveGroups(); render(); commit(g.secret);
  } catch (e) { toast(e instanceof TypeError ? t('unreachable') : e.message); }
};
$('#japp').onclick = () => { $('#joindlg').close(); location.href = appLink(joining, joining.nonce); }; // the invite works once: app or browser
$('#jcancel').onclick = () => $('#joindlg').close();

/** Asks a new group's server and recovery password, and its name unless [name] is given: { name, server, pw } or null. */
function askNewGroup(name) {
  const d = $('#newdlg'), n = $('#nname'), url = $('#nurl');
  n.hidden = !!name; n.required = !name; n.value = name || '';
  const pwText = () => $('#npwtext').textContent = t('recovery_new', name || n.value || '…');
  n.oninput = pwText; pwText();
  $('#ndefault').textContent = t('default_server', host(DEFAULT_SERVER));
  d.querySelector('[value=default]').checked = true; url.hidden = true; url.required = false; url.value = base; // custom starts as this page's server
  $('#nform').onchange = e => { if (e.target.name === 'srv') url.hidden = !(url.required = e.target.value === 'custom'); };
  $('#npw').value = ''; d.returnValue = ''; d.showModal(); (name ? $('#npw') : n).focus();
  return new Promise(res => d.onclose = () => res(d.returnValue !== 'ok' ? null : {
    name: name || n.value.trim(), pw: $('#npw').value,
    server: url.required ? withScheme(url.value.trim()) : DEFAULT_SERVER,
  }));
}
/** A new group: a random key and a recovery password made here; the first blob is uploaded (and this browser enrolled as admin) as soon as the server answers. */
async function makeGroup(name) {
  const a = await askNewGroup(name);
  if (!a?.name) return null;
  const secret = newSecret();
  const g = { secret, name: name || uniqueName(a.name, groups.map(g => g.name)), server: a.server, nameUpdatedAt: Date.now(), fresh: true,
    recovery: await stretch(a.pw, (await deriveKeys(secret)).id) };
  groups.push(g); saveGroups();
  return g;
}
async function newGroup() {
  if (!crypto.subtle) return toast(t('needs_https'));
  const g = await makeGroup();
  if (!g) return;
  view = g.secret; render(); commit(g.secret);
}

/** The link and a QR code of it: a fresh invite for admins, the key backup (needs the recovery password) for members. */
async function share(secret) {
  const g = groupOf(secret);
  if (!g) return;
  const nonce = g.admin ? await invite(g) : null;
  const link = shareLink(g, nonce), q = qrcode(0, 'M');
  q.addData(link); q.make();
  $('#sharetext').innerHTML = esc(t(nonce ? 'share_warning' : 'share_backup')).split('\n\n').map(p => `<p>${p.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')}</p>`).join('');
  $('#sharename').textContent = g.name || '…';
  $('#qr').innerHTML = q.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
  $('#copy').textContent = t('copy_link');
  $('#nativeshare').hidden = !navigator.share;
  $('#nativeshare').onclick = () => navigator.share({ title: g.name, text: link }).catch(() => {});
  $('#copy').onclick = async () => { await navigator.clipboard.writeText(link); $('#copy').textContent = t('link_copied'); };
  $('#share').showModal();
}
$('#closeshare').onclick = () => $('#share').close();

/* ---------- Time ---------- */

/** The two-row form: [top, bottom], like the app's. */
function parts(ms) {
  const s = Math.max(0, Math.floor(ms / 1000)), m = Math.floor(s / 60), h = Math.floor(m / 60), d = Math.floor(h / 24), y = Math.floor(d / 365);
  return h < 1 ? [`${m}m`, `${s % 60}s`] : d < 1 ? [`${h}h`, `${m % 60}m`] : y < 1 ? [`${d}${t('d')}`, `${h % 24}h`] : [`${y}${t('y')}`, `${d % 365}${t('d')}`];
}
/** The one-line form. */
const short = ms => ms < 36e5 ? parts(ms)[0] : parts(ms).join(' ');
const fmtDate = at => new Date(at).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
const timeBlock = r => { const [a, b] = parts(Date.now() - r.last); return `<div class="tb" data-since="${r.last}"><span class="t1">${a}</span><span class="t2">${b}</span></div>`; };
const ringOf = r => `<button class="rb ${r.c.notifyAfterMs ? 'has' : ''} ${r.overdue ? 'over' : ''}" data-reset="${r.c.id}" data-since="${r.last}" data-ms="${r.c.notifyAfterMs ?? ''}"
  aria-label="${esc(t('reset_label', r.c.label))}"><span>${icon('reset')}</span></button>`;

// Times move every second while any counter is under an hour old, else every 30 s; the top number slides in when it changes.
let tickTimer;
function tick() {
  clearTimeout(tickTimer);
  const now = Date.now();
  let fresh = false;
  document.querySelectorAll('.tb').forEach(el => {
    const ms = now - el.dataset.since, [a, b] = parts(ms), t1 = el.firstChild;
    if (ms < 36e5) fresh = true;
    if (t1.textContent !== a) { t1.textContent = a; t1.classList.remove('anim'); void t1.offsetWidth; t1.classList.add('anim'); }
    el.lastChild.textContent = b;
  });
  document.querySelectorAll('.rb.has').forEach(el => {
    const p = (now - el.dataset.since) / el.dataset.ms;
    el.style.setProperty('--p', Math.min(p, 1)); el.classList.toggle('over', p >= 1);
  });
  tickTimer = setTimeout(tick, fresh ? 1000 : 30000);
}

/* ---------- List ---------- */

const event = (counterId, source, at = Date.now()) => (at = Math.floor(at / 1000) * 1000, { id: `${counterId}@${at}`, counterId, at, source, deleted: false, deviceId });
// Same hue as the app's tagColor: Java String.hashCode() mod 360.
const tagHue = t => { let h = 0; for (const ch of t) h = Math.imul(h, 31) + ch.charCodeAt(0) | 0; return (h % 360 + 360) % 360; };
const tagColor = t => `hsl(${tagHue(t)} 55% var(--tag-l))`;
let debug = localStorage.getItem('debug') === '1';
let filter = null, showArchived = false, sort = localStorage.getItem('sort') || 'due';
let folded = new Set(); try { folded = new Set(JSON.parse(localStorage.getItem('folded') || '[]')); } catch {}

const rowsOf = k => { const { counters, events } = store(k).state; return counters.filter(c => !c.deleted).map(c => {
  const ev = events.filter(e => e.counterId === c.id && !e.deleted).sort((a, b) => b.at - a.at), last = ev[0]?.at ?? 0;
  return { c, ev, last, k, overdue: c.notifyAfterMs != null && Date.now() - last >= c.notifyAfterMs };
}); };
const rowOf = id => rowsOf(ownerOf(id)).find(r => r.c.id === id);
const groupName = k => k === '' ? t('ungrouped') : groupOf(k)?.name || '…';
const avgOf = ev => ev.length >= 3 ? (ev[0].at - ev.at(-1).at) / (ev.length - 1) : 0;

function cardHtml(r) {
  const { c, k, last } = r, tags = tagList(c.tags);
  return `<div class="card${selected.has(c.id) ? ' sel' : ''}" role="button" tabindex="0" data-open="${c.id}" style="--bar:${tags.length ? tagColor(tags[0]) : 'transparent'}">${timeBlock(r)}
    <div class="mid"><b>${esc(c.label)}</b><span class="muted">${fmtDate(last)}</span>${k && groups.length >= 2 ? `<span class="muted">${esc(groupName(k))}</span>` : ''}${tags.length ? `<span class="muted">${tags.map(x => '#' + esc(x)).join(' · ')}</span>` : ''}</div>
    ${ringOf(r)}</div>`;
}

function render() {
  selected.forEach(id => ownerOf(id) || selected.delete(id));
  fillGroups();
  const pool = (view === 'all' ? ['', ...groups.map(g => g.secret)] : [view]).flatMap(rowsOf);
  const hasArchived = pool.some(r => r.c.archived);
  if (!hasArchived) showArchived = false;
  const live = pool.filter(r => !!r.c.archived === showArchived);
  const big = pool.filter(r => !r.c.archived).length > 5; // the filter bar only earns its place past 5 counters
  if (!big) { $('#q').value = ''; filter = null; }
  const allTags = big ? [...new Set(live.flatMap(r => tagList(r.c.tags)))].sort() : [];
  if (!allTags.includes(filter)) filter = null;
  $('#filters').hidden = !big && !hasArchived;
  $('#searchbox').hidden = !big;
  $('#chips').innerHTML = (big ? `<button class="chip" data-sort>${icon('sort')}${t('sort_' + sort)}</button>` : '')
    + allTags.map(x => `<button class="chip ${x === filter ? 'on' : ''}" data-tag="${esc(x)}"><i class="dot" style="background:${tagColor(x)}"></i>${esc(x)}</button>`).join('')
    + (hasArchived ? `<button class="chip ${showArchived ? 'on' : ''}" data-archived>${t('archived')}</button>` : '');
  const q = $('#q').value.trim().toLowerCase();
  const shown = live.filter(r => !filter || tagList(r.c.tags).includes(filter))
    .filter(r => !q || r.c.label.toLowerCase().includes(q) || r.c.tags.includes(q));
  const order = sort === 'name' ? (a, b) => a.c.label.localeCompare(b.c.label)
    : sort === 'recent' ? (a, b) => b.last - a.last
    : (a, b) => b.overdue - a.overdue || a.last - b.last; // overdue first, then oldest reset
  const sections = view === 'all' && groups.length > 0;
  $('#fab').hidden = sections; // with sections, each header has its own +
  $('#list').innerHTML = !pool.length ? `<div class="empty"><h2>${t('empty_title')}</h2><p class="muted">${t('empty_hint')}</p></div>`
    : !sections ? shown.sort(order).map(cardHtml).join('')
    : ['', ...[...groups].sort(byName).map(g => g.secret)].map(k => {
      const rows = shown.filter(r => r.k === k).sort(order), s = store(k);
      if (!rows.length && !s.failed && (k === '' || q || filter || showArchived)) return ''; // empty groups stay, so their + can be reached
      const fold = folded.has(k) && !q; // a search opens every section
      return `<div class="sec" data-fold="${esc(k)}"><span class="nm">${esc(groupName(k))} · ${rows.length}</span>${s.failed ? `<span class="bad">${esc(s.status)}</span>` : ''}<span class="sp"></span>
        <button class="ib ${fold ? 'inv' : ''}" data-add="${esc(k)}" aria-label="${t('new_counter')}">${icon('add')}</button><span class="ib">${icon(fold ? 'down' : 'up')}</span></div>`
        + (fold ? '' : rows.map(cardHtml).join(''));
    }).join('');
  if (detailId) renderDetail();
  if (gsetId) { const s = store(gsetId), el = $('#gstatus'); if (el) { el.textContent = s.status; el.className = s.failed ? 'bad' : 'muted'; } }
  tick();
}

/** Reset now, with a haptic tick and an undo. */
function resetNow(id) {
  const ev = event(id, 'manual'), k = ownerOf(id);
  navigator.vibrate?.(15);
  commit(k, { counters: [], events: [ev] });
  toast(t('reset_label', counterOf(id).label), t('undo'), () => commit(k, { counters: [], events: [{ ...ev, deleted: true }] }));
}

const toggleSel = id => { selected.has(id) ? selected.delete(id) : selected.add(id); render(); };
let press, longPressed = false;
$('#list').onpointerdown = e => {
  const el = e.target.closest('[data-open]'); longPressed = false; clearTimeout(press);
  if (el && !e.target.closest('[data-reset]')) press = setTimeout(() => { longPressed = true; navigator.vibrate?.(15); toggleSel(el.dataset.open); }, 500);
};
for (const ev of ['pointerup', 'pointerleave', 'pointercancel']) $('#list').addEventListener(ev, () => clearTimeout(press));
$('#list').oncontextmenu = e => e.target.closest('[data-open]') && e.preventDefault(); // no browser menu on a long-press
$('#list').onclick = e => {
  if (longPressed) return longPressed = false; // the press that selected must not also open
  const el = e.target.closest('[data-add], [data-fold], [data-reset], [data-open]');
  if (!el) return;
  const d = el.dataset;
  if (d.add != null) return openCounter(null, d.add);
  if (d.reset) return resetNow(d.reset);
  if (d.open) return selected.size || e.ctrlKey || e.metaKey ? toggleSel(d.open) : openDetail(d.open);
  folded.has(d.fold) ? folded.delete(d.fold) : folded.add(d.fold);
  localStorage.setItem('folded', JSON.stringify([...folded]));
  render();
};
$('#list').onkeydown = e => { if (e.key === 'Enter' && e.target.dataset.open) selected.size ? toggleSel(e.target.dataset.open) : openDetail(e.target.dataset.open); };
$('#selclear').onclick = () => { selected.clear(); render(); };

/** Asks for a destination: a group's secret, '' for Ungrouped, null if cancelled. */
function pickGroup() {
  const d = $('#movedlg');
  $('#movelist').innerHTML = [['', t('ungrouped')], ...[...groups].sort(byName).map(g => [g.secret, g.name || '…'])]
    .map(([k, n]) => `<button type="button" class="text" data-to="${esc(k)}">${esc(n)}</button>`).join('');
  d.showModal();
  return new Promise(res => {
    d.onclick = e => { const b = e.target.closest('[data-to]'); if (b || e.target.id === 'movecancel') { res(b ? b.dataset.to : null); d.close(); } };
    d.onclose = () => res(null);
  });
}
$('#movehead').onclick = async () => {
  const to = await pickGroup(); if (to === null) return;
  const rows = [...selected].map(rowOf).filter(r => r && r.k !== to);
  if (rows.some(r => r.k !== '') && !await ask(t('move_title') + '\n\n' + t('move_text'))) return; // leaving a synced group removes it for everyone in it
  selected.clear();
  for (const r of rows) await moveCounter(r.k, to, { ...r.c, updatedAt: Date.now() });
  render();
};
$('#chips').onclick = e => {
  const b = e.target.closest('button'); if (!b) return;
  if (b.dataset.sort != null) { sort = { due: 'recent', recent: 'name', name: 'due' }[sort]; localStorage.setItem('sort', sort); }
  if (b.dataset.archived != null) showArchived = !showArchived;
  if (b.dataset.tag) filter = filter === b.dataset.tag ? null : b.dataset.tag;
  render();
};
$('#q').oninput = render;
$('#fab').onclick = () => openCounter(null, view === 'all' ? '' : view);
addEventListener('scroll', () => document.body.classList.toggle('scrolled', scrollY > 0), { passive: true });

/* ---------- Full screens: each one is a history entry, so Back closes it ---------- */

const stack = [];
let detailId = null, gsetId = null;
function openScreen(id) {
  const el = $('#' + id); el.hidden = false; el.scrollTop = 0; el.classList.remove('scrolled');
  stack.push(id); history.pushState({ screen: id }, '');
  document.body.classList.remove('drawer');
}
addEventListener('popstate', () => {
  const id = stack.pop(); if (!id) return;
  $('#' + id).hidden = true;
  if (id === 'detail') detailId = null;
  if (id === 'gset') gsetId = null;
});
document.querySelectorAll('.back').forEach(b => b.onclick = () => history.back());
document.querySelectorAll('.screen').forEach(s => s.addEventListener('scroll', () => s.classList.toggle('scrolled', s.scrollTop > 0), { passive: true }));
document.addEventListener('keydown', e => {
  if (e.key !== 'Escape' || document.querySelector('dialog[open]')) return;
  if (document.body.classList.contains('drawer')) document.body.classList.remove('drawer');
  else if (stack.length) history.back();
});

/* ---------- Detail ---------- */

/** Bars of the intervals between resets, oldest left, last 30; same as the app's IntervalChart. */
function chart(ev) {
  const gaps = ev.slice(0, -1).map((e, i) => e.at - ev[i + 1].at).reverse().slice(-30);
  if (gaps.length < 2) return '';
  const max = Math.max(...gaps, 1), w = 100 / gaps.length;
  const avg = gaps.reduce((a, b) => a + b) / gaps.length;
  return `<h3 class="sh">${t('intervals', short(avg))}</h3>
    <svg class="chart" viewBox="0 0 100 64" preserveAspectRatio="none">${gaps.map((g, i) => {
      const h = Math.max(g / max * 64, 1.5);
      return `<rect x="${i * w + w * .15}" y="${64 - h}" width="${w * .7}" height="${h}" rx=".8"><title>${short(g)}</title></rect>`;
    }).join('')}</svg>`;
}

function openDetail(id) { detailId = id; renderDetail(); openScreen('detail'); }
function renderDetail() {
  const r = ownerOf(detailId) != null && rowOf(detailId);
  if (!r) { if (stack.at(-1) === 'detail') history.back(); return; }
  const { c, ev } = r;
  $('#dtitle').textContent = c.label;
  $('#dbody').innerHTML = `<div class="summary">${timeBlock(r)}<span class="muted">${t('entries', ev.length)}</span>${ringOf(r)}</div>
    ${c.archived ? `<p class="hint muted">${t('archived_hint')}</p>` : ''}
    ${c.nfcTagId ? `<div class="hint row"><span class="muted sp">${t('nfc_paired')}</span><button class="text" data-unpair>${t('unpair')}</button></div>` : ''}
    ${chart(ev)}
    <ol class="tl">${ev.map((e, i) => {
      const meta = [ev[i + 1] && t('after', short(e.at - ev[i + 1].at)), e.source !== 'manual' && t(e.source), nameOfDevice(e.deviceId)].filter(Boolean);
      return `<li><b>${fmtDate(e.at)}</b>${meta.length ? `<span class="muted">${esc(meta.join(' · '))}</span>` : ''}${e.note ? `<span class="note">${esc(e.note)}</span>` : ''}
        ${ev.length > 1 ? `<button class="ib" data-del-event="${e.id}" aria-label="${t('delete')}">${icon('close')}</button>` : ''}</li>`;
    }).join('')}</ol>`;
}
$('#dbody').onclick = e => {
  const b = e.target.closest('button'); if (!b) return;
  const d = b.dataset;
  if (d.reset) openReset(d.reset);
  if (d.unpair != null) commit(ownerOf(detailId), { counters: [{ ...counterOf(detailId), nfcTagId: null, updatedAt: Date.now() }], events: [] });
  if (d.delEvent) { const k = ownerOfEvent(d.delEvent); commit(k, { counters: [], events: [{ ...store(k).state.events.find(x => x.id === d.delEvent), deleted: true }] }); }
};
$('#dedit').onclick = () => openCounter(counterOf(detailId));

// Reset from the detail: a note, then Now or Earlier (a past date and time).
function openReset(id) {
  const f = $('#rform'); f.reset(); f.dataset.id = id;
  $('#rtitle').textContent = counterOf(id).label;
  f.elements.at.hidden = true; f.elements.at.required = false; $('#rearlier').hidden = false; $('#rnow').textContent = t('now');
  $('#resetdlg').showModal();
}
$('#rearlier').onclick = () => {
  const at = $('#rform').elements.at, d = new Date();
  at.max = new Date(d - d.getTimezoneOffset() * 6e4).toISOString().slice(0, 16);
  at.hidden = false; at.required = true; $('#rearlier').hidden = true; $('#rnow').textContent = t('reset');
  at.focus(); try { at.showPicker(); } catch {}
};
$('#rcancel').onclick = () => $('#resetdlg').close();
$('#rform').onsubmit = e => {
  e.preventDefault();
  const f = e.target.elements, id = e.target.dataset.id, at = f.at.hidden ? Date.now() : new Date(f.at.value).getTime(), note = f.note.value.trim();
  if (at > Date.now()) return toast(t('pick_past'));
  $('#resetdlg').close();
  navigator.vibrate?.(15);
  commit(ownerOf(id), { counters: [], events: [{ ...event(id, 'manual', at), ...(note && { note }) }] });
};

/* ---------- New / edit counter ---------- */

let editing = null; // the counter being edited, or null for a new one
function openCounter(c, grp = ownerOf(c?.id)) {
  editing = c;
  const f = $('#cform'), ms = c?.notifyAfterMs, units = unitsFor(ms, debug);
  f.reset();
  $('#ctitle').textContent = t(c ? 'edit_counter' : 'new_counter');
  f.elements.label.value = c?.label ?? '';
  f.elements.tags.value = c ? tagList(c.tags).join(', ') : '';
  $('#cgroups').hidden = !groups.length;
  $('#cgchips').innerHTML = [['', t('ungrouped')], ...[...groups].sort(byName).map(g => [g.secret, g.name || '…'])]
    .map(([v, n]) => `<label class="chip"><input type="radio" name="group" value="${esc(v)}" ${v === grp ? 'checked' : ''}>${esc(n)}</label>`).join('');
  // Largest unit that divides the stored value evenly, like the app.
  const [unit, size] = ms ? units.filter(([, n]) => ms % n === 0).pop() ?? units[0] : UNITS[2];
  f.elements.amount.value = ms ? ms / size : '';
  $('#cunits').innerHTML = units.map(([u]) => `<label class="chip"><input type="radio" name="unit" value="${u}" ${u === unit ? 'checked' : ''}>${t(u)}</label>`).join('');
  const avg = c ? avgOf(rowOf(c.id).ev) : 0;
  $('#cavg').hidden = !avg; $('#cavg').dataset.avg = avg; $('#cavg').textContent = avg ? t('use_average', short(avg)) : '';
  $('#cdel').hidden = $('#carch').hidden = !c;
  $('#carch').textContent = t(c?.archived ? 'unarchive' : 'archive');
  $('#cdlg').showModal();
  if (!c) f.elements.label.focus();
}
$('#cavg').onclick = () => {
  const f = $('#cform').elements, a = Number($('#cavg').dataset.avg);
  const [u, n] = a < 36e5 && $('#cunits [value=minutes]') ? UNITS[0] : a < 1728e5 ? UNITS[1] : UNITS[2]; // minutes (debug only) under an hour, hours under 2 days, else days
  $(`#cunits [value=${u}]`).checked = true; f.amount.value = Math.max(1, Math.round(a / n));
};
$('#ccancel').onclick = () => $('#cdlg').close();
$('#carch').onclick = () => {
  $('#cdlg').close();
  commit(ownerOf(editing.id), { counters: [{ ...editing, archived: !editing.archived, updatedAt: Date.now() }], events: [] });
};
$('#cdel').onclick = async () => {
  const c = editing;
  $('#cdlg').close();
  if (!await ask(t('confirm_delete'), { ok: t('delete'), danger: true })) return;
  commit(ownerOf(c.id), { counters: [{ ...c, deleted: true, updatedAt: Date.now() }], events: [] });
  if (detailId === c.id) history.back();
};
$('#cform').onsubmit = async e => {
  e.preventDefault();
  const f = new FormData(e.target), amount = Number(f.get('amount'));
  const fields = { label: f.get('label').trim(), tags: tagList(f.get('tags')).join(','), updatedAt: Date.now(),
    notifyAfterMs: amount > 0 ? amount * UNITS.find(([u]) => u === f.get('unit'))[1] : null };
  $('#cdlg').close();
  if (!editing) {
    const c = { id: crypto.randomUUID(), nfcTagId: null, deleted: false, archived: false, ...fields };
    return commit(f.get('group') ?? '', { counters: [c], events: [event(c.id, 'create')] });
  }
  const edited = { ...editing, ...fields }, from = ownerOf(edited.id), to = f.get('group') ?? from;
  if (to === from) return commit(from, { counters: [edited], events: [] });
  if (from !== '' && !await ask(t('move_title') + '\n\n' + t('move_text'))) return; // leaving a synced group removes it for everyone in it
  moveCounter(from, to, edited);
};

/** Moves a counter, with its events, to another group (or Ungrouped): written to the new place first, then a tombstone in the old one. */
async function moveCounter(from, to, edited) {
  const events = store(from).state.events.filter(e => e.counterId === edited.id);
  await commit(to, { counters: [edited], events });
  if (to !== '' && store(to).failed) return; // not saved there, so it stays where it was; the section's status says why
  await commit(from, { counters: [{ ...edited, deleted: true }], events: [], drop: [edited.id] });
}

/* ---------- Group settings ---------- */

function openGset(k) { gsetId = k; renderGset(); openScreen('gset'); }
function renderGset() {
  const g = groupOf(gsetId); if (!g) return;
  const s = store(g.secret), pwLabel = !g.device ? 'pw_join' : g.admin ? 'change_password' : 'become_admin';
  $('#gtitle').textContent = g.name || '…';
  $('#gshare').hidden = !g.device;
  $('#gbody').innerHTML = `
    <form class="row" id="gname"><label class="field"><span>${t('name')}</span><input name="name" value="${esc(g.name || '')}" required></label><button>${t('save')}</button></form>
    <h3 class="sh">${t('sync')}</h3>
    <p class="muted">${esc(t('server', srv(g)))}</p>
    <p id="gstatus" class="${s.failed ? 'bad' : 'muted'}">${esc(s.status)}</p>
    ${g.admin ? `<h3 class="sh">${t('devices')}</h3><div id="gdev"></div>` : ''}
    <form class="row" id="gpw"><label class="field"><span>${t(pwLabel)}</span><input name="pw" type="password" minlength="6" autocomplete="new-password" required></label><button hidden>${t('save')}</button></form>
    <div class="row wrap"><button class="tonal" id="gsync">${t('sync_now')}</button><button class="tonal" id="gapp">${t('open_app')}</button><span class="sp"></span><button class="danger" id="gforget">${t('forget')}</button></div>`;
  if (g.admin) loadDevices(g);
}
$('#gbody').oninput = e => { if (e.target.name === 'pw') $('#gpw button').hidden = !e.target.value; };
$('#gbody').onsubmit = async e => {
  e.preventDefault();
  const g = groupOf(gsetId), f = e.target.elements;
  if (e.target.id === 'gname') {
    const name = f.name.value.trim();
    if (!name || name === g.name) return;
    g.name = uniqueName(name, groups.filter(x => x !== g).map(x => x.name)); saveGroups(); render(); renderGset();
    return commit(g.secret, { counters: [], events: [], name: g.name, nameUpdatedAt: Date.now() });
  }
  const pw = f.pw.value;
  if (pw.length < 6) return toast(t('pw_short'));
  try {
    const ok = !g.device ? (await enroll(g, null, pw), saveGroups(), commit(g.secret), true) : g.admin ? await setRecovery(g, pw) : await promote(g, pw);
    if (ok) renderGset();
  } catch (e) { toast(e instanceof TypeError ? t('unreachable') : e.message); }
};
$('#gbody').onclick = async e => {
  const g = groupOf(gsetId), b = e.target.closest('button'); if (!b) return;
  if (b.id === 'gsync') { await commit(g.secret); renderGset(); }
  if (b.id === 'gapp') location.href = appLink(g, g.admin ? await invite(g) : null); // admins hand the app a fresh invite; members open it with the key only
  if (b.id === 'gforget') {
    const r = await ask(t('forget_backup'), { input: 'text', value: shareLink(g), ok: t('forget_keep'), alt: t('forget_purge'), danger: true }); // a text box, so the link can be copied
    if (r === null) return;
    if (r !== ALT) { // keep the counters here, in Ungrouped (which never syncs)
      const { counters, events } = store(g.secret).state, live = counters.filter(c => !c.deleted);
      if (live.length) await commit('', { counters: live, events: events.filter(e => live.some(c => c.id === e.counterId)) });
    }
    if (g.device) api(g, 'leave', {}).catch(() => {}); // the server deletes the group once no device is left
    groups = groups.filter(x => x !== g); stores.delete(g.secret); view = 'all'; saveGroups();
    history.back(); render();
  }
};
$('#gshare').onclick = () => share(gsetId);

/** Admins: every device of the group, named from the group data, with revoke. */
async function loadDevices(g) {
  const r = await api(g, 'devices').catch(() => null), el = $('#gdev');
  if (!el) return;
  if (!r?.ok) { el.innerHTML = `<p class="muted">${r?.status === 403 ? t('need_invite') : t('unreachable')}</p>`; return; }
  const list = await r.json(), named = store(g.secret).state.devices ?? [];
  const name = d => d.id === deviceId ? t('this_device') : named.find(x => x.id === d.id)?.name || d.id.slice(0, 8);
  el.innerHTML = list.map(d => `<div class="drow"><span class="two"><span>${esc(name(d))}</span><small class="muted">${t(d.admin ? 'admin' : 'member')} · ${esc(t('last_seen', new Date(d.lastSeen * 1000).toLocaleDateString()))}</small></span>
    ${d.id === deviceId ? '' : `<button class="text danger" data-revoke="${esc(d.id)}">${t('revoke')}</button>`}</div>`).join('');
  el.onclick = async e => {
    const id = e.target.closest('[data-revoke]')?.dataset.revoke, d = list.find(d => d.id === id);
    if (!d || !await ask(t('confirm_revoke', name(d)), { ok: t('revoke'), danger: true })) return;
    await api(g, 'revoke', { deviceId: id });
    loadDevices(g);
  };
}

/* ---------- Drawer and top bar ---------- */

$('#menu').onclick = () => document.body.classList.add('drawer');
$('#scrim').onclick = () => document.body.classList.remove('drawer');
$('#views').onclick = e => { const b = e.target.closest('[data-view]'); if (b) { view = b.dataset.view; document.body.classList.remove('drawer'); render(); scrollTo(0, 0); } };
$('#home').onclick = () => { view = 'all'; document.body.classList.remove('drawer'); render(); };
$('#addgroup').onclick = () => { document.body.classList.remove('drawer'); openJoin(); };
$('#newgroup').onclick = () => { document.body.classList.remove('drawer'); newGroup(); };
$('#opensettings').onclick = () => openScreen('settings');
$('#sharehead').onclick = () => share(view);
$('#gearhead').onclick = () => openGset(view);

/* ---------- Settings ---------- */

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
$('#devnameval').textContent = deviceName;
$('#devname').onclick = async () => {
  const v = await ask(t('device_name_hint'), { input: 'text', value: deviceName, ok: t('save') });
  if (v === null) return;
  deviceName = v.trim().slice(0, 40) || 'Web'; deviceNameAt = Date.now(); $('#devnameval').textContent = deviceName;
  localStorage.setItem('deviceName', deviceName); localStorage.setItem('deviceNameAt', deviceNameAt);
  render(); syncAll();
};
applyTheme();

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
  try { items = parseBackup(await f.text()); } catch (e) { return toast(t('cant_read', e.message)); }
  $('#importtext').textContent = t('import_q', items.length);
  const go = async replace => {
    $('#importdlg').close();
    for (const entry of planImport(items, replace, groups.map(g => g.name), t('imported_name'))) await applyImport(entry);
    toast(t('done_imported', items.length));
  };
  $('#doreplace').onclick = () => go(true); $('#dokeep').onclick = () => go(false); $('#nogo').onclick = () => $('#importdlg').close();
  $('#importdlg').showModal();
};

if (location.hash.length > 1) { // opened from a share link: remember the group, then hide the key from the address bar
  const frag = location.hash.slice(1);
  history.replaceState(null, '', location.pathname + location.search);
  const p = checkLink(frag);
  if (p) openJoin(p); // deep links skip the paste step
}
render();
syncAll();
setInterval(syncAll, 15 * 60000); // pick up changes from the phone
document.addEventListener('visibilitychange', () => document.hidden || syncAll()); // fresh data when coming back to the tab

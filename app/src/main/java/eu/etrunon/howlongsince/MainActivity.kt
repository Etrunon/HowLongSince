package eu.etrunon.howlongsince

import android.Manifest
import android.os.Build
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = Db.get(this).dao()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        // Skip on recreate / relaunch from recents, or the same NFC tap would reset twice.
        val replay = savedInstanceState != null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (!replay) handle(intent)
        val prefs = getSharedPreferences("settings", 0)
        // Push every local change; converges because merge writes nothing when already in sync.
        lifecycleScope.launch { dao.counters().drop(1).debounce(1500).collect { sync(this@MainActivity) } }
        schedulePeriodicSync(this)
        // Launcher long-press: reset the counters most in need of it.
        lifecycleScope.launch { dao.counters().collect { list ->
            val now = System.currentTimeMillis()
            ShortcutManagerCompat.setDynamicShortcuts(this@MainActivity, list.filter { !it.counter.archived }.sortedByDescending { it.overdue(now) }
                .take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(this@MainActivity).coerceAtMost(4)).map { c ->
                    ShortcutInfoCompat.Builder(this@MainActivity, "reset-${c.counter.id}")
                        .setShortLabel(getString(R.string.reset_label, c.counter.label).take(25)).setLongLabel(getString(R.string.reset_label, c.counter.label))
                        .setIcon(IconCompat.createWithResource(this@MainActivity, R.mipmap.ic_launcher))
                        .setIntent(Intent(RESET_ACTION, null, this@MainActivity, MainActivity::class.java).putExtra("id", c.counter.id))
                        .build()
                })
        } }
        setContent {
            // null = follow the system until the user flips the switch.
            var dark by remember { mutableStateOf(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null) }
            val effective = dark ?: isSystemInDarkTheme()
            AppTheme(effective) {
                CounterList(dao, openId, onOpened = { openId = null }, dark = effective,
                    onDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() })
            }
        }
    }

    // Counter to show, e.g. from a notification tap; cleared once the UI has opened it.
    private var openId by mutableStateOf<Long?>(null)

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { sync(this@MainActivity) } // pull changes made on the web
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        intent.getLongExtra("id", 0).takeIf { it != 0L }?.let { openId = it } // notification tap
        if (intent.action == RESET_ACTION) return resetAndShow(intent.getLongExtra("id", 0), "manual")
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val id = parseResetUri(intent.dataString) ?: return
        @Suppress("DEPRECATION") val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        lifecycleScope.launch {
            val dao = Db.get(this@MainActivity).dao()
            val c = dao.counter(id)
            // URL copied for another app to write: the first tag that carries it gets paired.
            val prefs = getSharedPreferences("settings", 0)
            if (c != null && tag != null && prefs.getLong("adoptTag", 0) == id) {
                prefs.edit().remove("adoptTag").apply()
                dao.pair(id, tag.id.toHex())
                Toast.makeText(this@MainActivity, getString(R.string.tag_paired), Toast.LENGTH_SHORT).show()
                openId = id
                return@launch
            }
            // The tag must still be the one paired to this counter (unpair / re-pair invalidates old tags).
            if (c == null || tag == null || c.counter.nfcTagId != tag.id.toHex()) {
                Toast.makeText(this@MainActivity, getString(R.string.tag_not_paired), Toast.LENGTH_SHORT).show()
                return@launch
            }
            resetAndShow(id, "nfc")
        }
    }

    private fun resetAndShow(id: Long, source: String) { lifecycleScope.launch {
        val dao = Db.get(this@MainActivity).dao()
        val c = dao.counter(id) ?: return@launch
        dao.reset(id, source)
        reschedule(this@MainActivity, id)
        window.decorView.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        Toast.makeText(this@MainActivity, getString(R.string.reset_done, c.counter.label), Toast.LENGTH_SHORT).show()
        openId = id
    } }
}

const val RESET_ACTION = "eu.etrunon.howlongsince.RESET"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterList(dao: CounterDao, openRequest: Long?, onOpened: () -> Unit, dark: Boolean, onDark: (Boolean) -> Unit) {
    val counters by dao.counters().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var editing by remember { mutableStateOf<Counter?>(null) } // id 0 = new
    var filter by remember { mutableStateOf<String?>(null) }

    var showArchived by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val pool = counters.filter { it.counter.archived == showArchived }
    val hasArchived = counters.any { it.counter.archived }
    if (!hasArchived) showArchived = false
    val allTags = pool.flatMap { tagList(it.counter.tags) }.distinct().sorted()
    if (filter !in allTags) filter = null
    val prefs0 = remember { ctx.getSharedPreferences("settings", 0) }
    var sort by remember { mutableStateOf(prefs0.getString("sort", "due")!!) }
    val shown = pool.filter { filter == null || filter in tagList(it.counter.tags) }
        .filter { query.isBlank() || it.counter.label.contains(query.trim(), true) || it.counter.tags.contains(query.trim(), true) }
        .let { l -> when (sort) {
            "name" -> l.sortedBy { it.counter.label.lowercase() }
            "recent" -> l.sortedByDescending { it.lastAt }
            else -> l.sortedByDescending { it.overdue(now) } // overdue first; stable, so oldest reset next
        } }
    var open by remember { mutableStateOf<Long?>(null) }
    val openC = counters.find { it.counter.id == open }
    val prefs = remember { ctx.getSharedPreferences("settings", 0) }
    var debug by remember { mutableStateOf(prefs.getBoolean("debug", false)) }
    var settings by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(openRequest) { openRequest?.let { open = it; settings = false; onOpened() } }

    if (settings) {
        BackHandler { settings = false }
        Settings(dark, onDark, debug, onDebug = { debug = it; prefs.edit().putBoolean("debug", it).apply() }, onBack = { settings = false })
    } else if (openC != null) {
        BackHandler { open = null }
        Detail(openC, dao, now, onBack = { open = null }, onEdit = { editing = openC.counter })
    } else ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Text(stringResource(R.string.app_name), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            NavigationDrawerItem(label = { Text(stringResource(R.string.settings)) }, selected = false,
                onClick = { scope.launch { drawer.close() }; settings = true })
        }
    }) {
      val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
      val listState = rememberLazyListState()
      Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = bar, navigationIcon = {
            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu)) }
        }, actions = {
            IconButton(onClick = { searching = !searching; query = "" }) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search)) }
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.sort)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                listOf("due" to R.string.sort_due, "recent" to R.string.sort_recent, "name" to R.string.sort_name).forEach { (k, name) ->
                    DropdownMenuItem(text = { Text(stringResource(name)) }, onClick = { sort = k; prefs0.edit().putString("sort", k).apply(); menu = false },
                        leadingIcon = { if (sort == k) Icon(Icons.Filled.Check, contentDescription = null) else Spacer(Modifier.size(24.dp)) })
                }
            }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.new_)) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                expanded = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
                onClick = { editing = Counter(label = "") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (searching) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true)
            if (allTags.isNotEmpty() || hasArchived) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { t ->
                    FilterChip(selected = filter == t, onClick = { filter = if (filter == t) null else t }, label = { Text(t) },
                        leadingIcon = { Box(Modifier.size(8.dp).background(tagColor(t), CircleShape)) })
                }
                if (hasArchived) FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(stringResource(R.string.archived)) })
            }
            if (counters.isEmpty()) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            var refreshing by remember { mutableStateOf(false) }
            // Pull down at the top: fetch from the sync server; show the error if it failed.
            PullToRefreshBox(refreshing, onRefresh = { scope.launch {
                refreshing = true; sync(ctx); refreshing = false
                val st = prefs.getString("syncStatus", null)
                if (prefs.getString("syncUrl", "").isNullOrBlank()) snackbar.showSnackbar(ctx.getString(R.string.sync_off))
                else if (st != null && prefs.getBoolean("syncFailed", false)) snackbar.showSnackbar(st)
            } }) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                items(shown, key = { it.counter.id }) { c ->
                    CounterCard(c, now, onOpen = { open = c.counter.id }) {
                        scope.launch {
                            val id = c.counter.id
                            val event = dao.reset(id); reschedule(ctx, id)
                            snackbar.currentSnackbarData?.dismiss()
                            if (snackbar.showSnackbar(ctx.getString(R.string.reset_label, c.counter.label), ctx.getString(R.string.undo), duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                dao.deleteEvent(event); reschedule(ctx, id)
                            }
                        }
                    }
                }
            }
            }
        }
    } }

    editing?.let { c ->
        EditDialog(c, debug, dao, onDismiss = { editing = null }, onDelete = { scope.launch { dao.delete(c.id); reschedule(ctx, c.id) }; editing = null }) { saved ->
            scope.launch {
                val id = if (saved.id == 0L) dao.create(saved) else saved.id.also { dao.update(saved) }
                reschedule(ctx, id)
            }
            editing = null
        }
    }
}

// Keys stay English (logic below matches on them); the chips show the translated name.
private val units = listOf("minutes" to 60_000L, "hours" to 3_600_000L, "days" to 86_400_000L, "weeks" to 604_800_000L)
private val unitNames = mapOf("minutes" to R.string.unit_minutes, "hours" to R.string.unit_hours, "days" to R.string.unit_days, "weeks" to R.string.unit_weeks)

@Composable
fun EditDialog(c: Counter, debug: Boolean, dao: CounterDao, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Counter) -> Unit) {
    var label by remember { mutableStateOf(c.label) }
    var tags by remember { mutableStateOf(c.tags.replace(",", ", ")) }
    // Minutes only in debug mode, or if this counter already uses them (else it couldn't be shown).
    val units = if (debug || (c.notifyAfterMs ?: 0) % 3_600_000 != 0L) units else units.drop(1)
    // Show stored ms in the largest unit that divides it evenly.
    val start = units.lastOrNull { c.notifyAfterMs != null && c.notifyAfterMs % it.second == 0L } ?: units.first { it.first == "days" }
    var unit by remember { mutableStateOf(start) }
    var amount by remember { mutableStateOf(c.notifyAfterMs?.let { (it / start.second).toString() } ?: "") }
    val n = amount.toLongOrNull()?.takeIf { it > 0 }
    val avg by produceState<Long?>(null) { if (c.id != 0L) value = avgGap(dao.times(c.id)) }
    fun result() = c.copy(label = label.trim(), tags = tagList(tags).joinToString(","), notifyAfterMs = n?.times(unit.second))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (c.id == 0L) R.string.new_counter else R.string.edit_counter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.label)) }, singleLine = true)
                OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.tags_hint)) }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.notify_after)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(stringResource(unitNames.getValue(u.first))) }) }
                }
                // Remind at your usual pace: hours under 2 days, else days (minutes only if offered).
                avg?.let { a ->
                    TextButton(onClick = {
                        val u = units.first { it.first == if (a < 3_600_000 && units[0].first == "minutes") "minutes" else if (a < 172_800_000) "hours" else "days" }
                        unit = u; amount = maxOf(1, Math.round(a.toDouble() / u.second)).toString()
                    }) { Text(stringResource(R.string.use_average, since(a))) }
                }
                if (c.id != 0L) Row {
                    TextButton(onClick = { onSave(result().copy(archived = !c.archived)) }) { Text(stringResource(if (c.archived) R.string.unarchive else R.string.archive)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = {
                onSave(result())
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(dark: Boolean, onDark: (Boolean) -> Unit, debug: Boolean, onDebug: (Boolean) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } })
    }) { pad -> Column(Modifier.padding(pad)) {
        ListItem(
            modifier = Modifier.clickable { onDark(!dark) },
            headlineContent = { Text(stringResource(R.string.dark_theme)) },
            trailingContent = { Switch(checked = dark, onCheckedChange = onDark) },
        )
        ListItem(
            modifier = Modifier.clickable { onDebug(!debug) },
            headlineContent = { Text(stringResource(R.string.debug_mode)) },
            supportingContent = { Text(stringResource(R.string.debug_hint)) },
            trailingContent = { Switch(checked = debug, onCheckedChange = onDebug) },
        )
        val ctx = LocalContext.current
        // Per-app language is built in from Android 13; older phones follow the system language.
        if (Build.VERSION.SDK_INT >= 33) {
            val lm = remember { ctx.getSystemService(android.app.LocaleManager::class.java) }
            val current = lm.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language ?: ""
            var langMenu by remember { mutableStateOf(false) }
            val langs = listOf("" to stringResource(R.string.lang_system), "en" to "English", "it" to "Italiano")
            ListItem(
                modifier = Modifier.clickable { langMenu = true },
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(langs.firstOrNull { it.first == current }?.second ?: current) },
                trailingContent = {
                    DropdownMenu(langMenu, onDismissRequest = { langMenu = false }) {
                        langs.forEach { (tag, name) -> DropdownMenuItem(text = { Text(name) }, onClick = {
                            langMenu = false
                            lm.applicationLocales = android.os.LocaleList.forLanguageTags(tag) // restarts the activity in that language
                        }) }
                    }
                },
            )
        }
        HorizontalDivider()
        val scope = rememberCoroutineScope()
        val prefs = remember { ctx.getSharedPreferences("settings", 0) }
        var url by remember { mutableStateOf(prefs.getString("syncUrl", "")!!) }
        var token by remember { mutableStateOf(prefs.getString("syncToken", "")!!) }
        val notSynced = stringResource(R.string.not_synced)
        var status by remember { mutableStateOf(prefs.getString("syncStatus", null) ?: notSynced) }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sync_server), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(url, { url = it; prefs.edit().putString("syncUrl", it).apply() }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.url_hint)) }, placeholder = { Text("https://your-server.example") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(token, { token = it; prefs.edit().putString("syncToken", it).apply() }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.token)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = url.isNotBlank(), onClick = { scope.launch {
                    status = ctx.getString(R.string.syncing); sync(ctx); status = prefs.getString("syncStatus", "")!!
                } }) { Text(stringResource(R.string.sync_now)) }
            }
        }
        HorizontalDivider()
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) scope.launch {
                val msg = try { exportJson(ctx, uri).let { ctx.resources.getQuantityString(R.plurals.n_exported, it, it) } } catch (e: Exception) { ctx.getString(R.string.export_failed, e.message) }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }
        var pending by remember { mutableStateOf<Backup?>(null) }
        val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                try { pending = readBackup(ctx, uri) }
                catch (e: Exception) { Toast.makeText(ctx, ctx.getString(R.string.cant_read, e.message), Toast.LENGTH_LONG).show() }
            }
        }
        pending?.let { data ->
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text(stringResource(R.string.replace_title)) },
                text = { Text(ctx.resources.getQuantityString(R.plurals.n_replace_text, data.size, data.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        pending = null
                        scope.launch {
                            restore(ctx, data)
                            Toast.makeText(ctx, ctx.resources.getQuantityString(R.plurals.n_imported, data.size, data.size), Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.replace), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        ListItem(
            modifier = Modifier.clickable { import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            headlineContent = { Text(stringResource(R.string.import_data)) },
            supportingContent = { Text(stringResource(R.string.import_hint)) },
        )
        ListItem(
            modifier = Modifier.clickable { export.launch("howlongsince-${java.time.LocalDate.now()}.json") },
            headlineContent = { Text(stringResource(R.string.export_data)) },
            supportingContent = { Text(stringResource(R.string.export_hint)) },
        )
    } }
}

@Composable
fun CounterCard(c: CounterWithLast, now: Long, onOpen: () -> Unit, onReset: () -> Unit) {
    val (n, unitRes) = sinceBig(now - c.lastAt)
    val unit = LocalContext.current.resources.getQuantityString(unitRes, n.toInt())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tags = tagList(c.counter.tags)
    val haptic = LocalHapticFeedback.current
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
      Row(Modifier.height(IntrinsicSize.Min)) {
        // Bar in the first tag's color.
        Box(Modifier.width(8.dp).fillMaxHeight().background(tags.firstOrNull()?.let { tagColor(it) } ?: MaterialTheme.colorScheme.surfaceContainerHigh))
        Row(Modifier.padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(n.toString(), transitionSpec = {
                    (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                }, label = "count") {
                    Text(it, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text(unit, style = MaterialTheme.typography.labelLarge, color = muted)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.counter.label, style = MaterialTheme.typography.titleMedium)
                val fmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
                Text(fmt.format(java.util.Date(c.lastAt)), style = MaterialTheme.typography.bodySmall, color = muted)
                if (tags.isNotEmpty()) Text(tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            ResetButton(c, now) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset() }
        }
      }
    }
}

/** Round reset button; if a reminder is set, a ring fills up towards it and turns red when overdue. */
@Composable
fun ResetButton(c: CounterWithLast, now: Long, onReset: () -> Unit) {
    val after = c.counter.notifyAfterMs
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (after != null) {
            val p = ((now - c.lastAt).toFloat() / after).coerceIn(0f, 1f)
            CircularProgressIndicator(
                progress = { p }, modifier = Modifier.fillMaxSize(), strokeWidth = 4.dp,
                color = if (p >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reset)) }
    }
}

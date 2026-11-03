package eu.etrunon.howlongsince

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Asks a name for a new group; [onCreated] gets its id, so the caller can open its settings (and turn on sync). */
@Composable
fun CreateGroupDialog(groups: List<CounterGroup>, dao: CounterDao, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_group)) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.group_name)) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val n = uniqueName(name.trim(), groups.map { it.name })
                scope.launch { onCreated(dao.insert(CounterGroup(name = n))) }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A group's settings: name, sync, devices, share and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettings(g: CounterGroup, groups: List<CounterGroup>, dao: CounterDao, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("settings", 0) }
    var name by remember(g.id) { mutableStateOf(g.name) }
    var deleting by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(g.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.back)) } },
            actions = { if (g.syncUrl != null) IconButton(onClick = { sharing = true }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share)) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.group_name)) }, singleLine = true)
                TextButton(enabled = name.isNotBlank() && name.trim() != g.name, onClick = {
                    val n = uniqueName(name.trim(), groups.filter { it.id != g.id }.map { it.name })
                    name = n; scope.launch { dao.rename(g.id, n) }
                }) { Text(stringResource(R.string.save)) }
            }
            HorizontalDivider()
            SyncSection(g, dao, prefs)
            HorizontalDivider()
            TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.delete_group), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (sharing) GroupShare(g) { sharing = false }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.delete_group_title)) },
        text = { Text(stringResource(R.string.delete_group_text, g.name)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    leaveGroup(ctx, g)
                    dao.deleteGroup(g.id)
                    dropKeys(ctx, g.id) // its counters stay here, local only
                }
                deleting = false; onBack()
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Turn a group's sync on (server and recovery password; the key is made here and never sent), see its status and devices, or turn it off.
 *  The recovery password also makes this phone an admin (or lets it back in) and, for admins, changes it. */
@Composable
private fun SyncSection(g: CounterGroup, dao: CounterDao, prefs: android.content.SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(prefs.getString("syncStatus${g.id}", null)) }
    var confirmOff by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var admin by remember { mutableStateOf(isAdmin(ctx, g)) }
    var members by remember { mutableStateOf<List<Member>?>(null) }
    var revoking by remember { mutableStateOf<Member?>(null) }
    val names by dao.devices().collectAsState(emptyList())
    val on = g.syncUrl != null
    LaunchedEffect(on, admin) { if (on && admin) members = members(ctx, g) }
    LaunchedEffect(on) { url = ""; password = ""; status = prefs.getString("syncStatus${g.id}", null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (on) {
            Text(g.syncUrl!!)
            Text(status ?: stringResource(R.string.not_synced), style = MaterialTheme.typography.bodySmall)
            members?.forEach { m ->
                val name = if (m.id == Db.deviceId) stringResource(R.string.this_device) else names.find { it.id == m.id }?.name ?: m.id.take(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name)
                        Text(stringResource(if (m.admin) R.string.admin else R.string.member) + " · " +
                            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(m.lastSeen * 1000)), style = MaterialTheme.typography.bodySmall)
                    }
                    if (m.id != Db.deviceId) TextButton(onClick = { revoking = m }) { Text(stringResource(R.string.revoke), color = MaterialTheme.colorScheme.error) }
                }
            }
            PasswordField(password, stringResource(when {
                !hasDevice(ctx, g) -> R.string.recovery_join
                admin -> R.string.change_password
                else -> R.string.become_admin
            })) { password = it }
        } else {
            Text(stringResource(R.string.sync_hint), style = MaterialTheme.typography.bodySmall)
            listOf(false, true).forEach { c ->
                Row(Modifier.fillMaxWidth().selectable(custom == c, role = Role.RadioButton) { custom = c }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(custom == c, onClick = null)
                    Text(if (c) stringResource(R.string.custom_server) else stringResource(R.string.default_server, DEFAULT_SERVER.substringAfter("://")), Modifier.padding(start = 8.dp))
                }
            }
            if (custom) OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_url)) },
                placeholder = { Text("https://your-server.example") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            PasswordField(password, stringResource(R.string.recovery_new)) { password = it }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row {
            if (on) {
                if (password.isNotEmpty()) TextButton(enabled = !busy && (!admin || password.length >= 6), onClick = { // admins set a new one
                    busy = true
                    scope.launch {
                        val err = useRecovery(ctx, g, password)
                        busy = false; password = ""
                        if (err == null) admin = true
                        status = err ?: prefs.getString("syncStatus${g.id}", null)
                    }
                }) { Text(stringResource(R.string.save)) }
                TextButton(enabled = !busy, onClick = { scope.launch { status = ctx.getString(R.string.syncing); sync(ctx); status = prefs.getString("syncStatus${g.id}", null) } }) { Text(stringResource(R.string.sync_now)) }
                TextButton(onClick = { confirmOff = true }) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error) }
            } else TextButton(enabled = (!custom || url.isNotBlank()) && password.length >= 6 && !busy, onClick = {
                busy = true
                scope.launch {
                    val secret = newSecret()
                    val recovery = withContext(Dispatchers.Default) { stretch(password, keysOf(secret).id) }
                    keyPrefs(ctx).edit().putString("k${g.id}", secret).putString("r${g.id}", recovery).commit() // key first, so a sync never sees a URL without one
                    dao.setSyncUrl(g.id, if (custom) url.trim().trimEnd('/').let { if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(it)) it else "https://$it" } else DEFAULT_SERVER)
                    sync(ctx)
                    busy = false; admin = isAdmin(ctx, g)
                }
            }) { Text(stringResource(R.string.turn_on)) }
        }
    }
    revoking?.let { m ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            text = { Text(stringResource(R.string.confirm_revoke, names.find { it.id == m.id }?.name ?: m.id.take(8))) },
            confirmButton = {
                TextButton(onClick = { scope.launch { revoke(ctx, g, m.id); members = members(ctx, g) }; revoking = null }) {
                    Text(stringResource(R.string.revoke), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmOff) AlertDialog(
        onDismissRequest = { confirmOff = false },
        title = { Text(stringResource(R.string.turn_off_title)) },
        text = { Text(stringResource(R.string.turn_off_text)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    leaveGroup(ctx, g)
                    dropKeys(ctx, g.id)
                    dao.setSyncUrl(g.id, null)
                }
                confirmOff = false
            }) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

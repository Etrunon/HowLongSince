package eu.etrunon.howlongsince

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Create, rename and delete groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Groups(groups: List<CounterGroup>, dao: CounterDao, onMenu: () -> Unit, onJoin: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CounterGroup?>(null) } // id 0 = new
    var deleting by remember { mutableStateOf<CounterGroup?>(null) }
    var sharing by remember { mutableStateOf<Long?>(null) } // id of the group being shared
    var syncing by remember { mutableStateOf<Long?>(null) } // id of the group whose sync dialog is open
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("settings", 0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.groups)) }, navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu)) } },
            actions = { TextButton(onClick = onJoin) { Text(stringResource(R.string.join)) } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(text = { Text(stringResource(R.string.new_group)) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { editing = CounterGroup(name = "") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(groups, key = { it.id }) { c ->
                ListItem(modifier = Modifier.clickable { syncing = c.id }, headlineContent = { Text(c.name) },
                    supportingContent = { Text(if (c.syncUrl == null) stringResource(R.string.local_only) else stringResource(R.string.synced_with, c.syncUrl.removePrefix("https://"))) },
                    trailingContent = {
                        Row {
                            if (c.syncUrl != null) IconButton(onClick = { sharing = c.id }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share)) }
                            TextButton(onClick = { editing = c }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { deleting = c }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        }
                    })
            }
        }
    }
    groups.find { it.id == sharing }?.let { g -> GroupShare(g) { sharing = null } }
    groups.find { it.id == syncing }?.let { SyncDialog(it, dao, prefs) { syncing = null } }
    editing?.let { c ->
        var name by remember { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (c.id == 0L) R.string.new_group else R.string.edit)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.group_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val n = uniqueName(name.trim(), groups.filter { it.id != c.id }.map { it.name })
                    scope.launch { if (c.id == 0L) dao.insert(CounterGroup(name = n)) else if (n != c.name) dao.rename(c.id, n) }
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_text, c.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        leaveGroup(ctx, c)
                        dao.deleteGroup(c.id)
                        dropKeys(ctx, c.id) // its counters stay here, local only
                    }
                    deleting = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Turn a group's sync on (server and recovery password; the key is made here and never sent), see its status and devices, or turn it off.
 *  The recovery password also makes this phone an admin (or lets it back in) and, for admins, changes it. */
@Composable
private fun SyncDialog(g: CounterGroup, dao: CounterDao, prefs: android.content.SharedPreferences, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(prefs.getString("syncStatus${g.id}", null)) }
    var confirmOff by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var admin by remember { mutableStateOf(isAdmin(ctx, g)) }
    var members by remember { mutableStateOf<List<Member>?>(null) }
    var revoking by remember { mutableStateOf<Member?>(null) }
    val names by dao.devices().collectAsState(emptyList())
    val on = g.syncUrl != null
    LaunchedEffect(admin) { if (on && admin) members = members(ctx, g) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_url)) },
                        placeholder = { Text("https://your-server.example") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    PasswordField(password, stringResource(R.string.recovery_new)) { password = it }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (on) Row {
                if (password.isNotEmpty()) TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val ok = useRecovery(ctx, g, password)
                        busy = false; password = ""
                        if (ok) admin = true
                        status = if (ok) prefs.getString("syncStatus${g.id}", null) else ctx.getString(R.string.wrong_password)
                    }
                }) { Text(stringResource(R.string.save)) }
                TextButton(enabled = !busy, onClick = { scope.launch { status = ctx.getString(R.string.syncing); sync(ctx); status = prefs.getString("syncStatus${g.id}", null) } }) { Text(stringResource(R.string.sync_now)) }
            }
            else TextButton(enabled = url.isNotBlank() && password.isNotEmpty() && !busy, onClick = {
                busy = true
                scope.launch {
                    val secret = newSecret()
                    val recovery = withContext(Dispatchers.Default) { stretch(password, keysOf(secret).id) }
                    keyPrefs(ctx).edit().putString("k${g.id}", secret).putString("r${g.id}", recovery).commit() // key first, so a sync never sees a URL without one
                    dao.setSyncUrl(g.id, url.trim().trimEnd('/'))
                    sync(ctx)
                    onDismiss()
                }
            }) { Text(stringResource(R.string.turn_on)) }
        },
        dismissButton = {
            if (on) TextButton(onClick = { confirmOff = true }) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
                onDismiss()
            }) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

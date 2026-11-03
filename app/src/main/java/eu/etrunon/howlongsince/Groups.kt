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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    groups.find { it.id == sharing }?.let { g ->
        shareLinkOf(ctx, g)?.let { ShareDialog(g.name, it) { sharing = null } }
    }
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
                        ctx.getSharedPreferences("sync_keys", 0).edit().remove("k${c.id}").remove("t${c.id}").apply() // its counters stay here, local only
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

/** Turn a group's sync on (server, optional token; the key is made here and never sent), see its status, or turn it off. */
@Composable
private fun SyncDialog(g: CounterGroup, dao: CounterDao, prefs: android.content.SharedPreferences, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keys = remember { ctx.getSharedPreferences("sync_keys", 0) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(prefs.getString("syncStatus${g.id}", null)) }
    var confirmOff by remember { mutableStateOf(false) }
    val on = g.syncUrl != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (on) {
                    Text(g.syncUrl!!)
                    Text(status ?: stringResource(R.string.not_synced), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.sync_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_url)) },
                        placeholder = { Text("https://your-server.example") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.token)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            }
        },
        confirmButton = {
            if (on) TextButton(onClick = { scope.launch { status = ctx.getString(R.string.syncing); sync(ctx); status = prefs.getString("syncStatus${g.id}", null) } }) { Text(stringResource(R.string.sync_now)) }
            else TextButton(enabled = url.isNotBlank(), onClick = {
                keys.edit().putString("k${g.id}", newSecret()).putString("t${g.id}", token.trim()).commit() // key first, so a sync never sees a URL without one
                scope.launch { dao.setSyncUrl(g.id, url.trim().trimEnd('/')); sync(ctx) }
                onDismiss()
            }) { Text(stringResource(R.string.turn_on)) }
        },
        dismissButton = {
            if (on) TextButton(onClick = { confirmOff = true }) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    if (confirmOff) AlertDialog(
        onDismissRequest = { confirmOff = false },
        title = { Text(stringResource(R.string.turn_off_title)) },
        text = { Text(stringResource(R.string.turn_off_text)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    leaveGroup(ctx, g)
                    keys.edit().remove("k${g.id}").remove("t${g.id}").apply()
                    dao.setSyncUrl(g.id, null)
                }
                onDismiss()
            }) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The share link of a synced group, or null if it isn't synced or this phone has no key for it. */
fun shareLinkOf(ctx: android.content.Context, g: CounterGroup): String? {
    val keys = ctx.getSharedPreferences("sync_keys", 0)
    return keys.getString("k${g.id}", null)?.let { shareLink(Join(g.syncUrl ?: return null, it, keys.getString("t${g.id}", "")!!)) }
}

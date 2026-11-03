package eu.etrunon.howlongsince

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Create, rename and delete groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Groups(groups: List<CounterGroup>, dao: CounterDao, onMenu: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CounterGroup?>(null) } // id 0 = new
    var deleting by remember { mutableStateOf<CounterGroup?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.groups)) }, navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu)) } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(text = { Text(stringResource(R.string.new_group)) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { editing = CounterGroup(name = "") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(groups, key = { it.id }) { c ->
                ListItem(headlineContent = { Text(c.name) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { editing = c }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { deleting = c }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        }
                    })
            }
        }
    }
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
                TextButton(onClick = { scope.launch { dao.deleteGroup(c.id) }; deleting = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

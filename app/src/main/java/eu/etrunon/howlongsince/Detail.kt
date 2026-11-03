package eu.etrunon.howlongsince

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import android.nfc.NfcAdapter
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(c: CounterWithLast, dao: CounterDao, now: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val id = c.counter.id
    val events by remember(id) { dao.events(id) }.collectAsState(initial = emptyList())
    val devices by remember { dao.devices() }.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nfc = remember { NfcAdapter.getDefaultAdapter(ctx) }
    var pairing by remember { mutableStateOf(false) }
    var pairChoice by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    if (pairing && nfc != null) PairDialog(nfc, id, c.counter.uid, dao) { pairing = false }
    // Pair here, or copy the URL to write the tag with another app (then the first tap pairs it).
    if (pairChoice) AlertDialog(onDismissRequest = { pairChoice = false }, title = { Text(stringResource(R.string.pair_title)) },
        text = { Text(stringResource(R.string.pair_choice_text, resetUri(c.counter.uid))) },
        confirmButton = { TextButton(onClick = {
            pairChoice = false
            if (nfc?.isEnabled == true) pairing = true else Toast.makeText(ctx, ctx.getString(R.string.turn_on_nfc), Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.pair_now)) } },
        dismissButton = { TextButton(onClick = {
            pairChoice = false
            ctx.getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("NFC URL", resetUri(c.counter.uid)))
            ctx.getSharedPreferences("settings", 0).edit().putLong("adoptTag", id).apply()
            Toast.makeText(ctx, ctx.getString(R.string.url_copied), Toast.LENGTH_LONG).show()
        }) { Text(stringResource(R.string.copy_url)) } })

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c.counter.label) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.back)) } },
            actions = { IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (big, small) = sinceTwo(now - c.lastAt)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(big, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                    Text(small, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                }
                Text(ctx.resources.getQuantityString(R.plurals.n_entries, events.size, events.size), Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodySmall)
                ResetButton(c, now) { resetting = true }
            }
            if (c.counter.archived) Text(stringResource(R.string.archived_hint), style = MaterialTheme.typography.bodySmall)
            // Reset with a note, now or (forgot to log it) at a past date and time.
            if (resetting) {
                var note by remember { mutableStateOf("") }
                fun done(at: Long) { resetting = false; scope.launch { dao.reset(id, "manual", at, note); reschedule(ctx, id) } }
                AlertDialog(onDismissRequest = { resetting = false }, title = { Text(stringResource(R.string.reset_label, c.counter.label)) },
                    text = { OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note_optional)) }) },
                    confirmButton = { TextButton(onClick = { done(System.currentTimeMillis()) }) { Text(stringResource(R.string.now)) } },
                    dismissButton = { TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(ctx, { _, y, m, d ->
                        TimePickerDialog(ctx, { _, h, min ->
                            cal.set(y, m, d, h, min, 0)
                            if (cal.timeInMillis > System.currentTimeMillis())
                                Toast.makeText(ctx, ctx.getString(R.string.pick_past), Toast.LENGTH_SHORT).show()
                            else done(cal.timeInMillis)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(ctx)).show()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }) { Text(stringResource(R.string.earlier)) } })
            }
            if (nfc != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (c.counter.nfcTagId != null) R.string.nfc_paired else R.string.no_nfc), Modifier.weight(1f))
                if (c.counter.nfcTagId != null) TextButton(onClick = { cancelAdopt(ctx, id); scope.launch { dao.setTag(id, null) } }) { Text(stringResource(R.string.unpair)) }
                TextButton(onClick = { pairChoice = true }) { Text(stringResource(if (c.counter.nfcTagId != null) R.string.re_pair else R.string.pair_tag)) }
            }
            // Oldest -> newest intervals between resets.
            val gaps = events.zipWithNext { a, b -> a.at - b.at }.reversed().takeLast(30)
            if (gaps.size >= 2) IntervalChart(gaps)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val line = MaterialTheme.colorScheme.outlineVariant
            val dot = MaterialTheme.colorScheme.primary
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(events, key = { _, e -> e.id }) { i, e ->
                    // Newest first, so the previous entry is the next item in the list.
                    val gap = events.getOrNull(i + 1)?.let { ctx.getString(R.string.after, since(e.at - it.at)) }
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(24.dp).fillMaxHeight().drawBehind {
                            val x = size.width / 2; val y = 14.dp.toPx()
                            if (i > 0) drawLine(line, Offset(x, 0f), Offset(x, y), 2.dp.toPx())
                            if (i < events.lastIndex) drawLine(line, Offset(x, y), Offset(x, size.height), 2.dp.toPx())
                            drawCircle(dot, if (i == 0) 6.dp.toPx() else 4.dp.toPx(), Offset(x, y))
                        })
                        Column(Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                            Text(fmt.format(Date(e.at)), style = MaterialTheme.typography.titleSmall)
                            val tag = when (e.source) { "nfc" -> ctx.getString(R.string.via_nfc); "create" -> ctx.getString(R.string.created); else -> null }
                            Text(listOfNotNull(gap, tag, devices.find { it.id == e.deviceId }?.name).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            e.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairDialog(nfc: NfcAdapter, id: Long, uid: String, dao: CounterDao, onDone: () -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    // Reader mode: while this dialog is open, taps go to us instead of the normal tag dispatch.
    DisposableEffect(Unit) {
        nfc.enableReaderMode(activity, { tag ->
            val err = writeTag(tag, uid) // binder thread, blocking IO is fine here
            scope.launch {
                if (err == null) { cancelAdopt(activity, id); dao.pair(id, tag.id.toHex()) }
                Toast.makeText(activity, activity.getString(err ?: R.string.tag_paired), Toast.LENGTH_SHORT).show()
                if (err == null) onDone()
            }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V, null)
        onDispose { nfc.disableReaderMode(activity) }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.pair_title)) },
        text = { Text(stringResource(R.string.pair_text)) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Stop waiting for a tag written by another app (after unpair or an in-app pairing). */
fun cancelAdopt(ctx: android.content.Context, id: Long) {
    val p = ctx.getSharedPreferences("settings", 0)
    if (p.getLong("adoptTag", 0) == id) p.edit().remove("adoptTag").apply()
}

/** Tiny bar chart of the intervals between resets, oldest left. */
@Composable
private fun IntervalChart(gaps: List<Long>) {
    val color = MaterialTheme.colorScheme.primary
    Text(stringResource(R.string.intervals, since(gaps.average().toLong())), Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val max = gaps.max().coerceAtLeast(1).toFloat()
        val w = size.width / gaps.size
        gaps.forEachIndexed { i, g ->
            val h = (g / max * size.height).coerceAtLeast(2.dp.toPx())
            drawRect(color, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
        }
    }
}

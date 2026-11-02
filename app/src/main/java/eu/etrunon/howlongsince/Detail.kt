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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(c: CounterWithLast, dao: CounterDao, now: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val id = c.counter.id
    val events by remember(id) { dao.events(id) }.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nfc = remember { NfcAdapter.getDefaultAdapter(ctx) }
    var pairing by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    if (pairing && nfc != null) PairDialog(nfc, id, dao) { pairing = false }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c.counter.label) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = { TextButton(onClick = onEdit) { Text("Edit") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Text(since(now - c.lastAt), style = MaterialTheme.typography.displaySmall)
            if (c.counter.archived) Text("Archived: no reminders, hidden from the list", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${events.size} entries", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { resetting = true }) { Text("Reset…") }
            }
            // Reset with a note, now or (forgot to log it) at a past date and time.
            if (resetting) {
                var note by remember { mutableStateOf("") }
                fun done(at: Long) { resetting = false; scope.launch { dao.reset(id, "manual", at, note); reschedule(ctx, id) } }
                AlertDialog(onDismissRequest = { resetting = false }, title = { Text("Reset ${c.counter.label}") },
                    text = { OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }) },
                    confirmButton = { TextButton(onClick = { done(System.currentTimeMillis()) }) { Text("Now") } },
                    dismissButton = { TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(ctx, { _, y, m, d ->
                        TimePickerDialog(ctx, { _, h, min ->
                            cal.set(y, m, d, h, min, 0)
                            if (cal.timeInMillis > System.currentTimeMillis())
                                Toast.makeText(ctx, "Pick a time in the past", Toast.LENGTH_SHORT).show()
                            else done(cal.timeInMillis)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(ctx)).show()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }) { Text("Earlier…") } })
            }
            if (nfc != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (c.counter.nfcTagId != null) "NFC tag paired" else "No NFC tag", Modifier.weight(1f))
                if (c.counter.nfcTagId != null) TextButton(onClick = { scope.launch { dao.setTag(id, null) } }) { Text("Unpair") }
                TextButton(onClick = {
                    if (nfc.isEnabled) pairing = true else Toast.makeText(ctx, "Turn on NFC first", Toast.LENGTH_SHORT).show()
                }) { Text(if (c.counter.nfcTagId != null) "Re-pair" else "Pair tag") }
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
                    val gap = events.getOrNull(i + 1)?.let { "after ${since(e.at - it.at)}" }
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(24.dp).fillMaxHeight().drawBehind {
                            val x = size.width / 2; val y = 14.dp.toPx()
                            if (i > 0) drawLine(line, Offset(x, 0f), Offset(x, y), 2.dp.toPx())
                            if (i < events.lastIndex) drawLine(line, Offset(x, y), Offset(x, size.height), 2.dp.toPx())
                            drawCircle(dot, if (i == 0) 6.dp.toPx() else 4.dp.toPx(), Offset(x, y))
                        })
                        Column(Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                            Text(fmt.format(Date(e.at)), style = MaterialTheme.typography.titleSmall)
                            val tag = when (e.source) { "nfc" -> "via NFC"; "create" -> "created"; else -> null }
                            Text(listOfNotNull(gap, tag).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
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
private fun PairDialog(nfc: NfcAdapter, id: Long, dao: CounterDao, onDone: () -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    // Reader mode: while this dialog is open, taps go to us instead of the normal tag dispatch.
    DisposableEffect(Unit) {
        nfc.enableReaderMode(activity, { tag ->
            val err = writeTag(tag, id) // binder thread, blocking IO is fine here
            scope.launch {
                if (err == null) dao.pair(id, tag.id.toHex())
                Toast.makeText(activity, err ?: "Tag paired", Toast.LENGTH_SHORT).show()
                if (err == null) onDone()
            }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V, null)
        onDispose { nfc.disableReaderMode(activity) }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Pair NFC tag") },
        text = { Text("Hold a tag against the back of the phone. It will be overwritten.") },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** Tiny bar chart of the intervals between resets, oldest left. */
@Composable
private fun IntervalChart(gaps: List<Long>) {
    val color = MaterialTheme.colorScheme.primary
    Text("Intervals · avg ${since(gaps.average().toLong())}", Modifier.padding(top = 12.dp, bottom = 4.dp),
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

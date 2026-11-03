package eu.etrunon.howlongsince

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Counter, its events, and its group's name (null = Ungrouped). */
typealias Backup = List<Triple<Counter, List<Event>, String?>>

fun toJson(counters: List<Counter>, events: List<Event>, groups: Map<Long, String> = emptyMap(), now: Instant = Instant.now()): String {
    val byCounter = events.groupBy { it.counterId }
    return JSONObject()
        .put("version", 1)
        .put("exportedAt", now.toString())
        .put("counters", JSONArray(counters.map { c ->
            JSONObject()
                .put("id", c.id)
                .put("label", c.label)
                .put("tags", JSONArray(tagList(c.tags)))
                .put("notifyAfterMs", c.notifyAfterMs ?: JSONObject.NULL)
                .put("nfcTagId", c.nfcTagId ?: JSONObject.NULL)
                .put("uid", c.uid)
                .put("updatedAt", c.updatedAt)
                .put("archived", c.archived)
                .put("group", groups[c.groupId] ?: JSONObject.NULL)
                .put("events", JSONArray(byCounter[c.id].orEmpty().map { e ->
                    JSONObject().put("at", e.at).put("time", Instant.ofEpochMilli(e.at).toString()).put("source", e.source).put("uid", e.uid).putOpt("note", e.note)
                }))
        }))
        .toString(2)
}

/** Parses and validates a whole export; throws with a readable message on anything wrong. */
fun fromJson(s: String): Backup {
    val root = JSONObject(s)
    require(root.optInt("version") == 1) { "Unsupported file version" }
    val arr = root.getJSONArray("counters")
    val out = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val label = o.getString("label").trim()
        require(label.isNotEmpty()) { "Counter #${i + 1} has no label" }
        val id = o.getLong("id")
        val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).joinToString(",") { t.getString(it) } } ?: ""
        val ev = o.getJSONArray("events")
        require(ev.length() > 0) { "\"$label\" has no events" }
        val counter = Counter(
            id = id, label = label, tags = tagList(tags).joinToString(","),
            notifyAfterMs = if (o.isNull("notifyAfterMs")) null else o.getLong("notifyAfterMs"),
            nfcTagId = if (o.isNull("nfcTagId")) null else o.getString("nfcTagId"),
            uid = o.optString("uid").ifEmpty { UUID.randomUUID().toString() }, // older exports have none
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            archived = o.optBoolean("archived"),
        )
        val events = (0 until ev.length()).map { j ->
            val e = ev.getJSONObject(j)
            Event(counterId = id, at = secs(e.getLong("at")), source = e.optString("source", "manual"),
                uid = e.optString("uid").ifEmpty { UUID.randomUUID().toString() },
                note = if (e.isNull("note")) null else e.getString("note"))
        }.distinctBy { it.at } // same counter and second = same event
        Triple(counter, events, if (o.isNull("group")) null else o.getString("group").trim().ifEmpty { null })
    }
    require(out.map { it.first.id }.toSet().size == out.size) { "Duplicate counter ids" }
    return out
}

/** Writes every counter with its full reset log to [uri]. Returns the number of counters. */
suspend fun exportJson(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    val dao = Db.get(ctx).dao()
    val counters = dao.allCounters()
    val json = toJson(counters, dao.allEvents(), dao.allGroups().associate { it.id to it.name })
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
    counters.size
}

suspend fun readBackup(ctx: Context, uri: Uri): Backup = withContext(Dispatchers.IO) {
    fromJson(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
}

/**
 * [replace]: each group in the file replaces the local one of the same name (created if missing; no group = Ungrouped).
 * Otherwise every group in the file is imported as a new one, renamed if its name is taken, with fresh uids so it never merges with the originals.
 * Groups not in the file are untouched. Rebuilds the reminders.
 */
suspend fun restore(ctx: Context, data: Backup, replace: Boolean) {
    val dao = Db.get(ctx).dao()
    val names = dao.allGroups().toMutableList()
    for ((name, items) in data.groupBy { it.third }) {
        var rows = items.map { it.first to it.second }
        val group = if (replace && name == null) null else {
            val old = names.find { it.name.equals(name, true) }.takeIf { replace }
            old?.id ?: CounterGroup(name = uniqueName(name ?: ctx.getString(R.string.imported), names.map { it.name })).let { new ->
                if (!replace) rows = rows.map { (c, ev) -> c.copy(uid = UUID.randomUUID().toString(), nfcTagId = null) to ev.map { it.copy(uid = UUID.randomUUID().toString()) } }
                dao.insert(new).also { names += new.copy(id = it) }
            }
        }
        dao.replaceAll(rows, group)
    }
    WorkManager.getInstance(ctx).cancelAllWork() // only our reminders live in WorkManager (and the sync, put back below)
    schedulePeriodicSync(ctx)
    dao.allCounters().forEach { reschedule(ctx, it.id) }
}

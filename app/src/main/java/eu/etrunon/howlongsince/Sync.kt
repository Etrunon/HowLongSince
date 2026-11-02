package eu.etrunon.howlongsince

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date

/** Server state; events carry their counter's uid (local ids differ per device). */
data class Remote(val counters: List<Counter>, val events: List<Pair<String, Event>>)

fun stateJson(counters: List<Counter>, events: List<Event>): String {
    val uids = counters.associate { it.id to it.uid }
    return JSONObject()
        .put("counters", JSONArray(counters.map { c ->
            JSONObject().put("id", c.uid).put("label", c.label).put("tags", c.tags)
                .put("notifyAfterMs", c.notifyAfterMs ?: JSONObject.NULL).put("nfcTagId", c.nfcTagId ?: JSONObject.NULL)
                .put("updatedAt", c.updatedAt).put("deleted", c.deleted)
        }))
        .put("events", JSONArray(events.mapNotNull { e ->
            JSONObject().put("id", e.uid).put("counterId", uids[e.counterId] ?: return@mapNotNull null)
                .put("at", e.at).put("source", e.source).put("deleted", e.deleted)
        }))
        .toString()
}

fun parseState(s: String): Remote {
    val o = JSONObject(s)
    val cs = o.getJSONArray("counters"); val es = o.getJSONArray("events")
    return Remote(
        (0 until cs.length()).map { i ->
            val c = cs.getJSONObject(i)
            Counter(label = c.getString("label"), tags = c.optString("tags"),
                notifyAfterMs = if (c.isNull("notifyAfterMs")) null else c.getLong("notifyAfterMs"),
                nfcTagId = if (c.isNull("nfcTagId")) null else c.getString("nfcTagId"),
                uid = c.getString("id"), updatedAt = c.getLong("updatedAt"), deleted = c.optBoolean("deleted"))
        },
        (0 until es.length()).map { i ->
            val e = es.getJSONObject(i)
            e.getString("counterId") to Event(counterId = 0, at = e.getLong("at"), source = e.optString("source", "manual"),
                uid = e.getString("id"), deleted = e.optBoolean("deleted"))
        },
    )
}

private val lock = Mutex()

/** Sends everything to the server and applies the merged result. No-op without a server URL; never throws. */
suspend fun sync(ctx: Context): Unit = lock.withLock {
    val prefs = ctx.getSharedPreferences("settings", 0)
    val url = prefs.getString("syncUrl", "")!!.trim().trimEnd('/')
    if (url.isEmpty()) return
    val dao = Db.get(ctx).dao()
    val status = try {
        // ponytail: sends the whole history each time; fine for thousands of resets, send a delta if it grows past that.
        val body = stateJson(dao.rawCounters(), dao.rawEvents())
        val res = withContext(Dispatchers.IO) { post("$url/sync", prefs.getString("syncToken", "")!!.trim(), body) }
        dao.merge(parseState(res)).forEach { reschedule(ctx, it) }
        "Synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
    } catch (e: Exception) {
        "Sync failed: ${e.message}"
    }
    prefs.edit().putString("syncStatus", status).apply()
}

private fun post(url: String, token: String, body: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("Content-Type", "application/json")
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode != 200) throw IOException(if (c.responseCode == 401) "wrong token" else "HTTP ${c.responseCode}")
        return c.inputStream.use { it.readBytes().decodeToString() }
    } finally { c.disconnect() }
}

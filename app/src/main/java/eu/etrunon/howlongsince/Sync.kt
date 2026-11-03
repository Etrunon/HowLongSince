package eu.etrunon.howlongsince

import android.content.Context
import androidx.work.*
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
import java.util.Base64
import java.util.Date
import java.util.concurrent.TimeUnit

/** One group's decrypted blob; events carry their counter's uid (local ids differ per device). The group name travels inside, newest rename wins. */
data class Remote(val counters: List<Counter>, val events: List<Pair<String, Event>>, val name: String? = null, val nameUpdatedAt: Long = 0, val devices: List<Device> = emptyList())

/** Sorted by uid, so the same data always gives the same text and an unchanged group isn't uploaded again. */
fun stateJson(counters: List<Counter>, events: List<Event>, name: String? = null, nameUpdatedAt: Long = 0, devices: List<Device> = emptyList()): String {
    val uids = counters.associate { it.id to it.uid }
    val sent = events.filter { it.counterId in uids }
    val named = (sent.mapNotNull { it.deviceId } + Db.deviceId).toSet() // only the devices this group has seen, not every group's
    return JSONObject()
        .put("name", name ?: JSONObject.NULL).put("nameUpdatedAt", nameUpdatedAt)
        .put("counters", JSONArray(counters.sortedBy { it.uid }.map { c ->
            JSONObject().put("id", c.uid).put("label", c.label).put("tags", c.tags)
                .put("notifyAfterMs", c.notifyAfterMs ?: JSONObject.NULL).put("nfcTagId", c.nfcTagId ?: JSONObject.NULL)
                .put("updatedAt", c.updatedAt).put("deleted", c.deleted).put("archived", c.archived)
        }))
        .put("events", JSONArray(events.sortedBy { it.uid }.mapNotNull { e ->
            JSONObject().put("id", e.uid).put("counterId", uids[e.counterId] ?: return@mapNotNull null)
                .put("at", e.at).put("source", e.source).put("deleted", e.deleted).putOpt("note", e.note).putOpt("deviceId", e.deviceId)
        }))
        .put("devices", JSONArray(devices.filter { it.id in named }.sortedBy { it.id }.map { JSONObject().put("id", it.id).put("name", it.name).put("updatedAt", it.updatedAt) }))
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
                uid = c.getString("id"), updatedAt = c.getLong("updatedAt"), deleted = c.optBoolean("deleted"), archived = c.optBoolean("archived"))
        },
        (0 until es.length()).map { i ->
            val e = es.getJSONObject(i)
            e.getString("counterId") to Event(counterId = 0, at = e.getLong("at"), source = e.optString("source", "manual"),
                uid = e.getString("id"), deleted = e.optBoolean("deleted"), note = if (e.isNull("note")) null else e.getString("note"),
                deviceId = if (e.isNull("deviceId")) null else e.optString("deviceId", "").ifEmpty { null })
        },
        if (o.isNull("name")) null else o.getString("name"), o.optLong("nameUpdatedAt"),
        o.optJSONArray("devices").let { ds -> (0 until (ds?.length() ?: 0)).map { ds!!.getJSONObject(it) }.map { Device(it.getString("id"), it.getString("name"), it.getLong("updatedAt")) } },
    )
}

private val lock = Mutex()

private fun keyPrefs(ctx: Context) = ctx.getSharedPreferences("sync_keys", 0) // "k<groupId>" = secret, "t<groupId>" = token; excluded from backups

/** The old global server becomes a group ("Synced") holding every loose counter, with a fresh key. Other phones join in a later phase. */
private suspend fun migrateOldSync(ctx: Context, dao: CounterDao, prefs: android.content.SharedPreferences) {
    val url = prefs.getString("syncUrl", "")!!.trim()
    if (url.isEmpty()) return
    val id = dao.insert(CounterGroup(name = uniqueName(ctx.getString(R.string.synced_group), dao.allGroups().map { it.name }), syncUrl = url))
    keyPrefs(ctx).edit().putString("k$id", newSecret()).putString("t$id", prefs.getString("syncToken", "")!!.trim()).commit()
    dao.adopt(id)
    // ponytail: a crash right here would migrate twice (a second, empty "Synced" group); not worth a transaction across prefs and Room.
    prefs.edit().remove("syncUrl").remove("syncToken").remove("syncStatus").remove("syncFailed").apply()
}

/** Sends every synced group to its server and applies what came back. No-op without synced groups; never throws. */
suspend fun sync(ctx: Context): Unit = lock.withLock {
    val prefs = ctx.getSharedPreferences("settings", 0)
    val dao = Db.get(ctx).dao()
    migrateOldSync(ctx, dao, prefs)
    dao.addDevice(Device(Db.deviceId, android.os.Build.MODEL, 0)) // default name; any rename is newer
    dao.pruneDeleted(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)) // same TTL as the server
    for (g in dao.allGroups()) {
        if (g.syncUrl == null) continue
        val status = try {
            val secret = keyPrefs(ctx).getString("k${g.id}", null) ?: throw IOException(ctx.getString(R.string.key_missing))
            syncGroup(ctx, dao, g, secret, keyPrefs(ctx).getString("t${g.id}", "")!!)
            ctx.getString(R.string.synced, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())) to false
        } catch (e: Exception) {
            ctx.getString(R.string.sync_failed, if (e.message == "wrong token") ctx.getString(R.string.wrong_token) else e.message) to true
        }
        prefs.edit().putString("syncStatus${g.id}", status.first).putBoolean("syncFailed${g.id}", status.second).apply()
    }
}

/** GET, decrypt, merge, encrypt, PUT; if someone else wrote in between (409) start over. */
private suspend fun syncGroup(ctx: Context, dao: CounterDao, g: CounterGroup, secret: String, token: String) {
    val k = keysOf(secret)
    val url = "${g.syncUrl!!.trim().trimEnd('/')}/s/${k.id}"
    repeat(5) {
        val (code, text) = withContext(Dispatchers.IO) { http("GET", url, token) }
        var etag: String? = null; var remote: String? = null
        when (code) {
            200 -> JSONObject(text).let { etag = it.getString("etag"); remote = openDoc(k, Base64.getDecoder().decode(it.getString("ciphertext"))) }
            404 -> {} // not uploaded yet
            else -> throw httpError(code)
        }
        val parsed = remote?.let(::parseState)
        parsed?.let { dao.merge(it, g.id).forEach { id -> reschedule(ctx, id) } }
        val all = dao.rawCounters()
        // A counter that moved out of this group since the blob was written: tombstone it there. id -1 keeps its events out.
        val moved = parsed?.counters.orEmpty().filter { !it.deleted }.mapNotNull { r ->
            all.find { it.uid == r.uid && it.groupId != g.id && it.updatedAt > r.updatedAt }
                ?.let { Counter(id = -1, label = r.label, uid = r.uid, updatedAt = it.updatedAt, deleted = true) }
        }
        val me = dao.group(g.id) ?: return
        // Tombstones already in the blob stay (until they are 90 days old) so phones that haven't synced yet still learn of them.
        val mine = all.filter { it.groupId == g.id }
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        val kept = parsed?.counters.orEmpty().filter { r -> r.deleted && r.updatedAt >= cutoff && mine.none { it.uid == r.uid } }.map { it.copy(id = -1) }
        val out = stateJson(mine + moved + kept, dao.rawEvents(), me.name, me.nameUpdatedAt, dao.allDevices())
        if (out == remote) return // nothing new to say
        val body = JSONObject().put("ciphertext", Base64.getEncoder().encodeToString(sealDoc(k, out))).toString()
        when (val c = withContext(Dispatchers.IO) { http("PUT", url, token, etag, body).first }) {
            200 -> return
            409 -> {}
            else -> throw httpError(c)
        }
    }
    throw IOException("busy, try again")
}

/** Reads a group's blob without touching anything, to show its name before joining. Null if the server has nothing under that key. */
suspend fun peekGroup(j: Join): Remote? {
    val k = keysOf(j.secret)
    val (code, text) = withContext(Dispatchers.IO) { http("GET", "${j.url}/s/${k.id}", j.token) }
    return when (code) {
        200 -> parseState(openDoc(k, Base64.getDecoder().decode(JSONObject(text).getString("ciphertext"))))
        404 -> null
        else -> throw httpError(code)
    }
}

/** Adds the group from [j] (its blob [r] already read) and runs its first sync. */
suspend fun joinGroup(ctx: Context, j: Join, r: Remote) {
    count(j.url, keysOf(j.secret).id, j.token, "join")
    lock.withLock { // so a running sync never sees the URL without its key
        val id = Db.get(ctx).dao().insert(CounterGroup(name = r.name ?: ctx.getString(R.string.synced_group), nameUpdatedAt = r.nameUpdatedAt, syncUrl = j.url))
        keyPrefs(ctx).edit().putString("k$id", j.secret).putString("t$id", j.token).commit()
    }
    sync(ctx)
}

/** Tells the server this phone stopped syncing [g], so it can delete the group once no device is left. Call before the key is removed. */
suspend fun leaveGroup(ctx: Context, g: CounterGroup) {
    val keys = keyPrefs(ctx)
    val secret = keys.getString("k${g.id}", null) ?: return
    count(g.syncUrl ?: return, keysOf(secret).id, keys.getString("t${g.id}", "")!!, "leave")
}

/** Best effort: a failure only leaves the server's device count off by one. */
private suspend fun count(url: String, id: String, token: String, what: String) {
    try { withContext(Dispatchers.IO) { http("POST", "${url.trim().trimEnd('/')}/s/$id/$what", token) } } catch (_: IOException) {}
}

private fun httpError(code: Int) = IOException(if (code == 401) "wrong token" else "HTTP $code")

/** Background sync, so web changes and reminders stay current without opening the app. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { sync(applicationContext); return Result.success() }
}

private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

fun schedulePeriodicSync(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sync",
    ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build())

/** One sync as soon as there is network; for places that can't wait on HTTP (receivers). */
fun syncSoon(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE,
    OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build())

/** [ifMatch] is the etag the PUT expects; none means create only. */
private fun http(method: String, url: String, token: String, ifMatch: String? = null, body: String? = null): Pair<Int, String> {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.requestMethod = method; c.connectTimeout = 10_000; c.readTimeout = 20_000
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        if (ifMatch != null) c.setRequestProperty("If-Match", ifMatch)
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: "")
    } finally { c.disconnect() }
}

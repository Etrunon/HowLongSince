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

/** Excluded from backups. Per group: "k<id>" secret, "d<id>" this phone's device token, "a<id>" admin, "r<id>" stretched recovery password
 *  of a group not uploaded yet, "t<id>" the old server TOKEN (only to claim a group made before device tokens). */
fun keyPrefs(ctx: Context): android.content.SharedPreferences = ctx.getSharedPreferences("sync_keys", 0)

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
            syncGroup(ctx, dao, g, secret)
            ctx.getString(R.string.synced, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())) to false
        } catch (e: Exception) {
            ctx.getString(R.string.sync_failed, e.message) to true
        }
        prefs.edit().putString("syncStatus${g.id}", status.first).putBoolean("syncFailed${g.id}", status.second).apply()
    }
}

/** GET, decrypt, merge, encrypt, PUT; if someone else wrote in between (409) start over. */
private suspend fun syncGroup(ctx: Context, dao: CounterDao, g: CounterGroup, secret: String) {
    val k = keysOf(secret)
    val url = "${g.syncUrl!!.trim().trimEnd('/')}/s/${k.id}"
    val keys = keyPrefs(ctx)
    keys.getString("t${g.id}", null)?.takeIf { keys.getString("d${g.id}", null) == null && it.isNotEmpty() }?.let { legacy ->
        val (code, text) = withContext(Dispatchers.IO) { http("POST", "$url/claim", legacy, body = JSONObject().put("deviceId", Db.deviceId).toString()) }
        if (code == 200) keys.edit().putString("d${g.id}", JSONObject(text).getString("token")).remove("t${g.id}").commit() // a member; "Become admin" sets the password
    }
    val token = keys.getString("d${g.id}", null)
    val recovery = keys.getString("r${g.id}", null)
    if (token == null && recovery == null) throw IOException(ctx.getString(R.string.need_invite))
    repeat(5) {
        val (code, text) = withContext(Dispatchers.IO) { http("GET", url, token ?: "") }
        var etag: String? = null; var remote: String? = null
        when (code) {
            200 -> JSONObject(text).let { etag = it.getString("etag"); remote = openDoc(k, Base64.getDecoder().decode(it.getString("ciphertext"))) }
            404 -> if (recovery == null) throw IOException(ctx.getString(R.string.no_data)) // else: not uploaded yet
            401 -> throw IOException(ctx.getString(if (token == null) R.string.need_invite else R.string.no_access))
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
        val body = JSONObject().put("ciphertext", Base64.getEncoder().encodeToString(sealDoc(k, out)))
        if (etag == null) body.put("deviceId", Db.deviceId).put("recovery", recovery) // create: this phone becomes its admin
        val (c, answer) = withContext(Dispatchers.IO) { http("PUT", url, token ?: "", etag, body.toString()) }
        when (c) {
            200 -> {
                JSONObject(answer).optString("token").ifEmpty { null }?.let { keys.edit().putString("d${g.id}", it).putBoolean("a${g.id}", true).remove("r${g.id}").commit() }
                return
            }
            409 -> {}
            else -> throw httpError(c)
        }
    }
    throw IOException("busy, try again")
}

/** Joins the group in [j]: with an invite as a member, with an old server token by claiming, else with the recovery [password] as an admin.
 *  Null when joined (and synced once), else the reason. */
suspend fun joinGroup(ctx: Context, j: Join, password: String): String? {
    val k = keysOf(j.secret)
    val url = "${j.url}/s/${k.id}"
    val (code, text) = try {
        withContext(Dispatchers.IO) {
            val me = JSONObject().put("deviceId", Db.deviceId)
            when {
                j.nonce.isNotEmpty() -> http("POST", "$url/enroll", "", body = me.put("nonce", j.nonce).toString())
                j.token.isNotEmpty() -> http("POST", "$url/claim", j.token, body = me.toString())
                else -> http("POST", "$url/enroll", "", body = me.put("recovery", stretch(password, k.id)).toString())
            }
        }
    } catch (e: IOException) { return ctx.getString(R.string.sync_failed, e.message) }
    when (code) {
        200 -> {}
        404 -> return ctx.getString(R.string.no_data)
        401, 429 -> return ctx.getString(if (j.nonce.isEmpty() && j.token.isEmpty()) R.string.wrong_password else R.string.need_invite)
        else -> return ctx.getString(R.string.sync_failed, "HTTP $code")
    }
    lock.withLock { // so a running sync never sees the URL without its key
        val id = Db.get(ctx).dao().insert(CounterGroup(name = ctx.getString(R.string.synced_group), nameUpdatedAt = 0, syncUrl = j.url)) // the real name comes with the first sync
        keyPrefs(ctx).edit().putString("k$id", j.secret).putString("d$id", JSONObject(text).getString("token"))
            .putBoolean("a$id", j.nonce.isEmpty() && j.token.isEmpty()).commit()
    }
    sync(ctx)
    return null
}

/** This phone's call on group [g]'s server: (HTTP code, body). (0, "") if the group isn't synced here or the server can't be reached. */
private suspend fun call(ctx: Context, g: CounterGroup, method: String, path: String, body: JSONObject? = null): Pair<Int, String> {
    val keys = keyPrefs(ctx)
    val secret = keys.getString("k${g.id}", null) ?: return 0 to ""
    val url = "${(g.syncUrl ?: return 0 to "").trim().trimEnd('/')}/s/${keysOf(secret).id}/$path"
    return try { withContext(Dispatchers.IO) { http(method, url, keys.getString("d${g.id}", null) ?: "", body = body?.toString()) } } catch (_: IOException) { 0 to "" }
}

fun isAdmin(ctx: Context, g: CounterGroup) = keyPrefs(ctx).getBoolean("a${g.id}", false)
fun hasDevice(ctx: Context, g: CounterGroup) = keyPrefs(ctx).getString("d${g.id}", null) != null

/** The link to share: a fresh single-use invite for admins, else the key backup (joining with it needs the recovery password). Null if not synced. */
suspend fun inviteLink(ctx: Context, g: CounterGroup): Pair<String, Boolean>? {
    val secret = keyPrefs(ctx).getString("k${g.id}", null) ?: return null
    val nonce = if (isAdmin(ctx, g)) call(ctx, g, "POST", "invite", JSONObject()).let { (c, t) -> if (c == 200) JSONObject(t).getString("nonce") else null } else null
    return shareLink(Join(g.syncUrl ?: return null, secret, nonce.orEmpty())) to (nonce != null)
}

/** A device enrolled in a group, as an admin sees it. */
data class Member(val id: String, val admin: Boolean, val lastSeen: Long)

suspend fun members(ctx: Context, g: CounterGroup): List<Member>? = call(ctx, g, "GET", "devices").let { (c, t) ->
    if (c != 200) null else JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { Member(it.getString("id"), it.getBoolean("admin"), it.getLong("lastSeen")) } }
}

suspend fun revoke(ctx: Context, g: CounterGroup, deviceId: String) = call(ctx, g, "POST", "revoke", JSONObject().put("deviceId", deviceId)).first == 200

/** With the recovery password: a member becomes admin, an admin changes the password, a phone without access joins as admin,
 *  and a group not uploaded yet keeps it for its first upload. False if the password is wrong (or tried too often). */
suspend fun useRecovery(ctx: Context, g: CounterGroup, password: String): Boolean {
    val keys = keyPrefs(ctx)
    val secret = keys.getString("k${g.id}", null) ?: return false
    val recovery = withContext(Dispatchers.Default) { stretch(password, keysOf(secret).id) }
    val body = JSONObject().put("recovery", recovery)
    val ok = when {
        !hasDevice(ctx, g) -> call(ctx, g, "POST", "enroll", body.put("deviceId", Db.deviceId)).let { (c, t) ->
            when (c) {
                200 -> { keys.edit().putString("d${g.id}", JSONObject(t).getString("token")).commit(); true }
                404 -> { keys.edit().putString("r${g.id}", recovery).commit(); true } // not on the server yet: the next sync creates it
                else -> false
            }
        }
        isAdmin(ctx, g) -> call(ctx, g, "POST", "recovery", body).first == 200
        else -> call(ctx, g, "POST", "promote", body).first == 200 || call(ctx, g, "POST", "recovery", body).first == 200 // the second only works on a group without a password yet
    }
    if (ok) { keys.edit().putBoolean("a${g.id}", true).commit(); sync(ctx) }
    return ok
}

/** Tells the server this phone stopped syncing [g], so it can delete the group once no device is left. Call before the key is removed. */
suspend fun leaveGroup(ctx: Context, g: CounterGroup) { call(ctx, g, "POST", "leave", JSONObject()) }

/** Forgets everything this phone keeps for [g]'s sync. */
fun dropKeys(ctx: Context, id: Long) = keyPrefs(ctx).edit().remove("k$id").remove("d$id").remove("a$id").remove("r$id").remove("t$id").apply()

private fun httpError(code: Int) = IOException("HTTP $code")

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

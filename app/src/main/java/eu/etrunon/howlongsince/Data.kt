package eu.etrunon.howlongsince

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val tags: String = "",
    val notifyAfterMs: Long? = null,
    val nfcTagId: String? = null,
    val uid: String = UUID.randomUUID().toString(), // sync identity; id stays local (NFC tags, reminders)
    val updatedAt: Long = System.currentTimeMillis(), // last-write-wins on sync
    val deleted: Boolean = false, // tombstone, so deletes reach the server
    val archived: Boolean = false, // hidden from the list, no reminders, history kept
    val groupId: Long? = null, // null = Ungrouped. Local only for now: the sync protocol doesn't carry it
)

@Entity
data class CounterGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameUpdatedAt: Long = System.currentTimeMillis(), // newest rename wins on sync
    val deleted: Boolean = false,
    val syncUrl: String? = null, // null = local only. The key and token live in the "sync_keys" prefs, which backups skip
)

@Entity(foreignKeys = [ForeignKey(entity = Counter::class, parentColumns = ["id"], childColumns = ["counterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("counterId")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterId: Long,
    val at: Long,
    val source: String, // "create" | "manual" | "nfc"
    val uid: String = UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val note: String? = null, // set when logged, never edited (sync merges events by union)
)

data class CounterWithLast(@Embedded val counter: Counter, val lastAt: Long) {
    fun overdue(now: Long) = counter.notifyAfterMs?.let { now - lastAt >= it } == true
}

@Dao
interface CounterDao {
    @Query("SELECT Counter.*, MAX(Event.at) AS lastAt FROM Counter JOIN Event ON Event.counterId = Counter.id AND Event.deleted = 0 WHERE Counter.deleted = 0 GROUP BY Counter.id ORDER BY lastAt")
    fun counters(): Flow<List<CounterWithLast>>

    @Query("SELECT Counter.*, MAX(Event.at) AS lastAt FROM Counter JOIN Event ON Event.counterId = Counter.id AND Event.deleted = 0 WHERE Counter.deleted = 0 AND Counter.id = :id GROUP BY Counter.id")
    suspend fun counter(id: Long): CounterWithLast?

    @Query("SELECT id FROM Counter WHERE uid = :uid AND deleted = 0") suspend fun idOf(uid: String): Long?

    @Query("SELECT * FROM Event WHERE counterId = :id AND deleted = 0 ORDER BY at DESC")
    fun events(id: Long): Flow<List<Event>>

    @Query("SELECT at FROM Event WHERE counterId = :id AND deleted = 0 ORDER BY at")
    suspend fun times(id: Long): List<Long>

    @Query("UPDATE Counter SET nfcTagId = :uid, updatedAt = :now WHERE id = :id")
    suspend fun setTag(id: Long, uid: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE Counter SET nfcTagId = NULL, updatedAt = :now WHERE nfcTagId = :uid")
    suspend fun clearTag(uid: String, now: Long = System.currentTimeMillis())

    /** One tag belongs to one counter: steal it from any other counter. */
    @Transaction
    suspend fun pair(id: Long, uid: String) { clearTag(uid); setTag(id, uid) }

    @Query("SELECT * FROM Counter WHERE deleted = 0 ORDER BY id") suspend fun allCounters(): List<Counter>
    @Query("SELECT * FROM Event WHERE deleted = 0 ORDER BY at") suspend fun allEvents(): List<Event>
    @Query("SELECT * FROM Counter") suspend fun rawCounters(): List<Counter> // including tombstones, for sync
    @Query("SELECT * FROM Event") suspend fun rawEvents(): List<Event>

    @Query("SELECT * FROM CounterGroup WHERE deleted = 0 ORDER BY name COLLATE NOCASE") fun groups(): Flow<List<CounterGroup>>
    @Query("SELECT * FROM CounterGroup WHERE deleted = 0") suspend fun allGroups(): List<CounterGroup>
    @Insert suspend fun insert(c: CounterGroup): Long
    @Query("UPDATE CounterGroup SET name = :name, nameUpdatedAt = :now WHERE id = :id") suspend fun rename(id: Long, name: String, now: Long = System.currentTimeMillis())
    @Query("UPDATE Counter SET groupId = NULL WHERE groupId = :id") suspend fun ungroup(id: Long)
    @Query("UPDATE CounterGroup SET deleted = 1, nameUpdatedAt = :now WHERE id = :id") suspend fun tombstone(id: Long, now: Long)
    /** Its counters fall back to Ungrouped. */
    @Transaction
    suspend fun deleteGroup(id: Long) { ungroup(id); tombstone(id, System.currentTimeMillis()) }
    @Query("SELECT * FROM CounterGroup WHERE id = :id") suspend fun group(id: Long): CounterGroup?
    @Query("UPDATE CounterGroup SET syncUrl = :url WHERE id = :id") suspend fun setSyncUrl(id: Long, url: String?)
    /** Every loose counter joins group [id] (the old global sync becoming a group). */
    @Query("UPDATE Counter SET groupId = :id WHERE groupId IS NULL AND deleted = 0") suspend fun adopt(id: Long)

    @Query("DELETE FROM Counter WHERE uid IN (:uids)") suspend fun purge(uids: List<String>) // events go too (cascade)
    @Query("UPDATE Counter SET deleted = 1, updatedAt = :now WHERE deleted = 0 AND groupId IS :group") suspend fun deleteAll(now: Long, group: Long?)
    /** Drops tombstones older than [cutoff] for good, like the server does. */
    @Query("DELETE FROM Counter WHERE deleted = 1 AND updatedAt < :cutoff") suspend fun pruneDeleted(cutoff: Long)

    /** Import into group [group] (null = Ungrouped): the rest of that group is tombstoned (so the server drops it too), imported counters win as of now. */
    @Transaction
    suspend fun replaceAll(data: List<Pair<Counter, List<Event>>>, group: Long?) {
        val now = System.currentTimeMillis()
        purge(data.map { it.first.uid }); deleteAll(now, group)
        data.forEach { (c, events) ->
            val id = insert(c.copy(id = 0, groupId = group, updatedAt = now)) // file ids may clash with local ones
            events.forEach { insert(it.copy(id = 0, counterId = id)) }
        }
    }

    /**
     * Applies group [g]'s remote blob. Returns the local ids of counters that changed (to reschedule).
     * A counter that sits in another local group only comes over if the remote copy is newer (it was moved here elsewhere);
     * a tombstone of equal age revives, since moving writes a tombstone in the old group and the live copy in the new one at the same time.
     */
    @Transaction
    suspend fun merge(remote: Remote, g: Long): Set<Long> {
        val changed = mutableSetOf<Long>()
        val local = rawCounters().associateBy { it.uid }.toMutableMap()
        for (c in remote.counters) {
            val l = local[c.uid]
            if (l == null) local[c.uid] = c.copy(id = insert(c.copy(id = 0, groupId = g)), groupId = g).also { changed += it.id }
            else if (c.updatedAt > l.updatedAt || (l.groupId != g && l.deleted && !c.deleted && c.updatedAt == l.updatedAt)) {
                updateRaw(c.copy(id = l.id, groupId = g)); local[c.uid] = c.copy(id = l.id, groupId = g); changed += l.id
            }
        }
        val events = rawEvents().associateBy { it.uid }
        for ((counterUid, e) in remote.events) {
            val cid = local[counterUid]?.id ?: continue
            val l = events[e.uid]
            if (l == null) { insert(e.copy(id = 0, counterId = cid)); changed += cid }
            else if (e.deleted && !l.deleted) { deleteEvent(l.id); changed += cid }
        }
        val mine = group(g)
        if (mine != null && remote.name != null && remote.nameUpdatedAt > mine.nameUpdatedAt) rename(g, remote.name, remote.nameUpdatedAt)
        return changed
    }

    @Insert suspend fun insert(c: Counter): Long
    @Insert suspend fun insert(e: Event): Long
    @Query("UPDATE Event SET deleted = 1 WHERE id = :id") suspend fun deleteEvent(id: Long)
    @Update suspend fun updateRaw(c: Counter)
    suspend fun update(c: Counter) = updateRaw(c.copy(updatedAt = System.currentTimeMillis()))
    @Query("UPDATE Counter SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: Long, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun create(c: Counter): Long {
        val id = insert(c)
        insert(Event(counterId = id, at = System.currentTimeMillis(), source = "create"))
        return id
    }

    suspend fun reset(id: Long, source: String = "manual", at: Long = System.currentTimeMillis(), note: String? = null) =
        insert(Event(counterId = id, at = at, source = source, note = note?.trim()?.ifEmpty { null }))
}

@Database(entities = [Counter::class, Event::class, CounterGroup::class], version = 5, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): CounterDao

    companion object {
        /** v2: sync columns. Existing rows get random uids. */
        private val v2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = listOf(
                "ALTER TABLE Counter ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE Counter ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE Counter ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE Event ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE Event ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
                "UPDATE Counter SET uid = lower(hex(randomblob(16)))",
                "UPDATE Event SET uid = lower(hex(randomblob(16)))",
            ).forEach(db::execSQL)
        }
        /** v3: archived counters, notes on resets. */
        private val v3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = listOf(
                "ALTER TABLE Counter ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE Event ADD COLUMN note TEXT",
            ).forEach(db::execSQL)
        }
        /** v4: groups. Existing counters stay Ungrouped. */
        private val v4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = listOf(
                "CREATE TABLE IF NOT EXISTS CounterGroup (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameUpdatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, syncUrl TEXT, syncKey TEXT)",
                "ALTER TABLE Counter ADD COLUMN groupId INTEGER",
            ).forEach(db::execSQL)
        }
        /** v5: the sync key moved out of the database (so backups skip it); the column is dropped. Nobody had a key yet. */
        private val v5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = listOf(
                "CREATE TABLE CounterGroup_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameUpdatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, syncUrl TEXT)",
                "INSERT INTO CounterGroup_new SELECT id, name, nameUpdatedAt, deleted, syncUrl FROM CounterGroup",
                "DROP TABLE CounterGroup",
                "ALTER TABLE CounterGroup_new RENAME TO CounterGroup",
            ).forEach(db::execSQL)
        }
        @Volatile private var instance: Db? = null
        fun get(ctx: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "hls.db").addMigrations(v2, v3, v4, v5).build().also { instance = it }
        }
    }
}

/** Auto units: "5m", "3h 12m", "4d 2h", "1y 30d" (Italian: "4g 2h", "1a 30g"). */
fun since(ms: Long, italian: Boolean = java.util.Locale.getDefault().language == "it"): String {
    val (dd, yy) = if (italian) "g" to "a" else "d" to "y"
    val m = maxOf(ms, 0) / 60_000
    val h = m / 60; val d = h / 24; val y = d / 365
    return when {
        h < 1 -> "${m}m"
        d < 1 -> "${h}h ${m % 60}m"
        y < 1 -> "${d}$dd ${h % 24}h"
        else -> "${y}$yy ${d % 365}$dd"
    }
}

/** Big-number form for cards: 12 to the plural resource for "days". Same thresholds as [since]. */
fun sinceBig(ms: Long): Pair<Long, Int> {
    val m = maxOf(ms, 0) / 60_000
    val h = m / 60; val d = h / 24; val y = d / 365
    return when { h < 1 -> m to R.plurals.n_minutes; d < 1 -> h to R.plurals.n_hours; y < 1 -> d to R.plurals.n_days; else -> y to R.plurals.n_years }
}

/** "Home, health ,home" -> [home, health]. Tags are stored normalized, comma-joined. */
fun tagList(s: String) = s.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

/** Average gap between resets ([times] ascending), or null with fewer than 2 gaps. */
fun avgGap(times: List<Long>): Long? = if (times.size < 3) null else (times.last() - times.first()) / (times.size - 1)

/** [name], or "name (2)", "name (3)"... if [taken] (case-insensitive). */
fun uniqueName(name: String, taken: Collection<String>): String =
    generateSequence(1) { it + 1 }.map { if (it == 1) name else "$name ($it)" }.first { n -> taken.none { it.equals(n, true) } }

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
)

data class CounterWithLast(@Embedded val counter: Counter, val lastAt: Long)

@Dao
interface CounterDao {
    @Query("SELECT Counter.*, MAX(Event.at) AS lastAt FROM Counter JOIN Event ON Event.counterId = Counter.id AND Event.deleted = 0 WHERE Counter.deleted = 0 GROUP BY Counter.id ORDER BY lastAt")
    fun counters(): Flow<List<CounterWithLast>>

    @Query("SELECT Counter.*, MAX(Event.at) AS lastAt FROM Counter JOIN Event ON Event.counterId = Counter.id AND Event.deleted = 0 WHERE Counter.deleted = 0 AND Counter.id = :id GROUP BY Counter.id")
    suspend fun counter(id: Long): CounterWithLast?

    @Query("SELECT * FROM Event WHERE counterId = :id AND deleted = 0 ORDER BY at DESC")
    fun events(id: Long): Flow<List<Event>>

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

    @Query("DELETE FROM Counter WHERE uid IN (:uids)") suspend fun purge(uids: List<String>) // events go too (cascade)
    @Query("UPDATE Counter SET deleted = 1, updatedAt = :now WHERE deleted = 0") suspend fun deleteAll(now: Long)

    /** Import: everything else is tombstoned (so the server drops it too), imported counters win as of now. */
    @Transaction
    suspend fun replaceAll(data: List<Pair<Counter, List<Event>>>) {
        val now = System.currentTimeMillis()
        purge(data.map { it.first.uid }); deleteAll(now)
        data.forEach { (c, events) -> insert(c.copy(updatedAt = now)); events.forEach { insert(it) } }
    }

    /** Applies the server's state. Returns the local ids of counters that changed (to reschedule). */
    @Transaction
    suspend fun merge(remote: Remote): Set<Long> {
        val changed = mutableSetOf<Long>()
        val local = rawCounters().associateBy { it.uid }.toMutableMap()
        for (c in remote.counters) {
            val l = local[c.uid]
            if (l == null) local[c.uid] = c.copy(id = insert(c.copy(id = 0))).also { changed += it.id }
            else if (c.updatedAt > l.updatedAt) { updateRaw(c.copy(id = l.id)); changed += l.id }
        }
        val events = rawEvents().associateBy { it.uid }
        for ((counterUid, e) in remote.events) {
            val cid = local[counterUid]?.id ?: continue
            val l = events[e.uid]
            if (l == null) { insert(e.copy(id = 0, counterId = cid)); changed += cid }
            else if (e.deleted && !l.deleted) { deleteEvent(l.id); changed += cid }
        }
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

    suspend fun reset(id: Long, source: String = "manual") =
        insert(Event(counterId = id, at = System.currentTimeMillis(), source = source))
}

@Database(entities = [Counter::class, Event::class], version = 2, exportSchema = false)
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
        @Volatile private var instance: Db? = null
        fun get(ctx: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "hls.db").addMigrations(v2).build().also { instance = it }
        }
    }
}

/** Auto units: "5m", "3h 12m", "4d 2h", "1y 30d". */
fun since(ms: Long): String {
    val m = maxOf(ms, 0) / 60_000
    val h = m / 60; val d = h / 24; val y = d / 365
    return when {
        h < 1 -> "${m}m"
        d < 1 -> "${h}h ${m % 60}m"
        y < 1 -> "${d}d ${h % 24}h"
        else -> "${y}y ${d % 365}d"
    }
}

/** Big-number form for cards: 12 to "days". Same thresholds as [since]. */
fun sinceBig(ms: Long): Pair<String, String> {
    val m = maxOf(ms, 0) / 60_000
    val h = m / 60; val d = h / 24; val y = d / 365
    val (n, unit) = when { h < 1 -> m to "minute"; d < 1 -> h to "hour"; y < 1 -> d to "day"; else -> y to "year" }
    return n.toString() to if (n == 1L) unit else unit + "s"
}

/** "Home, health ,home" -> [home, health]. Tags are stored normalized, comma-joined. */
fun tagList(s: String) = s.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

package eu.etrunon.howlongsince

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val tags: String = "",
    val notifyAfterMs: Long? = null,
    val nfcTagId: String? = null,
)

@Entity(foreignKeys = [ForeignKey(entity = Counter::class, parentColumns = ["id"], childColumns = ["counterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("counterId")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterId: Long,
    val at: Long,
    val source: String, // "create" | "manual" | "nfc"
)

data class CounterWithLast(@Embedded val counter: Counter, val lastAt: Long)

@Dao
interface CounterDao {
    @Query("SELECT Counter.*, MAX(Event.at) AS lastAt FROM Counter JOIN Event ON Event.counterId = Counter.id GROUP BY Counter.id ORDER BY lastAt")
    fun counters(): Flow<List<CounterWithLast>>

    @Query("SELECT Counter.*, MAX(Event.at) AS lastAt FROM Counter JOIN Event ON Event.counterId = Counter.id WHERE Counter.id = :id GROUP BY Counter.id")
    suspend fun counter(id: Long): CounterWithLast?

    @Query("SELECT * FROM Event WHERE counterId = :id ORDER BY at DESC")
    fun events(id: Long): Flow<List<Event>>

    @Query("UPDATE Counter SET nfcTagId = :uid WHERE id = :id")
    suspend fun setTag(id: Long, uid: String?)

    @Query("UPDATE Counter SET nfcTagId = NULL WHERE nfcTagId = :uid")
    suspend fun clearTag(uid: String)

    /** One tag belongs to one counter: steal it from any other counter. */
    @Transaction
    suspend fun pair(id: Long, uid: String) { clearTag(uid); setTag(id, uid) }

    @Query("SELECT * FROM Counter ORDER BY id") suspend fun allCounters(): List<Counter>
    @Query("SELECT * FROM Event ORDER BY at") suspend fun allEvents(): List<Event>

    @Query("DELETE FROM Counter") suspend fun deleteAll() // events go too (cascade)

    @Transaction
    suspend fun replaceAll(data: List<Pair<Counter, List<Event>>>) {
        deleteAll()
        data.forEach { (c, events) -> insert(c); events.forEach { insert(it) } }
    }

    @Insert suspend fun insert(c: Counter): Long
    @Insert suspend fun insert(e: Event): Long
    @Query("DELETE FROM Event WHERE id = :id") suspend fun deleteEvent(id: Long)
    @Update suspend fun update(c: Counter)
    @Delete suspend fun delete(c: Counter)

    @Transaction
    suspend fun create(c: Counter): Long {
        val id = insert(c)
        insert(Event(counterId = id, at = System.currentTimeMillis(), source = "create"))
        return id
    }

    suspend fun reset(id: Long, source: String = "manual") =
        insert(Event(counterId = id, at = System.currentTimeMillis(), source = source))
}

@Database(entities = [Counter::class, Event::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): CounterDao

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "hls.db").build().also { instance = it }
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

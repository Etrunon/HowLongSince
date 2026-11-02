package eu.etrunon.howlongsince

import org.junit.Assert.assertEquals
import org.junit.Test

class SinceTest {
    private val m = 60_000L; private val h = 60 * m; private val d = 24 * h
    @Test fun units() {
        assertEquals("0m", since(-5))
        assertEquals("59m", since(59 * m))
        assertEquals("1h 0m", since(h))
        assertEquals("3d 2h", since(3 * d + 2 * h + 5 * m))
        assertEquals("1y 30d", since(395 * d))
    }
}

class TagsTest {
    @Test fun parse() {
        assertEquals(listOf("home", "health"), tagList(" Home, health ,home,, "))
        assertEquals(emptyList<String>(), tagList(""))
    }
}

class NotifyDelayTest {
    @Test fun delay() {
        assertEquals(null, notifyDelay(0, null, 10))
        assertEquals(90L, notifyDelay(0, 100, 10))
        assertEquals(null, notifyDelay(0, 100, 200)) // already overdue: don't fire on edit
    }
}

class NfcUriTest {
    @Test fun roundTrip() {
        assertEquals(42L, parseResetUri(resetUri(42)))
        assertEquals(null, parseResetUri("howlongsince://reset/abc"))
        assertEquals(null, parseResetUri("https://evil/howlongsince://reset/1"))
        assertEquals(null, parseResetUri(null))
        assertEquals("00ff10", byteArrayOf(0, -1, 16).toHex())
    }
}

class SinceBigTest {
    @Test fun big() {
        assertEquals("0" to "minutes", sinceBig(0))
        assertEquals("1" to "hour", sinceBig(3_600_000))
        assertEquals("12" to "days", sinceBig(12 * 86_400_000L + 5))
        assertEquals("2" to "years", sinceBig(800 * 86_400_000L))
    }
}

class BackupTest {
    @Test fun roundTrip() {
        val c = listOf(Counter(3, "Water", "home,plants", 3_600_000, "04ab"), Counter(7, "Run"))
        val e = listOf(Event(1, 3, 100, "create"), Event(2, 3, 200, "nfc"), Event(3, 7, 50, "create"))
        val back = fromJson(toJson(c, e))
        assertEquals(c, back.map { it.first })
        assertEquals(e.map { it.copy(id = 0) }, back.flatMap { it.second })
    }

    @Test fun rejectsBadFiles() {
        fun fails(s: String) = assertEquals(true, runCatching { fromJson(s) }.isFailure)
        fails("not json")
        fails("""{"version":2,"counters":[]}""")
        fails("""{"version":1,"counters":[{"id":1,"label":"x","events":[]}]}""")
        fails("""{"version":1,"counters":[{"id":1,"label":" ","events":[{"at":1}]}]}""")
        val ok = """{"id":1,"label":"x","events":[{"at":1}]}"""
        fails("""{"version":1,"counters":[$ok,$ok]}""")
    }
}

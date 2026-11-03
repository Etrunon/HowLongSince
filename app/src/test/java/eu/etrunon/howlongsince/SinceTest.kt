package eu.etrunon.howlongsince

import org.junit.Assert.assertEquals
import org.junit.Test

class SinceTest {
    private val m = 60_000L; private val h = 60 * m; private val d = 24 * h
    @Test fun units() {
        assertEquals("0m", since(-5, false))
        assertEquals("59m", since(59 * m, false))
        assertEquals("1h 0m", since(h, false))
        assertEquals("3d 2h", since(3 * d + 2 * h + 5 * m, false))
        assertEquals("3g 2h", since(3 * d + 2 * h + 5 * m, true))
        assertEquals("1y 30d", since(395 * d, false))
        assertEquals("1a 30g", since(395 * d, true))
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
        val uuid = "0b9e8f3a-1c2d-4e5f-8a9b-0c1d2e3f4a5b"
        assertEquals(uuid, parseResetUri(resetUri(uuid)))
        assertEquals("0123456789abcdef0123456789abcdef", parseResetUri(resetUri("0123456789abcdef0123456789abcdef"))) // uid of a migrated counter
        assertEquals(null, parseResetUri("howlongsince://reset/42"))
        assertEquals(null, parseResetUri("howlongsince://reset/abc"))
        assertEquals(null, parseResetUri("https://evil/howlongsince://reset/1"))
        assertEquals(null, parseResetUri(null))
        assertEquals("00ff10", byteArrayOf(0, -1, 16).toHex())
    }
}

class SinceTwoTest {
    @Test fun two() {
        assertEquals("0m" to "0s", sinceTwo(-5, false))
        assertEquals("12m" to "34s", sinceTwo(12 * 60_000L + 34_000, false))
        assertEquals("1h" to "0m", sinceTwo(3_600_000, false))
        assertEquals("12d" to "0h", sinceTwo(12 * 86_400_000L + 5, false))
        assertEquals("3g" to "4h", sinceTwo(3 * 86_400_000L + 4 * 3_600_000L, true))
        assertEquals("2y" to "70d", sinceTwo(800 * 86_400_000L, false))
    }
}

class BackupTest {
    @Test fun roundTrip() {
        val c = listOf(Counter(3, "Water", "home,plants", 3_600_000, "04ab"), Counter(7, "Run", archived = true))
        val e = listOf(Event(1, 3, 1000, "create"), Event(2, 3, 2000, "nfc", note = "new filter"), Event(3, 7, 5000, "create"))
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

class SyncJsonTest {
    @Test fun roundTrip() {
        val c = listOf(Counter(3, "Water", "home", 3_600_000, "04ab", uid = "a", updatedAt = 5, deleted = true, archived = true))
        val e = listOf(Event(1, 3, 100, "nfc", uid = "e", deleted = true, note = "hi"), Event(2, 99, 1, "manual")) // orphan dropped
        val r = parseState(stateJson(c, e))
        assertEquals(c.map { it.copy(id = 0) }, r.counters)
        assertEquals(listOf("a" to e[0].copy(id = 0, counterId = 0, uid = r.events[0].second.uid)), r.events) // event uids are local since v4
    }
}

class AvgGapTest {
    @Test fun avg() {
        assertEquals(null, avgGap(listOf(1, 5)))
        assertEquals(5L, avgGap(listOf(0, 4, 10)))
    }
}

class GroupTest {
    @Test fun uniqueNames() {
        assertEquals("Family", uniqueName("Family", listOf("Home")))
        assertEquals("Family (2)", uniqueName("Family", listOf("family")))
        assertEquals("Family (3)", uniqueName("Family", listOf("Family", "Family (2)")))
    }

    @Test fun backupKeepsGroup() {
        val c = listOf(Counter(3, "Water", groupId = 9), Counter(7, "Run"))
        val e = listOf(Event(1, 3, 100, "create"), Event(2, 7, 50, "create"))
        assertEquals(listOf("Home", null), fromJson(toJson(c, e, mapOf(9L to "Home"))).map { it.third })
    }
}

class CryptoTest {
    @Test fun hkdfMatchesRfc5869() { // test case 3: no salt, no info (first 32 bytes of the output)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d", hkdf(ByteArray(22) { 0x0b }, "", 32).toHex())
    }

    @Test fun sealOpen() {
        val k = keysOf(newSecret())
        assertEquals(32, k.id.length)
        assertEquals("hello", open(k, seal(k, "hello".toByteArray())).decodeToString())
        val blob = seal(k, "hello".toByteArray())
        fun fails(f: () -> Unit) = assertEquals(true, runCatching(f).isFailure)
        fails { open(keysOf(newSecret()), blob) } // wrong key
        fails { open(Keys("0".repeat(32), k.aes), blob) } // right key, blob served under another space id
        fails { open(k, blob.also { it[20] = (it[20] + 1).toByte() }) } // tampered
    }

    @Test fun groupStateIsStable() {
        val c = listOf(Counter(2, "b", uid = "b"), Counter(1, "a", uid = "a"))
        val e = listOf(Event(2, 2, 5, "create", uid = "y"), Event(1, 1, 5, "create", uid = "x"))
        assertEquals(stateJson(c, e, "Family", 7), stateJson(c.reversed(), e.reversed(), "Family", 7))
        val r = parseState(stateJson(c, e, "Family", 7))
        assertEquals("Family" to 7L, r.name to r.nameUpdatedAt)
    }
}

class JoinTest {
    private val secret = newSecret()
    @Test fun roundTrip() {
        val j = Join("https://hls.example.com/sub", secret, "abc")
        assertEquals(j, parseJoin(shareLink(j)))
        assertEquals(j.copy(nonce = ""), parseJoin(shareLink(j.copy(nonce = ""))))
        assertEquals(Join("https://x.example", secret, token = "to ken&1"), parseJoin("https://x.example/#k=$secret&t=to+ken%261")) // links from before invites
        assertEquals(j, parseJoin("howlongsince://join?h=https%3A%2F%2Fhls.example.com%2Fsub%2F#k=$secret&n=abc"))
    }

    @Test fun rejects() {
        for (s in listOf("", "hello", "https://x.example/", "https://x.example/#k=abc", "ftp://x.example/#k=$secret", "howlongsince://join#k=$secret", "howlongsince://join?h=nope#k=$secret"))
            assertEquals(s, null, parseJoin(s))
    }

    @Test fun recoveryStretchMatchesTheWebApp() = // the same vector as STRETCHED in server/web.test.mjs
        assertEquals("PHwhiIx1cylSWJVPIVMmYTP3aZR2NDTPX7TPSB8+mWo=", stretch("correct horse", "972a5ac5604d28f549b06e0ee52d7e3a"))
}

class WebCryptoTest {
    @Test fun opensWhatTheWebAppSealed() { // made by the browser's WebCrypto (server/index.html) with the same secret
        val k = keysOf("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8")
        assertEquals("972a5ac5604d28f549b06e0ee52d7e3a", k.id)
        val blob = java.util.Base64.getDecoder().decode("GoJMF2CGeZ3soko5Z05lyNidsw+Mht7X9Cfs6+3noBSR7OD5nkrzWlQDSzTn0g==")
        assertEquals("""{"hello":"wörld"}""", open(k, blob).decodeToString())
    }
}

class DocV4Test {
    private val k = keysOf("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8")
    private val c = listOf(Counter(1, "Water plants", tags = "garden", uid = "c1", updatedAt = 5))
    private val e = listOf(Event(1, 1, 5000, "manual", uid = "e1", note = "new filter", deviceId = "d1"))
    private val plain = stateJson(c, e, "Família", 7, listOf(Device("d1", "Pixel 9", 1)))

    @Test fun roundTripHidesText() {
        val blob = sealDoc(k, plain)
        assertEquals(plain, openDoc(k, blob))
        val text = blob.decodeToString()
        for (secret in listOf("Water plants", "garden", "new filter", "Pixel 9", "Família")) assertEquals(secret, false, text.contains(secret))
        assertEquals(true, text.contains("updatedAt"))
    }

    @Test fun noDeviceIdInBlob() {
        val text = sealDoc(k, plain).decodeToString()
        assertEquals(false, text.contains("deviceId")) // "d1" stays readable as the devices' id, so it can't be asserted absent
        assertEquals(false, text.contains("counterId"))
        assertEquals(false, org.json.JSONObject(org.json.JSONObject(text).getString("doc")).getJSONArray("counters").getJSONObject(0).getJSONArray("events").getJSONObject(0).has("id"))
    }

    @Test fun eventWithoutDeviceIdStaysWithout() {
        val p = stateJson(c, listOf(e[0].copy(deviceId = null), e[0].copy(at = 6000)), "Família", 7, listOf(Device("d1", "Pixel 9", 1)))
        assertEquals(p, openDoc(k, sealDoc(k, p)))
    }

    @Test fun v3Rejected() {
        val v2 = org.json.JSONObject(sealDoc(k, plain).decodeToString()).put("v", 3).toString().toByteArray()
        assertEquals(true, runCatching { openDoc(k, v2) }.isFailure)
    }

    @Test fun opensFixedV4() { // server/web.test.mjs opens the same blob (KOTLIN_V4)
        val blob = java.util.Base64.getDecoder().decode(System.getProperty("x") ?: sealDoc(k, plain).let { java.util.Base64.getEncoder().encodeToString(it) })
        assertEquals(plain, openDoc(k, blob))
    }

    @Test fun sameSecondIsOneEvent() {
        assertEquals(secs(5999), secs(5000))
        val p = stateJson(c, listOf(e[0], e[0].copy(deleted = true, note = null)), "Família", 7, listOf(Device("d1", "Pixel 9", 1)))
        val back = parseState(openDoc(k, sealDoc(k, p)))
        assertEquals(listOf("c1" to 5000L), back.events.map { it.first to it.second.at })
        assertEquals(true, back.events[0].second.deleted); assertEquals("new filter", back.events[0].second.note)
    }

    @Test fun eventsMatchByCounterAndAt() { // what merge keys on survives the round trip, with no event ids involved
        val two = c + Counter(2, "Run", uid = "c0", updatedAt = 5)
        val ev = listOf(e[0], e[0].copy(counterId = 2, at = 5000), e[0].copy(at = 2000))
        val back = parseState(openDoc(k, sealDoc(k, stateJson(two, ev, null, 0, emptyList())))).events
        assertEquals(listOf("c0" to 5000L, "c1" to 2000L, "c1" to 5000L), back.map { it.first to it.second.at })
    }

    @Test fun tamperFails() {
        val env = org.json.JSONObject(sealDoc(k, plain).decodeToString())
        val bad = env.put("doc", env.getString("doc").replace("\"at\":5000", "\"at\":6000")).toString().toByteArray()
        assertEquals(true, runCatching { openDoc(k, bad) }.isFailure)
    }
}

class WebBackupTest {
    // The same text as FIXTURE in server/web.test.mjs: a file in the phone's format, which the web app must read too.
    private val phoneFile = """{"version":1,"exportedAt":"2026-11-03T10:00:00Z","counters":[
{"id":1,"label":"Water plants","tags":["home","plants"],"notifyAfterMs":259200000,"nfcTagId":null,"uid":"11111111-1111-4111-8111-111111111111","updatedAt":1700000000000,"archived":false,"group":"Home","events":[{"at":1700000000000,"time":"2023-11-14T22:13:20Z","source":"create","uid":"e1"},{"at":1700003600000,"time":"2023-11-14T23:13:20Z","source":"manual","uid":"e2","note":"new filter"}]},
{"id":2,"label":"Run","tags":[],"notifyAfterMs":null,"nfcTagId":"04ab","uid":"22222222-2222-4222-8222-222222222222","updatedAt":1700000000000,"archived":true,"group":null,"events":[{"at":1700000000000,"time":"2023-11-14T22:13:20Z","source":"create","uid":"e3"}]}]}"""

    // Written by the web app's toBackup (exported with a local counter and a "Family" one).
    private val webFile = """{"version":1,"exportedAt":"2026-11-03T10:00:00.000Z","counters":[{"id":1,"label":"Local one","tags":[],"notifyAfterMs":86400000,"nfcTagId":null,"uid":"u1","updatedAt":5,"archived":false,"group":null,"events":[{"at":1700000000000,"time":"2023-11-14T22:13:20.000Z","source":"create","uid":"ev1"}]},{"id":2,"label":"Web counter","tags":["home","plants"],"notifyAfterMs":86400000,"nfcTagId":null,"uid":"u2","updatedAt":5,"archived":true,"group":"Family","events":[{"at":1700000000000,"time":"2023-11-14T22:13:20.000Z","source":"create","uid":"ev2"},{"at":1700003600000,"time":"2023-11-14T23:13:20.000Z","source":"manual","uid":"ev3","note":"from web"}]}]}"""

    @Test fun readsThePhoneFormat() {
        val b = fromJson(phoneFile)
        assertEquals(listOf("Water plants", "Run"), b.map { it.first.label })
        assertEquals(listOf<String?>("Home", null), b.map { it.third })
        assertEquals("home,plants", b[0].first.tags)
        assertEquals("new filter", b[0].second[1].note)
        assertEquals(true, b[1].first.archived)
    }

    @Test fun readsWhatTheWebAppExports() {
        val b = fromJson(webFile)
        assertEquals(listOf("Local one", "Web counter"), b.map { it.first.label })
        assertEquals(listOf<String?>(null, "Family"), b.map { it.third })
        assertEquals("u2", b[1].first.uid)
        assertEquals("home,plants", b[1].first.tags)
        assertEquals(listOf("create", "manual"), b[1].second.map { it.source })
        assertEquals("from web", b[1].second[1].note)
    }
}

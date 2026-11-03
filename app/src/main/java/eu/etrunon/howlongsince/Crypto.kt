package eu.etrunon.howlongsince

import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** What a group's 32-byte secret turns into: the id the server sees, and the AES-256-GCM key. The server never gets the secret. */
class Keys(val id: String, val aes: SecretKeySpec)

private fun hmac(key: ByteArray, data: ByteArray) = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

/** HKDF-SHA256 (RFC 5869) with no salt, for up to 32 bytes (one block). */
fun hkdf(secret: ByteArray, info: String, len: Int): ByteArray =
    hmac(hmac(ByteArray(32), secret), info.toByteArray() + byteArrayOf(1)).copyOf(len)

fun newSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

fun keysOf(secret: String): Keys {
    val s = Base64.getUrlDecoder().decode(secret)
    return Keys(hkdf(s, "hls id", 16).toHex(), SecretKeySpec(hkdf(s, "hls key", 32), "AES"))
}

/** nonce + ciphertext. The space id is authenticated too, so the server can't serve one group's blob as another's. */
fun seal(k: Keys, plain: ByteArray, ad: ByteArray = k.id.toByteArray()): ByteArray {
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k.aes, GCMParameterSpec(128, nonce)); updateAAD(ad) }
    return nonce + c.doFinal(plain)
}

/** Throws if the blob was altered, or belongs to another space or key. */
fun open(k: Keys, blob: ByteArray, ad: ByteArray = k.id.toByteArray()): ByteArray {
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k.aes, GCMParameterSpec(128, blob.copyOfRange(0, 12))); updateAAD(ad) }
    return c.doFinal(blob, 12, blob.size - 12)
}

// Format v2 (same as server/index.html): typed text is sealed per field, the rest stays readable for the server's stats.
// The envelope is {"v":2,"doc":"<json>","mac":...}; the mac seals SHA-256 of the exact doc bytes, so nobody without the key can edit anything.
private val SEALED = mapOf("counters" to listOf("label", "tags", "nfcTagId"), "events" to listOf("note", "deviceId"), "devices" to listOf("name"))
private val b64 = Base64.getEncoder(); private val unb64 = Base64.getDecoder()
private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
private fun fieldAd(k: Keys, uid: String, field: String) = "${k.id}\u0000$uid\u0000$field".toByteArray()

/** Replaces each sealed field of a group state in place (keeps key order, so the text stays comparable). */
private fun mapFields(k: Keys, o: JSONObject, f: (String, ByteArray) -> String) {
    if (!o.isNull("name")) o.put("name", f(o.getString("name"), fieldAd(k, "", "name")))
    for ((list, fields) in SEALED) o.optJSONArray(list)?.let { a ->
        for (i in 0 until a.length()) a.getJSONObject(i).let { r ->
            for (fl in fields) if (r.has(fl) && !r.isNull(fl)) r.put(fl, f(r.getString(fl), fieldAd(k, r.getString("id"), fl)))
        }
    }
}

/** A plain group state (stateJson) to the v2 blob. */
fun sealDoc(k: Keys, plain: String): ByteArray {
    val doc = JSONObject(plain).also { o -> mapFields(k, o) { v, ad -> b64.encodeToString(seal(k, v.toByteArray(), ad)) } }.toString()
    return JSONObject().put("v", 2).put("doc", doc).put("mac", b64.encodeToString(seal(k, sha256(doc.toByteArray())))).toString().toByteArray()
}

/** A v1 or v2 blob to the plain group state. Throws if anything was altered. */
fun openDoc(k: Keys, blob: ByteArray): String {
    val env = try { JSONObject(blob.decodeToString()).takeIf { it.optInt("v") == 2 } } catch (_: JSONException) { null }
        ?: return open(k, blob).decodeToString()
    val doc = env.getString("doc")
    if (!MessageDigest.isEqual(open(k, unb64.decode(env.getString("mac"))), sha256(doc.toByteArray()))) throw GeneralSecurityException("tampered")
    return JSONObject(doc).also { o -> mapFields(k, o) { v, ad -> open(k, unb64.decode(v), ad).decodeToString() } }.toString()
}

/** The recovery password as the server gets it (same as index.html): PBKDF2-HMAC-SHA256 over UTF-8, salted with the group id, 600k rounds, 32 bytes.
 *  Written out (one block) because Android's PBKDF2 providers differ on non-ASCII passwords. Slow on purpose: call off the main thread. */
fun stretch(password: String, id: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password.toByteArray(), "HmacSHA256")) }
    var u = mac.doFinal(id.toByteArray() + byteArrayOf(0, 0, 0, 1))
    val out = u.copyOf()
    repeat(600_000 - 1) { u = mac.doFinal(u); for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte() }
    return b64.encodeToString(out)
}

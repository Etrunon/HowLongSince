package eu.etrunon.howlongsince

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
fun seal(k: Keys, plain: ByteArray): ByteArray {
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k.aes, GCMParameterSpec(128, nonce)); updateAAD(k.id.toByteArray()) }
    return nonce + c.doFinal(plain)
}

/** Throws if the blob was altered, or belongs to another space or key. */
fun open(k: Keys, blob: ByteArray): ByteArray {
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k.aes, GCMParameterSpec(128, blob.copyOfRange(0, 12))); updateAAD(k.id.toByteArray()) }
    return c.doFinal(blob, 12, blob.size - 12)
}

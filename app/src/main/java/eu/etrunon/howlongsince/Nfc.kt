package eu.etrunon.howlongsince

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

private const val PREFIX = "howlongsince://reset/"

// The counter's sync uid, not its local id, so a tag works on every phone synced to the same server.
fun resetUri(uid: String) = "$PREFIX$uid"
fun parseResetUri(s: String?): String? = s?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.matches(Regex("[0-9a-f-]{32,36}")) }
fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

/** Writes the counter's reset URI to the tag. Returns an error message (string resource), or null on success. Blocking. */
fun writeTag(tag: Tag, uid: String): Int? = try {
    val msg = NdefMessage(NdefRecord.createUri(resetUri(uid)))
    val ndef = Ndef.get(tag)
    val formatable = NdefFormatable.get(tag)
    when {
        ndef != null -> ndef.use {
            it.connect()
            when {
                !it.isWritable -> R.string.tag_read_only
                it.maxSize < msg.byteArrayLength -> R.string.tag_too_small
                else -> { it.writeNdefMessage(msg); null }
            }
        }
        formatable != null -> formatable.use { it.connect(); it.format(msg); null }
        else -> R.string.tag_no_ndef
    }
} catch (e: Exception) { // tag moved away mid-write, etc.
    R.string.tag_write_failed
}

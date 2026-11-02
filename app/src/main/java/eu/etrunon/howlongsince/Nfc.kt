package eu.etrunon.howlongsince

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

private const val PREFIX = "howlongsince://reset/"

fun resetUri(id: Long) = "$PREFIX$id"
fun parseResetUri(s: String?): Long? = s?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()
fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

/** Writes the counter's reset URI to the tag. Returns an error message, or null on success. Blocking. */
fun writeTag(tag: Tag, id: Long): String? = try {
    val msg = NdefMessage(NdefRecord.createUri(resetUri(id)))
    val ndef = Ndef.get(tag)
    val formatable = NdefFormatable.get(tag)
    when {
        ndef != null -> ndef.use {
            it.connect()
            when {
                !it.isWritable -> "Tag is read-only"
                it.maxSize < msg.byteArrayLength -> "Tag is too small"
                else -> { it.writeNdefMessage(msg); null }
            }
        }
        formatable != null -> formatable.use { it.connect(); it.format(msg); null }
        else -> "Tag doesn't support NDEF"
    }
} catch (e: Exception) { // tag moved away mid-write, etc.
    "Write failed, hold the tag still and try again"
}

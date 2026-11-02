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

/** Writes the counter's reset URI to the tag. Returns an error message (string resource), or null on success. Blocking. */
fun writeTag(tag: Tag, id: Long): Int? = try {
    val msg = NdefMessage(NdefRecord.createUri(resetUri(id)))
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

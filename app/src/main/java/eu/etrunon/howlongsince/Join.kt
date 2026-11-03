package eu.etrunon.howlongsince

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/** What a share link carries: where the group lives, its secret, a single-use invite (empty: none, joining needs the recovery password),
 *  and the old server token of links made before device tokens (empty if none). */
data class Join(val url: String, val secret: String, val nonce: String = "", val token: String = "")

/** The link to share: opens the web app for the group (which offers "Open in app"). The key sits in the fragment, so no server ever sees it. */
fun shareLink(j: Join) = "${j.url}/#k=${j.secret}" + (if (j.nonce.isEmpty()) "" else "&n=${j.nonce}") +
    if (j.token.isEmpty()) "" else "&t=${URLEncoder.encode(j.token, "UTF-8")}"

/** Accepts the web link, or `howlongsince://join?h=<server url>#k=...`. Null for anything else. */
fun parseJoin(link: String): Join? {
    val u = runCatching { URI(link.trim()) }.getOrNull() ?: return null
    val p = u.rawFragment?.split("&")?.mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 } }?.associate { (k, v) -> k to URLDecoder.decode(v, "UTF-8") } ?: return null
    val secret = p["k"]?.takeIf { runCatching { Base64.getUrlDecoder().decode(it).size == 32 }.getOrDefault(false) } ?: return null
    val server = if (u.scheme == "howlongsince") u.rawQuery?.split("&")?.firstOrNull { it.startsWith("h=") }?.let { URLDecoder.decode(it.removePrefix("h="), "UTF-8") }
        else link.trim().substringBefore('#')
    if (server == null || !server.matches(Regex("https?://[^/\\s]+.*"))) return null
    return Join(server.trimEnd('/'), secret, p["n"].orEmpty(), p["t"].orEmpty())
}

/** Asks for a link if [initial] is empty, then joins: an invite needs nothing more, a link without one needs the recovery password. */
@Composable
fun JoinDialog(initial: String, groups: List<CounterGroup>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Join?>(null) }
    fun next() {
        val j = parseJoin(text) ?: return run { error = ctx.getString(R.string.bad_link) }
        val keys = keyPrefs(ctx)
        groups.find { keys.getString("k${it.id}", null) == j.secret }?.let { return run { error = ctx.getString(R.string.already_joined, it.name) } }
        found = j; error = null
    }
    LaunchedEffect(Unit) { if (initial.isNotBlank()) next() }
    val f = found
    val needsPassword = f != null && f.nonce.isEmpty() && f.token.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f != null) {
                    Text(stringResource(R.string.join_question, f.url.substringAfter("://")))
                    if (needsPassword) PasswordField(password, stringResource(R.string.recovery_join)) { password = it; error = null }
                } else OutlinedTextField(text, { text = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.link)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = error != null)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (f != null) TextButton(enabled = !busy && (!needsPassword || password.isNotEmpty()), onClick = {
                busy = true
                scope.launch { error = joinGroup(ctx.applicationContext, f, password); busy = false; if (error == null) onDismiss() } // dismissing first would cancel this scope
            }) { Text(stringResource(R.string.join)) }
            else TextButton(enabled = text.isNotBlank() && !busy, onClick = ::next) { Text(stringResource(R.string.next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PasswordField(value: String, label: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))

/** Share for group [g]: admins get a fresh invite, members the key backup. */
@Composable
fun GroupShare(g: CounterGroup, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var link by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(g.id) { link = inviteLink(ctx, g) ?: return@LaunchedEffect onDismiss() }
    link?.let { (l, invite) -> ShareDialog(g.name, l, stringResource(if (invite) R.string.share_warning else R.string.share_backup), onDismiss) }
}

/** The link and a QR code of it. */
@Composable
fun ShareDialog(name: String, link: String, warning: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Qr(link, Modifier.fillMaxWidth())
                Text(warning, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val clip = ClipData.newPlainText("link", link)
                    clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) } // keep it out of clipboard previews
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    Toast.makeText(ctx, R.string.link_copied, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.copy_link)) }
                TextButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null))
                }) { Text(stringResource(R.string.share)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun Qr(text: String, modifier: Modifier) {
    val m = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }
    // Always black on white, whatever the theme: scanners need the contrast.
    Canvas(modifier.aspectRatio(1f).background(Color.White)) {
        val s = size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) drawRect(Color.Black, Offset(x * s, y * s), Size(s + 0.5f, s + 0.5f))
    }
}

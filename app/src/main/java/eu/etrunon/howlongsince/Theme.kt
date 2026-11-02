package eu.etrunon.howlongsince

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/** Wallpaper colors on Android 12+, Material defaults before. */
@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // Status/nav bar icons must follow the app theme, not the system one.
    SideEffect {
        WindowCompat.getInsetsController((ctx as Activity).window, ctx.window.decorView).run {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
    }
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Stable color per tag name (String.hashCode is fixed by the JVM spec), lighter in dark theme. */
@Composable
fun tagColor(tag: String): Color {
    val hue = ((tag.hashCode() % 360) + 360) % 360
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return Color.hsl(hue.toFloat(), 0.55f, if (dark) 0.7f else 0.45f)
}

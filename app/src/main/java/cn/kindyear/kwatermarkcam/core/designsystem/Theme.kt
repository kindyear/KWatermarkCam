package cn.kindyear.kwatermarkcam.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Material 3 appearance; dynamic colors are available from Android 12. */
@Composable
fun WatermarkTheme(mode: String = "system", dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFF8CD2E2), secondary = Color(0xFFEFC16D))
    else lightColorScheme(primary = Color(0xFF176678), secondary = Color(0xFF7E5A17))
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(), content = content)
}

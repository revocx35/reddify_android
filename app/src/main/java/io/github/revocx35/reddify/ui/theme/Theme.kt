package io.github.revocx35.reddify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Same tokens as web/src/styles.css (:root). */
object R {
    val bg = Color(0xFF000000)
    val panel = Color(0xFF121212)
    val elev = Color(0xFF1F1F1F)
    val elev2 = Color(0xFF2A2A2A)
    val elev3 = Color(0xFF333333)
    val text = Color(0xFFFFFFFF)
    val sub = Color(0xFFB3B3B3)
    val muted = Color(0xFF727272)
    val accent = Color(0xFFE8283B)
    val accentHi = Color(0xFFFF4255)
    val accentDim = Color(0xFF8F1825)
    val mini = Color(0xFF4A1219)
    val danger = Color(0xFFF15E6C)
}

private val scheme = darkColorScheme(
    primary = R.accent,
    onPrimary = Color.Black,
    secondary = R.accent,
    background = R.panel,
    onBackground = R.text,
    surface = R.panel,
    onSurface = R.text,
    surfaceVariant = R.elev2,
    onSurfaceVariant = R.sub,
    error = R.danger,
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ReddifyTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)

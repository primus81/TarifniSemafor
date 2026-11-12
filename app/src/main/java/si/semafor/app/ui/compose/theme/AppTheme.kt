package si.semafor.app.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import si.semafor.app.ui.theme.Palette

/**
 * The app is dark only. The colours are the very same values the View-based screens use ([Palette]), so Compose
 * screens and legacy screens look identical while the migration is in progress.
 */
private val AppColorScheme = darkColorScheme(
    primary = Color(0xFF43A047), // green block colour, also the main call-to-action button
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFF42A5F5), // accent colour (pull-to-refresh spinner)
    onSecondary = Color(0xFF111111),
    error = Color(0xFFE53935), // red block colour
    onError = Color(0xFFFFFFFF),
    background = Color(Palette.BG_SCREEN),
    onBackground = Color(Palette.TEXT_PRIMARY),
    surface = Color(Palette.SURFACE),
    onSurface = Color(Palette.TEXT_PRIMARY),
    surfaceVariant = Color(Palette.SURFACE),
    onSurfaceVariant = Color(Palette.TEXT_SECONDARY),
    outline = Color(Palette.BORDER),
    outlineVariant = Color(Palette.DIVIDER)
)

/**
 * The text sizes the app has always used (in sp, so they follow the system font size):
 * 18 bold = screen title, 15 bold = card title, 14 = row text, 13 = body, 12 = small print.
 */
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 13.sp)
)

/** Line height of multi-line body text: the View screens used a 1.15 line spacing multiplier on 13 sp text. */
val BodyLineHeight = 17.5.sp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}

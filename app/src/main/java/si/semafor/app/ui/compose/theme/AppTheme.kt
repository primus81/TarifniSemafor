package si.semafor.app.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The fixed colours of the app (dark only). The values are the ones the app has always used. */
internal object AppColors {
    val BgScreen = Color(0xFF161C28)
    val Surface = Color(0xFF1F2738)
    val Border = Color(0xFF2E394E)
    val Divider = Color(0x33FFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFC8D2DC)
    val TopBar = Color(0xFF0C1018)

    /** Warnings: the yellow of the middle tariff block, as everywhere else in the app. */
    val Warning = Color(0xFFFDD835)

    /** Accent colour (pull-to-refresh spinner). */
    val Accent = Color(0xFF42A5F5)
}

private val AppColorScheme = darkColorScheme(
    primary = Color(0xFF43A047), // green block colour, also the main call-to-action button
    onPrimary = Color(0xFF111111),
    secondary = AppColors.Accent,
    onSecondary = Color(0xFF111111),
    error = Color(0xFFE53935), // red block colour
    onError = Color(0xFFFFFFFF),
    background = AppColors.BgScreen,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border,
    outlineVariant = AppColors.Divider
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

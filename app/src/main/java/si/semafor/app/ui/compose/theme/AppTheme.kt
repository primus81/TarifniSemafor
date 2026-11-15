package si.semafor.app.ui.compose.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import si.semafor.app.AppSettings

/**
 * The neutral colours around the content. The block colours are NOT part of it: they come from the official uro.si
 * table ([si.semafor.app.BlockPalette]) and look the same in both themes. The accent is a deep blue-teal that is
 * clearly different from the light blue of block 5, so a selected button is never mistaken for a block.
 */
@Immutable
data class AppPalette(
    val isDark: Boolean,
    val bgScreen: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val topBar: Color,
    val accent: Color,
    val onAccent: Color,
    /** Warning TEXT: the vivid yellow on dark surfaces, a dark amber on light ones (yellow text is unreadable there). */
    val warningText: Color
)

val DarkPalette = AppPalette(
    isDark = true,
    bgScreen = Color(0xFF12171F),
    surface = Color(0xFF1C232E),
    surfaceRaised = Color(0xFF242C38),
    border = Color(0xFF2F3946),
    divider = Color(0x33FFFFFF),
    textPrimary = Color(0xFFF4F6F8),
    textSecondary = Color(0xFFB9C3CD),
    topBar = Color(0xFF0C1016),
    accent = Color(0xFF6FC3E8),
    onAccent = Color(0xFF06222F),
    warningText = Color(0xFFF9C212)
)

val LightPalette = AppPalette(
    isDark = false,
    bgScreen = Color(0xFFF3F5F8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF7F9FB),
    border = Color(0xFFD8DEE6),
    divider = Color(0x1F000000),
    textPrimary = Color(0xFF1A1F26),
    textSecondary = Color(0xFF4E5A66),
    topBar = Color(0xFFFFFFFF),
    accent = Color(0xFF00658F),
    onAccent = Color(0xFFFFFFFF),
    warningText = Color(0xFF8A5A00)
)

val LocalAppPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Colours of the app. The neutral ones follow the theme; the semantic ones (excess, near the limit) never change, like
 * the block colours.
 */
internal object AppColors {
    val BgScreen: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.bgScreen
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surface
    val Border: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.border
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.divider
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textSecondary
    val TopBar: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.topBar
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accent

    /** Warning text (readable in both themes). */
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.warningText

    /** Above the agreed power: red, a colour no block uses. */
    val Over = Color(0xFFE53935)

    /** Close to the agreed power (from 90 %): amber. */
    val Near = Color(0xFFFFB300)

    /** Delivered energy (A-). */
    val Export = Color(0xFF2E9E4F)

    /** Neutral chart bars (no block, no warning). */
    val NeutralBar = Color(0xFF7D8B99)
}

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
    labelLarge = TextStyle(fontSize = 13.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

/** Line height of multi-line body text: the View screens used a 1.15 line spacing multiplier on 13 sp text. */
val BodyLineHeight = 17.5.sp

/** True if [mode] (AppSettings.THEME_*) means dark right now. */
@Composable
fun isDarkTheme(mode: Int): Boolean = when (mode) {
    AppSettings.THEME_LIGHT -> false
    AppSettings.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

/**
 * The app theme. A change of theme fades the neutral colours over 300 ms instead of switching them abruptly; the block
 * colours stay as they are.
 */
@Composable
fun AppTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val target = if (dark) DarkPalette else LightPalette
    val palette = target.copy(
        bgScreen = animate(target.bgScreen),
        surface = animate(target.surface),
        surfaceRaised = animate(target.surfaceRaised),
        border = animate(target.border),
        divider = animate(target.divider),
        textPrimary = animate(target.textPrimary),
        textSecondary = animate(target.textSecondary),
        topBar = animate(target.topBar),
        accent = animate(target.accent),
        onAccent = animate(target.onAccent),
        warningText = animate(target.warningText)
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            error = AppColors.Over,
            onError = Color.White,
            background = palette.bgScreen,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.surface,
            outline = palette.border,
            outlineVariant = palette.divider,
            secondaryContainer = palette.accent.copy(alpha = 0.22f),
            onSecondaryContainer = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            error = Color(0xFFC62828),
            onError = Color.White,
            background = palette.bgScreen,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.surface,
            outline = palette.border,
            outlineVariant = palette.divider,
            secondaryContainer = palette.accent.copy(alpha = 0.14f),
            onSecondaryContainer = palette.textPrimary
        )
    }
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

@Composable
private fun animate(c: Color): Color {
    val value by animateColorAsState(c, tween(300), label = "theme")
    return value
}

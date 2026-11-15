package si.semafor.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import si.semafor.app.AppLanguage
import si.semafor.app.AppSettings
import java.util.Locale

/** The app's language in the composition (AppSettings.LANGUAGE_*); set by the host Activity. */
val LocalLang = staticCompositionLocalOf { AppSettings.LANGUAGE_SL }

/** Locale for numbers in the app's language ("2,40" in Slovenian, "2.40" in English). */
val currentLocale: Locale
    @Composable @ReadOnlyComposable get() = AppLanguage.localeOf(LocalLang.current)

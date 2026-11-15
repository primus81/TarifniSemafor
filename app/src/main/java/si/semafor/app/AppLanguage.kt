package si.semafor.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The language of the app: Slovenian (default) or English, chosen in Settings (or, on Android 13+, also in the
 * phone's per-app language settings).
 *
 * Activities get the language through AppCompatDelegate.setApplicationLocales. Contexts without an Activity (widget,
 * notifications, workers) do not, so they use [localized], based on the choice mirrored in [AppSettings.language].
 */
object AppLanguage {
    /** Applies the stored choice at process start (Android 12 and older need this on every start). */
    fun init(ctx: Context) {
        val stored = AppSettings.language(ctx)
        val system = AppCompatDelegate.getApplicationLocales()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !system.isEmpty) {
            // Android keeps the per-app language itself; the user may have changed it in the phone's settings.
            AppSettings.setLanguage(ctx, normalize(system[0]?.language))
            return
        }
        val tag = stored ?: AppSettings.LANGUAGE_SL
        if (stored == null) AppSettings.setLanguage(ctx, tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Android 13+: takes over a language the user chose in the phone's per-app language settings. */
    fun syncFromSystem(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val system = AppCompatDelegate.getApplicationLocales()
        if (!system.isEmpty) AppSettings.setLanguage(ctx, normalize(system[0]?.language))
    }

    /** The current language tag ([AppSettings.LANGUAGE_SL] or [AppSettings.LANGUAGE_EN]). */
    fun current(ctx: Context): String = normalize(AppSettings.language(ctx))

    /** Switches the app to [tag]; open Activities are recreated in the new language. */
    fun set(ctx: Context, tag: String) {
        val t = normalize(tag)
        AppSettings.setLanguage(ctx, t)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(t))
    }

    /** Locale for numbers and dates in the current language. */
    fun locale(ctx: Context): Locale = localeOf(current(ctx))

    /** [ctx] with the app's language, for texts built outside an Activity (widget, notifications). */
    fun localized(ctx: Context): Context {
        val locale = locale(ctx)
        val config = Configuration(ctx.resources.configuration)
        if (config.locales[0]?.language == locale.language) return ctx
        config.setLocale(locale)
        return ctx.createConfigurationContext(config)
    }

    internal fun localeOf(tag: String): Locale =
        if (tag == AppSettings.LANGUAGE_EN) Locale.forLanguageTag("en-GB") else Locale.forLanguageTag("sl-SI")

    /** Only the two supported languages; anything else (or nothing) is Slovenian. */
    internal fun normalize(tag: String?): String =
        if (tag?.lowercase()?.startsWith(AppSettings.LANGUAGE_EN) == true) AppSettings.LANGUAGE_EN else AppSettings.LANGUAGE_SL
}

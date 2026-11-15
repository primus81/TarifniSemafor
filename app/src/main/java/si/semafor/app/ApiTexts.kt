package si.semafor.app

import android.content.Context
import android.content.res.Configuration

/**
 * Texts that the uro.si API sends in Slovenian (network load, season, recommendations). In Slovenian they are shown as
 * sent (only "visoka/nizka sezona" becomes "višja/nižja sezona", as everywhere in the app). In English the known texts
 * are translated through string resources: values/strings_api.xml holds the original wording, values-en the
 * translation. A text the app does not know yet is shown as the API sends it, never guessed.
 */
object ApiTexts {
    private val IDS = intArrayOf(
        R.string.api_load_highest, R.string.api_load_high, R.string.api_load_medium, R.string.api_load_lower,
        R.string.api_load_low, R.string.api_season_high_1, R.string.api_season_high_2, R.string.api_season_high,
        R.string.api_season_low, R.string.api_device_heating, R.string.api_device_household, R.string.api_device_ev,
        R.string.api_device_storage, R.string.api_use_normal, R.string.api_use_conditional, R.string.api_use_increase,
        R.string.api_use_decrease, R.string.api_use_limited
    )

    @Volatile
    private var index: Map<String, Int>? = null

    /** [raw] in the language of [ctx]. */
    fun translate(ctx: Context, raw: String): String {
        val slovenian = HomeFormat.seasonLabel(raw.trim())
        if (AppLanguage.current(ctx) != AppSettings.LANGUAGE_EN) return slovenian
        val id = index(ctx)[normalize(slovenian)] ?: return slovenian
        return ctx.getString(id)
    }

    /** Normalised Slovenian text -> resource id, built once from the Slovenian resources. */
    private fun index(ctx: Context): Map<String, Int> = index ?: run {
        val config = Configuration(ctx.resources.configuration).apply { setLocale(AppLanguage.localeOf(AppSettings.LANGUAGE_SL)) }
        val sl = ctx.createConfigurationContext(config)
        IDS.associateBy { normalize(sl.getString(it)) }.also { index = it }
    }

    /** Icon (Material Icons, Apache 2.0) for the device group of a recommendation, or null if it is not known. */
    fun deviceIcon(device: String): Int? {
        val d = normalize(device)
        return when {
            "vozil" in d -> R.drawable.ic_device_ev
            "gospodinjstv" in d -> R.drawable.ic_device_household
            "ogrevanj" in d || "hlajenj" in d -> R.drawable.ic_device_heating
            "hranilnik" in d -> R.drawable.ic_device_storage
            else -> null
        }
    }

    /** Lower case, single spaces, no line breaks (the API sometimes ends a name with "\r\n"). */
    internal fun normalize(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")
}

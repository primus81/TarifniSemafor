package si.semafor.app.mojelektro

import si.semafor.app.R
import si.semafor.app.UiText
import java.util.Locale

/**
 * Short explanations of a 15-minute interval for the combined chart. Pure Kotlin (unit tested); the texts are string
 * resources ([UiText]), the numbers are formatted for [Locale] (Slovenian by default).
 *
 * Only facts the app already has are used: the measured power, the agreed power of the block (Moj Elektro), the
 * block of the hour (official URO schedule) and the app's block order (1 = most expensive, 5 = cheapest). No tariff
 * rule is invented here.
 */
object UsageInsights {
    val SL: Locale = Locale.forLanguageTag("sl-SI")

    fun num(v: Double, decimals: Int, locale: Locale = SL): String = String.format(locale, "%.${decimals}f", v)

    /** "87 %" (whole percent). */
    fun percent(ratio: Double, locale: Locale = SL): String = num(ratio * 100, 0, locale) + " %"

    /** How the interval relates to the agreed power of its block. */
    fun powerText(point: IntervalPoint, locale: Locale = SL): UiText {
        val ratio = UsageAnalysis.utilization(point) ?: return UiText.res(R.string.usage_power_unknown)
        val share = percent(ratio, locale)
        return when (point.level) {
            PowerLevel.OVER -> UiText.res(R.string.usage_power_over, share)
            PowerLevel.NEAR -> UiText.res(R.string.usage_power_near, share)
            else -> UiText.res(R.string.usage_power_safe, share)
        }
    }

    /** Where the interval sits in the block order. */
    fun blockText(point: IntervalPoint): UiText = when (val b = point.blockId) {
        null -> UiText.res(R.string.usage_block_unknown)
        1, 2 -> UiText.res(R.string.usage_block_dear, b)
        3 -> UiText.res(R.string.usage_block_middle)
        else -> UiText.res(R.string.usage_block_cheap, b)
    }

    /** One line for a scrubbed interval (without the time, which the caller shows itself). */
    fun summary(point: IntervalPoint, locale: Locale = SL): UiText = UiText.res(
        R.string.usage_interval_summary,
        num(point.kw, 2, locale),
        num(point.kwh, 3, locale),
        point.blockId?.let { UiText.res(R.string.usage_interval_block, it) } ?: UiText.Raw(""),
        point.agreedKw?.let { UiText.res(R.string.usage_interval_agreed, num(it, 2, locale)) } ?: UiText.Raw("")
    )

    /** Summary of a day's peak for the daily peaks card. */
    fun peakVerdict(peak: DailyPeak, locale: Locale = SL): UiText {
        val ratio = peak.utilization ?: return UiText.res(R.string.usage_verdict_unknown)
        val share = percent(ratio, locale)
        return when {
            peak.overCount > 0 -> UiText.plural(R.plurals.usage_verdict_over, peak.overCount, peak.overCount, share)
            peak.nearCount > 0 -> UiText.plural(R.plurals.usage_verdict_near, peak.nearCount, peak.nearCount, share)
            else -> UiText.res(R.string.usage_verdict_safe, share)
        }
    }
}

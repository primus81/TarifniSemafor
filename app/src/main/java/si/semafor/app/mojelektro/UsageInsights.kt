package si.semafor.app.mojelektro

import java.util.Locale

/**
 * Short explanations of a 15-minute interval for the combined chart. Pure Kotlin (unit tested).
 *
 * Only facts the app already has are used: the measured power, the agreed power of the block (Moj Elektro), the
 * block of the hour (official URO schedule) and the app's block order (1 = most expensive, 5 = cheapest). No tariff
 * rule is invented here.
 */
object UsageInsights {
    private val SL = Locale.forLanguageTag("sl-SI")

    private fun num(v: Double, decimals: Int) = String.format(SL, "%.${decimals}f", v)

    /** "87 %" (whole percent, Slovenian formatting). */
    fun percent(ratio: Double): String = num(ratio * 100, 0) + " %"

    /** How the interval relates to the agreed power of its block. */
    fun powerText(point: IntervalPoint): String {
        val ratio = UsageAnalysis.utilization(point)
            ?: return "Dogovorjena moč za ta interval ni znana."
        val share = percent(ratio)
        return when (point.level) {
            PowerLevel.OVER -> "Nad dogovorjeno močjo bloka: $share dogovorjene moči."
            PowerLevel.NEAR -> "Blizu dogovorjene moči bloka: $share."
            else -> "Varno pod dogovorjeno močjo bloka: $share."
        }
    }

    /** Where the interval sits in the block order. */
    fun blockText(point: IntervalPoint): String = when (point.blockId) {
        null -> "Časovni blok tega intervala ni znan."
        1, 2 -> "${point.blockId}. blok je med dražjimi bloki (1 = najdražji). Porabo, ki ni nujna, lahko premakneš v blok z višjo številko."
        3 -> "3. blok je srednji blok."
        else -> "${point.blockId}. blok je med cenejšimi bloki (5 = najcenejši)."
    }

    /** One spoken / shown line for a scrubbed interval (without the time, which the caller shows itself). */
    fun summary(point: IntervalPoint): String = buildString {
        append(num(point.kw, 2)).append(" kW")
        append(" (").append(num(point.kwh, 3)).append(" kWh v 15 minutah)")
        point.blockId?.let { append(", ").append(it).append(". blok") }
        point.agreedKw?.let { append(", dogovorjena ").append(num(it, 2)).append(" kW") }
        append('.')
    }

    /** Summary of a day's peak for the daily peaks card. */
    fun peakVerdict(peak: DailyPeak): String {
        val ratio = peak.utilization ?: return "Dogovorjena moč ni znana."
        return when {
            peak.overCount > 0 -> "Presežena v ${peak.overCount} ${quarters(peak.overCount)} (najvišja ${percent(ratio)})."
            peak.nearCount > 0 -> "Nad 90 % v ${peak.nearCount} ${quarters(peak.nearCount)} (najvišja ${percent(ratio)})."
            else -> "Pod 90 % ves dan (najvišja ${percent(ratio)})."
        }
    }

    /** Slovenian dual and plural of "četrtura" in the locative ("v 1 četrturi", "v 2 četrturah"). */
    fun quarters(n: Int): String = if (n == 1) "četrturi" else "četrturah"
}

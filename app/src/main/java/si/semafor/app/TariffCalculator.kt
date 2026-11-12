package si.semafor.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/** Tariff season. Higher season = November to February (as everywhere in the app), lower season = March to October. */
enum class Season {
    HIGH, LOW;

    companion object {
        fun of(date: LocalDate): Season = if (date.monthValue >= 11 || date.monthValue <= 2) HIGH else LOW
    }
}

/**
 * Result of one estimate, in EUR rounded to cents. Prices in the official list are without VAT; [vat] and
 * [totalGross] add the VAT rate on top. Per month: the power rate is EUR per kW per month, energy is per kWh.
 */
data class NetworkCost(
    val list: PriceList,
    val powerNet: BigDecimal,
    val energyNet: BigDecimal,
    val totalNet: BigDecimal,
    val vat: BigDecimal,
    val totalGross: BigDecimal,
    val totalKwh: BigDecimal
)

/** Outcome of an estimate for what the user typed: the screen only has to show it. */
sealed interface Estimate {
    /** At least one typed value is not a plain number. */
    data object InvalidInput : Estimate

    /** There is no official price list for the chosen season in the app, so nothing is estimated. */
    data object NoPriceList : Estimate

    /** [notValidToday] = the list is the newest of its season but does not apply on today's date. */
    data class Ready(val cost: NetworkCost, val notValidToday: Boolean) : Estimate
}

/**
 * Local network-charge estimate. Pure Kotlin (no Android), so it is unit tested.
 *
 * The rates are NOT stored here: they come from [PriceLists.ALL], the single place that holds the official
 * SODO price lists (user group 0). To add a list (for example the lower season), append a PriceList there with
 * the official values; this calculator picks it up by its validity dates. Nothing is estimated for a season
 * without an official list: no rate is ever invented.
 */
object TariffCalculator {
    const val BLOCKS = 5

    /** Slovenian VAT (DDV) applied on top of the network charge. */
    val VAT_RATE: BigDecimal = BigDecimal("0.22")

    /** Upper bound for a typed value; anything bigger is treated as a typing mistake. */
    private const val MAX_INPUT = 1_000_000.0

    private val LOCALE: Locale = Locale.forLanguageTag("sl-SI")

    private val NUMBER = Regex("^(\\d+\\.?\\d*|\\.\\d+)$")

    /**
     * The list to use for [season]: the one valid on [today] if it belongs to that season, otherwise the newest list
     * of that season (its validity dates are shown to the user), otherwise null.
     * A list belongs to the season of its validFrom date.
     */
    fun listFor(season: Season, today: LocalDate, lists: List<PriceList> = PriceLists.ALL): PriceList? {
        val ofSeason = lists.filter { Season.of(it.validFrom) == season }
        return ofSeason
            .filter { !today.isBefore(it.validFrom) && !today.isAfter(it.validTo) }
            .maxByOrNull { it.validFrom }
            ?: ofSeason.maxByOrNull { it.validFrom }
    }

    /**
     * Monthly estimate. [powerKw] = agreed billing power per block 1..5 (kW), [energyKwh] = expected consumption per
     * block 1..5 (kWh per month). Both lists must have [BLOCKS] entries.
     */
    fun calculate(list: PriceList, powerKw: List<Double>, energyKwh: List<Double>): NetworkCost {
        require(powerKw.size == BLOCKS && energyKwh.size == BLOCKS) { "Expected $BLOCKS values per input" }
        var power = BigDecimal.ZERO
        var energy = BigDecimal.ZERO
        var kwh = BigDecimal.ZERO
        for (block in 1..BLOCKS) {
            val row = requireNotNull(list.rows.firstOrNull { it.block == block }) { "Missing block $block" }
            power += BigDecimal.valueOf(powerKw[block - 1]) * BigDecimal.valueOf(row.powerEurPerKw)
            energy += BigDecimal.valueOf(energyKwh[block - 1]) * BigDecimal.valueOf(row.energyEurPerKwh)
            kwh += BigDecimal.valueOf(energyKwh[block - 1])
        }
        val powerNet = power.cents()
        val energyNet = energy.cents()
        val totalNet = powerNet + energyNet
        val vat = (totalNet * VAT_RATE).cents()
        return NetworkCost(list, powerNet, energyNet, totalNet, vat, totalNet + vat, kwh)
    }

    /**
     * The whole estimate from the texts the user typed: [powerAllText] counts for every block when [samePower] is on,
     * otherwise the five [powerBlockTexts] do. Invalid input is reported before a missing price list.
     */
    fun estimate(
        season: Season,
        today: LocalDate,
        samePower: Boolean,
        powerAllText: String,
        powerBlockTexts: List<String>,
        energyTexts: List<String>,
        lists: List<PriceList> = PriceLists.ALL
    ): Estimate {
        val energy = energyTexts.map { parseAmount(it) }
        val power: List<Double?> =
            if (samePower) List(BLOCKS) { parseAmount(powerAllText) } else powerBlockTexts.map { parseAmount(it) }
        if (energy.size != BLOCKS || power.size != BLOCKS || energy.any { it == null } || power.any { it == null }) {
            return Estimate.InvalidInput
        }
        val list = listFor(season, today, lists) ?: return Estimate.NoPriceList
        val cost = calculate(list, power.map { it!! }, energy.map { it!! })
        return Estimate.Ready(cost, today.isBefore(list.validFrom) || today.isAfter(list.validTo))
    }

    /**
     * Parses a typed amount (comma or dot as the decimal separator). Empty text is 0.
     * Returns null for anything that is not a plain non-negative number.
     */
    fun parseAmount(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return 0.0
        if (!NUMBER.matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite() && v <= MAX_INPUT) v else null
    }

    /** "1.234,56 €" in Slovenian formatting. */
    fun formatEur(amount: BigDecimal): String =
        String.format(LOCALE, "%,.2f", amount) + " €"

    /** "1.234,5 kWh" style number without a unit, at most two decimals. */
    fun formatNumber(value: BigDecimal): String =
        String.format(LOCALE, "%,.2f", value).trimEnd('0').trimEnd(',')

    private fun BigDecimal.cents(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
}

private val NOTE_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d. M. yyyy")

/** The explanation shown under the result of an [Estimate] (Slovenian, second person singular). */
internal fun estimateNote(estimate: Estimate): String = when (estimate) {
    Estimate.InvalidInput -> "Preveri vnos: uporabi samo številke, npr. 5,5."

    Estimate.NoPriceList ->
        "Za izbrano sezono v aplikaciji še ni uradnega cenika SODO, zato izračun ni mogoč."

    is Estimate.Ready -> {
        val list = estimate.cost.list
        val notes = StringBuilder()
        notes.append("Cenik SODO (skupina 0): velja od ${list.validFrom.format(NOTE_DATE)} ")
        notes.append("do ${list.validTo.format(NOTE_DATE)}.")
        if (estimate.notValidToday) notes.append(" Danes ta cenik ne velja, zato je izračun samo okviren.")
        list.note?.let { notes.append('\n').append(it) }
        notes.append("\nSkupna poraba: ${TariffCalculator.formatNumber(estimate.cost.totalKwh)} kWh. ")
        notes.append("Izračun zajema samo omrežnino (moč in energijo) z DDV 22 %, ")
        notes.append("brez trošarin, taks in drugih dajatev. Je informativen in ne nadomešča računa.")
        notes.toString()
    }
}

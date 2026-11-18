package si.semafor.app

import java.time.LocalDate
import java.util.Locale

data class PriceRow(val block: Int, val powerEurPerKw: Double, val energyEurPerKwh: Double)
data class PriceList(
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val rows: List<PriceRow>,
    val note: String?,
    val sourceUrl: String,
    /** English wording of [note] (a translation, no additional tariff information). */
    val noteEn: String? = null
) {
    /** [note] in the language [lang] (AppSettings.LANGUAGE_*). */
    fun noteIn(lang: String): String? = if (lang == AppSettings.LANGUAGE_EN) noteEn ?: note else note
}

object PriceLists {
    // User group 0 only (households and other low-voltage customers).
    // TO ADD A NEW PRICE LIST: append a new PriceList to BUILT_IN with the correct validFrom/validTo. Only the list valid today is ever shown.
    // Lists from the downloaded price data (si.semafor.app.pricing.PriceRepository) are added on top, see ALL.
    private val BUILT_IN = listOf(
        PriceList(
            validFrom = LocalDate.of(2026, 11, 1),
            validTo = LocalDate.of(2026, 12, 31),
            rows = listOf(
                PriceRow(1, 2.67611, 0.02217),
                PriceRow(2, 1.09230, 0.01998),
                PriceRow(3, 0.28902, 0.01717),
                PriceRow(4, 0.02436, 0.01805),
                PriceRow(5, 0.00245, 0.01299)
            ),
            note = "V višji sezoni 2026/27 se za blok 1 obračuna 70 % ciljne vrednosti postavke za moč (postopno uvajanje).",
            sourceUrl = "https://www.sodo.si/ceniki/2026-11-01/cenik-omreznin",
            noteEn = "In the 2026/27 higher season, 70 % of the target power rate is charged for block 1 (gradual introduction)."
        )
    )

    /** Network tariffs from the downloaded, validated price data ("ceniki.json"); empty until it is loaded. */
    @Volatile
    private var downloaded: List<PriceList> = emptyList()

    /**
     * All known lists: the built-in ones plus the downloaded ones. A downloaded list with the same validFrom as a
     * built-in one replaces it (it is the newer copy of the same official list).
     */
    val ALL: List<PriceList> get() = merge(BUILT_IN, downloaded)

    internal fun setDownloaded(lists: List<PriceList>) {
        downloaded = lists
    }

    internal fun merge(builtIn: List<PriceList>, extra: List<PriceList>): List<PriceList> {
        val starts = extra.map { it.validFrom }.toSet()
        return (builtIn.filter { it.validFrom !in starts } + extra).sortedBy { it.validFrom }
    }

    private val SL: Locale = Locale.forLanguageTag("sl-SI")

    const val SOURCE_ALL = "https://www.sodo.si/ceniki"

    /** The list with validFrom <= d <= validTo; if several, the latest validFrom; else null. */
    fun forDate(d: LocalDate): PriceList? =
        ALL.filter { !d.isBefore(it.validFrom) && !d.isAfter(it.validTo) }
            .maxByOrNull { it.validFrom }

    fun current(): PriceList? = forDate(SloTime.today())

    /** Null if list is null or block missing. */
    fun energyPrice(list: PriceList?, block: Int): Double? =
        list?.rows?.firstOrNull { it.block == block }?.energyEurPerKwh

    fun formatKwh(v: Double, locale: Locale = SL): String = String.format(locale, "%.5f", v) + "€/kWh"

    fun formatNumber(v: Double, locale: Locale = SL): String = String.format(locale, "%.5f", v)
}

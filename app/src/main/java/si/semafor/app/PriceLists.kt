package si.semafor.app

import java.time.LocalDate
import java.util.Locale

data class PriceRow(val block: Int, val powerEurPerKw: Double, val energyEurPerKwh: Double)
data class PriceList(
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val rows: List<PriceRow>,
    val note: String?,
    val sourceUrl: String
)

object PriceLists {
    // User group 0 only (households and other low-voltage customers).
    // TO ADD A NEW PRICE LIST: append a new PriceList to ALL with the correct validFrom/validTo. Only the list valid today is ever shown.
    val ALL = listOf(
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
            sourceUrl = "https://www.sodo.si/ceniki/2026-11-01/cenik-omreznin"
        )
    )

    const val SOURCE_ALL = "https://www.sodo.si/ceniki"

    /** The list with validFrom <= d <= validTo; if several, the latest validFrom; else null. */
    fun forDate(d: LocalDate): PriceList? =
        ALL.filter { !d.isBefore(it.validFrom) && !d.isAfter(it.validTo) }
            .maxByOrNull { it.validFrom }

    fun current(): PriceList? = forDate(SloTime.today())

    /** Null if list is null or block missing. */
    fun energyPrice(list: PriceList?, block: Int): Double? =
        list?.rows?.firstOrNull { it.block == block }?.energyEurPerKwh

    fun formatKwh(v: Double): String = String.format(Locale("sl", "SI"), "%.5f", v) + "€/kWh"

    fun formatNumber(v: Double): String = String.format(Locale("sl", "SI"), "%.5f", v)
}

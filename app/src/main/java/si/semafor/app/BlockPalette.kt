package si.semafor.app

import java.time.LocalDate

/**
 * Colours of the time blocks 1-5, exactly as the official uro.si tables of each year show them (read from the
 * published images). The same colours are used everywhere (cards, charts, widget, notifications) and in the light and
 * the dark theme alike. Pure Kotlin (unit tested).
 *
 * A new year: add a [Table] with the colours of that year's official table. Until then the latest known table stays
 * in use; at the turn of the year the app switches to the new table on its own.
 */
object BlockPalette {
    /** Colours (ARGB) of blocks 1..5 valid from 1 January of [year]. */
    class Table(val year: Int, val source: String, val colors: IntArray) {
        init {
            require(colors.size == 5) { "Five block colours expected" }
        }
    }

    val TABLES: List<Table> = listOf(
        Table(
            2026,
            "https://www.uro.si/documents/d/guest/tabela-cb-2026",
            intArrayOf(0xFFF1690F.toInt(), 0xFFF9C212.toInt(), 0xFF8F8F8F.toInt(), 0xFFA1D09E.toInt(), 0xFF54C9E3.toInt())
        ),
        Table(
            2027,
            "https://www.uro.si/documents/d/guest/tabela-cb-2027",
            intArrayOf(0xFFF1690F.toInt(), 0xFFF9C212.toInt(), 0xFF8F8F8F.toInt(), 0xFFA1D09E.toInt(), 0xFF54C9E3.toInt())
        )
    ).sortedBy { it.year }

    /** "No data / unknown block": a light neutral that cannot be mistaken for the grey of block 3. */
    const val UNKNOWN: Int = 0xFFC9D2DC.toInt()

    /** Text drawn on a block colour (dark; the best contrast on every official block colour). */
    const val ON_BLOCK: Int = 0xFF1A1A1A.toInt()

    /** The table valid in [year]: the latest one that has started, or the earliest one before the first year. */
    fun tableFor(year: Int): Table = TABLES.lastOrNull { it.year <= year } ?: TABLES.first()

    /** Colour of block [id] (1..5) on [date]; anything else is [UNKNOWN]. */
    fun color(id: Int, date: LocalDate = SloTime.today()): Int =
        if (id in 1..5) tableFor(date.year).colors[id - 1] else UNKNOWN
}

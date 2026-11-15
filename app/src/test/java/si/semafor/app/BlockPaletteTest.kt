package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/** Colours of the official uro.si tables (tabela-cb-2026, tabela-cb-2027). */
class BlockPaletteTest {
    private val official = listOf(0xFFF1690F, 0xFFF9C212, 0xFF8F8F8F, 0xFFA1D09E, 0xFF54C9E3).map { it.toInt() }

    @Test
    fun officialColoursOf2026And2027() {
        for (year in listOf(2026, 2027)) {
            assertEquals(official, (1..5).map { BlockPalette.color(it, LocalDate.of(year, 6, 1)) })
        }
    }

    @Test
    fun theTableChangesAtTheTurnOfTheYear() {
        assertEquals(2026, BlockPalette.tableFor(2026).year)
        assertEquals(2027, BlockPalette.tableFor(2027).year)
        // A later year keeps the latest known table; an earlier one uses the first.
        assertEquals(2027, BlockPalette.tableFor(2031).year)
        assertEquals(2026, BlockPalette.tableFor(2020).year)
    }

    @Test
    fun unknownIsNotABlockColour() {
        for (id in listOf(0, 6, -1)) assertEquals(BlockPalette.UNKNOWN, BlockPalette.color(id))
        for (c in official) assertNotEquals(BlockPalette.UNKNOWN, c)
    }

    @Test
    fun darkTextIsReadableOnEveryBlock() {
        // WCAG contrast of #1A1A1A on each official colour is at least 4.5:1 (AA for normal text).
        for (c in official) {
            val ratio = contrast(BlockPalette.ON_BLOCK, c)
            assert(ratio >= 4.5) { "Contrast %.2f on #%06X".format(ratio, c and 0xFFFFFF) }
        }
    }

    private fun luminance(c: Int): Double {
        fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c shr 16 and 0xFF) + 0.7152 * ch(c shr 8 and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}

package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class EstimateTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val noBlocks = List(5) { "" }
    private fun same(text: String) = List(5) { text }

    @Test
    fun equalPowerAndConsumptionGivesTheKnownTotal() {
        val e = TariffCalculator.estimate(Season.HIGH, today, true, "5", noBlocks, same("100"))
        e as Estimate.Ready
        assertEquals(BigDecimal("29.46"), e.cost.totalNet)
        assertEquals(BigDecimal("35.94"), e.cost.totalGross)
        assertFalse(e.notValidToday)
    }

    @Test
    fun perBlockPowerIsUsedWhenTheSwitchIsOffAndEmptyMeansZero() {
        val e = TariffCalculator.estimate(Season.HIGH, today, false, "ignored", listOf("3", "", "", "", ""), same(""))
        e as Estimate.Ready
        assertEquals(BigDecimal("8.03"), e.cost.powerNet)
        assertEquals(BigDecimal("9.80"), e.cost.totalGross)
    }

    @Test
    fun theSingleValueCountsForAllBlocksAndTheBlockFieldsAreIgnored() {
        // Block fields hold nonsense, but with "same power" only the single field matters.
        val e = TariffCalculator.estimate(Season.HIGH, today, true, "5", same("abc"), same("100"))
        assertTrue(e is Estimate.Ready)
    }

    @Test
    fun invalidTextIsReportedBeforeAMissingPriceList() {
        assertEquals(
            Estimate.InvalidInput,
            TariffCalculator.estimate(Season.LOW, today, true, "5", noBlocks, listOf("1", "2", "abc", "4", "5"))
        )
        assertEquals(
            Estimate.InvalidInput,
            TariffCalculator.estimate(Season.HIGH, today, true, "-5", noBlocks, same("1"))
        )
        assertEquals(
            Estimate.InvalidInput,
            TariffCalculator.estimate(Season.HIGH, today, false, "", listOf("1", "x", "", "", ""), same("1"))
        )
    }

    @Test
    fun noOfficialListMeansNothingIsEstimated() {
        assertEquals(Estimate.NoPriceList, TariffCalculator.estimate(Season.LOW, today, true, "5", noBlocks, same("100")))
    }

    @Test
    fun anExpiredListIsStillUsedButMarked() {
        val e = TariffCalculator.estimate(Season.HIGH, LocalDate.of(2027, 3, 15), true, "5", noBlocks, same("100"))
        e as Estimate.Ready
        assertTrue(e.notValidToday)
    }

    @Test
    fun noteExplainsEveryOutcome() {
        assertEquals("Preveri vnos: uporabi samo številke, npr. 5,5.", estimateNote(Estimate.InvalidInput))
        assertEquals(
            "Za izbrano sezono v aplikaciji še ni uradnega cenika SODO, zato izračun ni mogoč.",
            estimateNote(Estimate.NoPriceList)
        )
        val ready = TariffCalculator.estimate(Season.HIGH, today, true, "5", noBlocks, same("100"))
        val note = estimateNote(ready)
        assertTrue(note, note.contains("velja od 1. 11. 2026 do 31. 12. 2026."))
        assertTrue(note, note.contains("70 %")) // the note of the official list is passed on
        assertTrue(note, note.contains("Skupna poraba: 500 kWh."))
        assertTrue(note, note.contains("z DDV 22 %"))
        assertFalse(note, note.contains("Danes ta cenik ne velja"))
        val expired = TariffCalculator.estimate(Season.HIGH, LocalDate.of(2027, 3, 15), true, "5", noBlocks, same("100"))
        assertTrue(estimateNote(expired).contains("Danes ta cenik ne velja"))
    }
}

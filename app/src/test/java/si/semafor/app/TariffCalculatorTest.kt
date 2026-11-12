package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TariffCalculatorTest {

    // Same rows as the official list valid 1 Nov - 31 Dec 2026 (see PriceLists).
    private val list = PriceLists.ALL.first()

    private fun same(v: Double) = List(TariffCalculator.BLOCKS) { v }

    @Test
    fun equalPowerAndConsumptionInAllBlocks() {
        // power: 5 kW * (2.67611 + 1.09230 + 0.28902 + 0.02436 + 0.00245) = 20.4212
        // energy: 100 kWh * (0.02217 + 0.01998 + 0.01717 + 0.01805 + 0.01299) = 9.036
        val r = TariffCalculator.calculate(list, same(5.0), same(100.0))
        assertEquals(BigDecimal("20.42"), r.powerNet)
        assertEquals(BigDecimal("9.04"), r.energyNet)
        assertEquals(BigDecimal("29.46"), r.totalNet)
        assertEquals(BigDecimal("6.48"), r.vat) // 22 % of 29.46 = 6.4812
        assertEquals(BigDecimal("35.94"), r.totalGross)
        assertEquals(0, BigDecimal("500").compareTo(r.totalKwh))
    }

    @Test
    fun powerInOneBlockOnly() {
        val r = TariffCalculator.calculate(list, listOf(3.0, 0.0, 0.0, 0.0, 0.0), same(0.0))
        assertEquals(BigDecimal("8.03"), r.powerNet) // 3 * 2.67611 = 8.02833
        assertEquals(BigDecimal("0.00"), r.energyNet)
        assertEquals(BigDecimal("1.77"), r.vat) // 22 % of 8.03 = 1.7666
        assertEquals(BigDecimal("9.80"), r.totalGross)
    }

    @Test
    fun emptyInputIsZero() {
        val r = TariffCalculator.calculate(list, same(0.0), same(0.0))
        assertEquals(BigDecimal("0.00"), r.totalNet)
        assertEquals(BigDecimal("0.00"), r.totalGross)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongNumberOfValuesIsRejected() {
        TariffCalculator.calculate(list, listOf(1.0, 2.0), same(0.0))
    }

    @Test
    fun seasonByMonth() {
        for (m in listOf(11, 12, 1, 2)) assertEquals(Season.HIGH, Season.of(LocalDate.of(2026, m, 15)))
        for (m in 3..10) assertEquals(Season.LOW, Season.of(LocalDate.of(2026, m, 15)))
    }

    @Test
    fun listIsChosenBySeasonAndNeverInvented() {
        val today = LocalDate.of(2026, 11, 11)
        assertSame(list, TariffCalculator.listFor(Season.HIGH, today))
        // No official list for the lower season in the app: nothing to calculate with.
        assertNull(TariffCalculator.listFor(Season.LOW, today))
        // Outside its validity the newest list of that season is still offered (the screen shows its dates).
        assertNotNull(TariffCalculator.listFor(Season.HIGH, LocalDate.of(2027, 3, 15)))
    }

    @Test
    fun validListWinsOverNewerOneOfTheSameSeason() {
        val old = PriceList(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), list.rows, null, "a")
        val newer = PriceList(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 28), list.rows, null, "b")
        assertSame(old, TariffCalculator.listFor(Season.HIGH, LocalDate.of(2026, 12, 1), listOf(old, newer)))
        assertSame(newer, TariffCalculator.listFor(Season.HIGH, LocalDate.of(2027, 1, 10), listOf(old, newer)))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(0.0, TariffCalculator.parseAmount("")!!, 0.0)
        assertEquals(0.0, TariffCalculator.parseAmount("   ")!!, 0.0)
        assertEquals(5.5, TariffCalculator.parseAmount("5,5")!!, 0.0)
        assertEquals(5.5, TariffCalculator.parseAmount("5.5")!!, 0.0)
        assertEquals(12.0, TariffCalculator.parseAmount(" 12 ")!!, 0.0)
        assertEquals(0.5, TariffCalculator.parseAmount(".5")!!, 0.0)
        assertEquals(5.0, TariffCalculator.parseAmount("5.")!!, 0.0)
    }

    @Test
    fun rejectsEverythingThatIsNotAPlainNumber() {
        for (bad in listOf("abc", "1e5", "-3", "1.2.3", ".", ",", "NaN", "Infinity", "5 kW", "2000000")) {
            assertNull("'$bad' must be rejected", TariffCalculator.parseAmount(bad))
        }
    }

    @Test
    fun formatsEuroWithComma() {
        assertEquals("35,94 €", TariffCalculator.formatEur(BigDecimal("35.94")))
        assertEquals("0,00 €", TariffCalculator.formatEur(BigDecimal("0.00")))
        assertEquals("500", TariffCalculator.formatNumber(BigDecimal("500")))
        assertEquals("20,5", TariffCalculator.formatNumber(BigDecimal("20.50")))
    }
}

package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PriceListsTest {

    @Test
    fun priceListValidity_followsSlovenianDates() {
        // Validity boundaries of the current list (1. 11. 2026 - 31. 12. 2026).
        assertNull(PriceLists.forDate(LocalDate.of(2026, 10, 31)))
        assertNotNull(PriceLists.forDate(LocalDate.of(2026, 11, 1)))
        assertNotNull(PriceLists.forDate(LocalDate.of(2026, 12, 31)))
        assertNull(PriceLists.forDate(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun priceListStructure_isComplete() {
        val list = PriceLists.forDate(LocalDate.of(2026, 11, 15))!!
        assertEquals(listOf(1, 2, 3, 4, 5), list.rows.map { it.block })
        assertTrue(list.rows.all { it.powerEurPerKw > 0.0 && it.energyEurPerKwh > 0.0 })
        assertTrue(list.sourceUrl.startsWith("https://"))
        assertTrue(PriceLists.SOURCE_ALL.startsWith("https://"))
    }

    @Test
    fun energyPrice_isNullWhenUnknown() {
        val list = PriceLists.forDate(LocalDate.of(2026, 11, 15))
        assertNull(PriceLists.energyPrice(null, 3))
        assertNull(PriceLists.energyPrice(list, 0))
        assertNull(PriceLists.energyPrice(list, 6))
        assertNotNull(PriceLists.energyPrice(list, 3))
    }

    @Test
    fun numbers_useSlovenianFormat() {
        // Example from the specification: decimal comma, no space before the unit.
        assertEquals("0,01717€/kWh", PriceLists.formatKwh(0.01717))
        assertEquals("2,67611", PriceLists.formatNumber(2.67611))
    }
}

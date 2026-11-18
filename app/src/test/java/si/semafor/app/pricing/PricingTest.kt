package si.semafor.app.pricing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.semafor.app.PriceList
import si.semafor.app.PriceLists
import si.semafor.app.PriceRow
import si.semafor.app.SloTime
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class PricingTest {
    private val fallbackText = File("src/main/assets/ceniki_fallback.json").readText()
    private val data = PriceDataParser.parse(fallbackText)!!
    private val times = data.tariffTimes

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        ZonedDateTime.of(date, LocalTime.of(h, m), SloTime.ZONE).toInstant().toEpochMilli()

    private val network = PriceList(
        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
        listOf(PriceRow(1, 2.0, 0.03), PriceRow(2, 1.0, 0.02), PriceRow(3, 0.3, 0.017), PriceRow(4, 0.02, 0.018), PriceRow(5, 0.002, 0.013)),
        null, "https://example.si"
    )

    private fun custom(vt: Double, mt: Double, withVat: Boolean = false) =
        PricingSetup(MeterType.DUAL, EnergySource.Custom(EnergyPrices(null, vt, mt), withVat, null))

    // ---- Holidays and tariffs ----

    @Test
    fun easterSunday_knownYears() {
        assertEquals(LocalDate.of(2024, 3, 31), SloHolidays.easterSunday(2024))
        assertEquals(LocalDate.of(2026, 4, 5), SloHolidays.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), SloHolidays.easterSunday(2027))
    }

    @Test
    fun workFreeDays_followTheLaw() {
        assertTrue(SloHolidays.isWorkFree(LocalDate.of(2026, 4, 6))) // Easter Monday
        assertTrue(SloHolidays.isWorkFree(LocalDate.of(2027, 1, 2)))
        assertTrue(SloHolidays.isWorkFree(LocalDate.of(2026, 12, 26)))
        assertFalse(SloHolidays.isWorkFree(LocalDate.of(2026, 6, 8))) // Primož Trubar day: holiday, not work-free
        assertFalse(SloHolidays.isWorkFree(LocalDate.of(2026, 8, 7))) // Triglav day (ZPDPD-I): not work-free
        assertEquals(15, SloHolidays.workFreeDays(2026).size)
    }

    @Test
    fun tariff_vtOnWorkingDaysMtOtherwise() {
        val wed = LocalDate.of(2026, 11, 18)
        assertEquals(EnergyTariff.MT, EnergyTariffs.tariffAt(at(wed, 5, 59), MeterType.DUAL, times))
        assertEquals(EnergyTariff.VT, EnergyTariffs.tariffAt(at(wed, 6), MeterType.DUAL, times))
        assertEquals(EnergyTariff.VT, EnergyTariffs.tariffAt(at(wed, 21, 59), MeterType.DUAL, times))
        assertEquals(EnergyTariff.MT, EnergyTariffs.tariffAt(at(wed, 22), MeterType.DUAL, times))
        assertEquals(EnergyTariff.MT, EnergyTariffs.tariffAt(at(LocalDate.of(2026, 11, 21), 12), MeterType.DUAL, times)) // Saturday
        assertEquals(EnergyTariff.MT, EnergyTariffs.tariffAt(at(LocalDate.of(2026, 12, 25), 12), MeterType.DUAL, times)) // Friday, Christmas
        assertEquals(EnergyTariff.ET, EnergyTariffs.tariffAt(at(wed, 12), MeterType.SINGLE, times))
    }

    // ---- Parser ----

    @Test
    fun fallbackFile_isValid() {
        assertEquals(0.22, data.vatRate, 0.0)
        assertEquals(0.0008, data.charge(ChargeId.URE, LocalDate.of(2026, 11, 18))!!.value, 0.0)
        assertEquals(ChargeUnit.PER_KW_MONTH, data.charge(ChargeId.RES_SUPPORT, LocalDate.of(2026, 11, 18))!!.unit)
        assertEquals(5, data.networkOn(LocalDate.of(2026, 11, 18))!!.rows.size)
        assertEquals(6, times.vtStartHour)
        assertEquals(22, times.vtEndHour)
        // The copy built into the app and the one for the server are the same file.
        assertEquals(fallbackText, File("../server/public_html/api/ceniki.json").readText())
    }

    private fun mutated(change: (JSONObject) -> Unit): String = JSONObject(fallbackText).also(change).toString()

    @Test
    fun parser_rejectsBrokenFiles() {
        assertNull(PriceDataParser.parse(""))
        assertNull(PriceDataParser.parse("{}"))
        assertNull(PriceDataParser.parse(mutated { it.put("schemaVersion", 2) }))
        assertNull(PriceDataParser.parse(mutated { it.getJSONObject("taxes").remove("borzenContribution") }))
        assertNull(PriceDataParser.parse(mutated { it.getJSONObject("taxes").put("vatRate", 22) }))
        assertNull(PriceDataParser.parse(mutated {
            it.getJSONArray("network").getJSONObject(0).getJSONArray("blocks").remove(4)
        }))
        val pkg = JSONObject(
            """{"id":"a","name":"A","packages":[{"id":"p","name":"P","validFrom":"2026-11-01","validTo":null,
            "sourceUrl":"https://example.si","pricesWithoutVat":{"VT":148},"monthlyFeeWithoutVat":null}]}"""
        )
        assertNull(PriceDataParser.parse(mutated { it.getJSONArray("suppliers").put(pkg) }))
    }

    @Test
    fun parser_readsSupplierPackages() {
        val supplier = JSONObject(
            """{"id":"dob","name":"Dobavitelj","packages":[{"id":"dob-osnovni","name":"Osnovni","validFrom":"2026-11-01",
            "validTo":"2027-01-31","sourceUrl":"https://example.si/cenik.pdf","pricesWithoutVat":{"ET":0.11,"VT":0.12,"MT":0.09},
            "monthlyFeeWithoutVat":1.5}]}"""
        )
        val d = PriceDataParser.parse(mutated { it.getJSONArray("suppliers").put(supplier) })!!
        val p = d.findPackage("dob-osnovni")!!
        assertEquals(0.12, p.prices.vt!!, 0.0)
        assertEquals(1.5, p.monthlyFee!!, 0.0)
        assertEquals("Dobavitelj", d.supplierOf(p)!!.name)
    }

    // ---- Calculator ----

    @Test
    fun components_followTheFormula() {
        val day = LocalDate.of(2026, 11, 18)
        val c = EnergyPriceCalculator.components(data, custom(0.12, 0.09), network, day, 3, EnergyTariff.VT)!!
        val expectedNet = 0.12 + 0.017 + 0.0008 + 0.00013 + 0.00153
        assertEquals(expectedNet, c.net, 1e-12)
        assertEquals(expectedNet * 1.22, c.total, 1e-12)
        assertEquals(0.0, c.resSupport, 0.0) // billed on power, not per kWh
        // Prices typed with VAT are converted to prices without VAT first.
        val withVat = EnergyPriceCalculator.components(data, custom(0.1464, 0.1098, withVat = true), network, day, 3, EnergyTariff.VT)!!
        assertEquals(0.12, withVat.energy, 1e-12)
        // Unknown energy price or block: no price at all.
        assertNull(EnergyPriceCalculator.components(data, PricingSetup(MeterType.DUAL, null), network, day, 3, EnergyTariff.VT))
        assertNull(EnergyPriceCalculator.components(data, custom(0.12, 0.09), null, day, 3, EnergyTariff.VT))
    }

    @Test
    fun hourly_nextCheaperAndShift() {
        val wed = LocalDate.of(2026, 11, 18)
        val schedule = IntArray(24) { h -> if (h < 6 || h >= 22) 3 else 2 }
        val setup = custom(0.12, 0.09)
        val prices = EnergyPriceCalculator.hourly(data, setup, at(wed, 20, 30), 24, { schedule }, { network })
        assertEquals(24, prices.size)
        assertEquals(at(wed, 20), prices.first().startMillis)
        assertEquals(EnergyTariff.VT, prices.first().tariff)
        val next = EnergyPriceCalculator.nextCheaper(prices)!!
        assertEquals(at(wed, 22), next.startMillis)
        assertEquals(EnergyTariff.MT, next.tariff)
        // 2 kWh at 2 kW from 20:30: half an hour at 20 h and half at 21 h (VT, block 2); best start 22:00.
        val shift = EnergyPriceCalculator.bestShift(prices, at(wed, 20, 30), 2.0, 2.0)!!
        assertEquals(2 * prices[0].total, shift.costNow, 1e-9)
        assertEquals(at(wed, 22), shift.bestStartMillis)
        assertEquals(2 * prices[2].total, shift.costBest, 1e-9)
        assertTrue(shift.saving > 0)
        // A run longer than the known prices does not fit.
        assertNull(EnergyPriceCalculator.bestShift(prices, at(wed, 20, 30), 100.0, 2.0))
        // Without a schedule for the next day the list ends at midnight.
        val partial = EnergyPriceCalculator.hourly(data, setup, at(wed, 20, 30), 24, { if (it == wed) schedule else null }, { network })
        assertEquals(4, partial.size)
    }

    @Test
    fun monthlyBill_hasAllItems() {
        val day = LocalDate.of(2026, 11, 18) // higher season: OVE/SPTE on the agreed power of block 1
        val bill = EnergyPriceCalculator.monthlyBill(
            data, custom(0.12, 0.09), network, day,
            powerKw = listOf(5.0, 5.0, 6.0, 6.0, 7.0), energyKwh = listOf(50.0, 100.0, 150.0, 0.0, 0.0), mtShare = 0.5
        )!!
        val byKind = bill.items.associate { it.kind to it.net }
        assertEquals(300 * (0.5 * 0.09 + 0.5 * 0.12), byKind.getValue(BillItemKind.ENERGY), 1e-9)
        assertEquals(5 * 2.0 + 5 * 1.0 + 6 * 0.3 + 6 * 0.02 + 7 * 0.002, byKind.getValue(BillItemKind.NETWORK_POWER), 1e-9)
        assertEquals(50 * 0.03 + 100 * 0.02 + 150 * 0.017, byKind.getValue(BillItemKind.NETWORK_ENERGY), 1e-9)
        assertEquals(0.77562 * 5.0, byKind.getValue(BillItemKind.RES_SUPPORT), 1e-9)
        assertEquals(300 * 0.00153, byKind.getValue(BillItemKind.EXCISE), 1e-9)
        assertEquals(bill.net * 1.22, bill.total, 1e-9)
        // Lower season: block 2.
        val summer = EnergyPriceCalculator.monthlyBill(
            data, custom(0.12, 0.09), network, LocalDate.of(2026, 6, 1),
            listOf(5.0, 4.0, 6.0, 6.0, 7.0), listOf(50.0, 100.0, 150.0, 0.0, 0.0), 0.5
        )!!
        assertEquals(0.77562 * 4.0, summer.items.first { it.kind == BillItemKind.RES_SUPPORT }.net, 1e-9)
    }

    @Test
    fun customSettings_needCompletePrices() {
        val base = PricingSettings.Stored(MeterType.DUAL, PricingSettings.SOURCE_CUSTOM, null, PricingSettings.CustomInput("", "0,15", "", "", true))
        assertNull(PricingSettings.sourceOf(base))
        assertNotNull(PricingSettings.sourceOf(base.copy(custom = base.custom.copy(mt = "0,10"))))
        assertNull(PricingSettings.sourceOf(base.copy(meter = MeterType.SINGLE)))
        assertNull(PricingSettings.sourceOf(base.copy(custom = base.custom.copy(mt = "10"))))
        assertNull(PricingSettings.sourceOf(base.copy(sourceType = PricingSettings.SOURCE_NONE)))
    }

    // ---- When to ask the server ----

    @Test
    fun syncPolicy_checksOnlyWhenNeeded() {
        val today = LocalDate.of(2026, 11, 18)
        val now = at(today, 12)
        val day = 24 * 3600 * 1000L
        // First start: never answered yet.
        assertTrue(PriceSyncPolicy.shouldCheck(now, today, 0L, 0L, data))
        // Checked a few hours ago: wait.
        assertFalse(PriceSyncPolicy.shouldCheck(now, today, now - 3600_000L, 0L, data))
        // The file ends on 31 Dec: from 10 Dec on it is checked (21 days before).
        assertFalse(PriceSyncPolicy.shouldCheck(now, today, now - 2 * day, now - 2 * day, data))
        val dec10 = LocalDate.of(2026, 12, 10)
        assertTrue(PriceSyncPolicy.shouldCheck(at(dec10, 12), dec10, at(dec10, 12) - 2 * day, at(dec10, 12) - 2 * day, data))
        // Expired data: every day.
        val jan5 = LocalDate.of(2027, 1, 5)
        assertTrue(PriceSyncPolicy.shouldCheck(at(jan5, 12), jan5, at(jan5, 12) - day, at(jan5, 12) - day, data))
        // Nothing expires soon, but the last answer is older than 60 days.
        assertTrue(PriceSyncPolicy.shouldCheck(now, today, now - 2 * day, now - 61 * day, data))
    }

    @Test
    fun priceLists_downloadedReplaceBuiltInOfSameStart() {
        val a = network.copy(note = "built-in")
        val b = network.copy(note = "downloaded")
        val c = network.copy(validFrom = LocalDate.of(2027, 1, 1), validTo = LocalDate.of(2027, 2, 28))
        val merged = PriceLists.merge(listOf(a), listOf(b, c))
        assertEquals(listOf("downloaded", null), merged.map { it.note })
    }

    @Test
    fun diff_namesWhatChanged() {
        val today = LocalDate.of(2026, 11, 18)
        val changed = PriceDataParser.parse(mutated {
            it.getJSONObject("taxes").getJSONArray("exciseDuty").getJSONObject(0).put("value", 0.002)
        })!!
        assertEquals(setOf(PriceChange.CHARGES), PriceDiff.between(data, changed, today, null))
        assertTrue(PriceDiff.between(data, data, today, null).isEmpty())
        assertTrue(PriceDiff.between(null, data, today, null).isEmpty())
    }
}

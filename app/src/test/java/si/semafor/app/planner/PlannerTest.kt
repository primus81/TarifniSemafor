package si.semafor.app.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.semafor.app.PriceList
import si.semafor.app.PriceLists
import si.semafor.app.PriceRow
import si.semafor.app.Season
import si.semafor.app.SloTime
import si.semafor.app.pricing.EnergyPriceCalculator
import si.semafor.app.pricing.EnergyPrices
import si.semafor.app.pricing.EnergySource
import si.semafor.app.pricing.EnergyTariff
import si.semafor.app.pricing.EnergyTariffs
import si.semafor.app.pricing.KwhPrice
import si.semafor.app.pricing.MeterType
import si.semafor.app.pricing.PriceDataParser
import si.semafor.app.pricing.PricingSetup
import si.semafor.app.pricing.SloHolidays
import si.semafor.app.reminders.ReminderSettings
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** The planner, the advice, their storage and the calendar edge cases. All prices are made up for the tests. */
class PlannerTest {
    private val data = PriceDataParser.parse(File("src/main/assets/ceniki_fallback.json").readText())!!
    private val network = PriceList(
        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31),
        listOf(PriceRow(1, 2.0, 0.03), PriceRow(2, 1.0, 0.02), PriceRow(3, 0.3, 0.017), PriceRow(4, 0.02, 0.018), PriceRow(5, 0.002, 0.013)),
        null, "https://example.si"
    )
    private val wed = LocalDate.of(2026, 11, 18)

    // Made-up schedule of a working day: block 3 at night, block 2 by day.
    private val schedule = IntArray(24) { h -> if (h < 6 || h >= 22) 3 else 2 }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        ZonedDateTime.of(date, LocalTime.of(h, m), SloTime.ZONE).toInstant().toEpochMilli()

    private fun dual(vt: Double = 0.12, mt: Double = 0.09) =
        PricingSetup(MeterType.DUAL, EnergySource.Custom(EnergyPrices(null, vt, mt), false, null))

    private fun prices(from: Long, setup: PricingSetup = dual(), hours: Int = 48, scheduleOf: (LocalDate) -> IntArray? = { schedule }): List<KwhPrice> =
        EnergyPriceCalculator.hourly(data, setup, from, hours, scheduleOf, { network })

    private fun device(
        id: String,
        kw: Double,
        minutes: Int,
        kwh: Double? = null,
        earliest: Int? = null,
        deadline: Int? = null,
        days: Set<DayOfWeek> = emptySet()
    ) = PlannerDevice(id, DeviceType.CUSTOM, id, kw, kwh, minutes, earliest, deadline, days, userValues = true)

    private val noLimit: (Long) -> SlotLimit = { SlotLimit(null, 0.0) }

    // ---- One appliance ----

    @Test
    fun washer_cheapestStartBeforeDeadline_overMidnight() {
        val now = at(wed, 20, 30)
        val p = prices(now)
        val washer = device("pralni", 2.0, 150, kwh = 1.2, deadline = 7 * 60)
        val r = DayPlanner.plan(listOf(washer), p, now, noLimit).single()
        assertEquals(PlanStatus.OK, r.status)
        // MT and block 3 start at 22:00; the run 22:00-00:30 crosses midnight.
        assertEquals(at(wed, 22), r.startMillis)
        assertEquals(at(wed.plusDays(1), 0, 30), r.endMillis)
        val night = p.first { it.startMillis == at(wed, 22) }.total
        assertEquals(1.2 * night, r.cost, 1e-9)
        assertEquals(listOf(3), r.blocks)
        // Starting now (20:30) costs more: VT in block 2 until 22:00, then MT.
        assertTrue(r.costNow!! > r.cost)
        assertEquals(PlanReason.CHEAPEST, r.reason)
        // The alternative is at least an hour away.
        assertTrue(kotlin.math.abs(r.altStartMillis!! - r.startMillis) >= DayPlanner.ALT_MIN_DISTANCE_MS)
    }

    @Test
    fun runOverTwoBlocks_isSplitByTime() {
        val now = at(wed, 21, 0)
        val p = prices(now)
        val d = device("x", 2.0, 120)
        // From 21:00: one hour VT block 2, one hour MT block 3.
        val cost = DayPlanner.runCost(d, p, now)!!
        assertEquals(2.0 * p[0].total + 2.0 * p[1].total, cost, 1e-9)
        assertEquals(listOf(2, 3), DayPlanner.blocksOf(p, now, now + 2 * 3_600_000L))
    }

    @Test
    fun earliestStartAndDays_areRespected() {
        val now = at(wed, 20, 30)
        val p = prices(now)
        val late = device("late", 2.0, 60, earliest = 23 * 60, deadline = 7 * 60)
        assertEquals(at(wed, 23), DayPlanner.plan(listOf(late), p, now, noLimit).single().startMillis)
        // Only on Saturdays: nothing fits in the next 48 hours (Wednesday, Thursday).
        val sat = device("sat", 2.0, 60, days = setOf(DayOfWeek.SATURDAY))
        assertEquals(PlanStatus.NO_WINDOW, DayPlanner.plan(listOf(sat), p, now, noLimit).single().status)
        // Deadline too close for the run.
        val tight = device("tight", 2.0, 150, deadline = 21 * 60)
        assertEquals(PlanStatus.NO_WINDOW, DayPlanner.plan(listOf(tight), p, now, noLimit).single().status)
    }

    @Test
    fun missingPrices_andUnknownEnergy() {
        val now = at(wed, 20, 30)
        assertEquals(PlanStatus.NO_PRICES, DayPlanner.plan(listOf(device("a", 2.0, 60)), emptyList(), now, noLimit).single().status)
        // Prices only until midnight (no schedule for tomorrow), deadline 07:00, 5 hours: fits the window, not the prices.
        val p = prices(now, scheduleOf = { if (it == wed) schedule else null })
        val long = device("b", 2.0, 300, deadline = 7 * 60)
        assertEquals(PlanStatus.NO_PRICES, DayPlanner.plan(listOf(long), p, now, noLimit).single().status)
        // Unknown energy: power x duration, marked as an estimate.
        val d = device("c", 1.5, 90)
        assertTrue(d.energyEstimated)
        assertEquals(2.25, d.runKwh, 1e-12)
        assertEquals(1.5, d.averageKw, 1e-12)
        // Inactive appliances are not planned.
        assertTrue(DayPlanner.plan(listOf(d.copy(active = false)), p, now, noLimit).isEmpty())
    }

    @Test
    fun singleTariffSupplier_differencesOnlyFromNetwork() {
        val now = at(wed, 20, 30)
        val flat = PricingSetup(MeterType.SINGLE, EnergySource.Custom(EnergyPrices(0.11, null, null), false, null))
        val p = prices(now, flat)
        assertTrue(p.all { it.tariff == EnergyTariff.ET && it.components.energy == 0.11 })
        val r = DayPlanner.plan(listOf(device("x", 2.0, 60, deadline = 7 * 60)), p, now, noLimit).single()
        // Block 3 has the lower network charge for energy than block 2 in the test list.
        assertEquals(3, p.first { it.startMillis == r.startMillis }.block)
    }

    // ---- Several appliances and the agreed power ----

    @Test
    fun twoAppliances_doNotOverlapWhenPowerIsShort() {
        val now = at(wed, 20, 30)
        val p = prices(now)
        val washer = device("pralni", 2.0, 120, deadline = 7 * 60)
        val dryer = device("susilni", 2.5, 120, deadline = 7 * 60)
        // Agreed 4 kW, other use 1 kW: the two cannot run together (5.5 kW).
        val results = DayPlanner.plan(listOf(washer, dryer), p, now) { SlotLimit(4.0, 1.0) }
        assertEquals(2, results.size)
        val (a, b) = results
        assertTrue(a.status == PlanStatus.OK && b.status == PlanStatus.OK)
        assertTrue(a.endMillis <= b.startMillis || b.endMillis <= a.startMillis)
        assertTrue(results.none { it.overPower })
        assertTrue(results.all { it.overlapsWith.isEmpty() })
        // Without a limit they may run together, and the overlap is reported.
        val free = DayPlanner.plan(listOf(washer, dryer), p, now, noLimit)
        assertEquals(free[0].startMillis, free[1].startMillis)
        assertEquals(listOf("susilni"), free[0].overlapsWith)
    }

    @Test
    fun applianceAboveAgreedPower_isFlagged() {
        val now = at(wed, 20, 30)
        val ev = device("ev", 11.0, 220, kwh = 40.0, deadline = 7 * 60)
        val r = DayPlanner.plan(listOf(ev), prices(now), now) { SlotLimit(7.0, 0.5) }.single()
        assertEquals(PlanStatus.OK, r.status)
        assertTrue(r.overPower)
        assertEquals(PlanReason.CHEAPEST_OVER_POWER, r.reason)
        assertEquals(11.5, r.peakKw, 1e-9)
        assertEquals(7.0, r.peakAgreedKw!!, 0.0)
    }

    @Test
    fun nextRun_isTheEarliestNotFinished() {
        val now = at(wed, 20, 30)
        val res = DayPlanner.plan(listOf(device("a", 1.0, 60, deadline = 7 * 60), device("b", 1.0, 60, earliest = 23 * 60, deadline = 7 * 60)), prices(now), now, noLimit)
        assertEquals("a", DayPlanner.nextRun(res, now)!!.device.id)
        assertNull(DayPlanner.nextRun(res, at(wed.plusDays(2), 0)))
    }

    // ---- Advice ----

    @Test
    fun advice_goodNowOrWait() {
        val night = at(wed, 22, 15)
        val goodNow = NowAdvisor.advise(night, prices(night), false, null, null, 3, null, null)
        assertTrue(goodNow.first() is NowAdvice.GoodNow)
        assertTrue(goodNow.first().personal)
        val evening = at(wed, 20, 15)
        val wait = NowAdvisor.advise(evening, prices(evening), false, null, null, 2, null, null).first() as NowAdvice.WaitForCheaper
        assertEquals(at(wed, 22), wait.atMillis)
        assertEquals(3, wait.block)
        assertTrue(wait.priceThen < wait.priceNow)
    }

    @Test
    fun advice_withoutPrices_isGeneral_andDeviceFirst() {
        val now = at(wed, 20, 0)
        val general = NowAdvisor.advise(now, emptyList(), false, null, null, 2, 3, at(wed, 22)).single()
        assertTrue(general is NowAdvice.General)
        assertFalse(general.personal)
        val run = PlannerStore.PlannedRun("id", "Pralni stroj", now + 20 * 60_000L, now + 140 * 60_000L, 0.3)
        val list = NowAdvisor.advise(now, prices(now), true, run, null, 2, null, null)
        assertEquals(NowAdvice.DeviceSoon("Pralni stroj", run.startMillis, 20), list.first())
        assertTrue(list.contains(NowAdvice.FlatEnergy))
    }

    // ---- Storage and settings ----

    @Test
    fun devices_roundTripAndDamagedInput() {
        val d = PlannerDevice("1", DeviceType.EV, "Avto", 11.0, 40.0, 220, 22 * 60, 7 * 60, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), true, true)
        val e = PlannerDevice("2", DeviceType.WASHER, "Pralni", 2.0, null, 150)
        assertEquals(listOf(d, e), PlannerStore.decodeDevices(PlannerStore.encodeDevices(listOf(d, e))))
        assertTrue(PlannerStore.decodeDevices("not json").isEmpty())
        assertTrue(PlannerStore.decodeDevices(null).isEmpty())
        val run = PlannerStore.PlannedRun("1", "Avto", 1L, 2L, 3.5)
        assertEquals(listOf(run), PlannerStore.decodePlan(PlannerStore.encodePlan(listOf(run))))
    }

    @Test
    fun quietHours_overMidnight() {
        val v = ReminderSettings.Values(true, true, true, 10, quiet = true, quietFrom = 22, quietTo = 7)
        assertTrue(ReminderSettings.inQuiet(23, v))
        assertTrue(ReminderSettings.inQuiet(3, v))
        assertFalse(ReminderSettings.inQuiet(7, v))
        assertFalse(ReminderSettings.inQuiet(12, v))
        assertFalse(ReminderSettings.inQuiet(23, v.copy(quiet = false)))
    }

    @Test
    fun loadEstimate_medianByHour() {
        val pts = listOf(at(wed, 18) to 1.0, at(wed, 18, 15) to 3.0, at(wed, 18, 30) to 2.0, at(wed, 2) to 0.4)
        val m = LoadEstimate.hourlyMedian(pts)
        assertEquals(2.0, m[18], 1e-12)
        assertEquals(0.4, m[2], 1e-12)
        assertTrue(m[12].isNaN())
    }

    // ---- Calendar edge cases ----

    @Test
    fun clockChanges_hoursAreRealHours() {
        val all3 = IntArray(24) { 3 }
        // 25 October 2026: 25 hours (the hour 2:00 twice).
        val autumn = EnergyPriceCalculator.hourly(data, dual(), at(LocalDate.of(2026, 10, 25), 0), 30, { all3 }, { network })
        val oct25 = autumn.filter { SloTime.dateOf(it.startMillis) == LocalDate.of(2026, 10, 25) }
        assertEquals(25, oct25.size)
        assertTrue(autumn.zipWithNext().all { (a, b) -> b.startMillis - a.startMillis == 3_600_000L })
        // 28 March 2027: 23 hours.
        val spring = EnergyPriceCalculator.hourly(data, dual(), at(LocalDate.of(2027, 3, 28), 0), 30, { all3 }, { network })
        assertEquals(23, spring.count { SloTime.dateOf(it.startMillis) == LocalDate.of(2027, 3, 28) })
        // A planned run over the autumn change lasts its real duration.
        val p = DayPlanner.plan(listOf(device("x", 1.0, 180, earliest = 0, deadline = 6 * 60)), autumn, at(LocalDate.of(2026, 10, 25), 0), noLimit).single()
        assertEquals(3 * 3_600_000L, p.endMillis - p.startMillis)
    }

    @Test
    fun yearChange_2026to2027() {
        val times = data.tariffTimes
        val dec31 = LocalDate.of(2026, 12, 31) // Thursday, working day
        assertEquals(EnergyTariff.VT, EnergyTariffs.tariffAt(dec31, 12, times))
        assertEquals(EnergyTariff.MT, EnergyTariffs.tariffAt(LocalDate.of(2027, 1, 1), 12, times))
        assertEquals(EnergyTariff.MT, EnergyTariffs.tariffAt(LocalDate.of(2027, 1, 2), 12, times))
        assertEquals(EnergyTariff.VT, EnergyTariffs.tariffAt(LocalDate.of(2027, 1, 4), 12, times)) // Monday
        assertTrue(SloHolidays.isWorkFree(LocalDate.of(2027, 3, 29))) // Easter Monday 2027
        assertEquals(Season.HIGH, Season.of(LocalDate.of(2027, 2, 28)))
        assertEquals(Season.LOW, Season.of(LocalDate.of(2027, 3, 1)))
        // No network price list for 2027 is published in the app yet: the prices stop at midnight, nothing is guessed.
        assertNull(PriceLists.forDate(LocalDate.of(2027, 1, 1)))
        val p = EnergyPriceCalculator.hourly(data, dual(), at(dec31, 20), 24, { schedule }, { PriceLists.forDate(it) })
        assertEquals(4, p.size)
        assertNotNull(PriceLists.forDate(dec31))
    }
}

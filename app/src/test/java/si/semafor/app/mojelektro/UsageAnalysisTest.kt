package si.semafor.app.mojelektro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.semafor.app.Block
import si.semafor.app.SloTime
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Synthetic readings (made up) to check the analysis; block schedules are fake URO answers. */
class UsageAnalysisTest {
    private val day = LocalDate.of(2026, 11, 10)

    private fun at(date: LocalDate, h: Int, m: Int): Long =
        ZonedDateTime.of(date, LocalTime.of(h, m), SloTime.ZONE).toInstant().toEpochMilli()

    /** 96 readings of [date] of 0.25 kWh, labelled at the start or the end of each interval. */
    private fun dayReadings(date: LocalDate, label: IntervalLabel, kwh: (Int) -> Double = { 0.25 }): List<Reading> =
        (0 until 96).map { i ->
            val start = at(date, 0, 0) + i * UsageAnalysis.INTERVAL_MS
            val ts = if (label == IntervalLabel.END) start + UsageAnalysis.INTERVAL_MS else start
            Reading(ts, BigDecimal(kwh(i).toString()), emptyList())
        }

    @Test
    fun detectLabel_fromDailyMeterStates() {
        // Only the readings at the two edges of a day tell "start" from "end", so the checked day has a large first
        // (5 kWh) and last (3 kWh) interval, the days around it 0.1 kWh everywhere.
        val special = { i: Int -> if (i == 0) 5.0 else if (i == 95) 3.0 else 0.1 }
        fun threeDays(label: IntervalLabel) = dayReadings(day.minusDays(1), label) { 0.1 } +
            dayReadings(day, label, special) + dayReadings(day.plusDays(1), label) { 0.1 }
        val dayTotal = 5.0 + 3.0 + 94 * 0.1
        val states = listOf(
            Reading(at(day, 0, 0), BigDecimal("1000"), emptyList()),
            Reading(at(day.plusDays(1), 0, 0), BigDecimal("1000").add(BigDecimal(dayTotal.toString())), emptyList())
        )
        assertEquals(IntervalLabel.START, UsageAnalysis.detectLabel(threeDays(IntervalLabel.START), states, day.minusDays(1)))
        assertEquals(IntervalLabel.END, UsageAnalysis.detectLabel(threeDays(IntervalLabel.END), states, day.minusDays(1)))
    }

    @Test
    fun detectLabel_withoutStates_usesFirstTimestamp() {
        assertEquals(IntervalLabel.START, UsageAnalysis.detectLabel(dayReadings(day, IntervalLabel.START), emptyList(), day))
        assertEquals(IntervalLabel.END, UsageAnalysis.detectLabel(dayReadings(day, IntervalLabel.END), emptyList(), day))
        assertEquals(IntervalLabel.UNKNOWN, UsageAnalysis.detectLabel(emptyList(), emptyList(), day))
    }

    @Test
    fun groupByDate_endLabelBelongsToPreviousInterval() {
        val byDate = UsageAnalysis.groupByDate(dayReadings(day, IntervalLabel.END), IntervalLabel.END)
        // The last reading (next day 00:00) is the 23:45-24:00 interval of the same day.
        assertEquals(setOf(day), byDate.keys)
        assertEquals(96, byDate.getValue(day).size)
        assertEquals(day, UsageAnalysis.lastCompleteDay(byDate))
    }

    @Test
    fun expectedIntervals_followClockChanges() {
        assertEquals(96, UsageAnalysis.expectedIntervals(day))
        assertEquals(92, UsageAnalysis.expectedIntervals(LocalDate.of(2026, 3, 29)))
        assertEquals(100, UsageAnalysis.expectedIntervals(LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun dayProfile_assignsBlocksAgreedPowerAndLevels() {
        val schedule = IntArray(24) { h -> if (h < 6) 3 else 2 }
        val agreed = AgreedPower(null, null, listOf(BigDecimal("5"), BigDecimal("4"), BigDecimal("2")) + listOf(null, null), true)
        // Interval 0 (block 3, agreed 2 kW): 0.6 kWh = 2.4 kW -> over. Interval 1: 0.46 kWh = 1.84 kW -> near (>= 90 %).
        val readings = UsageAnalysis.groupByDate(
            dayReadings(day, IntervalLabel.START) { i -> if (i == 0) 0.6 else if (i == 1) 0.46 else 0.1 },
            IntervalLabel.START
        ).getValue(day)
        val profile = UsageAnalysis.dayProfile(day, readings, schedule, agreed, IntervalLabel.START)
        assertEquals(3, profile.intervals[0].blockId)
        assertEquals(2.4, profile.intervals[0].kw, 1e-9)
        assertEquals(PowerLevel.OVER, profile.intervals[0].level)
        assertEquals(PowerLevel.NEAR, profile.intervals[1].level)
        assertEquals(PowerLevel.NORMAL, profile.intervals[30].level) // 06:30, block 2, 0.4 kW of 4 kW
        assertEquals(0, profile.peakIndex)
        assertEquals(2.4, UsageAnalysis.peakInBlock(profile, 3)!!.kw, 1e-9)
    }

    @Test
    fun dailyByBlocks_sumsPerBlockAndUnassigned() {
        val byDate = UsageAnalysis.groupByDate(
            dayReadings(day, IntervalLabel.START) + dayReadings(day.plusDays(1), IntervalLabel.START),
            IntervalLabel.START
        )
        val schedule = IntArray(24) { h -> if (h < 6) 3 else 2 }
        val result = UsageAnalysis.dailyByBlocks(byDate, mapOf(day to schedule, day.plusDays(1) to null), IntervalLabel.START)
        assertEquals(6.0, result[0].kwhByBlock[2], 1e-9) // 24 intervals x 0.25
        assertEquals(18.0, result[0].kwhByBlock[1], 1e-9)
        assertEquals(0.0, result[0].unassignedKwh, 1e-9)
        assertEquals(24.0, result[1].unassignedKwh, 1e-9) // no schedule for the second day
    }

    @Test
    fun currentAgreedPower_picksValidAndLatest() {
        val now = at(day, 12, 0)
        val old = AgreedPower("2024-10-01T00:00:00+02:00", "2025-09-30T23:59:59+02:00", List(5) { BigDecimal.ONE }, true)
        val current = AgreedPower("2025-10-01T00:00:00+02:00", null, List(5) { BigDecimal.TEN }, true)
        val invalid = AgreedPower("2026-01-01T00:00:00+01:00", null, List(5) { BigDecimal("3") }, false)
        assertEquals(current, UsageAnalysis.currentAgreedPower(listOf(old, current, invalid), now))
        assertNull(UsageAnalysis.currentAgreedPower(listOf(old), now))
    }

    @Test
    fun blockSchedule_walksTheDayBlockByBlock() {
        // Fake URO answers for a day: 0-6 block 3, 6-7 block 2, 7-14 block 1, 14-16 block 2, 16-20 block 1, 20-22
        // block 2, 22-6 block 3.
        val segments = listOf(Triple(0, 6, 3), Triple(6, 7, 2), Triple(7, 14, 1), Triple(14, 16, 2), Triple(16, 20, 1), Triple(20, 22, 2), Triple(22, 6, 3))
        var calls = 0
        val schedule = BlockSchedules.build(day) { millis ->
            calls++
            val h = SloTime.hourOfDay(millis)
            val (from, to, id) = segments.first { SloTime.coversHour(h, it.first, it.second) }
            Block(id, from, to, "", "", emptyList())
        }
        val expected = IntArray(24) { h -> segments.first { SloTime.coversHour(h, it.first, it.second) }.third }
        assertArrayEquals(expected, schedule)
        assertEquals(segments.size, calls)
        assertNull(BlockSchedules.build(day) { null })
        assertArrayEquals(expected, BlockSchedules.decode(BlockSchedules.encode(expected)))
        assertNull(BlockSchedules.decode("1,2,3"))
    }

    @Test
    fun registers_areResolvedFromCatalogueLabels() {
        val catalogue = listOf(
            ReadingTypeInfo("code-a-plus", null, "A+", null, "15 min", null, ReadingKind.KOLICINA),
            ReadingTypeInfo("code-et", null, "A+_T0", null, "24 h", null, ReadingKind.STANJE),
            ReadingTypeInfo("code-r", null, "R+", null, "15 min", null, ReadingKind.KOLICINA)
        )
        assertEquals(
            mapOf(ReadingRegister.IMPORT_15MIN to "code-a-plus", ReadingRegister.IMPORT_DAILY_STATE to "code-et"),
            ReadingRegister.resolve(catalogue)
        )
    }
}

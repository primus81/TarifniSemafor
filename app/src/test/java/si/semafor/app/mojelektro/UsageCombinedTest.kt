package si.semafor.app.mojelektro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.semafor.app.SloTime
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Combination of the URO block schedule with the 15-minute readings (synthetic data). */
class UsageCombinedTest {
    private val day = LocalDate.of(2026, 11, 10)
    private val agreed = AgreedPower(
        null, null,
        listOf(BigDecimal("5"), BigDecimal("4"), BigDecimal("2"), null, BigDecimal("6")),
        true
    )

    // 00-06 block 3, 06-14 block 2, 14-22 block 1, 22-24 block 3.
    private val schedule = IntArray(24) { h -> when (h) { in 0..5 -> 3; in 6..13 -> 2; in 14..21 -> 1; else -> 3 } }

    private fun at(date: LocalDate, h: Int, m: Int): Long =
        ZonedDateTime.of(date, LocalTime.of(h, m), SloTime.ZONE).toInstant().toEpochMilli()

    private fun readings(date: LocalDate, kwh: (Int) -> Double?): List<Reading> = (0 until 96).map { i ->
        Reading(at(date, 0, 0) + i * UsageAnalysis.INTERVAL_MS, kwh(i)?.let { BigDecimal(it.toString()) }, emptyList())
    }

    private fun profile(date: LocalDate, kwh: (Int) -> Double): DayProfile {
        val byDate = UsageAnalysis.groupByDate(readings(date, kwh), IntervalLabel.START)
        return UsageAnalysis.dayProfile(date, byDate.getValue(date), schedule, agreed, IntervalLabel.START)
    }

    @Test
    fun blockSegments_followTheScheduleInTimeOrder() {
        val segments = UsageAnalysis.blockSegments(profile(day) { 0.1 })
        assertEquals(
            listOf(BlockSegment(3, 0, 24), BlockSegment(2, 24, 56), BlockSegment(1, 56, 88), BlockSegment(3, 88, 96)),
            segments
        )
        assertTrue(UsageAnalysis.blockSegments(DayProfile(day, emptyList(), null)).isEmpty())
    }

    @Test
    fun blockSegments_unknownLabelGivesOneUnknownSegment() {
        val byDate = UsageAnalysis.groupByDate(readings(day) { 0.1 }, IntervalLabel.UNKNOWN)
        val p = UsageAnalysis.dayProfile(day, byDate.getValue(day), schedule, agreed, IntervalLabel.UNKNOWN)
        assertEquals(listOf(BlockSegment(null, 0, 96)), UsageAnalysis.blockSegments(p))
    }

    @Test
    fun indexAt_mapsTouchToIntervalAndClamps() {
        assertEquals(0, UsageAnalysis.indexAt(-50f, 10f, 960f, 96))
        assertEquals(0, UsageAnalysis.indexAt(10f, 10f, 960f, 96))
        assertEquals(1, UsageAnalysis.indexAt(20f, 10f, 960f, 96))
        assertEquals(48, UsageAnalysis.indexAt(490f, 10f, 960f, 96))
        assertEquals(95, UsageAnalysis.indexAt(5000f, 10f, 960f, 96))
        assertEquals(0, UsageAnalysis.indexAt(100f, 0f, 0f, 96))
        assertEquals(0, UsageAnalysis.indexAt(100f, 0f, 100f, 0))
    }

    @Test
    fun utilization_isShareOfTheBlocksAgreedPower() {
        val p = profile(day) { i -> if (i == 60) 1.1 else 0.1 } // 15:00, block 1 (5 kW): 4.4 kW
        assertEquals(0.88, UsageAnalysis.utilization(p.intervals[60])!!, 1e-9)
        // Block 4 has no agreed power in this data.
        val unknown = IntervalPoint(0L, 0.5, 2.0, 4, null)
        assertNull(UsageAnalysis.utilization(unknown))
    }

    @Test
    fun dailyPeaks_onePerDayWithCounts() {
        // Day 1: interval 0 (block 3, 2 kW) at 2.4 kW = over; interval 1 at 1.84 kW = near.
        val d1 = profile(day) { i -> if (i == 0) 0.6 else if (i == 1) 0.46 else 0.1 }
        val d2 = profile(day.plusDays(1)) { 0.1 }
        val zero = profile(day.plusDays(2)) { 0.0 }
        val peaks = UsageAnalysis.dailyPeaks(listOf(d2, zero, d1))
        assertEquals(listOf(day, day.plusDays(1)), peaks.map { it.date })
        assertEquals(1, peaks[0].overCount)
        assertEquals(1, peaks[0].nearCount)
        assertEquals(1.2, peaks[0].utilization!!, 1e-9)
        assertEquals(0, peaks[1].overCount)
    }

    @Test
    fun blockPowerRows_coverAllFiveBlocks() {
        val d1 = profile(day) { i -> if (i == 0) 0.6 else if (i == 60) 1.1 else 0.1 }
        val rows = UsageAnalysis.blockPowerRows(listOf(d1), agreed)
        assertEquals((1..5).toList(), rows.map { it.blockId })
        assertEquals(4.4, rows[0].peak!!.kw, 1e-9)
        assertEquals(0.88, rows[0].utilization!!, 1e-9)
        assertEquals(2.4, rows[2].peak!!.kw, 1e-9)
        assertEquals(1, rows[2].overCount)
        assertEquals(32, rows[1].intervalCount)
        assertNull(rows[3].agreedKw)
        assertNull(rows[3].peak) // no interval in block 4
    }

    @Test
    fun registerSummary_totalsInRange() {
        val before = readings(day.minusDays(1)) { 1.0 }
        val inRange = readings(day) { i -> if (i == 5) null else 0.25 }
        val flagged = inRange.mapIndexed { i, r -> if (i == 7) r.copy(qualities = listOf("ESTIMATED")) else r }
        val exports = readings(day) { i -> if (i in 40..43) 0.5 else 0.0 }
        val s = UsageAnalysis.registerSummary(before + flagged, exports, IntervalLabel.START, day, hasExport = true)
        assertEquals(95 * 0.25, s.importKwh, 1e-9)
        assertEquals(2.0, s.exportKwh!!, 1e-9)
        assertEquals(95, s.intervalCount)
        assertEquals(1, s.missingCount)
        assertEquals(1, s.flaggedCount)
        assertEquals(mapOf("ESTIMATED" to 1), s.flaggedByType)
        assertNull(UsageAnalysis.registerSummary(flagged, exports, IntervalLabel.START, day, hasExport = false).exportKwh)
    }

    @Test
    fun insights_describePowerAndBlock() {
        val over = IntervalPoint(0L, 0.6, 2.4, 3, 2.0)
        val near = IntervalPoint(0L, 0.46, 1.84, 2, 2.0)
        val safe = IntervalPoint(0L, 0.1, 0.4, 5, 2.0)
        assertEquals("Nad dogovorjeno močjo bloka: 120 % dogovorjene moči.", UsageInsights.powerText(over))
        assertEquals("Blizu dogovorjene moči bloka: 92 %.", UsageInsights.powerText(near))
        assertEquals("Varno pod dogovorjeno močjo bloka: 20 %.", UsageInsights.powerText(safe))
        assertEquals("Dogovorjena moč za ta interval ni znana.", UsageInsights.powerText(over.copy(agreedKw = null)))
        assertTrue(UsageInsights.blockText(near).startsWith("2. blok je med dražjimi"))
        assertTrue(UsageInsights.blockText(safe).startsWith("5. blok je med cenejšimi"))
        assertEquals("Časovni blok tega intervala ni znan.", UsageInsights.blockText(safe.copy(blockId = null)))
        assertEquals("2,40 kW (0,600 kWh v 15 minutah), 3. blok, dogovorjena 2,00 kW.", UsageInsights.summary(over))
    }

    @Test
    fun peakVerdict_usesTheCounts() {
        val p = IntervalPoint(0L, 0.6, 2.4, 3, 2.0)
        assertEquals("Presežena v 2 četrturah (najvišja 120 %).", UsageInsights.peakVerdict(DailyPeak(day, p, 0, 2)))
        assertEquals("Nad 90 % v 1 četrturi (najvišja 120 %).", UsageInsights.peakVerdict(DailyPeak(day, p, 1, 0)))
        val low = p.copy(kw = 1.0)
        assertEquals("Pod 90 % ves dan (najvišja 50 %).", UsageInsights.peakVerdict(DailyPeak(day, low, 0, 0)))
    }
}

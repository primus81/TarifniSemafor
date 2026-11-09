package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class SloTimeTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun normalWinterDay_cet() {
        // 2026-01-15 10:00 CET = 09:00Z -> next 22:00 CET = 21:00Z
        assertEquals(
            ms("2026-01-15T21:00:00Z"),
            SloTime.nextStartAfter(ms("2026-01-15T09:00:00Z"), 22)
        )
        assertEquals("10:00", SloTime.formatHm(ms("2026-01-15T09:00:00Z")))
    }

    @Test
    fun normalSummerDay_cest() {
        // 2026-07-15 10:00 CEST = 08:00Z -> next 22:00 CEST = 20:00Z
        assertEquals(
            ms("2026-07-15T20:00:00Z"),
            SloTime.nextStartAfter(ms("2026-07-15T08:00:00Z"), 22)
        )
        assertEquals("10:00", SloTime.formatHm(ms("2026-07-15T08:00:00Z")))
    }

    @Test
    fun lastSundayInMarch_springForward() {
        // 2026-03-29: 02:00 CET -> 03:00 CEST. Local 01:30 CET = 00:30Z.
        val after = ms("2026-03-29T00:30:00Z")
        assertEquals(ms("2026-03-29T01:00:00Z"), SloTime.nextStartAfter(after, 3))
        // Hour 2 does not exist that day: resolves to the first valid instant (03:00 CEST).
        assertEquals(ms("2026-03-29T01:00:00Z"), SloTime.nextStartAfter(after, 2))
        assertEquals(ms("2026-03-29T20:00:00Z"), SloTime.nextStartAfter(after, 22))
        // The day is 23 hours long.
        val midnight = SloTime.nextStartAfter(ms("2026-03-28T11:00:00Z"), 0)
        assertEquals(ms("2026-03-28T23:00:00Z"), midnight)
        assertEquals(23 * 3600_000L, SloTime.nextStartAfter(midnight, 0) - midnight)
    }

    @Test
    fun lastSundayInOctober_fallBack() {
        // 2026-10-25: 03:00 CEST -> 02:00 CET. Local 00:30 CEST = 22:30Z (previous day).
        val before = ms("2026-10-24T22:30:00Z")
        assertEquals(
            ms("2026-10-25T00:00:00Z"),
            SloTime.nextStartAfter(before, 2)
        ) // first 02:00 (CEST)
        assertEquals(ms("2026-10-25T02:00:00Z"), SloTime.nextStartAfter(before, 3)) // 03:00 CET
        // Already past the first 02:00: the next 02:00 is tomorrow (CET).
        assertEquals(
            ms("2026-10-26T01:00:00Z"),
            SloTime.nextStartAfter(ms("2026-10-25T00:30:00Z"), 2)
        )
        // The day is 25 hours long.
        val midnight = SloTime.nextStartAfter(ms("2026-10-24T10:00:00Z"), 0)
        assertEquals(ms("2026-10-24T22:00:00Z"), midnight)
        assertEquals(25 * 3600_000L, SloTime.nextStartAfter(midnight, 0) - midnight)
        // Wall hour 2 is seen twice (CEST and CET).
        assertEquals(2, SloTime.hourOfDay(ms("2026-10-25T00:30:00Z")))
        assertEquals(2, SloTime.hourOfDay(ms("2026-10-25T01:30:00Z")))
    }

    @Test
    fun midnight() {
        // 23:59:59 CEST on 30 June -> hour 0 is 00:00 CEST on 1 July (= 22:00Z on 30 June)
        val after = ms("2026-06-30T21:59:59Z")
        assertEquals(ms("2026-06-30T22:00:00Z"), SloTime.nextStartAfter(after, 0))
        assertEquals(ms("2026-06-30T22:00:00Z"), SloTime.nextStartAfter(after, 24))
        // Exactly at the boundary the next start is the following day, never "now".
        assertEquals(
            ms("2026-07-01T22:00:00Z"),
            SloTime.nextStartAfter(ms("2026-06-30T22:00:00Z"), 0)
        )
    }

    @Test
    fun yearTransition() {
        // 2026-12-31 23:00 CET = 22:00Z
        val after = ms("2026-12-31T22:00:00Z")
        assertEquals(ms("2026-12-31T23:00:00Z"), SloTime.nextStartAfter(after, 0))
        assertEquals(LocalDate.of(2027, 1, 1), SloTime.dateOf(ms("2026-12-31T23:00:00Z")))
        assertEquals(LocalDate.of(2026, 12, 31), SloTime.dateOf(ms("2026-12-31T22:59:59Z")))
    }

    @Test
    fun deviceTimeZoneChanged_resultsStayInSlovenianTime() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf(
                "America/New_York",
                "Pacific/Auckland",
                "UTC",
                "Europe/Ljubljana"
            )) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(
                    ms("2026-07-15T20:00:00Z"),
                    SloTime.nextStartAfter(ms("2026-07-15T08:00:00Z"), 22)
                )
                assertEquals("10:00", SloTime.formatHm(ms("2026-07-15T08:00:00Z")))
                assertEquals(10, SloTime.hourOfDay(ms("2026-07-15T08:00:00Z")))
                assertEquals(
                    "15-07-2026 10:00:00",
                    SloTime.formatApiTimestamp(ms("2026-07-15T08:00:00Z"))
                )
                assertEquals(
                    "15-01-2026 10:00:00",
                    SloTime.formatApiTimestamp(ms("2026-01-15T09:00:00Z"))
                )
                assertEquals(
                    "15. 7. 2026 ob 10:00",
                    SloTime.formatDateTime(ms("2026-07-15T08:00:00Z"))
                )
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun previousStart_isStartOfRunningBlock() {
        // 2026-01-15 10:00 CET = 09:00Z
        val at = ms("2026-01-15T09:00:00Z")
        assertEquals(ms("2026-01-15T05:00:00Z"), SloTime.previousStartAtOrBefore(at, 6))
        assertEquals(
            ms("2026-01-14T21:00:00Z"),
            SloTime.previousStartAtOrBefore(at, 22)
        ) // started yesterday
        assertEquals(at, SloTime.previousStartAtOrBefore(at, 10)) // exactly at the start
        // Spring gap (hour 2 does not exist on 2026-03-29): 07:00 CEST = 05:00Z -> 03:00 CEST
        assertEquals(
            ms("2026-03-29T01:00:00Z"),
            SloTime.previousStartAtOrBefore(ms("2026-03-29T05:00:00Z"), 2)
        )
        // Autumn overlap (2026-10-25): 06:00 CET = 05:00Z -> first 02:00 (CEST) = 00:00Z
        assertEquals(
            ms("2026-10-25T00:00:00Z"),
            SloTime.previousStartAtOrBefore(ms("2026-10-25T05:00:00Z"), 2)
        )
    }

    @Test
    fun previousStart_matchesAnnouncedBoundary_aroundDst() {
        // The alarm fires 30 s after the boundary computed by nextStartAfter; both must agree on the block start.
        val samples = listOf(
            "2026-01-15T09:00:00Z", "2026-03-28T22:00:00Z", "2026-03-29T00:10:00Z",
            "2026-10-24T21:00:00Z", "2026-10-25T00:10:00Z", "2026-12-31T22:30:00Z"
        )
        for (s in samples) for (hour in listOf(0, 2, 3, 6, 22)) {
            val boundary = SloTime.nextStartAfter(ms(s), hour)
            assertEquals(
                "$s hour $hour",
                boundary,
                SloTime.previousStartAtOrBefore(boundary + 30_000L, hour)
            )
        }
    }

    @Test
    fun transitionMessages_onlyStateVerifiedFacts() {
        assertEquals("Začel se je ugodnejši časovni blok 4.", transitionMessage(4, 3, 60_000L))
        assertEquals("Začel se je dražji časovni blok 1.", transitionMessage(1, 5, 60_000L))
        assertEquals(
            "Začel se je časovni blok 2.",
            transitionMessage(2, -1, 60_000L)
        ) // previous unknown
        assertEquals("Začel se je časovni blok 2.", transitionMessage(2, 2, 60_000L))
        assertEquals(
            "Začel se je ugodnejši časovni blok 5.",
            transitionMessage(5, 4, MAX_LATENESS_MS)
        ) // limit is still on time
        assertEquals(
            "Zdaj velja časovni blok 3.",
            transitionMessage(3, 2, MAX_LATENESS_MS + 1)
        ) // delayed: no "started" claim
    }

    @Test
    fun widgetStamp_todayShowsOnlyTime_otherDayShowsDate() {
        val at = ms("2026-07-15T08:00:00Z") // 10:00 CEST
        assertEquals("10:00", SloTime.formatStamp(at, LocalDate.of(2026, 7, 15)))
        assertEquals("15. 7. 10:00", SloTime.formatStamp(at, LocalDate.of(2026, 7, 16)))
        // Late evening UTC is already the next Slovenian day.
        assertEquals(
            "00:30",
            SloTime.formatStamp(ms("2026-12-31T23:30:00Z"), LocalDate.of(2027, 1, 1))
        )
        assertEquals(
            "1. 1. 00:30",
            SloTime.formatStamp(ms("2026-12-31T23:30:00Z"), LocalDate.of(2027, 1, 2))
        )
    }

    @Test
    fun blockIntervals_wrapOverMidnight() {
        assertTrue(SloTime.coversHour(23, 22, 6))
        assertTrue(SloTime.coversHour(0, 22, 6))
        assertTrue(SloTime.coversHour(5, 22, 6))
        assertFalse(SloTime.coversHour(6, 22, 6))
        assertFalse(SloTime.coversHour(21, 22, 6))
        assertTrue(SloTime.coversHour(6, 6, 7))
        assertFalse(SloTime.coversHour(7, 6, 7))
        assertTrue(SloTime.coversHour(5, 24, 24)) // from == to: whole day
        assertTrue(SloTime.coversHour(23, 22, 24)) // block 22-24 ends at midnight
        assertFalse(SloTime.coversHour(0, 22, 24))
    }
}

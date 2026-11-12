package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusIndicatorTest {

    @Test
    fun titleNamesTheCurrentBlock() {
        for (id in 1..5) assertEquals("Trenutni časovni blok: $id", statusTitle(id))
    }

    @Test
    fun textGivesTheTimeOfTheNextChangeAndTheNextBlock() {
        assertEquals("Do 22:00 · naslednji: 4. blok", statusText("22:00", 4))
        assertEquals("Do 06:00 · naslednji: 2. blok", statusText("06:00", 2))
    }

    @Test
    fun indicatorOutlivesItsBlockOnlyBrieflyAndNeverDisappearsImmediately() {
        // Normal case: shortly after the end of the block, so the alarm has time to replace it.
        assertEquals(60 * 60 * 1000L + STATUS_GRACE_MS, statusTimeoutMs(60 * 60 * 1000L))
        // The block is just over or already over: still at least one minute, never a zero or negative timeout.
        assertEquals(STATUS_GRACE_MS - 5_000L, statusTimeoutMs(-5_000L))
        assertEquals(60_000L, statusTimeoutMs(-10 * 60 * 1000L))
        assertTrue(statusTimeoutMs(Long.MIN_VALUE / 4) >= 60_000L)
    }

    @Test
    fun alarmIsWantedIfEitherFeatureIsOn() {
        assertTrue(alarmWanted(blockNotifications = true, statusIcon = true))
        assertTrue(alarmWanted(blockNotifications = true, statusIcon = false))
        assertTrue(alarmWanted(blockNotifications = false, statusIcon = true))
        assertFalse(alarmWanted(blockNotifications = false, statusIcon = false))
    }
}

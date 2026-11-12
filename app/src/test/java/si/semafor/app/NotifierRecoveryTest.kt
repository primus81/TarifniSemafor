package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifierRecoveryTest {

    @Test
    fun missedBoundaryNeedsAnAnnouncedBoundaryThatHasPassed() {
        assertFalse(isMissedBoundary(0L, 100L)) // nothing was ever announced
        assertTrue(isMissedBoundary(50L, 100L)) // boundary passed while offline
        assertTrue(isMissedBoundary(100L, 100L)) // exactly now
        assertFalse(isMissedBoundary(150L, 100L)) // boundary still in the future
        assertFalse(isMissedBoundary(-5L, 100L))
    }

    @Test
    fun recoveryMessageClaimsNoDirectionWhenThePreviousBlockIsUnknown() {
        // previousId = -1 is what the recovery path passes: neither "cheaper" nor "dearer" may be claimed.
        assertEquals("Začel se je časovni blok 3.", transitionMessage(3, -1, 60_000L))
        assertEquals("Zdaj velja časovni blok 3.", transitionMessage(3, -1, MAX_LATENESS_MS + 1))
    }

    @Test
    fun verifiedTransitionStillSaysWhetherItGotCheaperOrDearer() {
        assertEquals("Začel se je ugodnejši časovni blok 4.", transitionMessage(4, 2, 60_000L))
        assertEquals("Začel se je dražji časovni blok 2.", transitionMessage(2, 4, 60_000L))
    }
}

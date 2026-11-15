package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFormatTest {
    @Test
    fun durationSplitsHoursMinutesSeconds() {
        assertEquals("0 h 0 min 0 s", HomeFormat.duration(999))
        assertEquals("1 h 2 min 3 s", HomeFormat.duration((3600 + 120 + 3) * 1000L))
        assertEquals("23 h 59 min 59 s", HomeFormat.duration((24 * 3600 - 1) * 1000L))
    }

    @Test
    fun blockCountdownSaysNowAtOrAfterTheChange() {
        assertEquals("Sprememba časovnega bloka zdaj", HomeFormat.blockCountdown(0))
        assertEquals("Sprememba časovnega bloka zdaj", HomeFormat.blockCountdown(-5000))
        assertEquals("Sprememba časovnega bloka čez 0 h 1 min 0 s", HomeFormat.blockCountdown(60_000))
    }

    @Test
    fun bestCountdown() {
        assertEquals("(zdaj)", HomeFormat.bestCountdown(0))
        assertEquals("(čez 2 h 0 min 0 s)", HomeFormat.bestCountdown(7_200_000))
    }

    @Test
    fun seasonLabelUsesHigherLowerWording() {
        assertEquals("Višja sezona".lowercase(), HomeFormat.seasonLabel("Visoka sezona").lowercase())
        assertEquals("višja sezona, delovni dan", HomeFormat.seasonLabel("visoka sezona, delovni dan"))
        assertEquals("nižja sezona", HomeFormat.seasonLabel("NIZKA SEZONA"))
        assertEquals("brez spremembe", HomeFormat.seasonLabel("brez spremembe"))
    }
}

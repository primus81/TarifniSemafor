package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun onlyTwoLanguagesAndSlovenianByDefault() {
        assertEquals("sl", AppLanguage.normalize(null))
        assertEquals("sl", AppLanguage.normalize("de"))
        assertEquals("sl", AppLanguage.normalize("sl-SI"))
        assertEquals("en", AppLanguage.normalize("en"))
        assertEquals("en", AppLanguage.normalize("en-US"))
    }

    @Test
    fun datesFollowTheLanguage() {
        val millis = java.time.ZonedDateTime.of(2026, 11, 15, 14, 5, 0, 0, SloTime.ZONE).toInstant().toEpochMilli()
        assertEquals("15. 11. 2026", SloTime.formatDate(millis))
        assertEquals("15 Nov 2026", SloTime.formatDate(millis, AppSettings.LANGUAGE_EN))
        assertEquals("15. 11. 2026 ob 14:05", SloTime.formatDateTime(millis))
        assertEquals("15 Nov 2026, 14:05", SloTime.formatDateTime(millis, AppSettings.LANGUAGE_EN))
    }

    @Test
    fun apiTextsAreNormalisedForLookup() {
        assertEquals("nižja obremenitev omrežja", ApiTexts.normalize("Nižja obremenitev omrežja\r\n"))
        assertEquals("višja sezona-prvi del", ApiTexts.normalize(HomeFormat.seasonLabel("visoka sezona-prvi del")))
    }
}

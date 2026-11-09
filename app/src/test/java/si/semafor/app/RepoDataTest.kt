package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RepoDataTest {

    private fun blockJson(
        id: Int,
        from: Int,
        to: Int,
        advice: String? = """[{"vrstaNaprave":"Pralni stroj","vrstaRabe":"Priporočeno","stanje":1}]"""
    ): String {
        val adviceField = if (advice == null) "" else ""","nasveti":$advice"""
        return """{"casovniBlok":{"casovniBlokId":$id,"uraOd":$from,"uraDo":$to},""" +
                """"obremenjenostOmrezja":{"naziv":" Nizka obremenjenost "},""" +
                """"sezonaCasovnegaBloka":{"naziv":"visoka sezona-drugi del"}$adviceField}"""
    }

    private fun response(now: String, next: String) =
        """{"casovniBlokTrenutni":$now,"casovniBlokNaslednji":$next}"""

    private fun assertRejected(text: String) {
        try {
            Repo.parse(text, 1L, true)
            fail("Response should have been rejected: $text")
        } catch (e: Exception) {
            // expected: invalid or incomplete responses never become data
        }
    }

    // ---- API response handling ----

    @Test
    fun normalResponse_isParsed() {
        val info = Repo.parse(response(blockJson(3, 22, 6), blockJson(2, 6, 7)), 123L, true)
        assertEquals(3, info.now.id)
        assertEquals(22, info.now.from)
        assertEquals(6, info.now.to)
        assertEquals("Nizka obremenjenost", info.now.load) // trimmed
        assertEquals(
            "visoka sezona-drugi del",
            info.now.season
        ) // raw API text, presentation maps it later
        assertEquals(1, info.now.advice.size)
        assertEquals(2, info.next.id)
        assertEquals(123L, info.time)
        assertTrue(info.fresh)
    }

    @Test
    fun invalidJson_isRejected() {
        assertRejected("not json at all")
        assertRejected("<html><body>Service unavailable</body></html>")
        assertRejected("{\"casovniBlokTrenutni\":")
    }

    @Test
    fun emptyOrBlankBody_isRejected() {
        assertRejected("")
        assertRejected("   ")
        assertRejected("{}")
        assertRejected("[]")
    }

    @Test
    fun missingFields_areRejected() {
        val ok = blockJson(3, 22, 6)
        assertRejected("""{"casovniBlokTrenutni":$ok}""") // no next block
        assertRejected("""{"casovniBlokNaslednji":$ok}""") // no current block
        assertRejected(response(blockJson(3, 22, 6, advice = null), ok)) // no advice array
        assertRejected(
            response(
                """{"casovniBlok":{"casovniBlokId":3,"uraOd":22}}""",
                ok
            )
        ) // missing uraDo + rest
    }

    @Test
    fun unexpectedValues_areRejected() {
        val ok = blockJson(3, 22, 6)
        assertRejected(response(blockJson(0, 22, 6), ok))
        assertRejected(response(blockJson(6, 22, 6), ok))
        assertRejected(response(blockJson(-1, 22, 6), ok))
        assertRejected(response(blockJson(3, 25, 6), ok))
        assertRejected(response(blockJson(3, 22, -1), ok))
        assertRejected(response(ok, blockJson(9, 6, 7)))
    }

    // ---- Freshness states (cached / stale / unverified) ----

    private fun coveringBlock(): Block {
        val h = SloTime.hourOfDay()
        return Block(3, h, (h + 1) % 24, "x", "y", emptyList())
    }

    private fun notCoveringBlock(): Block {
        val h = SloTime.hourOfDay()
        return Block(3, (h + 2) % 24, (h + 3) % 24, "x", "y", emptyList())
    }

    @Test
    fun freshData_isFresh() {
        val b = coveringBlock()
        assertEquals(DataState.FRESH, Repo.dataState(Info(b, b, System.currentTimeMillis(), true)))
    }

    @Test
    fun storedData_stillCoveringNow_isCached() {
        val b = coveringBlock()
        val hourAgo = System.currentTimeMillis() - 3_600_000L
        assertEquals(DataState.CACHED, Repo.dataState(Info(b, b, hourAgo, false)))
    }

    @Test
    fun staleOrExpiredOrFutureData_isUnverified() {
        val now = System.currentTimeMillis()
        val b = coveringBlock()
        assertEquals(
            DataState.UNVERIFIED,
            Repo.dataState(Info(b, b, now - 13 * 3_600_000L, false))
        ) // too old
        assertEquals(
            DataState.UNVERIFIED,
            Repo.dataState(Info(b, b, now + 3_600_000L, false))
        ) // clock went back
        assertEquals(DataState.UNVERIFIED, Repo.dataState(Info(b, b, 0L, false))) // no timestamp
        val expired = notCoveringBlock()
        assertEquals(
            DataState.UNVERIFIED,
            Repo.dataState(Info(expired, expired, now - 60_000L, false))
        ) // block already over
    }

    @Test
    fun recentMeansUpdatedWithinThirtyMinutes() {
        val b = coveringBlock()
        val now = System.currentTimeMillis()
        assertTrue(Repo.isRecent(Info(b, b, now - 29 * 60_000L, false)))
        assertFalse(Repo.isRecent(Info(b, b, now - 31 * 60_000L, false)))
        assertFalse(Repo.isRecent(Info(b, b, now + 60_000L, false)))
    }
}

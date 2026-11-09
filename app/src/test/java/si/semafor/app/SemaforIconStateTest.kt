package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemaforIconStateTest {

    @Test
    fun blocks1to5AreLitWithTheirOwnBlockId() {
        for (id in 1..5) {
            val s = SemaforIconState.forBlock(id)
            assertTrue("block $id must be lit", s.lit)
            assertEquals(id, s.blockId)
        }
    }

    @Test
    fun unknownOrMissingBlockIsOff() {
        for (id in listOf<Int?>(null, 0, 6, -1, 99, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val s = SemaforIconState.forBlock(id)
            assertFalse("block $id must be off", s.lit)
            assertNull(s.blockId)
        }
    }

    @Test
    fun colourComesFromRepoSoItMatchesTheCards() {
        // The icon asks Repo.color for the lit colour; every valid block has a colour that differs from "unknown".
        val unknown = Repo.color(0)
        for (id in 1..5) {
            assertTrue(
                "block $id colour must differ from the unknown colour",
                Repo.color(id) != unknown
            )
        }
    }
}

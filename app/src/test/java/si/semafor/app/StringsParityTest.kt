package si.semafor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text exists in Slovenian and English, with the same placeholders, and none is left empty. */
class StringsParityTest {
    private val placeholder = Regex("%(\\d+\\$)?[sdf]")

    @Test
    fun sameKeysInBothLanguages() {
        assertEquals(TestStrings.SL.strings.keys - TestStrings.EN.strings.keys, emptySet<String>())
        assertEquals(TestStrings.EN.strings.keys - TestStrings.SL.strings.keys, emptySet<String>())
        assertEquals(TestStrings.SL.plurals.keys, TestStrings.EN.plurals.keys)
    }

    @Test
    fun samePlaceholdersInBothLanguages() {
        for ((key, sl) in TestStrings.SL.strings) {
            val en = TestStrings.EN.strings.getValue(key)
            assertEquals("Placeholders of $key", placeholder.findAll(sl).map { it.value }.sorted().toList(),
                placeholder.findAll(en).map { it.value }.sorted().toList())
        }
    }

    @Test
    fun noEmptyTexts() {
        // home_best_suffix is intentionally empty in English ("at 14:00" needs no word after the time).
        val allowedEmpty = setOf("home_best_suffix")
        for (lang in listOf(TestStrings.SL, TestStrings.EN)) {
            for ((key, value) in lang.strings) if (key !in allowedEmpty) assertTrue("Empty $key", value.isNotBlank())
        }
    }

    @Test
    fun slovenianPluralsHaveAllForms() {
        for ((key, forms) in TestStrings.SL.plurals) {
            assertEquals("Plural forms of $key", setOf("one", "two", "few", "other"), forms.keys)
        }
    }
}

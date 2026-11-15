package si.semafor.app

/** Texts of the home screen that depend only on numbers. Pure Kotlin (unit tested). */
object HomeFormat {
    /** "0 h 5 min 3 s" for a positive duration in milliseconds. */
    fun duration(diffMs: Long): String {
        val totalSec = diffMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "$h h $m min $s s"
    }

    private val HIGH_SEASON = Regex("(?i)visoka sezona")
    private val LOW_SEASON = Regex("(?i)nizka sezona")

    /** The API names the seasons "visoka"/"nizka"; the app consistently says "višja"/"nižja" sezona. */
    fun seasonLabel(raw: String): String =
        raw.replace(HIGH_SEASON, "višja sezona").replace(LOW_SEASON, "nižja sezona")
}

package si.semafor.app.mojelektro

import android.content.Context
import si.semafor.app.Block
import si.semafor.app.OfficialBlocks
import si.semafor.app.Repo
import si.semafor.app.SloTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Date

/**
 * Tariff block of every hour of a given day. For the years the Energy Agency has published a table for
 * ([OfficialBlocks], 2026 and 2027) the built-in official table is used: no request, and future days are known as
 * well. For any other year the URO API is asked at the start of every block, which also gives the hour the block
 * ends, until the whole day is covered (about 5-7 requests per day); such schedules of past days are kept.
 *
 * Public data only (no personal data): stored in plain private preferences.
 */
internal class BlockSchedules(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Schedules of [dates]; a day whose schedule cannot be obtained (no connection) maps to null.
     * [onProgress] reports (done, total) while days are requested. Blocking: call from Dispatchers.IO.
     */
    fun forDates(dates: Collection<LocalDate>, onProgress: (Int, Int) -> Unit = { _, _ -> }): Map<LocalDate, IntArray?> {
        val result = HashMap<LocalDate, IntArray?>()
        val missing = ArrayList<LocalDate>()
        for (d in dates) {
            OfficialBlocks.scheduleFor(d)?.let {
                result[d] = it
                continue
            }
            val cached = prefs.getString(d.toString(), null)?.let(::decode)
            if (cached != null) result[d] = cached else missing += d
        }
        missing.sorted().forEachIndexed { i, d ->
            onProgress(i, missing.size)
            val schedule = build(d) { millis -> Repo.fetchAt(Date(millis))?.now }
            result[d] = schedule
            // Only days that have already begun are kept (a future day's schedule could still be changed officially).
            if (schedule != null && !d.isAfter(SloTime.today())) prefs.edit().putString(d.toString(), encode(schedule)).apply()
        }
        return result
    }

    companion object {
        private const val PREFS = "block_schedules"

        /**
         * Walks through [date] block by block: [fetch] returns the block valid at an instant. Returns 24 block ids
         * (index = Slovenian hour), or null if any request failed or answered with a block that does not contain
         * the asked hour. Pure apart from [fetch] (unit tested).
         */
        fun build(date: LocalDate, fetch: (Long) -> Block?): IntArray? {
            val result = IntArray(24)
            var hour = 0
            var guard = 0
            while (hour < 24) {
                if (guard++ > 24) return null
                // Half a minute after the hour; a time skipped by the spring clock change resolves to the next hour.
                val at = ZonedDateTime.of(date, LocalTime.of(hour, 0, 30), SloTime.ZONE).toInstant().toEpochMilli()
                val block = fetch(at) ?: return null
                val hourAt = SloTime.hourOfDay(at)
                if (!SloTime.coversHour(hourAt, block.from, block.to)) return null
                val to = block.to % 24
                val end = if (to > hourAt) to else 24 // a block that runs over midnight fills the rest of the day
                for (h in hour until end) result[h] = block.id
                hour = end
            }
            return result
        }

        internal fun encode(schedule: IntArray): String = schedule.joinToString(",")

        internal fun decode(text: String): IntArray? =
            text.split(",").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 24 && it.all { id -> id in 1..5 } }
                ?.toIntArray()
    }
}

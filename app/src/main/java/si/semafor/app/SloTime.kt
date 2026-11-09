package si.semafor.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Single place for Slovenian local time.
 *
 * Tariff blocks are defined in Slovenian wall-clock hours, so every "local" calculation and every
 * displayed time uses Europe/Ljubljana (CET/CEST handled by the tz database) and never the device
 * time zone. No fixed UTC offsets are used. All instants are plain epoch milliseconds.
 */
object SloTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Ljubljana")
    private val LOCALE: Locale = Locale.forLanguageTag("sl-SI")

    private val HM = DateTimeFormatter.ofPattern("HH:mm", LOCALE).withZone(ZONE)
    private val DATE = DateTimeFormatter.ofPattern("d. M. yyyy", LOCALE).withZone(ZONE)
    private val DATE_TIME =
        DateTimeFormatter.ofPattern("d. M. yyyy 'ob' HH:mm", LOCALE).withZone(ZONE)
    private val TOP_DATE = DateTimeFormatter.ofPattern("EEE, d. MMM", LOCALE).withZone(ZONE)
    private val SHORT_STAMP = DateTimeFormatter.ofPattern("d. M. HH:mm", LOCALE).withZone(ZONE)
    private val API_TIMESTAMP =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.US).withZone(ZONE)

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate()

    fun hourOfDay(millis: Long = System.currentTimeMillis()): Int =
        Instant.ofEpochMilli(millis).atZone(ZONE).hour

    fun formatHm(millis: Long): String = HM.format(Instant.ofEpochMilli(millis))
    fun formatDate(millis: Long): String = DATE.format(Instant.ofEpochMilli(millis))
    fun formatDateTime(millis: Long): String = DATE_TIME.format(Instant.ofEpochMilli(millis))
    fun formatTopDate(millis: Long): String = TOP_DATE.format(Instant.ofEpochMilli(millis))

    /** Compact stamp for small widgets: "HH:mm" if it is today (Slovenian date), otherwise "d. M. HH:mm". */
    fun formatStamp(millis: Long, today: LocalDate = today()): String =
        if (dateOf(millis) == today) formatHm(millis) else SHORT_STAMP.format(
            Instant.ofEpochMilli(
                millis
            )
        )

    /** Timestamp for the official API: Slovenian wall-clock time, independent of the device zone. */
    fun formatApiTimestamp(millis: Long): String =
        API_TIMESTAMP.format(Instant.ofEpochMilli(millis))

    /**
     * First instant strictly after [afterMillis] at which the Slovenian wall clock shows [hour]:00.
     * Day arithmetic is done on local dates, so 23 h / 25 h days at the DST changes are correct.
     * A local time skipped by the spring change resolves to the first valid instant after the gap;
     * an ambiguous local time in autumn resolves to its first occurrence.
     */
    fun nextStartAfter(afterMillis: Long, hour: Int): Long {
        val h = LocalTime.of(Math.floorMod(hour, 24), 0)
        val date = Instant.ofEpochMilli(afterMillis).atZone(ZONE).toLocalDate()
        var start = ZonedDateTime.of(date, h, ZONE).toInstant().toEpochMilli()
        if (start <= afterMillis) {
            start = ZonedDateTime.of(date.plusDays(1), h, ZONE).toInstant().toEpochMilli()
        }
        return start
    }

    /** Latest instant at or before [atMillis] at which the Slovenian wall clock showed [hour]:00 (start of the block that is running). */
    fun previousStartAtOrBefore(atMillis: Long, hour: Int): Long {
        val h = LocalTime.of(Math.floorMod(hour, 24), 0)
        val date = Instant.ofEpochMilli(atMillis).atZone(ZONE).toLocalDate()
        var start = ZonedDateTime.of(date, h, ZONE).toInstant().toEpochMilli()
        if (start > atMillis) {
            start = ZonedDateTime.of(date.minusDays(1), h, ZONE).toInstant().toEpochMilli()
        }
        return start
    }

    /** True if [hour] lies in the block interval [from, to) (wrapping over midnight, e.g. 22-6). */
    fun coversHour(hour: Int, from: Int, to: Int): Boolean {
        val f = from % 24
        val t = to % 24
        return if (f < t) hour in f until t else if (f > t) (hour >= f || hour < t) else true
    }
}

package si.semafor.app.mojelektro

import si.semafor.app.SloTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import kotlin.math.abs

/** What the timestamp of a 15-minute reading marks. Determined from the data itself, never assumed. */
enum class IntervalLabel {
    /** The timestamp is the start of the 15 minutes. */
    START,

    /** The timestamp is the end of the 15 minutes. */
    END,

    /** Could not be determined; readings are then not assigned to tariff blocks. */
    UNKNOWN
}

/** How close a 15-minute average power came to the agreed power of its block. */
enum class PowerLevel { NORMAL, NEAR, OVER, UNKNOWN }

/** One 15-minute interval of a day. [kw] = average power of the interval = kWh / 0.25 h. */
data class IntervalPoint(
    val startMillis: Long,
    val kwh: Double,
    val kw: Double,
    /** Tariff block 1..5 of the interval (official URO schedule of that day); null = unknown. */
    val blockId: Int?,
    /** Agreed power of that block, kW; null = unknown. */
    val agreedKw: Double?
) {
    val level: PowerLevel
        get() = when {
            agreedKw == null || agreedKw <= 0.0 -> PowerLevel.UNKNOWN
            kw > agreedKw -> PowerLevel.OVER
            kw >= UsageAnalysis.NEAR_RATIO * agreedKw -> PowerLevel.NEAR
            else -> PowerLevel.NORMAL
        }
}

/** The 15-minute intervals of one day, in time order. [peakIndex] = interval with the highest average power. */
data class DayProfile(val date: LocalDate, val intervals: List<IntervalPoint>, val peakIndex: Int?) {
    val totalKwh: Double get() = intervals.sumOf { it.kwh }
}

/** Energy of one day per tariff block. [kwhByBlock] index 0 = block 1. */
data class DailyBlocks(val date: LocalDate, val kwhByBlock: List<Double>, val unassignedKwh: Double) {
    val totalKwh: Double get() = kwhByBlock.sum() + unassignedKwh
}

/** Taken (A+) and delivered (A-) energy per 15 minutes of one day, aligned by interval start. */
data class ProductionDay(
    val date: LocalDate,
    val startMillis: List<Long>,
    val importKwh: List<Double>,
    val exportKwh: List<Double>
) {
    val totalImport: Double get() = importKwh.sum()
    val totalExport: Double get() = exportKwh.sum()
}

/**
 * Pure analysis of Moj Elektro readings (unit tested). Tariff blocks are NEVER derived from rules written into the app:
 * the block of every hour comes from the official URO schedule of that day ([BlockSchedules]).
 */
object UsageAnalysis {
    const val INTERVAL_MS = 15 * 60 * 1000L

    /** "Approached the agreed power" = at least this share of it (a display threshold, not a tariff rule). */
    const val NEAR_RATIO = 0.9

    /** The agreed power valid at [nowMillis]: valid, started, not ended; the latest start wins. */
    fun currentAgreedPower(list: List<AgreedPower>, nowMillis: Long): AgreedPower? =
        list.filter { it.valid != false }
            .mapNotNull { p -> p.validFrom?.let(::millis)?.let { from -> p to from } }
            .filter { (p, from) ->
                val to = p.validTo?.let(::millis)
                from <= nowMillis && (to == null || nowMillis <= to)
            }
            .maxByOrNull { it.second }
            ?.first

    /**
     * Decides whether 15-minute timestamps mark the start or the end of their interval.
     *
     * 1. Data check: for every pair of consecutive daily meter states, the 15-minute energy between them is summed
     *    once as "start" and once as "end" labelled; the variant closer to the meter's own difference gets a vote.
     * 2. If there is no usable daily state: the first reading of the requested start day at 00:00 means "start",
     *    at 00:15 means "end".
     * 3. Otherwise [IntervalLabel.UNKNOWN].
     */
    fun detectLabel(fifteen: List<Reading>, dailyStates: List<Reading>, requestedStart: LocalDate): IntervalLabel {
        val readings = fifteen.filter { it.value != null }.sortedBy { it.timestampMillis }
        if (readings.isEmpty()) return IntervalLabel.UNKNOWN

        var startVotes = 0
        var endVotes = 0
        val states = dailyStates.filter { it.value != null }.sortedBy { it.timestampMillis }
        for (i in 0 until states.size - 1) {
            val t0 = states[i].timestampMillis
            val t1 = states[i + 1].timestampMillis
            val diff = states[i + 1].value!!.toDouble() - states[i].value!!.toDouble()
            if (t1 <= t0 || diff < 0) continue
            val expected = ((t1 - t0) / INTERVAL_MS).toInt()
            val asStart = readings.filter { it.timestampMillis >= t0 && it.timestampMillis < t1 }
            val asEnd = readings.filter { it.timestampMillis > t0 && it.timestampMillis <= t1 }
            if (asStart.size != expected || asEnd.size != expected) continue
            val errStart = abs(asStart.sumOf { it.value!!.toDouble() } - diff)
            val errEnd = abs(asEnd.sumOf { it.value!!.toDouble() } - diff)
            // Only a clear difference counts, and only if the better variant really matches the meter.
            val tolerance = maxOf(1.0, diff * 0.02)
            if (abs(errStart - errEnd) < 0.0005 || minOf(errStart, errEnd) > tolerance) continue
            if (errStart < errEnd) startVotes++ else endVotes++
        }
        if (startVotes > endVotes) return IntervalLabel.START
        if (endVotes > startVotes) return IntervalLabel.END

        val first = Instant.ofEpochMilli(readings.first().timestampMillis).atZone(SloTime.ZONE)
        if (first.toLocalDate() == requestedStart) {
            val minuteOfDay = first.hour * 60 + first.minute
            if (minuteOfDay == 0) return IntervalLabel.START
            if (minuteOfDay == 15) return IntervalLabel.END
        }
        return IntervalLabel.UNKNOWN
    }

    /** Start of the interval a reading belongs to ([IntervalLabel.UNKNOWN] uses the timestamp as it is). */
    fun intervalStart(timestampMillis: Long, label: IntervalLabel): Long =
        if (label == IntervalLabel.END) timestampMillis - INTERVAL_MS else timestampMillis

    /** Readings with a value, grouped by the Slovenian date of their interval start; each list in time order. */
    fun groupByDate(readings: List<Reading>, label: IntervalLabel): Map<LocalDate, List<Pair<Long, Double>>> =
        readings.mapNotNull { r -> r.value?.let { intervalStart(r.timestampMillis, label) to it.toDouble() } }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .groupBy { SloTime.dateOf(it.first) }

    /** Number of 15-minute intervals of a Slovenian calendar day (92, 96 or 100 at the clock changes). */
    fun expectedIntervals(date: LocalDate): Int {
        val start = ZonedDateTime.of(date, LocalTime.MIDNIGHT, SloTime.ZONE)
        val end = ZonedDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT, SloTime.ZONE)
        return (Duration.between(start, end).toMinutes() / 15).toInt()
    }

    /** The latest day that has all its intervals. */
    fun lastCompleteDay(byDate: Map<LocalDate, List<Pair<Long, Double>>>): LocalDate? =
        byDate.filter { (date, list) -> list.size >= expectedIntervals(date) }.keys.maxOrNull()

    /** Block of the hour of [millis] from the day's official schedule (24 entries, index = Slovenian hour). */
    fun blockAt(millis: Long, schedule: IntArray?): Int? =
        schedule?.getOrNull(SloTime.hourOfDay(millis))?.takeIf { it in 1..5 }

    /** The intervals of one day with block, agreed power and the peak. Blocks are left out for an unknown label. */
    fun dayProfile(
        date: LocalDate,
        readings: List<Pair<Long, Double>>,
        schedule: IntArray?,
        agreed: AgreedPower?,
        label: IntervalLabel
    ): DayProfile {
        val points = readings.map { (start, kwh) ->
            val block = if (label == IntervalLabel.UNKNOWN) null else blockAt(start, schedule)
            IntervalPoint(
                startMillis = start,
                kwh = kwh,
                kw = kwh * 4.0,
                blockId = block,
                agreedKw = block?.let { agreed?.blockKw?.getOrNull(it - 1)?.toDouble() }
            )
        }
        val peak = points.indices.maxByOrNull { points[it].kw }?.takeIf { points[it].kw > 0.0 }
        return DayProfile(date, points, peak)
    }

    /** Energy per day and tariff block; energy whose block is not known is counted as unassigned. */
    fun dailyByBlocks(
        byDate: Map<LocalDate, List<Pair<Long, Double>>>,
        schedules: Map<LocalDate, IntArray?>,
        label: IntervalLabel
    ): List<DailyBlocks> = byDate.keys.sorted().map { date ->
        val sums = DoubleArray(5)
        var unassigned = 0.0
        for ((start, kwh) in byDate.getValue(date)) {
            val block = if (label == IntervalLabel.UNKNOWN) null else blockAt(start, schedules[date])
            if (block != null) sums[block - 1] += kwh else unassigned += kwh
        }
        DailyBlocks(date, sums.toList(), unassigned)
    }

    /** Highest interval of [profile] inside block [blockId]. */
    fun peakInBlock(profile: DayProfile, blockId: Int): IntervalPoint? =
        profile.intervals.filter { it.blockId == blockId }.maxByOrNull { it.kw }

    /** Taken and delivered energy of [date], aligned by interval start (missing values count as 0). */
    fun productionDay(
        date: LocalDate,
        imports: List<Pair<Long, Double>>,
        exports: List<Pair<Long, Double>>
    ): ProductionDay {
        val imp = imports.toMap()
        val exp = exports.toMap()
        val starts = (imp.keys + exp.keys).sorted()
        return ProductionDay(date, starts, starts.map { imp[it] ?: 0.0 }, starts.map { exp[it] ?: 0.0 })
    }

    private fun millis(iso: String): Long? = try {
        OffsetDateTime.parse(iso.trim()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

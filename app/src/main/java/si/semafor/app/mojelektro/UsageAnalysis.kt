package si.semafor.app.mojelektro

import androidx.compose.runtime.Immutable
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
@Immutable
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
@Immutable
data class DayProfile(val date: LocalDate, val intervals: List<IntervalPoint>, val peakIndex: Int?) {
    val totalKwh: Double get() = intervals.sumOf { it.kwh }
}

/** Energy of one day per tariff block. [kwhByBlock] index 0 = block 1. */
@Immutable
data class DailyBlocks(val date: LocalDate, val kwhByBlock: List<Double>, val unassignedKwh: Double) {
    val totalKwh: Double get() = kwhByBlock.sum() + unassignedKwh
}

/** Taken (A+) and delivered (A-) energy per 15 minutes of one day, aligned by interval start. */
@Immutable
data class ProductionDay(
    val date: LocalDate,
    val startMillis: List<Long>,
    val importKwh: List<Double>,
    val exportKwh: List<Double>
) {
    val totalImport: Double get() = importKwh.sum()
    val totalExport: Double get() = exportKwh.sum()
}

/** A run of consecutive intervals of a day in the same tariff block ([blockId] null = block not known). */
@Immutable
data class BlockSegment(val blockId: Int?, val fromIndex: Int, val toIndexExclusive: Int)

/** The highest 15-minute interval of one day and how many intervals came close to / above the agreed power. */
@Immutable
data class DailyPeak(
    val date: LocalDate,
    val peak: IntervalPoint,
    val nearCount: Int,
    val overCount: Int
) {
    /** Peak as a share of the agreed power of its block (1.0 = 100 %); null if the agreed power is not known. */
    val utilization: Double? get() = UsageAnalysis.utilization(peak)
}

/** Per tariff block: agreed power, the highest interval in the period and how often the agreed power was exceeded. */
@Immutable
data class BlockPowerRow(
    val blockId: Int,
    val agreedKw: Double?,
    val peak: IntervalPoint?,
    val overCount: Int,
    val intervalCount: Int
) {
    val utilization: Double? get() = peak?.let(UsageAnalysis::utilization)
}

/** Totals of the registers in the chosen period. */
@Immutable
data class RegisterSummary(
    /** Taken energy (A+), kWh. */
    val importKwh: Double,
    /** Delivered energy (A-), kWh; null if the metering point delivers no energy. */
    val exportKwh: Double?,
    /** 15-minute readings with a value. */
    val intervalCount: Int,
    /** Readings the API marked with a status code (estimated, replaced, ...). */
    val flaggedCount: Int,
    /** Status code -> number of readings that carry it (a reading may carry several). */
    val flaggedByType: Map<String, Int>,
    /** Readings without a usable value. */
    val missingCount: Int
)

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

        // Sorted timestamps + prefix sums: every window below is two binary searches instead of a scan of all
        // readings (a month of 15-minute values against 30 daily states).
        val ts = LongArray(readings.size) { readings[it].timestampMillis }
        val prefix = DoubleArray(readings.size + 1)
        for (i in readings.indices) prefix[i + 1] = prefix[i] + readings[i].value!!.toDouble()

        var startVotes = 0
        var endVotes = 0
        val states = dailyStates.filter { it.value != null }.sortedBy { it.timestampMillis }
        for (i in 0 until states.size - 1) {
            val t0 = states[i].timestampMillis
            val t1 = states[i + 1].timestampMillis
            val diff = states[i + 1].value!!.toDouble() - states[i].value!!.toDouble()
            if (t1 <= t0 || diff < 0) continue
            val expected = ((t1 - t0) / INTERVAL_MS).toInt()
            // "start" labelled: t0 <= t < t1; "end" labelled: t0 < t <= t1.
            val startFrom = firstIndex(ts, t0, inclusive = true)
            val startTo = firstIndex(ts, t1, inclusive = true)
            val endFrom = firstIndex(ts, t0, inclusive = false)
            val endTo = firstIndex(ts, t1, inclusive = false)
            if (startTo - startFrom != expected || endTo - endFrom != expected) continue
            val errStart = abs(prefix[startTo] - prefix[startFrom] - diff)
            val errEnd = abs(prefix[endTo] - prefix[endFrom] - diff)
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

    /** First index of sorted [ts] with a value >= [t] ([inclusive]) or > [t]; [ts].size if there is none. */
    private fun firstIndex(ts: LongArray, t: Long, inclusive: Boolean): Int {
        var lo = 0
        var hi = ts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (if (inclusive) ts[mid] < t else ts[mid] <= t) lo = mid + 1 else hi = mid
        }
        return lo
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

    /** [point] power as a share of the agreed power of its block (1.0 = 100 %); null if that is not known. */
    fun utilization(point: IntervalPoint): Double? =
        point.agreedKw?.takeIf { it > 0.0 }?.let { point.kw / it }

    /**
     * The intervals of [profile] cut into runs of the same tariff block, in time order: the coloured bands of the
     * combined chart (official URO schedule of the day under the user's own 15-minute power).
     */
    fun blockSegments(profile: DayProfile): List<BlockSegment> {
        val points = profile.intervals
        if (points.isEmpty()) return emptyList()
        val result = ArrayList<BlockSegment>()
        var start = 0
        for (i in 1..points.size) {
            if (i == points.size || points[i].blockId != points[start].blockId) {
                result += BlockSegment(points[start].blockId, start, i)
                start = i
            }
        }
        return result
    }

    /**
     * Index of the interval under a touch at [x] on a chart whose [count] intervals are spread evenly over
     * [left]..[left] + [width]; clamped to the first / last interval (the finger may leave the chart while scrubbing).
     */
    fun indexAt(x: Float, left: Float, width: Float, count: Int): Int {
        if (count <= 0 || width <= 0f) return 0
        return (((x - left) / width) * count).toInt().coerceIn(0, count - 1)
    }

    /** The highest interval of every day that has one with power above zero, in date order. */
    fun dailyPeaks(profiles: List<DayProfile>): List<DailyPeak> =
        profiles.sortedBy { it.date }.mapNotNull { day ->
            val peak = day.peakIndex?.let { day.intervals[it] } ?: return@mapNotNull null
            DailyPeak(
                date = day.date,
                peak = peak,
                nearCount = day.intervals.count { it.level == PowerLevel.NEAR },
                overCount = day.intervals.count { it.level == PowerLevel.OVER }
            )
        }

    /** One row per tariff block 1..5 over all [profiles]: agreed power, highest interval, excesses. */
    fun blockPowerRows(profiles: List<DayProfile>, agreed: AgreedPower?): List<BlockPowerRow> {
        val all = profiles.flatMap { it.intervals }
        return (1..5).map { block ->
            val inBlock = all.filter { it.blockId == block }
            BlockPowerRow(
                blockId = block,
                agreedKw = agreed?.blockKw?.getOrNull(block - 1)?.toDouble(),
                peak = inBlock.maxByOrNull { it.kw }?.takeIf { it.kw > 0.0 },
                overCount = inBlock.count { it.level == PowerLevel.OVER },
                intervalCount = inBlock.size
            )
        }
    }

    /**
     * Totals of the taken ([imports]) and delivered ([exports]) 15-minute readings whose interval starts on a date
     * from [from] to [to] (null = no end). [hasExport] = the metering point delivers energy (otherwise the delivered
     * total is null).
     */
    fun registerSummary(
        imports: List<Reading>,
        exports: List<Reading>,
        label: IntervalLabel,
        from: LocalDate,
        hasExport: Boolean,
        to: LocalDate? = null
    ): RegisterSummary {
        fun inRange(r: Reading): Boolean {
            val d = SloTime.dateOf(intervalStart(r.timestampMillis, label))
            return !d.isBefore(from) && (to == null || !d.isAfter(to))
        }
        val imp = imports.filter(::inRange).distinctBy { it.timestampMillis }
        val exp = exports.filter(::inRange).distinctBy { it.timestampMillis }
        return RegisterSummary(
            importKwh = imp.sumOf { it.value?.toDouble() ?: 0.0 },
            exportKwh = if (hasExport) exp.sumOf { it.value?.toDouble() ?: 0.0 } else null,
            intervalCount = imp.count { it.value != null },
            flaggedCount = imp.count { !it.isValid },
            flaggedByType = imp.flatMap { it.qualities }.groupingBy { it }.eachCount(),
            missingCount = imp.count { it.value == null }
        )
    }

    private fun millis(iso: String): Long? = try {
        OffsetDateTime.parse(iso.trim()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

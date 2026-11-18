package si.semafor.app.planner

import si.semafor.app.SloTime
import si.semafor.app.pricing.EnergyPriceCalculator
import si.semafor.app.pricing.KwhPrice
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * The smart day planner: for every appliance it tries every start in the user's window (now and every quarter hour)
 * and picks the one with the lowest variable cost, using the final price of every hour ([KwhPrice]: energy of the
 * chosen supplier and tariff, network charge for energy of the block, per-kWh levies and VAT). Fixed monthly items
 * (network charge for power, supplier fee) do not change when a run is moved, so they are not part of the comparison.
 *
 * Several appliances are planned one after another, the biggest first, and a start is avoided if the planned load
 * (other consumption + appliances) would exceed the agreed power of the block. Pure Kotlin, unit tested.
 */
object DayPlanner {
    const val STEP_MS = 15 * 60 * 1000L
    private const val HOUR_MS = 3_600_000L

    /** Minimum distance of the alternative start from the recommended one. */
    const val ALT_MIN_DISTANCE_MS = HOUR_MS

    /**
     * The window [start, end) for [device] seen at [nowMillis]; [horizonEnd] = end of the known prices. With [clip]
     * the end is cut to the known prices; null if the run does not fit.
     */
    fun window(device: PlannerDevice, nowMillis: Long, horizonEnd: Long, clip: Boolean = true): Pair<Long, Long>? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(SloTime.ZONE)
        val end = device.deadlineMin?.let { nextOccurrence(now, it).toInstant().toEpochMilli() } ?: horizonEnd
        val start = device.earliestStartMin?.let { e ->
            if (device.deadlineMin != null) {
                // The latest "earliest start" before the deadline (an evening start for a morning deadline).
                val endZ = Instant.ofEpochMilli(end).atZone(SloTime.ZONE)
                var s = atMinute(endZ, e)
                if (!s.isBefore(endZ)) s = s.minusDays(1)
                maxOf(nowMillis, s.toInstant().toEpochMilli())
            } else {
                // Without a deadline: "not before e"; if e passed less than 12 hours ago, now is inside the window.
                val last = atMinute(now, e).let { if (it.isAfter(now)) it.minusDays(1) else it }
                if (nowMillis - last.toInstant().toEpochMilli() < 12 * HOUR_MS) nowMillis
                else nextOccurrence(now, e).toInstant().toEpochMilli()
            }
        } ?: nowMillis
        val clippedEnd = if (clip) minOf(end, horizonEnd) else end
        return if (clippedEnd - start >= device.durationMin * 60_000L) start to clippedEnd else null
    }

    private fun atMinute(day: ZonedDateTime, minute: Int): ZonedDateTime =
        ZonedDateTime.of(day.toLocalDate(), LocalTime.of(minute / 60, minute % 60), SloTime.ZONE)

    /** First moment strictly after [now] at which the clock shows [minute]. */
    private fun nextOccurrence(now: ZonedDateTime, minute: Int): ZonedDateTime {
        val today = atMinute(now, minute)
        return if (today.isAfter(now)) today else atMinute(now.plusDays(1), minute)
    }

    /** Possible starts: the window start itself and every following quarter hour, while the run fits. */
    fun candidateStarts(device: PlannerDevice, windowStart: Long, windowEnd: Long, prices: List<KwhPrice>): List<Long> {
        if (prices.isEmpty() || device.durationMin <= 0) return emptyList()
        val dur = device.durationMin * 60_000L
        val first = maxOf(windowStart, prices.first().startMillis)
        val last = minOf(windowEnd, prices.last().endMillis) - dur
        if (last < first) return emptyList()
        val starts = ArrayList<Long>()
        starts += first
        var q = (first / STEP_MS + 1) * STEP_MS
        while (q <= last) {
            starts += q
            q += STEP_MS
        }
        return starts.filter { s ->
            device.days.isEmpty() || SloTime.dateOf(s).dayOfWeek in device.days
        }
    }

    /** Variable cost (EUR with VAT) of one run of [device] from [start]; null if outside the known prices. */
    fun runCost(device: PlannerDevice, prices: List<KwhPrice>, start: Long): Double? =
        EnergyPriceCalculator.runCost(prices, start, device.runKwh, device.averageKw)

    /** Blocks the run [start, end) touches, in order (repeated blocks merged). */
    fun blocksOf(prices: List<KwhPrice>, start: Long, end: Long): List<Int> {
        val out = ArrayList<Int>()
        for (p in prices) if (p.endMillis > start && p.startMillis < end && out.lastOrNull() != p.block) out += p.block
        return out
    }

    /**
     * Plans [devices] (the active ones) with the hourly [prices] starting at the current hour. [limitAt] gives the
     * agreed power and the other consumption for the 15-minute slot starting at a moment.
     */
    fun plan(
        devices: List<PlannerDevice>,
        prices: List<KwhPrice>,
        nowMillis: Long,
        limitAt: (Long) -> SlotLimit
    ): List<PlanResult> {
        val active = devices.filter { it.active }
        val horizonEnd = prices.lastOrNull()?.endMillis ?: nowMillis
        // Planned load per slot start (kW) and the appliances in it.
        val load = HashMap<Long, Double>()
        val users = HashMap<Long, MutableList<String>>()
        val results = HashMap<String, PlanResult>()

        // The biggest runs first: they have the fewest good options.
        for (device in active.sortedByDescending { it.runKwh * 1000 + it.powerKw }) {
            if (prices.isEmpty()) {
                results[device.id] = PlanResult(device, PlanStatus.NO_PRICES)
                continue
            }
            val win = window(device, nowMillis, horizonEnd)
            val starts = if (win == null) emptyList() else candidateStarts(device, win.first, win.second, prices)
            val options = starts.mapNotNull { s -> runCost(device, prices, s)?.let { s to it } }
            if (options.isEmpty()) {
                // The run does not fit the user's window at all, or not on an allowed day: NO_WINDOW. It would fit, but
                // the prices are not known that far: NO_PRICES.
                val fitsWithoutPrices = window(device, nowMillis, horizonEnd, clip = false) != null
                val status = if (!fitsWithoutPrices || (win != null && starts.isEmpty())) PlanStatus.NO_WINDOW else PlanStatus.NO_PRICES
                results[device.id] = PlanResult(device, status)
                continue
            }
            val dur = device.durationMin * 60_000L

            fun slotsOf(s: Long): List<Long> {
                val list = ArrayList<Long>()
                var slot = s / STEP_MS * STEP_MS
                while (slot < s + dur) {
                    list += slot
                    slot += STEP_MS
                }
                return list
            }

            fun overPower(s: Long): Boolean = slotsOf(s).any { slot ->
                val lim = limitAt(slot)
                val agreed = lim.agreedKw ?: return@any false
                lim.baseKw + (load[slot] ?: 0.0) + device.powerKw > agreed + 1e-9
            }

            val cheapest = pickBest(options)!!
            val feasible = options.filter { !overPower(it.first) }
            val chosen = pickBest(feasible) ?: cheapest
            val over = feasible.isEmpty()
            val isNow = abs(chosen.first - nowMillis) < 1000
            val reason = when {
                over -> PlanReason.CHEAPEST_OVER_POWER
                cheapest.second < chosen.second - SAME_COST -> PlanReason.CHEAPEST_WITHIN_POWER
                isNow -> PlanReason.NOW_IS_CHEAPEST
                else -> PlanReason.CHEAPEST
            }
            val alt = pickBest((if (over) options else feasible).filter { abs(it.first - chosen.first) >= ALT_MIN_DISTANCE_MS })
            val costNow = runCost(device, prices, nowMillis)

            // Book the run and look at the load during it.
            val overlaps = LinkedHashSet<String>()
            var peak = 0.0
            var peakAgreed: Double? = null
            for (slot in slotsOf(chosen.first)) {
                users[slot]?.let { overlaps += it }
                val lim = limitAt(slot)
                val total = lim.baseKw + (load[slot] ?: 0.0) + device.powerKw
                if (total > peak) {
                    peak = total
                    peakAgreed = lim.agreedKw
                }
                load[slot] = (load[slot] ?: 0.0) + device.powerKw
                users.getOrPut(slot) { ArrayList() } += device.name
            }
            results[device.id] = PlanResult(
                device = device,
                status = PlanStatus.OK,
                startMillis = chosen.first,
                endMillis = chosen.first + dur,
                cost = chosen.second,
                reason = reason,
                costNow = costNow,
                altStartMillis = alt?.first,
                altCost = alt?.second,
                overlapsWith = overlaps.toList(),
                overPower = over,
                peakKw = peak,
                peakAgreedKw = peakAgreed,
                blocks = blocksOf(prices, chosen.first, chosen.first + dur)
            )
        }
        // Earlier runs get the overlap with later-planned ones as well.
        val withOverlaps = results.values.map { r ->
            if (r.status != PlanStatus.OK) r else {
                val others = results.values.filter {
                    it !== r && it.status == PlanStatus.OK && it.startMillis < r.endMillis && r.startMillis < it.endMillis
                }.map { it.device.name }
                r.copy(overlapsWith = others)
            }
        }.associateBy { it.device.id }
        return active.mapNotNull { withOverlaps[it.id] }
    }

    /** Costs closer than this (EUR) are equal: rounding of the sums must not move a start later. */
    private const val SAME_COST = 1e-7

    /** The cheapest option; among (practically) equal costs the earliest start. */
    private fun pickBest(options: List<Pair<Long, Double>>): Pair<Long, Double>? {
        val min = options.minOfOrNull { it.second } ?: return null
        return options.filter { it.second <= min + SAME_COST }.minBy { it.first }
    }

    /** The next planned run that has not ended yet (for the home screen, widget and reminders). */
    fun nextRun(results: List<PlanResult>, nowMillis: Long): PlanResult? =
        results.filter { it.status == PlanStatus.OK && it.endMillis > nowMillis }.minByOrNull { it.startMillis }
}

/** Other consumption of the household by hour of day, from the delayed Moj Elektro readings. Pure, unit tested. */
object LoadEstimate {
    /**
     * Median power (kW) of every hour of the day over the given 15-minute readings; NaN where there is no reading.
     * [points] = pairs of (interval start, kW).
     */
    fun hourlyMedian(points: List<Pair<Long, Double>>): DoubleArray {
        val byHour = Array(24) { ArrayList<Double>() }
        for ((start, kw) in points) if (kw.isFinite() && kw >= 0) byHour[SloTime.hourOfDay(start)] += kw
        return DoubleArray(24) { h ->
            val v = byHour[h].sorted()
            when {
                v.isEmpty() -> Double.NaN
                v.size % 2 == 1 -> v[v.size / 2]
                else -> (v[v.size / 2 - 1] + v[v.size / 2]) / 2
            }
        }
    }
}

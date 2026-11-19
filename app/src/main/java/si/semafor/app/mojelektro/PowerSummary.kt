package si.semafor.app.mojelektro

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.LocalDate

/**
 * The last known power of the user's metering point, from the DELAYED Moj Elektro readings (usually a day old):
 * the latest 15-minute interval and the highest interval of that day, each with the agreed power of its block.
 * Used by the home screen, the widget, the planner and the power warnings; never shown as a live value.
 */
data class PowerSummary(
    val lastDay: LocalDate,
    val lastStartMillis: Long,
    val lastKw: Double,
    val lastBlock: Int?,
    val lastAgreedKw: Double?,
    val peakStartMillis: Long,
    val peakKw: Double,
    val peakBlock: Int?,
    val peakAgreedKw: Double?,
    /** Agreed power per block 1..5 (null = unknown). */
    val agreedByBlock: List<Double?>,
    /** Median power of every hour of the day over the loaded days (NaN = unknown), for the planner's load check. */
    val hourlyBaseKw: DoubleArray,
    /** Of the last [RECENT_DAYS] days with readings: how many came to 90 % of the agreed power, how many above it. */
    val nearDays: Int = 0,
    val overDays: Int = 0,
    val recentDays: Int = 0
) {
    /** Whole days between [lastDay] and [today] (1 = yesterday's readings, the usual delay). */
    fun ageDays(today: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(lastDay, today)

    /**
     * Recent enough to warn about: older readings are still shown with their date, but never trigger a warning or a
     * recommendation (they say nothing about the household today).
     */
    fun isFresh(today: LocalDate): Boolean = ageDays(today) in 0..FRESH_DAYS

    /** Typical power of the household in the hour of [millis] (median of the loaded days; null = unknown). */
    fun typicalKwAt(millis: Long): Double? = hourlyBaseKw.getOrNull(si.semafor.app.SloTime.hourOfDay(millis))?.takeIf { !it.isNaN() }

    /** Share of the agreed power reached at the day's highest interval (null if unknown). */
    val peakShare: Double? get() = peakAgreedKw?.takeIf { it > 0 }?.let { peakKw / it }
    val lastShare: Double? get() = lastAgreedKw?.takeIf { it > 0 }?.let { lastKw / it }

    companion object {
        /** Readings older than this many days do not trigger warnings or recommendations. */
        const val FRESH_DAYS = 3L

        /** Days looked at for repeated high loads (and for the typical load per hour). */
        const val RECENT_DAYS = 7

        /** Pure: the summary of loaded usage data, or null if there is no complete day. */
        fun of(data: UsageData): PowerSummary? {
            val profiles = data.recentProfiles.filter { it.intervals.isNotEmpty() }
            val day = profiles.lastOrNull { it.date == data.lastDay } ?: profiles.lastOrNull() ?: return null
            val last = day.intervals.lastOrNull() ?: return null
            val peak = day.peakIndex?.let { day.intervals.getOrNull(it) } ?: last
            val agreed = (0 until 5).map { i -> data.agreed?.blockKw?.getOrNull(i)?.toDouble()?.takeIf { it > 0 } }
            val recent = profiles.filter { !it.date.isAfter(day.date) }.takeLast(RECENT_DAYS)
            val base = si.semafor.app.planner.LoadEstimate.hourlyMedian(
                recent.flatMap { p -> p.intervals.map { it.startMillis to it.kw } }
            )
            val peaks = UsageAnalysis.dailyPeaks(recent)
            return PowerSummary(
                day.date, last.startMillis, last.kw, last.blockId, last.agreedKw,
                peak.startMillis, peak.kw, peak.blockId, peak.agreedKw, agreed, base,
                nearDays = peaks.count { it.overCount == 0 && it.nearCount > 0 },
                overDays = peaks.count { it.overCount > 0 },
                recentDays = recent.size
            )
        }
    }
}

/** Encrypted on-device copy of [PowerSummary] (Keystore, like the readings), for the widget and the reminders. */
object PowerSummaryStore {
    private const val PREFS = "power_summary"
    private const val KEY = "summary"

    @Volatile
    private var memory: PowerSummary? = null

    fun save(ctx: Context, s: PowerSummary) {
        memory = s
        val json = JSONObject()
            .put("day", s.lastDay.toString())
            .put("ls", s.lastStartMillis).put("lk", s.lastKw).put("lb", s.lastBlock ?: -1).put("la", s.lastAgreedKw ?: -1.0)
            .put("ps", s.peakStartMillis).put("pk", s.peakKw).put("pb", s.peakBlock ?: -1).put("pa", s.peakAgreedKw ?: -1.0)
            .put("ag", org.json.JSONArray(s.agreedByBlock.map { it ?: -1.0 }))
            .put("base", org.json.JSONArray(s.hourlyBaseKw.map { if (it.isNaN()) -1.0 else it }))
            .put("nd", s.nearDays).put("od", s.overDays).put("rd", s.recentDays)
        try {
            val enc = MojElektroCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8))
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, enc) }
        } catch (e: Exception) {
            // Keystore not available: the summary stays in memory only.
        }
    }

    fun get(ctx: Context): PowerSummary? {
        memory?.let { return it }
        val enc = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(MojElektroCrypto.decrypt(enc).toString(Charsets.UTF_8))
            fun opt(v: Double) = v.takeIf { it >= 0 }
            fun optInt(v: Int) = v.takeIf { it in 1..5 }
            val ag = o.getJSONArray("ag")
            val base = o.getJSONArray("base")
            PowerSummary(
                LocalDate.parse(o.getString("day")),
                o.getLong("ls"), o.getDouble("lk"), optInt(o.getInt("lb")), opt(o.getDouble("la")),
                o.getLong("ps"), o.getDouble("pk"), optInt(o.getInt("pb")), opt(o.getDouble("pa")),
                (0 until 5).map { opt(ag.optDouble(it, -1.0)) },
                DoubleArray(24) { h -> base.optDouble(h, -1.0).let { if (it < 0) Double.NaN else it } },
                o.optInt("nd", 0), o.optInt("od", 0), o.optInt("rd", 0)
            ).also { memory = it }
        } catch (e: Exception) {
            null
        }
    }

    fun clear(ctx: Context) {
        memory = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }
}

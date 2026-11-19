package si.semafor.app.planner

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * The planner's data on this phone (SharedPreferences "planner"): the appliances, the load settings, the household
 * profile, the starts the user confirmed (for the recognised habits; only while learning is on) and a short summary
 * of the last plan (for the home screen, the widget and the reminders). Not part of the backup and never sent.
 */
object PlannerStore {
    private const val PREFS = "planner"
    private const val KEY_DEVICES = "devices"
    private const val KEY_AGREED = "manual_agreed_kw"
    private const val KEY_BASE = "manual_base_kw"
    private const val KEY_PLAN = "last_plan"
    private const val KEY_PROFILE = "profile"
    private const val KEY_LOG = "run_log"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun devices(ctx: Context): List<PlannerDevice> = decodeDevices(prefs(ctx).getString(KEY_DEVICES, null))

    fun saveDevices(ctx: Context, devices: List<PlannerDevice>) {
        prefs(ctx).edit { putString(KEY_DEVICES, encodeDevices(devices)) }
    }

    fun loadSettings(ctx: Context): LoadSettings {
        val p = prefs(ctx)
        return LoadSettings(
            p.getString(KEY_AGREED, null)?.toDoubleOrNull(),
            p.getString(KEY_BASE, null)?.toDoubleOrNull()
        )
    }

    fun saveLoadSettings(ctx: Context, s: LoadSettings) {
        prefs(ctx).edit {
            putString(KEY_AGREED, s.manualAgreedKw?.toString())
            putString(KEY_BASE, s.manualBaseKw?.toString())
        }
    }

    /** Longest note kept for an appliance. */
    const val NOTE_MAX = 120

    fun profile(ctx: Context): HouseholdProfile = decodeProfile(prefs(ctx).getString(KEY_PROFILE, null))

    fun saveProfile(ctx: Context, p: HouseholdProfile) {
        prefs(ctx).edit {
            putString(KEY_PROFILE, encodeProfile(p))
            // Switching learning off deletes what was learned.
            if (!p.learn) remove(KEY_LOG)
        }
    }

    fun runLog(ctx: Context): List<RunLogEntry> = decodeLog(prefs(ctx).getString(KEY_LOG, null))

    fun saveRunLog(ctx: Context, log: List<RunLogEntry>) {
        prefs(ctx).edit { putString(KEY_LOG, encodeLog(log)) }
    }

    /** One planned run, as remembered for places without the full plan. */
    data class PlannedRun(val deviceId: String, val name: String, val startMillis: Long, val endMillis: Long, val cost: Double)

    fun savePlan(ctx: Context, runs: List<PlannedRun>) {
        prefs(ctx).edit { putString(KEY_PLAN, encodePlan(runs)) }
    }

    fun plan(ctx: Context): List<PlannedRun> = decodePlan(prefs(ctx).getString(KEY_PLAN, null))

    /** The next run that has not ended yet. */
    fun nextRun(ctx: Context, nowMillis: Long = System.currentTimeMillis()): PlannedRun? =
        plan(ctx).filter { it.endMillis > nowMillis }.minByOrNull { it.startMillis }

    // ---- JSON (pure, unit tested) ----

    internal fun encodeDevices(devices: List<PlannerDevice>): String = JSONArray().apply {
        for (d in devices) put(
            JSONObject()
                .put("id", d.id)
                .put("type", d.type.key)
                .put("name", d.name)
                .put("kw", d.powerKw)
                .put("kwh", d.energyKwh ?: JSONObject.NULL)
                .put("min", d.durationMin)
                .put("earliest", d.earliestStartMin ?: JSONObject.NULL)
                .put("deadline", d.deadlineMin ?: JSONObject.NULL)
                .put("days", JSONArray(d.days.map { it.value }.sorted()))
                .put("active", d.active)
                .put("user", d.userValues)
                .put("fav", d.favorite)
                .put("note", d.note)
        )
    }.toString()

    internal fun decodeDevices(text: String?): List<PlannerDevice> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val kw = o.getDouble("kw")
                val min = o.getInt("min")
                if (kw <= 0 || min <= 0) return@mapNotNull null
                val days = o.optJSONArray("days")?.let { a ->
                    (0 until a.length()).mapNotNull { j -> a.optInt(j).takeIf { it in 1..7 }?.let { DayOfWeek.of(it) } }.toSet()
                }.orEmpty()
                PlannerDevice(
                    id = o.getString("id"),
                    type = DeviceType.of(o.optString("type")),
                    name = o.optString("name"),
                    powerKw = kw,
                    energyKwh = if (o.isNull("kwh")) null else o.getDouble("kwh").takeIf { it > 0 },
                    durationMin = min,
                    earliestStartMin = if (o.isNull("earliest")) null else o.getInt("earliest").takeIf { it in 0..1439 },
                    deadlineMin = if (o.isNull("deadline")) null else o.getInt("deadline").takeIf { it in 0..1439 },
                    days = days,
                    active = o.optBoolean("active", true),
                    userValues = o.optBoolean("user", false),
                    favorite = o.optBoolean("fav", false),
                    note = o.optString("note", "").take(NOTE_MAX)
                )
            }
        } catch (e: Exception) {
            // A damaged entry must never crash the app: start with an empty list.
            emptyList()
        }
    }

    internal fun encodeProfile(p: HouseholdProfile): String = JSONObject()
        .put("goal", p.goal.key)
        .put("chores", p.choresDoneByMin ?: JSONObject.NULL)
        .put("evFrom", p.evFromMin ?: JSONObject.NULL)
        .put("evTo", p.evToMin ?: JSONObject.NULL)
        .put("learn", p.learn)
        .toString()

    internal fun decodeProfile(text: String?): HouseholdProfile {
        if (text.isNullOrBlank()) return HouseholdProfile()
        return try {
            val o = JSONObject(text)
            fun minute(key: String): Int? = if (!o.has(key) || o.isNull(key)) null else o.getInt(key).takeIf { it in 0..1439 }
            HouseholdProfile(PlanGoal.of(o.optString("goal")), minute("chores"), minute("evFrom"), minute("evTo"), o.optBoolean("learn", true))
        } catch (e: Exception) {
            HouseholdProfile()
        }
    }

    internal fun encodeLog(log: List<RunLogEntry>): String = JSONArray().apply {
        for (e in log) put(
            JSONObject().put("id", e.deviceId).put("type", e.type.key).put("start", e.startMillis)
                .put("planned", e.plannedStartMillis ?: JSONObject.NULL)
        )
    }.toString()

    internal fun decodeLog(text: String?): List<RunLogEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RunLogEntry(
                    o.getString("id"), DeviceType.of(o.optString("type")), o.getLong("start"),
                    if (o.isNull("planned")) null else o.getLong("planned")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    internal fun encodePlan(runs: List<PlannedRun>): String = JSONArray().apply {
        for (r in runs) put(
            JSONObject().put("id", r.deviceId).put("name", r.name).put("start", r.startMillis).put("end", r.endMillis).put("cost", r.cost)
        )
    }.toString()

    internal fun decodePlan(text: String?): List<PlannedRun> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlannedRun(o.getString("id"), o.getString("name"), o.getLong("start"), o.getLong("end"), o.getDouble("cost"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

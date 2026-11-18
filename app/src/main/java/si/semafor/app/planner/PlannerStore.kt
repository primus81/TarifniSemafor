package si.semafor.app.planner

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * The planner's data on this phone (SharedPreferences "planner"): the appliances, the load settings and a short
 * summary of the last plan (for the home screen, the widget and the reminders). Not part of the backup and never sent.
 */
object PlannerStore {
    private const val PREFS = "planner"
    private const val KEY_DEVICES = "devices"
    private const val KEY_AGREED = "manual_agreed_kw"
    private const val KEY_BASE = "manual_base_kw"
    private const val KEY_PLAN = "last_plan"

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
                    userValues = o.optBoolean("user", false)
                )
            }
        } catch (e: Exception) {
            // A damaged entry must never crash the app: start with an empty list.
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

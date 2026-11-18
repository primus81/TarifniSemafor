package si.semafor.app.planner

import java.time.DayOfWeek

/** Kinds of appliances with simple defaults. The defaults are rough starting values; the user can change them. */
enum class DeviceType(val key: String, val defaultKw: Double, val defaultKwh: Double?, val defaultMinutes: Int) {
    WASHER("washer", 2.0, 1.5, 120),
    DISHWASHER("dishwasher", 1.8, 1.2, 120),
    DRYER("dryer", 2.5, 2.5, 120),
    BOILER("boiler", 2.0, 2.0, 60),
    HEAT_PUMP("heat_pump", 2.0, null, 180),
    // 40 kWh at 11 kW (values from the app author's request): about 3 h 40 min.
    EV("ev", 11.0, 40.0, 220),
    HEATING("heating", 2.0, null, 120),
    CUSTOM("custom", 1.0, null, 60);

    companion object {
        fun of(key: String?): DeviceType = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/**
 * An appliance the user wants to plan. Minutes of the day are Slovenian wall-clock minutes (0..1439).
 *
 * [energyKwh] null = not known: the energy is estimated from [powerKw] x duration (marked as an estimate).
 * [days] empty = every day.
 */
data class PlannerDevice(
    val id: String,
    val type: DeviceType,
    val name: String,
    val powerKw: Double,
    val energyKwh: Double?,
    val durationMin: Int,
    val earliestStartMin: Int? = null,
    val deadlineMin: Int? = null,
    val days: Set<DayOfWeek> = emptySet(),
    /** Part of today's plan. */
    val active: Boolean = true,
    /** The user changed at least one value of the defaults (energy or power entered by hand). */
    val userValues: Boolean = false
) {
    /** Energy of one run: the given value or power x duration. */
    val runKwh: Double get() = energyKwh ?: (powerKw * durationMin / 60.0)

    /** Average power over the run (for the cost); the nominal [powerKw] is used for the load check. */
    val averageKw: Double get() = if (durationMin <= 0) 0.0 else runKwh / (durationMin / 60.0)

    val energyEstimated: Boolean get() = energyKwh == null
}

/** Settings of the load check. Values typed by the user are used only if Moj Elektro gives none. */
data class LoadSettings(
    /** Agreed power for all blocks, typed by the user (kW); null = not known. */
    val manualAgreedKw: Double?,
    /** Typical other consumption of the household while the appliances run (kW); null = not known. */
    val manualBaseKw: Double?
)

/** Limits of one 15-minute slot for the load check. */
data class SlotLimit(val agreedKw: Double?, val baseKw: Double)

enum class PlanStatus {
    OK,

    /** No prices for the window (energy price not set, block schedule or price list missing). */
    NO_PRICES,

    /** The run does not fit between the earliest start and the deadline (or not on an allowed day). */
    NO_WINDOW
}

/** Why the recommended start was chosen (shown in plain words). */
enum class PlanReason {
    /** The cheapest start in the window. */
    CHEAPEST,

    /** Starting now is already the cheapest option. */
    NOW_IS_CHEAPEST,

    /** A cheaper start would exceed the agreed power together with the other planned appliances. */
    CHEAPEST_WITHIN_POWER,

    /** Every start exceeds the agreed power: the cheapest one is shown with a warning. */
    CHEAPEST_OVER_POWER
}

/** Result for one appliance. All costs are EUR with VAT, for the variable part only (energy + per-kWh items). */
data class PlanResult(
    val device: PlannerDevice,
    val status: PlanStatus,
    val startMillis: Long = 0L,
    val endMillis: Long = 0L,
    val cost: Double = 0.0,
    val reason: PlanReason = PlanReason.CHEAPEST,
    /** Cost if started right now (null if starting now is not allowed or not inside the known prices). */
    val costNow: Double? = null,
    /** Another good start at least an hour away from the recommended one. */
    val altStartMillis: Long? = null,
    val altCost: Double? = null,
    /** The run overlaps another planned appliance. */
    val overlapsWith: List<String> = emptyList(),
    /** Planned load (base + appliances) would exceed the agreed power in some slot of the run. */
    val overPower: Boolean = false,
    /** The highest planned load during the run (kW, including the base load) and the agreed power then. */
    val peakKw: Double = 0.0,
    val peakAgreedKw: Double? = null,
    /** Blocks the run touches, in order of time. */
    val blocks: List<Int> = emptyList()
) {
    /** How much starting at the recommended time costs less than starting now (null if not comparable). */
    val savingVsNow: Double? get() = costNow?.let { (it - cost).coerceAtLeast(0.0) }
}

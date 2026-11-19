package si.semafor.app.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.PowerSummary
import si.semafor.app.mojelektro.PowerSummaryStore
import si.semafor.app.pricing.KwhPrice
import java.util.UUID

data class PlannerUiState(
    val loaded: Boolean = false,
    val devices: List<PlannerDevice> = emptyList(),
    val load: LoadSettings = LoadSettings(null, null),
    /** Today's plan (home screen, reminders, widget always use this one). */
    val results: List<PlanResult> = emptyList(),
    /** Prices of the planner's week (from the current hour on). */
    val prices: List<KwhPrice> = emptyList(),
    /** The day shown in the planner: 0 = today, up to [MAX_DAY_OFFSET] days later. */
    val dayOffset: Int = 0,
    /** Plan of the chosen later day (empty for today) and the prices it was made with. */
    val dayResults: List<PlanResult> = emptyList(),
    val dayPrices: List<KwhPrice> = emptyList(),
    /** Where the agreed power of the load check comes from. */
    val agreedSource: AgreedSource = AgreedSource.NONE,
    val power: PowerSummary? = null,
    val plannedAtMillis: Long = 0L,
    val profile: HouseholdProfile = HouseholdProfile(),
    /** Starts the user confirmed (empty while learning is off). */
    val log: List<RunLogEntry> = emptyList(),
    /** Habits recognised from [log]. */
    val habits: List<Habit> = emptyList()
) {
    /** The plan and the prices of the day shown in the planner. */
    val shownResults: List<PlanResult> get() = if (dayOffset == 0) results else dayResults
    val shownPrices: List<KwhPrice> get() = if (dayOffset == 0) todayPrices else dayPrices

    /** Today's 48 hours (as before the week was added). */
    val todayPrices: List<KwhPrice> get() = prices.take(PlannerViewModel.TODAY_HOURS)

    val summary: PlanSummary? get() = PlanSummary.of(shownResults)
}

enum class AgreedSource { MOJ_ELEKTRO, MANUAL, NONE }

/**
 * State of the smart day planner. The prices come from the price screen's model (the same calculation everywhere);
 * the host passes them in with [onPrices]. The plan is recalculated when the prices, the appliances or the load
 * settings change, and a short summary is stored for the home screen, the widget and the reminders.
 */
class PlannerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val loaded = withContext(Dispatchers.IO) {
                val profile = PlannerStore.profile(ctx)
                Loaded(
                    PlannerStore.devices(ctx), PlannerStore.loadSettings(ctx), PowerSummaryStore.get(ctx), profile,
                    if (profile.learn) Habits.prune(PlannerStore.runLog(ctx), System.currentTimeMillis()) else emptyList()
                )
            }
            _state.update {
                it.copy(
                    loaded = true, devices = loaded.devices, load = loaded.load, power = loaded.power,
                    profile = loaded.profile, log = loaded.log, habits = Habits.habits(loaded.log, System.currentTimeMillis())
                )
            }
            replan()
        }
    }

    fun onPrices(prices: List<KwhPrice>) {
        if (prices == _state.value.prices) return
        _state.update { it.copy(prices = prices) }
        replan()
    }

    /** Shows the plan of today + [offset] days (0..[MAX_DAY_OFFSET]). */
    fun setDay(offset: Int) {
        val o = offset.coerceIn(0, MAX_DAY_OFFSET)
        if (o == _state.value.dayOffset) return
        _state.update { it.copy(dayOffset = o, dayResults = emptyList(), dayPrices = emptyList()) }
        replan()
    }

    fun onPower(power: PowerSummary?) {
        if (power == _state.value.power) return
        _state.update { it.copy(power = power) }
        replan()
    }

    private class Loaded(
        val devices: List<PlannerDevice>,
        val load: LoadSettings,
        val power: PowerSummary?,
        val profile: HouseholdProfile,
        val log: List<RunLogEntry>
    )

    fun addDevice(type: DeviceType, name: String) {
        // A new appliance takes the household's usual window (laundry done by, car charging) when one is set.
        val (earliest, deadline) = _state.value.profile.windowFor(type)
        val d = PlannerDevice(
            id = UUID.randomUUID().toString(),
            type = type,
            name = name,
            powerKw = type.defaultKw,
            energyKwh = type.defaultKwh,
            durationMin = type.defaultMinutes,
            earliestStartMin = earliest,
            deadlineMin = deadline
        )
        saveDevices(_state.value.devices + d)
    }

    fun setProfile(profile: HouseholdProfile) {
        val clearLog = !profile.learn
        // Appliances without any window of their own take the household's usual window.
        val devices = _state.value.devices.map { d ->
            val (earliest, deadline) = profile.windowFor(d.type)
            if (d.earliestStartMin == null && d.deadlineMin == null && (earliest != null || deadline != null)) {
                d.copy(earliestStartMin = earliest, deadlineMin = deadline)
            } else d
        }
        val devicesChanged = devices != _state.value.devices
        _state.update {
            it.copy(
                profile = profile, devices = devices,
                log = if (clearLog) emptyList() else it.log, habits = if (clearLog) emptyList() else it.habits
            )
        }
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            PlannerStore.saveProfile(ctx, profile)
            if (devicesChanged) PlannerStore.saveDevices(ctx, devices)
        }
        replan()
    }

    /** The user says the appliance was started now; recorded only while learning is on. */
    fun confirmStart(deviceId: String) {
        val s = _state.value
        if (!s.profile.learn) return
        val device = s.devices.firstOrNull { it.id == deviceId } ?: return
        val now = System.currentTimeMillis()
        val planned = s.results.firstOrNull { it.device.id == deviceId && it.status == PlanStatus.OK }?.startMillis
        val log = Habits.prune(s.log + RunLogEntry(deviceId, device.type, now, planned), now)
        _state.update { it.copy(log = log, habits = Habits.habits(log, now)) }
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { PlannerStore.saveRunLog(ctx, log) }
    }

    /** Applies a recognised habit to its appliance (the user tapped the suggestion). */
    fun applyHabit(habit: Habit) {
        val device = _state.value.devices.firstOrNull { it.id == habit.deviceId } ?: return
        updateDevice(
            when (habit) {
                is Habit.TypicalStart -> device.copy(earliestStartMin = habit.fromMin)
                is Habit.WeekendOnly -> device.copy(days = setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY))
            }
        )
    }

    /** Deletes all confirmed starts and the habits learned from them. */
    fun clearLog() {
        _state.update { it.copy(log = emptyList(), habits = emptyList()) }
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { PlannerStore.saveRunLog(ctx, emptyList()) }
    }

    fun updateDevice(device: PlannerDevice) = saveDevices(_state.value.devices.map { if (it.id == device.id) device else it })

    fun removeDevice(id: String) = saveDevices(_state.value.devices.filterNot { it.id == id })

    fun setLoad(load: LoadSettings) {
        _state.update { it.copy(load = load) }
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { PlannerStore.saveLoadSettings(ctx, load) }
        replan()
    }

    private fun saveDevices(devices: List<PlannerDevice>) {
        _state.update { it.copy(devices = devices) }
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { PlannerStore.saveDevices(ctx, devices) }
        replan()
    }

    /** The clock passed the start of the first price hour: plan again from now. */
    fun onTick(nowMillis: Long) {
        val first = _state.value.prices.firstOrNull() ?: return
        if (nowMillis >= first.endMillis) replan()
    }

    private var planJob: kotlinx.coroutines.Job? = null

    private fun replan() {
        val s = _state.value
        if (!s.loaded) return
        // Several orders of the appliances are compared: plan off the main thread, only the latest request counts.
        planJob?.cancel()
        planJob = viewModelScope.launch(Dispatchers.Default) { replan(s) }
    }

    private suspend fun replan(s: PlannerUiState) {
        val now = System.currentTimeMillis()
        val week = s.prices.dropWhile { it.endMillis <= now }
        val prices = week.take(TODAY_HOURS)
        val power = s.power
        val mojAgreed = power?.agreedByBlock?.any { it != null } == true
        val source = when {
            mojAgreed -> AgreedSource.MOJ_ELEKTRO
            s.load.manualAgreedKw != null -> AgreedSource.MANUAL
            else -> AgreedSource.NONE
        }
        val limitAt: (Long) -> SlotLimit = { slot ->
            val hour = week.firstOrNull { slot >= it.startMillis && slot < it.endMillis }
            val agreed = when (source) {
                AgreedSource.MOJ_ELEKTRO -> hour?.let { power?.agreedByBlock?.getOrNull(it.block - 1) }
                AgreedSource.MANUAL -> s.load.manualAgreedKw
                AgreedSource.NONE -> null
            }
            // Moj Elektro readings first (typical consumption of that hour), otherwise the typed value.
            val base = power?.hourlyBaseKw?.getOrNull(SloTime.hourOfDay(slot))?.takeIf { !it.isNaN() }
                ?: s.load.manualBaseKw
                ?: 0.0
            SlotLimit(agreed, base)
        }
        val results = DayPlanner.plan(s.devices, prices, now, s.profile.goal, limitAt)
        // A later day: planned as if the day had just begun, with the 48 hours from its midnight (night windows
        // reach into the next morning). "Starting now" means nothing for a later day, so it is not compared.
        val (dayResults, dayPrices) = if (s.dayOffset == 0) emptyList<PlanResult>() to emptyList() else {
            val from = java.time.ZonedDateTime.of(SloTime.today().plusDays(s.dayOffset.toLong()), java.time.LocalTime.MIDNIGHT, SloTime.ZONE)
                .toInstant().toEpochMilli()
            val dp = week.dropWhile { it.endMillis <= from }.take(TODAY_HOURS)
            DayPlanner.plan(s.devices, dp, from, s.profile.goal, limitAt).map { it.copy(costNow = null) } to dp
        }
        currentCoroutineContext().ensureActive()
        _state.update {
            it.copy(results = results, agreedSource = source, plannedAtMillis = now, dayResults = dayResults, dayPrices = dayPrices)
        }
        val ctx = getApplication<Application>()
        val runs = results.filter { it.status == PlanStatus.OK }
            .map { PlannerStore.PlannedRun(it.device.id, it.device.name, it.startMillis, it.endMillis, it.cost) }
        withContext(Dispatchers.IO) {
            PlannerStore.savePlan(ctx, runs)
            si.semafor.app.reminders.Reminders.reschedule(ctx)
            // The tall widget shows the next planned appliance.
            si.semafor.app.SemaforWidget.renderFromCache(ctx)
        }
    }

    companion object {
        /** Today's plan looks this many hours ahead (as the price screen). */
        const val TODAY_HOURS = si.semafor.app.pricing.EnergyPriceViewModel.HOURS

        /** The planner can show today and the next 6 days. */
        const val MAX_DAY_OFFSET = si.semafor.app.pricing.EnergyPriceViewModel.PLAN_DAYS
    }
}

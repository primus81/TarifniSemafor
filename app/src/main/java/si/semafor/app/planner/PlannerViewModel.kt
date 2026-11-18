package si.semafor.app.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.semafor.app.mojelektro.PowerSummary
import si.semafor.app.mojelektro.PowerSummaryStore
import si.semafor.app.pricing.KwhPrice
import java.util.UUID

data class PlannerUiState(
    val loaded: Boolean = false,
    val devices: List<PlannerDevice> = emptyList(),
    val load: LoadSettings = LoadSettings(null, null),
    val results: List<PlanResult> = emptyList(),
    /** Prices the plan was made with (from the current hour on). */
    val prices: List<KwhPrice> = emptyList(),
    /** Where the agreed power of the load check comes from. */
    val agreedSource: AgreedSource = AgreedSource.NONE,
    val power: PowerSummary? = null,
    val plannedAtMillis: Long = 0L
)

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
            val (devices, load, power) = withContext(Dispatchers.IO) {
                Triple(PlannerStore.devices(ctx), PlannerStore.loadSettings(ctx), PowerSummaryStore.get(ctx))
            }
            _state.update { it.copy(loaded = true, devices = devices, load = load, power = power) }
            replan()
        }
    }

    fun onPrices(prices: List<KwhPrice>) {
        if (prices == _state.value.prices) return
        _state.update { it.copy(prices = prices) }
        replan()
    }

    fun onPower(power: PowerSummary?) {
        if (power == _state.value.power) return
        _state.update { it.copy(power = power) }
        replan()
    }

    fun addDevice(type: DeviceType, name: String) {
        val d = PlannerDevice(
            id = UUID.randomUUID().toString(),
            type = type,
            name = name,
            powerKw = type.defaultKw,
            energyKwh = type.defaultKwh,
            durationMin = type.defaultMinutes
        )
        saveDevices(_state.value.devices + d)
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

    private fun replan() {
        val s = _state.value
        if (!s.loaded) return
        val now = System.currentTimeMillis()
        val prices = s.prices.dropWhile { it.endMillis <= now }
        val power = s.power
        val mojAgreed = power?.agreedByBlock?.any { it != null } == true
        val source = when {
            mojAgreed -> AgreedSource.MOJ_ELEKTRO
            s.load.manualAgreedKw != null -> AgreedSource.MANUAL
            else -> AgreedSource.NONE
        }
        val results = DayPlanner.plan(s.devices, prices, now) { slot ->
            val hour = prices.firstOrNull { slot >= it.startMillis && slot < it.endMillis }
            val agreed = when (source) {
                AgreedSource.MOJ_ELEKTRO -> hour?.let { power?.agreedByBlock?.getOrNull(it.block - 1) }
                AgreedSource.MANUAL -> s.load.manualAgreedKw
                AgreedSource.NONE -> null
            }
            // Moj Elektro readings first (typical consumption of that hour), otherwise the typed value.
            val base = power?.hourlyBaseKw?.getOrNull(si.semafor.app.SloTime.hourOfDay(slot))?.takeIf { !it.isNaN() }
                ?: s.load.manualBaseKw
                ?: 0.0
            SlotLimit(agreed, base)
        }
        _state.update { it.copy(results = results, agreedSource = source, plannedAtMillis = now) }
        val ctx = getApplication<Application>()
        val runs = results.filter { it.status == PlanStatus.OK }
            .map { PlannerStore.PlannedRun(it.device.id, it.device.name, it.startMillis, it.endMillis, it.cost) }
        viewModelScope.launch(Dispatchers.IO) {
            PlannerStore.savePlan(ctx, runs)
            si.semafor.app.reminders.Reminders.reschedule(ctx)
            // The tall widget shows the next planned appliance.
            si.semafor.app.SemaforWidget.renderFromCache(ctx)
        }
    }
}

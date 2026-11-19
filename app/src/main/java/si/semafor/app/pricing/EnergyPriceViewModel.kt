package si.semafor.app.pricing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.semafor.app.PriceLists
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.BlockSchedules

/** Why the hourly prices end before the end of tomorrow (shown, so a short list is never a silent gap). */
enum class PriceGapReason { NO_SCHEDULE, NO_NETWORK_LIST, NO_ENERGY_PRICE }

data class PriceGap(val date: java.time.LocalDate, val reason: PriceGapReason)

/** Result of the "Preveri zdaj" button, shown once under it. */
enum class SyncMessage { UPDATED, UNCHANGED, SAME_AS_BUILT_IN, NO_CONNECTION, SERVER_ERROR, INVALID_DATA }

data class EnergyPriceUiState(
    val data: PriceData? = null,
    val stored: PricingSettings.Stored? = null,
    val setup: PricingSetup = PricingSetup(MeterType.DUAL, null),
    /** Prices hour by hour from the current hour on (as far as the block schedule is known). */
    val hourly: List<KwhPrice> = emptyList(),
    /** The same prices for the planner: from this hour to the end of the day after the last plannable day (stops at the first gap). */
    val weekHourly: List<KwhPrice> = emptyList(),
    val hourlyLoading: Boolean = false,
    /** The block schedule could not be loaded (no connection): the chart and the appliance comparison are missing. */
    val hourlyFailed: Boolean = false,
    /** The prices stop early for this reason (null = known until the end of tomorrow). */
    val gap: PriceGap? = null,
    val lastCheckMillis: Long = 0L,
    val lastSuccessMillis: Long = 0L,
    val checking: Boolean = false,
    val syncMessage: SyncMessage? = null,
    /** HTTP status of a failed check (shown with SERVER_ERROR). */
    val syncHttpCode: Int = 0,
    val notifyChanges: Boolean = true
)

/**
 * State of the "Cena elektrike" screen and of the price card on the home screen. The block schedule of today and
 * tomorrow comes from the official URO API (the same requests as the Moj Elektro analysis; kept for an hour).
 */
class EnergyPriceViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(EnergyPriceUiState())
    val state: StateFlow<EnergyPriceUiState> = _state.asStateFlow()

    private var schedules: Map<java.time.LocalDate, IntArray?> = emptyMap()
    private var schedulesAt = 0L
    private var scheduleJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PriceRepository.get(app) }
            reloadSettings()
            PriceRepository.data.collect { d ->
                _state.update { it.copy(data = d) }
                recompute()
            }
        }
    }

    /** Called when the screen or the home card is shown: settings again, schedule if older than an hour. */
    fun onShown() {
        viewModelScope.launch {
            reloadSettings()
            ensureSchedules()
        }
    }

    private suspend fun reloadSettings() {
        val ctx = getApplication<Application>()
        val stored = withContext(Dispatchers.IO) { PricingSettings.stored(ctx) }
        _state.update {
            it.copy(
                stored = stored,
                setup = PricingSettings.toSetup(stored),
                lastCheckMillis = PriceSync.lastCheck(ctx),
                lastSuccessMillis = PriceSync.lastSuccess(ctx),
                notifyChanges = PricingSettings.notifyChanges(ctx)
            )
        }
        recompute()
    }

    fun save(stored: PricingSettings.Stored) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PricingSettings.save(ctx, stored) }
            reloadSettings()
            ensureSchedules()
        }
    }

    fun setNotifyChanges(on: Boolean) {
        PricingSettings.setNotifyChanges(getApplication(), on)
        _state.update { it.copy(notifyChanges = on) }
    }

    /** The "Preveri zdaj" button: one download right away (the user asked for it). */
    fun checkNow() {
        if (_state.value.checking) return
        val ctx = getApplication<Application>()
        _state.update { it.copy(checking = true, syncMessage = null) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { PriceSync.syncNow(ctx) }
            _state.update {
                it.copy(
                    checking = false,
                    syncMessage = when (r) {
                        is PriceSync.Result.Updated -> SyncMessage.UPDATED
                        is PriceSync.Result.Unchanged -> if (r.sameAsBuiltIn) SyncMessage.SAME_AS_BUILT_IN else SyncMessage.UNCHANGED
                        is PriceSync.Result.Failed -> when (r.reason) {
                            PriceSync.FailReason.NO_CONNECTION -> SyncMessage.NO_CONNECTION
                            PriceSync.FailReason.HTTP_ERROR -> SyncMessage.SERVER_ERROR
                            PriceSync.FailReason.INVALID_DATA -> SyncMessage.INVALID_DATA
                        }
                    },
                    syncHttpCode = (r as? PriceSync.Result.Failed)?.httpCode ?: 0,
                    lastCheckMillis = PriceSync.lastCheck(ctx),
                    lastSuccessMillis = PriceSync.lastSuccess(ctx)
                )
            }
        }
    }

    private fun ensureSchedules() {
        if (!_state.value.setup.configured) return
        val now = System.currentTimeMillis()
        if (schedules.isNotEmpty() && now - schedulesAt in 0 until SCHEDULE_TTL_MS && !_state.value.hourlyFailed) {
            recompute()
            return
        }
        if (scheduleJob?.isActive == true) return
        scheduleJob = viewModelScope.launch {
            _state.update { it.copy(hourlyLoading = true) }
            val today = SloTime.today()
            val loaded = withContext(Dispatchers.IO) {
                // Today and the planner's week (built-in official tables: no requests for 2026 and 2027).
                BlockSchedules(getApplication()).forDates((0..PLAN_DAYS + 1L).map { today.plusDays(it) })
            }
            schedules = loaded
            schedulesAt = System.currentTimeMillis()
            _state.update { it.copy(hourlyLoading = false, hourlyFailed = loaded[today] == null) }
            recompute()
        }
    }

    private fun recompute() {
        val s = _state.value
        val data = s.data
        val now = System.currentTimeMillis()
        val week = if (data == null || !s.setup.configured || schedules.isEmpty()) emptyList() else {
            EnergyPriceCalculator.hourly(
                data, s.setup, now, (PLAN_DAYS + 2) * 24,
                scheduleOf = { schedules[it] },
                networkOf = { PriceLists.forDate(it) }
            )
        }
        val hourly = week.take(HOURS)
        val gap = if (data == null || !s.setup.configured || schedules.isEmpty()) null else {
            val end = hourly.lastOrNull()?.endMillis ?: SloTime.previousStartAtOrBefore(now, SloTime.hourOfDay(now))
            val date = SloTime.dateOf(end)
            val tomorrow = SloTime.today().plusDays(1)
            when {
                date.isAfter(tomorrow) -> null
                schedules[date] == null -> PriceGap(date, PriceGapReason.NO_SCHEDULE)
                PriceLists.forDate(date) == null -> PriceGap(date, PriceGapReason.NO_NETWORK_LIST)
                else -> PriceGap(date, PriceGapReason.NO_ENERGY_PRICE)
            }
        }
        _state.update { it.copy(hourly = hourly, weekHourly = week, gap = gap) }
    }

    /** The clock moved to a new hour: the list starts with the current hour again. */
    fun onTick(nowMillis: Long) {
        val first = _state.value.hourly.firstOrNull() ?: return
        if (nowMillis >= first.endMillis) {
            if (SloTime.dateOf(nowMillis) != SloTime.dateOf(schedulesAt)) schedulesAt = 0L
            ensureSchedules()
            recompute()
        }
    }

    companion object {
        /** Up to the end of tomorrow (the block schedule is known for today and tomorrow). */
        const val HOURS = 48

        /** The planner plans up to this many days after today. */
        const val PLAN_DAYS = 6
        private const val SCHEDULE_TTL_MS = 3600 * 1000L
    }
}

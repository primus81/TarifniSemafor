package si.semafor.app.mojelektro

import android.app.Application
import androidx.compose.runtime.Immutable
import si.semafor.app.R
import si.semafor.app.UiText
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the "Moja poraba" screen shows. */
@Immutable
sealed interface UsageUiState {
    data object NotConnected : UsageUiState
    data class Loading(val step: UiText) : UsageUiState
    data class Error(val message: UiText) : UsageUiState
    data class Content(val data: UsageData) : UsageUiState
}

/** State of the "Moja poraba" screen; the analysis runs off the main thread, the screen only draws. */
class UsageViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UsageRepository(app)

    private val _state = MutableStateFlow<UsageUiState>(UsageUiState.Loading(UiText.res(R.string.usage_loading)))
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    private val _period = MutableStateFlow(UsagePeriod.LAST_7)
    val period: StateFlow<UsagePeriod> = _period.asStateFlow()

    /** A refresh is running while charts stay on screen. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var job: Job? = null

    // When the shown content was produced; a page that is swiped to again within a few minutes is not re-analysed.
    private var shownAt = 0L

    /** Called whenever the screen is opened: shows stored data, downloads only what is missing or too old. */
    fun onScreenShown() {
        val shown = _state.value as? UsageUiState.Content
        if (shown != null && shown.data.period == _period.value &&
            System.currentTimeMillis() - shownAt in 0 until REUSE_MS
        ) {
            return
        }
        load(force = false)
    }

    // The connected metering point last reported by the host; "unset" until the first report.
    private var knownEimm: String? = null
    private var eimmKnown = false

    /**
     * The host reports the connected metering point (null = none) whenever it is read. If it differs from the last
     * report (added, removed or replaced), nothing shown so far may be kept.
     */
    fun onConnection(eimm: String?) {
        val changed = eimmKnown && eimm != knownEimm
        eimmKnown = true
        knownEimm = eimm
        if (!changed) return
        job?.cancel()
        shownAt = 0L
        _refreshing.value = false
        _state.value = UsageUiState.Loading(UiText.res(R.string.usage_loading))
    }

    fun refresh() = load(force = true)

    fun setPeriod(period: UsagePeriod) {
        if (period == _period.value) return
        _period.value = period
        load(force = false)
    }

    private fun load(force: Boolean) {
        if (job?.isActive == true) return
        val range = _period.value
        job = viewModelScope.launch {
            val connected = withContext(Dispatchers.IO) { repository.isConnected() }
            if (!connected) {
                _state.value = UsageUiState.NotConnected
                return@launch
            }
            if (_state.value is UsageUiState.Content) _refreshing.value = true
            else _state.value = UsageUiState.Loading(UiText.res(R.string.usage_loading))
            val result = withContext(Dispatchers.IO) {
                repository.load(range, force) { step ->
                    // Progress only while nothing is shown yet (a refresh keeps the charts on screen).
                    if (_state.value is UsageUiState.Loading) _state.value = UsageUiState.Loading(step)
                }
            }
            _state.value = when (result) {
                is MojElektroResult.Success -> {
                    shownAt = System.currentTimeMillis()
                    UsageUiState.Content(result.data)
                }
                is MojElektroResult.Failure ->
                    if (result.error == MojElektroError.NotConnected) UsageUiState.NotConnected
                    else UsageUiState.Error(mojElektroErrorMessage(result.error))
            }
            _refreshing.value = false
        }.also { started ->
            // The range was changed while this load was running: load the new one once this one has finished.
            started.invokeOnCompletion { cause ->
                if (cause == null && _period.value != range) viewModelScope.launch { load(force = false) }
            }
        }
    }

    companion object {
        private const val REUSE_MS = 5 * 60 * 1000L
    }
}

package si.semafor.app.mojelektro

import android.app.Application
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
sealed interface UsageUiState {
    data object NotConnected : UsageUiState
    data class Loading(val step: String) : UsageUiState
    data class Error(val message: String) : UsageUiState
    data class Content(val data: UsageData) : UsageUiState
}

/** State of the "Moja poraba" screen; the analysis runs off the main thread, the screen only draws. */
class UsageViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UsageRepository(app)

    private val _state = MutableStateFlow<UsageUiState>(UsageUiState.Loading("Nalagam …"))
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    private val _rangeDays = MutableStateFlow(DEFAULT_RANGE)
    val rangeDays: StateFlow<Int> = _rangeDays.asStateFlow()

    /** A refresh is running while charts stay on screen. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var job: Job? = null

    /** Called whenever the screen is opened: shows stored data, downloads only what is missing or too old. */
    fun onScreenShown() = load(force = false)

    fun refresh() = load(force = true)

    fun setRange(days: Int) {
        if (days == _rangeDays.value) return
        _rangeDays.value = days
        load(force = false)
    }

    private fun load(force: Boolean) {
        if (job?.isActive == true) return
        val range = _rangeDays.value
        job = viewModelScope.launch {
            val connected = withContext(Dispatchers.IO) { repository.isConnected() }
            if (!connected) {
                _state.value = UsageUiState.NotConnected
                return@launch
            }
            if (_state.value is UsageUiState.Content) _refreshing.value = true
            else _state.value = UsageUiState.Loading("Nalagam …")
            val result = withContext(Dispatchers.IO) {
                repository.load(range, force) { step ->
                    // Progress only while nothing is shown yet (a refresh keeps the charts on screen).
                    if (_state.value is UsageUiState.Loading) _state.value = UsageUiState.Loading(step)
                }
            }
            _state.value = when (result) {
                is MojElektroResult.Success -> UsageUiState.Content(result.data)
                is MojElektroResult.Failure ->
                    if (result.error == MojElektroError.NotConnected) UsageUiState.NotConnected
                    else UsageUiState.Error(mojElektroErrorMessage(result.error))
            }
            _refreshing.value = false
        }.also { started ->
            // The range was changed while this load was running: load the new one once this one has finished.
            started.invokeOnCompletion { cause ->
                if (cause == null && _rangeDays.value != range) viewModelScope.launch { load(force = false) }
            }
        }
    }

    companion object {
        const val DEFAULT_RANGE = 7
        val RANGES = listOf(7, 30)
    }
}

package si.semafor.app.mojelektro

import android.app.Application
import androidx.compose.runtime.Immutable
import si.semafor.app.R
import si.semafor.app.UiText
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A message under the connection form: success or error. */
@Immutable
data class MojElektroMessage(val text: UiText, val isError: Boolean)

/** What the Moj Elektro screen shows. */
@Immutable
data class MojElektroUiState(
    /** The stored connection has been read (until then the screen shows neither the form nor the connection). */
    val loaded: Boolean = false,
    /** EIMM of the stored connection; null = not connected. */
    val connectedEimm: String? = null,
    /** A request to Moj Elektro is running. */
    val busy: Boolean = false,
    val message: MojElektroMessage? = null
)

/** State of the Moj Elektro connection screen. The token itself never becomes part of this state. */
class MojElektroViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MojElektroRepository(app)

    private val _state = MutableStateFlow(MojElektroUiState())
    val state: StateFlow<MojElektroUiState> = _state.asStateFlow()

    init {
        refreshConnection()
    }

    /**
     * Reads the stored connection again (the app comes back to the foreground; the stored data may also have been
     * deleted elsewhere because it could no longer be decrypted). Not while a request is running.
     */
    fun refreshConnection() {
        if (_state.value.busy) return
        viewModelScope.launch {
            val eimm = withContext(Dispatchers.IO) { repository.connectedEimm() }
            _state.update { it.copy(loaded = true, connectedEimm = eimm) }
        }
    }

    /** Checks what the user typed and, if Moj Elektro accepts it, stores it encrypted. */
    fun connect(tokenText: String, eimmText: String) {
        if (_state.value.busy) return
        val eimm = MojElektroInput.normalizeEimm(eimmText)
        val token = MojElektroInput.normalizeToken(tokenText)
        val inputError = when {
            token == null -> UiText.res(R.string.me_input_token)
            eimm == null -> UiText.res(R.string.me_input_eimm)
            else -> null
        }
        if (inputError != null || token == null || eimm == null) {
            _state.update { it.copy(message = MojElektroMessage(inputError ?: UiText.res(R.string.me_input_token), isError = true)) }
            return
        }
        launchCheck { repository.connect(token, eimm) }
    }

    /** Checks the stored connection again. */
    fun verify() {
        if (_state.value.busy) return
        launchCheck { repository.verifyStored() }
    }

    /** Deletes the stored token and EIMM. */
    fun disconnect() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.disconnect() }
            _state.update {
                it.copy(
                    busy = false,
                    connectedEimm = null,
                    message = MojElektroMessage(UiText.res(R.string.me_disconnected), false)
                )
            }
        }
    }

    private fun launchCheck(request: () -> MojElektroResult<MeteringPoint>) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { request() }
            // The stored state may have changed (connected now, or deleted because it was unreadable).
            val eimm = withContext(Dispatchers.IO) { repository.connectedEimm() }
            val message = when (result) {
                is MojElektroResult.Success -> MojElektroMessage(successText(result.data), isError = false)
                is MojElektroResult.Failure -> MojElektroMessage(mojElektroErrorMessage(result.error), isError = true)
            }
            _state.update { it.copy(busy = false, connectedEimm = eimm, message = message) }
        }
    }
}

/** Confirmation after a successful check (pure, unit tested). */
internal fun successText(point: MeteringPoint): UiText {
    val count = point.measurementPoints.size
    val first = if (count > 0) UiText.res(R.string.me_ok_points, point.eimm, count) else UiText.res(R.string.me_ok, point.eimm)
    return when (point.has15MinuteData) {
        true -> UiText.Concat(listOf(first, UiText.res(R.string.me_ok_15_yes)))
        false -> UiText.Concat(listOf(first, UiText.res(R.string.me_ok_15_no)))
        null -> first
    }
}

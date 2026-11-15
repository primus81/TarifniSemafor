package si.semafor.app.mojelektro

import android.app.Application
import androidx.compose.runtime.Immutable
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
data class MojElektroMessage(val text: String, val isError: Boolean)

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
            token == null -> "Vnesi žeton API (brez presledkov), kot ga prikaže mojelektro.si."
            eimm == null -> "EIMM mora imeti obliko številka-številka, npr. 3-1234567."
            else -> null
        }
        if (inputError != null || token == null || eimm == null) {
            _state.update { it.copy(message = MojElektroMessage(inputError ?: "", isError = true)) }
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
                    message = MojElektroMessage("Povezava je prekinjena. Žeton in EIMM sta izbrisana iz telefona.", false)
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
internal fun successText(point: MeteringPoint): String {
    val sb = StringBuilder("Povezava deluje. Merilno mesto ${point.eimm}")
    val count = point.measurementPoints.size
    if (count > 0) sb.append(", merilne točke: ").append(count)
    sb.append('.')
    when (point.has15MinuteData) {
        true -> sb.append(" Merilno mesto ima 15-minutne meritve.")
        false -> sb.append(" Merilno mesto nima 15-minutnih meritev.")
        null -> Unit
    }
    return sb.toString()
}

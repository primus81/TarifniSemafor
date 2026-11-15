package si.semafor.app

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the "Novice" screen shows. */
sealed interface NewsUiState {
    data object Loading : NewsUiState

    @Immutable
    data class Loaded(val result: NewsResult) : NewsUiState
}

/**
 * Loading of the AGEN-RS news feed (replaces the former background thread of the Activity). The result survives
 * rotation; every visit of the screen and every refresh loads the feed again, as before.
 */
class NewsViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    /** True while a load started by pulling the list down runs (drives the pull-to-refresh spinner only). */
    private val _pullRefreshing = MutableStateFlow(false)
    val pullRefreshing: StateFlow<Boolean> = _pullRefreshing.asStateFlow()

    // Main thread only (viewModelScope uses Dispatchers.Main.immediate).
    private var job: Job? = null

    /** [fromPull] = started by the pull-to-refresh gesture. A request during a running load is covered by that load. */
    fun load(fromPull: Boolean = false) {
        if (fromPull) _pullRefreshing.value = true
        if (job?.isActive == true) return
        _state.value = NewsUiState.Loading
        job = viewModelScope.launch {
            try {
                // NewsRepo catches every failure itself and falls back to the stored feed.
                val result = withContext(Dispatchers.IO) { NewsRepo.fetchNews(getApplication()) }
                _state.value = NewsUiState.Loaded(result)
            } finally {
                _pullRefreshing.value = false
            }
        }
    }
}

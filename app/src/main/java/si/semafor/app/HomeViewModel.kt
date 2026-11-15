package si.semafor.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the home screen shows. Kept in [HomeViewModel], so it survives rotation and other configuration changes. */
sealed interface HomeUiState {
    /** No trusted data yet; a load is running or about to run. */
    data object Loading : HomeUiState

    /** [offlineFailure] = the last refresh failed and [info] is the stored data that can still be trusted. */
    data class Content(val info: Info, val best: BestBlock?, val offlineFailure: Boolean) : HomeUiState

    /** Nothing that can be trusted; [lastUpdate] = time of the last successful update, if any. */
    data class Error(val lastUpdate: Long?) : HomeUiState
}

/**
 * State and data loading of the home screen. The Activity only renders [state].
 *
 * A refresh runs in two phases:
 * 1. the current block (Repo.load + SyncRecovery): the result is shown and the pull-to-refresh spinner ends;
 * 2. the best upcoming block, the widget and the notifications/alarm: these may take longer (several requests)
 *    and no longer hold up the screen.
 * The data work of both phases is NonCancellable, so leaving the screen never skips the catch-up scheduling,
 * the widget update or the next alarm (same as the former background thread, which always ran to the end).
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** True while phase 1 runs. The Activity only uses the end (false) to dismiss the pull-to-refresh spinner. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Main thread only (viewModelScope uses Dispatchers.Main.immediate).
    private var loadJob: Job? = null
    private var lastFreshFetch = 0L
    private var lagRetryJob: Job? = null
    private var lagRetries = 0

    /**
     * Called by the Activity in onResume. If the screen was not refreshed within the last minute, the stored data is
     * shown first (or the loading state), exactly as before; then a refresh runs if the last one is older than a minute.
     * After a rotation the shown data is kept as it is.
     */
    fun onScreenResumed() {
        val keepShown = _state.value is HomeUiState.Content &&
            System.currentTimeMillis() - lastFreshFetch in 0 until THROTTLE_MS
        if (!keepShown) {
            // Read synchronously (small, usually already in memory), so the first frame shows the stored data
            // instead of a short "loading" flash.
            val cached = Repo.cached(ctx)
            _state.value = if (cached != null && Repo.dataState(cached) != DataState.UNVERIFIED) {
                HomeUiState.Content(cached, Repo.getMemoryCachedBest(), offlineFailure = false)
            } else {
                HomeUiState.Loading
            }
        }
        refresh(force = false)
    }

    /** Device clock or time zone changed: drop the throttle and re-validate the shown data. */
    fun onClockChanged() {
        lastFreshFetch = 0L
        refresh(force = true)
    }

    /** Fresh data delivered by the offline catch-up worker ([SyncRecovery]). */
    fun onRecovered(info: Info) {
        _state.value = HomeUiState.Content(info, Repo.getMemoryCachedBest(), offlineFailure = false)
    }

    /**
     * [force] = manual refresh, block boundary, new day or clock change; otherwise at most once per minute.
     * Never runs two loads at once; a request during a running load is covered by that load.
     */
    fun refresh(force: Boolean) {
        if (loadJob?.isActive == true) return
        if (!force && System.currentTimeMillis() - lastFreshFetch in 0 until THROTTLE_MS) return

        _loading.value = true
        loadJob = viewModelScope.launch {
            val appCtx = ctx
            var phase1: Phase1? = null
            try {
                val p = withContext(Dispatchers.IO + NonCancellable) {
                    val fresh = Repo.load(appCtx)
                    // Failed fetch: schedule the catch-up for when the network returns. Success: cancel a pending one.
                    val recovered = SyncRecovery.afterFetch(appCtx, fresh)
                    val cached = if (fresh != null && fresh.fresh) null else Repo.cached(appCtx)
                    Phase1(fresh, recovered, cached)
                }
                phase1 = p
                val fresh = p.fresh
                val cached = p.cached
                val memBest = Repo.getMemoryCachedBest()
                val freshState = fresh?.let { Repo.dataState(it) }
                val lagging = fresh != null && fresh.fresh && freshState == DataState.UNVERIFIED
                _state.value = when {
                    fresh != null && freshState == DataState.FRESH -> {
                        lastFreshFetch = System.currentTimeMillis()
                        HomeUiState.Content(fresh, memBest, offlineFailure = false)
                    }
                    // Downloaded, but the block does not match the Slovenian clock: never shown as current.
                    lagging -> HomeUiState.Error(fresh?.time)
                    // Stored data is shown only while it can still be trusted; otherwise say so.
                    cached != null && Repo.dataState(cached) != DataState.UNVERIFIED ->
                        HomeUiState.Content(cached, memBest, offlineFailure = true)

                    else -> HomeUiState.Error(cached?.time)
                }
                if (lagging) scheduleLagRetry() else lagRetries = 0
            } finally {
                _loading.value = false
            }

            // Phase 2: best block, widget, alarm and status bar icon.
            val result = phase1 ?: return@launch
            val best = withContext(Dispatchers.IO + NonCancellable) {
                val target = result.fresh ?: Repo.cached(appCtx)
                val best = if (target != null) Repo.findBestUpcoming(appCtx, target) else null
                SemaforWidget.render(appCtx, target, best)
                Notifier.onData(appCtx, target, result.recovered)
                if (target != null) BestUpdate(best) else null
            } ?: return@launch
            (_state.value as? HomeUiState.Content)?.let { _state.value = it.copy(best = best.best) }
        }
    }

    /**
     * The server may still answer with the previous block for a short while after a block boundary. Then the home
     * screen tries again a few times instead of waiting for the next manual refresh.
     */
    private fun scheduleLagRetry() {
        if (lagRetries >= MAX_LAG_RETRIES || lagRetryJob?.isActive == true) return
        lagRetries++
        lagRetryJob = viewModelScope.launch {
            delay(LAG_RETRY_MS)
            refresh(force = true)
        }
    }

    private data class Phase1(val fresh: Info?, val recovered: Boolean, val cached: Info?)
    private data class BestUpdate(val best: BestBlock?)

    private companion object {
        const val THROTTLE_MS = 60_000L
        const val LAG_RETRY_MS = 30_000L
        const val MAX_LAG_RETRIES = 3
    }
}

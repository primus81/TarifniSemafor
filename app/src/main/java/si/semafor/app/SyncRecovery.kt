package si.semafor.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Offline recovery for the block-change flow.
 *
 * When a fetch fails (no connection at the block boundary, server not reachable), [afterFetch] schedules ONE unique
 * work that runs as soon as the network is back ([BlockSyncRecoveryWorker]) and remembers in a flag that a catch-up is
 * pending. Whoever obtains fresh data first (alarm, widget job, manual refresh, the recovery worker) consumes the flag,
 * so the catch-up happens exactly once and a pending work is cancelled when it is no longer needed.
 */
object SyncRecovery {
    const val WORK_NAME = "block_sync_recovery"
    internal const val MAX_ATTEMPTS = 5

    private const val PREFS = "sync_recovery"
    private const val KEY_PENDING = "pending"

    /** Set by the visible MainActivity so that a catch-up refreshes the home screen immediately. */
    @Volatile
    var uiListener: ((Info) -> Unit)? = null

    /**
     * Call with the result of every [Repo.load]. Returns true if this fresh data is the catch-up after a failed fetch
     * (the caller then passes recovered = true to [Notifier.onData]).
     */
    @Synchronized
    fun afterFetch(ctx: Context, info: Info?): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (info != null && info.fresh) {
            val wasPending = prefs.getBoolean(KEY_PENDING, false)
            if (wasPending) {
                prefs.edit().putBoolean(KEY_PENDING, false).apply()
                // No longer needed: the data is already up to date (saves battery).
                WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            }
            return wasPending
        }
        schedule(ctx)
        return false
    }

    internal fun isPending(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PENDING, false)

    /** Marks the catch-up as done WITHOUT cancelling the work (it is called by the running worker itself). */
    internal fun markCaughtUp(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENDING, false).apply()
    }

    private fun schedule(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENDING, true).apply()
        val request = OneTimeWorkRequestBuilder<BlockSyncRecoveryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // KEEP: repeated failures while offline do not restart or duplicate the pending work.
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

/** Runs once the network is back: fetches the current block, updates cache, widget and home screen, notifies once. */
class BlockSyncRecoveryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        // Another path (alarm, widget job, manual refresh) may have caught up in the meantime.
        if (!SyncRecovery.isPending(ctx)) return@withContext Result.success()

        val info = Repo.load(ctx)
        if (info == null || !info.fresh) {
            // WorkManager says "connected" but the request still failed (server down, captive portal): retry with
            // exponential backoff, but not forever. The pending flag stays, so the next fresh fetch still catches up.
            return@withContext if (runAttemptCount + 1 < SyncRecovery.MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

        val best = Repo.findBestUpcoming(ctx, info)
        SemaforWidget.render(ctx, info, best)
        // Duplicate-safe: Notifier remembers the last announced block and sends at most one notification, only for
        // the CURRENT block, even if several blocks passed while the device was offline.
        Notifier.onData(ctx, info, recovered = true)
        SyncRecovery.markCaughtUp(ctx)
        SyncRecovery.uiListener?.invoke(info)
        Result.success()
    }
}

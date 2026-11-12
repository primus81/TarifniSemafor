package si.semafor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BlockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                // Needed if the block-change notification or the status bar icon is switched on.
                if (AppSettings.alarmWanted(ctx)) {
                    val info = Repo.load(ctx)
                    // Failed fetch (offline): schedule the catch-up for when the network returns. Success: cancel it.
                    val recovered = SyncRecovery.afterFetch(ctx, info)
                    // Verified transition check + planning of the next alarm + status bar icon (single place).
                    Notifier.onData(ctx, info, recovered)
                    if (info != null && info.fresh) SemaforWidget.refreshNow(ctx)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

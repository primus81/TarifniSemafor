package si.semafor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BlockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val enabled = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean("notif_enabled", false)
                if (enabled) {
                    val info = Repo.load(ctx)
                    // Verified transition check + planning of the next alarm (single place, no duplicates).
                    Notifier.onData(ctx, info)
                    if (info != null && info.fresh) SemaforWidget.refreshNow(ctx)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

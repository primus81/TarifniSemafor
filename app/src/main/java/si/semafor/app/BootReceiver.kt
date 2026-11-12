package si.semafor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val enqueue = when (intent.action) {
            // Alarms do not survive a reboot or an app update: reschedule the block-change notification.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                AppSettings.alarmWanted(ctx)
            // Device clock or time zone changed: widget and scheduled alarm may be outdated.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> true
            else -> false
        }
        if (enqueue) {
            // No network work here; the worker reloads data, renders the widget and calls Notifier.scheduleNext.
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<UpdateWorker>().build())
        }
    }
}

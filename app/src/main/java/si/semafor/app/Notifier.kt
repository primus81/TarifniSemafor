package si.semafor.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Latest a "Začel se je ..." message may be delivered after the real block start (Doze can delay alarms). */
internal const val MAX_LATENESS_MS = 30 * 60 * 1000L

/**
 * Text of the block-change notification. Only states what was verified:
 * the new block is the current block of fresh data; the direction (cheaper/more expensive)
 * is stated only when the previous block is known from the schedule that produced the alarm.
 * Blocks are ordered as elsewhere in the app: higher block number = cheaper (1 most expensive, 5 cheapest).
 */
internal fun transitionMessage(newId: Int, previousId: Int, latenessMs: Long): String = when {
    latenessMs > MAX_LATENESS_MS -> "Zdaj velja časovni blok $newId."
    previousId in 1..5 && newId > previousId -> "Začel se je ugodnejši časovni blok $newId."
    previousId in 1..5 && newId < previousId -> "Začel se je dražji časovni blok $newId."
    else -> "Začel se je časovni blok $newId."
}

object Notifier {
    const val CHANNEL_ID = "block_changes"
    private const val NOTIFICATION_ID = 2001
    private const val ALARM_REQUEST_CODE = 1002

    private const val KEY_LAST = "notif_last_key"
    private const val KEY_EXP_START = "notif_exp_start"
    private const val KEY_EXP_FROM = "notif_exp_from_id"
    private const val KEY_EXP_TO = "notif_exp_to_id"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Menjava časovnega bloka",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Obvestilo ob začetku novega časovnega bloka omrežnine (po nastavitvah v aplikaciji)."
            }
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** True if Android will actually show our notifications (permission granted, not blocked, channel not off). */
    fun canPost(ctx: Context): Boolean {
        createChannel(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /**
     * Single entry point for every place that obtains data. Fresh data first checks whether a verified
     * block transition happened (exactly once per transition, whoever sees it first), then the next alarm is planned.
     */
    fun onData(ctx: Context, info: Info?) {
        if (info != null && info.fresh) checkTransition(ctx, info)
        scheduleNext(ctx, info)
    }

    private fun checkTransition(ctx: Context, info: Info) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_enabled", false)) return
        // Data that contradicts the Slovenian clock is not verified.
        if (!Repo.coversNow(info.now)) return

        val now = System.currentTimeMillis()
        val blockStart = SloTime.previousStartAtOrBefore(now, info.now.from)
        val key = "${info.now.id}|$blockStart"
        if (prefs.getString(KEY_LAST, null) == key) return // this transition was already handled

        // A transition is verified only if it matches what the scheduled alarm announced.
        val expectedTo = prefs.getInt(KEY_EXP_TO, -1)
        val expectedStart = prefs.getLong(KEY_EXP_START, 0L)
        val previousId = prefs.getInt(KEY_EXP_FROM, -1)
        val verified =
            expectedTo == info.now.id && kotlin.math.abs(expectedStart - blockStart) < 60_000L

        prefs.edit().putString(KEY_LAST, key).apply() // never notify twice for the same transition
        if (!verified) return

        val mode = prefs.getInt("notif_mode", 0)
        val isBlockMatch = (mode == 0) || (info.now.id == 1 || info.now.id == 2)
        if (!isBlockMatch) return
        if (!canPost(ctx)) return

        post(ctx, info, previousId, now - blockStart)
    }

    private fun post(ctx: Context, info: Info, previousId: Int, latenessMs: Long) {
        val openIntent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val headline = transitionMessage(info.now.id, previousId, latenessMs)
        var details = info.now.load
        val price = PriceLists.energyPrice(PriceLists.current(), info.now.id)
        if (price != null) {
            details += ". Omrežnina za energijo: " + PriceLists.formatKwh(price)
        }

        // The notification disappears when this block ends, so it never shows outdated information.
        val untilBlockEnd = Repo.nextStartMillis(info.next.from) - System.currentTimeMillis()

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_semafor)
            .setContentTitle("Tarifni Semafor")
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(headline + "\n" + details))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (untilBlockEnd > 60_000L) builder.setTimeoutAfter(untilBlockEnd)

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked in the meantime: nothing to show, never crash.
        }
    }

    fun scheduleNext(ctx: Context, info: Info?) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notif_enabled", false)

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            ALARM_REQUEST_CODE,
            Intent(ctx, BlockChangeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!enabled) {
            am.cancel(pendingIntent)
        } else if (!canPost(ctx)) {
            // Notifications are blocked in the phone settings: do not wake the device for nothing.
            am.cancel(pendingIntent)
        } else if (info != null && Repo.dataState(info) != DataState.UNVERIFIED && Repo.coversNow(
                info.now
            )
        ) {
            val boundary = Repo.nextStartMillis(info.next.from)
            // Remember what this alarm announces, so the transition can be verified when it happens.
            prefs.edit()
                .putInt(KEY_EXP_FROM, info.now.id)
                .putInt(KEY_EXP_TO, info.next.id)
                .putLong(KEY_EXP_START, boundary)
                .apply()
            // Inexact on purpose (no exact-alarm permission); Android may delay it by a few minutes.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary + 30_000L, pendingIntent)
        }
        // Enabled but data cannot be trusted: keep the existing alarm, do not schedule from stale data.
    }
}

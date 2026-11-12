package si.semafor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** The indicator may outlive the end of its block by this long, so the alarm has time to replace it. */
internal const val STATUS_GRACE_MS = 2 * 60 * 1000L

internal fun statusTitle(blockId: Int): String = "Trenutni časovni blok: $blockId"

internal fun statusText(untilHm: String, nextBlockId: Int): String = "Do $untilHm · naslednji: $nextBlockId. blok"

/** When the indicator disappears by itself if nothing replaced it: shortly after its block ended (at least 1 min). */
internal fun statusTimeoutMs(untilBlockEndMs: Long): Long = (untilBlockEndMs + STATUS_GRACE_MS).coerceAtLeast(60_000L)

/**
 * The icon of the current block in the status bar: a silent, ongoing notification whose small icon is the logo.
 * Android draws status bar icons as a single colour mask, so the block colour is applied where Android shows
 * colour (setColor: the notification in the shade), and the text names the block. It is replaced at every block
 * change (alarm, widget job, refresh), never shows data that cannot be trusted, and disappears by itself if
 * nothing replaces it. The user can switch it off in Nastavitve ([AppSettings.statusIconEnabled]).
 */
object StatusIndicator {
    const val CHANNEL_ID = "status_indicator"
    private const val NOTIFICATION_ID = 2002

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trenutni blok v vrstici stanja",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ikona trenutnega časovnega bloka v vrstici stanja (po nastavitvah v aplikaciji)."
            setShowBadge(false)
        }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    /** Shows or refreshes the indicator for [info], or removes it when it is switched off or [info] cannot be trusted. */
    fun update(ctx: Context, info: Info?) {
        val data = info?.takeIf { Repo.dataState(it) != DataState.UNVERIFIED && Repo.coversNow(it.now) }
        if (data == null || !AppSettings.statusIconEnabled(ctx) || !Notifier.canPost(ctx, CHANNEL_ID)) {
            cancel(ctx)
            return
        }

        val nextStart = Repo.nextStartMillis(data.next.from)
        val untilEnd = nextStart - System.currentTimeMillis()
        val summary = statusText(SloTime.formatHm(nextStart), data.next.id)
        val details = StringBuilder(data.now.load)
        PriceLists.energyPrice(PriceLists.current(), data.now.id)?.let {
            details.append(". Omrežnina za energijo: ").append(PriceLists.formatKwh(it))
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_semafor)
            .setColor(Repo.color(data.now.id))
            .setContentTitle(statusTitle(data.now.id))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary + "\n" + details))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(statusTimeoutMs(untilEnd))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked in the meantime: nothing to show, never crash.
        }
    }

    fun cancel(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
    }
}

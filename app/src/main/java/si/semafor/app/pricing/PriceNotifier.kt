package si.semafor.app.pricing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import si.semafor.app.AppLanguage
import si.semafor.app.MainActivity
import si.semafor.app.R

/** Notification "the prices have changed" after a download brought new price data. */
object PriceNotifier {
    const val CHANNEL_ID = "price_changes"

    /** Intent extra for MainActivity: open the screen with this name (the "Cena elektrike" screen). */
    const val EXTRA_SCREEN = "open_screen"
    const val SCREEN_ENERGY_PRICE = "ENERGY_PRICE"
    const val SCREEN_PLANNER = "PLANNER"

    /** Not a Screen name: opens the "Semafor porabe" tab. */
    const val SCREEN_USAGE = "TAB_USAGE"
    private const val NOTIFICATION_ID = 2101

    fun createChannel(ctx: Context) {
        val res = AppLanguage.localized(ctx)
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.price_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = res.getString(R.string.price_channel_description) }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    fun notifyChanges(ctx: Context, changes: Set<PriceChange>) {
        createChannel(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val res = AppLanguage.localized(ctx)
        val lines = changes.map {
            res.getString(
                when (it) {
                    PriceChange.YOUR_PACKAGE -> R.string.price_change_package
                    PriceChange.NETWORK -> R.string.price_change_network
                    PriceChange.CHARGES -> R.string.price_change_charges
                    PriceChange.SUPPLIERS -> R.string.price_change_suppliers
                }
            )
        }
        val text = lines.joinToString("\n")
        val open = Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SCREEN, SCREEN_ENERGY_PRICE)
        val pending = PendingIntent.getActivity(
            ctx, 3, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_semafor)
            .setContentTitle(res.getString(R.string.price_change_title))
            .setContentText(lines.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked in the meantime: nothing to show, never crash.
        }
    }
}

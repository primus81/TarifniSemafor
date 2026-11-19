package si.semafor.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import si.semafor.app.AppLanguage
import si.semafor.app.MainActivity
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.PowerSummary
import si.semafor.app.planner.PlannerStore
import si.semafor.app.pricing.PriceNotifier
import si.semafor.app.pricing.PriceNow

/**
 * The user's choices for the reminders (in the backed-up "settings" file: only on/off switches and times, no
 * personal data). The block-change notification and the price-list notification keep their own settings.
 */
object ReminderSettings {
    private const val PREFS = "settings"
    private const val KEY_DEVICES = "remind_devices"
    private const val KEY_CHEAPER = "remind_cheaper"
    private const val KEY_POWER = "remind_power"
    private const val KEY_LEAD = "remind_lead_min"
    private const val KEY_QUIET = "quiet_enabled"
    private const val KEY_QUIET_FROM = "quiet_from_hour"
    private const val KEY_QUIET_TO = "quiet_to_hour"

    val LEAD_CHOICES = listOf(5, 10, 15, 30)
    val QUIET_FROM_CHOICES = listOf(21, 22, 23)
    val QUIET_TO_CHOICES = listOf(6, 7, 8)

    data class Values(
        val devices: Boolean,
        val cheaper: Boolean,
        val power: Boolean,
        val leadMin: Int,
        val quiet: Boolean,
        val quietFrom: Int,
        val quietTo: Int
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context): Values {
        val p = prefs(ctx)
        return Values(
            // Reminders for appliances the user planned: on. A reminder before every cheaper hour: off (could be too many).
            devices = p.getBoolean(KEY_DEVICES, true),
            cheaper = p.getBoolean(KEY_CHEAPER, false),
            power = p.getBoolean(KEY_POWER, true),
            leadMin = p.getInt(KEY_LEAD, 10).takeIf { it in LEAD_CHOICES } ?: 10,
            quiet = p.getBoolean(KEY_QUIET, false),
            quietFrom = p.getInt(KEY_QUIET_FROM, 22),
            quietTo = p.getInt(KEY_QUIET_TO, 7)
        )
    }

    fun save(ctx: Context, v: Values) {
        prefs(ctx).edit {
            putBoolean(KEY_DEVICES, v.devices)
            putBoolean(KEY_CHEAPER, v.cheaper)
            putBoolean(KEY_POWER, v.power)
            putInt(KEY_LEAD, v.leadMin)
            putBoolean(KEY_QUIET, v.quiet)
            putInt(KEY_QUIET_FROM, v.quietFrom)
            putInt(KEY_QUIET_TO, v.quietTo)
        }
    }

    /** Pure: is [hour] inside the quiet period from [from] to [to] (over midnight when from > to)? */
    fun inQuiet(hour: Int, v: Values): Boolean = v.quiet && SloTime.coversHour(hour, v.quietFrom, v.quietTo)
}

/** Kinds of reminders (also the dedup key prefix). */
enum class ReminderKind(val key: String) { DEVICE("dev"), CHEAPER("cheap"), POWER("pow") }

/** A reminder that is due at [atMillis]; [key] identifies it, so the same reminder is never sent twice. */
data class DueReminder(val kind: ReminderKind, val key: String, val atMillis: Long, val title: String, val text: String)

/**
 * Reminders without a permanently running process: one inexact alarm (setAndAllowWhileIdle, no exact-alarm permission)
 * for the next due reminder, recalculated whenever the plan or the prices change, at boot and after each alarm.
 */
object Reminders {
    const val CHANNEL_ID = "reminders"
    private const val STATE = "reminder_state"
    private const val KEY_SENT = "sent"
    private const val KEY_CHEAPER_AT = "cheaper_at"
    private const val KEY_CHEAPER_BLOCK = "cheaper_block"
    private const val KEY_CHEAPER_PRICE = "cheaper_price"
    private const val KEY_LAST_POST = "last_post"
    private const val REQUEST = 4001

    /** A cheaper hour is announced only if it is at least this far away (a reminder for "now" is pointless). */
    private const val MIN_AHEAD_MS = 20 * 60_000L

    /** At most one cheaper-hour reminder per this interval. */
    private const val CHEAPER_GAP_MS = 3 * 3600_000L

    private fun state(ctx: Context) = ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE)

    fun createChannel(ctx: Context) {
        val res = AppLanguage.localized(ctx)
        val channel = NotificationChannel(CHANNEL_ID, res.getString(R.string.rem_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = res.getString(R.string.rem_channel_description) }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    /** The next cheaper hour as seen by the price screen (null = none known). */
    fun saveCheaper(ctx: Context, atMillis: Long?, block: Int?, price: Double?) {
        val tooSoon = atMillis != null && atMillis - System.currentTimeMillis() < MIN_AHEAD_MS
        state(ctx).edit {
            if (atMillis == null || block == null || price == null || tooSoon) {
                remove(KEY_CHEAPER_AT); remove(KEY_CHEAPER_BLOCK); remove(KEY_CHEAPER_PRICE)
            } else {
                putLong(KEY_CHEAPER_AT, atMillis); putInt(KEY_CHEAPER_BLOCK, block); putFloat(KEY_CHEAPER_PRICE, price.toFloat())
            }
        }
        reschedule(ctx)
    }

    /** The stored next cheaper hour (start, block, price with VAT), if it is still ahead. */
    fun storedCheaper(ctx: Context, nowMillis: Long = System.currentTimeMillis()): Triple<Long, Int, Double>? {
        val st = state(ctx)
        val at = st.getLong(KEY_CHEAPER_AT, 0L)
        if (at <= nowMillis) return null
        return Triple(at, st.getInt(KEY_CHEAPER_BLOCK, 0), st.getFloat(KEY_CHEAPER_PRICE, 0f).toDouble())
    }

    /** All reminders that may become due, with their notification texts. */
    private fun candidates(ctx: Context, nowMillis: Long): List<DueReminder> {
        val v = ReminderSettings.get(ctx)
        val res = AppLanguage.localized(ctx)
        val out = ArrayList<DueReminder>()
        if (v.devices) {
            for (run in PlannerStore.plan(ctx)) {
                if (run.startMillis <= nowMillis) continue
                val at = run.startMillis - v.leadMin * 60_000L
                out += DueReminder(
                    ReminderKind.DEVICE, "dev:${run.deviceId}:${run.startMillis}", at,
                    res.getString(R.string.rem_device_title, run.name),
                    res.getString(R.string.rem_device_text, SloTime.formatHm(run.startMillis), SloTime.formatHm(run.endMillis))
                )
            }
        }
        if (v.cheaper) {
            val s = state(ctx)
            val at = s.getLong(KEY_CHEAPER_AT, 0L)
            if (at > nowMillis) {
                val price = PriceNow.formatKwh(s.getFloat(KEY_CHEAPER_PRICE, 0f).toDouble(), AppLanguage.locale(ctx))
                out += DueReminder(
                    ReminderKind.CHEAPER, "cheap:$at", at - v.leadMin * 60_000L,
                    res.getString(R.string.rem_cheaper_title),
                    res.getString(R.string.rem_cheaper_text, SloTime.formatHm(at), s.getInt(KEY_CHEAPER_BLOCK, 0), price)
                )
            }
        }
        return out
    }

    /** Plans the alarm for the next reminder that has not been sent yet. */
    fun reschedule(ctx: Context) {
        val now = System.currentTimeMillis()
        val sent = sentKeys(ctx)
        val next = candidates(ctx, now).filter { it.key !in sent }.minOfOrNull { maxOf(it.atMillis, now + 1000) }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, REQUEST, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (next != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    private fun sentKeys(ctx: Context): Set<String> = state(ctx).getStringSet(KEY_SENT, emptySet()).orEmpty()

    private fun markSent(ctx: Context, key: String, nowMillis: Long) {
        // Keys hold their time; keys older than two days are dropped, so the set stays small.
        val keep = sentKeys(ctx).filter { k -> k.substringAfterLast(':').toLongOrNull()?.let { nowMillis - it < 2 * 86_400_000L } ?: false }
        state(ctx).edit { putStringSet(KEY_SENT, (keep + key).toSet()) }
    }

    /** Called by the alarm: posts what is due now (respecting quiet hours and duplicates) and plans the next one. */
    fun onAlarm(ctx: Context) {
        val now = System.currentTimeMillis()
        val v = ReminderSettings.get(ctx)
        val sent = sentKeys(ctx)
        val quiet = ReminderSettings.inQuiet(SloTime.hourOfDay(now), v)
        for (r in candidates(ctx, now)) {
            if (r.key in sent || r.atMillis > now + 60_000L) continue
            // A reminder whose moment passed long ago (phone off) is dropped, not sent late.
            if (now - r.atMillis > 30 * 60_000L) {
                markSent(ctx, r.key, now)
                continue
            }
            if (quiet) {
                markSent(ctx, r.key, now)
                continue
            }
            if (r.kind == ReminderKind.CHEAPER && now - state(ctx).getLong(KEY_LAST_POST, 0L) < CHEAPER_GAP_MS) {
                markSent(ctx, r.key, now)
                continue
            }
            post(ctx, r)
            markSent(ctx, r.key, now)
            if (r.kind == ReminderKind.CHEAPER) state(ctx).edit { putLong(KEY_LAST_POST, now) }
        }
        reschedule(ctx)
    }

    /**
     * New delayed readings arrived: warn once per day of data if the agreed power was exceeded or nearly reached.
     * Clearly worded as a past measurement, never as a live value.
     */
    fun onPowerSummary(ctx: Context, s: PowerSummary) {
        val v = ReminderSettings.get(ctx)
        val share = s.peakShare ?: return
        if (!v.power || share < 0.9) return
        // Old readings (for example after a long time without opening the app) never cause a warning.
        if (!s.isFresh(SloTime.today())) return
        val key = "pow:${s.lastDay}:${s.peakStartMillis}"
        if (key in sentKeys(ctx)) return
        val now = System.currentTimeMillis()
        markSent(ctx, key, now)
        if (ReminderSettings.inQuiet(SloTime.hourOfDay(now), v)) return
        val res = AppLanguage.localized(ctx)
        val locale = AppLanguage.locale(ctx)
        val title = res.getString(if (share > 1.0) R.string.rem_power_over_title else R.string.rem_power_near_title)
        val text = res.getString(
            R.string.rem_power_text,
            SloTime.formatDateTime(s.peakStartMillis, AppLanguage.current(ctx)),
            String.format(locale, "%.1f", s.peakKw),
            String.format(locale, "%.1f", s.peakAgreedKw ?: 0.0),
            s.peakBlock ?: 0
        )
        post(ctx, DueReminder(ReminderKind.POWER, key, now, title, text))
    }

    private fun post(ctx: Context, r: DueReminder) {
        createChannel(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val screen = if (r.kind == ReminderKind.POWER) PriceNotifier.SCREEN_USAGE else PriceNotifier.SCREEN_PLANNER
        val open = Intent(ctx, MainActivity::class.java).putExtra(PriceNotifier.EXTRA_SCREEN, screen)
        val pi = PendingIntent.getActivity(ctx, r.kind.ordinal + 10, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_semafor)
            .setContentTitle(r.title)
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            // A reminder for a start that has passed is no longer useful.
            .setTimeoutAfter(2 * 3600_000L)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(r.key.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked in the meantime: nothing to show, never crash.
        }
    }
}

/** Fired by the reminder alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                Reminders.onAlarm(ctx.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

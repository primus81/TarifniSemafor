package si.semafor.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SemaforWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Draw immediately from stored data (add, reboot, app update), then refresh from the network.
        renderFromCache(ctx)
        schedule(ctx)
        refreshNow(ctx)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(ctx, mgr, appWidgetId, newOptions)
        // Resizing only needs another layout of the stored data, never a network request.
        renderFromCache(ctx)
    }

    override fun onEnabled(ctx: Context) = schedule(ctx)

    override fun onDisabled(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork("semafor-periodic")
        WorkManager.getInstance(ctx).cancelUniqueWork("semafor-boundary")
        WorkManager.getInstance(ctx).cancelUniqueWork("semafor-stale")
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("semafor-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<UpdateWorker>().build())
        }

        /** Redraws all widgets from stored data (no network). */
        fun renderFromCache(ctx: Context) {
            render(ctx, Repo.cached(ctx), Repo.storedBest(ctx))
        }

        fun render(ctx: Context, rawInfo: Info?, best: BestBlock? = null) {
            // Data that can no longer be trusted (expired block, too old) is never presented as current.
            val info = rawInfo?.takeIf { Repo.dataState(it) != DataState.UNVERIFIED }
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SemaforWidget::class.java))
            if (ids.isEmpty()) return

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "Recent" = successfully updated within the last 30 minutes. Older stored data is shown muted and labelled.
            val recent = info != null && Repo.isRecent(info)
            val lastUpdate = rawInfo?.time?.takeIf { it > 0L }

            // Texts in the app's language (a widget is drawn without an Activity).
            val res = AppLanguage.localized(ctx)
            val lang = AppLanguage.current(ctx)
            val locale = AppLanguage.locale(ctx)
            for (id in ids) {
                val opts = mgr.getAppWidgetOptions(id)
                val minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val isSmall = minWidth < 180
                // Very low cells cannot fit the last detail line without clipping.
                val compactHeight = minHeightDp in 1..59

                // Stretched to at least two cells in height: the tall layout with the extra lines.
                val isTall = !isSmall && minHeightDp >= 100
                val layoutRes = if (isSmall) R.layout.widget_small else if (isTall) R.layout.widget_tall else R.layout.widget_wide
                val v = RemoteViews(ctx.packageName, layoutRes)

                if (info == null) {
                    v.setInt(R.id.bg, "setColorFilter", Repo.color(0))
                    v.setTextViewText(R.id.num, res.getString(R.string.widget_no_data))
                    v.setTextViewText(
                        R.id.hours,
                        if (lastUpdate != null) res.getString(R.string.widget_last, SloTime.formatStamp(lastUpdate, lang = lang))
                        else res.getString(R.string.widget_check_connection)
                    )
                    v.setTextViewText(R.id.next_label, "")
                    v.setTextViewText(R.id.next_num, "")
                    v.setTextViewText(R.id.next_time, "")
                    v.setViewVisibility(R.id.cur_price, View.GONE)
                    if (!isSmall) {
                        v.setTextViewText(R.id.best_label, "")
                        v.setTextViewText(R.id.best_num, "")
                        v.setTextViewText(R.id.best_time, "")
                        v.setViewVisibility(R.id.best_price, View.GONE)
                    }
                    v.setContentDescription(R.id.root, res.getString(R.string.widget_no_data_spoken))
                } else {
                    val blockColor = Repo.color(info.now.id)
                    // Muted colour = stored data that was not verified recently.
                    val shownColor = if (recent) blockColor else ColorUtils.blendARGB(
                        blockColor,
                        0xFFBDBDBD.toInt(),
                        0.5f
                    )
                    v.setInt(R.id.bg, "setColorFilter", shownColor)
                    v.setTextViewText(R.id.num, res.getString(R.string.common_block, info.now.id))

                    val nextStart = Repo.nextStartMillis(info.next.from)
                    val changeTimeStr = SloTime.formatHm(nextStart)
                    v.setTextViewText(R.id.hours, res.getString(R.string.widget_until, changeTimeStr))

                    val curPrice = PriceLists.energyPrice(PriceLists.current(), info.now.id)
                    val totalNow = si.semafor.app.pricing.PriceNow.priceAt(ctx, System.currentTimeMillis(), info.now.id)
                    if (!recent) {
                        v.setTextViewText(
                            R.id.cur_price,
                            res.getString(R.string.widget_state, SloTime.formatStamp(info.time, lang = lang))
                        )
                        v.setViewVisibility(R.id.cur_price, View.VISIBLE)
                    } else if (totalNow != null) {
                        // The user set an energy price: the final price with network charge, levies and VAT.
                        v.setTextViewText(
                            R.id.cur_price,
                            res.getString(R.string.widget_total_price, si.semafor.app.pricing.PriceNow.formatKwh(totalNow.total, locale))
                        )
                        v.setViewVisibility(R.id.cur_price, View.VISIBLE)
                    } else if (curPrice != null) {
                        v.setTextViewText(R.id.cur_price, PriceLists.formatKwh(curPrice, locale))
                        v.setViewVisibility(R.id.cur_price, View.VISIBLE)
                    } else {
                        v.setViewVisibility(R.id.cur_price, View.GONE)
                    }

                    v.setTextViewText(R.id.next_label, res.getString(R.string.widget_next))
                    v.setTextViewText(R.id.next_num, res.getString(R.string.common_block, info.next.id))
                    v.setTextViewText(R.id.next_time, Repo.whenText(res, nextStart))

                    if (!isSmall) {
                        v.setTextViewText(R.id.best_label, res.getString(R.string.widget_best))

                        if (best != null) {
                            v.setTextViewText(R.id.best_num, res.getString(R.string.common_block, best.id))
                            v.setTextViewText(R.id.best_time, Repo.whenText(res, best.startMillis))
                            val bestDate = SloTime.dateOf(best.startMillis)
                            val bestPrice =
                                PriceLists.energyPrice(PriceLists.forDate(bestDate), best.id)
                            val bestTotal = si.semafor.app.pricing.PriceNow.priceAt(ctx, best.startMillis, best.id)
                            if (bestTotal != null && !compactHeight) {
                                v.setTextViewText(R.id.best_price, si.semafor.app.pricing.PriceNow.formatKwh(bestTotal.total, locale))
                                v.setViewVisibility(R.id.best_price, View.VISIBLE)
                            } else if (bestPrice != null && !compactHeight) {
                                v.setTextViewText(R.id.best_price, PriceLists.formatKwh(bestPrice, locale))
                                v.setViewVisibility(R.id.best_price, View.VISIBLE)
                            } else {
                                v.setViewVisibility(R.id.best_price, View.GONE)
                            }
                        } else {
                            v.setTextViewText(R.id.best_num, "–")
                            v.setTextViewText(R.id.best_time, "")
                            v.setViewVisibility(R.id.best_price, View.GONE)
                        }
                    }

                    v.setContentDescription(
                        R.id.root,
                        res.getString(
                            R.string.widget_spoken,
                            info.now.id,
                            changeTimeStr,
                            info.next.id,
                            Repo.whenText(res, nextStart),
                            if (recent) "" else res.getString(R.string.widget_spoken_stored, SloTime.formatStamp(info.time, lang = lang))
                        )
                    )
                }

                v.setOnClickPendingIntent(R.id.root, open)
                // The price opens the price screen; the extra lines of the tall widget open their screens.
                v.setOnClickPendingIntent(R.id.cur_price, openScreen(ctx, si.semafor.app.pricing.PriceNotifier.SCREEN_ENERGY_PRICE, 21))
                if (isTall) renderExtras(ctx, v, res, locale, lang, info?.let { best })
                mgr.updateAppWidget(id, v)
            }

            if (info != null) {
                val now = System.currentTimeMillis()

                // Refresh right after the next block starts (one job, replaced on every render).
                val nextMillis = Repo.nextStartMillis(info.next.from)
                val delayMs = (nextMillis - now + 5000L).coerceAtLeast(1000L)
                WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "semafor-boundary",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<UpdateWorker>()
                        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                        .build()
                )

                // Offline the periodic job (needs network) does not run, so the widget would keep looking current.
                // One job re-renders it when the last successful update becomes "not recent"; every newer
                // successful update replaces it, so it only fires when no update arrived in time.
                if (recent) {
                    val staleDelay =
                        (info.time + Repo.WIDGET_RECENT_MS + 5000L - now).coerceAtLeast(1000L)
                    WorkManager.getInstance(ctx).enqueueUniqueWork(
                        "semafor-stale",
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<UpdateWorker>()
                            .setInitialDelay(staleDelay, TimeUnit.MILLISECONDS)
                            .build()
                    )
                }
            }
        }
    }
}

/** The lines of the tall widget: recommendation, next planned appliance, last known power (all from local data). */
private fun renderExtras(
    ctx: Context,
    v: RemoteViews,
    res: Context,
    locale: java.util.Locale,
    lang: String,
    best: BestBlock?
) {
    val now = System.currentTimeMillis()
    val cheaper = si.semafor.app.reminders.Reminders.storedCheaper(ctx, now)
    val rec = when {
        cheaper != null -> res.getString(
            R.string.widget_rec_cheaper, SloTime.formatHm(cheaper.first), cheaper.second,
            si.semafor.app.pricing.PriceNow.formatKwh(cheaper.third, locale)
        )
        best != null && best.startMillis > now -> res.getString(R.string.widget_rec_best, best.id, Repo.whenText(res, best.startMillis))
        else -> null
    }
    v.setTextViewText(R.id.extra_rec, rec ?: "")
    v.setViewVisibility(R.id.extra_rec, if (rec == null) View.GONE else View.VISIBLE)
    v.setOnClickPendingIntent(R.id.extra_rec, openScreen(ctx, si.semafor.app.pricing.PriceNotifier.SCREEN_ENERGY_PRICE, 22))

    val run = si.semafor.app.planner.PlannerStore.nextRun(ctx, now)
    v.setTextViewText(
        R.id.extra_plan,
        if (run != null) res.getString(R.string.widget_plan, run.name, Repo.whenText(res, run.startMillis))
        else res.getString(R.string.widget_plan_none)
    )
    v.setOnClickPendingIntent(R.id.extra_plan, openScreen(ctx, si.semafor.app.pricing.PriceNotifier.SCREEN_PLANNER, 23))

    val power = si.semafor.app.mojelektro.PowerSummaryStore.get(ctx)
    if (power != null) {
        v.setTextViewText(
            R.id.extra_power,
            res.getString(R.string.widget_power, String.format(locale, "%.2f", power.lastKw), SloTime.formatStamp(power.lastStartMillis, lang = lang))
        )
        v.setViewVisibility(R.id.extra_power, View.VISIBLE)
        v.setOnClickPendingIntent(R.id.extra_power, openScreen(ctx, si.semafor.app.pricing.PriceNotifier.SCREEN_USAGE, 24))
    } else {
        v.setViewVisibility(R.id.extra_power, View.GONE)
    }
}

/** Opens the app on the screen [screen] (see MainActivity.openFromIntent); [request] keeps the intents apart. */
private fun openScreen(ctx: Context, screen: String, request: Int): PendingIntent = PendingIntent.getActivity(
    ctx, request,
    Intent(ctx, MainActivity::class.java)
        .putExtra(si.semafor.app.pricing.PriceNotifier.EXTRA_SCREEN, screen)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

class UpdateWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val info = Repo.load(applicationContext)
        // Failed fetch: schedule the catch-up for when the network returns. Success: cancel a pending one.
        val recovered = SyncRecovery.afterFetch(applicationContext, info)
        val best = Repo.findBestUpcoming(applicationContext, info)
        SemaforWidget.render(applicationContext, info, best)
        Notifier.onData(applicationContext, info, recovered)
        return Result.success()
    }
}

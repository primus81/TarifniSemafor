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

            for (id in ids) {
                val opts = mgr.getAppWidgetOptions(id)
                val minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val isSmall = minWidth < 180
                // Very low cells cannot fit the last detail line without clipping.
                val compactHeight = minHeightDp in 1..59

                val layoutRes = if (isSmall) R.layout.widget_small else R.layout.widget_wide
                val v = RemoteViews(ctx.packageName, layoutRes)

                if (info == null) {
                    v.setInt(R.id.bg, "setColorFilter", Repo.color(0))
                    v.setTextViewText(R.id.num, "Ni podatkov")
                    v.setTextViewText(
                        R.id.hours,
                        if (lastUpdate != null) "Zadnje: " + SloTime.formatStamp(lastUpdate) else "Preveri povezavo"
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
                    v.setContentDescription(
                        R.id.root,
                        "Tarifni semafor. Ni podatkov. Preveri internetno povezavo. Dotakni se za odprtje aplikacije."
                    )
                } else {
                    val blockColor = Repo.color(info.now.id)
                    // Muted colour = stored data that was not verified recently.
                    val shownColor = if (recent) blockColor else ColorUtils.blendARGB(
                        blockColor,
                        0xFFBDBDBD.toInt(),
                        0.5f
                    )
                    v.setInt(R.id.bg, "setColorFilter", shownColor)
                    v.setTextViewText(R.id.num, "${info.now.id}. blok")

                    val nextStart = Repo.nextStartMillis(info.next.from)
                    val changeTimeStr = SloTime.formatHm(nextStart)
                    v.setTextViewText(R.id.hours, "do $changeTimeStr")

                    val curPrice = PriceLists.energyPrice(PriceLists.current(), info.now.id)
                    if (!recent) {
                        v.setTextViewText(
                            R.id.cur_price,
                            "Stanje: " + SloTime.formatStamp(info.time)
                        )
                        v.setViewVisibility(R.id.cur_price, View.VISIBLE)
                    } else if (curPrice != null) {
                        v.setTextViewText(R.id.cur_price, PriceLists.formatKwh(curPrice))
                        v.setViewVisibility(R.id.cur_price, View.VISIBLE)
                    } else {
                        v.setViewVisibility(R.id.cur_price, View.GONE)
                    }

                    v.setTextViewText(R.id.next_label, "Naslednji:")
                    v.setTextViewText(R.id.next_num, "${info.next.id}. blok")
                    v.setTextViewText(R.id.next_time, Repo.whenText(nextStart))

                    if (!isSmall) {
                        v.setTextViewText(R.id.best_label, "Najugodnejši")

                        if (best != null) {
                            v.setTextViewText(R.id.best_num, "${best.id}. blok")
                            v.setTextViewText(R.id.best_time, Repo.whenText(best.startMillis))
                            val bestDate = SloTime.dateOf(best.startMillis)
                            val bestPrice =
                                PriceLists.energyPrice(PriceLists.forDate(bestDate), best.id)
                            if (bestPrice != null && !compactHeight) {
                                v.setTextViewText(R.id.best_price, PriceLists.formatKwh(bestPrice))
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
                        "Tarifni semafor. Zdaj velja ${info.now.id}. blok, do $changeTimeStr. " +
                                "Naslednji je ${info.next.id}. blok, ${Repo.whenText(nextStart)}." +
                                (if (recent) "" else " Prikazani so shranjeni podatki, stanje ${
                                    SloTime.formatStamp(
                                        info.time
                                    )
                                }.") +
                                " Dotakni se za odprtje aplikacije."
                    )
                }

                v.setOnClickPendingIntent(R.id.root, open)
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

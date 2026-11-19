package si.semafor.app.pricing

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.semafor.app.SloTime
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * When the app asks the server for new prices. Not on a fixed schedule: only when it is needed, so the server is
 * contacted a few times a year. Pure Kotlin, unit tested.
 */
object PriceSyncPolicy {
    /** Never more than one check per this interval after a successful answer. */
    const val MIN_INTERVAL_MS = 20 * 3600 * 1000L

    /** After a failed attempt (no connection, server error) the next try comes sooner. */
    const val RETRY_AFTER_FAILURE_MS = 3600 * 1000L

    /** Start checking this many days before something in use stops being valid. */
    const val LEAD_DAYS = 21L

    /** Even when nothing expires, check after this long (a supplier may change prices earlier). */
    const val ROUTINE_MS = 60 * 24 * 3600 * 1000L

    fun shouldCheck(
        nowMillis: Long,
        today: LocalDate,
        lastCheckMillis: Long,
        lastSuccessMillis: Long,
        data: PriceData?
    ): Boolean {
        val lastFailed = lastCheckMillis > lastSuccessMillis
        val wait = if (lastFailed) RETRY_AFTER_FAILURE_MS else MIN_INTERVAL_MS
        if (lastCheckMillis > 0 && nowMillis - lastCheckMillis in 0 until wait) return false
        // Never answered by the server yet (first start), or no usable data at all.
        if (lastSuccessMillis <= 0 || data == null) return true
        val fileEnd = data.validTo
        if (fileEnd != null && today.isAfter(fileEnd)) return true
        val expiry = data.earliestExpiry(today)
        return expiry != null && !today.isBefore(expiry.minusDays(LEAD_DAYS)) || nowMillis - lastSuccessMillis !in 0 until ROUTINE_MS
    }
}

/** What changed between two versions of the price data, for the notification. */
enum class PriceChange { NETWORK, CHARGES, YOUR_PACKAGE, SUPPLIERS }

internal object PriceDiff {
    fun between(old: PriceData?, new: PriceData, today: LocalDate, packageId: String?): Set<PriceChange> {
        if (old == null) return emptySet()
        val changes = LinkedHashSet<PriceChange>()
        if (old.networkOn(today)?.rows != new.networkOn(today)?.rows || old.network.size != new.network.size) {
            changes += PriceChange.NETWORK
        }
        val chargesChanged = old.vatRate != new.vatRate ||
            ChargeId.entries.any { old.charges[it]?.map { c -> c.value to c.validFrom } != new.charges[it]?.map { c -> c.value to c.validFrom } }
        if (chargesChanged) changes += PriceChange.CHARGES
        if (packageId != null) {
            val a = old.findPackage(packageId)
            val b = new.findPackage(packageId)
            if (a?.prices != b?.prices || a?.monthlyFee != b?.monthlyFee || a?.monthlyExtras != b?.monthlyExtras || a?.validTo != b?.validTo) changes += PriceChange.YOUR_PACKAGE
        }
        val oldPackages = old.suppliers.flatMap { s -> s.packages.map { it.id to it.prices } }.toSet()
        val newPackages = new.suppliers.flatMap { s -> s.packages.map { it.id to it.prices } }.toSet()
        if (oldPackages != newPackages) changes += PriceChange.SUPPLIERS
        return changes
    }
}

/**
 * Downloads "ceniki.json" from the app's own website. Privacy (see the privacy policy): a plain GET of a public static
 * file, nothing else is sent. No query parameters, no cookies, no identifiers, no caching headers, and a generic
 * User-Agent instead of the default one (which would contain the phone model and the Android version).
 */
object PriceSync {
    const val URL_PRICES = "https://tarifnisemafor.si/api/ceniki.json"
    const val USER_AGENT = "TarifniSemafor"
    private const val MAX_BYTES = 256 * 1024
    private const val PREFS = "pricing_sync"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LAST_SUCCESS = "last_success"

    sealed interface Result {
        /** Newer data was stored; [changes] = what differs from before (for the notification). */
        data class Updated(val changes: Set<PriceChange>) : Result
        /** The server answered; [sameAsBuiltIn] = its file is the one built into the app. */
        data class Unchanged(val sameAsBuiltIn: Boolean) : Result

        /** No usable answer, with the reason (shown to the user). */
        data class Failed(val reason: FailReason, val httpCode: Int = 0) : Result
    }

    enum class FailReason { NO_CONNECTION, HTTP_ERROR, INVALID_DATA }

    private sealed interface Download {
        data class Ok(val text: String) : Download
        data class Error(val reason: FailReason, val code: Int = 0) : Download
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastCheck(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_CHECK, 0L)
    fun lastSuccess(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_SUCCESS, 0L)

    fun shouldCheck(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        return PriceSyncPolicy.shouldCheck(now, SloTime.today(), lastCheck(ctx), lastSuccess(ctx), PriceRepository.get(ctx))
    }

    /** One check (blocking; call off the main thread). */
    fun syncNow(ctx: Context): Result {
        val now = System.currentTimeMillis()
        prefs(ctx).edit { putLong(KEY_LAST_CHECK, now) }
        val text = when (val d = download()) {
            is Download.Ok -> d.text
            is Download.Error -> return Result.Failed(d.reason, d.code)
        }
        val parsed = PriceDataParser.parse(text) ?: return Result.Failed(FailReason.INVALID_DATA)
        prefs(ctx).edit { putLong(KEY_LAST_SUCCESS, now) }
        val before = PriceRepository.get(ctx)
        val same = !PriceRepository.hasDownloaded(ctx) && before != null && parsed.updatedAtMillis == before.updatedAtMillis
        if (before != null && parsed.updatedAtMillis <= before.updatedAtMillis) return Result.Unchanged(same)
        if (!PriceRepository.storeIfNewer(ctx, text, parsed)) return Result.Unchanged(false)
        val packageId = (PricingSettings.setup(ctx).source as? EnergySource.Package)?.id
        return Result.Updated(PriceDiff.between(before, parsed, SloTime.today(), packageId))
    }

    private fun download(): Download {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(URL_PRICES).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) return Download.Error(FailReason.HTTP_ERROR, code)
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return Download.Error(FailReason.INVALID_DATA)
                }
                val text = out.toString(Charsets.UTF_8.name())
                if (text.isBlank()) Download.Error(FailReason.INVALID_DATA) else Download.Ok(text)
            }
        } catch (e: IOException) {
            Download.Error(FailReason.NO_CONNECTION)
        } catch (e: RuntimeException) {
            Download.Error(FailReason.NO_CONNECTION)
        } finally {
            conn?.disconnect()
        }
    }

    // ---- Scheduling ----

    private const val WORK_DAILY = "price_sync_daily"
    private const val WORK_NOW = "price_sync_now"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * A daily wake-up that only decides locally whether a check is due (no network traffic unless [PriceSyncPolicy]
     * says so), plus an immediate check at the start of the app when one is due.
     */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            WORK_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceSyncWorker>(1, TimeUnit.DAYS).setConstraints(networkConstraint).build()
        )
        if (shouldCheck(ctx)) checkSoon(ctx)
    }

    /** One check as soon as there is a connection (also the "Preveri zdaj" button). */
    fun checkSoon(ctx: Context, manual: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<PriceSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf(PriceSyncWorker.KEY_MANUAL to manual))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Runs [PriceSync] when it is due (or when the user asked) and notifies about changed prices. */
class PriceSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !PriceSync.shouldCheck(ctx)) return@withContext Result.success()
        when (val r = PriceSync.syncNow(ctx)) {
            is PriceSync.Result.Updated -> {
                if (r.changes.isNotEmpty() && PricingSettings.notifyChanges(ctx)) PriceNotifier.notifyChanges(ctx, r.changes)
                Result.success()
            }
            is PriceSync.Result.Unchanged -> Result.success()
            // A manual check retries a few times with back-off; the daily one simply tries again later.
            is PriceSync.Result.Failed -> if (manual && runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}

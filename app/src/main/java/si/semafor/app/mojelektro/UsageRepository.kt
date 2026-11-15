package si.semafor.app.mojelektro

import android.content.Context
import androidx.compose.runtime.Immutable
import si.semafor.app.DataState
import si.semafor.app.Repo
import si.semafor.app.SloTime
import java.time.LocalDate

/** Everything the "Moj semafor porabe" screen shows, already analysed. */
@Immutable
data class UsageData(
    /** The connected metering point. */
    val eimm: String,
    val rangeDays: Int,
    /** When the readings were downloaded from Moj Elektro. */
    val fetchedAtMillis: Long,
    /** True if a refresh failed and the stored readings are shown instead. */
    val fromCacheAfterError: Boolean,
    val label: IntervalLabel,
    /** The latest day with all 15-minute readings (the data is delayed). */
    val lastDay: LocalDate?,
    /** Block that is running now (from the app's URO data); null if not known. */
    val currentBlockId: Int?,
    val agreed: AgreedPower?,
    /** When the agreed power was read from Moj Elektro (0 = never). */
    val agreedFetchedAtMillis: Long,
    /** 15-minute intervals of every day of the range that has readings, in date order (blocks from URO). */
    val profiles: List<DayProfile>,
    /** The highest interval of each day of [profiles]. */
    val dailyPeaks: List<DailyPeak>,
    /** Agreed power, highest interval and excesses per block over [profiles]. */
    val blockRows: List<BlockPowerRow>,
    val registers: RegisterSummary,
    /** Descriptions of the status codes in [registers] (Moj Elektro catalogue); empty if not available. */
    val qualityDescriptions: Map<String, String>,
    /** Energy per day and block in the chosen range. */
    val daily: List<DailyBlocks>,
    /** Taken vs delivered energy of [lastDay]; null if the metering point delivers no energy. */
    val production: ProductionDay?
) {
    /** 15-minute intervals of [lastDay] (the latest complete day). */
    val peakDay: DayProfile? get() = lastDay?.let { d -> profiles.firstOrNull { it.date == d } }
}

/**
 * Loads, caches and analyses the readings for the "Moja poraba" screen.
 * Requests: Moj Elektro only when the cache is missing, too old (6 h) or does not cover the chosen range; the URO
 * block schedule only for days not seen before. Blocking: call from Dispatchers.IO.
 */
class UsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val repository = MojElektroRepository(appContext)
    private val cache = UsageCache(appContext)
    private val schedules = BlockSchedules(appContext)

    fun isConnected(): Boolean = repository.isConnected()

    fun load(rangeDays: Int, force: Boolean, onProgress: (String) -> Unit): MojElektroResult<UsageData> {
        val eimm = repository.connectedEimm() ?: return MojElektroResult.Failure(MojElektroError.NotConnected)
        val now = System.currentTimeMillis()
        val end = SloTime.today()
        val start = end.minusDays(rangeDays.toLong())

        val cached = cache.load()?.takeIf { it.eimm == eimm }
        val covers = cached != null && !cached.start.isAfter(start) && !cached.end.isBefore(end.minusDays(1))
        val fresh = cached != null && (now - cached.fetchedAtMillis) in 0 until CACHE_MAX_AGE_MS
        var fromCacheAfterError = false

        val snapshot = if (cached != null && covers && fresh && !force) {
            cached
        } else {
            onProgress("Prenašam meritve z Moj Elektro …")
            when (val f = repository.fifteenMinuteReadings(start, end)) {
                is MojElektroResult.Failure -> {
                    if (cached == null || f.error == MojElektroError.NotConnected ||
                        f.error == MojElektroError.StorageUnavailable
                    ) {
                        return f
                    }
                    fromCacheAfterError = true
                    cached
                }

                is MojElektroResult.Success -> {
                    // Daily states one day earlier, so the first day has a starting state as well. Not essential.
                    val daily = (repository.dailyStates(start.minusDays(1), end) as? MojElektroResult.Success)?.data
                        ?: MeterReadings(null, null, emptyList())
                    val freshAgreed = cached?.takeIf { (now - it.agreedFetchedAtMillis) in 0 until AGREED_MAX_AGE_MS }
                    val (agreed, agreedTime) = if (freshAgreed != null) {
                        freshAgreed.agreed to freshAgreed.agreedFetchedAtMillis
                    } else {
                        onProgress("Prenašam dogovorjeno moč …")
                        when (val a = repository.currentAgreedPower(now)) {
                            is MojElektroResult.Success -> a.data to now
                            is MojElektroResult.Failure -> (cached?.agreed) to (cached?.agreedFetchedAtMillis ?: 0L)
                        }
                    }
                    UsageCache.Snapshot(eimm, now, start, end, f.data, daily, agreed, agreedTime).also(cache::save)
                }
            }
        }

        return MojElektroResult.Success(analyse(snapshot, rangeDays, start, fromCacheAfterError, onProgress))
    }

    private fun analyse(
        s: UsageCache.Snapshot,
        rangeDays: Int,
        start: LocalDate,
        fromCacheAfterError: Boolean,
        onProgress: (String) -> Unit
    ): UsageData {
        val codes = (repository.registerCodes() as? MojElektroResult.Success)?.data.orEmpty()
        fun series(of: MeterReadings, reg: ReadingRegister): List<Reading> =
            codes[reg]?.let { code -> of.series.filter { it.readingType == code }.flatMap { it.readings } }.orEmpty()

        val imports = series(s.fifteen, ReadingRegister.IMPORT_15MIN)
        val exports = series(s.fifteen, ReadingRegister.EXPORT_15MIN)
        val dailyStates = series(s.daily, ReadingRegister.IMPORT_DAILY_STATE)

        val label = UsageAnalysis.detectLabel(imports, dailyStates, s.start)
        val byDate = UsageAnalysis.groupByDate(imports, label).filterKeys { !it.isBefore(start) }
        val exportByDate = UsageAnalysis.groupByDate(exports, label)

        val scheduleMap = if (label == IntervalLabel.UNKNOWN) emptyMap() else {
            schedules.forDates(byDate.keys) { done, total ->
                onProgress("Pripravljam časovne bloke (${done + 1}/$total) …")
            }
        }
        val lastDay = UsageAnalysis.lastCompleteDay(byDate)
        val profiles = byDate.keys.sorted().map { date ->
            UsageAnalysis.dayProfile(date, byDate.getValue(date), scheduleMap[date], s.agreed, label)
        }
        val hasExport = exportByDate.values.any { day -> day.any { it.second > 0.0 } }
        val production = if (hasExport && lastDay != null) {
            UsageAnalysis.productionDay(lastDay, byDate.getValue(lastDay), exportByDate[lastDay].orEmpty())
        } else {
            null
        }
        val registers = UsageAnalysis.registerSummary(imports, exports, label, start, hasExport)
        val currentBlock = Repo.cached(appContext)
            ?.takeIf { Repo.dataState(it) != DataState.UNVERIFIED }
            ?.now?.id

        return UsageData(
            eimm = s.eimm,
            rangeDays = rangeDays,
            fetchedAtMillis = s.fetchedAtMillis,
            fromCacheAfterError = fromCacheAfterError,
            label = label,
            lastDay = lastDay,
            currentBlockId = currentBlock,
            agreed = s.agreed,
            agreedFetchedAtMillis = s.agreedFetchedAtMillis,
            profiles = profiles,
            dailyPeaks = UsageAnalysis.dailyPeaks(profiles),
            blockRows = UsageAnalysis.blockPowerRows(profiles, s.agreed),
            registers = registers,
            qualityDescriptions = if (registers.flaggedCount > 0) repository.qualityDescriptions() else emptyMap(),
            daily = UsageAnalysis.dailyByBlocks(byDate, scheduleMap, label),
            production = production
        )
    }

    private companion object {
        /** The data is delayed by about a day anyway; downloading it more often than this brings nothing new. */
        const val CACHE_MAX_AGE_MS = 6 * 3600 * 1000L
        const val AGREED_MAX_AGE_MS = 24 * 3600 * 1000L
    }
}

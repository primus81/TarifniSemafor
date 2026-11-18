package si.semafor.app.pricing

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import si.semafor.app.PriceList
import si.semafor.app.PriceLists
import java.io.File
import java.io.IOException

/**
 * Offline-first store of the price data. Two copies exist: the one built into the app (assets/ceniki_fallback.json,
 * always valid, used at the first start without internet) and the last downloaded one (files/pricing/ceniki.json).
 * The newer of the two (by updatedAt) is used. A downloaded file is stored only after [PriceDataParser] accepted it.
 */
object PriceRepository {
    private const val ASSET = "ceniki_fallback.json"
    private const val DIR = "pricing"
    private const val FILE = "ceniki.json"

    private val lock = Any()

    @Volatile
    private var current: PriceData? = null

    @Volatile
    private var downloadedInUse = false

    private val _data = MutableStateFlow<PriceData?>(null)

    /** The data in use; the screens follow it (a background download updates them). */
    val data: StateFlow<PriceData?> = _data.asStateFlow()

    /** Loads the data once per process (small file) and publishes the network tariffs to [PriceLists]. */
    fun init(ctx: Context): PriceData? = get(ctx)

    fun get(ctx: Context): PriceData? {
        current?.let { return it }
        synchronized(lock) {
            current?.let { return it }
            val builtIn = readAsset(ctx)
            val downloaded = readFile(ctx)
            val use = if (downloaded != null && (builtIn == null || downloaded.updatedAtMillis >= builtIn.updatedAtMillis)) downloaded else builtIn
            downloadedInUse = use != null && use === downloaded
            publish(use)
            return use
        }
    }

    /** True if the data in use came from the server (not only the copy built into the app). */
    fun hasDownloaded(ctx: Context): Boolean {
        get(ctx)
        return downloadedInUse
    }

    /**
     * Stores a validated download if it is newer than the data in use; true if it was stored and is now in use.
     * The write is atomic: an interrupted write never leaves a broken file.
     */
    fun storeIfNewer(ctx: Context, text: String, parsed: PriceData): Boolean = synchronized(lock) {
        val old = get(ctx)
        if (old != null && parsed.updatedAtMillis <= old.updatedAtMillis) return@synchronized false
        val file = AtomicFile(file(ctx))
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            return@synchronized false
        }
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            return@synchronized false
        }
        downloadedInUse = true
        publish(parsed)
        true
    }

    private fun publish(d: PriceData?) {
        current = d
        _data.value = d
        PriceLists.setDownloaded(d?.network?.map { toPriceList(it) }.orEmpty())
    }

    internal fun toPriceList(n: NetworkTariff): PriceList = PriceList(
        validFrom = n.validFrom,
        validTo = n.validTo,
        rows = n.rows.map { si.semafor.app.PriceRow(it.block, it.powerEurPerKwMonth, it.energyEurPerKwh) },
        note = n.note,
        sourceUrl = n.sourceUrl,
        noteEn = n.noteEn
    )

    private fun file(ctx: Context): File = File(File(ctx.filesDir, DIR).apply { mkdirs() }, FILE)

    private fun readFile(ctx: Context): PriceData? = try {
        val f = file(ctx)
        if (!f.exists()) null else PriceDataParser.parse(AtomicFile(f).readFully().toString(Charsets.UTF_8))
    } catch (e: IOException) {
        null
    }

    private fun readAsset(ctx: Context): PriceData? = try {
        ctx.assets.open(ASSET).use { PriceDataParser.parse(it.readBytes().toString(Charsets.UTF_8)) }
    } catch (e: IOException) {
        null
    }
}

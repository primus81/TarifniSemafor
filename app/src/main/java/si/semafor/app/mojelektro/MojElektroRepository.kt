package si.semafor.app.mojelektro

import android.content.Context
import java.time.LocalDate

/** Checks of what the user typed (pure, unit tested). The API's own rules are used, nothing is guessed. */
internal object MojElektroInput {
    /** EIMM format from the API description ("\d{1,2}-\d+"), e.g. 3-1234567. */
    private val EIMM = Regex("^\\d{1,2}-\\d+$")

    /** Generous upper bound; the API does not document the token length. */
    private const val MAX_TOKEN_LENGTH = 2048

    /** Trimmed EIMM, or null if it does not have the documented form. */
    fun normalizeEimm(text: String): String? = text.trim().takeIf { EIMM.matches(it) }

    /**
     * Trimmed token, or null if it is empty, too long or contains spaces or control characters (a copy mistake, and
     * such characters must never reach an HTTP header).
     */
    fun normalizeToken(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_TOKEN_LENGTH) return null
        if (t.any { it.isWhitespace() || it.isISOControl() }) return null
        return t
    }
}

/**
 * The optional Moj Elektro connection: the only place the rest of the app talks to.
 *
 * Everything stays on the device: the token and EIMM are stored encrypted ([MojElektroCredentialStore]) and are sent
 * only to the official Moj Elektro API ([MojElektroClient.BASE_URL]), never to any other server. The app works the same
 * without a connection.
 *
 * All functions block (network, Keystore): call them from Dispatchers.IO.
 */
class MojElektroRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = MojElektroCredentialStore(appContext)

    // Public, non-personal metadata (register codes of the official catalogue); plain private preferences.
    private val meta = appContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
    private val qualities = appContext.getSharedPreferences(QUALITIES_PREFS, Context.MODE_PRIVATE)

    /** True if a connection is stored (cheap, no decryption and no network). */
    fun isConnected(): Boolean = store.hasCredentials()

    /** The stored EIMM, or null if there is no (readable) connection. */
    fun connectedEimm(): String? = (store.load() as? MojElektroCredentialStore.Loaded.Present)?.credentials?.eimm

    /**
     * Checks [token] + [eimm] against the API (GET /merilno-mesto/{EIMM}) WITHOUT storing anything.
     * Success = the token is accepted and gives access to this metering point.
     */
    fun testConnection(token: String, eimm: String): MojElektroResult<MeteringPoint> =
        mapResponse(MojElektroClient.meteringPoint(token, eimm), MojElektroParser::meteringPoint)

    /** [testConnection]; only on success the credentials are stored (encrypted). */
    fun connect(token: String, eimm: String): MojElektroResult<MeteringPoint> {
        val result = testConnection(token, eimm)
        if (result is MojElektroResult.Success && !store.save(MojElektroCredentials(token, eimm))) {
            return MojElektroResult.Failure(MojElektroError.StorageUnavailable)
        }
        return result
    }

    /** Checks the stored connection again (the token may have been revoked on mojelektro.si). */
    fun verifyStored(): MojElektroResult<MeteringPoint> = withCredentials { c -> testConnection(c.token, c.eimm) }

    /** Deletes the stored token and EIMM and every cached reading of the connected metering point. */
    fun disconnect() {
        UsageCache(appContext).clear()
        store.clear()
    }

    /** Details of the connected metering point. */
    fun meteringPoint(): MojElektroResult<MeteringPoint> = verifyStored()

    /** Details of a measurement point, including the agreed power per time block. */
    fun measurementPoint(gsrn: String): MojElektroResult<MeasurementPoint> = withCredentials { c ->
        mapResponse(MojElektroClient.measurementPoint(c.token, gsrn), MojElektroParser::measurementPoint)
    }

    /** Catalogue of the registers that can be requested in [meterReadings]. */
    fun readingTypes(): MojElektroResult<List<ReadingTypeInfo>> = withCredentials { c ->
        mapResponse(MojElektroClient.readingTypes(c.token), MojElektroParser::readingTypes)
    }

    /**
     * 15-minute data or daily states of [readingTypes] (codes from [readingTypes()]) for [usagePoint] (EIMM or GSRN;
     * default: the connected EIMM), from [start] to [end]. The data is not real-time: the API delivers it with a delay.
     */
    fun meterReadings(
        start: LocalDate,
        end: LocalDate,
        readingTypes: List<String>,
        usagePoint: String? = null
    ): MojElektroResult<MeterReadings> {
        if (end.isBefore(start)) return MojElektroResult.Failure(MojElektroError.InvalidRequest(null))
        return withCredentials { c ->
            mapResponse(
                MojElektroClient.meterReadings(c.token, usagePoint ?: c.eimm, start, end, readingTypes),
                MojElektroParser::meterReadings
            )
        }
    }

    /**
     * Codes of the registers the app uses, looked up in the official catalogue by their labels (A+, A-, A+_T0, A-_T0).
     * The catalogue is public metadata; it is kept for a week so it is not requested every time.
     */
    fun registerCodes(): MojElektroResult<Map<ReadingRegister, String>> {
        cachedRegisterCodes()?.let { return MojElektroResult.Success(it) }
        return when (val r = readingTypes()) {
            is MojElektroResult.Failure -> r
            is MojElektroResult.Success -> {
                val codes = ReadingRegister.resolve(r.data)
                if (ReadingRegister.IMPORT_15MIN !in codes) {
                    MojElektroResult.Failure(MojElektroError.InvalidResponse)
                } else {
                    saveRegisterCodes(codes)
                    MojElektroResult.Success(codes)
                }
            }
        }
    }

    /**
     * Descriptions of the reading status codes (public catalogue, no personal data), kept for a week in plain private
     * preferences. Not essential: any failure gives the stored descriptions or an empty map.
     */
    fun qualityDescriptions(): Map<String, String> {
        val age = System.currentTimeMillis() - qualities.getLong(KEY_QUALITIES_TIME, 0L)
        val stored = qualities.all.filterKeys { it.startsWith(QUALITY_PREFIX) }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(QUALITY_PREFIX) to it } }
            .toMap()
        if (stored.isNotEmpty() && age in 0 until CODES_MAX_AGE_MS) return stored
        val fetched = withCredentials { c ->
            mapResponse(MojElektroClient.readingQualities(c.token), MojElektroParser::readingQualities)
        }
        val map = (fetched as? MojElektroResult.Success)?.data ?: return stored
        val e = qualities.edit().clear()
        map.forEach { (code, text) -> e.putString(QUALITY_PREFIX + code, text) }
        e.putLong(KEY_QUALITIES_TIME, System.currentTimeMillis()).apply()
        return map
    }

    /** 15-minute energy taken from (A+) and, if the catalogue has it, delivered to (A-) the grid. */
    fun fifteenMinuteReadings(start: LocalDate, end: LocalDate): MojElektroResult<MeterReadings> =
        readingsOf(start, end, listOf(ReadingRegister.IMPORT_15MIN, ReadingRegister.EXPORT_15MIN))

    /** Daily meter states (24 h) of taken (A+_T0) and delivered (A-_T0) energy. */
    fun dailyStates(start: LocalDate, end: LocalDate): MojElektroResult<MeterReadings> =
        readingsOf(start, end, listOf(ReadingRegister.IMPORT_DAILY_STATE, ReadingRegister.EXPORT_DAILY_STATE))

    /**
     * The agreed power (dogovorjena moč) valid at [nowMillis]: the measurement points of the connected metering point
     * are read until one of them has a valid agreed power. Success(null) = the API lists none.
     */
    fun currentAgreedPower(nowMillis: Long): MojElektroResult<AgreedPower?> {
        val point = when (val r = meteringPoint()) {
            is MojElektroResult.Failure -> return r
            is MojElektroResult.Success -> r.data
        }
        for (ref in point.measurementPoints) {
            when (val r = measurementPoint(ref.gsrn)) {
                is MojElektroResult.Failure -> if (r.error != MojElektroError.NotFound) return r
                is MojElektroResult.Success ->
                    UsageAnalysis.currentAgreedPower(r.data.agreedPowers, nowMillis)
                        ?.let { return MojElektroResult.Success(it) }
            }
        }
        return MojElektroResult.Success(null)
    }

    private fun readingsOf(
        start: LocalDate,
        end: LocalDate,
        registers: List<ReadingRegister>
    ): MojElektroResult<MeterReadings> {
        val codes = when (val r = registerCodes()) {
            is MojElektroResult.Failure -> return r
            is MojElektroResult.Success -> registers.mapNotNull { r.data[it] }
        }
        if (codes.isEmpty()) return MojElektroResult.Success(MeterReadings(null, null, emptyList()))
        return meterReadings(start, end, codes)
    }

    private fun cachedRegisterCodes(): Map<ReadingRegister, String>? {
        val time = meta.getLong(KEY_CODES_TIME, 0L)
        val age = System.currentTimeMillis() - time
        if (age !in 0 until CODES_MAX_AGE_MS) return null
        val codes = ReadingRegister.entries.mapNotNull { reg -> meta.getString("code_${reg.name}", null)?.let { reg to it } }
            .toMap()
        return codes.takeIf { ReadingRegister.IMPORT_15MIN in it }
    }

    private fun saveRegisterCodes(codes: Map<ReadingRegister, String>) {
        val e = meta.edit().clear()
        codes.forEach { (reg, code) -> e.putString("code_${reg.name}", code) }
        e.putLong(KEY_CODES_TIME, System.currentTimeMillis()).apply()
    }

    private companion object {
        const val META_PREFS = "mojelektro_meta"
        const val KEY_CODES_TIME = "codes_time"
        const val CODES_MAX_AGE_MS = 7 * 24 * 3600 * 1000L
        const val QUALITIES_PREFS = "mojelektro_qualities"
        const val KEY_QUALITIES_TIME = "time"
        const val QUALITY_PREFIX = "q_"
    }

    private fun <T> withCredentials(block: (MojElektroCredentials) -> MojElektroResult<T>): MojElektroResult<T> =
        when (val loaded = store.load()) {
            MojElektroCredentialStore.Loaded.None -> MojElektroResult.Failure(MojElektroError.NotConnected)
            MojElektroCredentialStore.Loaded.Unreadable -> MojElektroResult.Failure(MojElektroError.StorageUnavailable)
            is MojElektroCredentialStore.Loaded.Present -> block(loaded.credentials)
        }
}

/** Slovenian explanation of an error for the user (second person singular). Pure, unit tested. */
internal fun mojElektroErrorMessage(error: MojElektroError): String = when (error) {
    is MojElektroError.InvalidRequest ->
        "Strežnik Moj Elektro je zavrnil zahtevo. Preveri vnesene podatke." +
            (error.code?.let { " (koda $it)" } ?: "")

    MojElektroError.Unauthorized ->
        "Žeton ni bil sprejet. Preveri, ali si ga kopiral v celoti in ali je še veljaven."

    MojElektroError.Forbidden ->
        "Žeton ne omogoča dostopa do tega merilnega mesta. Preveri žeton in EIMM."

    MojElektroError.NotFound -> "Merilnega mesta s tem EIMM ni mogoče najti. Preveri EIMM."
    is MojElektroError.Server ->
        "Strežnik Moj Elektro trenutno ne odgovarja pravilno (napaka ${error.httpStatus}). Poskusi pozneje."

    MojElektroError.Network -> "Povezava s strežnikom Moj Elektro ni uspela. Preveri internetno povezavo."
    MojElektroError.Timeout -> "Strežnik Moj Elektro ni odgovoril pravočasno. Poskusi znova čez nekaj minut."
    MojElektroError.InvalidResponse -> "Odgovor strežnika Moj Elektro ni v pričakovani obliki. Poskusi pozneje."
    MojElektroError.NotConnected -> "Merilno mesto ni povezano."
    MojElektroError.StorageUnavailable ->
        "Shranjene povezave ni bilo mogoče prebrati, zato je izbrisana. Poveži merilno mesto znova."
}

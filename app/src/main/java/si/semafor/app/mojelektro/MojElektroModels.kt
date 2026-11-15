package si.semafor.app.mojelektro

import java.math.BigDecimal

/*
 * Data models of the optional Moj Elektro connection.
 *
 * Source of every field: the official OpenAPI description of the Moj Elektro API
 * (https://docs.informatika.si/mojelektro/api/mojelektro-openapi.yaml, version 1.0).
 *
 * Privacy: only what the app needs is modelled. Personal data that the API also returns (owner, payer, addressee,
 * tax numbers, addresses, contacts) is deliberately NOT parsed, kept or stored anywhere.
 */

/** What the user typed to connect: the read-only API token and the unique metering point identifier (EIMM). */
data class MojElektroCredentials(val token: String, val eimm: String) {
    // Never print the token (logs, crash reports, toString of a containing object).
    override fun toString(): String = "MojElektroCredentials(eimm=$eimm, token=***)"
}

/** One measurement point (merilna točka) listed in a metering point: GSRN and kind (OMTO or MTP). */
data class MeasurementPointRef(val gsrn: String, val kind: String?)

/** Metering point (merilno mesto), technical subset. Answer of GET /merilno-mesto/{EIMM}. */
data class MeteringPoint(
    /** Unique metering point identifier, e.g. 3-1234567. */
    val eimm: String,
    /** GSRN of the metering point; can be used as usagePoint of GET /meter-readings. */
    val gsrn: String?,
    val measurementPoints: List<MeasurementPointRef>,
    /** "obstoj15MinutneMeritve": the metering point has 15-minute data; null = not stated. */
    val has15MinuteData: Boolean?,
    /** "daljinskoCitanje": the meter is read remotely; null = not stated. */
    val remoteReading: Boolean?
)

/**
 * Agreed power (dogovorjena moč) of one validity period, in kW per time block 1..5 exactly as the API returns it.
 * [blockKw] always has 5 entries (index 0 = block 1); an entry is null when the API did not send that value.
 */
data class AgreedPower(
    /** ISO date-time with offset, as sent by the API ("datumOd"). */
    val validFrom: String?,
    /** ISO date-time with offset, as sent by the API ("datumDo"). */
    val validTo: String?,
    val blockKw: List<BigDecimal?>,
    /** "veljavnost" */
    val valid: Boolean?
)

/** Measurement point (merilna točka), contract subset. Answer of GET /merilna-tocka/{GSRN}. */
data class MeasurementPoint(
    val gsrn: String,
    val name: String?,
    val kind: String?,
    /** "uporabniskaSkupina" */
    val userGroup: String?,
    val agreedPowers: List<AgreedPower>
)

/** "vrsta" of a register in GET /reading-type. */
enum class ReadingKind { KOLICINA, STANJE, UNKNOWN }

/**
 * One register of the catalogue GET /reading-type. The API says the registers that can be requested are described
 * there, so the app reads the catalogue instead of hard-coding register codes.
 */
data class ReadingTypeInfo(
    /** The value used in option=ReadingType=<code>. */
    val code: String,
    val name: String?,
    val label: String?,
    val type: String?,
    val period: String?,
    val description: String?,
    val kind: ReadingKind
)

/** One reading of a register. [value] is null if the API sent no parsable number. */
data class Reading(
    /** Epoch milliseconds of the reading's timestamp. */
    val timestampMillis: Long,
    val value: BigDecimal?,
    /** Status codes of the reading; empty = valid (as defined by the API). */
    val qualities: List<String>
) {
    val isValid: Boolean get() = qualities.isEmpty()
}

/** All readings of one register. */
data class ReadingSeries(val readingType: String, val readings: List<Reading>)

/**
 * Answer of GET /meter-readings: 15-minute data and daily states. Prepared so that personal consumption can later
 * be shown against the tariff blocks of the app (each [Reading] carries its own instant).
 */
data class MeterReadings(val usagePoint: String?, val createdMillis: Long?, val series: List<ReadingSeries>)

/** Why a request did not succeed. Mapped from the HTTP status codes the API documents. */
sealed interface MojElektroError {
    /** 400: invalid input; [code] = API error code (E.x.xxx) if one was sent. */
    data class InvalidRequest(val code: String?) : MojElektroError

    /** 401: token missing or not accepted. */
    data object Unauthorized : MojElektroError

    /** 403: the token does not give access to this data. */
    data object Forbidden : MojElektroError

    /** 404: no such metering or measurement point. */
    data object NotFound : MojElektroError

    /** Any other HTTP status (for example 5xx). */
    data class Server(val httpStatus: Int) : MojElektroError

    /** No connection, DNS failure, TLS failure. */
    data object Network : MojElektroError

    /** The server did not answer in time. */
    data object Timeout : MojElektroError

    /** HTTP 200 but the body is not what the API describes (or too large). */
    data object InvalidResponse : MojElektroError

    /** The app has no stored connection. */
    data object NotConnected : MojElektroError

    /** The stored token could not be decrypted (for example after the device's secure storage was reset). */
    data object StorageUnavailable : MojElektroError
}

sealed interface MojElektroResult<out T> {
    data class Success<T>(val data: T) : MojElektroResult<T>
    data class Failure(val error: MojElektroError) : MojElektroResult<Nothing>
}

/**
 * The registers the app uses, identified by the label ("oznaka") the official catalogue GET /reading-type gives them.
 * The register CODES are never written into the app: they are read from the catalogue at runtime.
 */
enum class ReadingRegister(val label: String) {
    /** 15-minute active energy taken from the grid, kWh. */
    IMPORT_15MIN("A+"),

    /** 15-minute active energy delivered to the grid (production), kWh. */
    EXPORT_15MIN("A-"),

    /** 24-hour meter state of taken energy, single tariff (ET), kWh. */
    IMPORT_DAILY_STATE("A+_T0"),

    /** 24-hour meter state of delivered energy, single tariff (ET), kWh. */
    EXPORT_DAILY_STATE("A-_T0");

    companion object {
        /** Codes of the registers present in [catalogue] (a register missing from the catalogue is left out). */
        fun resolve(catalogue: List<ReadingTypeInfo>): Map<ReadingRegister, String> =
            entries.mapNotNull { reg -> catalogue.firstOrNull { it.label == reg.label }?.let { reg to it.code } }
                .toMap()
    }
}

package si.semafor.app.mojelektro

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Parses Moj Elektro API answers (org.json, no Android classes, unit tested). Field names follow the official
 * OpenAPI description. Optional fields that are missing or null become null; a missing required identifier makes the
 * answer invalid (IllegalArgumentException / JSONException, which the repository maps to InvalidResponse).
 */
internal object MojElektroParser {

    fun meteringPoint(text: String): MeteringPoint {
        val o = JSONObject(text)
        val id = o.optJSONObject("identifikator")
        val eimm = requireNotNull(id?.str("enotniIdentifikatorMerilnegaMesta")) { "Missing EIMM" }
        val points = o.optJSONArray("merilneTocke").objects().mapNotNull { mt ->
            mt.str("gsrn")?.let { MeasurementPointRef(it, mt.str("vrsta")) }
        }
        val contract = o.optJSONObject("pogodbeniPodatki")
        return MeteringPoint(
            eimm = eimm,
            gsrn = id.str("gsrn"),
            measurementPoints = points,
            has15MinuteData = contract?.bool("obstoj15MinutneMeritve"),
            remoteReading = contract?.bool("daljinskoCitanje")
        )
    }

    fun measurementPoint(text: String): MeasurementPoint {
        val o = JSONObject(text)
        val gsrn = requireNotNull(o.str("gsrn")) { "Missing GSRN" }
        val powers = o.optJSONArray("dogovorjeneMoci").objects().map { p ->
            AgreedPower(
                validFrom = p.str("datumOd"),
                validTo = p.str("datumDo"),
                blockKw = (1..5).map { p.decimal("casovniBlok$it") },
                valid = p.bool("veljavnost")
            )
        }
        return MeasurementPoint(
            gsrn = gsrn,
            name = o.str("naziv"),
            kind = o.str("vrsta"),
            userGroup = o.str("uporabniskaSkupina"),
            agreedPowers = powers
        )
    }

    fun readingTypes(text: String): List<ReadingTypeInfo> =
        JSONArray(text).objects().mapNotNull { t ->
            val code = t.str("readingType") ?: return@mapNotNull null
            ReadingTypeInfo(
                code = code,
                name = t.str("naziv"),
                label = t.str("oznaka"),
                type = t.str("tip"),
                period = t.str("perioda"),
                description = t.str("opis"),
                kind = when (t.str("vrsta")) {
                    "KOLICINA" -> ReadingKind.KOLICINA
                    "STANJE" -> ReadingKind.STANJE
                    else -> ReadingKind.UNKNOWN
                }
            )
        }

    /**
     * Answer of GET /reading-qualities: status code ("readingQualityType") -> description. Entries without a code are
     * skipped; an entry without a description maps to the code itself.
     */
    fun readingQualities(text: String): Map<String, String> =
        JSONArray(text).objects().mapNotNull { q ->
            val code = q.str("readingQualityType") ?: return@mapNotNull null
            code to (q.str("description") ?: code)
        }.toMap()

    fun meterReadings(text: String): MeterReadings {
        val o = JSONObject(text)
        val series = o.optJSONArray("intervalBlocks").objects().mapNotNull { block ->
            val type = block.str("readingType") ?: return@mapNotNull null
            val readings = block.optJSONArray("intervalReadings").objects().mapNotNull { r ->
                val millis = r.str("timestamp")?.let(::instantMillis) ?: return@mapNotNull null
                Reading(
                    timestampMillis = millis,
                    value = r.decimal("value"),
                    qualities = r.optJSONArray("readingQualities").objects()
                        .mapNotNull { it.str("readingQualityType") }
                )
            }
            ReadingSeries(type, readings)
        }
        return MeterReadings(
            usagePoint = o.str("usagePoint"),
            createdMillis = o.str("messageCreated")?.let(::instantMillis),
            series = series
        )
    }

    /**
     * API error code (pattern E.d.ddd) from an error body. The API describes both a single error object
     * ({"koda", "opis"}) and a list ({"errors": [...]}); both are accepted. Anything else gives null.
     */
    fun errorCode(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return try {
            val o = JSONObject(text)
            val code = o.str("koda") ?: o.optJSONArray("errors").objects().firstNotNullOfOrNull { it.str("koda") }
            code?.takeIf { ERROR_CODE.matches(it) }
        } catch (e: Exception) {
            null
        }
    }

    private val ERROR_CODE = Regex("^E\\.\\d\\.\\d{3}$")

    /** ISO-8601 date-time with offset (e.g. 2023-12-19T00:00:15+02:00) to epoch milliseconds, or null. */
    internal fun instantMillis(text: String): Long? = try {
        OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    /** String value, or null if the key is missing, JSON null or blank. */
    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.bool(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Boolean -> v
            is String -> v.toBooleanStrictOrNull()
            else -> null
        }
    }

    /** Decimal values are strings in the API ("Decimal: type string"); plain JSON numbers are accepted as well. */
    private fun JSONObject.decimal(key: String): BigDecimal? =
        str(key)?.replace(',', '.')?.let { runCatching { BigDecimal(it) }.getOrNull() }
}

package si.semafor.app.mojelektro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Encrypted on-device cache of the readings of the connected metering point, so the screen opens without new requests
 * and data is not downloaded again unnecessarily.
 *
 * - Content: only meter values (timestamps, kWh) and the agreed power; no names, addresses or other personal fields.
 * - Encrypted with [MojElektroCrypto] (Keystore AES-256-GCM) in noBackupFilesDir (never in backups).
 * - Deleted when the connection is removed; unreadable data is deleted as well.
 *
 * Blocking (disk + Keystore): call from Dispatchers.IO.
 */
internal class UsageCache(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    data class Snapshot(
        val eimm: String,
        val fetchedAtMillis: Long,
        val start: LocalDate,
        val end: LocalDate,
        val fifteen: MeterReadings,
        val daily: MeterReadings,
        val agreed: AgreedPower?,
        val agreedFetchedAtMillis: Long
    )

    fun load(): Snapshot? {
        if (!file.exists()) return null
        return try {
            val plain = MojElektroCrypto.decrypt(file.readText(Charsets.UTF_8))
            val o = JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            Snapshot(
                eimm = o.getString("eimm"),
                fetchedAtMillis = o.getLong("fetchedAt"),
                start = LocalDate.parse(o.getString("start")),
                end = LocalDate.parse(o.getString("end")),
                fifteen = readingsFrom(o.getJSONArray("fifteen")),
                daily = readingsFrom(o.getJSONArray("daily")),
                agreed = o.optJSONObject("agreed")?.let(::agreedFrom),
                agreedFetchedAtMillis = o.optLong("agreedFetchedAt", 0L)
            )
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun save(snapshot: Snapshot) {
        try {
            val o = JSONObject()
                .put("eimm", snapshot.eimm)
                .put("fetchedAt", snapshot.fetchedAtMillis)
                .put("start", snapshot.start.toString())
                .put("end", snapshot.end.toString())
                .put("fifteen", readingsTo(snapshot.fifteen))
                .put("daily", readingsTo(snapshot.daily))
                .put("agreedFetchedAt", snapshot.agreedFetchedAtMillis)
            snapshot.agreed?.let { o.put("agreed", agreedTo(it)) }
            val plain = o.toString().toByteArray(Charsets.UTF_8)
            val encrypted = MojElektroCrypto.encrypt(plain)
            plain.fill(0)
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(encrypted, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // A cache that cannot be written only means the data is requested again next time.
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, "$FILE_NAME.tmp").delete()
    }

    private fun readingsTo(r: MeterReadings): JSONArray = JSONArray().apply {
        for (s in r.series) {
            val rows = JSONArray()
            for (x in s.readings) {
                rows.put(
                    JSONArray().put(x.timestampMillis).put(x.value?.toPlainString() ?: JSONObject.NULL)
                        .put(x.qualities.joinToString("|"))
                )
            }
            put(JSONObject().put("type", s.readingType).put("rows", rows))
        }
    }

    private fun readingsFrom(a: JSONArray): MeterReadings {
        val series = (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            val rows = s.getJSONArray("rows")
            ReadingSeries(s.getString("type"), (0 until rows.length()).map { j ->
                val row = rows.getJSONArray(j)
                Reading(
                    timestampMillis = row.getLong(0),
                    value = if (row.isNull(1)) null else BigDecimal(row.getString(1)),
                    qualities = row.optString(2, "").split("|").filter { it.isNotEmpty() }
                )
            })
        }
        return MeterReadings(null, null, series)
    }

    private fun agreedTo(p: AgreedPower): JSONObject = JSONObject()
        .put("from", p.validFrom ?: JSONObject.NULL)
        .put("to", p.validTo ?: JSONObject.NULL)
        .put("valid", p.valid ?: JSONObject.NULL)
        .put("kw", JSONArray().apply { p.blockKw.forEach { put(it?.toPlainString() ?: JSONObject.NULL) } })

    private fun agreedFrom(o: JSONObject): AgreedPower {
        val kw = o.getJSONArray("kw")
        return AgreedPower(
            validFrom = if (o.isNull("from")) null else o.getString("from"),
            validTo = if (o.isNull("to")) null else o.getString("to"),
            blockKw = (0 until 5).map { if (kw.isNull(it)) null else BigDecimal(kw.getString(it)) },
            valid = if (o.isNull("valid")) null else o.getBoolean("valid")
        )
    }

    private companion object {
        const val FILE_NAME = "mojelektro_usage.bin"
    }
}

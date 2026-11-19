package si.semafor.app.pricing

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Reads and strictly checks "ceniki.json". A file with any wrong or missing part is rejected as a whole ([parse]
 * returns null), so a broken download can never replace good data with empty or wrong prices. The same rules are in
 * the server script (server/private/update_prices.php).
 */
object PriceDataParser {
    const val SCHEMA_VERSION = 1

    /** Upper bounds that catch a misplaced decimal point (for example 148 instead of 0.148). */
    private const val MAX_PER_KWH = 1.0
    private const val MAX_PER_KW_MONTH = 50.0
    private const val MAX_PER_MONTH = 1000.0
    private const val MAX_ENERGY_PRICE = 2.0
    private const val MAX_TEXT = 200
    private val ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    fun parse(text: String): PriceData? = try {
        read(JSONObject(text))
    } catch (e: Exception) {
        // org.json and the checks below throw on anything unexpected: the file is rejected.
        null
    }

    private fun read(o: JSONObject): PriceData {
        val version = o.getInt("schemaVersion")
        require(version == SCHEMA_VERSION) { "Unsupported schema $version" }
        val updatedAt = OffsetDateTime.parse(o.getString("updatedAt")).toInstant().toEpochMilli()
        val validFrom = date(o, "validFrom")!!
        val validTo = date(o, "validTo")
        require(validTo == null || !validTo.isBefore(validFrom))

        val taxes = o.getJSONObject("taxes")
        val vat = taxes.getDouble("vatRate")
        require(vat > 0.0 && vat <= 0.5) { "VAT rate" }
        val charges = ChargeId.entries.associateWith { id -> charges(id, taxes.getJSONArray(id.jsonKey)) }

        val times = o.getJSONObject("tariffTimes").let {
            val start = it.getInt("vtStartHour")
            val end = it.getInt("vtEndHour")
            require(start in 0..23 && end in 1..24 && start != end % 24) { "Tariff hours" }
            TariffTimes(start, end, it.getBoolean("mtOnWeekendsAndHolidays"), url(it))
        }

        val network = o.getJSONArray("network").objects().map { n ->
            val from = date(n, "validFrom")!!
            val to = date(n, "validTo")!!
            require(!to.isBefore(from))
            val rows = n.getJSONArray("blocks").objects().map {
                NetworkRow(it.getInt("block"), amount(it, "powerEurPerKwMonth", MAX_PER_KW_MONTH), amount(it, "energyEurPerKwh", MAX_PER_KWH))
            }
            require(rows.map { it.block }.sorted() == listOf(1, 2, 3, 4, 5)) { "Network blocks" }
            NetworkTariff(from, to, rows.sortedBy { it.block }, url(n), optText(n, "note"), optText(n, "noteEn"))
        }

        val supplierIds = HashSet<String>()
        val packageIds = HashSet<String>()
        val suppliers = o.getJSONArray("suppliers").objects().map { s ->
            val id = s.getString("id")
            require(ID.matches(id) && supplierIds.add(id)) { "Supplier id" }
            val packages = s.getJSONArray("packages").objects().map { p ->
                val pid = p.getString("id")
                require(ID.matches(pid) && packageIds.add(pid)) { "Package id" }
                val prices = p.getJSONObject("pricesWithoutVat").let {
                    EnergyPrices(optPrice(it, "ET"), optPrice(it, "VT"), optPrice(it, "MT"))
                }
                require(prices.hasSingle || prices.hasDual) { "Package without prices" }
                require((prices.vt == null) == (prices.mt == null)) { "VT and MT come together" }
                val from = date(p, "validFrom")!!
                val to = date(p, "validTo")
                require(to == null || !to.isBefore(from))
                val fee = if (p.isNull("monthlyFeeWithoutVat")) null else amount(p, "monthlyFeeWithoutVat", MAX_PER_MONTH)
                val extras = if (!p.has("monthlyExtrasWithoutVat") || p.isNull("monthlyExtrasWithoutVat")) null else {
                    val v = p.getDouble("monthlyExtrasWithoutVat")
                    require(v.isFinite() && v >= -MAX_PER_MONTH && v <= MAX_PER_MONTH) { "monthlyExtrasWithoutVat out of range" }
                    v
                }
                SupplierPackage(pid, text(p, "name"), prices, fee, from, to, url(p), extras)
            }
            require(packages.isNotEmpty()) { "Supplier without packages" }
            Supplier(id, text(s, "name"), packages)
        }

        return PriceData(version, updatedAt, validFrom, validTo, vat, times, charges, network, suppliers)
    }

    private fun charges(id: ChargeId, arr: JSONArray): List<Charge> {
        val list = arr.objects().map { c ->
            val unit = ChargeUnit.of(c.getString("unit")) ?: error("Unit")
            val max = when (unit) {
                ChargeUnit.PER_KWH -> MAX_PER_KWH
                ChargeUnit.PER_KW_MONTH -> MAX_PER_KW_MONTH
                ChargeUnit.PER_MONTH -> MAX_PER_MONTH
            }
            val from = date(c, "validFrom")!!
            val to = date(c, "validTo")
            require(to == null || !to.isBefore(from))
            val high = if (c.has("powerBlockHighSeason")) c.getInt("powerBlockHighSeason") else null
            val low = if (c.has("powerBlockLowSeason")) c.getInt("powerBlockLowSeason") else null
            if (unit == ChargeUnit.PER_KW_MONTH) require(high in 1..5 && low in 1..5) { "Power blocks" }
            Charge(id, amount(c, "value", max), unit, from, to, url(c), optText(c, "note"), high, low)
        }
        require(list.isNotEmpty()) { "Charge ${id.jsonKey} missing" }
        return list
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun date(o: JSONObject, key: String): LocalDate? =
        if (!o.has(key) || o.isNull(key)) null else LocalDate.parse(o.getString(key))

    private fun amount(o: JSONObject, key: String, max: Double): Double {
        val v = o.getDouble(key)
        require(v.isFinite() && v >= 0.0 && v <= max) { "$key out of range" }
        return v
    }

    private fun optPrice(o: JSONObject, key: String): Double? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = o.getDouble(key)
        require(v.isFinite() && v > 0.0 && v <= MAX_ENERGY_PRICE) { "$key out of range" }
        return v
    }

    private fun text(o: JSONObject, key: String): String {
        val t = o.getString(key).trim()
        require(t.isNotEmpty() && t.length <= MAX_TEXT) { key }
        return t
    }

    private fun optText(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.getString(key).trim().takeIf { it.isNotEmpty() }?.take(1000)

    private fun url(o: JSONObject): String {
        val u = o.getString("sourceUrl").trim()
        require(u.startsWith("https://") && u.length <= 500) { "Source URL" }
        return u
    }
}

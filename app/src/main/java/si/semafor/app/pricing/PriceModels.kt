package si.semafor.app.pricing

import java.time.LocalDate

/**
 * The price data of the app (file "ceniki.json"): energy prices of suppliers, the network charge and the state
 * charges. Every value carries its own validity and source; nothing here is computed or assumed by the app.
 *
 * All amounts are WITHOUT VAT, in EUR. [vatRate] is added on top by [EnergyPriceCalculator].
 */
data class PriceData(
    val schemaVersion: Int,
    /** When the file was published (epoch milliseconds). A newer file replaces an older one. */
    val updatedAtMillis: Long,
    /** The period in which the whole file applies (informative; every item also has its own validity). */
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val vatRate: Double,
    val tariffTimes: TariffTimes,
    val charges: Map<ChargeId, List<Charge>>,
    val network: List<NetworkTariff>,
    val suppliers: List<Supplier>
) {
    /** The period of [id] that applies on [date], or null. */
    fun charge(id: ChargeId, date: LocalDate): Charge? =
        charges[id].orEmpty().filter { it.appliesOn(date) }.maxByOrNull { it.validFrom }

    /** The network tariff that applies on [date] (the latest validFrom if several do), or null. */
    fun networkOn(date: LocalDate): NetworkTariff? =
        network.filter { !date.isBefore(it.validFrom) && !date.isAfter(it.validTo) }.maxByOrNull { it.validFrom }

    fun findPackage(id: String?): SupplierPackage? =
        if (id == null) null else suppliers.asSequence().flatMap { it.packages.asSequence() }.firstOrNull { it.id == id }

    fun supplierOf(pkg: SupplierPackage): Supplier? = suppliers.firstOrNull { s -> s.packages.any { it.id == pkg.id } }

    /**
     * The earliest end of validity, on or after [today], of everything the app uses today: charges, network tariff,
     * supplier packages and the file itself. Null if nothing in use has an end date.
     */
    fun earliestExpiry(today: LocalDate): LocalDate? {
        val ends = ArrayList<LocalDate>()
        validTo?.let { ends += it }
        for (id in ChargeId.entries) charge(id, today)?.validTo?.let { ends += it }
        networkOn(today)?.let { ends += it.validTo }
        for (s in suppliers) for (p in s.packages) if (p.appliesOn(today)) p.validTo?.let { ends += it }
        return ends.filter { !it.isBefore(today) }.minOrNull()
    }
}

/** Hours of the higher (VT) daily tariff on working days; everything else is MT. Read from the data file. */
data class TariffTimes(
    val vtStartHour: Int,
    val vtEndHour: Int,
    /** True: Saturdays, Sundays and work-free days are MT all day. */
    val mtOnWeekendsAndHolidays: Boolean,
    val sourceUrl: String
)

/** State charges and other items that are the same for every supplier. */
enum class ChargeId(val jsonKey: String) {
    /** Prispevek za energetsko učinkovitost (URE). */
    URE("ureContribution"),

    /** Prispevek za delovanje operaterja trga (Borzen). */
    MARKET_OPERATOR("borzenContribution"),

    /** Trošarina za električno energijo (I. stopnja). */
    EXCISE("exciseDuty"),

    /** Prispevek za zagotavljanje podpor proizvodnji iz OVE in SPTE. */
    RES_SUPPORT("oveSpteContribution")
}

enum class ChargeUnit(val jsonValue: String) {
    PER_KWH("EUR/kWh"),

    /** Per kW of the agreed power per month (the block is given by the charge). */
    PER_KW_MONTH("EUR/kW/month"),
    PER_MONTH("EUR/month");

    companion object {
        fun of(text: String): ChargeUnit? = entries.firstOrNull { it.jsonValue == text }
    }
}

/**
 * One period of a charge. For [ChargeUnit.PER_KW_MONTH] the agreed power of [powerBlockHighSeason] (November to
 * February) or [powerBlockLowSeason] (March to October) is the base.
 */
data class Charge(
    val id: ChargeId,
    val value: Double,
    val unit: ChargeUnit,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val sourceUrl: String,
    val note: String? = null,
    val powerBlockHighSeason: Int? = null,
    val powerBlockLowSeason: Int? = null
) {
    fun appliesOn(date: LocalDate): Boolean = !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo))
}

data class NetworkRow(val block: Int, val powerEurPerKwMonth: Double, val energyEurPerKwh: Double)

data class NetworkTariff(
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val rows: List<NetworkRow>,
    val sourceUrl: String,
    val note: String?,
    val noteEn: String?
) {
    fun row(block: Int): NetworkRow? = rows.firstOrNull { it.block == block }
}

/** Energy prices of one package, EUR/kWh without VAT. A package has ET, or VT and MT, or all three. */
data class EnergyPrices(val et: Double?, val vt: Double?, val mt: Double?) {
    fun forTariff(tariff: EnergyTariff): Double? = when (tariff) {
        EnergyTariff.ET -> et
        EnergyTariff.VT -> vt
        EnergyTariff.MT -> mt
    }

    val hasSingle: Boolean get() = et != null
    val hasDual: Boolean get() = vt != null && mt != null
}

data class SupplierPackage(
    val id: String,
    val name: String,
    val prices: EnergyPrices,
    /** Fixed monthly fee of the supplier without VAT (null = none stated). */
    val monthlyFee: Double?,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val sourceUrl: String,
    /**
     * Monthly services and discounts of the package without VAT ("ugodnosti in storitve", for example a green energy
     * service +0.82 and an eco discount -1.00 give -0.18); null = none stated. May be negative.
     */
    val monthlyExtras: Double? = null
) {
    fun appliesOn(date: LocalDate): Boolean = !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo))
}

data class Supplier(val id: String, val name: String, val packages: List<SupplierPackage>)

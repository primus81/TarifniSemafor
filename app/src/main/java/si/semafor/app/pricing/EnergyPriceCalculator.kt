package si.semafor.app.pricing

import si.semafor.app.PriceList
import si.semafor.app.Season
import si.semafor.app.SloTime
import java.time.Instant
import java.time.LocalDate

/** Where the energy price (the supplier's part) comes from. */
sealed interface EnergySource {
    /** A package from the price data, by its id. */
    data class Package(val id: String) : EnergySource

    /** Prices of the user's own contract, as typed. [includesVat] = the typed prices already contain VAT. */
    data class Custom(val prices: EnergyPrices, val includesVat: Boolean, val monthlyFee: Double?) : EnergySource
}

/** The user's choice: how the meter measures and where the energy price comes from. */
data class PricingSetup(val meter: MeterType, val source: EnergySource?) {
    val configured: Boolean get() = source != null
}

/** Parts of the price of one kWh, EUR without VAT, plus [vat]. Only items billed per kWh are here. */
data class PriceComponents(
    val energy: Double,
    val networkEnergy: Double,
    val ure: Double,
    val marketOperator: Double,
    val excise: Double,
    /** The OVE/SPTE contribution when it is billed per kWh (otherwise 0: it is billed on the agreed power). */
    val resSupport: Double,
    val vatRate: Double
) {
    val net: Double get() = energy + networkEnergy + ure + marketOperator + excise + resSupport
    val vat: Double get() = net * vatRate
    val total: Double get() = net + vat
}

/** Final price of one kWh at one moment, with what it is made of. */
data class KwhPrice(
    val startMillis: Long,
    val endMillis: Long,
    val block: Int,
    val tariff: EnergyTariff,
    val components: PriceComponents
) {
    val total: Double get() = components.total
}

/** Kinds of items of a monthly bill, in the order they are shown. */
enum class BillItemKind { ENERGY, SUPPLIER_FEE, NETWORK_POWER, NETWORK_ENERGY, RES_SUPPORT, URE, MARKET_OPERATOR, EXCISE }

/** One item of a bill; [fixed] = paid per month regardless of the kWh used (power, fees). */
data class BillItem(val kind: BillItemKind, val net: Double, val fixed: Boolean = false)

data class MonthlyBill(val items: List<BillItem>, val vatRate: Double, val date: LocalDate) {
    val net: Double get() = items.sumOf { it.net }
    val vat: Double get() = net * vatRate
    val total: Double get() = net + vat
}

/**
 * The final price of electricity:
 *
 * (energy price of the tariff + network charge for energy of the block + URE + market operator + excise
 *  [+ OVE/SPTE when billed per kWh]) x (1 + VAT rate)
 *
 * Items billed on power (network charge for power, OVE/SPTE per kW) or per month (supplier fee) do not depend on the
 * kWh used and are part of [monthlyBill] only. Pure Kotlin (no Android), unit tested.
 */
object EnergyPriceCalculator {
    /** Energy price without VAT for [tariff] on [date], or null (not configured, package not valid, price missing). */
    fun energyNet(data: PriceData, setup: PricingSetup, date: LocalDate, tariff: EnergyTariff): Double? =
        when (val source = setup.source) {
            null -> null
            is EnergySource.Package -> data.findPackage(source.id)?.takeIf { it.appliesOn(date) }?.prices?.forTariff(tariff)
            is EnergySource.Custom -> source.prices.forTariff(tariff)?.let { if (source.includesVat) it / (1 + data.vatRate) else it }
        }

    /** Supplier's fixed monthly fee without VAT (0 if none). */
    fun supplierFeeNet(data: PriceData, setup: PricingSetup, date: LocalDate): Double = when (val source = setup.source) {
        null -> 0.0
        is EnergySource.Package -> data.findPackage(source.id)?.takeIf { it.appliesOn(date) }?.monthlyFee ?: 0.0
        is EnergySource.Custom -> source.monthlyFee?.let { if (source.includesVat) it / (1 + data.vatRate) else it } ?: 0.0
    }

    private fun perKwhCharge(data: PriceData, id: ChargeId, date: LocalDate): Double =
        data.charge(id, date)?.takeIf { it.unit == ChargeUnit.PER_KWH }?.value ?: 0.0

    /**
     * Price of one kWh in [block] with [tariff] on [date]; null if the energy price or the network charge for the
     * block is not known (a price without them would be misleading).
     */
    fun components(
        data: PriceData,
        setup: PricingSetup,
        network: PriceList?,
        date: LocalDate,
        block: Int,
        tariff: EnergyTariff
    ): PriceComponents? {
        val energy = energyNet(data, setup, date, tariff) ?: return null
        val net = network?.rows?.firstOrNull { it.block == block }?.energyEurPerKwh ?: return null
        return PriceComponents(
            energy = energy,
            networkEnergy = net,
            ure = perKwhCharge(data, ChargeId.URE, date),
            marketOperator = perKwhCharge(data, ChargeId.MARKET_OPERATOR, date),
            excise = perKwhCharge(data, ChargeId.EXCISE, date),
            resSupport = perKwhCharge(data, ChargeId.RES_SUPPORT, date),
            vatRate = data.vatRate
        )
    }

    /**
     * Prices hour by hour from the hour that contains [fromMillis] on, for at most [hours] hours, while the block
     * schedule of the day is known. [scheduleOf] returns the 24 block ids of a date (index = local hour) or null;
     * [networkOf] the network tariff of a date. Hours are real hours (23 / 25 on the clock-change days).
     */
    fun hourly(
        data: PriceData,
        setup: PricingSetup,
        fromMillis: Long,
        hours: Int,
        scheduleOf: (LocalDate) -> IntArray?,
        networkOf: (LocalDate) -> PriceList?
    ): List<KwhPrice> {
        val result = ArrayList<KwhPrice>(hours)
        var start = Instant.ofEpochMilli(fromMillis).atZone(SloTime.ZONE).withMinute(0).withSecond(0).withNano(0)
        repeat(hours) {
            val end = start.plusHours(1)
            val date = start.toLocalDate()
            val block = scheduleOf(date)?.getOrNull(start.hour) ?: return result
            if (block !in 1..5) return result
            val tariff = EnergyTariffs.tariffAt(start.toInstant().toEpochMilli(), setup.meter, data.tariffTimes)
            val parts = components(data, setup, networkOf(date), date, block, tariff) ?: return result
            result += KwhPrice(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), block, tariff, parts)
            start = end
        }
        return result
    }

    /**
     * The cheapest hour after the current one ([prices] starts with the current hour); the earliest of equally cheap
     * hours. Null if no later hour is cheaper than the current one by at least [minSaving] EUR/kWh.
     */
    fun nextCheaper(prices: List<KwhPrice>, minSaving: Double = 0.0005): KwhPrice? {
        val now = prices.firstOrNull() ?: return null
        val best = prices.drop(1).minByOrNull { it.total } ?: return null
        val earliestBest = prices.drop(1).first { it.total <= best.total + 1e-9 }
        return if (now.total - earliestBest.total >= minSaving) earliestBest else null
    }

    /**
     * Cost (EUR with VAT) of [kwh] used evenly at [kw] from [startMillis]; null if the run reaches beyond [prices]
     * (no price known) or the input is not positive.
     */
    fun runCost(prices: List<KwhPrice>, startMillis: Long, kwh: Double, kw: Double): Double? {
        if (kwh <= 0 || kw <= 0 || prices.isEmpty()) return null
        val durationMs = kwh / kw * 3_600_000.0
        val endMillis = startMillis + durationMs
        if (startMillis < prices.first().startMillis || endMillis > prices.last().endMillis + 0.5) return null
        var cost = 0.0
        for (p in prices) {
            val from = maxOf(startMillis.toDouble(), p.startMillis.toDouble())
            val to = minOf(endMillis, p.endMillis.toDouble())
            if (to > from) cost += kw * (to - from) / 3_600_000.0 * p.total
        }
        return cost
    }

    /** Result of shifting a run of an appliance to the cheapest start. */
    data class ShiftResult(val costNow: Double, val bestStartMillis: Long, val costBest: Double) {
        val saving: Double get() = (costNow - costBest).coerceAtLeast(0.0)
    }

    /**
     * Compares starting now ([nowMillis]) with every later full-hour start inside [prices]; null if even the run
     * starting now does not fit into the known prices.
     */
    fun bestShift(prices: List<KwhPrice>, nowMillis: Long, kwh: Double, kw: Double): ShiftResult? {
        val costNow = runCost(prices, nowMillis, kwh, kw) ?: return null
        var bestStart = nowMillis
        var bestCost = costNow
        for (p in prices) {
            if (p.startMillis <= nowMillis) continue
            val c = runCost(prices, p.startMillis, kwh, kw) ?: break
            if (c < bestCost - 1e-9) {
                bestCost = c
                bestStart = p.startMillis
            }
        }
        return ShiftResult(costNow, bestStart, bestCost)
    }

    /**
     * Estimate of a monthly bill. [powerKw] = agreed power per block 1..5, [energyKwh] = consumption per block 1..5
     * (kWh per month), [mtShare] = part of the consumption in MT (0..1) with two-tariff metering. Null if the energy
     * price or the network tariff is not known. The network items use [network] exactly like the network calculator.
     */
    fun monthlyBill(
        data: PriceData,
        setup: PricingSetup,
        network: PriceList?,
        date: LocalDate,
        powerKw: List<Double>,
        energyKwh: List<Double>,
        mtShare: Double
    ): MonthlyBill? {
        if (network == null || powerKw.size != 5 || energyKwh.size != 5) return null
        val kwh = energyKwh.sum()
        val energy = when (setup.meter) {
            MeterType.SINGLE -> (energyNet(data, setup, date, EnergyTariff.ET) ?: return null) * kwh
            MeterType.DUAL -> {
                val vt = energyNet(data, setup, date, EnergyTariff.VT) ?: return null
                val mt = energyNet(data, setup, date, EnergyTariff.MT) ?: return null
                val share = mtShare.coerceIn(0.0, 1.0)
                kwh * (share * mt + (1 - share) * vt)
            }
        }
        var networkPower = 0.0
        var networkEnergy = 0.0
        for (b in 1..5) {
            val row = network.rows.firstOrNull { it.block == b } ?: return null
            networkPower += powerKw[b - 1] * row.powerEurPerKw
            networkEnergy += energyKwh[b - 1] * row.energyEurPerKwh
        }
        val items = ArrayList<BillItem>()
        items += BillItem(BillItemKind.ENERGY, energy)
        val fee = supplierFeeNet(data, setup, date)
        if (fee > 0) items += BillItem(BillItemKind.SUPPLIER_FEE, fee, fixed = true)
        items += BillItem(BillItemKind.NETWORK_POWER, networkPower, fixed = true)
        items += BillItem(BillItemKind.NETWORK_ENERGY, networkEnergy)
        data.charge(ChargeId.RES_SUPPORT, date)?.let { c ->
            val amount = when (c.unit) {
                ChargeUnit.PER_KWH -> c.value * kwh
                ChargeUnit.PER_MONTH -> c.value
                ChargeUnit.PER_KW_MONTH -> {
                    val block = if (Season.of(date) == Season.HIGH) c.powerBlockHighSeason else c.powerBlockLowSeason
                    if (block == null || block !in 1..5) null else c.value * powerKw[block - 1]
                }
            }
            if (amount != null) items += BillItem(BillItemKind.RES_SUPPORT, amount, fixed = c.unit != ChargeUnit.PER_KWH)
        }
        items += BillItem(BillItemKind.URE, perKwhCharge(data, ChargeId.URE, date) * kwh)
        items += BillItem(BillItemKind.MARKET_OPERATOR, perKwhCharge(data, ChargeId.MARKET_OPERATOR, date) * kwh)
        items += BillItem(BillItemKind.EXCISE, perKwhCharge(data, ChargeId.EXCISE, date) * kwh)
        return MonthlyBill(items, data.vatRate, date)
    }
}

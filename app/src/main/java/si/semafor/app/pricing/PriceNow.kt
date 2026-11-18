package si.semafor.app.pricing

import android.content.Context
import si.semafor.app.PriceLists
import si.semafor.app.SloTime
import java.util.Locale

/** The final price at one moment for the home screen, the widget and the notifications. */
object PriceNow {
    /** Pure: price of one kWh at [millis] in [block]; null if not configured or a part of the price is unknown. */
    fun priceAt(data: PriceData?, setup: PricingSetup, millis: Long, block: Int): KwhPrice? {
        if (data == null || !setup.configured || block !in 1..5) return null
        val date = SloTime.dateOf(millis)
        val tariff = EnergyTariffs.tariffAt(millis, setup.meter, data.tariffTimes)
        val parts = EnergyPriceCalculator.components(data, setup, PriceLists.forDate(date), date, block, tariff) ?: return null
        val hourStart = SloTime.previousStartAtOrBefore(millis, SloTime.hourOfDay(millis))
        return KwhPrice(hourStart, hourStart + 3_600_000L, block, tariff, parts)
    }

    /** Same, with the stored data and the user's choice (reads two small preference files). */
    fun priceAt(ctx: Context, millis: Long, block: Int): KwhPrice? =
        priceAt(PriceRepository.get(ctx), PricingSettings.setup(ctx), millis, block)

    /** "0,148 €/kWh" (three decimals, the decimal sign of [locale]). */
    fun formatKwh(value: Double, locale: Locale): String = String.format(locale, "%.3f €/kWh", value)

    /** "0,148" without the unit. */
    fun formatNumber(value: Double, locale: Locale): String = String.format(locale, "%.3f", value)

    /** "1,23 €" (two decimals). */
    fun formatEur(value: Double, locale: Locale): String = String.format(locale, "%,.2f €", value)
}

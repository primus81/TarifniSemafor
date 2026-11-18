package si.semafor.app.pricing

import android.content.Context
import androidx.core.content.edit
import si.semafor.app.TariffCalculator

/**
 * The user's energy-price choice (SharedPreferences "pricing_user"). It stays on this phone only: it is not part of
 * the backup (res/xml/data_extraction_rules.xml backs up "settings" only) and it is never sent anywhere.
 */
object PricingSettings {
    private const val PREFS = "pricing_user"
    private const val KEY_METER = "meter"
    private const val KEY_SOURCE = "source"
    private const val KEY_PACKAGE = "package_id"
    private const val KEY_ET = "custom_et"
    private const val KEY_VT = "custom_vt"
    private const val KEY_MT = "custom_mt"
    private const val KEY_FEE = "custom_fee"
    private const val KEY_WITH_VAT = "custom_with_vat"
    private const val KEY_NOTIFY = "notify_changes"

    const val SOURCE_NONE = "none"
    const val SOURCE_PACKAGE = "package"
    const val SOURCE_CUSTOM = "custom"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What the user typed for a custom contract (texts, so the form shows exactly what was typed). */
    data class CustomInput(val et: String, val vt: String, val mt: String, val fee: String, val withVat: Boolean)

    data class Stored(val meter: MeterType, val sourceType: String, val packageId: String?, val custom: CustomInput)

    fun stored(ctx: Context): Stored {
        val p = prefs(ctx)
        return Stored(
            meter = if (p.getString(KEY_METER, null) == MeterType.SINGLE.name) MeterType.SINGLE else MeterType.DUAL,
            sourceType = p.getString(KEY_SOURCE, SOURCE_NONE) ?: SOURCE_NONE,
            packageId = p.getString(KEY_PACKAGE, null),
            custom = CustomInput(
                p.getString(KEY_ET, "") ?: "",
                p.getString(KEY_VT, "") ?: "",
                p.getString(KEY_MT, "") ?: "",
                p.getString(KEY_FEE, "") ?: "",
                p.getBoolean(KEY_WITH_VAT, true)
            )
        )
    }

    fun save(ctx: Context, s: Stored) {
        prefs(ctx).edit {
            putString(KEY_METER, s.meter.name)
            putString(KEY_SOURCE, s.sourceType)
            putString(KEY_PACKAGE, s.packageId)
            putString(KEY_ET, s.custom.et)
            putString(KEY_VT, s.custom.vt)
            putString(KEY_MT, s.custom.mt)
            putString(KEY_FEE, s.custom.fee)
            putBoolean(KEY_WITH_VAT, s.custom.withVat)
        }
    }

    /** The setup used for calculations; source null = not configured (or the typed prices are not usable). */
    fun setup(ctx: Context): PricingSetup = toSetup(stored(ctx))

    fun toSetup(s: Stored): PricingSetup = PricingSetup(s.meter, sourceOf(s))

    /** Pure: what a stored choice means. Custom prices must be complete for the chosen meter type. */
    fun sourceOf(s: Stored): EnergySource? = when (s.sourceType) {
        SOURCE_PACKAGE -> s.packageId?.let { EnergySource.Package(it) }
        SOURCE_CUSTOM -> {
            fun price(t: String): Double? = TariffCalculator.parseAmount(t)?.takeIf { t.isNotBlank() && it > 0.0 && it <= 2.0 }
            val prices = EnergyPrices(price(s.custom.et), price(s.custom.vt), price(s.custom.mt))
            val complete = if (s.meter == MeterType.SINGLE) prices.hasSingle else prices.hasDual
            val fee = s.custom.fee.takeIf { it.isNotBlank() }?.let { TariffCalculator.parseAmount(it) }
            if (complete) EnergySource.Custom(prices, s.custom.withVat, fee) else null
        }
        else -> null
    }

    /** Notification when the downloaded prices change. Default: on. */
    fun notifyChanges(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_NOTIFY, true)

    fun setNotifyChanges(ctx: Context, on: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_NOTIFY, on) }
    }
}

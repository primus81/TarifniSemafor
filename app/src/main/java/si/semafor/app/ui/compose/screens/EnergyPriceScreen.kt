package si.semafor.app.ui.compose.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.R
import si.semafor.app.Season
import si.semafor.app.SloTime
import si.semafor.app.TariffCalculator
import si.semafor.app.pricing.ChargeId
import si.semafor.app.pricing.ChargeUnit
import si.semafor.app.pricing.EnergyPriceCalculator
import si.semafor.app.pricing.EnergyPriceUiState
import si.semafor.app.pricing.KwhPrice
import si.semafor.app.pricing.MeterType
import si.semafor.app.pricing.PriceData
import si.semafor.app.pricing.PriceNow
import si.semafor.app.pricing.PricingSettings
import si.semafor.app.pricing.PricingSetup
import si.semafor.app.pricing.SyncMessage
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.charts.HourlyPriceChart
import si.semafor.app.ui.compose.charts.PriceColors
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.BillBreakdown
import si.semafor.app.ui.compose.components.BillInputs
import si.semafor.app.ui.compose.components.Choice
import si.semafor.app.ui.compose.components.ChoiceChips
import si.semafor.app.ui.compose.components.CurrentTotalPriceCard
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LegendRow
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.NumberRow
import si.semafor.app.ui.compose.components.PrimaryButton
import si.semafor.app.ui.compose.components.PriceBreakdown
import si.semafor.app.ui.compose.components.RadioRow
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.components.SwitchRow
import si.semafor.app.ui.compose.components.tariffShort
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.theme.BodyLineHeight

/** Everything the "Cena elektrike" screen can ask the host to do. */
data class EnergyPriceActions(
    val onBack: () -> Unit,
    val onSave: (PricingSettings.Stored) -> Unit,
    val onCheckNow: () -> Unit,
    val onNotifyChange: (Boolean) -> Unit,
    val onOpenUrl: (String) -> Unit
)

/**
 * "Cena elektrike": the final price of electricity now and in the coming hours, what it is made of, when to switch on
 * an appliance, the user's energy price and the price lists in use. [current] = the price of this hour (from the
 * current block of the home screen); [state] comes from [si.semafor.app.pricing.EnergyPriceViewModel].
 */
@Composable
fun EnergyPriceScreen(
    state: EnergyPriceUiState,
    current: KwhPrice?,
    nowMillis: Long,
    actions: EnergyPriceActions,
    /** Agreed power per block 1..5 (Moj Elektro or typed in the planner); empty = not known. */
    agreedKw: List<Double?> = emptyList(),
    /** Where [agreedKw] comes from, for the note under the fixed costs. */
    agreedSource: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    // The setup is open at first when nothing is configured yet.
    var setupOpen by rememberSaveable { mutableStateOf(!state.setup.configured) }
    val next = EnergyPriceCalculator.nextCheaper(state.hourly)

    ScreenScaffold {
        ScreenHeader(stringResource(R.string.ep_title), actions.onBack, stringResource(R.string.ep_subtitle))

        CurrentTotalPriceCard(
            price = current,
            next = next,
            configured = state.setup.configured,
            hourlyKnown = state.hourly.size > 1,
            nowMillis = nowMillis,
            onSetup = { setupOpen = true }
        )

        if (current != null) {
            SectionHeading(stringResource(R.string.ep_breakdown_title))
            InfoCard {
                Text(stringResource(R.string.ep_variable_title), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                PriceBreakdown(current.components)
                Text(stringResource(R.string.ep_breakdown_note), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        val data = state.data
        if (data != null && state.setup.configured) {
            SectionHeading(stringResource(R.string.ep_blocks_title))
            InfoCard { BlockPriceTable(data, state.setup) }
            SectionHeading(stringResource(R.string.ep_fixed_title))
            InfoCard { FixedCosts(data, state.setup, agreedKw, agreedSource) }
        }
        if (data != null) {
            SectionHeading(stringResource(R.string.ep_compare_title))
            InfoCard { OfferComparison(data, state.setup, current) }
        }

        if (state.setup.configured) {
            SectionHeading(stringResource(R.string.ep_hourly_title))
            InfoCard { HourlySection(state) }

            SectionHeading(stringResource(R.string.ep_appliance_title))
            InfoCard { ApplianceCostCalculator(state.hourly, nowMillis) }
        }

        SectionHeading(stringResource(R.string.ep_setup_title))
        InfoCard {
            if (setupOpen || !state.setup.configured) {
                SetupForm(state, actions.onSave) { setupOpen = false }
            } else {
                SetupSummary(state)
                Spacer(Modifier.size(8.dp))
                ActionButton(stringResource(R.string.ep_setup_button)) { setupOpen = true }
            }
        }

        SectionHeading(stringResource(R.string.ep_data_title))
        InfoCard { DataSection(state, actions) }

        Text(stringResource(R.string.ep_disclaimer), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
    }
}

@Composable
private fun HourlySection(state: EnergyPriceUiState) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    // The chart shows the next 24 hours (the list may reach further for the planner).
    val prices = state.hourly.take(24)
    when {
        prices.isNotEmpty() -> {
            val min = prices.minBy { it.total }
            val max = prices.maxBy { it.total }
            val spoken = stringResource(
                R.string.ep_hourly_spoken,
                PriceNow.formatKwh(prices.first().total, locale),
                PriceNow.formatKwh(min.total, locale), SloTime.formatHm(min.startMillis),
                PriceNow.formatKwh(max.total, locale), SloTime.formatHm(max.startMillis)
            )
            HourlyPriceChart(prices, spoken)
            LegendRow(
                listOf(
                    PriceColors.Energy to stringResource(R.string.ep_legend_energy),
                    PriceColors.Network to stringResource(R.string.ep_legend_network),
                    PriceColors.Charges to stringResource(R.string.ep_legend_charges),
                    PriceColors.Vat to stringResource(R.string.ep_legend_vat)
                )
            )
            Text(stringResource(R.string.ep_hourly_note), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        state.hourlyLoading -> Text(stringResource(R.string.ep_hourly_loading), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        else -> Text(stringResource(R.string.ep_hourly_failed), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
    state.data?.let { d ->
        if (state.setup.meter == MeterType.DUAL) {
            Text(
                stringResource(R.string.ep_tariff_rule, d.tariffTimes.vtStartHour, d.tariffTimes.vtEndHour),
                style = small,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** A preset appliance: energy per run and power; [nameRes] = label, [icon] = drawable. */
private data class Appliance(val key: String, val nameRes: Int, val icon: Int, val kwh: String, val kw: String)

// Values from the request of the app's author (EV 11 kW / 40 kWh, appliances per run); 1 hour for the others.
private val APPLIANCES = listOf(
    Appliance("ev", R.string.ep_app_ev, R.drawable.ic_device_ev, "40", "11"),
    Appliance("washer", R.string.ep_app_washer, R.drawable.ic_device_household, "1.5", "1.5"),
    Appliance("dryer", R.string.ep_app_dryer, R.drawable.ic_device_household, "2.5", "2.5"),
    Appliance("dishwasher", R.string.ep_app_dishwasher, R.drawable.ic_device_household, "1.2", "1.2"),
    Appliance("boiler", R.string.ep_app_boiler, R.drawable.ic_device_heating, "2", "2"),
    Appliance("custom", R.string.ep_app_custom, R.drawable.ic_bolt, "", "")
)

/** Cost of one run now and at the cheapest start in the known hours, and the saving. */
@Composable
private fun ApplianceCostCalculator(prices: List<KwhPrice>, nowMillis: Long) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    var selected by rememberSaveable { mutableStateOf("washer") }
    var kwhText by rememberSaveable { mutableStateOf("1.5") }
    var kwText by rememberSaveable { mutableStateOf("1.5") }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(bottom = 8.dp)
    ) {
        for (a in APPLIANCES) {
            val isSelected = a.key == selected
            val shape = RoundedCornerShape(20.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(if (isSelected) colors.primary else colors.surface)
                    .border(1.dp, colors.outline, shape)
                    .clickable(role = Role.Tab) {
                        selected = a.key
                        if (a.key != "custom") {
                            kwhText = a.kwh
                            kwText = a.kw
                        }
                    }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                val tint = if (isSelected) colors.onPrimary else colors.onSurface
                Icon(painterResource(a.icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(a.nameRes), style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }

    NumberRow(stringResource(R.string.ep_app_energy), stringResource(R.string.ep_app_energy), kwhText) {
        kwhText = it
        selected = "custom"
    }
    NumberRow(stringResource(R.string.ep_app_power), stringResource(R.string.ep_app_power), kwText) {
        kwText = it
        selected = "custom"
    }

    val kwh = TariffCalculator.parseAmount(kwhText) ?: 0.0
    val kw = TariffCalculator.parseAmount(kwText) ?: 0.0
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    if (kwh <= 0.0 || kw <= 0.0) {
        Text(stringResource(R.string.ep_app_invalid), style = body, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    } else {
        Text(
            stringResource(R.string.ep_app_duration, String.format(locale, "%.1f", kwh), String.format(locale, "%.1f", kwh / kw)),
            style = body,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        val shift = EnergyPriceCalculator.bestShift(prices, nowMillis, kwh, kw)
        if (shift == null) {
            Text(stringResource(R.string.ep_app_not_fit), style = body, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        } else {
            Column(Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.ep_app_cost_now, PriceNow.formatEur(shift.costNow, locale)), style = body, color = colors.onSurface)
                if (shift.saving >= 0.005) {
                    val time = SloTime.formatHm(shift.bestStartMillis)
                    val best = PriceNow.formatEur(shift.costBest, locale)
                    Text(
                        if (SloTime.dateOf(shift.bestStartMillis) != SloTime.dateOf(nowMillis)) stringResource(R.string.ep_app_cost_best_tomorrow, time, best)
                        else stringResource(R.string.ep_app_cost_best, time, best),
                        style = body,
                        color = colors.onSurface
                    )
                    // The saving pops in and changes with a fade when the inputs change.
                    AnimatedContent(
                        targetState = PriceNow.formatEur(shift.saving, locale),
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)).togetherWith(fadeOut()) },
                        label = "saving"
                    ) { saving ->
                        Text(
                            stringResource(R.string.ep_app_saving, saving),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Text(stringResource(R.string.ep_app_already_best), style = body, color = colors.onSurface)
                }
            }
        }
    }
    Text(stringResource(R.string.ep_app_note), style = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight), color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SetupSummary(state: EnergyPriceUiState) {
    val colors = MaterialTheme.colorScheme
    val stored = state.stored ?: return
    val meter = stringResource(if (stored.meter == MeterType.SINGLE) R.string.ep_meter_single else R.string.ep_meter_dual)
    val source = when (stored.sourceType) {
        PricingSettings.SOURCE_PACKAGE -> state.data?.findPackage(stored.packageId)?.let { p ->
            (state.data.supplierOf(p)?.name?.let { "$it, " } ?: "") + p.name
        } ?: stringResource(R.string.ep_source_package)
        else -> stringResource(R.string.ep_source_custom)
    }
    Text(meter, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    Text(source, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    // Validity of the chosen package, so the user sees which price list is in use.
    val lang = LocalLang.current
    state.data?.findPackage(stored.packageId)?.takeIf { stored.sourceType == PricingSettings.SOURCE_PACKAGE }?.let { p ->
        Text(
            p.validTo?.let { stringResource(R.string.ep_validity_to, SloTime.formatLocalDate(p.validFrom, lang), SloTime.formatLocalDate(it, lang)) }
                ?: stringResource(R.string.ep_validity, SloTime.formatLocalDate(p.validFrom, lang)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SetupForm(state: EnergyPriceUiState, onSave: (PricingSettings.Stored) -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val locale = currentLocale
    val stored = state.stored ?: return
    val data = state.data
    val suppliers = data?.suppliers.orEmpty()
    var meter by rememberSaveable { mutableStateOf(stored.meter) }
    var source by rememberSaveable {
        mutableStateOf(
            if (stored.sourceType == PricingSettings.SOURCE_NONE) {
                if (suppliers.isEmpty()) PricingSettings.SOURCE_CUSTOM else PricingSettings.SOURCE_PACKAGE
            } else stored.sourceType
        )
    }
    var packageId by rememberSaveable { mutableStateOf(stored.packageId) }
    var et by rememberSaveable { mutableStateOf(stored.custom.et) }
    var vt by rememberSaveable { mutableStateOf(stored.custom.vt) }
    var mt by rememberSaveable { mutableStateOf(stored.custom.mt) }
    var fee by rememberSaveable { mutableStateOf(stored.custom.fee) }
    var withVat by rememberSaveable { mutableStateOf(stored.custom.withVat) }
    var message by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(meter, source, packageId, et, vt, mt, fee, withVat) { message = null }

    Text(stringResource(R.string.ep_meter_label), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.padding(bottom = 6.dp))
    ChoiceChips(
        listOf(Choice(MeterType.DUAL, stringResource(R.string.ep_meter_dual)), Choice(MeterType.SINGLE, stringResource(R.string.ep_meter_single))),
        meter,
        { meter = it }
    )

    Text(stringResource(R.string.ep_source_label), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    ChoiceChips(
        listOf(
            Choice(PricingSettings.SOURCE_PACKAGE, stringResource(R.string.ep_source_package)),
            Choice(PricingSettings.SOURCE_CUSTOM, stringResource(R.string.ep_source_custom))
        ),
        source,
        { source = it }
    )

    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    if (source == PricingSettings.SOURCE_PACKAGE) {
        if (suppliers.isEmpty()) {
            Text(stringResource(R.string.ep_no_suppliers), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight), color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        } else {
            Column(Modifier.padding(top = 6.dp).selectableGroup()) {
                for (s in suppliers) for (p in s.packages) {
                    RadioRow(
                        label = s.name + ", " + p.name,
                        selected = packageId == p.id,
                        onClick = { packageId = p.id },
                        fontSize = 14.sp
                    )
                    val prices = listOfNotNull(
                        p.prices.et?.let { "ET " + PriceNow.formatNumber(it, locale) },
                        p.prices.vt?.let { "VT " + PriceNow.formatNumber(it, locale) },
                        p.prices.mt?.let { "MT " + PriceNow.formatNumber(it, locale) }
                    ).joinToString(" · ")
                    val valid = p.validTo?.let { stringResource(R.string.ep_package_valid_to, SloTime.formatLocalDate(p.validFrom, lang), SloTime.formatLocalDate(it, lang)) }
                        ?: stringResource(R.string.ep_package_valid, SloTime.formatLocalDate(p.validFrom, lang))
                    Text(stringResource(R.string.ep_package_prices, prices) + ". " + valid, style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp, bottom = 4.dp))
                }
            }
        }
    } else {
        Column(Modifier.padding(top = 6.dp)) {
            if (meter == MeterType.SINGLE) {
                NumberRow(stringResource(R.string.ep_custom_et), stringResource(R.string.ep_custom_et), et) { et = it }
            } else {
                NumberRow(stringResource(R.string.ep_custom_vt), stringResource(R.string.ep_custom_vt), vt) { vt = it }
                NumberRow(stringResource(R.string.ep_custom_mt), stringResource(R.string.ep_custom_mt), mt) { mt = it }
            }
            NumberRow(stringResource(R.string.ep_custom_fee), stringResource(R.string.ep_custom_fee), fee) { fee = it }
            SwitchRow(stringResource(R.string.ep_custom_vat), withVat, { withVat = it }, fontSize = 14.sp)
        }
    }

    Spacer(Modifier.size(8.dp))
    PrimaryButton(stringResource(R.string.ep_save)) {
        val candidate = PricingSettings.Stored(meter, source, packageId, PricingSettings.CustomInput(et, vt, mt, fee, withVat))
        val setup = PricingSettings.toSetup(candidate)
        val today = SloTime.today()
        message = when {
            source == PricingSettings.SOURCE_PACKAGE && suppliers.isEmpty() -> R.string.ep_no_suppliers
            source == PricingSettings.SOURCE_PACKAGE && packageId == null -> R.string.ep_pick_package
            source == PricingSettings.SOURCE_PACKAGE && data != null && !packageUsable(data, setup, today) -> R.string.ep_package_unusable
            setup.source == null -> R.string.ep_custom_incomplete
            else -> null
        }
        if (message == null) {
            onSave(candidate)
            onDone()
        }
    }
    message?.let {
        Text(
            stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** True if the chosen package has the prices the meter type needs and is valid today. */
private fun packageUsable(data: PriceData, setup: PricingSetup, today: java.time.LocalDate): Boolean {
    val tariffs = if (setup.meter == MeterType.SINGLE) listOf(si.semafor.app.pricing.EnergyTariff.ET)
    else listOf(si.semafor.app.pricing.EnergyTariff.VT, si.semafor.app.pricing.EnergyTariff.MT)
    return tariffs.all { EnergyPriceCalculator.energyNet(data, setup, today, it) != null }
}

@Composable
private fun DataSection(state: EnergyPriceUiState, actions: EnergyPriceActions) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val locale = currentLocale
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    val data = state.data
    val today = SloTime.today()

    if (data != null) {
        Text(stringResource(R.string.ep_data_updated, SloTime.formatDate(data.updatedAtMillis, lang)), style = body, color = colors.onSurface)
    }
    Text(
        if (state.lastSuccessMillis > 0) stringResource(R.string.ep_data_checked, SloTime.formatDateTime(state.lastSuccessMillis, lang))
        else stringResource(R.string.ep_data_never),
        style = small,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
    Spacer(Modifier.size(8.dp))
    ActionButton(stringResource(if (state.checking) R.string.ep_checking else R.string.ep_check_now)) { if (!state.checking) actions.onCheckNow() }
    AnimatedVisibility(state.syncMessage != null) {
        val m = state.syncMessage
        Text(
            stringResource(
                when (m) {
                    SyncMessage.UPDATED -> R.string.ep_sync_updated
                    SyncMessage.FAILED -> R.string.ep_sync_failed
                    else -> R.string.ep_sync_unchanged
                }
            ),
            style = body,
            color = if (m == SyncMessage.FAILED) colors.error else colors.onSurface,
            modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    SwitchRow(stringResource(R.string.ep_notify_switch), state.notifyChanges, actions.onNotifyChange, fontSize = 14.sp)

    if (data != null) {
        Spacer(Modifier.size(4.dp))
        val labels = mapOf(
            ChargeId.URE to R.string.ep_item_ure,
            ChargeId.MARKET_OPERATOR to R.string.ep_item_market,
            ChargeId.EXCISE to R.string.ep_item_excise,
            ChargeId.RES_SUPPORT to R.string.ep_item_res
        )
        for (id in ChargeId.entries) {
            val c = data.charge(id, today) ?: continue
            val unit = stringResource(
                when (c.unit) {
                    ChargeUnit.PER_KWH -> R.string.ep_unit_kwh
                    ChargeUnit.PER_KW_MONTH -> R.string.ep_unit_kw_month
                    ChargeUnit.PER_MONTH -> R.string.ep_unit_month
                }
            )
            Text(stringResource(labels.getValue(id)), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.ep_charge_value, String.format(locale, "%.5f", c.value), unit, SloTime.formatLocalDate(c.validFrom, lang)),
                style = small,
                color = colors.onSurfaceVariant
            )
            c.note?.let { Text(it, style = small, color = colors.onSurfaceVariant) }
            LinkText(stringResource(R.string.ep_open_source)) { actions.onOpenUrl(c.sourceUrl) }
        }
        Text(stringResource(R.string.ep_vat_value, String.format(locale, "%.0f", data.vatRate * 100)), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        si.semafor.app.PriceLists.forDate(today)?.let { list ->
            val values = list.rows.joinToString(" · ") { "${it.block}: " + String.format(locale, "%.5f", it.energyEurPerKwh) }
            Text(stringResource(R.string.ep_network_value, values), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            LinkText(stringResource(R.string.ep_open_source)) { actions.onOpenUrl(list.sourceUrl) }
        }
    }
    Text(stringResource(R.string.ep_privacy), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
}

/**
 * The whole-bill estimate below the network calculator: energy, supplier fee, network charge, levies and VAT for the
 * typed powers and consumption. With two-tariff metering the user gives the share of use in MT.
 */
@Composable
fun WholeBillSection(inputs: BillInputs, data: PriceData?, setup: PricingSetup, onOpenPriceScreen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalLang.current
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    var mtShareText by rememberSaveable { mutableStateOf("") }
    InfoCard {
        Text(stringResource(R.string.calc_bill_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.padding(bottom = 6.dp))
        if (data == null || !setup.configured) {
            Text(stringResource(R.string.calc_bill_not_configured), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight), color = colors.onSurfaceVariant)
            LinkText(stringResource(R.string.calc_bill_open), onOpenPriceScreen)
            return@InfoCard
        }
        if (setup.meter == MeterType.DUAL) {
            NumberRow(stringResource(R.string.calc_bill_mt_share), stringResource(R.string.calc_bill_mt_share), mtShareText) { mtShareText = it }
        }
        val today = SloTime.today()
        val date = if (inputs.notValidToday) inputs.list.validFrom else today
        val share = ((TariffCalculator.parseAmount(mtShareText) ?: 0.0) / 100.0).coerceIn(0.0, 1.0)
        val bill = EnergyPriceCalculator.monthlyBill(data, setup, inputs.list, date, inputs.powerKw, inputs.energyKwh, share)
        if (bill == null) {
            Text(stringResource(R.string.calc_bill_unavailable), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            BillBreakdown(bill, inputs.energyKwh.sum())
            val res = data.charge(ChargeId.RES_SUPPORT, date)
            val block = if (res?.unit == ChargeUnit.PER_KW_MONTH) {
                if (Season.of(date) == Season.HIGH) res.powerBlockHighSeason else res.powerBlockLowSeason
            } else null
            val day = SloTime.formatLocalDate(date, lang)
            Text(
                if (block != null) stringResource(R.string.calc_bill_note, day, block) else stringResource(R.string.calc_bill_note_plain, day),
                style = small,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        LinkText(stringResource(R.string.calc_bill_open), onOpenPriceScreen)
    }
}

/** Final variable price (EUR/kWh with VAT) of every block for the tariffs of the user's meter, for today. */
@Composable
private fun BlockPriceTable(data: PriceData, setup: PricingSetup) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val lang = LocalLang.current
    val today = SloTime.today()
    val network = si.semafor.app.PriceLists.forDate(today)
    val tariffs = if (setup.meter == MeterType.SINGLE) listOf(si.semafor.app.pricing.EnergyTariff.ET)
    else listOf(si.semafor.app.pricing.EnergyTariff.VT, si.semafor.app.pricing.EnergyTariff.MT)
    if (network == null) {
        Text(stringResource(R.string.ep_missing), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(stringResource(R.string.prices_col_block), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        for (t in tariffs) {
            Text(tariffShort(t), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    for (b in 1..5) {
        val values = tariffs.map { t -> EnergyPriceCalculator.components(data, setup, network, today, b, t)?.total }
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(androidx.compose.ui.graphics.Color(si.semafor.app.BlockPalette.color(b, today)))
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ep_chip_block, b), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            }
            for (v in values) {
                Text(
                    v?.let { PriceNow.formatNumber(it, locale) } ?: "–",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
    Text(
        stringResource(R.string.ep_blocks_note, SloTime.formatLocalDate(today, lang)) + " " + stringResource(R.string.ep_vat_note),
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight),
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Monthly items that do not depend on the kWh used: rates, and amounts with VAT when the agreed power is known. */
@Composable
private fun FixedCosts(data: PriceData, setup: PricingSetup, agreedKw: List<Double?>, agreedSource: String?) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val today = SloTime.today()
    val network = si.semafor.app.PriceLists.forDate(today)
    val vat = 1 + data.vatRate
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    var total = 0.0
    var complete = true

    @Composable
    fun row(label: String, rate: String, amount: Double?) {
        Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                Text(rate, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(amount?.let { PriceNow.formatEur(it, locale) } ?: "–", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }

    if (network != null) {
        for (r in network.rows) {
            val kw = agreedKw.getOrNull(r.block - 1)
            val amount = kw?.let { it * r.powerEurPerKw * vat }
            if (amount == null) complete = false else total += amount
            row(stringResource(R.string.ep_fixed_power, r.block), String.format(locale, "%.5f", r.powerEurPerKw) + " " + stringResource(R.string.ep_unit_kw_month), amount)
        }
    }
    data.charge(ChargeId.RES_SUPPORT, today)?.takeIf { it.unit == ChargeUnit.PER_KW_MONTH }?.let { c ->
        val block = (if (Season.of(today) == Season.HIGH) c.powerBlockHighSeason else c.powerBlockLowSeason) ?: 1
        val kw = agreedKw.getOrNull(block - 1)
        val amount = kw?.let { it * c.value * vat }
        if (amount == null) complete = false else total += amount
        row(stringResource(R.string.ep_fixed_res, block), String.format(locale, "%.5f", c.value) + " " + stringResource(R.string.ep_unit_kw_month), amount)
    }
    val fee = EnergyPriceCalculator.supplierFeeNet(data, setup, today)
    if (fee > 0) {
        total += fee * vat
        row(stringResource(R.string.ep_fixed_fee), stringResource(R.string.ep_unit_month), fee * vat)
    }
    if (complete && total > 0) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.ep_fixed_total), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(PriceNow.formatEur(total, locale), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        }
    }
    Text(
        if (agreedSource != null && agreedKw.any { it != null }) stringResource(R.string.ep_fixed_note, agreedSource)
        else stringResource(R.string.ep_fixed_note_rates),
        style = small,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Final price of every published package for the current block and tariff (EUR/kWh with VAT), fee shown apart. */
@Composable
private fun OfferComparison(data: PriceData, setup: PricingSetup, current: KwhPrice?) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val today = SloTime.today()
    val packages = data.suppliers.flatMap { s -> s.packages.filter { it.appliesOn(today) }.map { s to it } }
    if (packages.isEmpty() || current == null) {
        Text(stringResource(R.string.ep_compare_empty), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight), color = colors.onSurfaceVariant)
        return
    }
    val network = si.semafor.app.PriceLists.forDate(today)
    val mine = (setup.source as? si.semafor.app.pricing.EnergySource.Package)?.id
    val rows = packages.map { (s, p) ->
        val alt = PricingSetup(setup.meter, si.semafor.app.pricing.EnergySource.Package(p.id))
        Triple(s, p, EnergyPriceCalculator.components(data, alt, network, today, current.block, current.tariff)?.total)
    }.sortedBy { it.third ?: Double.MAX_VALUE }
    for ((s, p, total) in rows) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name + ", " + p.name + if (p.id == mine) " (" + stringResource(R.string.ep_compare_yours) + ")" else "", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                p.monthlyFee?.let { Text(stringResource(R.string.ep_compare_fee, PriceNow.formatEur(it * (1 + data.vatRate), locale)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            Text(total?.let { PriceNow.formatKwh(it, locale) } ?: stringResource(R.string.ep_compare_na), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }
    Text(
        stringResource(R.string.ep_compare_note, current.block, tariffShort(current.tariff)),
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight),
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

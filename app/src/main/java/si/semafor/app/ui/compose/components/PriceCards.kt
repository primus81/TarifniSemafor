package si.semafor.app.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.BlockPalette
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.pricing.BillItemKind
import si.semafor.app.pricing.EnergyTariff
import si.semafor.app.pricing.KwhPrice
import si.semafor.app.pricing.MonthlyBill
import si.semafor.app.pricing.PriceComponents
import si.semafor.app.pricing.PriceNow
import si.semafor.app.ui.compose.charts.BarPart
import si.semafor.app.ui.compose.charts.PriceColors
import si.semafor.app.ui.compose.charts.StackedBar
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.theme.BodyLineHeight
import java.util.Locale

@Composable
fun tariffShort(t: EnergyTariff): String = stringResource(
    when (t) {
        EnergyTariff.ET -> R.string.ep_tariff_et
        EnergyTariff.VT -> R.string.ep_tariff_vt
        EnergyTariff.MT -> R.string.ep_tariff_mt
    }
)

@Composable
fun tariffLong(t: EnergyTariff): String = stringResource(
    when (t) {
        EnergyTariff.ET -> R.string.ep_tariff_et_long
        EnergyTariff.VT -> R.string.ep_tariff_vt_long
        EnergyTariff.MT -> R.string.ep_tariff_mt_long
    }
)

private fun tariffIcon(t: EnergyTariff): Int = when (t) {
    EnergyTariff.ET -> R.drawable.ic_bolt
    EnergyTariff.VT -> R.drawable.ic_tariff_vt
    EnergyTariff.MT -> R.drawable.ic_tariff_mt
}

/** A small rounded label: the block in its official colour, or the tariff with a sun / moon / bolt icon. */
@Composable
private fun PriceChip(text: String, background: Color, content: Color, icon: Int? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

@Composable
private fun BlockAndTariffChips(block: Int, tariff: EnergyTariff, date: java.time.LocalDate) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PriceChip(stringResource(R.string.ep_chip_block, block), Color(BlockPalette.color(block, date)), Color(BlockPalette.ON_BLOCK))
        PriceChip(tariffShort(tariff), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, tariffIcon(tariff))
    }
}

/** Blinking dot with "ZDAJ": the price is the one of this moment. */
@Composable
private fun NowBadge() {
    val pulse = rememberInfiniteTransition(label = "now")
    // Read only in the draw phase: the pulse does not recompose the card on every frame.
    val a = pulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "nowAlpha")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = a.value }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.ep_now_badge), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The line about the cheapest coming hour, or null if there is nothing to say. */
@Composable
fun nextCheaperText(next: KwhPrice?, nowMillis: Long, locale: Locale): String? {
    next ?: return null
    val time = SloTime.formatHm(next.startMillis)
    val price = PriceNow.formatKwh(next.total, locale)
    val tariff = tariffShort(next.tariff)
    return if (SloTime.dateOf(next.startMillis) != SloTime.dateOf(nowMillis)) {
        stringResource(R.string.ep_next_cheaper_tomorrow, time, next.block, tariff, price)
    } else {
        stringResource(R.string.ep_next_cheaper, time, next.block, tariff, price)
    }
}

/**
 * The main price card: "Zdaj: 0,148 €/kWh skupaj z omrežnino in DDV", the block and the tariff as chips and the next
 * cheaper time. Without a configured energy price it invites the user to set one. [onClick] opens the price screen.
 */
@Composable
fun CurrentTotalPriceCard(
    price: KwhPrice?,
    next: KwhPrice?,
    configured: Boolean,
    hourlyKnown: Boolean,
    nowMillis: Long,
    onSetup: () -> Unit,
    onClick: (() -> Unit)? = null,
    title: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
        .clip(shape)
        .background(colors.surface)
        .border(1.dp, colors.outline, shape)
    if (!configured) {
        Column(base.padding(16.dp)) {
            if (title != null) CardTitle(title)
            Text(stringResource(R.string.ep_not_configured_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                stringResource(R.string.ep_not_configured_text),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            ActionButton(stringResource(R.string.ep_setup_button), onClick = onSetup)
        }
        return
    }
    if (price == null) {
        Column(base.padding(16.dp)) {
            if (title != null) CardTitle(title)
            NowBadge()
            Text(
                stringResource(R.string.ep_missing),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        return
    }
    val formatted = PriceNow.formatKwh(price.total, locale)
    val spoken = stringResource(R.string.ep_now_spoken, PriceNow.formatNumber(price.total, locale), price.block, tariffLong(price.tariff))
    val nextText = nextCheaperText(next, nowMillis, locale) ?: if (hourlyKnown) stringResource(R.string.ep_no_cheaper) else null
    Column(
        base
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        if (title != null) CardTitle(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NowBadge()
            Spacer(Modifier.weight(1f))
            BlockAndTariffChips(price.block, price.tariff, SloTime.dateOf(price.startMillis))
        }
        // The number slides when the price changes (a new block or tariff).
        AnimatedContent(
            targetState = formatted,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "price"
        ) { text ->
            Text(
                stringResource(R.string.ep_now_line, text),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    }
            )
        }
        Text(stringResource(R.string.ep_now_suffix), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (nextText != null) {
            Text(
                nextText,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight, fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        extra?.invoke(this)
    }
}

/** Small bold heading inside a card (announced as a heading). */
@Composable
fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .semantics { heading() }
    )
}

/** One labelled amount with a colour square; [share] in percent of the total (null = not shown). */
@Composable
private fun PartRow(color: Color?, label: String, value: String, share: String?, emphasis: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (color != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (share != null) {
            Text(share, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
        }
        Text(value, style = if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

private fun percent(part: Double, total: Double, locale: Locale): String =
    String.format(locale, "%.0f", if (total > 0) part / total * 100 else 0.0)

/** The price of one kWh split into its parts: a stacked bar and one row per part (EUR/kWh, share of the total). */
@Composable
fun PriceBreakdown(c: PriceComponents) {
    val locale = currentLocale
    fun v(x: Double) = String.format(locale, "%.4f", x)
    val rows = buildList {
        add(Triple(PriceColors.Energy, R.string.ep_item_energy, c.energy))
        add(Triple(PriceColors.Network, R.string.ep_item_network_energy, c.networkEnergy))
        add(Triple(PriceColors.Charges, R.string.ep_item_ure, c.ure))
        add(Triple(PriceColors.Charges, R.string.ep_item_market, c.marketOperator))
        add(Triple(PriceColors.Charges, R.string.ep_item_excise, c.excise))
        if (c.resSupport > 0) add(Triple(PriceColors.Res, R.string.ep_item_res, c.resSupport))
    }
    // Ring of the shares, the final price in the middle; the table below gives the exact amounts.
    val ringParts = listOf(
        BarPart(c.energy, PriceColors.Energy),
        BarPart(c.networkEnergy, PriceColors.Network),
        BarPart(c.ure + c.marketOperator + c.excise, PriceColors.Charges),
        BarPart(c.resSupport, PriceColors.Res),
        BarPart(c.vat, PriceColors.Vat)
    )
    val ringSpoken = stringResource(
        R.string.calc_bill_chart_spoken,
        rows.map { stringResource(it.second) + " " + percent(it.third, c.total, locale) + " %" }.joinToString(", ") +
            ", " + stringResource(R.string.ep_legend_vat) + " " + percent(c.vat, c.total, locale) + " %"
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        si.semafor.app.ui.compose.charts.DonutChart(
            ringParts,
            stringResource(R.string.ep_donut_center, PriceNow.formatNumber(c.total, locale)),
            ringSpoken
        )
    }
    for ((color, label, value) in rows) {
        PartRow(color, stringResource(label), v(value), stringResource(R.string.ep_share, percent(value, c.total, locale)))
    }
    val vatPercent = String.format(locale, "%.0f", c.vatRate * 100)
    PartRow(PriceColors.Vat, stringResource(R.string.ep_item_vat, vatPercent), v(c.vat), stringResource(R.string.ep_share, percent(c.vat, c.total, locale)))
    PartRow(null, stringResource(R.string.ep_now_suffix), PriceNow.formatKwh(c.total, locale), null, emphasis = true)
}

private fun billColor(kind: BillItemKind): Color = when (kind) {
    BillItemKind.ENERGY -> PriceColors.Energy
    BillItemKind.SUPPLIER_FEE -> PriceColors.Fee
    BillItemKind.NETWORK_POWER -> PriceColors.NetworkPower
    BillItemKind.NETWORK_ENERGY -> PriceColors.Network
    BillItemKind.RES_SUPPORT -> PriceColors.Res
    BillItemKind.URE, BillItemKind.MARKET_OPERATOR, BillItemKind.EXCISE -> PriceColors.Charges
}

private fun billLabel(kind: BillItemKind): Int = when (kind) {
    BillItemKind.ENERGY -> R.string.ep_item_energy
    BillItemKind.SUPPLIER_FEE -> R.string.ep_item_fee
    BillItemKind.NETWORK_POWER -> R.string.ep_item_network_power
    BillItemKind.NETWORK_ENERGY -> R.string.ep_item_network_energy
    BillItemKind.RES_SUPPORT -> R.string.ep_item_res
    BillItemKind.URE -> R.string.ep_item_ure
    BillItemKind.MARKET_OPERATOR -> R.string.ep_item_market
    BillItemKind.EXCISE -> R.string.ep_item_excise
}

/** A monthly bill by items: stacked bar (also announced as text) and one row per item with its share. */
@Composable
fun BillBreakdown(bill: MonthlyBill, totalKwh: Double) {
    val locale = currentLocale
    val total = bill.total
    val labels = bill.items.map { stringResource(billLabel(it.kind)) }
    val spoken = bill.items.mapIndexed { i, it -> labels[i] + " " + percent(it.net, total, locale) + " %" }.joinToString(", ")
    val description = stringResource(R.string.calc_bill_chart_spoken, spoken)
    Box(Modifier.clearAndSetSemantics { contentDescription = description }) {
        StackedBar(
            bill.items.map { BarPart(it.net, billColor(it.kind)) } + BarPart(bill.vat, PriceColors.Vat),
            Modifier.padding(vertical = 8.dp)
        )
    }
    // Variable items (depend on the kWh used) first, then the fixed monthly ones.
    for (fixed in listOf(false, true)) {
        val group = bill.items.withIndex().filter { it.value.fixed == fixed }
        if (group.isEmpty()) continue
        Text(
            stringResource(if (fixed) R.string.ep_fixed_title else R.string.ep_variable_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )
        for ((i, item) in group) {
            PartRow(billColor(item.kind), labels[i], PriceNow.formatEur(item.net, locale), stringResource(R.string.ep_share, percent(item.net, total, locale)))
        }
    }
    val vatPercent = String.format(locale, "%.0f", bill.vatRate * 100)
    PartRow(PriceColors.Vat, stringResource(R.string.ep_item_vat, vatPercent), PriceNow.formatEur(bill.vat, locale), stringResource(R.string.ep_share, percent(bill.vat, total, locale)))
    PartRow(null, stringResource(R.string.calc_bill_net), PriceNow.formatEur(bill.net, locale), null)
    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        PartRow(null, stringResource(R.string.calc_bill_total), PriceNow.formatEur(total, locale), null, emphasis = true)
    }
    if (totalKwh > 0) {
        Text(
            stringResource(R.string.calc_bill_avg, PriceNow.formatKwh(total / totalKwh, locale)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

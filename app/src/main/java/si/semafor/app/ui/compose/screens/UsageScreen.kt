package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.IntervalLabel
import si.semafor.app.mojelektro.PowerLevel
import si.semafor.app.mojelektro.UsageAnalysis
import si.semafor.app.mojelektro.UsageData
import si.semafor.app.mojelektro.UsageUiState
import si.semafor.app.ui.compose.charts.AgreedPowerGauge
import si.semafor.app.ui.compose.charts.DailyBlocksChart
import si.semafor.app.ui.compose.charts.ExportColor
import si.semafor.app.ui.compose.charts.NearBar
import si.semafor.app.ui.compose.charts.NormalBar
import si.semafor.app.ui.compose.charts.OverBar
import si.semafor.app.ui.compose.charts.PeakBarChart
import si.semafor.app.ui.compose.charts.ProductionChart
import si.semafor.app.ui.compose.charts.blockColor
import si.semafor.app.ui.compose.charts.fmt
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.PrimaryButton
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.theme.BodyLineHeight
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("d. M. yyyy")

/**
 * "Moja poraba": the user's own Moj Elektro readings next to the tariff blocks. Optional (shown only with a
 * connection), all data is on the device. The delay of the data is stated at the top of every state.
 */
@Composable
fun UsageScreen(
    state: UsageUiState,
    rangeDays: Int,
    ranges: List<Int>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onOpenConnection: () -> Unit
) {
    ScreenScaffold {
        ScreenHeader(title = "Moja poraba", subtitle = "Podatki portala Moj Elektro", onBack = onBack)
        DelayNotice((state as? UsageUiState.Content)?.data)

        when (state) {
            UsageUiState.NotConnected -> InfoCard {
                Body("Za prikaz tvoje porabe najprej poveži svoje merilno mesto z žetonom API portala Moj Elektro. Povezava je neobvezna; podatki ostanejo v telefonu.")
                Spacer(Modifier.height(10.dp))
                ActionButton("Poveži merilno mesto", onOpenConnection)
            }

            is UsageUiState.Loading -> InfoCard { Body(state.step) }

            is UsageUiState.Error -> {
                InfoCard { Body(state.message, color = WarningColor) }
                PrimaryButton("Poskusi znova", onRefresh)
            }

            is UsageUiState.Content -> Content(state.data, rangeDays, ranges, refreshing, onRefresh, onRangeChange)
        }
    }
}

@Composable
private fun DelayNotice(data: UsageData?) {
    InfoCard {
        Text(
            text = "Podatki niso v realnem času",
            style = MaterialTheme.typography.titleSmall,
            color = WarningColor
        )
        Spacer(Modifier.height(4.dp))
        val last = data?.lastDay?.let { " Zadnji dan z vsemi podatki: ${it.format(DAY)}." } ?: ""
        Body("Moj Elektro objavi meritve z zamikom, običajno približno en dan.$last", small = true)
    }
}

@Composable
private fun Content(
    data: UsageData,
    rangeDays: Int,
    ranges: List<Int>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRangeChange: (Int) -> Unit
) {
    if (data.fromCacheAfterError) {
        InfoCard { Body("Osvežitev ni uspela, zato so prikazani shranjeni podatki.", color = WarningColor) }
    }
    if (data.label == IntervalLabel.UNKNOWN) {
        InfoCard {
            Body(
                "Časovnih oznak 15-minutnih meritev ni bilo mogoče zanesljivo razvrstiti, zato meritve niso " +
                    "razdeljene po časovnih blokih.",
                color = WarningColor
            )
        }
    }

    AgreedPowerCard(data)

    SectionHeading("15-minutne konice")
    InfoCard {
        val day = data.peakDay
        if (day == null || day.intervals.isEmpty()) {
            Body("Za zadnje dni še ni popolnih 15-minutnih podatkov.")
        } else {
            Body("${day.date.format(DAY)} · skupaj ${fmt(day.totalKwh, 1)} kWh", small = true)
            Spacer(Modifier.height(8.dp))
            PeakBarChart(day)
            Spacer(Modifier.height(8.dp))
            LegendRow(listOf(NormalBar to "običajno", NearBar to "nad 90 %", OverBar to "nad dogovorjeno"))
            Body(
                "Stolpci kažejo povprečno moč v vsaki četrturi (kWh × 4), črtkana črta dogovorjeno moč bloka, " +
                    "barvni trak pod stolpci časovni blok. Prag 90 % je samo opozorilo aplikacije.",
                small = true
            )
            val over = day.intervals.count { it.level == PowerLevel.OVER }
            if (over > 0) Body("Dogovorjena moč je bila presežena v $over četrturah.", color = OverBar)
        }
    }

    SectionHeading("Poraba po časovnih blokih")
    RangeSelector(rangeDays, ranges, onRangeChange)
    InfoCard {
        val days = data.daily
        if (days.isEmpty()) {
            Body("V izbranem obdobju ni 15-minutnih podatkov.")
        } else {
            DailyBlocksChart(days)
            Spacer(Modifier.height(8.dp))
            LegendRow((1..5).map { blockColor(it) to "$it" } + (Color(0xFF888888) to "neznan"))
            val total = days.sumOf { it.totalKwh }
            val perBlock = (0 until 5).map { b -> days.sumOf { it.kwhByBlock[b] } }
            val text = buildString {
                append("Skupaj ${fmt(total, 1)} kWh. ")
                if (total > 0) {
                    perBlock.forEachIndexed { i, v ->
                        if (v > 0) append("Blok ${i + 1}: ${fmt(v, 1)} kWh (${fmt(v / total * 100, 0)} %). ")
                    }
                }
            }
            Body(text.trim(), small = true)
        }
    }

    data.production?.let { prod ->
        SectionHeading("Prejeta in oddana energija")
        InfoCard {
            Body("${prod.date.format(DAY)} · prejeto ${fmt(prod.totalImport)} kWh, oddano ${fmt(prod.totalExport)} kWh", small = true)
            Spacer(Modifier.height(8.dp))
            ProductionChart(prod, NormalBar)
            Spacer(Modifier.height(8.dp))
            LegendRow(listOf(NormalBar to "prejeto (A+)", ExportColor to "oddano (A-)"))
        }
    }

    Body(
        "Preneseno ${SloTime.formatDateTime(data.fetchedAtMillis)}. Časovni bloki so iz uradnih podatkov (uro.si). " +
            "Izračuni so informativni in ne nadomeščajo računa.",
        small = true
    )
    PrimaryButton(if (refreshing) "Osvežujem …" else "Osveži") { if (!refreshing) onRefresh() }
}

@Composable
private fun AgreedPowerCard(data: UsageData) {
    SectionHeading("Dogovorjena moč")
    InfoCard {
        val agreed = data.agreed
        val block = data.currentBlockId
        when {
            agreed == null -> Body("Moj Elektro za tvoje merilno mesto ne navaja veljavne dogovorjene moči.")
            block == null -> {
                Body("Trenutni časovni blok ni znan (ni preverjenih podatkov uro.si).")
                AgreedPowerList(agreed.blockKw.map { it?.toDouble() })
            }

            else -> {
                val kw = agreed.blockKw.getOrNull(block - 1)?.toDouble()
                Text(
                    text = "Zdaj velja $block. blok: " + (kw?.let { "${fmt(it)} kW" } ?: "ni podatka"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val day = data.peakDay
                val peak = day?.let { UsageAnalysis.peakInBlock(it, block) }
                if (kw != null && kw > 0 && day != null && peak != null) {
                    Spacer(Modifier.height(10.dp))
                    AgreedPowerGauge(kw, peak.kw, blockColor(block))
                    Spacer(Modifier.height(6.dp))
                    Body(
                        "Najvišja povprečna 15-minutna moč v $block. bloku dne ${day.date.format(DAY)}: " +
                            "${fmt(peak.kw)} kW ob ${SloTime.formatHm(peak.startMillis)} " +
                            "(${fmt(peak.kw / kw * 100, 0)} % dogovorjene moči). Bela oznaka je 100 %.",
                        small = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                AgreedPowerList(agreed.blockKw.map { it?.toDouble() })
            }
        }
    }
}

@Composable
private fun AgreedPowerList(kw: List<Double?>) {
    Body(
        "Po blokih: " + kw.mapIndexed { i, v -> "${i + 1}: ${v?.let { fmt(it) } ?: "–"} kW" }.joinToString(" · "),
        small = true
    )
}

@Composable
private fun RangeSelector(selected: Int, ranges: List<Int>, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        for (days in ranges) {
            val isSelected = days == selected
            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clickable(role = Role.Tab) { onChange(days) }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$days dni",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendRow(items: List<Pair<Color, String>>) {
    // Wraps to a second line on narrow screens and with large fonts.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((color, label) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Body(text: String, small: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    val style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    Text(
        text = text,
        style = style.copy(lineHeight = BodyLineHeight),
        color = if (small && color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else color,
        modifier = Modifier.padding(top = 2.dp)
    )
}

private val WarningColor = Color(0xFFFDD835)

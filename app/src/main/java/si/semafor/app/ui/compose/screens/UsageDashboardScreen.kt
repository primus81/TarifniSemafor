package si.semafor.app.ui.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.semafor.app.PriceLists
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.BlockPowerRow
import si.semafor.app.mojelektro.DayProfile
import si.semafor.app.mojelektro.IntervalLabel
import si.semafor.app.mojelektro.UsageAnalysis
import si.semafor.app.mojelektro.UsageData
import si.semafor.app.mojelektro.UsageInsights
import si.semafor.app.mojelektro.UsageUiState
import si.semafor.app.ui.compose.charts.AgreedPowerGauge
import si.semafor.app.ui.compose.charts.CombinedDayChart
import si.semafor.app.ui.compose.charts.DailyBlocksChart
import si.semafor.app.ui.compose.charts.DailyPeaksChart
import si.semafor.app.ui.compose.charts.ExportColor
import si.semafor.app.ui.compose.charts.NearBar
import si.semafor.app.ui.compose.charts.NormalBar
import si.semafor.app.ui.compose.charts.OverBar
import si.semafor.app.ui.compose.charts.ProductionChart
import si.semafor.app.ui.compose.charts.blockColor
import si.semafor.app.ui.compose.charts.fmt
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.Body
import si.semafor.app.ui.compose.components.Choice
import si.semafor.app.ui.compose.components.ChoiceChips
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LegendRow
import si.semafor.app.ui.compose.components.PrimaryButton
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.theme.AppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("d. M. yyyy")
private val SHORT_DAY = DateTimeFormatter.ofPattern("d. M.")
private val WEEKDAYS = listOf("pon", "tor", "sre", "čet", "pet", "sob", "ned")
private val UnknownColor = Color(0xFF888888)

/** "tor 10. 11." */
private fun shortDay(date: LocalDate) = WEEKDAYS[date.dayOfWeek.value - 1] + " " + date.format(SHORT_DAY)

/**
 * "Moj semafor porabe": the user's own Moj Elektro readings combined with the official URO block schedule. Shown as
 * the second page of the home screen when a Moj Elektro connection exists. Everything is computed on the device.
 *
 * Order: summary (metering point, agreed power, freshness, privacy) -> the interactive combined chart of one day ->
 * secondary charts (daily peaks, energy by block, taken/delivered energy) -> details and explanations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboardScreen(
    state: UsageUiState,
    rangeDays: Int,
    ranges: List<Int>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onManageConnection: () -> Unit
) {
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The day shown in the combined chart (ISO date, saveable); null = the latest complete day.
    var chosenDay by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = contentPadding()
            ) {
                item(key = "title") { DashboardTitle() }
                when (state) {
                    UsageUiState.NotConnected -> item(key = "not-connected") {
                        InfoCard {
                            Body("Za prikaz tvoje porabe najprej poveži svoje merilno mesto z žetonom API portala Moj Elektro. Povezava je neobvezna; podatki ostanejo v telefonu.")
                            Spacer(Modifier.height(10.dp))
                            ActionButton("Poveži merilno mesto", onClick = onManageConnection)
                        }
                    }

                    is UsageUiState.Loading -> item(key = "loading") { LoadingCard(state.step) }

                    is UsageUiState.Error -> item(key = "error") {
                        InfoCard {
                            Body(state.message, color = AppColors.Warning)
                            Spacer(Modifier.height(10.dp))
                            ActionButton("Upravljaj povezavo", onClick = onManageConnection)
                        }
                        PrimaryButton("Poskusi znova", onRefresh)
                    }

                    is UsageUiState.Content -> content(
                        data = state.data,
                        chosenDay = chosenDay?.let(LocalDate::parse),
                        onChooseDay = { date, scrollToChart ->
                            chosenDay = date.toString()
                            if (scrollToChart) scope.launch { listState.animateScrollToItem(HERO_INDEX) }
                        },
                        rangeDays = rangeDays,
                        ranges = ranges,
                        refreshing = refreshing,
                        onRefresh = onRefresh,
                        onRangeChange = onRangeChange,
                        onManageConnection = onManageConnection
                    )
                }
            }
        }
    }
}

/** Position of the combined chart in the list (title 0, summary 1, notices 2, chart 3). */
private const val HERO_INDEX = 3

@Composable
private fun contentPadding(): PaddingValues {
    val dir = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .asPaddingValues()
    return PaddingValues(
        start = 24.dp + bars.calculateStartPadding(dir),
        top = 8.dp,
        end = 24.dp + bars.calculateEndPadding(dir),
        bottom = 24.dp + bars.calculateBottomPadding()
    )
}

@Composable
private fun DashboardTitle() {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            text = "Moj semafor porabe",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Tvoja poraba (Moj Elektro) ob časovnih blokih (uro.si)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun LoadingCard(step: String) {
    InfoCard {
        Body(step, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(10.dp))
        Body("Prvo nalaganje lahko traja nekaj sekund: aplikacija prenese meritve in uradne časovne bloke za vsak dan.", small = true)
    }
}

private fun LazyListScope.content(
    data: UsageData,
    chosenDay: LocalDate?,
    onChooseDay: (LocalDate, Boolean) -> Unit,
    rangeDays: Int,
    ranges: List<Int>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onManageConnection: () -> Unit
) {
    val withData = data.profiles.filter { it.intervals.isNotEmpty() }
    val day = withData.firstOrNull { it.date == chosenDay }
        ?: data.peakDay
        ?: withData.lastOrNull()

    item(key = "summary") { SummaryCard(data, onManageConnection) }
    item(key = "notices") { Notices(data) }
    item(key = "hero") { HeroCard(day, withData, onChooseDay = { onChooseDay(it, false) }) }

    item(key = "peaks-title") { SectionHeading("Dnevne 15-minutne konice") }
    item(key = "peaks") { PeaksCard(data, day?.date) { onChooseDay(it, true) } }

    item(key = "blocks-title") { SectionHeading("Poraba po časovnih blokih") }
    item(key = "range") {
        ChoiceChips(
            choices = ranges.map { Choice(it, "$it dni") },
            selected = rangeDays,
            onSelect = onRangeChange,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
    item(key = "blocks") { BlocksCard(data) }

    data.production?.let { prod ->
        item(key = "production-title") { SectionHeading("Prejeta in oddana energija") }
        item(key = "production") {
            InfoCard {
                Body("${prod.date.format(DAY)} · prejeto ${fmt(prod.totalImport)} kWh, oddano ${fmt(prod.totalExport)} kWh", small = true)
                Spacer(Modifier.height(8.dp))
                ProductionChart(prod, NormalBar)
                Spacer(Modifier.height(8.dp))
                LegendRow(listOf(NormalBar to "prejeto (A+)", ExportColor to "oddano (A-)"))
            }
        }
    }

    item(key = "details-title") { SectionHeading("Podrobnosti Moj Elektro") }
    item(key = "block-table") { BlockTableCard(data.blockRows, data.currentBlockId, rangeDays) }
    item(key = "registers") { RegistersCard(data) }
    item(key = "help") { HelpCard() }
    item(key = "footer") {
        Body(
            "Preneseno ${SloTime.formatDateTime(data.fetchedAtMillis)}. Časovni bloki so iz uradnih podatkov (uro.si). " +
                "Izračuni so informativni in ne nadomeščajo računa.",
            small = true
        )
        PrimaryButton(if (refreshing) "Osvežujem …" else "Osveži") { if (!refreshing) onRefresh() }
    }
}

/** Metering point, agreed power per block, freshness of the data and how the connection is stored. */
@Composable
private fun SummaryCard(data: UsageData, onManageConnection: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Merilno mesto (EIMM)", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(data.eimm, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            FreshnessBadge(data)
        }

        Spacer(Modifier.height(12.dp))
        Text("Dogovorjena moč", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val agreed = data.agreed
        if (agreed == null) {
            Body("Moj Elektro za tvoje merilno mesto ne navaja veljavne dogovorjene moči.")
        } else {
            AgreedPowerChips(agreed.blockKw.map { it?.toDouble() }, data.currentBlockId)
        }

        Spacer(Modifier.height(12.dp))
        val last = data.lastDay?.let { "Zadnji popoln dan: ${it.format(DAY)}. " } ?: ""
        Body(
            "${last}Preneseno ${SloTime.formatDateTime(data.fetchedAtMillis)}. Moj Elektro objavi meritve z zamikom, " +
                "običajno približno en dan, zato tu ni tvoje trenutne porabe.",
            small = true
        )

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant)
        )
        Spacer(Modifier.height(10.dp))
        Text("Zasebnost", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Body(
            "Žeton API in EIMM sta šifrirana s ključem v varni shrambi telefona (Android Keystore) in ne zapustita " +
                "telefona, razen do uradnega strežnika Moj Elektro. Meritve so shranjene šifrirane. Povezavo in vse " +
                "shranjene meritve lahko kadar koli izbrišeš.",
            small = true
        )
        Spacer(Modifier.height(8.dp))
        ActionButton("Upravljaj povezavo", onClick = onManageConnection)
    }
}

/** "Sveže" / "Shranjeno": whether the shown readings come from the last download or from storage after an error. */
@Composable
private fun FreshnessBadge(data: UsageData) {
    val stale = data.fromCacheAfterError
    val color = if (stale) AppColors.Warning else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (stale) "Shranjeno" else "Osveženo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The agreed power of every block as small coloured chips; the block that is running now is outlined. */
@Composable
private fun AgreedPowerChips(kw: List<Double?>, currentBlock: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        kw.forEachIndexed { i, v ->
            val block = i + 1
            val now = block == currentBlock
            val shape = RoundedCornerShape(10.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(blockColor(block).copy(alpha = if (now) 0.35f else 0.16f))
                    .border(if (now) 2.dp else 1.dp, if (now) Color.White else blockColor(block).copy(alpha = 0.5f), shape)
                    .padding(vertical = 6.dp)
                    .clearAndSetSemantics {
                        contentDescription = "$block. blok" + (if (now) ", velja zdaj" else "") +
                            ": " + (v?.let { "${fmt(it)} kilovata" } ?: "ni podatka")
                    }
            ) {
                Text("$block", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(v?.let { fmt(it, 1) } ?: "–", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(if (now) "zdaj" else "kW", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Notices(data: UsageData) {
    Column(Modifier.animateContentSize()) {
        if (data.fromCacheAfterError) {
            InfoCard { Body("Osvežitev ni uspela, zato so prikazani shranjeni podatki.", color = AppColors.Warning) }
        }
        if (data.label == IntervalLabel.UNKNOWN) {
            InfoCard {
                Body(
                    "Časovnih oznak 15-minutnih meritev ni bilo mogoče zanesljivo razvrstiti, zato meritve niso " +
                        "razdeljene po časovnih blokih.",
                    color = AppColors.Warning
                )
            }
        }
    }
}

/**
 * The primary card: one day of the combined chart, a day picker, the readout of the touched interval and buttons to
 * step through the intervals (also the accessible way to explore the chart).
 */
@Composable
private fun HeroCard(day: DayProfile?, days: List<DayProfile>, onChooseDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(14.dp)
    ) {
        Text(
            text = "Tvoja moč in časovni bloki",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Body("Dotakni se grafa ali podrsaj po njem in preveri vsako četrturo.", small = true)

        if (day == null) {
            Spacer(Modifier.height(8.dp))
            Body("Za izbrano obdobje še ni 15-minutnih podatkov.")
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        // Newest first: the days of the range that have readings (at most the last 7).
        val recent = days.takeLast(7).reversed()
        if (recent.size > 1) {
            ChoiceChips(
                choices = recent.map { Choice(it.date, shortDay(it.date)) },
                selected = day.date,
                onSelect = onChooseDay
            )
            Spacer(Modifier.height(8.dp))
        }

        // The selected interval of this day; starts at the day's peak. Read only by the chart's draw phase and by
        // the readout below, so scrubbing never recomposes this card.
        val initial = day.peakIndex ?: day.intervals.lastIndex
        var selected by rememberSaveable(day.date.toString()) { mutableIntStateOf(initial) }
        val incomplete = day.intervals.size < UsageAnalysis.expectedIntervals(day.date)
        val total = fmt(day.totalKwh, 1)
        Body(
            "${day.date.format(DAY)} · skupaj $total kWh" + if (incomplete) " · dan še ni popoln" else "",
            small = true
        )
        Spacer(Modifier.height(6.dp))
        CombinedDayChart(day, selected = { selected }, onSelect = { selected = it })
        Spacer(Modifier.height(6.dp))
        LegendRow(
            (1..5).map { blockColor(it) to "$it. blok" } + listOf(
                Color.White to "dogovorjena moč",
                AppColors.Warning to "90 %",
                OverBar to "presežek"
            )
        )
        Spacer(Modifier.height(10.dp))
        IntervalReadout(day, selected = { selected }, onStep = { selected = it })
    }
}

/** Details of the selected interval; recomposes alone when the selection changes. */
@Composable
private fun IntervalReadout(day: DayProfile, selected: () -> Int, onStep: (Int) -> Unit) {
    val points = day.intervals
    val index = selected().coerceIn(0, points.lastIndex)
    val p = points[index]
    val colors = MaterialTheme.colorScheme
    val color = p.blockId?.let(::blockColor) ?: UnknownColor
    val shape = RoundedCornerShape(14.dp)
    val powerPrice = p.blockId?.let { b -> PriceLists.current()?.rows?.firstOrNull { it.block == b }?.powerEurPerKw }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = SloTime.formatHm(p.startMillis) + "–" + SloTime.formatHm(p.startMillis + UsageAnalysis.INTERVAL_MS) +
                    (if (index == day.peakIndex) " · najvišja konica dneva" else ""),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Body(UsageInsights.summary(p), modifier = Modifier.padding(top = 4.dp))
            Body(
                UsageInsights.powerText(p),
                color = when {
                    p.agreedKw == null -> colors.onSurface
                    p.kw > p.agreedKw -> OverBar
                    p.kw >= UsageAnalysis.NEAR_RATIO * p.agreedKw -> NearBar
                    else -> colors.onSurface
                }
            )
        }
        val agreedKw = p.agreedKw
        if (agreedKw != null && agreedKw > 0.0) {
            Spacer(Modifier.height(6.dp))
            AgreedPowerGauge(agreedKw, p.kw, color)
        }
        Body(UsageInsights.blockText(p), small = true, modifier = Modifier.padding(top = 4.dp))
        if (powerPrice != null) {
            Body("Postavka za moč v tem bloku: ${PriceLists.formatNumber(powerPrice)} €/kW/mesec (cenik SODO).", small = true)
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StepButton("Prejšnja četrtura", R.drawable.ic_arrow_back, Modifier.weight(1f), enabled = index > 0) {
                onStep(index - 1)
            }
            day.peakIndex?.let { peak ->
                StepButtonText("Konica", "Na najvišjo konico dneva", Modifier.weight(1f), enabled = index != peak) {
                    onStep(peak)
                }
            }
            StepButton("Naslednja četrtura", R.drawable.ic_arrow_back, Modifier.weight(1f), enabled = index < points.lastIndex, mirrored = true) {
                onStep(index + 1)
            }
        }
    }
}

@Composable
private fun StepButton(
    description: String,
    icon: Int,
    modifier: Modifier,
    enabled: Boolean,
    mirrored: Boolean = false,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(150), label = "step")
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier
                .size(20.dp)
                .rotate(if (mirrored) 180f else 0f)
        )
    }
}

@Composable
private fun StepButtonText(label: String, description: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(150), label = "peak")
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    }
}

/** Daily peaks against the agreed power with the 90 % / 100 % lines; tapping a day opens it in the main chart. */
@Composable
private fun PeaksCard(data: UsageData, selectedDate: LocalDate?, onDayClick: (LocalDate) -> Unit) {
    InfoCard {
        val peaks = data.dailyPeaks
        if (peaks.isEmpty()) {
            Body("V izbranem obdobju še ni 15-minutnih podatkov.")
            return@InfoCard
        }
        DailyPeaksChart(peaks, selectedDate, onDayClick)
        Spacer(Modifier.height(8.dp))
        LegendRow(listOf(OverBar to "nad 100 %", NearBar to "nad 90 %", Color.White to "dogovorjena moč"))
        Body("Stolpec je najvišja četrtura dneva kot delež dogovorjene moči njenega bloka. Dotakni se dneva za prikaz v glavnem grafu. Prag 90 % je samo opozorilo aplikacije.", small = true)
        val worst = peaks.filter { it.utilization != null }.maxByOrNull { it.utilization!! }
        if (worst != null) {
            Spacer(Modifier.height(6.dp))
            Body(
                "Najbližje dogovorjeni moči: ${shortDay(worst.date)} ob ${SloTime.formatHm(worst.peak.startMillis)}, " +
                    "${fmt(worst.peak.kw)} kW v ${worst.peak.blockId ?: "?"}. bloku. " + UsageInsights.peakVerdict(worst),
                color = if (worst.overCount > 0) OverBar else MaterialTheme.colorScheme.onSurface
            )
        }
        val overDays = peaks.count { it.overCount > 0 }
        if (overDays > 0) {
            Body("Dogovorjena moč je bila presežena v $overDays od ${peaks.size} dni.", color = OverBar)
        }
    }
}

@Composable
private fun BlocksCard(data: UsageData) {
    InfoCard {
        val days = data.daily
        if (days.isEmpty()) {
            Body("V izbranem obdobju ni 15-minutnih podatkov.")
            return@InfoCard
        }
        DailyBlocksChart(days)
        Spacer(Modifier.height(8.dp))
        LegendRow((1..5).map { blockColor(it) to "$it" } + (UnknownColor to "neznan"))
        Spacer(Modifier.height(8.dp))
        val total = days.sumOf { it.totalKwh }
        val perBlock = (0 until 5).map { b -> days.sumOf { it.kwhByBlock[b] } }
        Body("Skupaj ${fmt(total, 1)} kWh v ${days.size} dneh.", small = true)
        if (total > 0) {
            Spacer(Modifier.height(6.dp))
            perBlock.forEachIndexed { i, v -> ShareBar(i + 1, v, v / total) }
        }
    }
}

/** One block's share of the energy as a thin bar with the numbers. */
@Composable
private fun ShareBar(block: Int, kwh: Double, share: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = "$block. blok: ${fmt(kwh, 1)} kilovatnih ur, ${fmt(share * 100, 0)} odstotkov." }
    ) {
        Text("$block.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(blockColor(block))
            )
        }
        Text(
            "${fmt(kwh, 1)} kWh · ${fmt(share * 100, 0)} %",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Per block: agreed power, highest interval in the range and its share, excesses and the power price (SODO). */
@Composable
private fun BlockTableCard(rows: List<BlockPowerRow>, currentBlock: Int?, rangeDays: Int) {
    val colors = MaterialTheme.colorScheme
    val prices = PriceLists.current()
    InfoCard {
        Text("Moč po časovnih blokih (zadnjih $rangeDays dni)", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        for (r in rows) {
            val price = prices?.rows?.firstOrNull { it.block == r.blockId }?.powerEurPerKw
            val util = r.utilization
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(blockColor(r.blockId))
                        .border(if (r.blockId == currentBlock) 2.dp else 0.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${r.blockId}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Dogovorjena " + (r.agreedKw?.let { "${fmt(it)} kW" } ?: "ni podatka") +
                            (if (r.blockId == currentBlock) " · velja zdaj" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                    val peakText = r.peak?.let { p ->
                        "Najvišja ${fmt(p.kw)} kW (${shortDay(SloTime.dateOf(p.startMillis))} ob ${SloTime.formatHm(p.startMillis)})" +
                            (util?.let { " · ${UsageInsights.percent(it)}" } ?: "")
                    } ?: if (r.intervalCount == 0) "V obdobju ni bilo četrtur v tem bloku." else "Ni porabe."
                    Body(peakText, small = true)
                    if (r.overCount > 0) {
                        Body("Presežena v ${r.overCount} ${UsageInsights.quarters(r.overCount)}.", small = true, color = OverBar)
                    }
                    if (price != null) Body("Postavka za moč: ${PriceLists.formatNumber(price)} €/kW/mesec", small = true)
                }
            }
        }
        Body("Postavke so iz vgrajenega cenika SODO (brez DDV), dogovorjena moč iz Moj Elektro.", small = true)
    }
}

/** Totals of the registers and the quality of the readings. */
@Composable
private fun RegistersCard(data: UsageData) {
    val r = data.registers
    InfoCard {
        Text("Registri v obdobju", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        DetailRow("Prejeta energija (A+)", "${fmt(r.importKwh, 1)} kWh")
        r.exportKwh?.let { DetailRow("Oddana energija (A-)", "${fmt(it, 1)} kWh") }
        DetailRow("15-minutnih meritev", "${r.intervalCount}")
        if (r.missingCount > 0) DetailRow("Meritev brez vrednosti", "${r.missingCount}")
        DetailRow("Meritev z oznako stanja", "${r.flaggedCount}")
        // Each status code with the description from the Moj Elektro catalogue (GET /reading-qualities).
        for ((code, count) in r.flaggedByType.entries.sortedByDescending { it.value }) {
            val text = data.qualityDescriptions[code]
            Body(
                "• " + (if (text != null && text != code) "$text ($code)" else code) + ": $count",
                small = true,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        DetailRow(
            "Časovna oznaka meritve",
            when (data.label) {
                IntervalLabel.START -> "začetek četrture"
                IntervalLabel.END -> "konec četrture"
                IntervalLabel.UNKNOWN -> "ni zanesljivo določena"
            }
        )
        if (data.agreedFetchedAtMillis > 0L) {
            DetailRow("Dogovorjena moč prebrana", SloTime.formatDateTime(data.agreedFetchedAtMillis))
        }
        Spacer(Modifier.height(4.dp))
        Body(
            "Oznako stanja doda Moj Elektro (npr. ocenjena ali nadomestna vrednost). Ali časovna oznaka pomeni začetek " +
                "ali konec četrture, aplikacija ugotovi iz samih podatkov (primerja jih z dnevnimi stanji števca).",
            small = true
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Short explanations, folded by default. */
@Composable
private fun HelpCard() {
    InfoCard {
        HelpItem(
            "Kaj je dogovorjena moč?",
            "Moč, ki jo ima tvoje merilno mesto dogovorjeno za posamezen časovni blok. Podatek prebere aplikacija iz Moj Elektro. " +
                "Postavka za moč se obračuna glede na dogovorjeno (in morebitno preseženo) moč v bloku, ne glede na porabljene kWh."
        )
        HelpItem(
            "Kako se izračuna moč četrture?",
            "Števec zapiše energijo vsake četrture v kWh. Povprečna moč te četrture je energija, deljena s četrt ure (kWh × 4)."
        )
        HelpItem(
            "Od kod so časovni bloki?",
            "Za vsak pretekli dan aplikacija vpraša uradni vir uro.si, kateri blok je veljal ob posamezni uri. Pravila blokov niso vpisana v aplikacijo."
        )
        HelpItem(
            "Zakaj ni današnjih podatkov?",
            "Moj Elektro objavi meritve z zamikom, običajno približno en dan. Trenutno veljavni blok vidiš na prvi strani."
        )
    }
}

@Composable
private fun ColumnScope.HelpItem(title: String, text: String) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, tween(150), label = "help")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { open = !open }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = title + ", " + (if (open) "razširjeno" else "strnjeno")
            }
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }
    AnimatedVisibility(visible = open) {
        Body(text, small = true, modifier = Modifier.padding(bottom = 8.dp))
    }
}

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.semafor.app.AppLanguage
import si.semafor.app.AppSettings
import si.semafor.app.BlockPalette
import si.semafor.app.PriceLists
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.BlockPowerRow
import si.semafor.app.mojelektro.DayProfile
import si.semafor.app.mojelektro.IntervalLabel
import si.semafor.app.mojelektro.UsageAnalysis
import si.semafor.app.mojelektro.UsageData
import si.semafor.app.mojelektro.UsageInsights
import si.semafor.app.mojelektro.UsagePeriod
import si.semafor.app.planner.RunLogEntry
import si.semafor.app.pricing.PriceComponents
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.mojelektro.UsageUiState
import si.semafor.app.text
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.charts.AgreedPowerGauge
import si.semafor.app.ui.compose.charts.CombinedDayChart
import si.semafor.app.ui.compose.charts.DailyBlocksChart
import si.semafor.app.ui.compose.charts.DailyPeaksChart
import si.semafor.app.ui.compose.charts.ProductionChart
import si.semafor.app.ui.compose.charts.UnknownColor
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
import si.semafor.app.ui.compose.components.UsageSkeleton
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.theme.AppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val OnBlock = Color(BlockPalette.ON_BLOCK)

/** "tor 10. 11." / "Tue 10 Nov" */
internal fun shortDay(date: LocalDate, lang: String): String {
    val locale = AppLanguage.localeOf(lang)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
    val pattern = if (lang == AppSettings.LANGUAGE_EN) "d MMM" else "d. M."
    return weekday + " " + date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/**
 * "Semafor porabe": the user's own Moj Elektro readings combined with the official URO block schedule. Second page of
 * the main pager and second tab of the bottom bar. Without a connection ([connected] = false) it shows a short
 * introduction with a button to the connection settings. Everything is computed on the device.
 *
 * Order: summary (metering point, agreed power, freshness, privacy) -> the interactive combined chart of one day ->
 * secondary charts (daily peaks, energy by block, taken/delivered energy) -> details and explanations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboardScreen(
    connected: Boolean,
    state: UsageUiState,
    period: UsagePeriod,
    periods: List<UsagePeriod>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPeriodChange: (UsagePeriod) -> Unit,
    onManageConnection: () -> Unit,
    /** Price of one kWh in a block at a moment with the user's own prices; null = no own prices. */
    priceAt: ((Long, Int) -> PriceComponents?)? = null,
    /** Changes when the prices change (the weekly review is computed again). */
    priceKey: Any? = null,
    /** Starts confirmed in the planner (for the weekly review). */
    runLog: List<RunLogEntry> = emptyList(),
    onOpenPlanner: () -> Unit = {},
    onSetPrice: () -> Unit = {}
) {
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The day shown in the combined chart (ISO date, saveable); null = the latest complete day.
    var chosenDay by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (connected) onRefresh() },
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
                when {
                    !connected || state == UsageUiState.NotConnected ->
                        item(key = "onboarding") { OnboardingCard(onManageConnection) }

                    state is UsageUiState.Loading -> item(key = "loading") {
                        Body(
                            state.step.text(),
                            small = true,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        UsageSkeleton()
                        Body(stringResource(R.string.usage_loading_note), small = true, modifier = Modifier.padding(top = 8.dp))
                    }

                    state is UsageUiState.Error -> item(key = "error") {
                        InfoCard {
                            Body(state.message.text(), color = AppColors.Warning)
                            Spacer(Modifier.height(10.dp))
                            ActionButton(stringResource(R.string.usage_manage), onClick = onManageConnection)
                        }
                        PrimaryButton(stringResource(R.string.common_retry), onRefresh)
                    }

                    state is UsageUiState.Content -> content(
                        data = state.data,
                        chosenDay = chosenDay?.let(LocalDate::parse),
                        onChooseDay = { date, scrollToChart ->
                            chosenDay = date.toString()
                            if (scrollToChart) scope.launch { listState.animateScrollToItem(HERO_INDEX) }
                        },
                        period = period,
                        periods = periods,
                        refreshing = refreshing,
                        onRefresh = onRefresh,
                        onPeriodChange = onPeriodChange,
                        onManageConnection = onManageConnection,
                        review = WeekReviewInputs(priceAt, priceKey, runLog, onOpenPlanner, onSetPrice)
                    )
                }
            }
        }
    }
}

/** Position of the combined chart in the list (title 0, summary 1, notices 2, weekly review 3, chart 4). */
private const val HERO_INDEX = 4


@Composable
private fun contentPadding(): PaddingValues {
    val dir = LocalLayoutDirection.current
    // The bottom bar below this page already keeps clear of the navigation bar; only the sides are added here.
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal).asPaddingValues()
    return PaddingValues(
        start = 24.dp + bars.calculateStartPadding(dir),
        top = 8.dp,
        end = 24.dp + bars.calculateEndPadding(dir),
        bottom = 24.dp
    )
}

@Composable
private fun DashboardTitle() {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.usage_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.usage_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Shown without a Moj Elektro connection: what the page does, three steps and a button to the connection. */
@Composable
private fun OnboardingCard(onManageConnection: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.secondaryContainer)
            .border(1.dp, colors.outline, shape)
            .padding(18.dp)
    ) {
        // The five official block colours as a small "traffic light" strip.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            for (b in 1..5) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(blockColor(b))
                )
            }
        }
        Text(
            stringResource(R.string.usage_onboarding_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Body(stringResource(R.string.usage_onboarding_text), modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        for (step in listOf(R.string.usage_onboarding_step1, R.string.usage_onboarding_step2, R.string.usage_onboarding_step3)) {
            Body(stringResource(step), small = true, modifier = Modifier.padding(top = 2.dp))
        }
        PrimaryButton(stringResource(R.string.usage_onboarding_button), onManageConnection)
    }
}

private fun LazyListScope.content(
    data: UsageData,
    chosenDay: LocalDate?,
    onChooseDay: (LocalDate, Boolean) -> Unit,
    period: UsagePeriod,
    periods: List<UsagePeriod>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPeriodChange: (UsagePeriod) -> Unit,
    onManageConnection: () -> Unit,
    review: WeekReviewInputs
) {
    val withData = data.profiles.filter { it.intervals.isNotEmpty() }
    val day = withData.firstOrNull { it.date == chosenDay }
        ?: data.peakDay
        ?: withData.lastOrNull()

    item(key = "summary") { SummaryCard(data, onManageConnection) }
    item(key = "notices") { Notices(data) }
    item(key = "week") { WeekCard(data, review) { onChooseDay(it, true) } }
    item(key = "hero") { HeroCard(day, withData, onChooseDay = { onChooseDay(it, false) }) }

    item(key = "peaks-title") { SectionHeading(stringResource(R.string.usage_peaks_heading)) }
    item(key = "peaks") { PeaksCard(data, day?.date) { onChooseDay(it, true) } }

    item(key = "blocks-title") { SectionHeading(stringResource(R.string.usage_blocks_heading)) }
    item(key = "range") {
        ChoiceChips(
            choices = periods.map { Choice(it, periodLabel(it)) },
            selected = period,
            onSelect = onPeriodChange,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
    item(key = "blocks") { BlocksCard(data) { onChooseDay(it, true) } }

    data.production?.let { prod ->
        item(key = "production-title") { SectionHeading(stringResource(R.string.usage_production_heading)) }
        item(key = "production") {
            val locale = currentLocale
            InfoCard {
                Body(
                    stringResource(
                        R.string.usage_production_line,
                        SloTime.formatLocalDate(prod.date, LocalLang.current),
                        fmt(prod.totalImport, 2, locale),
                        fmt(prod.totalExport, 2, locale)
                    ),
                    small = true
                )
                Spacer(Modifier.height(8.dp))
                var showImport by rememberSaveable { mutableStateOf(true) }
                var showExport by rememberSaveable { mutableStateOf(true) }
                var at by rememberSaveable(prod.date.toString()) { mutableStateOf<Int?>(null) }
                ProductionChart(prod, AppColors.NeutralBar, showImport = showImport, showExport = showExport, selected = at, onSelect = { at = it })
                Spacer(Modifier.height(8.dp))
                // The legend is also the switch: each series can be hidden to read the other one better.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeriesToggle(AppColors.NeutralBar, stringResource(R.string.usage_legend_import), showImport) { if (it || showExport) showImport = it }
                    SeriesToggle(AppColors.Export, stringResource(R.string.usage_legend_export), showExport) { if (it || showImport) showExport = it }
                }
                at?.takeIf { it in prod.startMillis.indices }?.let { i ->
                    val t = prod.startMillis[i]
                    Body(
                        stringResource(
                            R.string.usage_production_at,
                            SloTime.formatHm(t) + "–" + SloTime.formatHm(t + UsageAnalysis.INTERVAL_MS),
                            fmt(prod.importKwh[i], 3, locale),
                            fmt(prod.exportKwh[i], 3, locale)
                        ),
                        small = true,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }

    item(key = "details-title") { SectionHeading(stringResource(R.string.usage_details_heading)) }
    item(key = "block-table") { BlockTableCard(data.blockRows, data.currentBlockId, periodLabel(period)) }
    item(key = "registers") { RegistersCard(data) }
    item(key = "help") { HelpCard() }
    item(key = "footer") {
        Body(stringResource(R.string.usage_footer, SloTime.formatDateTime(data.fetchedAtMillis, LocalLang.current)), small = true)
        PrimaryButton(stringResource(if (refreshing) R.string.common_refreshing else R.string.common_refresh)) {
            if (!refreshing) onRefresh()
        }
    }
}

/** Metering point, agreed power per block, freshness of the data and how the connection is stored. */
@Composable
private fun SummaryCard(data: UsageData, onManageConnection: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalLang.current
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.usage_eimm_label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(data.eimm, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            FreshnessBadge(data)
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.usage_agreed), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val agreed = data.agreed
        if (agreed == null) {
            Body(stringResource(R.string.usage_agreed_missing))
        } else {
            AgreedPowerChips(agreed.blockKw.map { it?.toDouble() }, data.currentBlockId)
        }

        Spacer(Modifier.height(12.dp))
        val last = data.lastDay?.let { stringResource(R.string.usage_last_day, SloTime.formatLocalDate(it, lang)) } ?: ""
        Body(stringResource(R.string.usage_freshness, last, SloTime.formatDateTime(data.fetchedAtMillis, lang)), small = true)

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant)
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.me_privacy_heading), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Body(stringResource(R.string.usage_privacy), small = true)
        Spacer(Modifier.height(8.dp))
        ActionButton(stringResource(R.string.usage_manage), onClick = onManageConnection)
    }
}

/** Whether the shown readings come from the last download or from storage after an error. */
@Composable
private fun FreshnessBadge(data: UsageData) {
    val stale = data.fromCacheAfterError
    val color = if (stale) AppColors.Near else AppColors.Export
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
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
            stringResource(if (stale) R.string.usage_stored else R.string.usage_fresh),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The agreed power of every block as chips in the official block colours; the block in force now is outlined. */
@Composable
private fun AgreedPowerChips(kw: List<Double?>, currentBlock: Int?) {
    val locale = currentLocale
    val ink = MaterialTheme.colorScheme.onSurface
    val nowSpoken = stringResource(R.string.usage_chip_spoken_now)
    val noData = stringResource(R.string.usage_no_data)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        kw.forEachIndexed { i, v ->
            val block = i + 1
            val now = block == currentBlock
            val shape = RoundedCornerShape(10.dp)
            val kwSpoken = v?.let { stringResource(R.string.usage_chip_kw_spoken, fmt(it, 2, locale)) } ?: noData
            val spoken = stringResource(R.string.usage_chip_spoken, block, if (now) nowSpoken else "", kwSpoken)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(blockColor(block))
                    .border(if (now) 2.dp else 0.dp, if (now) ink else Color.Transparent, shape)
                    .padding(vertical = 6.dp)
                    .clearAndSetSemantics { contentDescription = spoken }
            ) {
                Text("$block", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OnBlock)
                Text(v?.let { fmt(it, 1, locale) } ?: "–", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OnBlock)
                Text(
                    if (now) stringResource(R.string.usage_chip_now) else "kW",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnBlock
                )
            }
        }
    }
}

@Composable
private fun Notices(data: UsageData) {
    Column(Modifier.animateContentSize()) {
        if (data.fromCacheAfterError) {
            InfoCard { Body(stringResource(R.string.usage_cache_after_error), color = AppColors.Warning) }
        }
        if (data.label == IntervalLabel.UNKNOWN) {
            InfoCard { Body(stringResource(R.string.usage_label_unknown), color = AppColors.Warning) }
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
    val lang = LocalLang.current
    val locale = currentLocale
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
            text = stringResource(R.string.usage_hero_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Body(stringResource(R.string.usage_hero_hint), small = true)

        if (day == null) {
            Spacer(Modifier.height(8.dp))
            Body(stringResource(R.string.usage_no_15min))
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        // Newest first: the days of the range that have readings (at most the last 7).
        val recent = days.takeLast(7).reversed()
        if (recent.size > 1) {
            ChoiceChips(
                choices = recent.map { Choice(it.date, shortDay(it.date, lang)) },
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
        Body(
            stringResource(R.string.usage_day_total, SloTime.formatLocalDate(day.date, lang), fmt(day.totalKwh, 1, locale)) +
                if (incomplete) stringResource(R.string.usage_day_incomplete) else "",
            small = true
        )
        Spacer(Modifier.height(6.dp))
        CombinedDayChart(day, selected = { selected }, onSelect = { selected = it })
        Spacer(Modifier.height(6.dp))
        LegendRow(
            (1..5).map { blockColor(it) to stringResource(R.string.common_block, it) } + listOf(
                colors.onSurface to stringResource(R.string.usage_legend_agreed),
                AppColors.Near to stringResource(R.string.usage_legend_90),
                AppColors.Over to stringResource(R.string.usage_legend_over)
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
    val locale = currentLocale
    val color = p.blockId?.let(::blockColor) ?: UnknownColor
    val shape = RoundedCornerShape(14.dp)
    val powerPrice = p.blockId?.let { b -> PriceLists.current()?.rows?.firstOrNull { it.block == b }?.powerEurPerKw }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color, shape)
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
                    (if (index == day.peakIndex) stringResource(R.string.usage_peak_of_day) else ""),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Body(UsageInsights.summary(p, locale).text(), modifier = Modifier.padding(top = 4.dp))
            val agreed = p.agreedKw
            Body(
                UsageInsights.powerText(p, locale).text(),
                color = when {
                    agreed == null -> colors.onSurface
                    p.kw > agreed -> AppColors.Over
                    p.kw >= UsageAnalysis.NEAR_RATIO * agreed -> AppColors.Warning
                    else -> colors.onSurface
                }
            )
        }
        val agreedKw = p.agreedKw
        if (agreedKw != null && agreedKw > 0.0) {
            Spacer(Modifier.height(6.dp))
            AgreedPowerGauge(agreedKw, p.kw, color)
        }
        Body(UsageInsights.blockText(p).text(), small = true, modifier = Modifier.padding(top = 4.dp))
        if (powerPrice != null) {
            Body(stringResource(R.string.usage_power_price, PriceLists.formatNumber(powerPrice, locale)), small = true)
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StepButton(stringResource(R.string.usage_prev), Modifier.weight(1f), enabled = index > 0) {
                onStep(index - 1)
            }
            day.peakIndex?.let { peak ->
                StepButtonText(
                    stringResource(R.string.usage_peak),
                    stringResource(R.string.usage_peak_spoken),
                    Modifier.weight(1f),
                    enabled = index != peak
                ) { onStep(peak) }
            }
            StepButton(stringResource(R.string.usage_next), Modifier.weight(1f), enabled = index < points.lastIndex, mirrored = true) {
                onStep(index + 1)
            }
        }
    }
}

@Composable
private fun StepButton(
    description: String,
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
            painter = painterResource(R.drawable.ic_arrow_back),
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
    val lang = LocalLang.current
    val locale = currentLocale
    InfoCard {
        val peaks = data.dailyPeaks
        if (peaks.isEmpty()) {
            Body(stringResource(R.string.usage_peaks_none))
            return@InfoCard
        }
        DailyPeaksChart(peaks, selectedDate, onDayClick)
        Spacer(Modifier.height(8.dp))
        LegendRow(
            listOf(
                AppColors.Over to stringResource(R.string.usage_legend_over_100),
                AppColors.Near to stringResource(R.string.usage_legend_over_90),
                MaterialTheme.colorScheme.onSurface to stringResource(R.string.usage_legend_agreed)
            )
        )
        Body(stringResource(R.string.usage_peaks_explain), small = true)
        val worst = peaks.filter { it.utilization != null }.maxByOrNull { it.utilization!! }
        if (worst != null) {
            Spacer(Modifier.height(6.dp))
            Body(
                stringResource(
                    R.string.usage_peaks_closest,
                    shortDay(worst.date, lang),
                    SloTime.formatHm(worst.peak.startMillis),
                    fmt(worst.peak.kw, 2, locale),
                    worst.peak.blockId?.toString() ?: "?",
                    UsageInsights.peakVerdict(worst, locale).text()
                ),
                color = if (worst.overCount > 0) AppColors.Over else MaterialTheme.colorScheme.onSurface
            )
        }
        val overDays = peaks.count { it.overCount > 0 }
        if (overDays > 0) {
            Body(stringResource(R.string.usage_peaks_over_days, overDays, peaks.size), color = AppColors.Over)
        }
    }
}

@Composable
private fun BlocksCard(data: UsageData, onOpenDay: (LocalDate) -> Unit) {
    val locale = currentLocale
    val lang = LocalLang.current
    InfoCard {
        val days = data.daily
        if (days.isEmpty()) {
            Body(stringResource(R.string.usage_no_period_data))
            return@InfoCard
        }
        var picked by rememberSaveable(data.period) { mutableStateOf<String?>(null) }
        val pickedDay = days.firstOrNull { it.date.toString() == picked }
        DailyBlocksChart(days, selected = pickedDay?.date, onSelect = { picked = it.toString() })
        if (pickedDay == null) {
            Body(stringResource(R.string.usage_blocks_tap), small = true, modifier = Modifier.padding(top = 4.dp))
        } else {
            // The numbers of the touched day, below the bars (never on top of them).
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    .padding(10.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    stringResource(R.string.usage_day_total, shortDay(pickedDay.date, lang), fmt(pickedDay.totalKwh, 1, locale)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val blockText = (1..5).map { stringResource(R.string.usage_block_kwh, it, fmt(pickedDay.kwhByBlock[it - 1], 1, locale)) }
                val parts = (1..5).filter { pickedDay.kwhByBlock[it - 1] > 0.0005 }.joinToString(" · ") { blockText[it - 1] }
                if (parts.isNotEmpty()) Body(parts, small = true)
                LinkText(stringResource(R.string.usage_open_day)) { onOpenDay(pickedDay.date) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LegendRow((1..5).map { blockColor(it) to "$it" } + (UnknownColor to stringResource(R.string.common_unknown_block)))
        Spacer(Modifier.height(8.dp))
        val total = days.sumOf { it.totalKwh }
        val perBlock = (0 until 5).map { b -> days.sumOf { it.kwhByBlock[b] } }
        Body(pluralStringResource(R.plurals.usage_blocks_total, days.size, fmt(total, 1, locale), days.size), small = true)
        if (total > 0) {
            Spacer(Modifier.height(6.dp))
            perBlock.forEachIndexed { i, v -> ShareBar(i + 1, v, v / total) }
        }
    }
}

/** One block's share of the energy as a thin bar with the numbers. */
@Composable
private fun ShareBar(block: Int, kwh: Double, share: Double) {
    val locale = currentLocale
    val spoken = stringResource(R.string.usage_share_spoken, block, fmt(kwh, 1, locale), fmt(share * 100, 0, locale))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = spoken }
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
            "${fmt(kwh, 1, locale)} kWh · ${fmt(share * 100, 0, locale)} %",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Per block: agreed power, highest interval in the range and its share, excesses and the power price (SODO). */
@Composable
private fun BlockTableCard(rows: List<BlockPowerRow>, currentBlock: Int?, periodName: String) {
    val colors = MaterialTheme.colorScheme
    val prices = PriceLists.current()
    val lang = LocalLang.current
    val locale = currentLocale
    InfoCard {
        Text(stringResource(R.string.usage_table_title, periodName), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
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
                        .border(if (r.blockId == currentBlock) 2.dp else 0.dp, colors.onSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${r.blockId}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OnBlock)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            R.string.usage_table_agreed,
                            r.agreedKw?.let { "${fmt(it, 2, locale)} kW" } ?: stringResource(R.string.usage_no_data)
                        ) + if (r.blockId == currentBlock) stringResource(R.string.usage_table_now) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                    val peakText = r.peak?.let { p ->
                        stringResource(
                            R.string.usage_table_peak,
                            fmt(p.kw, 2, locale),
                            shortDay(SloTime.dateOf(p.startMillis), lang),
                            SloTime.formatHm(p.startMillis)
                        ) + (util?.let { " · ${UsageInsights.percent(it, locale)}" } ?: "")
                    } ?: stringResource(if (r.intervalCount == 0) R.string.usage_table_no_quarters else R.string.usage_table_no_use)
                    Body(peakText, small = true)
                    if (r.overCount > 0) {
                        Body(pluralStringResource(R.plurals.usage_block_over, r.overCount, r.overCount), small = true, color = AppColors.Over)
                    }
                    if (price != null) {
                        Body(stringResource(R.string.usage_table_rate, PriceLists.formatNumber(price, locale)), small = true)
                    }
                }
            }
        }
        Body(stringResource(R.string.usage_table_footer), small = true)
    }
}

/** Totals of the registers and the quality of the readings. */
@Composable
private fun RegistersCard(data: UsageData) {
    val r = data.registers
    val locale = currentLocale
    InfoCard {
        Text(stringResource(R.string.usage_registers_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        DetailRow(stringResource(R.string.usage_reg_import), "${fmt(r.importKwh, 1, locale)} kWh")
        r.exportKwh?.let { DetailRow(stringResource(R.string.usage_reg_export), "${fmt(it, 1, locale)} kWh") }
        DetailRow(stringResource(R.string.usage_reg_count), "${r.intervalCount}")
        if (r.missingCount > 0) DetailRow(stringResource(R.string.usage_reg_missing), "${r.missingCount}")
        DetailRow(stringResource(R.string.usage_reg_flagged), "${r.flaggedCount}")
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
            stringResource(R.string.usage_reg_label),
            stringResource(
                when (data.label) {
                    IntervalLabel.START -> R.string.usage_reg_label_start
                    IntervalLabel.END -> R.string.usage_reg_label_end
                    IntervalLabel.UNKNOWN -> R.string.usage_reg_label_unknown
                }
            )
        )
        if (data.agreedFetchedAtMillis > 0L) {
            DetailRow(stringResource(R.string.usage_reg_agreed_read), SloTime.formatDateTime(data.agreedFetchedAtMillis, LocalLang.current))
        }
        Spacer(Modifier.height(4.dp))
        Body(stringResource(R.string.usage_reg_explain), small = true)
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
        HelpItem(R.string.usage_help_agreed_q, R.string.usage_help_agreed_a)
        HelpItem(R.string.usage_help_power_q, R.string.usage_help_power_a)
        HelpItem(R.string.usage_help_blocks_q, R.string.usage_help_blocks_a)
        HelpItem(R.string.usage_help_delay_q, R.string.usage_help_delay_a)
    }
}

@Composable
private fun ColumnScope.HelpItem(titleRes: Int, textRes: Int) {
    val title = stringResource(titleRes)
    var open by rememberSaveable(titleRes) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, tween(150), label = "help")
    val spoken = stringResource(if (open) R.string.common_expanded else R.string.common_collapsed, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { open = !open }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = spoken
            }
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }
    AnimatedVisibility(visible = open) {
        Body(stringResource(textRes), small = true, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun periodLabel(p: UsagePeriod): String = when (p) {
    UsagePeriod.LAST_7 -> pluralStringResource(R.plurals.usage_range_days, 7, 7)
    UsagePeriod.LAST_30 -> pluralStringResource(R.plurals.usage_range_days, 30, 30)
    UsagePeriod.THIS_MONTH -> stringResource(R.string.usage_period_month)
    UsagePeriod.LAST_MONTH -> stringResource(R.string.usage_period_prev_month)
}

/** A legend entry that switches its series on and off. */
@Composable
private fun SeriesToggle(color: Color, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.Checkbox) { onChange(!on) }
            .semantics { selected = on }
            .padding(horizontal = 12.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (on) color else Color.Transparent)
                .border(1.dp, color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (on) 1f else 0.6f))
    }
}

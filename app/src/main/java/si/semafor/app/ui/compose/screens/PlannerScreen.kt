package si.semafor.app.ui.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.BlockPalette
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.TariffCalculator
import si.semafor.app.planner.AgreedSource
import si.semafor.app.planner.DeviceType
import si.semafor.app.planner.Habit
import si.semafor.app.planner.HouseholdProfile
import si.semafor.app.planner.LoadSettings
import si.semafor.app.planner.PlanGoal
import si.semafor.app.planner.PlanSummary
import si.semafor.app.planner.PlanReason
import si.semafor.app.planner.PlanResult
import si.semafor.app.planner.PlanStatus
import si.semafor.app.planner.PlannerDevice
import si.semafor.app.planner.PlannerUiState
import si.semafor.app.pricing.PriceGap
import si.semafor.app.pricing.PriceGapReason
import si.semafor.app.pricing.PriceNow
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.Choice
import si.semafor.app.ui.compose.components.ChoiceChips
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.NumberRow
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.components.SwitchRow
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.BodyLineHeight
import java.time.DayOfWeek

/** Everything the planner screen can ask the host to do. */
data class PlannerActions(
    val onBack: () -> Unit,
    val onAdd: (DeviceType, String) -> Unit,
    val onUpdate: (PlannerDevice) -> Unit,
    val onRemove: (String) -> Unit,
    val onLoad: (LoadSettings) -> Unit,
    val onOpenPrice: () -> Unit,
    val onProfile: (HouseholdProfile) -> Unit = {},
    val onConfirmStart: (String) -> Unit = {},
    val onApplyHabit: (Habit) -> Unit = {},
    val onClearLog: () -> Unit = {},
    val onDay: (Int) -> Unit = {}
)

@Composable
fun deviceTypeName(t: DeviceType): String = stringResource(
    when (t) {
        DeviceType.WASHER -> R.string.pl_dev_washer
        DeviceType.DISHWASHER -> R.string.pl_dev_dishwasher
        DeviceType.DRYER -> R.string.pl_dev_dryer
        DeviceType.BOILER -> R.string.pl_dev_boiler
        DeviceType.HEAT_PUMP -> R.string.pl_dev_heat_pump
        DeviceType.EV -> R.string.pl_dev_ev
        DeviceType.HEATING -> R.string.pl_dev_heating
        DeviceType.AIR_CON -> R.string.pl_dev_air_con
        DeviceType.CUSTOM -> R.string.pl_dev_custom
    }
)

fun deviceIcon(t: DeviceType): Int = when (t) {
    DeviceType.EV -> R.drawable.ic_device_ev
    DeviceType.BOILER, DeviceType.HEATING, DeviceType.HEAT_PUMP, DeviceType.AIR_CON -> R.drawable.ic_device_heating
    DeviceType.WASHER, DeviceType.DISHWASHER, DeviceType.DRYER -> R.drawable.ic_device_household
    DeviceType.CUSTOM -> R.drawable.ic_bolt
}

/** "22:00–00:30", "jutri 01:00–03:30" or "sob 21. 11. 10:00–12:00" (any later day). */
@Composable
fun runRange(start: Long, end: Long, nowMillis: Long): String {
    val a = SloTime.formatHm(start)
    val b = SloTime.formatHm(end)
    val day = SloTime.dateOf(start)
    val today = SloTime.dateOf(nowMillis)
    return when {
        day == today -> stringResource(R.string.pl_range, a, b)
        day == today.plusDays(1) -> stringResource(R.string.pl_range_tomorrow, a, b)
        else -> stringResource(R.string.pl_range_day, dayLabel(day, LocalLang.current), a, b)
    }
}

/** "sob 21. 11." / "Sat 21 Nov". */
fun dayLabel(date: java.time.LocalDate, lang: String): String {
    val locale = si.semafor.app.AppLanguage.localeOf(lang)
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).trimEnd('.')
    val pattern = if (lang == si.semafor.app.AppSettings.LANGUAGE_EN) "d MMM" else "d. M."
    return weekday + " " + date.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}

/**
 * "Pametni dnevni načrt": the user adds appliances, the app suggests the cheapest start in each one's window with the
 * user's own final prices, checks the planned load against the agreed power and shows everything on a timeline.
 */
@Composable
fun PlannerScreen(
    state: PlannerUiState,
    configured: Boolean,
    pricesLoading: Boolean,
    pricesFailed: Boolean,
    gap: PriceGap?,
    nowMillis: Long,
    actions: PlannerActions
) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val lang = LocalLang.current

    ScreenScaffold {
        ScreenHeader(stringResource(R.string.pl_title), actions.onBack, stringResource(R.string.pl_subtitle))

        // ---- Which day: today and up to 6 days ahead ----
        if (configured) {
            val today = SloTime.today()
            ChoiceChips(
                choices = (0..si.semafor.app.planner.PlannerViewModel.MAX_DAY_OFFSET).map { o ->
                    Choice(
                        o,
                        when (o) {
                            0 -> stringResource(R.string.pl_day_today)
                            1 -> stringResource(R.string.pl_day_tomorrow)
                            else -> dayLabel(today.plusDays(o.toLong()), lang)
                        }
                    )
                },
                selected = state.dayOffset,
                onSelect = actions.onDay,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        val later = state.dayOffset > 0
        val shownPrices = state.shownPrices
        val shownResults = state.shownResults

        if (!configured) {
            InfoCard {
                Text(stringResource(R.string.pl_need_price), style = body, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                ActionButton(stringResource(R.string.pl_set_price), onClick = actions.onOpenPrice)
            }
        } else if (shownPrices.isEmpty()) {
            InfoCard {
                Text(
                    stringResource(
                        when {
                            pricesLoading -> R.string.pl_loading_prices
                            later && state.prices.isNotEmpty() -> R.string.pl_day_no_prices
                            else -> R.string.pl_no_schedule
                        }
                    ),
                    style = body,
                    color = colors.onSurfaceVariant
                )
            }
        }
        gap?.takeIf { !later }?.let {
            val day = SloTime.formatLocalDate(it.date, lang)
            Text(
                stringResource(
                    when (it.reason) {
                        PriceGapReason.NO_SCHEDULE -> R.string.pl_gap_schedule
                        PriceGapReason.NO_NETWORK_LIST -> R.string.pl_gap_network
                        PriceGapReason.NO_ENERGY_PRICE -> R.string.pl_gap_energy
                    },
                    day
                ),
                style = small,
                color = AppColors.Warning,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // ---- Add an appliance ----
        SectionHeading(stringResource(R.string.pl_add))
        val addNames = DeviceType.entries.associateWith { deviceTypeName(it) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            for (t in DeviceType.entries) {
                val shape = RoundedCornerShape(20.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, colors.outline, shape)
                        .clickable(role = Role.Button) { actions.onAdd(t, addNames.getValue(t)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(painterResource(deviceIcon(t)), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ " + addNames.getValue(t), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
            }
        }

        if (state.devices.isEmpty()) {
            InfoCard { Text(stringResource(R.string.pl_empty), style = body, color = colors.onSurfaceVariant) }
        } else {
            // ---- The answer first: what to do today ----
            state.summary?.let { SummaryCard(it, state.profile.goal, nowMillis, later) }

            // ---- Timeline ----
            val runs = shownResults.filter { it.status == PlanStatus.OK }
            if (shownPrices.isNotEmpty()) {
                SectionHeading(stringResource(R.string.pl_timeline_title))
                InfoCard { PlanTimeline(shownPrices, runs, nowMillis) }
            }

            // ---- Recognised habits (only those that would change something) ----
            val suggestions = state.habits.mapNotNull { h -> state.devices.firstOrNull { it.id == h.deviceId }?.let { d -> h to d } }
                .filter { (h, d) ->
                    when (h) {
                        is Habit.TypicalStart -> d.earliestStartMin != h.fromMin
                        is Habit.WeekendOnly -> d.days != setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                    }
                }
            if (suggestions.isNotEmpty()) {
                si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
                    stringResource(R.string.pl_habits_title),
                    stringResource(R.string.pl_habits_title),
                    listOf(stringResource(R.string.pl_habits_note))
                )
                InfoCard { for ((h, d) in suggestions) HabitRow(h, d, actions.onApplyHabit) }
            }

            // ---- Results, one card per appliance ----
            si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
                stringResource(R.string.pl_results_title),
                stringResource(R.string.pl_results_title),
                listOf(stringResource(R.string.pl_note))
            )
            // Priority appliances first, then in the order they were added.
            for (device in state.devices.sortedByDescending { it.favorite }) {
                val result = shownResults.firstOrNull { it.device.id == device.id }
                // "I started it" only makes sense for today's plan.
                DeviceCard(device, result, nowMillis, state.profile.learn && !later, later, actions)
            }
        }

        // ---- Household goal and habits (optional, all of it) ----
        si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
            stringResource(R.string.pl_profile_title),
            stringResource(R.string.pl_profile_title),
            listOf(stringResource(R.string.pl_profile_note), stringResource(R.string.pl_learn_note))
        )
        InfoCard { ProfileSection(state.profile, state.log.size, actions) }

        // ---- Load settings ----
        si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
            stringResource(R.string.pl_load_title),
            stringResource(R.string.pl_load_title),
            listOf(stringResource(R.string.pl_load_note))
        )
        InfoCard { LoadSection(state, actions.onLoad) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeviceCard(device: PlannerDevice, result: PlanResult?, nowMillis: Long, learn: Boolean, later: Boolean, actions: PlannerActions) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    var editing by rememberSaveable(device.id) { mutableStateOf(false) }
    val name = device.name.ifBlank { deviceTypeName(device.type) }

    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(deviceIcon(device.type)), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    String.format(locale, "%.1f kW · %.1f kWh · %d h %02d min", device.powerKw, device.runKwh, device.durationMin / 60, device.durationMin % 60),
                    style = small,
                    color = colors.onSurfaceVariant
                )
                if (device.note.isNotBlank()) Text(device.note, style = small, color = colors.onSurfaceVariant)
            }
            FavoriteToggle(device.favorite, name) { actions.onUpdate(device.copy(favorite = it)) }
        }
        SwitchRow(stringResource(R.string.pl_active), device.active, { actions.onUpdate(device.copy(active = it)) }, fontSize = 14.sp)

        if (!device.active) {
            Text(stringResource(R.string.pl_not_active), style = body, color = colors.onSurfaceVariant)
        } else if (result != null) {
            ResultBody(result, nowMillis, later)
            if (learn && result.status == PlanStatus.OK) ConfirmStart(device.id, actions.onConfirmStart)
        }

        Spacer(Modifier.height(6.dp))
        if (editing) {
            DeviceEditor(device, onDone = { updated ->
                actions.onUpdate(updated)
                editing = false
            }, onRemove = { actions.onRemove(device.id) })
        } else {
            LinkText(stringResource(R.string.pl_edit)) { editing = true }
        }
    }
}

@Composable
private fun ResultBody(r: PlanResult, nowMillis: Long, later: Boolean) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    when (r.status) {
        PlanStatus.NO_WINDOW -> Text(stringResource(R.string.pl_no_window), style = body, color = AppColors.Warning)
        PlanStatus.NO_PRICES -> Text(stringResource(R.string.pl_no_prices), style = body, color = colors.onSurfaceVariant)
        PlanStatus.OK -> {
            Text(stringResource(R.string.pl_recommended), style = small, color = colors.onSurfaceVariant)
            Text(runRange(r.startMillis, r.endMillis, nowMillis), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(stringResource(R.string.pl_cost, PriceNow.formatEur(r.cost, locale)), style = body, color = colors.onSurface)
            Text(
                stringResource(
                    when (r.reason) {
                        PlanReason.CHEAPEST -> R.string.pl_reason_cheapest
                        PlanReason.NOW_IS_CHEAPEST -> R.string.pl_reason_now
                        PlanReason.CHEAPEST_WITHIN_POWER -> R.string.pl_reason_power
                        PlanReason.CHEAPEST_OVER_POWER -> R.string.pl_reason_over
                        PlanReason.LOWER_PEAK -> R.string.pl_reason_lower_peak
                        PlanReason.SOONER -> R.string.pl_reason_sooner
                    }
                ),
                style = small,
                color = if (r.reason == PlanReason.CHEAPEST_OVER_POWER) AppColors.Warning else colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            val now = r.costNow
            // A later day has no "now" to compare with.
            if (!later) Text(
                when {
                    now == null -> stringResource(R.string.pl_now_unknown)
                    now - r.cost >= 0.005 -> stringResource(R.string.pl_now_more, PriceNow.formatEur(now, locale), PriceNow.formatEur(now - r.cost, locale))
                    else -> stringResource(R.string.pl_now_same, PriceNow.formatEur(now, locale))
                },
                style = body,
                color = colors.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
            val dur = r.endMillis - r.startMillis
            if (r.earlierStartMillis != null && r.earlierCost != null) {
                Text(
                    stringResource(R.string.pl_earlier, runRange(r.earlierStartMillis, r.earlierStartMillis + dur, nowMillis), PriceNow.formatEur(r.earlierCost, locale)),
                    style = body,
                    color = colors.onSurface
                )
            }
            if (r.altStartMillis != null && r.altCost != null && r.altStartMillis != r.earlierStartMillis) {
                Text(
                    stringResource(R.string.pl_alt, runRange(r.altStartMillis, r.altStartMillis + dur, nowMillis), PriceNow.formatEur(r.altCost, locale)),
                    style = body,
                    color = colors.onSurface
                )
            }
            if (r.blocks.isNotEmpty()) {
                Text(stringResource(R.string.pl_blocks, r.blocks.joinToString(", ")), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (r.overlapsWith.isNotEmpty()) {
                Text(stringResource(R.string.pl_overlap, r.overlapsWith.joinToString(", ")), style = small, color = AppColors.Warning)
            }
            val peak = String.format(locale, "%.1f", r.peakKw)
            Text(
                if (r.peakAgreedKw != null) stringResource(R.string.pl_peak, peak, String.format(locale, "%.1f", r.peakAgreedKw))
                else stringResource(R.string.pl_peak_no_agreed, peak),
                style = small,
                color = if (r.overPower) AppColors.Warning else colors.onSurfaceVariant
            )
            Text(
                stringResource(
                    when {
                        !r.device.userValues -> R.string.pl_basis_preset
                        r.device.energyEstimated -> R.string.pl_basis_estimate
                        else -> R.string.pl_basis_known
                    }
                ),
                style = small,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Typed numbers -> appliance; minutes of day from hour and minute fields; days toggled by chips. */
@Composable
private fun DeviceEditor(device: PlannerDevice, onDone: (PlannerDevice) -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    fun num(v: Double?) = v?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: ""
    var name by rememberSaveable(device.id) { mutableStateOf(device.name) }
    var kw by rememberSaveable(device.id) { mutableStateOf(num(device.powerKw)) }
    var kwh by rememberSaveable(device.id) { mutableStateOf(num(device.energyKwh)) }
    var hours by rememberSaveable(device.id) { mutableStateOf((device.durationMin / 60).toString()) }
    var minutes by rememberSaveable(device.id) { mutableStateOf((device.durationMin % 60).toString()) }
    var advanced by rememberSaveable(device.id) { mutableStateOf(device.earliestStartMin != null || device.deadlineMin != null || device.days.isNotEmpty()) }
    var useEarliest by rememberSaveable(device.id) { mutableStateOf(device.earliestStartMin != null) }
    var eh by rememberSaveable(device.id) { mutableStateOf(((device.earliestStartMin ?: 1320) / 60).toString()) }
    var em by rememberSaveable(device.id) { mutableStateOf(((device.earliestStartMin ?: 1320) % 60).toString()) }
    var useDeadline by rememberSaveable(device.id) { mutableStateOf(device.deadlineMin != null) }
    var dh by rememberSaveable(device.id) { mutableStateOf(((device.deadlineMin ?: 420) / 60).toString()) }
    var dm by rememberSaveable(device.id) { mutableStateOf(((device.deadlineMin ?: 420) % 60).toString()) }
    var days by rememberSaveable(device.id) { mutableStateOf(device.days.map { it.value }.sorted()) }
    var invalid by rememberSaveable(device.id) { mutableStateOf(false) }
    val typeLabel = deviceTypeName(device.type)

    Text(stringResource(R.string.pl_name), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    PlainTextField(name, { name = it.take(40) }, stringResource(R.string.pl_name), deviceTypeName(device.type))
    var note by rememberSaveable(device.id) { mutableStateOf(device.note) }
    NumberRow(stringResource(R.string.pl_power), stringResource(R.string.pl_power), kw) { kw = it }
    NumberRow(stringResource(R.string.pl_energy), stringResource(R.string.pl_energy), kwh) { kwh = it }
    NumberRow(stringResource(R.string.pl_duration_h), stringResource(R.string.pl_duration_h), hours) { hours = it }
    NumberRow(stringResource(R.string.pl_duration_m), stringResource(R.string.pl_duration_m), minutes) { minutes = it }

    SwitchRow(stringResource(R.string.pl_advanced), advanced, { advanced = it }, fontSize = 14.sp)
    AnimatedVisibility(advanced) {
        Column {
            SwitchRow(stringResource(R.string.pl_earliest), useEarliest, { useEarliest = it }, fontSize = 14.sp)
            if (useEarliest) {
                NumberRow(stringResource(R.string.pl_hour), stringResource(R.string.pl_earliest) + " " + stringResource(R.string.pl_hour), eh) { eh = it }
                NumberRow(stringResource(R.string.pl_minute), stringResource(R.string.pl_earliest) + " " + stringResource(R.string.pl_minute), em) { em = it }
            }
            SwitchRow(stringResource(R.string.pl_deadline), useDeadline, { useDeadline = it }, fontSize = 14.sp)
            if (useDeadline) {
                NumberRow(stringResource(R.string.pl_hour), stringResource(R.string.pl_deadline) + " " + stringResource(R.string.pl_hour), dh) { dh = it }
                NumberRow(stringResource(R.string.pl_minute), stringResource(R.string.pl_deadline) + " " + stringResource(R.string.pl_minute), dm) { dm = it }
            }
            Text(stringResource(R.string.pl_note_label), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.padding(top = 6.dp))
            PlainTextField(note, { note = it.take(si.semafor.app.planner.PlannerStore.NOTE_MAX) }, stringResource(R.string.pl_note_label), stringResource(R.string.pl_note_hint))
            Text(stringResource(R.string.pl_days), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (d in DayOfWeek.entries) {
                    val on = d.value in days
                    val shape = RoundedCornerShape(16.dp)
                    Text(
                        d.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) colors.onPrimary else colors.onSurface,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(shape)
                            .background(if (on) colors.primary else colors.surface)
                            .border(1.dp, colors.outline, shape)
                            .clickable(role = Role.Checkbox) { days = if (on) days - d.value else (days + d.value).sorted() }
                            .semantics { selected = on }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
    if (invalid) Text(stringResource(R.string.pl_invalid), style = MaterialTheme.typography.bodyMedium, color = colors.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        ActionButton(stringResource(R.string.pl_done)) {
            fun minuteOf(h: String, m: String): Int? {
                val hh = h.trim().toIntOrNull() ?: return null
                val mm = m.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
                return if (hh in 0..23 && mm in 0..59) hh * 60 + mm else null
            }
            val power = TariffCalculator.parseAmount(kw) ?: 0.0
            val energy = kwh.takeIf { it.isNotBlank() }?.let { TariffCalculator.parseAmount(it) }?.takeIf { it > 0 }
            val dur = (hours.trim().ifEmpty { "0" }.toIntOrNull() ?: -1) * 60 + (minutes.trim().ifEmpty { "0" }.toIntOrNull() ?: -1)
            if (power <= 0 || power > 100 || dur <= 0 || dur > 48 * 60) {
                invalid = true
                return@ActionButton
            }
            invalid = false
            val changed = power != device.powerKw || energy != device.energyKwh || dur != device.durationMin
            onDone(
                device.copy(
                    name = name.trim().ifBlank { typeLabel },
                    powerKw = power,
                    energyKwh = energy,
                    durationMin = dur,
                    earliestStartMin = if (advanced && useEarliest) minuteOf(eh, em) else null,
                    deadlineMin = if (advanced && useDeadline) minuteOf(dh, dm) else null,
                    days = if (advanced) days.map { DayOfWeek.of(it) }.toSet() else emptySet(),
                    note = if (advanced) note.trim() else device.note,
                    userValues = device.userValues || changed
                )
            )
        }
        ActionButton(stringResource(R.string.pl_remove), onClick = onRemove)
    }
}

@Composable
private fun PlainTextField(value: String, onChange: (String) -> Unit, description: String, placeholder: String) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val style = TextStyle(fontSize = 15.sp, color = colors.onSurface)
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, colors.outline, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.onSurfaceVariant))
                inner()
            }
        }
    )
}

@Composable
private fun LoadSection(state: PlannerUiState, onLoad: (LoadSettings) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    if (state.agreedSource == AgreedSource.MOJ_ELEKTRO) {
        val values = state.power?.agreedByBlock?.mapIndexed { i, v -> "${i + 1}: " + (v?.let { String.format(locale, "%.1f", it) } ?: "–") }?.joinToString(" · ").orEmpty()
        Text(stringResource(R.string.pl_load_moj, values), style = body, color = colors.onSurface)
    } else {
        var agreed by rememberSaveable { mutableStateOf(state.load.manualAgreedKw?.toString() ?: "") }
        var base by rememberSaveable { mutableStateOf(state.load.manualBaseKw?.toString() ?: "") }
        fun push() = onLoad(
            LoadSettings(
                agreed.takeIf { it.isNotBlank() }?.let { TariffCalculator.parseAmount(it) }?.takeIf { it > 0 },
                base.takeIf { it.isNotBlank() }?.let { TariffCalculator.parseAmount(it) }
            )
        )
        NumberRow(stringResource(R.string.pl_load_manual_agreed), stringResource(R.string.pl_load_manual_agreed), agreed) { agreed = it; push() }
        NumberRow(stringResource(R.string.pl_load_manual_base), stringResource(R.string.pl_load_manual_base), base) { base = it; push() }
        if (state.agreedSource == AgreedSource.NONE) {
            Text(stringResource(R.string.pl_load_none), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * The day at a glance: the block colour of every hour, the final price as bars (lower = cheaper) and one lane per
 * planned appliance (overlaps in the warning colour). A line marks "now".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanTimeline(prices: List<si.semafor.app.pricing.KwhPrice>, runs: List<PlanResult>, nowMillis: Long) {
    if (prices.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val laneStyle = TextStyle(fontSize = 11.sp, color = Color(BlockPalette.ON_BLOCK))
    val warn = AppColors.Warning
    val runColor = colors.primary
    val outline = colors.onSurface
    // Show at least 24 hours, more if a run ends later.
    val endWanted = maxOf(prices.first().startMillis + 24 * 3_600_000L, runs.maxOfOrNull { it.endMillis } ?: 0L)
    val shown = prices.filter { it.startMillis < endWanted }
    val from = shown.first().startMillis
    val to = shown.last().endMillis
    val maxPrice = shown.maxOf { it.total }
    val minPrice = shown.minOf { it.total }
    val names = runs.joinToString(", ") { it.device.name.ifBlank { it.device.type.key } + " " + SloTime.formatHm(it.startMillis) }
    val spoken = stringResource(R.string.pl_timeline_spoken, SloTime.formatHm(from), SloTime.formatHm(to), names)
    val lanes = runs.size.coerceAtLeast(1)
    val blockColors = shown.map { Color(BlockPalette.color(it.block, SloTime.dateOf(it.startMillis))) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height((96 + lanes * 24).dp)
            .clearAndSetSemantics { contentDescription = spoken }
    ) {
        val span = (to - from).toFloat()
        fun x(t: Long) = ((t - from) / span) * size.width
        val stripH = 10.dp.toPx()
        val barTop = stripH + 4.dp.toPx()
        val barH = 48.dp.toPx()
        val labelY = barTop + barH + 2.dp.toPx()
        val laneTop = labelY + 16.dp.toPx()
        val laneH = 20.dp.toPx()

        shown.forEachIndexed { i, p ->
            val x0 = x(p.startMillis)
            val w = x(p.endMillis) - x0
            drawRect(blockColors[i], Offset(x0, 0f), Size(w, stripH))
            // Bar height: the cheapest hour short, the dearest tall (relative to the range shown).
            val rel = if (maxPrice > minPrice) ((p.total - minPrice) / (maxPrice - minPrice)).toFloat() else 0.5f
            val h = barH * (0.25f + 0.75f * rel)
            drawRect(blockColors[i].copy(alpha = 0.55f), Offset(x0 + 1f, barTop + barH - h), Size(w - 2f, h))
            val hour = SloTime.hourOfDay(p.startMillis)
            if (hour % 3 == 0) {
                val t = measurer.measure(hour.toString(), labelStyle)
                drawText(t, topLeft = Offset((x0 - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), labelY))
            }
        }
        runs.forEachIndexed { lane, r ->
            val x0 = x(r.startMillis).coerceAtLeast(0f)
            val x1 = x(r.endMillis).coerceAtMost(size.width)
            val y = laneTop + lane * (laneH + 4.dp.toPx())
            val c = if (r.overPower || r.overlapsWith.isNotEmpty()) warn else runColor
            drawRoundRect(c, Offset(x0, y), Size((x1 - x0).coerceAtLeast(4f), laneH), CornerRadius(6f))
            val label = measurer.measure(r.device.name.ifBlank { r.device.type.key }, laneStyle)
            if (label.size.width + 8 < x1 - x0) drawText(label, topLeft = Offset(x0 + 4f, y + (laneH - label.size.height) / 2))
        }
        if (nowMillis in from..to) {
            val nx = x(nowMillis)
            drawLine(outline, Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 2.dp.toPx())
        }
        drawRoundRect(outline.copy(alpha = 0.2f), Offset.Zero, Size(size.width, stripH), CornerRadius(2f), style = Stroke(1f))
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
        Text(stringResource(R.string.pl_legend_price), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(runColor))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.pl_legend_run), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * The plan in one sentence and the numbers behind it: where the biggest saving is, the total of all runs, and any
 * power or overlap problem with what to do about it.
 */
@Composable
private fun SummaryCard(s: PlanSummary, goal: PlanGoal, nowMillis: Long, later: Boolean) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    InfoCard {
        val best = s.bestSaving
        val headline = if (later) {
            // A later day: no "now" to compare with; the cheapest run of the day is the headline.
            val main = s.mainRun
            stringResource(R.string.pl_sum_day, main.device.name, runRange(main.startMillis, main.endMillis, nowMillis))
        } else if (best != null) {
            val start = SloTime.formatHm(best.startMillis)
            val tomorrow = SloTime.dateOf(best.startMillis) != SloTime.dateOf(nowMillis)
            stringResource(
                if (tomorrow) R.string.pl_sum_best_tomorrow else R.string.pl_sum_best,
                best.device.name, start, PriceNow.formatEur(best.savingVsNow ?: 0.0, locale)
            )
        } else stringResource(R.string.pl_sum_now_fine)
        Text(headline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, lineHeight = 23.sp)
        Text(stringResource(R.string.pl_sum_total, PriceNow.formatEur(s.totalCost, locale)), style = body, color = colors.onSurface, modifier = Modifier.padding(top = 6.dp))
        s.totalCostNow?.let {
            if (it - s.totalCost >= PlanSummary.MIN_SAVING) {
                Text(stringResource(R.string.pl_sum_total_now, PriceNow.formatEur(it, locale)), style = small, color = colors.onSurfaceVariant)
            }
        }
        for (r in s.overPower) {
            Text(stringResource(R.string.pl_sum_over, r.device.name), style = body, color = AppColors.Warning, modifier = Modifier.padding(top = 6.dp))
        }
        if (s.overPower.isEmpty() && s.overlapping.isNotEmpty()) {
            val names = s.overlapping.map { it.device.name }.distinct().joinToString(", ")
            Text(stringResource(R.string.pl_sum_overlap, names), style = body, color = AppColors.Warning, modifier = Modifier.padding(top = 6.dp))
        }
        val peak = s.peakRun
        val agreed = peak.peakAgreedKw
        Text(
            if (agreed != null) stringResource(R.string.pl_sum_peak, String.format(locale, "%.1f", peak.peakKw), peak.device.name, String.format(locale, "%.1f", agreed))
            else stringResource(R.string.pl_sum_peak_no_agreed, String.format(locale, "%.1f", peak.peakKw), peak.device.name),
            style = small,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(stringResource(R.string.pl_sum_goal, goalName(goal)), style = small, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun goalName(goal: PlanGoal): String = stringResource(
    when (goal) {
        PlanGoal.COST -> R.string.pl_goal_cost
        PlanGoal.FEWER_PEAKS -> R.string.pl_goal_peaks
        PlanGoal.COMFORT -> R.string.pl_goal_comfort
    }
)

/** Star that marks a priority appliance (listed and planned first). */
@Composable
private fun FavoriteToggle(on: Boolean, name: String, onChange: (Boolean) -> Unit) {
    val spoken = stringResource(if (on) R.string.pl_fav_on else R.string.pl_fav_off, name)
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Switch) { onChange(!on) }
            .clearAndSetSemantics {
                contentDescription = spoken
                selected = on
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (on) R.drawable.ic_star else R.drawable.ic_star_outline),
            contentDescription = null,
            tint = if (on) AppColors.Near else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** "Zagnal sem": records the start on this phone, so recurring habits can be recognised. */
@Composable
private fun ConfirmStart(deviceId: String, onConfirm: (String) -> Unit) {
    var done by rememberSaveable(deviceId) { mutableStateOf(false) }
    if (done) {
        Text(
            stringResource(R.string.pl_confirmed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    } else {
        LinkText(stringResource(R.string.pl_confirm)) {
            onConfirm(deviceId)
            done = true
        }
    }
}

/** One recognised habit with the change it suggests. Always worded as a pattern, never as a fact. */
@Composable
private fun HabitRow(h: Habit, device: PlannerDevice, onApply: (Habit) -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    fun hm(min: Int) = String.format(java.util.Locale.ROOT, "%02d:%02d", min / 60, min % 60)
    val name = device.name.ifBlank { deviceTypeName(device.type) }
    Text(
        when (h) {
            is Habit.TypicalStart -> stringResource(R.string.pl_habit_time, name, hm(h.fromMin), hm(h.toMin), h.count)
            is Habit.WeekendOnly -> stringResource(R.string.pl_habit_weekend, name, h.count)
        },
        style = body,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
    LinkText(
        stringResource(
            when (h) {
                is Habit.TypicalStart -> R.string.pl_habit_apply_time
                is Habit.WeekendOnly -> R.string.pl_habit_apply_weekend
            }
        )
    ) { onApply(h) }
}

/**
 * The household's goal and usual times. Everything is optional and set when the user wants to: the planner works
 * without any of it. Times are whole hours (enough for a window and much quicker to set).
 */
@Composable
private fun ProfileSection(profile: HouseholdProfile, logSize: Int, actions: PlannerActions) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    Text(stringResource(R.string.pl_goal_title), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    ChoiceChips(
        choices = PlanGoal.entries.map { Choice(it, goalName(it)) },
        selected = profile.goal,
        onSelect = { actions.onProfile(profile.copy(goal = it)) },
        modifier = Modifier.padding(vertical = 6.dp)
    )
    Text(
        stringResource(
            when (profile.goal) {
                PlanGoal.COST -> R.string.pl_goal_cost_text
                PlanGoal.FEWER_PEAKS -> R.string.pl_goal_peaks_text
                PlanGoal.COMFORT -> R.string.pl_goal_comfort_text
            }
        ),
        style = small,
        color = colors.onSurfaceVariant
    )

    Spacer(Modifier.height(10.dp))
    HourSetting(stringResource(R.string.pl_chores_by), profile.choresDoneByMin, 7 * 60) { actions.onProfile(profile.copy(choresDoneByMin = it)) }
    HourSetting(stringResource(R.string.pl_ev_from), profile.evFromMin, 22 * 60) { actions.onProfile(profile.copy(evFromMin = it)) }
    HourSetting(stringResource(R.string.pl_ev_to), profile.evToMin, 6 * 60) { actions.onProfile(profile.copy(evToMin = it)) }

    Spacer(Modifier.height(6.dp))
    SwitchRow(stringResource(R.string.pl_learn), profile.learn, { actions.onProfile(profile.copy(learn = it)) }, fontSize = 14.sp)
    if (profile.learn && logSize > 0) {
        Text(learnCountText(logSize), style = small, color = colors.onSurfaceVariant)
        LinkText(stringResource(R.string.pl_learn_clear), actions.onClearLog)
    }
}

@Composable
private fun learnCountText(count: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.pl_learn_count, count, count)

/** An optional hour: a switch, and when on, the hour (0-23). */
@Composable
private fun HourSetting(label: String, minute: Int?, defaultMinute: Int, onChange: (Int?) -> Unit) {
    SwitchRow(label, minute != null, { on -> onChange(if (on) defaultMinute else null) }, fontSize = 14.sp)
    if (minute != null) {
        var text by rememberSaveable(label) { mutableStateOf((minute / 60).toString()) }
        NumberRow(stringResource(R.string.pl_hour_of_day), label, text) { t ->
            text = t
            t.trim().toIntOrNull()?.takeIf { it in 0..23 }?.let { onChange(it * 60) }
        }
    }
}

package si.semafor.app.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.PowerSummary
import si.semafor.app.planner.NowAdvice
import si.semafor.app.planner.PlannerStore
import si.semafor.app.pricing.PriceNow
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.BodyLineHeight

/** The common frame of the home cards; the whole card is a button when [onClick] is given. */
@Composable
private fun HomeCard(onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/** "22:00", "jutri 06:00". */
@Composable
private fun whenText(millis: Long, nowMillis: Long): Pair<String, Boolean> =
    SloTime.formatHm(millis) to (SloTime.dateOf(millis) != SloTime.dateOf(nowMillis))

/**
 * "Kaj lahko naredim zdaj?": the first recommendation large, up to two more below. Every recommendation says whether
 * it is calculated from the user's own prices or data, or only a general hint.
 */
@Composable
fun NowAdviceCard(advice: List<NowAdvice>, nowMillis: Long, onSetPrice: () -> Unit, onOpen: (() -> Unit)?) {
    if (advice.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val lang = LocalLang.current
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)

    @Composable
    fun texts(a: NowAdvice): Pair<String, String?> = when (a) {
        is NowAdvice.GoodNow -> stringResource(R.string.adv_good_now) to
            stringResource(R.string.adv_good_now_detail, PriceNow.formatKwh(a.priceNow, locale), a.hours)
        is NowAdvice.WaitForCheaper -> {
            val (t, tomorrow) = whenText(a.atMillis, nowMillis)
            stringResource(if (tomorrow) R.string.adv_wait_tomorrow else R.string.adv_wait, t) to
                stringResource(R.string.adv_wait_detail, a.block, PriceNow.formatKwh(a.priceThen, locale), PriceNow.formatKwh(a.priceNow, locale))
        }
        is NowAdvice.DeviceSoon ->
            (if (a.minutes <= 0) stringResource(R.string.adv_device_now, a.name) else stringResource(R.string.adv_device_soon, a.minutes.toInt(), a.name)) to null
        is NowAdvice.DeviceNext -> {
            val (t, tomorrow) = whenText(a.startMillis, nowMillis)
            stringResource(if (tomorrow) R.string.adv_device_next_tomorrow else R.string.adv_device_next, a.name, t) to null
        }
        NowAdvice.FlatEnergy -> stringResource(R.string.adv_flat) to null
        is NowAdvice.PowerHigh -> {
            val day = SloTime.formatLocalDate(a.summary.lastDay, lang)
            (if (a.share > 1.0) stringResource(R.string.adv_power_over, day, a.summary.peakBlock ?: 0)
            else stringResource(R.string.adv_power_near, day, String.format(locale, "%.0f", a.share * 100))) to null
        }
        is NowAdvice.General -> {
            val main = if (a.bestBlock != null && a.bestAtMillis != null) {
                stringResource(R.string.adv_general_best, a.bestBlock, SloTime.formatHm(a.bestAtMillis))
            } else if (a.currentBlock != null) stringResource(R.string.adv_general_now, a.currentBlock) else stringResource(R.string.adv_general_hint)
            main to stringResource(R.string.adv_general_hint)
        }
    }

    @Composable
    fun tag(a: NowAdvice): String = stringResource(
        when {
            a is NowAdvice.PowerHigh -> R.string.adv_tag_history
            a.personal -> R.string.adv_tag_personal
            else -> R.string.adv_tag_general
        }
    )

    HomeCard(onOpen) {
        CardTitle(stringResource(R.string.adv_title))
        val first = advice.first()
        val (main, detail) = texts(first)
        Text(main, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, lineHeight = 23.sp)
        detail?.let { Text(it, style = body, color = colors.onSurface, modifier = Modifier.padding(top = 2.dp)) }
        Tag(tag(first))
        for (a in advice.drop(1).take(2)) {
            val (m, d) = texts(a)
            Text(m, style = body, color = if (a is NowAdvice.PowerHigh) AppColors.Warning else colors.onSurface, modifier = Modifier.padding(top = 10.dp))
            d?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            Tag(tag(a))
        }
        if (first is NowAdvice.General) LinkText(stringResource(R.string.ep_setup_button), onSetPrice)
    }
}

/** Shortcut to the planner with the next planned appliance. */
@Composable
fun PlannerHomeCard(next: PlannerStore.PlannedRun?, nowMillis: Long, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    HomeCard(onOpen) {
        CardTitle(stringResource(R.string.home_plan_title))
        if (next != null) {
            val start = SloTime.formatHm(next.startMillis)
            val range = if (SloTime.dateOf(next.startMillis) != SloTime.dateOf(nowMillis)) {
                stringResource(R.string.pl_range_tomorrow, start, SloTime.formatHm(next.endMillis))
            } else stringResource(R.string.pl_range, start, SloTime.formatHm(next.endMillis))
            Text(
                stringResource(R.string.home_plan_next, next.name, range, PriceNow.formatEur(next.cost, locale)),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface
            )
        } else {
            Text(stringResource(R.string.home_plan_empty), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight), color = colors.onSurfaceVariant)
        }
        LinkText(stringResource(R.string.home_plan_open), onOpen)
    }
}

/**
 * The last known power from the delayed Moj Elektro readings: latest quarter hour, the day's peak against the agreed
 * power, and a clear note that this is not a live value. Without a connection: a short invitation.
 */
@Composable
fun PowerHomeCard(connected: Boolean, summary: PowerSummary?, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale
    val lang = LocalLang.current
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    HomeCard(onOpen) {
        CardTitle(stringResource(R.string.home_power_title))
        if (!connected || summary == null) {
            Text(
                stringResource(if (connected) R.string.usage_loading else R.string.home_power_connect),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurfaceVariant
            )
        } else {
            fun kw(v: Double) = String.format(locale, "%.2f", v)
            Text(
                stringResource(R.string.home_power_last, kw(summary.lastKw), SloTime.formatDateTime(summary.lastStartMillis, lang)),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface
            )
            val lastShare = summary.lastShare
            if (summary.lastBlock != null && summary.lastAgreedKw != null && lastShare != null) {
                Text(
                    stringResource(R.string.home_power_agreed, summary.lastBlock, kw(summary.lastAgreedKw), String.format(locale, "%.0f", lastShare * 100)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            } else {
                Text(stringResource(R.string.home_power_no_agreed), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            val peakShare = summary.peakShare
            if (peakShare != null) {
                Text(
                    stringResource(R.string.home_power_peak, kw(summary.peakKw), SloTime.formatHm(summary.peakStartMillis), String.format(locale, "%.0f", peakShare * 100)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (peakShare > 1.0) AppColors.Warning else colors.onSurface
                )
            }
            Text(stringResource(R.string.home_power_delay), style = small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        LinkText(stringResource(R.string.home_power_open), onOpen)
    }
}

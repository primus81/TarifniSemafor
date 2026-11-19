package si.semafor.app.ui.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import si.semafor.app.ui.compose.WidthClass
import si.semafor.app.ui.compose.currentWidthClass
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import si.semafor.app.Advice
import si.semafor.app.ApiTexts
import si.semafor.app.BlockPalette
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.components.HomeSkeleton
import si.semafor.app.BestBlock
import si.semafor.app.Block
import si.semafor.app.DataState
import si.semafor.app.DonationConfig
import si.semafor.app.HomeFormat
import si.semafor.app.HomeUiState
import si.semafor.app.PriceLists
import si.semafor.app.R
import si.semafor.app.Repo
import si.semafor.app.SloTime
import si.semafor.app.ui.compose.components.RefreshButton
import si.semafor.app.ui.compose.components.RefreshableScreen
import si.semafor.app.ui.compose.components.SectionDivider
import si.semafor.app.ui.compose.components.StateCard
import si.semafor.app.ui.compose.theme.AppColors

/**
 * The home screen: current block, data status, the best upcoming block, recommendations for now and for the next
 * block, and the donation card. [nowMillis] ticks every second (countdowns); the reloads at a block boundary are
 * done by the host ([si.semafor.app.HomeViewModel.onTick]).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    nowMillis: Long,
    pullRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPullRefresh: () -> Unit,
    onOpenDonation: (String) -> Unit,
    /** The cards below the current block (advice, plan, price, power); [block] = current block or null. */
    priceCard: (@Composable (block: Int?) -> Unit)? = null
) {
    // Closed at first: the screen starts with the essentials; the uro.si recommendations open with one tap.
    var nowAdviceExpanded by rememberSaveable { mutableStateOf(false) }
    var nextAdviceExpanded by rememberSaveable { mutableStateOf(false) }

    // Wide windows (large tablet, desktop): the current block on the left, the advice on the right.
    val twoColumns = currentWidthClass() == WidthClass.EXPANDED && state is HomeUiState.Content
    RefreshableScreen(
        refreshing = pullRefreshing,
        onRefresh = onPullRefresh,
        maxWidth = if (twoColumns) 1100.dp else 640.dp
    ) {
        when (state) {
            HomeUiState.Loading -> {
                HomeSkeleton()
            }

            is HomeUiState.Error -> {
                val last = state.lastUpdate
                val detail = if (last != null && last > 0L) {
                    stringResource(R.string.home_error_detail, SloTime.formatDateTime(last, LocalLang.current))
                } else null
                StateCard(
                    stringResource(R.string.home_error_title),
                    detail,
                    stringResource(R.string.home_error_warning)
                )
                RefreshButton(onRefresh)
            }

            is HomeUiState.Content -> {
                val now = state.info.now
                val blockPart: @Composable ColumnScope.() -> Unit = {
                    // A. Current state: block, countdown, data status, next favourable block.
                    CurrentBlockCard(now, state.nextChangeMillis, nowMillis)
                    StatusRow(state, onRefresh)
                    state.best?.let { BestBlockCard(it, nowMillis) }
                    // B-E. What to do now, the day plan, my price, my power (block only if it covers this hour).
                    priceCard?.invoke(if (Repo.coversNow(now)) now.id else null)
                }
                val advicePart: @Composable ColumnScope.() -> Unit = {
                    AdviceSection(stringResource(R.string.home_advice_now), now, nowAdviceExpanded) { nowAdviceExpanded = it }
                    SectionDivider()
                    AdviceSection(stringResource(R.string.home_advice_next), state.info.next, nextAdviceExpanded) {
                        nextAdviceExpanded = it
                    }
                    Text(
                        text = stringResource(R.string.home_advice_source_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        Column(Modifier.weight(1f)) {
                            blockPart()
                            if (DonationConfig.DONATIONS_ENABLED) {
                                SectionDivider(thickness = 2.dp)
                                DonationCard(onOpenDonation)
                            }
                        }
                        Column(Modifier.weight(1f)) { advicePart() }
                    }
                } else {
                    blockPart()
                    SectionDivider()
                    advicePart()
                    if (DonationConfig.DONATIONS_ENABLED) {
                        SectionDivider(thickness = 2.dp)
                        DonationCard(onOpenDonation)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentBlockCard(block: Block, nextChangeMillis: Long, nowMillis: Long) {
    val background = Color(Repo.color(block.id))
    // Text colour follows the block colour: whichever of dark / white reads better on it (WCAG contrast).
    val spokenPrice = PriceLists.energyPrice(PriceLists.current(), block.id)
        ?.let { stringResource(R.string.home_card_price, PriceLists.formatNumber(it, currentLocale)) }
        ?: ""
    // Dark text on every official block colour (contrast at least 4.5:1, checked by BlockPaletteTest).
    val onCard = Color(BlockPalette.ON_BLOCK)
    val context = LocalContext.current
    val season = remember(block.season, LocalLang.current) { ApiTexts.translate(context, block.season) }
    val load = remember(block.load, LocalLang.current) { ApiTexts.translate(context, block.load) }
    val nextHm = SloTime.formatHm(nextChangeMillis)
    val spoken = stringResource(R.string.home_card_description, block.id, load, block.from, block.to, season, spokenPrice, nextHm)
    // Must always stay on ONE line. The minimum is 10 sp; with a large system font it is lowered so that the longest
    // text ("čez 23 h 59 min 59 s") still fits instead of being cut off.
    val fontScale = LocalDensity.current.fontScale
    val countdownMin = (10f / maxOf(1f, fontScale)).toInt().coerceAtLeast(1)

    // TalkBack reads the card as ONE item. The ticking countdown is not part of it (it would be re-announced every
    // second); the fixed time of the next change is.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
            }
    ) {
        // Text label as well as colour: the active block is recognisable without relying on colour.
        Text(stringResource(R.string.home_current_block), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onCard)
        BasicText(
            text = stringResource(R.string.common_block, block.id),
            style = TextStyle(color = onCard, fontWeight = FontWeight.Bold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 27.sp, maxFontSize = 43.sp, stepSize = 1.sp)
        )
        Text(load, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = onCard)
        Text(stringResource(R.string.home_block_hours, block.from, block.to, season), fontSize = 13.sp, color = onCard)
        BasicText(
            text = (nextChangeMillis - nowMillis).let {
                if (it <= 0) stringResource(R.string.home_countdown_now)
                else stringResource(R.string.home_countdown, HomeFormat.duration(it))
            },
            style = TextStyle(color = onCard, fontStyle = FontStyle.Italic),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = countdownMin.sp, maxFontSize = 12.sp, stepSize = 1.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
    }
}

/**
 * Status text on the left, round refresh button on the right edge. The button is as high as the text block (at most
 * 32 dp); if the text block is lower than 24 dp the text is reduced by 1 sp (at most 3 times). Compose extends the
 * touch area of the small button to the 48 dp minimum by itself.
 */
@Composable
private fun StatusRow(state: HomeUiState.Content, onRefresh: () -> Unit) {
    val info = state.info
    val dataState = remember(info) { Repo.dataState(info) }
    val text = stringResource(R.string.home_last_update, SloTime.formatDateTime(info.time, LocalLang.current)) + "\n" +
        stringResource(
            when {
                dataState == DataState.FRESH -> R.string.home_data_fresh
                state.offlineFailure -> R.string.home_data_offline
                else -> R.string.home_data_cached
            }
        )
    val refreshLabel = stringResource(R.string.common_refresh)
    val density = LocalDensity.current
    var textSizeSp by remember(state) { mutableFloatStateOf(12f) }
    var shrinkAttempts by remember(state) { mutableIntStateOf(0) }
    var textHeightPx by remember { mutableIntStateOf(0) }
    val minTextPx = with(density) { 24.dp.roundToPx() }
    val buttonSize = with(density) { minOf(textHeightPx.toDp(), 32.dp) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = textSizeSp.sp,
            color = if (dataState == DataState.FRESH) AppColors.TextSecondary else AppColors.Warning,
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { size ->
                    if (size.height in 1 until minTextPx && textSizeSp > 9f && shrinkAttempts < 3) {
                        shrinkAttempts++
                        textSizeSp -= 1f
                    } else {
                        textHeightPx = size.height
                    }
                }
        )
        if (buttonSize > 0.dp) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(role = Role.Button, onClick = onRefresh)
                    .semantics { contentDescription = refreshLabel }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Same colour as the card above: the colour of the current block.
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = Color(Repo.color(info.now.id))
                )
            }
        }
    }
}

@Composable
private fun BestBlockCard(best: BestBlock, nowMillis: Long) {
    val colors = MaterialTheme.colorScheme
    val time = SloTime.formatHm(best.startMillis)
    val prefix = stringResource(R.string.home_best_prefix)
    val suffix = stringResource(R.string.home_best_suffix)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(16.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(prefix)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(time) }
                append(suffix)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface
        )
        // The ticking countdown stays visual only; the line above already says when the block starts.
        Text(
            text = (best.startMillis - nowMillis).let {
                if (it <= 0) stringResource(R.string.home_best_now)
                else stringResource(R.string.home_best_countdown, HomeFormat.duration(it))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics { }
        )
    }
}

/**
 * A foldable list of recommendations: the heading row (name + chevron) is one button that shows or hides the list.
 */
@Composable
private fun AdviceSection(title: String, block: Block, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val spoken = stringResource(if (expanded) R.string.common_expanded else R.string.common_collapsed, title)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(150), label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onExpandedChange(!expanded) }
            .clearAndSetSemantics {
                role = Role.Button
                heading()
                contentDescription = spoken
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column { for (a in block.advice) AdviceRow(a) }
    }
}

@Composable
private fun AdviceRow(a: Advice) {
    // Symbol + description in addition to colour (colour alone must not carry the meaning).
    val (symbol, dotColor, description) = when (a.state) {
        1 -> Triple(R.drawable.ic_state_good, Color(0xFF43A047), stringResource(R.string.home_advice_good))
        2 -> Triple(R.drawable.ic_state_mid, Color(0xFFFFB300), stringResource(R.string.home_advice_mid))
        else -> Triple(R.drawable.ic_state_bad, AppColors.Over, stringResource(R.string.home_advice_bad))
    }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    // Recommendations come from uro.si in Slovenian; known texts are shown in the app's language.
    val context = LocalContext.current
    val lang = LocalLang.current
    val line = remember(a, lang) { ApiTexts.translate(context, a.device) + ": " + ApiTexts.translate(context, a.use) }
    // One spoken item per recommendation, with the state in words.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = "$description. $line" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(dotColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(symbol), contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(16.dp))
        }
        // Device group icon (Material Icons), tinted like the text so it works in both themes.
        ApiTexts.deviceIcon(a.device)?.let { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(22.dp)
            )
        }
        Text(line, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

/**
 * Small, unobtrusive donation card at the end of the home screen: one quiet line of text and one row of amounts.
 * No border and muted colours, so it never competes with the information above it.
 */
@Composable
private fun DonationCard(onOpenDonation: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val donationSpoken = stringResource(R.string.home_donation_title_spoken)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 16.dp)
            .background(colors.surface.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.home_donation_title),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {
                heading()
                contentDescription = donationSpoken
            }
        )
        Text(
            text = stringResource(R.string.home_donation_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DonationButton("1 €", stringResource(R.string.home_donation_1), Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_1)
            }
            DonationButton("3 €", stringResource(R.string.home_donation_3), Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_3)
            }
            DonationButton("5 €", stringResource(R.string.home_donation_5), Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_5)
            }
            DonationButton(stringResource(R.string.home_donation_custom), stringResource(R.string.home_donation_any), Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_CUSTOM)
            }
        }
    }
}

@Composable
private fun DonationButton(
    label: String,
    description: String,
    modifier: Modifier,
    fontSize: Int = 12,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

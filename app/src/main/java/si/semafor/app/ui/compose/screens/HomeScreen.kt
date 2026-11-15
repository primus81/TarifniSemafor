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
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import si.semafor.app.Advice
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
    onOpenDonation: (String) -> Unit
) {
    // Open at every start; kept while the app runs (also across screen switches and rotation). Not a setting.
    var nowAdviceExpanded by rememberSaveable { mutableStateOf(true) }
    var nextAdviceExpanded by rememberSaveable { mutableStateOf(true) }

    RefreshableScreen(refreshing = pullRefreshing, onRefresh = onPullRefresh) {
        when (state) {
            HomeUiState.Loading -> {
                StateCard("Nalagam podatke …", "Pridobivam podatke o trenutnem časovnem bloku.")
                RefreshButton(onRefresh)
            }

            is HomeUiState.Error -> {
                val last = state.lastUpdate
                val detail = if (last != null && last > 0L) {
                    "Zadnja posodobitev: " + SloTime.formatDateTime(last) +
                        "\nShranjeni podatki niso več veljavni, zato niso prikazani."
                } else null
                StateCard(
                    "Podatkov trenutno ni mogoče preveriti.",
                    detail,
                    "Posodobitev trenutno ni mogoča. Preveri internetno povezavo."
                )
                RefreshButton(onRefresh)
            }

            is HomeUiState.Content -> {
                val now = state.info.now
                CurrentBlockCard(now, state.nextChangeMillis, nowMillis)
                StatusRow(state, onRefresh)
                state.best?.let { BestBlockCard(it, nowMillis) }

                SectionDivider()
                AdviceSection("Priporočila zdaj", now, nowAdviceExpanded) { nowAdviceExpanded = it }
                SectionDivider()
                AdviceSection("Priporočila za naslednji časovni blok", state.info.next, nextAdviceExpanded) {
                    nextAdviceExpanded = it
                }

                if (DonationConfig.DONATIONS_ENABLED) {
                    SectionDivider(thickness = 2.dp)
                    DonationCard(onOpenDonation)
                }
            }
        }
    }
}

@Composable
private fun CurrentBlockCard(block: Block, nextChangeMillis: Long, nowMillis: Long) {
    val background = Color(Repo.color(block.id))
    // Text colour follows the block colour: whichever of dark / white reads better on it (WCAG contrast).
    val onCard = remember(block.id) { readableTextColor(background) }
    val season = HomeFormat.seasonLabel(block.season)
    val spokenPrice = PriceLists.energyPrice(PriceLists.current(), block.id)
        ?.let { " Omrežnina za energijo " + PriceLists.formatNumber(it) + " evra na kilovatno uro." }
        ?: ""
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
                contentDescription =
                    "Trenutni časovni blok: ${block.id}. blok, ${block.load}, od ${block.from} do ${block.to} ure, " +
                        "$season.$spokenPrice Naslednja sprememba ob ${SloTime.formatHm(nextChangeMillis)}."
            }
    ) {
        // Text label as well as colour: the active block is recognisable without relying on colour.
        Text("Trenutni časovni blok", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onCard)
        BasicText(
            text = "${block.id}. blok",
            style = TextStyle(color = onCard, fontWeight = FontWeight.Bold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 27.sp, maxFontSize = 43.sp, stepSize = 1.sp)
        )
        Text(block.load, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = onCard)
        Text("${block.from}–${block.to} h · $season", fontSize = 13.sp, color = onCard)
        BasicText(
            text = HomeFormat.blockCountdown(nextChangeMillis - nowMillis),
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
    val text = "Zadnja posodobitev: " + SloTime.formatDateTime(info.time) + "\n" + when {
        dataState == DataState.FRESH -> "Podatki so aktualni."
        state.offlineFailure -> "Posodobitev trenutno ni mogoča. Preveri internetno povezavo.\nPrikazani so zadnji shranjeni podatki."
        else -> "Prikazani so zadnji shranjeni podatki."
    }
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
                    .background(RefreshButtonFill)
                    .border(1.dp, RefreshButtonStroke, CircleShape)
                    .clickable(role = Role.Button, onClick = onRefresh)
                    .semantics { contentDescription = "Osveži" }
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
                append("Naslednji najugodnejši časovni blok ob ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(time) }
                append(" uri")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface
        )
        // The ticking countdown stays visual only; the line above already says when the block starts.
        Text(
            text = HomeFormat.bestCountdown(best.startMillis - nowMillis),
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
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(150), label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onExpandedChange(!expanded) }
            .clearAndSetSemantics {
                role = Role.Button
                heading()
                contentDescription = title + ", " + (if (expanded) "razširjeno" else "strnjeno") +
                    ". Dotakni se za " + (if (expanded) "strnitev" else "razširitev") + "."
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
        1 -> Triple("✓", Color(0xFF43A047), "Ugodno")
        2 -> Triple("!", Color(0xFFFDD835), "Vmes")
        else -> Triple("✕", Color(0xFFE53935), "Manj ugodno")
    }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    // One spoken item per recommendation, with the state in words.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = "$description. ${a.device}: ${a.use}" },
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
            Text(symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
        }
        Text("${a.device}: ${a.use}", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

@Composable
private fun DonationCard(onOpenDonation: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(14.dp)
    ) {
        Text(
            text = "☕ Podpri razvoj aplikacije",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    heading()
                    contentDescription = "Podpri razvoj aplikacije"
                }
        )
        Text(
            text = "Če ti je aplikacija všeč in ti koristi, lahko s prostovoljno donacijo podpreš njen nadaljnji razvoj. Hvala za tvojo podporo!",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DonationButton("1 €", "Podpri razvoj z donacijo v višini 1 evra", Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_1)
            }
            DonationButton("3 €", "Podpri razvoj z donacijo v višini 3 evrov", Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_3)
            }
            DonationButton("5 €", "Podpri razvoj z donacijo v višini 5 evrov", Modifier.weight(1f)) {
                onOpenDonation(DonationConfig.URL_5)
            }
        }
        DonationButton(
            "Poljuben znesek",
            "Podpri razvoj z donacijo poljubnega zneska",
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            fontSize = 12
        ) { onOpenDonation(DonationConfig.URL_CUSTOM) }
    }
}

@Composable
private fun DonationButton(
    label: String,
    description: String,
    modifier: Modifier,
    fontSize: Int = 13,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val RefreshButtonFill = Color(0xFF2A3037)
private val RefreshButtonStroke = Color(0xFF3A424B)

/**
 * Text colour for content drawn on [background] (the block colour): the candidate with the higher WCAG contrast
 * ratio, dark #111111 or white. If even the best one stays below 4.5:1 (AA for small text), pure black is used
 * instead of #111111 because it gains a little more contrast. A fixed luminance threshold such as 0.5 is
 * deliberately not used: with this palette it would pick white on the red, orange and green blocks.
 */
private fun readableTextColor(background: Color): Color {
    val bg = background.toArgb()
    val dark = 0xFF111111.toInt()
    val light = 0xFFFFFFFF.toInt()
    val contrastDark = ColorUtils.calculateContrast(dark, bg)
    val contrastLight = ColorUtils.calculateContrast(light, bg)
    val darkWins = contrastDark >= contrastLight
    val chosen = if (maxOf(contrastDark, contrastLight) >= 4.5) {
        if (darkWins) dark else light
    } else {
        if (darkWins) 0xFF000000.toInt() else light
    }
    return Color(chosen)
}

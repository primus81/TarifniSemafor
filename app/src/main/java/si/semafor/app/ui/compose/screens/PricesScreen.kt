package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import si.semafor.app.PriceList
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.PriceLists
import si.semafor.app.Repo
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.theme.AppTheme

private const val W_BLOCK = 1.3f
private const val W_POWER = 1.5f
private const val W_ENERGY = 1.3f

/**
 * The "Cenik omrežnine" screen. Prices come only from [PriceLists] (single source of truth); [currentBlockId] is the
 * verified current block of the home screen (highlighted), null when no trusted data is shown.
 */
@Composable
fun PricesScreen(
    list: PriceList?,
    currentBlockId: Int?,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    ScreenScaffold {
        ScreenHeader(
            title = stringResource(R.string.prices_title),
            subtitle = stringResource(R.string.prices_subtitle),
            onBack = onBack,
            bottomPadding = if (list == null) 12.dp else 2.dp
        )
        PricesContent(list, currentBlockId, onOpenUrl)
    }
}

/**
 * The SODO network price list (table of blocks 1-5), its validity and, behind the "i" button, the notes that explain
 * it. Used inside the "Cene elektrike in omrežnine" screen and by [PricesScreen].
 */
@Composable
fun ColumnScope.PricesContent(list: PriceList?, currentBlockId: Int?, onOpenUrl: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalLang.current
    if (list == null) {
        InfoCard {
            Text(
                text = stringResource(R.string.prices_missing),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface
            )
            ActionButton(
                label = stringResource(R.string.prices_open_sodo),
                onClick = { onOpenUrl(PriceLists.SOURCE_ALL) },
                modifier = Modifier.padding(top = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                horizontalPadding = 24.dp
            )
        }
        return
    }
    val notes = buildList {
        add(stringResource(R.string.prices_note_group))
        add(stringResource(R.string.prices_note_taxes))
        add(stringResource(R.string.prices_note_power))
        add(stringResource(R.string.prices_note_block1))
        list.noteIn(lang)?.let { add(it) }
        add(stringResource(R.string.prices_note_info))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = stringResource(
                R.string.prices_valid,
                SloTime.formatLocalDate(list.validFrom, lang),
                SloTime.formatLocalDate(list.validTo, lang)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        si.semafor.app.ui.compose.components.InfoButton(stringResource(R.string.prices_title), notes)
    }

    PriceTable(list, currentBlockId)

    LinkText(stringResource(R.string.prices_official)) { onOpenUrl(list.sourceUrl) }
}

@Composable
private fun PriceTable(list: PriceList, currentBlockId: Int?) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(8.dp)
    ) {
        // One spoken item for the header; each data row is one item with units (cells alone would be bare numbers).
        val headerSpoken = stringResource(R.string.prices_header_spoken)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 8.dp)
                .clearAndSetSemantics { contentDescription = headerSpoken }
        ) {
            HeaderCell(stringResource(R.string.prices_col_block), W_BLOCK, TextAlign.Start)
            HeaderCell(stringResource(R.string.prices_col_power), W_POWER, TextAlign.End)
            HeaderCell(stringResource(R.string.prices_col_energy), W_ENERGY, TextAlign.End)
        }
        val locale = currentLocale
        val nowSpoken = stringResource(R.string.prices_row_now_spoken)

        for (r in list.rows) {
            val isNow = currentBlockId == r.block
            val blockColor = Color(Repo.color(r.block))
            val power = PriceLists.formatNumber(r.powerEurPerKw, locale)
            val energy = PriceLists.formatNumber(r.energyEurPerKwh, locale)
            val spoken = stringResource(R.string.prices_row_spoken, r.block, if (isNow) nowSpoken else "", power, energy)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 44.dp)
                    .background(if (isNow) blockColor.copy(alpha = 0x40 / 255f) else Color.Transparent)
                    .clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(if (isNow) blockColor else Color.Transparent)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(if (isNow) R.string.common_block_now else R.string.common_block, r.block),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNow) FontWeight.Bold else null,
                        color = colors.onSurface,
                        modifier = Modifier.weight(W_BLOCK)
                    )
                    NumberCell(power, W_POWER, isNow)
                    NumberCell(energy, W_ENERGY, isNow)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.weight(weight)
    )
}

/** A price: one line, right aligned; it shrinks (down to 10 sp) instead of wrapping with a large system font. */
@Composable
private fun RowScope.NumberCell(text: String, weight: Float, bold: Boolean) {
    BasicText(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.Bold else null,
            textAlign = TextAlign.End
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 1.sp),
        modifier = Modifier.weight(weight)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun PricesScreenPreview() {
    AppTheme { PricesScreen(PriceLists.ALL.firstOrNull(), currentBlockId = 2, onBack = {}, onOpenUrl = {}) }
}

package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import si.semafor.app.PriceList
import si.semafor.app.PriceLists
import si.semafor.app.Repo
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.theme.AppTheme
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("d. M. yyyy")
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
    val colors = MaterialTheme.colorScheme
    ScreenScaffold {
        ScreenHeader(
            title = "Cenik omrežnine",
            subtitle = "Uporabniška skupina 0 (gospodinjstva in nizka napetost)",
            onBack = onBack,
            bottomPadding = if (list == null) 12.dp else 2.dp
        )

        if (list == null) {
            InfoCard {
                Text(
                    text = "Cenik za trenutno obdobje še ni vnesen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface
                )
                ActionButton(
                    label = "Odpri cenike SODO",
                    onClick = { onOpenUrl(PriceLists.SOURCE_ALL) },
                    modifier = Modifier.padding(top = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    horizontalPadding = 24.dp
                )
            }
            return@ScreenScaffold
        }

        Text(
            text = "Velja od ${list.validFrom.format(DATE)} do ${list.validTo.format(DATE)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        PriceTable(list, currentBlockId)

        val notes = buildList {
            add("Cenik velja za uporabniško skupino 0 (gospodinjski odjemalci in drugi odjemalci na nizkonapetostnem omrežju). Uporabniki brez 15-minutnih meritev imajo drugačen obračun in tu niso prikazani.")
            add("Zneski so brez davkov, taks, trošarin in drugih dajatev.")
            add("Tarifna postavka za moč se obračunava glede na dogovorjeno (in morebitno presežno) obračunsko moč v posameznem časovnem bloku, ne glede na porabo kWh. Tarifna postavka za energijo se obračunava glede na prevzeto energijo v kWh.")
            add("Blok 1 velja samo ob delavnikih v višji sezoni (november–februar).")
            list.note?.let { add(it) }
            add("Cenik je informativen. Veljavne postavke objavlja operater (SODO).")
        }
        InfoCard {
            notes.forEachIndexed { i, n ->
                Text(
                    text = "• $n",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 14.3.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (i > 0) 6.dp else 0.dp)
                )
            }
        }

        LinkText("Uradni cenik (SODO)") { onOpenUrl(list.sourceUrl) }
    }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 8.dp)
                .clearAndSetSemantics {
                    contentDescription =
                        "Stolpci: blok, moč v evrih na kilovat na mesec, energija v evrih na kilovatno uro."
                }
        ) {
            HeaderCell("Blok", W_BLOCK, TextAlign.Start)
            HeaderCell("Moč [€/kW/mesec]", W_POWER, TextAlign.End)
            HeaderCell("Energija [€/kWh]", W_ENERGY, TextAlign.End)
        }

        for (r in list.rows) {
            val isNow = currentBlockId == r.block
            val blockColor = Color(Repo.color(r.block))
            val power = PriceLists.formatNumber(r.powerEurPerKw)
            val energy = PriceLists.formatNumber(r.energyEurPerKwh)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 44.dp)
                    .background(if (isNow) blockColor.copy(alpha = 0x40 / 255f) else Color.Transparent)
                    .clearAndSetSemantics {
                        contentDescription = "${r.block}. blok" +
                            (if (isNow) ", trenutno veljavni časovni blok" else "") +
                            ". Moč $power evra na kilovat na mesec. Energija $energy evra na kilovatno uro."
                    },
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
                        text = if (isNow) "${r.block}. blok (zdaj)" else "${r.block}. blok",
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

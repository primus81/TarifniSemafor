package si.semafor.app.ui.compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.BlockPalette
import si.semafor.app.R
import si.semafor.app.Repo
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.DailyBlocks
import si.semafor.app.mojelektro.ProductionDay
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.ui.compose.theme.AppColors
import java.util.Locale

/*
 * Charts of the "Semafor porabe" screen, drawn with Compose Canvas. No chart library: MPAndroidChart is View based and
 * no longer maintained, and these few chart types are small enough to draw directly (no extra dependency, no R8 rules,
 * colours and fonts exactly as in the rest of the app). Every chart has a spoken summary for TalkBack.
 */

private val SL = Locale.forLanguageTag("sl-SI")

/** "1,23" (Slovenian) or "1.23" (English, with [locale]). */
internal fun fmt(v: Double, decimals: Int = 2, locale: Locale = SL): String = String.format(locale, "%.${decimals}f", v)

/** Energy whose block is not known (never a block colour). */
internal val UnknownColor = Color(BlockPalette.UNKNOWN)

/** The block colour of the official uro.si table of the current year (cards, widget, notification, charts). */
internal fun blockColor(id: Int): Color = Color(Repo.color(id))

/**
 * Horizontal gauge: a 15-minute average power ([peakKw]) against the agreed power ([agreedKw]) of a block.
 * The track ends at 120 % so that an excess stays visible; the white mark is 100 %.
 */
@Composable
fun AgreedPowerGauge(agreedKw: Double, peakKw: Double, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outline
    val mark = MaterialTheme.colorScheme.onSurface
    val locale = currentLocale
    val ratio = if (agreedKw > 0) peakKw / agreedKw else 0.0
    val spoken = stringResource(R.string.chart_gauge, fmt(peakKw, 2, locale), fmt(ratio * 100, 0, locale), fmt(agreedKw, 2, locale))
    Canvas(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { contentDescription = spoken }
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val fill = (ratio / 1.2).coerceIn(0.0, 1.0).toFloat()
        val fillColor = when {
            ratio > 1.0 -> AppColors.Over
            ratio >= 0.9 -> AppColors.Near
            else -> color
        }
        if (fill > 0f) drawRoundRect(fillColor, size = Size(size.width * fill, size.height), cornerRadius = r)
        val x = size.width / 1.2f
        drawLine(mark, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
    }
}

/** Energy per day, stacked by tariff block (block colours of the app, block 1 on top); unassigned energy apart. */
@Composable
fun DailyBlocksChart(days: List<DailyBlocks>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axis)
    val locale = currentLocale
    val total = days.sumOf { it.totalKwh }
    val dear = days.sumOf { it.kwhByBlock[0] + it.kwhByBlock[1] }
    val summary = stringResource(R.string.chart_daily_blocks, days.size, fmt(total, 1, locale), fmt(dear, 1, locale))
    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = summary }
    ) {
        if (days.isEmpty()) return@Canvas
        val left = 34.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 8.dp.toPx()
        val width = size.width - left
        val maxKwh = maxOf(days.maxOf { it.totalKwh }, 0.1) * 1.1
        fun h(kwh: Double) = ((kwh / maxKwh) * (bottom - top)).toFloat()
        val slot = width / days.size

        yAxis(measurer, labelStyle, maxKwh, left, top, bottom, "kWh", locale)

        val labelEvery = if (days.size > 10) 5 else 1
        days.forEachIndexed { i, d ->
            val x = left + i * slot + slot * 0.15f
            val w = slot * 0.7f
            var y = bottom
            // From the cheapest block at the bottom to the most expensive on top.
            for (block in 5 downTo 1) {
                val seg = h(d.kwhByBlock[block - 1])
                if (seg > 0f) {
                    drawRect(blockColor(block), Offset(x, y - seg), Size(w, seg))
                    y -= seg
                }
            }
            val un = h(d.unassignedKwh)
            if (un > 0f) drawRect(UnknownColor, Offset(x, y - un), Size(w, un))
            if (i % labelEvery == 0 || i == days.lastIndex) {
                drawLabel(measurer, "${d.date.dayOfMonth}.", labelStyle, Offset(left + i * slot + slot / 2, bottom + 3.dp.toPx()))
            }
        }
    }
}

/** Taken (A+) and delivered (A-) energy per 15 minutes of one day, as two filled areas. */
@Composable
fun ProductionChart(day: ProductionDay, importColor: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axis)
    val locale = currentLocale
    val summary = stringResource(
        R.string.chart_production,
        fmt(day.totalImport, 2, locale),
        fmt(day.totalExport, 2, locale),
        SloTime.formatDate(day.startMillis.firstOrNull() ?: 0L, LocalLang.current)
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = summary }
    ) {
        val n = day.startMillis.size
        if (n < 2) return@Canvas
        val left = 34.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 8.dp.toPx()
        val width = size.width - left
        val max = maxOf(day.importKwh.max(), day.exportKwh.max(), 0.01) * 1.1
        fun x(i: Int) = left + width * i / (n - 1)
        fun y(v: Double) = (bottom - (v / max) * (bottom - top)).toFloat()

        yAxis(measurer, labelStyle, max, left, top, bottom, "kWh", locale)
        area(day.importKwh, importColor, ::x, ::y, bottom)
        area(day.exportKwh, AppColors.Export, ::x, ::y, bottom)
        day.startMillis.forEachIndexed { i, t ->
            val hour = SloTime.hourOfDay(t)
            if (hour % 6 == 0 && SloTime.formatHm(t).endsWith(":00")) {
                drawLabel(measurer, "$hour h", labelStyle, Offset(x(i), bottom + 3.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.area(values: List<Double>, color: Color, x: (Int) -> Float, y: (Double) -> Float, bottom: Float) {
    val line = Path()
    values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
    val fill = Path().apply {
        addPath(line)
        lineTo(x(values.lastIndex), bottom)
        lineTo(x(0), bottom)
        close()
    }
    drawPath(fill, color.copy(alpha = 0.25f))
    drawPath(line, color, style = Stroke(width = 1.5.dp.toPx()))
}

/** Y axis: base line plus the maximum and half of it, with the unit. */
private fun DrawScope.yAxis(
    measurer: TextMeasurer,
    style: TextStyle,
    max: Double,
    left: Float,
    top: Float,
    bottom: Float,
    unit: String,
    locale: Locale
) {
    val grid = style.color.copy(alpha = 0.3f)
    drawLine(grid, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
    for (f in listOf(0.5, 1.0)) {
        val y = (bottom - f * (bottom - top)).toFloat()
        drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
        val layout = measurer.measure(fmt(max * f, 1, locale), style)
        drawText(layout, topLeft = Offset(left - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
    }
    val unitLayout = measurer.measure(unit, style)
    drawText(unitLayout, topLeft = Offset(0f, bottom - unitLayout.size.height / 2f))
}

/** Text centred horizontally on [center].x, kept inside the canvas. */
private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, center: Offset) {
    val layout = measurer.measure(text, style)
    val x = (center.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(x, center.y))
}

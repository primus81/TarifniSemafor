package si.semafor.app.ui.compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.Repo
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.DailyBlocks
import si.semafor.app.mojelektro.DayProfile
import si.semafor.app.mojelektro.PowerLevel
import si.semafor.app.mojelektro.ProductionDay
import java.util.Locale

/*
 * Charts of the "Moja poraba" screen, drawn with Compose Canvas. No chart library: MPAndroidChart is View based and
 * no longer maintained, and these few chart types are small enough to draw directly (no extra dependency, no R8 rules,
 * colours and fonts exactly as in the rest of the app). Every chart has a spoken summary for TalkBack.
 */

private val SL = Locale.forLanguageTag("sl-SI")

/** "1,23" in Slovenian formatting. */
internal fun fmt(v: Double, decimals: Int = 2): String = String.format(SL, "%.${decimals}f", v)

/** Bar colour of an interval: neutral, close to the agreed power, above it. */
internal val NormalBar = Color(0xFF42A5F5)
internal val NearBar = Color(0xFFFDD835)
internal val OverBar = Color(0xFFE53935)
internal val ExportColor = Color(0xFF43A047)
private val Unassigned = Color(0xFF888888)

/** The block colour the app uses everywhere (cards, widget, notification). */
internal fun blockColor(id: Int): Color = Color(Repo.color(id))

/**
 * Horizontal gauge: highest 15-minute average power ([peakKw]) against the agreed power ([agreedKw]) of a block.
 * The track ends at 120 % so that an excess stays visible; the white mark is 100 %.
 */
@Composable
fun AgreedPowerGauge(agreedKw: Double, peakKw: Double, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outline
    val ratio = if (agreedKw > 0) peakKw / agreedKw else 0.0
    Canvas(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics {
                contentDescription = "Najvišja povprečna 15-minutna moč ${fmt(peakKw)} kW, " +
                    "to je ${fmt(ratio * 100, 0)} odstotkov dogovorjene moči ${fmt(agreedKw)} kW."
            }
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val fill = (ratio / 1.2).coerceIn(0.0, 1.0).toFloat()
        val fillColor = when {
            ratio > 1.0 -> OverBar
            ratio >= 0.9 -> NearBar
            else -> color
        }
        if (fill > 0f) drawRoundRect(fillColor, size = Size(size.width * fill, size.height), cornerRadius = r)
        val mark = size.width / 1.2f
        drawLine(Color.White, Offset(mark, -2f), Offset(mark, size.height + 2f), strokeWidth = 2.dp.toPx())
    }
}

/**
 * 15-minute average power of one day: bars coloured by how close they came to the agreed power of their block, the
 * agreed power as a dashed step line, a strip with the block colours under the bars and the highest peak marked.
 */
@Composable
fun PeakBarChart(profile: DayProfile, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axis)
    val peak = profile.peakIndex?.let { profile.intervals[it] }
    val summary = remember(profile) {
        val over = profile.intervals.count { it.level == PowerLevel.OVER }
        val near = profile.intervals.count { it.level == PowerLevel.NEAR }
        buildString {
            append("Povprečna 15-minutna moč dne ${SloTime.formatDate(profile.intervals.firstOrNull()?.startMillis ?: 0L)}.")
            peak?.let { append(" Najvišja ${fmt(it.kw)} kW ob ${SloTime.formatHm(it.startMillis)}.") }
            append(" Nad dogovorjeno močjo: $over intervalov, blizu nje: $near.")
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = summary }
    ) {
        val points = profile.intervals
        if (points.isEmpty()) return@Canvas
        val left = 34.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        val top = 18.dp.toPx()
        val width = size.width - left
        val maxKw = maxOf(points.maxOf { it.kw }, points.mapNotNull { it.agreedKw }.maxOrNull() ?: 0.0, 0.1) * 1.1
        fun y(kw: Double) = (bottom - (kw / maxKw) * (bottom - top)).toFloat()
        val slot = width / points.size

        yAxis(measurer, labelStyle, maxKw, left, top, bottom, "kW")

        points.forEachIndexed { i, p ->
            val color = when (p.level) {
                PowerLevel.OVER -> OverBar
                PowerLevel.NEAR -> NearBar
                else -> NormalBar
            }
            val x = left + i * slot
            drawRect(color, Offset(x + slot * 0.1f, y(p.kw)), Size(slot * 0.8f, bottom - y(p.kw)))
            // Block strip.
            p.blockId?.let { drawRect(blockColor(it), Offset(x, bottom + 2.dp.toPx()), Size(slot + 0.5f, 4.dp.toPx())) }
        }
        // Agreed power per block: dashed step line.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        points.forEachIndexed { i, p ->
            p.agreedKw?.let { kw ->
                drawLine(
                    Color.White, Offset(left + i * slot, y(kw)), Offset(left + (i + 1) * slot, y(kw)),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = dash
                )
            }
        }
        // Hour labels every 6 hours.
        points.forEachIndexed { i, p ->
            val hour = SloTime.hourOfDay(p.startMillis)
            if (hour % 6 == 0 && SloTime.formatHm(p.startMillis).endsWith(":00")) {
                drawLabel(measurer, "$hour h", labelStyle, Offset(left + i * slot, bottom + 9.dp.toPx()))
            }
        }
        // Peak.
        profile.peakIndex?.let { i ->
            val p = points[i]
            val cx = left + i * slot + slot / 2
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(cx, y(p.kw)))
            val text = "${fmt(p.kw)} kW ob ${SloTime.formatHm(p.startMillis)}"
            val layout = measurer.measure(text, labelStyle.copy(color = Color.White, fontSize = 11.sp))
            val tx = (cx - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, (y(p.kw) - layout.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
        }
    }
}

/** Energy per day, stacked by tariff block (block colours of the app, block 1 on top); unassigned energy in grey. */
@Composable
fun DailyBlocksChart(days: List<DailyBlocks>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axis)
    val summary = remember(days) {
        val total = days.sumOf { it.totalKwh }
        val dear = days.sumOf { it.kwhByBlock[0] + it.kwhByBlock[1] }
        "Poraba po dnevih in časovnih blokih, ${days.size} dni, skupaj ${fmt(total, 1)} kWh, " +
            "od tega v blokih 1 in 2 ${fmt(dear, 1)} kWh."
    }
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

        yAxis(measurer, labelStyle, maxKwh, left, top, bottom, "kWh")

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
            if (un > 0f) drawRect(Unassigned, Offset(x, y - un), Size(w, un))
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
    val summary = remember(day) {
        "Prejeta energija ${fmt(day.totalImport)} kWh in oddana energija ${fmt(day.totalExport)} kWh " +
            "dne ${SloTime.formatDate(day.startMillis.firstOrNull() ?: 0L)}."
    }
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

        yAxis(measurer, labelStyle, max, left, top, bottom, "kWh")
        area(day.importKwh, importColor, ::x, ::y, bottom)
        area(day.exportKwh, ExportColor, ::x, ::y, bottom)
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
    unit: String
) {
    val grid = style.color.copy(alpha = 0.3f)
    drawLine(grid, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
    for (f in listOf(0.5, 1.0)) {
        val y = (bottom - f * (bottom - top)).toFloat()
        drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
        val layout = measurer.measure(fmt(max * f, 1), style)
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

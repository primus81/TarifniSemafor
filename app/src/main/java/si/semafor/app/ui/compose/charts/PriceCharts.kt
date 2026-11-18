package si.semafor.app.ui.compose.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.SloTime
import si.semafor.app.pricing.KwhPrice

/** Colours of the price parts, the same in the light and the dark theme (not the block colours). */
object PriceColors {
    val Energy = Color(0xFF5B8DEF)
    val Network = Color(0xFF26A69A)
    val NetworkPower = Color(0xFF00796B)
    val Charges = Color(0xFFF2A541)
    val Res = Color(0xFFB07CC6)
    val Fee = Color(0xFF7A8BA6)
    val Vat = Color(0xFF9E9E9E)
}

/** One part of a stacked bar. */
@Immutable
data class BarPart(val value: Double, val color: Color)

/** A horizontal bar split into [parts] by their size; the widths grow in when the bar first appears. */
@Composable
fun StackedBar(parts: List<BarPart>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.value }.takeIf { it > 0 } ?: return
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        for (p in parts) {
            if (p.value <= 0) continue
            val w = (p.value / total).toFloat() * grow.value
            if (w > 0.0005f) {
                Box(
                    Modifier
                        .weight(w)
                        .fillMaxHeight()
                        .background(p.color)
                )
            }
        }
        val rest = 1f - grow.value
        if (rest > 0.001f) Box(Modifier.weight(rest).fillMaxHeight())
    }
}

/**
 * Price of every coming hour as stacked bars (energy, network charge, contributions and excise, VAT). The bar of the
 * current hour (the first one) has an outline; the cheapest hour has a dot above it. [description] is read by
 * TalkBack instead of the drawing.
 */
@Composable
fun HourlyPriceChart(prices: List<KwhPrice>, description: String, modifier: Modifier = Modifier) {
    if (prices.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val outline = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(prices.first().startMillis) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val max = prices.maxOf { it.total }
    val cheapest = prices.indices.minBy { prices[it].total }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        val labelH = 16.dp.toPx()
        val topPad = 14.dp.toPx()
        val chartH = size.height - labelH - topPad
        val slot = size.width / prices.size
        val barW = slot * 0.72f
        // Grid line at the top value.
        drawLine(gridColor, Offset(0f, topPad), Offset(size.width, topPad), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, topPad + chartH), Offset(size.width, topPad + chartH), strokeWidth = 1f)

        prices.forEachIndexed { i, p ->
            val c = p.components
            val parts = listOf(
                c.energy to PriceColors.Energy,
                c.networkEnergy to PriceColors.Network,
                (c.ure + c.marketOperator + c.excise + c.resSupport) to PriceColors.Charges,
                c.vat to PriceColors.Vat
            )
            val x = i * slot + (slot - barW) / 2
            var y = topPad + chartH
            for ((v, color) in parts) {
                val h = (v / max).toFloat() * chartH * grow.value
                if (h <= 0f) continue
                y -= h
                drawRect(color, Offset(x, y), Size(barW, h))
            }
            if (i == 0) {
                val h = (p.total / max).toFloat() * chartH * grow.value
                drawRoundRect(
                    outline,
                    Offset(x - 2f, topPad + chartH - h - 2f),
                    Size(barW + 4f, h + 4f),
                    CornerRadius(3f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            if (i == cheapest) {
                val h = (p.total / max).toFloat() * chartH * grow.value
                drawCircle(outline, radius = 3.dp.toPx(), center = Offset(x + barW / 2, topPad + chartH - h - 7.dp.toPx()))
            }
            // Hour labels: the first bar and every third hour.
            val hour = SloTime.hourOfDay(p.startMillis)
            if (i == 0 || hour % 3 == 0) {
                val text = measurer.measure(hour.toString(), labelStyle)
                val tx = (x + barW / 2 - text.size.width / 2).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(tx, topPad + chartH + 2.dp.toPx()))
            }
        }
    }
}

/**
 * A ring of the price parts (share of the total), with [center] text inside. Grows in when it first appears.
 * [description] is read by TalkBack instead of the drawing.
 */
@Composable
fun DonutChart(parts: List<BarPart>, center: String, description: String, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.value }.takeIf { it > 0 } ?: return
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outlineVariant
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(
        modifier
            .size(150.dp)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        val stroke = 22.dp.toPx()
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
        var angle = -90f
        for (p in parts) {
            if (p.value <= 0) continue
            val sweep = (p.value / total * 360.0).toFloat() * grow.value
            drawArc(p.color, angle, (sweep - 1f).coerceAtLeast(0.5f), false, topLeft, Size(d, d), style = Stroke(stroke))
            angle += sweep
        }
        val t = measurer.measure(center, TextStyle(fontSize = 15.sp, color = textColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        drawText(t, topLeft = Offset((size.width - t.size.width) / 2, (size.height - t.size.height) / 2))
    }
}

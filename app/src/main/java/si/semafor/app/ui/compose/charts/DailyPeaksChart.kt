package si.semafor.app.ui.compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import si.semafor.app.R
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.mojelektro.DailyPeak
import si.semafor.app.mojelektro.UsageAnalysis
import si.semafor.app.ui.compose.theme.AppColors
import java.time.LocalDate

private val PeaksLeft = 34.dp

/**
 * The highest 15-minute interval of every day as a share of the agreed power of its block, with the 90 % (dotted
 * yellow) and 100 % (dashed white) lines. Bars above 100 % are red, from 90 % yellow, otherwise in the colour of the
 * block the peak happened in. When the agreed power is not known the bars show kW instead. Tapping a day reports it
 * ([onDayClick]); the highlighted day is [selectedDate].
 */
@Composable
fun DailyPeaksChart(
    peaks: List<DailyPeak>,
    selectedDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val click by rememberUpdatedState(onDayClick)
    val percentMode = peaks.isNotEmpty() && peaks.all { it.utilization != null }
    val summary = stringResource(
        R.string.chart_peaks,
        peaks.size,
        peaks.count { it.overCount > 0 },
        peaks.count { it.overCount == 0 && it.nearCount > 0 }
    )
    val locale = currentLocale
    val axisColor = AppColors.TextSecondary
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = summary }
            .pointerInput(peaks) {
                detectTapGestures { pos ->
                    if (peaks.isEmpty()) return@detectTapGestures
                    val left = PeaksLeft.toPx()
                    click(peaks[UsageAnalysis.indexAt(pos.x, left, size.width - left, peaks.size)].date)
                }
            }
    ) {
        if (peaks.isEmpty()) return@Canvas
        val left = PeaksLeft.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val width = size.width - left
        val values = peaks.map { if (percentMode) it.utilization!! * 100 else it.peak.kw }
        val max = if (percentMode) maxOf(values.max(), 110.0) * 1.08 else maxOf(values.max(), 0.1) * 1.15
        fun y(v: Double) = (bottom - (v / max) * (bottom - top)).toFloat()
        val slot = width / peaks.size
        val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
        val grid = axisColor.copy(alpha = 0.25f)

        drawLine(grid, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        val ticks = if (percentMode) listOf(90.0, 100.0) else listOf(max / 1.15 / 2, max / 1.15)
        for (t in ticks) {
            val layout = measurer.measure(if (percentMode) "${t.toInt()} %" else fmt(t, 1, locale), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 4.dp.toPx(), y(t) - layout.size.height / 2f))
        }
        if (!percentMode) {
            val unit = measurer.measure("kW", labelStyle)
            drawText(unit, topLeft = Offset(0f, top - unit.size.height / 2f))
        }

        val labelEvery = if (peaks.size > 10) 5 else 1
        peaks.forEachIndexed { i, p ->
            val v = values[i]
            val color = when {
                p.overCount > 0 -> AppColors.Over
                p.nearCount > 0 -> AppColors.Near
                else -> p.peak.blockId?.let(::blockColor) ?: UnknownColor
            }
            val selected = p.date == selectedDate
            val x = left + i * slot + slot * 0.18f
            val w = slot * 0.64f
            drawRoundRect(
                color.copy(alpha = if (selectedDate == null || selected) 1f else 0.6f),
                Offset(x, y(v)), Size(w, bottom - y(v)),
                CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            if (selected) {
                drawRect(ink, Offset(x, bottom + 1.dp.toPx()), Size(w, 2.dp.toPx()))
            }
            if (i % labelEvery == 0 || i == peaks.lastIndex) {
                val layout = measurer.measure("${p.date.dayOfMonth}.", labelStyle)
                val cx = left + i * slot + slot / 2
                drawText(
                    layout,
                    topLeft = Offset((cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), bottom + 4.dp.toPx())
                )
            }
        }
        if (percentMode) {
            drawLine(
                AppColors.Near.copy(alpha = 0.9f), Offset(left, y(90.0)), Offset(size.width, y(90.0)),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx()))
            )
            drawLine(
                ink, Offset(left, y(100.0)), Offset(size.width, y(100.0)),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
        }
    }
}

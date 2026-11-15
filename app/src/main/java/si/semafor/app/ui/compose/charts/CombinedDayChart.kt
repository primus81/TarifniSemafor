package si.semafor.app.ui.compose.charts

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import si.semafor.app.R
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.currentLocale
import si.semafor.app.SloTime
import si.semafor.app.mojelektro.DayProfile
import si.semafor.app.mojelektro.PowerLevel
import si.semafor.app.mojelektro.UsageAnalysis
import si.semafor.app.ui.compose.theme.AppColors

private val ChartLeft = 34.dp

/**
 * The combined chart of one day: the official URO block schedule as coloured bands and a strip, the user's own
 * 15-minute average power (Moj Elektro) as bars in the colour of their block, and the agreed power of each block as a
 * dashed step line (90 % of it dotted). Touch or drag across the chart to inspect an interval.
 *
 * Performance: [selected] is read only in the draw phase, so scrubbing redraws the canvas without recomposing it;
 * everything that does not depend on the selection (bars, bands, labels) is built once per size and day in
 * [drawWithCache].
 */
@Composable
fun CombinedDayChart(
    profile: DayProfile,
    selected: () -> Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val points = profile.intervals
    val segments = remember(profile) { UsageAnalysis.blockSegments(profile) }
    val locale = currentLocale
    val lang = LocalLang.current
    val peakPoint = profile.peakIndex?.let { points[it] }
    val peakText = peakPoint?.let {
        stringResource(R.string.chart_combined_peak, fmt(it.kw, 2, locale), SloTime.formatHm(it.startMillis))
    } ?: ""
    val summary = stringResource(
        R.string.chart_combined,
        SloTime.formatDate(points.firstOrNull()?.startMillis ?: 0L, lang),
        peakText,
        points.count { it.level == PowerLevel.OVER }.let { pluralStringResource(R.plurals.chart_over_quarters, it, it) }
    )
    // Theme colours, read here (the drawing below is not composable).
    val axisColor = AppColors.TextSecondary
    val ink = MaterialTheme.colorScheme.onSurface

    // Selects the interval under x; a light tick when the finger enters another block or reaches the day's peak.
    fun selectAt(x: Float, left: Float, width: Float) {
        if (points.isEmpty()) return
        val index = UsageAnalysis.indexAt(x, left, width, points.size)
        val previous = currentSelected()
        if (index == previous) return
        if (points[index].blockId != points.getOrNull(previous)?.blockId || index == profile.peakIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        currentOnSelect(index)
    }

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics { contentDescription = summary }
            .pointerInput(profile) {
                val left = ChartLeft.toPx()
                detectTapGestures { selectAt(it.x, left, size.width - left) }
            }
            .pointerInput(profile) {
                val left = ChartLeft.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { selectAt(it.x, left, size.width - left) }
                ) { change, _ ->
                    // Consumed, so the pager around the screen does not turn the page while scrubbing.
                    change.consume()
                    selectAt(change.position.x, left, size.width - left)
                }
            }
            .drawWithCache {
                val left = ChartLeft.toPx()
                val top = 14.dp.toPx()
                val stripHeight = 8.dp.toPx()
                val bottom = size.height - stripHeight - 22.dp.toPx()
                val width = size.width - left
                val count = points.size.coerceAtLeast(1)
                val slot = width / count
                val maxKw = maxOf(
                    points.maxOfOrNull { it.kw } ?: 0.0,
                    points.mapNotNull { it.agreedKw }.maxOrNull() ?: 0.0,
                    0.1
                ) * 1.15
                fun y(kw: Double) = (bottom - (kw / maxKw) * (bottom - top)).toFloat()

                val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
                val grid = axisColor.copy(alpha = 0.25f)
                val yLabels = listOf(0.5, 1.0).map { f ->
                    val v = maxKw / 1.15 * f
                    Triple(y(v), measurer.measure(fmt(v, 1, locale), labelStyle), v)
                }
                val unit = measurer.measure("kW", labelStyle)
                val hourLabels = points.mapIndexedNotNull { i, p ->
                    val hour = SloTime.hourOfDay(p.startMillis)
                    if (hour % 6 == 0 && SloTime.formatHm(p.startMillis).endsWith(":00")) {
                        i to measurer.measure("$hour h", labelStyle)
                    } else null
                }

                // Agreed power and its 90 % as step lines (one path each, built once).
                val agreedPath = Path()
                val nearPath = Path()
                points.forEachIndexed { i, p ->
                    val kw = p.agreedKw ?: return@forEachIndexed
                    val x0 = left + i * slot
                    agreedPath.moveTo(x0, y(kw)); agreedPath.lineTo(x0 + slot, y(kw))
                    nearPath.moveTo(x0, y(kw * UsageAnalysis.NEAR_RATIO)); nearPath.lineTo(x0 + slot, y(kw * UsageAnalysis.NEAR_RATIO))
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                val dots = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx()))
                val barRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

                onDrawBehind {
                    // Block bands (background) and the block strip under the bars: the URO schedule of the day.
                    for (s in segments) {
                        val color = s.blockId?.let(::blockColor) ?: UnknownColor
                        val x0 = left + s.fromIndex * slot
                        val w = (s.toIndexExclusive - s.fromIndex) * slot
                        drawRect(color.copy(alpha = 0.10f), Offset(x0, top), Size(w, bottom - top))
                        drawRect(color, Offset(x0, bottom + 3.dp.toPx()), Size(w, stripHeight))
                    }
                    // Grid and y axis.
                    drawLine(grid, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
                    for ((yy, layout, _) in yLabels) {
                        drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 0.5.dp.toPx())
                        drawText(layout, topLeft = Offset(left - layout.size.width - 4.dp.toPx(), yy - layout.size.height / 2f))
                    }
                    drawText(unit, topLeft = Offset(0f, top - unit.size.height / 2f))

                    val sel = currentSelected().coerceIn(0, (points.size - 1).coerceAtLeast(0))
                    // Bars in the colour of their block; the selected one fully opaque.
                    points.forEachIndexed { i, p ->
                        val color = p.blockId?.let(::blockColor) ?: UnknownColor
                        val yy = y(p.kw)
                        drawRoundRect(
                            color.copy(alpha = if (i == sel) 1f else 0.78f),
                            Offset(left + i * slot + slot * 0.12f, yy),
                            Size(slot * 0.76f, bottom - yy),
                            barRadius
                        )
                        // Above the agreed power: a red cap makes the excess visible in every block colour.
                        if (p.level == PowerLevel.OVER) {
                            val capTop = y(p.kw)
                            val capBottom = y(p.agreedKw ?: p.kw)
                            drawRect(AppColors.Over, Offset(left + i * slot + slot * 0.12f, capTop), Size(slot * 0.76f, capBottom - capTop))
                        }
                    }
                    drawPath(nearPath, AppColors.Near.copy(alpha = 0.9f), style = Stroke(1.dp.toPx(), pathEffect = dots))
                    drawPath(agreedPath, ink, style = Stroke(1.5.dp.toPx(), pathEffect = dash))

                    for ((i, layout) in hourLabels) {
                        val cx = left + i * slot
                        val x = (cx - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                        drawText(layout, topLeft = Offset(x, bottom + stripHeight + 6.dp.toPx()))
                    }

                    // Peak marker.
                    profile.peakIndex?.let { i ->
                        drawCircle(ink, 3.dp.toPx(), Offset(left + i * slot + slot / 2, y(points[i].kw) - 6.dp.toPx()))
                    }

                    // Scrub cursor.
                    if (points.isNotEmpty()) {
                        val p = points[sel]
                        val cx = left + sel * slot + slot / 2
                        drawLine(ink.copy(alpha = 0.9f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.5.dp.toPx())
                        drawCircle(ink, 5.dp.toPx(), Offset(cx, y(p.kw)))
                        drawCircle(p.blockId?.let(::blockColor) ?: UnknownColor, 3.dp.toPx(), Offset(cx, y(p.kw)))
                    }
                }
            }
    )
}

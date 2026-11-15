package si.semafor.app.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import si.semafor.app.SemaforIconState
import si.semafor.app.ui.compose.charts.blockColor

/** The lightning bolt of the app (the outline of the earlier logo), in a 100 x 100 box. */
private const val BOLT = "M58,8 L28,55 L47,55 L35,92 L75,43 L54,43 L70,8 Z"

/**
 * The app's logo: a ring of five arcs in the official colours of the time blocks (like a clock face of the five
 * blocks) around a lightning bolt. The arc of the block in force now is bright and thicker, the others are dimmed;
 * the bolt takes the colour of that block. Without verified data all arcs are dimmed and the bolt is neutral.
 * A change of block glides over (colour and arc width), so the logo visibly "switches" with the time blocks.
 *
 * An original design of this app: it does not use or imitate the uro.si logo.
 */
@Composable
fun SemaforLogo(blockId: Int?, modifier: Modifier = Modifier) {
    val state = SemaforIconState.forBlock(blockId)
    val active = if (state.lit) state.blockId else null
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val boltColor by animateColorAsState(active?.let(::blockColor) ?: neutral.copy(alpha = 0.6f), tween(400), label = "bolt")
    val emphasis = (1..5).map { b ->
        animateFloatAsState(
            if (b == active) 1f else 0f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "arc$b"
        )
    }
    val colors = (1..5).map { blockColor(it) }
    val bolt = remember { PathParser().parsePathString(BOLT).toPath() }

    Canvas(modifier) {
        val d = size.minDimension
        val base = d * 0.085f
        val radius = d / 2 - base * 1.4f
        val center = Offset(size.width / 2, size.height / 2)
        val sweep = 72f
        val gap = 12f
        for (i in 0 until 5) {
            val e = emphasis[i].value
            val width = base * (1f + 0.55f * e)
            // Clockwise from the top: block 1 ... block 5.
            drawArc(
                color = colors[i].copy(alpha = 0.32f + 0.68f * e),
                startAngle = -90f + i * sweep + gap / 2,
                sweepAngle = sweep - gap,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
        }
        // The bolt fills the inner circle (about 62 % of the ring's diameter).
        val boltSize = radius * 2 * 0.62f
        translate(center.x - boltSize / 2, center.y - boltSize / 2) {
            scale(boltSize / 100f, pivot = Offset.Zero) {
                drawPath(bolt, boltColor)
                drawPath(bolt, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 3f))
            }
        }
    }
}

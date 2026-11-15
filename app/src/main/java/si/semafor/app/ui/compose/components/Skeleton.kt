package si.semafor.app.ui.compose.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.semafor.app.R

/**
 * A soft light band that sweeps across a placeholder while content loads. With animations switched off in the
 * phone's settings the placeholder stays still.
 */
fun Modifier.shimmer(): Modifier = composed {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    if (!animate) return@composed this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )
    // Read in the draw phase only: the sweep never recomposes the placeholder.
    drawWithContent {
        drawContent()
        val x = size.width * progress.value
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - size.width / 2, 0f),
                end = Offset(x + size.width / 2, size.height)
            )
        )
    }
}

/** A rounded grey block of a skeleton. */
@Composable
fun Bone(height: Dp, modifier: Modifier = Modifier, width: Dp? = null, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(
        (if (width == null) modifier.fillMaxWidth() else modifier.width(width))
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            .shimmer()
    )
}

/** The home screen while the first data loads: the shapes of the block card, the status line and the cards. */
@Composable
fun HomeSkeleton() {
    val loading = stringResource(R.string.home_loading_title)
    Column(
        Modifier.clearAndSetSemantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        Bone(height = 168.dp, shape = RoundedCornerShape(24.dp))
        Row(Modifier.padding(top = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Bone(width = 200.dp, height = 12.dp)
                Spacer(Modifier.height(6.dp))
                Bone(width = 140.dp, height = 12.dp)
            }
            Bone(height = 30.dp, modifier = Modifier.size(30.dp), width = 30.dp, shape = CircleShape)
        }
        Spacer(Modifier.height(14.dp))
        Bone(height = 64.dp, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(28.dp))
        Bone(width = 160.dp, height = 16.dp)
        repeat(3) {
            Spacer(Modifier.height(8.dp))
            Bone(height = 48.dp, shape = RoundedCornerShape(16.dp))
        }
    }
}

/** The usage page while readings load: summary card, the hero chart and a secondary card. */
@Composable
fun UsageSkeleton() {
    Column {
        Bone(height = 150.dp, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(12.dp))
        Bone(height = 300.dp, shape = RoundedCornerShape(20.dp))
        Spacer(Modifier.height(12.dp))
        Bone(height = 180.dp, shape = RoundedCornerShape(16.dp))
    }
}

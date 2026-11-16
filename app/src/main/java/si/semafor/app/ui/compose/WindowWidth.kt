package si.semafor.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Width of the app window in the Material window size classes: phones in portrait are [COMPACT], phones in landscape,
 * small tablets and unfolded foldables [MEDIUM], large tablets and desktop windows [EXPANDED].
 * Read from the window (not the screen), so split screen and resizable windows get the layout that fits.
 */
enum class WidthClass {
    COMPACT, MEDIUM, EXPANDED;

    /** The side navigation rail replaces the bottom bar from [MEDIUM] on. */
    val useRail: Boolean get() = this != COMPACT

    companion object {
        /** Breakpoints of the Material window size classes (600 dp, 840 dp). */
        fun of(widthDp: Float): WidthClass = when {
            widthDp < 600f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }
    }
}

@Composable
fun currentWidthClass(): WidthClass {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return WidthClass.of(width.value.takeIf { width > 0.dp } ?: 0f)
}

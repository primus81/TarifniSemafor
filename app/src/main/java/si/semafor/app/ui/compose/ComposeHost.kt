package si.semafor.app.ui.compose

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import si.semafor.app.ui.compose.theme.AppTheme

/**
 * Space (in pixels) that a screen must keep clear on the left, right and bottom: the system bars. The host
 * Activity measures it from the window insets, because it also lays out the legacy View screens.
 */
@Immutable
data class ScreenInsets(val leftPx: Int = 0, val rightPx: Int = 0, val bottomPx: Int = 0) {
    companion object {
        val None = ScreenInsets()
    }
}

val LocalScreenInsets = compositionLocalOf { ScreenInsets.None }

/**
 * Wraps a Compose screen into a [ComposeView] that can sit in the Activity's `contentContainer` next to the legacy
 * View screens. The view is cached and detached by the screen switcher, so the composition is kept (scroll position
 * included) until the Activity's lifecycle is destroyed.
 */
fun composeScreen(
    context: Context,
    insets: State<ScreenInsets>,
    content: @Composable () -> Unit
): ComposeView = ComposeView(context).apply {
    layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        CompositionLocalProvider(LocalScreenInsets provides insets.value) {
            AppTheme { content() }
        }
    }
}

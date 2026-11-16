package si.semafor.app.ui.compose.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * [ScreenScaffold] that reloads when it is pulled down. The spinner uses the accent colour on the card surface;
 * a visible "Osveži" button on the screen stays the accessible alternative to the gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableScreen(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    maxWidth: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        ScreenScaffold(maxWidth, content)
    }
}

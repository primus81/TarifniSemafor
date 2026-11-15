package si.semafor.app.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import si.semafor.app.ui.compose.theme.AppColors

/** Titles of the home pages, also the spoken names of the page indicator. */
private val PageTitles = listOf("Tarifni semafor", "Moj semafor porabe")

/**
 * The home screen. Without a Moj Elektro connection it is exactly the single home page. With a connection
 * ([usageEnabled]) the user can swipe to the second page, "Moj semafor porabe"; a small indicator shows where they
 * are, and back on the second page returns to the first. When the connection is removed the pager returns to the
 * first page and shrinks back to one page.
 *
 * [requestedPage] asks the pager to go to a page (the menu entry "Moja poraba"); [onRequestHandled] confirms it.
 * [onPageApproached] reports the page the user is moving to as soon as the swipe aims at it, so its data can start
 * loading before the page has settled.
 */
@Composable
fun HomePager(
    usageEnabled: Boolean,
    requestedPage: Int?,
    onRequestHandled: () -> Unit,
    onPageApproached: (Int) -> Unit,
    page: @Composable (Int) -> Unit
) {
    val pageCount = if (usageEnabled) 2 else 1
    val state = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val approached by rememberUpdatedState(onPageApproached)

    LaunchedEffect(usageEnabled) {
        if (!usageEnabled && state.currentPage != 0) state.scrollToPage(0)
    }
    LaunchedEffect(requestedPage, usageEnabled) {
        val target = requestedPage ?: return@LaunchedEffect
        // A request for the usage page waits until the connection has been read (the effect runs again then).
        if (target >= pageCount) return@LaunchedEffect
        state.animateScrollToPage(target)
        onRequestHandled()
    }
    LaunchedEffect(state) {
        snapshotFlow { state.targetPage }.distinctUntilChanged().collect { approached(it) }
    }
    BackHandler(enabled = usageEnabled && state.currentPage > 0) {
        scope.launch { state.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize()) {
        if (usageEnabled) {
            PageIndicator(
                current = state.currentPage,
                count = pageCount,
                onClick = { scope.launch { state.animateScrollToPage(it) } }
            )
        }
        HorizontalPager(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Pages keep their own state (scroll position) while the other one is shown.
            key = { it }
        ) { index -> page(index) }
    }
}

/** Two small dots under the top bar; the current one is a wider pill. Each dot is also a button to its page. */
@Composable
private fun PageIndicator(current: Int, count: Int, onClick: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(16.dp)
            .selectableGroup()
    ) {
        for (i in 0 until count) {
            val active = i == current
            val width by animateDpAsState(if (active) 18.dp else 6.dp, tween(200), label = "dot")
            val color by animateColorAsState(
                if (active) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.45f),
                tween(200),
                label = "dotColor"
            )
            // 32 dp touch area around a 6 dp dot; Compose extends it to the 48 dp minimum.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 32.dp, height = 16.dp)
                    .clickable(role = Role.Tab) { onClick(i) }
                    .semantics {
                        selected = active
                        contentDescription = "Stran ${i + 1} od $count: ${PageTitles[i]}"
                    }
            ) {
                Box(
                    Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

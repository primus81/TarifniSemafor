package si.semafor.app.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The three main destinations as swipeable pages: Domov, Semafor porabe, Nastavitve. The bottom bar and the swipe are
 * two views of the same [state]: swiping moves the selected tab, tapping a tab animates the pager.
 *
 * [onPageApproached] reports a page as soon as a swipe aims at it, so its data starts loading before it settles.
 * Back on another page returns to the home page.
 */
@Composable
fun MainPager(state: PagerState, onPageApproached: (Int) -> Unit, page: @Composable (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val approached by rememberUpdatedState(onPageApproached)
    LaunchedEffect(state) {
        snapshotFlow { state.targetPage }.distinctUntilChanged().collect { approached(it) }
    }
    BackHandler(enabled = state.currentPage > 0) {
        scope.launch { state.animateScrollToPage(0) }
    }
    HorizontalPager(
        state = state,
        modifier = Modifier.fillMaxSize(),
        // Pages keep their own state (scroll position) while another one is shown.
        key = { it },
        beyondViewportPageCount = 0
    ) { index -> page(index) }
}

package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import si.semafor.app.NewsItem
import si.semafor.app.NewsResult
import si.semafor.app.NewsUiState
import si.semafor.app.SloTime
import si.semafor.app.ui.compose.components.RefreshButton
import si.semafor.app.ui.compose.components.RefreshableScreen
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.StateCard
import si.semafor.app.ui.compose.theme.AppTheme

/** The "Novice" screen: the AGEN-RS news feed (stored news when offline). Pull down or "Osveži" reloads it. */
@Composable
fun NewsScreen(
    state: NewsUiState,
    pullRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPullRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    RefreshableScreen(refreshing = pullRefreshing, onRefresh = onPullRefresh) {
        when (state) {
            NewsUiState.Loading -> {
                ScreenHeader(title = "Novice", onBack = onBack)
                StateCard("Nalagam novice …")
            }

            is NewsUiState.Loaded -> {
                val result = state.result
                ScreenHeader(
                    title = "Novice",
                    subtitle = if (result.isOffline) {
                        "Agencija za energijo · Shranjene novice (brez povezave)"
                    } else {
                        "Agencija za energijo"
                    },
                    onBack = onBack
                )
                val items = result.items
                if (items.isNullOrEmpty()) {
                    StateCard("Novic ni mogoče naložiti.", null, "Preveri internetno povezavo.")
                } else {
                    for (item in items) NewsCard(item, onOpenUrl)
                }
                RefreshButton(onRefresh)
                Text(
                    text = "Vir: Agencija za energijo (agen-rs.si)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onOpenUrl: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .clickable(enabled = item.link.isNotEmpty(), onClickLabel = "odpri povezavo") { onOpenUrl(item.link) }
            .padding(14.dp)
    ) {
        if (item.title.isNotEmpty()) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.dateMillis > 0L) {
            Text(
                text = SloTime.formatDate(item.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (item.description.isNotEmpty()) {
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun NewsScreenPreview() {
    AppTheme {
        NewsScreen(
            state = NewsUiState.Loaded(
                NewsResult(
                    listOf(NewsItem("Nov tarifni sistem", "Kratek povzetek novice.", "https://www.agen-rs.si", 0L)),
                    isOffline = true
                )
            ),
            pullRefreshing = false,
            onBack = {},
            onRefresh = {},
            onPullRefresh = {},
            onOpenUrl = {}
        )
    }
}

package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import si.semafor.app.R
import si.semafor.app.Tips
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.BodyLineHeight

/**
 * The "Nasveti" screen. Static content (see [Tips]); the host only supplies two callbacks, so the screen knows
 * nothing about the Activity.
 */
@Composable
fun TipsScreen(onNavigateHome: () -> Unit, onOpenUrl: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)

    ScreenScaffold {
        ScreenHeader(title = stringResource(R.string.tips_title), onBack = onNavigateHome)

        Text(
            text = stringResource(R.string.tips_intro),
            style = body,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )

        for ((title, text) in Tips.ALL) {
            InfoCard {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(text = stringResource(text), style = body, color = colors.onSurfaceVariant)
            }
        }

        Text(
            text = stringResource(R.string.tips_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        LinkText(stringResource(R.string.tips_more)) { onOpenUrl(Tips.MORE_URL) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun TipsScreenPreview() {
    AppTheme { TipsScreen(onNavigateHome = {}, onOpenUrl = {}) }
}

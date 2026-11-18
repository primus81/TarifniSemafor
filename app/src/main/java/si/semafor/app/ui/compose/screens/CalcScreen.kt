package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import si.semafor.app.Links
import si.semafor.app.R
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.NetworkCalculator
import si.semafor.app.ui.compose.components.PrimaryButton
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.BodyLineHeight

private const val HELP_EMAIL = "primerjalnik@agen-rs.si"

/**
 * The "Kalkulator" screen: the quick local network-charge estimate on top, below it the information about the
 * Energy Agency comparison tool and its external link. The host supplies callbacks only (no Activity reference).
 */
@Composable
fun CalcScreen(
    onNavigateHome: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSendMail: (String) -> Unit,
    billSection: (@Composable (si.semafor.app.ui.compose.components.BillInputs) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall

    @Composable
    fun InfoSection(title: String, text: String, extra: @Composable () -> Unit = {}) {
        InfoCard {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(text = text, style = body, color = colors.onSurface)
            extra()
        }
    }

    ScreenScaffold {
        ScreenHeader(
            title = stringResource(R.string.calc_title),
            subtitle = stringResource(R.string.calc_subtitle),
            onBack = onNavigateHome
        )

        // Native quick estimate first; the existing Energy Agency comparison stays below it.
        NetworkCalculator(billSection)

        Text(
            text = stringResource(R.string.calc_compare_heading),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 6.dp)
                .semantics { heading() }
        )

        InfoSection(stringResource(R.string.calc_what_title), stringResource(R.string.calc_what_text))
        InfoSection(stringResource(R.string.calc_how_title), stringResource(R.string.calc_how_text))
        InfoSection(stringResource(R.string.calc_offers_title), stringResource(R.string.calc_offers_text))
        InfoSection(stringResource(R.string.calc_personal_title), stringResource(R.string.calc_personal_text)) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                LinkText(stringResource(R.string.calc_terms)) { onOpenUrl(Links.CALC_TERMS) }
                LinkText(stringResource(R.string.calc_info)) { onOpenUrl(Links.CALC_INFO) }
            }
        }

        PrimaryButton(stringResource(R.string.calc_open)) { onOpenUrl(Links.CALC_URL) }

        Text(
            text = stringResource(R.string.calc_opens_in_browser),
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        val helpLabel = stringResource(R.string.calc_help)
        Text(
            text = buildAnnotatedString {
                append(helpLabel)
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "mail",
                        styles = TextLinkStyles(
                            SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
                        )
                    ) { onSendMail(HELP_EMAIL) }
                ) { append(HELP_EMAIL) }
            },
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp)
        )

        Text(
            text = stringResource(R.string.calc_summary_note),
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun CalcScreenPreview() {
    AppTheme { CalcScreen(onNavigateHome = {}, onOpenUrl = {}, onSendMail = {}) }
}

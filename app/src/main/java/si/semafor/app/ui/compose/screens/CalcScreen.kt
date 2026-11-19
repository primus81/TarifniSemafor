package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
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
    ScreenScaffold {
        ScreenHeader(
            title = stringResource(R.string.calc_title),
            subtitle = stringResource(R.string.calc_subtitle),
            onBack = onNavigateHome
        )
        CalcContent(onOpenUrl, onSendMail, billSection)
    }
}

/**
 * The calculator part of "Cene elektrike in omrežnine": the in-app network charge and whole-bill estimate, then the
 * Energy Agency's comparison of offers (electricity and gas), which opens in the browser. Its description is behind
 * the "i" button.
 */
@Composable
fun ColumnScope.CalcContent(
    onOpenUrl: (String) -> Unit,
    onSendMail: (String) -> Unit,
    billSection: (@Composable (si.semafor.app.ui.compose.components.BillInputs) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall

    // Native quick estimate first; the Energy Agency comparison stays below it.
    NetworkCalculator(billSection)

    si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
        stringResource(R.string.calc_compare_heading),
        stringResource(R.string.calc_compare_heading),
        listOf(
            stringResource(R.string.calc_what_text),
            stringResource(R.string.calc_how_text),
            stringResource(R.string.calc_offers_text),
            stringResource(R.string.calc_personal_text),
            stringResource(R.string.calc_summary_note)
        )
    )
    InfoCard {
        Text(stringResource(R.string.calc_compare_short), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight), color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        PrimaryButton(stringResource(R.string.calc_open)) { onOpenUrl(Links.CALC_URL) }
        Text(
            text = stringResource(R.string.calc_opens_in_browser),
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        LinkText(stringResource(R.string.calc_terms)) { onOpenUrl(Links.CALC_TERMS) }
        LinkText(stringResource(R.string.calc_info)) { onOpenUrl(Links.CALC_INFO) }
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
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun CalcScreenPreview() {
    AppTheme { CalcScreen(onNavigateHome = {}, onOpenUrl = {}, onSendMail = {}) }
}

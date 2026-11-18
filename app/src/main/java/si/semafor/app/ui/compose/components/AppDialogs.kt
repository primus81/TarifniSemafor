package si.semafor.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import si.semafor.app.BlockPalette
import si.semafor.app.Links
import si.semafor.app.R
import si.semafor.app.SloTime
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.theme.AppColors

/**
 * Frame of the app's dialogs: rounded surface with a thin border, a title, scrollable content and the buttons
 * ([buttons]; default: a single "V redu").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit = {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_ok), fontSize = 14.sp, color = AppColors.Accent)
        }
    },
    content: @Composable ColumnScope.() -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    color = AppColors.TextPrimary,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .semantics { heading() }
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    content = content
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    content = buttons
                )
            }
        }
    }
}

@Composable
private fun linkStyle() = TextLinkStyles(SpanStyle(color = AppColors.Accent, textDecoration = TextDecoration.Underline))

/** "Viri podatkov": the official sources of every part of the app, each with a link. */
@Composable
fun SourcesDialog(onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val sources = listOf(
        Triple(R.string.sources_blocks, R.string.sources_blocks_link, Links.URO_URL),
        Triple(R.string.sources_colors, R.string.sources_colors_link, BlockPalette.tableFor(SloTime.today().year).source),
        Triple(R.string.sources_news, R.string.sources_news_link, Links.NEWS_PAGE),
        Triple(R.string.sources_calc, R.string.sources_calc_link, Links.CALC_URL),
        Triple(R.string.sources_tariff, R.string.sources_tariff_link, Links.TARIFF_INFO),
        Triple(R.string.sources_icons, R.string.sources_icons_link, Links.MATERIAL_SYMBOLS),
        Triple(R.string.sources_usage, R.string.sources_usage_link, Links.MOJ_ELEKTRO_API_DOCS)
    ).map { (label, link, url) -> Triple(stringResource(label), stringResource(link), url) }
    val style = linkStyle()
    val text = buildAnnotatedString {
        sources.forEachIndexed { i, (label, linkLabel, url) ->
            if (i > 0) append("\n\n")
            append("• $label ")
            withLink(LinkAnnotation.Url(url, style) { onOpenUrl(url) }) { append(linkLabel) }
        }
    }
    AppDialog(stringResource(R.string.sources_title), onDismiss) {
        Text(text, fontSize = 14.sp, color = AppColors.TextPrimary)
    }
}

/** "O aplikaciji": disclaimer, developer, version and the privacy policy. */
@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val style = linkStyle()
    val lang = LocalLang.current
    val developer = stringResource(R.string.about_developer)
    val andText = stringResource(R.string.about_and)
    val privacy = stringResource(R.string.about_privacy_policy)
    val privacySpoken = stringResource(R.string.common_link_suffix, privacy)
    AppDialog(stringResource(R.string.about_title), onDismiss) {
        // The app's name: "Tarifni semafor" as the title, "moja poraba" smaller below it.
        Text(stringResource(R.string.app_name_main), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = AppColors.TextPrimary)
        Text(
            stringResource(R.string.app_name_sub),
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            text = stringResource(R.string.about_disclaimer),
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = buildAnnotatedString {
                append(developer)
                withLink(LinkAnnotation.Url(Links.DEVELOPER, style) { onOpenUrl(Links.DEVELOPER) }) { append("primus") }
                append(andText)
                withLink(LinkAnnotation.Url(Links.ANTHROPIC, style) { onOpenUrl(Links.ANTHROPIC) }) { append("Claude") }
            },
            fontSize = 15.sp,
            color = AppColors.TextPrimary
        )
        Text(
            text = stringResource(R.string.about_version, version),
            fontSize = 15.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (!Links.PRIVACY_POLICY.contains("REPLACE_WITH")) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp)
                    .clickable { onOpenUrl(Links.privacyPolicy(lang)) }
                    .semantics { contentDescription = privacySpoken },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = privacy,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

/**
 * Shown once before the system asks for the notification permission (Android 13+), so the user knows what the
 * permission is for. [onAllow] continues to the system dialog; [onLater] (or dismissing) leaves notifications off
 * until the user switches them on in Settings.
 */
@Composable
fun NotificationIntroDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.notif_intro_title),
        onDismiss = onLater,
        buttons = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.notif_intro_later), fontSize = 14.sp, color = AppColors.TextSecondary)
            }
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.notif_intro_allow), fontSize = 14.sp, color = AppColors.Accent)
            }
        }
    ) {
        Text(
            text = stringResource(R.string.notif_intro_text),
            fontSize = 15.sp,
            color = AppColors.TextPrimary
        )
    }
}

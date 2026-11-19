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

/**
 * "O aplikaciji": name, version, a short disclaimer, the developer (primus, linked to primush.online), the legal pages
 * and the copyright line with the current year at the bottom.
 */
@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val style = linkStyle()
    val lang = LocalLang.current
    val developer = stringResource(R.string.about_developer)
    AppDialog(stringResource(R.string.about_title), onDismiss) {
        // The app's name: "Tarifni semafor" as the title, "moja poraba" smaller below it.
        Text(stringResource(R.string.app_name_main), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = AppColors.TextPrimary)
        Text(stringResource(R.string.app_name_sub), fontSize = 13.sp, color = AppColors.TextSecondary)
        Text(
            text = stringResource(R.string.about_version, version),
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        Text(
            text = buildAnnotatedString {
                append(developer)
                withLink(LinkAnnotation.Url(Links.DEVELOPER, style) { onOpenUrl(Links.DEVELOPER) }) { append("primus") }
            },
            fontSize = 15.sp,
            color = AppColors.TextPrimary
        )
        Text(
            text = stringResource(R.string.about_disclaimer),
            fontSize = 12.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        AboutLink(stringResource(R.string.about_privacy_policy)) { onOpenUrl(Links.privacyPolicy(lang)) }
        AboutLink(stringResource(R.string.about_terms)) { onOpenUrl(Links.TERMS) }
        AboutLink(stringResource(R.string.about_disclaimer_link)) { onOpenUrl(Links.DISCLAIMER) }
        // Copyright at the very bottom; the year follows the calendar.
        val year = SloTime.today().year
        Text(
            text = buildAnnotatedString {
                append("© $year ")
                withLink(LinkAnnotation.Url(Links.WEBSITE, style) { onOpenUrl(Links.WEBSITE) }) { append("tarifnisemafor.si") }
            },
            fontSize = 12.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun AboutLink(label: String, onClick: () -> Unit) {
    val spoken = stringResource(R.string.common_link_suffix, label)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline
        )
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

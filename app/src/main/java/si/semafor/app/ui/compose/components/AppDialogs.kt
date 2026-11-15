package si.semafor.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import si.semafor.app.Links
import si.semafor.app.ui.compose.theme.AppColors

/** Frame of the app's dialogs: rounded surface with a thin border, a title, scrollable content and "V redu". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("V redu", fontSize = 14.sp, color = AppColors.Accent) }
                }
            }
        }
    }
}

private fun linkStyle() = TextLinkStyles(SpanStyle(color = AppColors.Accent, textDecoration = TextDecoration.Underline))

/** "Viri podatkov": the official sources of every part of the app, each with a link. */
@Composable
fun SourcesDialog(onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val sources = listOf(
        Triple("Tarifni semafor in časovni bloki:", "Agencija za energijo, uro.si (API)", Links.URO_URL),
        Triple("Novice:", "Agencija za energijo (RSS)", Links.NEWS_PAGE),
        Triple(
            "Kalkulator (Primerjalnik stroškov oskrbe), povzetek in povezava:",
            "Agencija za energijo",
            Links.CALC_URL
        ),
        Triple("Informacije o tarifnem sistemu:", "MojElektro", Links.TARIFF_INFO)
    )
    val text = buildAnnotatedString {
        sources.forEachIndexed { i, (label, linkLabel, url) ->
            if (i > 0) append("\n\n")
            append("• $label ")
            withLink(LinkAnnotation.Url(url, linkStyle()) { onOpenUrl(url) }) { append(linkLabel) }
        }
    }
    AppDialog("Viri podatkov", onDismiss) {
        Text(text, fontSize = 14.sp, color = AppColors.TextPrimary)
    }
}

/** "O aplikaciji": disclaimer, developer, version and the privacy policy. */
@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val developerUrl = "https://bsky.app/profile/primush.bsky.social"
    AppDialog("O aplikaciji", onDismiss) {
        Text(
            text = "Tarifni semafor je neuradna aplikacija. Ni povezana z Agencijo za energijo, SODO ali drugimi operaterji. Podatki so povzeti iz javnih virov (glej Viri podatkov); njihova točnost ni zagotovljena, veljavne podatke objavljajo uradni viri.",
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = buildAnnotatedString {
                append("Razvijalec: ")
                withLink(LinkAnnotation.Url(developerUrl, linkStyle()) { onOpenUrl(developerUrl) }) { append("Primus") }
            },
            fontSize = 15.sp,
            color = AppColors.TextPrimary
        )
        Text(
            text = "Verzija: $version",
            fontSize = 15.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (!Links.PRIVACY_POLICY.contains("REPLACE_WITH")) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp)
                    .clickable { onOpenUrl(Links.PRIVACY_POLICY) }
                    .semantics { contentDescription = "Politika zasebnosti, povezava" },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Politika zasebnosti",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

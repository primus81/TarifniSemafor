package si.semafor.app.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import si.semafor.app.R
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.BodyLineHeight

/**
 * "Tvoja zasebnost": what stays on the phone, how Moj Elektro is protected, no cookies, and the user's choice about
 * usage analytics (off by default). Opens by itself once, at the first start, and any time from Settings.
 *
 * Truthful by design: the app contains no analytics today, so the switch sends nothing; the text says so and does not
 * call the data "anonymous" (an analytics SDK would process identifiers such as an installation ID and the IP address).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(
    analyticsConsent: Boolean,
    onSave: (Boolean) -> Unit,
    onOpenPolicy: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var allowed by rememberSaveable { mutableStateOf(analyticsConsent) }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = { onSave(allowed) },
        sheetState = sheet,
        containerColor = colors.surface
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.privacy_intro),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            Point(AppColors.Export, R.string.privacy_local_title, R.string.privacy_local_text)
            Point(AppColors.Accent, R.string.privacy_keystore_title, R.string.privacy_keystore_text)
            Point(AppColors.NeutralBar, R.string.privacy_cookies_title, R.string.privacy_cookies_text)

            InfoCard {
                Text(stringResource(R.string.privacy_analytics_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(
                    stringResource(R.string.privacy_analytics_text),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                SwitchRow(stringResource(R.string.privacy_analytics_switch), allowed, { allowed = it }, MaterialTheme.typography.bodyLarge.fontSize)
            }

            LinkText(stringResource(R.string.privacy_policy_link), onOpenPolicy)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                // Equal weight for both answers: refusing is as easy as accepting.
                Box(Modifier.weight(1f)) {
                    ActionButton(
                        stringResource(R.string.privacy_decline),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSave(false) }
                    )
                }
                Box(Modifier.weight(1f)) {
                    ActionButton(
                        stringResource(R.string.privacy_save),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSave(allowed) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Point(color: Color, title: Int, text: Int) {
    Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 4.dp, end = 12.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

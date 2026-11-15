package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import si.semafor.app.R
import si.semafor.app.UiText
import si.semafor.app.text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.mojelektro.MojElektroMessage
import si.semafor.app.mojelektro.MojElektroUiState
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.PrimaryButton
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.components.SwitchRow
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.BodyLineHeight

/**
 * The "Moj Elektro" screen (opened from Nastavitve): optional connection of the user's own metering point with a
 * read-only API token. The screen only reports actions through callbacks; [MojElektroUiState] comes from the host.
 *
 * The typed token is kept with remember (NOT rememberSaveable) on purpose: saved instance state is written outside
 * the app's encrypted storage, so the token must never go there. After rotation it has to be pasted again.
 */
@Composable
fun MojElektroScreen(
    state: MojElektroUiState,
    onBack: () -> Unit,
    onConnect: (token: String, eimm: String) -> Unit,
    onVerify: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenMojElektro: () -> Unit,
    onOpenUsage: () -> Unit,
    backDescription: String = stringResource(R.string.me_back_settings)
) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val note = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)

    ScreenScaffold {
        ScreenHeader(
            title = stringResource(R.string.me_title),
            subtitle = stringResource(R.string.me_subtitle),
            onBack = onBack,
            backDescription = backDescription
        )

        InfoCard {
            Text(
                text = stringResource(R.string.me_intro),
                style = body,
                color = colors.onSurface
            )
        }

        SectionHeading(stringResource(R.string.me_privacy_heading))
        InfoCard {
            Text(
                text = stringResource(R.string.me_privacy_text),
                style = body,
                color = colors.onSurface
            )
        }

        SectionHeading(stringResource(R.string.me_delay_heading))
        InfoCard {
            Text(
                text = stringResource(R.string.me_delay_text),
                style = body,
                color = colors.onSurface
            )
        }

        if (state.loaded) {
            val eimm = state.connectedEimm
            if (eimm != null) {
                ConnectedCard(eimm, state.busy, onVerify, onDisconnect)
                PrimaryButton(stringResource(R.string.me_show_usage), onOpenUsage)
            } else {
                ConnectForm(state.busy, onConnect, onOpenMojElektro)
            }
        }

        state.message?.let { StatusMessage(it) }

        Text(
            text = stringResource(R.string.me_not_official),
            style = note,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun ConnectedCard(eimm: String, busy: Boolean, onVerify: () -> Unit, onDisconnect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionHeading(stringResource(R.string.me_connection_heading))
    InfoCard {
        Text(
            text = stringResource(R.string.me_connected, eimm),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
        Spacer(Modifier.height(12.dp))
        ActionButton(stringResource(if (busy) R.string.me_checking else R.string.me_verify)) { if (!busy) onVerify() }
        Spacer(Modifier.height(8.dp))
        ActionButton(stringResource(R.string.me_disconnect)) { if (!busy) onDisconnect() }
        Text(
            text = stringResource(R.string.me_disconnect_note),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ConnectForm(busy: Boolean, onConnect: (String, String) -> Unit, onOpenMojElektro: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val note = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    // Token: remember only (never saved instance state). EIMM is not secret and survives rotation.
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var eimm by rememberSaveable { mutableStateOf("") }

    SectionHeading(stringResource(R.string.me_connect_heading))
    InfoCard {
        FieldLabel(stringResource(R.string.me_token))
        CredentialField(
            value = token,
            onValueChange = { token = it },
            description = stringResource(R.string.me_token),
            placeholder = stringResource(R.string.me_token_placeholder),
            secret = !showToken,
            keyboardType = KeyboardType.Password
        )
        SwitchRow(label = stringResource(R.string.me_show_token), checked = showToken, onCheckedChange = { showToken = it }, fontSize = 14.sp)

        Spacer(Modifier.height(4.dp))
        FieldLabel(stringResource(R.string.me_eimm))
        CredentialField(
            value = eimm,
            onValueChange = { eimm = it },
            description = stringResource(R.string.me_eimm_spoken),
            placeholder = stringResource(R.string.me_eimm_placeholder),
            secret = false,
            keyboardType = KeyboardType.Ascii
        )

        Text(
            text = stringResource(R.string.me_where),
                style = note,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
        LinkText(stringResource(R.string.me_open_portal), onOpenMojElektro)
    }
    PrimaryButton(stringResource(if (busy) R.string.me_checking else R.string.me_save)) {
        if (!busy) onConnect(token, eimm)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/** Single-line input in the style of the calculator fields; [secret] hides the characters. */
@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    placeholder: String,
    secret: Boolean,
    keyboardType: KeyboardType
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val textStyle = TextStyle(fontSize = 15.sp, color = colors.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.onSurface),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        // No suggestions and no learning from what is typed (the token must not end up in the keyboard's memory).
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, colors.outline, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = textStyle.copy(color = colors.onSurfaceVariant))
                }
                inner()
            }
        }
    )
}

@Composable
private fun StatusMessage(message: MojElektroMessage) {
    InfoCard {
        Text(
            text = message.text.text(),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
            // Colours of the app: yellow for warnings (as in Nastavitve), green block colour for success.
            color = if (message.isError) AppColors.Warning else SuccessColor,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

private val SuccessColor = Color(0xFF2E9E4F)

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun MojElektroScreenPreview() {
    AppTheme {
        MojElektroScreen(
            state = MojElektroUiState(
                loaded = true,
                message = MojElektroMessage(UiText.res(R.string.me_err_unauthorized), true)
            ),
            onBack = {},
            onConnect = { _, _ -> },
            onVerify = {},
            onDisconnect = {},
            onOpenMojElektro = {},
            onOpenUsage = {}
        )
    }
}

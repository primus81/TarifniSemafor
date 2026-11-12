package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import si.semafor.app.AppSettings
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.RadioRow
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.components.SwitchRow
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.BodyLineHeight

/** What the Settings screen shows. The host reads it from [AppSettings] and from the system on every visit. */
@Immutable
data class SettingsUiState(
    val blockNotifications: Boolean = true,
    val notificationMode: Int = AppSettings.MODE_ALL,
    val statusIcon: Boolean = true,
    /** Android blocks the notifications of this app (permission refused or switched off in the phone settings). */
    val notificationsBlocked: Boolean = false
)

/**
 * The "Nastavitve" screen: every user-configurable setting of the app. New settings are added here as new rows
 * (and in [AppSettings]). The screen only reports changes through callbacks; the host applies them.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateHome: () -> Unit,
    onBlockNotificationsChange: (Boolean) -> Unit,
    onNotificationModeChange: (Int) -> Unit,
    onStatusIconChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val note = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)

    ScreenScaffold {
        ScreenHeader(
            title = "Nastavitve",
            subtitle = "Obvestila in druge nastavitve aplikacije",
            onBack = onNavigateHome
        )

        // Explain (here, at the top) why no notification will appear when Android blocks them.
        if (state.notificationsBlocked) {
            InfoCard {
                Text(
                    text = "Obvestila so za to aplikacijo v telefonu izklopljena, zato ne prejmeš opozoril. Dovoli jih v nastavitvah telefona.",
                    style = note,
                    color = WarningColor
                )
                Spacer(Modifier.height(8.dp))
                ActionButton("Odpri nastavitve obvestil", onOpenNotificationSettings)
            }
        }

        SectionHeading("Obvestila")
        InfoCard {
            SwitchRow(
                label = "Obvestilo ob menjavi bloka",
                checked = state.blockNotifications,
                onCheckedChange = onBlockNotificationsChange
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outlineVariant)
            )
            Column(modifier = Modifier.selectableGroup()) {
                RadioRow(
                    label = "Ob vsaki menjavi",
                    selected = state.notificationMode == AppSettings.MODE_ALL,
                    enabled = state.blockNotifications,
                    onClick = { onNotificationModeChange(AppSettings.MODE_ALL) },
                    modifier = Modifier.fillMaxWidth()
                )
                RadioRow(
                    label = "Samo ob prehodu v blok 1 ali 2",
                    selected = state.notificationMode == AppSettings.MODE_BLOCKS_1_2,
                    enabled = state.blockNotifications,
                    onClick = { onNotificationModeChange(AppSettings.MODE_BLOCKS_1_2) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "Obvestila se ustvarijo v telefonu. Aplikacija zaradi njih ne pošilja nobenih podatkov. Zaradi varčevanja z baterijo lahko obvestilo zamuja za nekaj minut.",
                style = note,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        SectionHeading("Vrstica stanja")
        InfoCard {
            SwitchRow(
                label = "Ikona trenutnega bloka v vrstici stanja",
                checked = state.statusIcon,
                onCheckedChange = onStatusIconChange
            )
            Text(
                text = "Ob ikoni se prikaže tudi obvestilo z imenom trenutnega bloka in uro naslednje menjave. Android ikone v vrstici stanja vedno nariše enobarvno; barva trenutnega bloka se vidi v obvestilu.",
                style = note,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Colour of warnings (the yellow of the middle tariff block, as everywhere else in the app). */
private val WarningColor = Color(0xFFFDD835)

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            state = SettingsUiState(notificationsBlocked = true),
            onNavigateHome = {},
            onBlockNotificationsChange = {},
            onNotificationModeChange = {},
            onStatusIconChange = {},
            onOpenNotificationSettings = {}
        )
    }
}

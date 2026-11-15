package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.AppSettings
import si.semafor.app.R
import si.semafor.app.ui.compose.components.ActionButton
import si.semafor.app.ui.compose.components.AppDialog
import si.semafor.app.ui.compose.components.Choice
import si.semafor.app.ui.compose.components.ChoiceChips
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.RadioRow
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.components.SectionHeading
import si.semafor.app.ui.compose.components.SwitchRow
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.BodyLineHeight

/** What the Settings screen shows. The host reads it from [AppSettings] and from the system on every visit. */
@Immutable
data class SettingsUiState(
    val themeMode: Int = AppSettings.THEME_SYSTEM,
    val language: String = AppSettings.LANGUAGE_SL,
    val blockNotifications: Boolean = true,
    val notificationMode: Int = AppSettings.MODE_ALL,
    val statusIcon: Boolean = true,
    /** Android blocks the notifications of this app (permission refused or switched off in the phone settings). */
    val notificationsBlocked: Boolean = false,
    /** EIMM of the connected Moj Elektro metering point (optional feature); null = not connected. */
    val mojElektroEimm: String? = null,
    val analyticsConsent: Boolean = false,
    val version: String = ""
)

/** Everything the Settings screen can ask the host to do. */
@Immutable
data class SettingsActions(
    val onThemeChange: (Int) -> Unit = {},
    val onLanguageChange: (String) -> Unit = {},
    val onBlockNotificationsChange: (Boolean) -> Unit = {},
    val onNotificationModeChange: (Int) -> Unit = {},
    val onStatusIconChange: (Boolean) -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    val onOpenMojElektro: () -> Unit = {},
    val onWipeMojElektro: () -> Unit = {},
    val onOpenTips: () -> Unit = {},
    val onOpenPrices: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenPrivacyPolicy: () -> Unit = {},
    val onOpenSources: () -> Unit = {},
    val onOpenAbout: () -> Unit = {}
)

/**
 * The "Nastavitve" tab: every user-configurable setting of the app, grouped as appearance and language, Moj Elektro,
 * notifications, tips and tools, privacy, legal. New settings are added here as new rows (and in [AppSettings]).
 * The screen only reports changes through [actions]; the host applies them.
 */
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    val note = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)
    var confirmWipe by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // Explain (here, at the top) why no notification will appear when Android blocks them.
        if (state.notificationsBlocked) {
            InfoCard {
                Text(text = stringResource(R.string.settings_blocked), style = note, color = AppColors.Warning)
                Spacer(Modifier.height(8.dp))
                ActionButton(stringResource(R.string.settings_open_notification_settings), onClick = actions.onOpenNotificationSettings)
            }
        }

        // ---- Appearance and language ----
        SectionHeading(stringResource(R.string.settings_appearance))
        InfoCard {
            Label(stringResource(R.string.settings_theme))
            ChoiceChips(
                choices = listOf(
                    Choice(AppSettings.THEME_SYSTEM, stringResource(R.string.settings_theme_system)),
                    Choice(AppSettings.THEME_LIGHT, stringResource(R.string.settings_theme_light)),
                    Choice(AppSettings.THEME_DARK, stringResource(R.string.settings_theme_dark))
                ),
                selected = state.themeMode,
                onSelect = actions.onThemeChange
            )
            Text(stringResource(R.string.settings_theme_note), style = note, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Label(stringResource(R.string.settings_language))
            ChoiceChips(
                choices = listOf(
                    Choice(AppSettings.LANGUAGE_SL, stringResource(R.string.settings_language_sl)),
                    Choice(AppSettings.LANGUAGE_EN, stringResource(R.string.settings_language_en))
                ),
                selected = state.language,
                onSelect = actions.onLanguageChange
            )
            Text(stringResource(R.string.settings_language_note), style = note, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        // ---- Moj Elektro ----
        SectionHeading(stringResource(R.string.settings_mojelektro))
        InfoCard {
            val eimm = state.mojElektroEimm
            Text(
                text = if (eimm != null) stringResource(R.string.settings_mojelektro_connected, eimm)
                else stringResource(R.string.settings_mojelektro_not_connected),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurface
            )
            Text(
                stringResource(R.string.settings_mojelektro_security),
                style = note,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                stringResource(if (eimm != null) R.string.settings_mojelektro_manage else R.string.settings_mojelektro_connect),
                onClick = actions.onOpenMojElektro
            )
            if (eimm != null) {
                Spacer(Modifier.height(8.dp))
                ActionButton(stringResource(R.string.settings_mojelektro_wipe), onClick = { confirmWipe = true })
            }
        }

        // ---- Notifications ----
        SectionHeading(stringResource(R.string.settings_notifications))
        InfoCard {
            SwitchRow(
                label = stringResource(R.string.settings_block_notifications),
                checked = state.blockNotifications,
                onCheckedChange = actions.onBlockNotificationsChange
            )
            Column(Modifier.selectableGroup()) {
                RadioRow(
                    label = stringResource(R.string.settings_mode_all),
                    selected = state.notificationMode == AppSettings.MODE_ALL,
                    onClick = { actions.onNotificationModeChange(AppSettings.MODE_ALL) },
                    enabled = state.blockNotifications
                )
                RadioRow(
                    label = stringResource(R.string.settings_mode_1_2),
                    selected = state.notificationMode == AppSettings.MODE_BLOCKS_1_2,
                    onClick = { actions.onNotificationModeChange(AppSettings.MODE_BLOCKS_1_2) },
                    enabled = state.blockNotifications
                )
            }
            Text(stringResource(R.string.settings_notifications_note), style = note, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                label = stringResource(R.string.settings_status_icon),
                checked = state.statusIcon,
                onCheckedChange = actions.onStatusIconChange
            )
            Text(stringResource(R.string.settings_status_note), style = note, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        // ---- Tips and tools ----
        SectionHeading(stringResource(R.string.settings_tools))
        InfoCard {
            NavRow(stringResource(R.string.menu_tips), stringResource(R.string.settings_tips_sub), actions.onOpenTips)
            NavRow(stringResource(R.string.menu_prices), stringResource(R.string.settings_prices_sub), actions.onOpenPrices)
        }

        // ---- Privacy ----
        SectionHeading(stringResource(R.string.settings_privacy))
        InfoCard {
            Text(
                stringResource(
                    R.string.settings_privacy_summary,
                    stringResource(if (state.analyticsConsent) R.string.privacy_analytics_on else R.string.privacy_analytics_off)
                ),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurface
            )
            NavRow(stringResource(R.string.settings_privacy_open), null, actions.onOpenPrivacy)
            NavRow(stringResource(R.string.privacy_policy_link), null, actions.onOpenPrivacyPolicy)
        }

        // ---- Legal and about ----
        SectionHeading(stringResource(R.string.settings_legal))
        InfoCard {
            NavRow(stringResource(R.string.menu_about), stringResource(R.string.settings_disclaimer_sub), actions.onOpenAbout)
            NavRow(stringResource(R.string.menu_sources), null, actions.onOpenSources)
            if (state.version.isNotEmpty()) {
                Text(
                    stringResource(R.string.settings_version, state.version),
                    style = note,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (confirmWipe) {
        AppDialog(
            title = stringResource(R.string.settings_mojelektro_wipe_title),
            onDismiss = { confirmWipe = false },
            buttons = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text(stringResource(R.string.common_cancel), fontSize = 14.sp, color = AppColors.Accent)
                }
                TextButton(onClick = {
                    confirmWipe = false
                    actions.onWipeMojElektro()
                }) {
                    Text(stringResource(R.string.settings_mojelektro_wipe_confirm), fontSize = 14.sp, color = AppColors.Over)
                }
            }
        ) {
            Text(
                stringResource(R.string.settings_mojelektro_wipe_text),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** A row that opens another screen or a dialog: title, optional grey line and a chevron. */
@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(180f)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F5F8)
@Composable
private fun SettingsScreenPreview() {
    AppTheme(dark = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            SettingsScreen(
                state = SettingsUiState(notificationsBlocked = true, mojElektroEimm = "3-1234567", version = "2.2.2"),
                actions = SettingsActions()
            )
        }
    }
}

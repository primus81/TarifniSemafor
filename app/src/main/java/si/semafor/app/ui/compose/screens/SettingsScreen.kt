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
    /** Reminders of the planner, warnings and quiet hours. */
    val reminders: si.semafor.app.reminders.ReminderSettings.Values? = null,
    /** Notification when the price lists change. */
    val priceChanges: Boolean = true,
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
    val onOpenEnergyPrice: () -> Unit = {},
    val onOpenPlanner: () -> Unit = {},
    val onRemindersChange: (si.semafor.app.reminders.ReminderSettings.Values) -> Unit = {},
    val onPriceChangesChange: (Boolean) -> Unit = {},
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
        si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
            stringResource(R.string.settings_appearance),
            stringResource(R.string.settings_appearance),
            listOf(stringResource(R.string.settings_theme_note), stringResource(R.string.settings_language_note))
        )
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
        }

        // ---- Moj Elektro ----
        si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
            stringResource(R.string.settings_mojelektro),
            stringResource(R.string.settings_mojelektro),
            listOf(stringResource(R.string.settings_mojelektro_security))
        )
        InfoCard {
            val eimm = state.mojElektroEimm
            Text(
                text = if (eimm != null) stringResource(R.string.settings_mojelektro_connected, eimm)
                else stringResource(R.string.settings_mojelektro_not_connected),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = colors.onSurface
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
        si.semafor.app.ui.compose.components.SectionHeadingWithInfo(
            stringResource(R.string.settings_notifications),
            stringResource(R.string.settings_notifications),
            listOf(
                stringResource(R.string.settings_notifications_note),
                stringResource(R.string.settings_status_note),
                stringResource(R.string.set_rem_note)
            )
        )
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
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                label = stringResource(R.string.settings_status_icon),
                checked = state.statusIcon,
                onCheckedChange = actions.onStatusIconChange
            )
            state.reminders?.let { r -> ReminderSettingsBlock(r, state.priceChanges, actions) }
        }

        // ---- Tips and tools ----
        SectionHeading(stringResource(R.string.settings_tools))
        InfoCard {
            NavRow(stringResource(R.string.menu_tips), stringResource(R.string.settings_tips_sub), actions.onOpenTips)
            NavRow(stringResource(R.string.menu_planner), stringResource(R.string.menu_planner_sub), actions.onOpenPlanner)
            NavRow(stringResource(R.string.menu_energy_price), stringResource(R.string.menu_energy_price_sub), actions.onOpenEnergyPrice)
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
                state = SettingsUiState(notificationsBlocked = true, mojElektroEimm = "3-1234567", version = "2.2.3"),
                actions = SettingsActions()
            )
        }
    }
}

/** Separate switches for the reminders, how early they come and the quiet hours. */
@Composable
private fun ReminderSettingsBlock(
    r: si.semafor.app.reminders.ReminderSettings.Values,
    priceChanges: Boolean,
    actions: SettingsActions
) {
    val colors = MaterialTheme.colorScheme
    val change = actions.onRemindersChange
    Spacer(Modifier.height(10.dp))
    SwitchRow(stringResource(R.string.set_rem_devices), r.devices, { change(r.copy(devices = it)) })
    SwitchRow(stringResource(R.string.set_rem_cheaper), r.cheaper, { change(r.copy(cheaper = it)) })
    SwitchRow(stringResource(R.string.set_rem_power), r.power, { change(r.copy(power = it)) })
    SwitchRow(stringResource(R.string.set_rem_prices), priceChanges, actions.onPriceChangesChange)
    Text(stringResource(R.string.set_rem_lead), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    ChoiceChips(
        si.semafor.app.reminders.ReminderSettings.LEAD_CHOICES.map { Choice(it, stringResource(R.string.set_rem_lead_value, it)) },
        r.leadMin,
        { change(r.copy(leadMin = it)) }
    )
    SwitchRow(stringResource(R.string.set_rem_quiet), r.quiet, { change(r.copy(quiet = it)) })
    if (r.quiet) {
        Text(stringResource(R.string.set_rem_quiet_from), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.padding(bottom = 4.dp))
        ChoiceChips(
            si.semafor.app.reminders.ReminderSettings.QUIET_FROM_CHOICES.map { Choice(it, stringResource(R.string.set_rem_hour, it)) },
            r.quietFrom,
            { change(r.copy(quietFrom = it)) }
        )
        Text(stringResource(R.string.set_rem_quiet_to), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        ChoiceChips(
            si.semafor.app.reminders.ReminderSettings.QUIET_TO_CHOICES.map { Choice(it, stringResource(R.string.set_rem_hour, it)) },
            r.quietTo,
            { change(r.copy(quietTo = it)) }
        )
    }
}

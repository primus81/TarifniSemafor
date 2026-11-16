package si.semafor.app

import android.content.Context
import androidx.core.content.edit

/**
 * All user-configurable settings in one place (SharedPreferences file "settings"), with their defaults.
 * New settings are added here and in the Settings screen.
 *
 * Notifications are ON by default: a user who never touched the switch has no stored value and gets the default.
 * Android 13+ still needs the user's permission, which the app asks for once (see MainActivity).
 */
object AppSettings {
    private const val PREFS = "settings"

    // The key keeps its old name so that the choice of existing users is preserved.
    private const val KEY_BLOCK_NOTIFICATIONS = "notif_enabled"
    private const val KEY_NOTIFICATION_MODE = "notif_mode"
    private const val KEY_STATUS_ICON = "status_icon_enabled"
    private const val KEY_PERMISSION_DENIED = "notif_perm_denied"
    private const val KEY_PERMISSION_ASKED = "notif_perm_asked"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_PRIVACY_SEEN = "privacy_notice_seen"
    private const val KEY_ANALYTICS_CONSENT = "analytics_consent"

    /** Follow the phone's light / dark setting (default). */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /** Languages of the app (BCP 47 tags). Slovenian is the default. */
    const val LANGUAGE_SL = "sl"
    const val LANGUAGE_EN = "en"

    /** Notify at every block change. */
    const val MODE_ALL = 0

    /** Notify only when block 1 or 2 begins. */
    const val MODE_BLOCKS_1_2 = 1

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * State that belongs to this phone only and must not travel with a backup (the backup contains only "settings",
     * see res/xml/data_extraction_rules.xml): whether this phone's notification permission was asked or refused.
     * Restored on a new phone, it would stop the app from asking there.
     */
    private const val DEVICE_PREFS = "device_state"
    private const val KEY_DEVICE_MIGRATED = "migrated_from_settings"

    private fun devicePrefs(ctx: Context): android.content.SharedPreferences {
        val device = ctx.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        if (!device.getBoolean(KEY_DEVICE_MIGRATED, false)) {
            // Versions up to 2.2.3 kept the permission state in "settings": moved once, then removed there.
            val old = prefs(ctx)
            device.edit {
                if (old.contains(KEY_PERMISSION_DENIED)) putBoolean(KEY_PERMISSION_DENIED, old.getBoolean(KEY_PERMISSION_DENIED, false))
                if (old.contains(KEY_PERMISSION_ASKED)) putBoolean(KEY_PERMISSION_ASKED, old.getBoolean(KEY_PERMISSION_ASKED, false))
                putBoolean(KEY_DEVICE_MIGRATED, true)
            }
            old.edit { remove(KEY_PERMISSION_DENIED); remove(KEY_PERMISSION_ASKED) }
        }
        return device
    }

    /** Notification when a new time block begins. Default: on. */
    fun blockNotificationsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BLOCK_NOTIFICATIONS, true)

    fun setBlockNotificationsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_BLOCK_NOTIFICATIONS, enabled).apply()
    }

    /** [MODE_ALL] (default) or [MODE_BLOCKS_1_2]. */
    fun notificationMode(ctx: Context): Int = prefs(ctx).getInt(KEY_NOTIFICATION_MODE, MODE_ALL)

    fun setNotificationMode(ctx: Context, mode: Int) {
        prefs(ctx).edit().putInt(KEY_NOTIFICATION_MODE, mode).apply()
    }

    /** Icon of the current block in the status bar (an ongoing notification). Default: on. */
    fun statusIconEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_STATUS_ICON, true)

    fun setStatusIconEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_STATUS_ICON, enabled).apply()
    }

    /** True if the notification permission was refused (the system dialog is not shown again on its own). */
    fun permissionDenied(ctx: Context): Boolean = devicePrefs(ctx).getBoolean(KEY_PERMISSION_DENIED, false)

    fun setPermissionDenied(ctx: Context, denied: Boolean) {
        devicePrefs(ctx).edit { putBoolean(KEY_PERMISSION_DENIED, denied) }
    }

    /** True once the app has asked for the notification permission by itself (first start with the defaults). */
    fun permissionAsked(ctx: Context): Boolean = devicePrefs(ctx).getBoolean(KEY_PERMISSION_ASKED, false)

    fun setPermissionAsked(ctx: Context, asked: Boolean) {
        devicePrefs(ctx).edit { putBoolean(KEY_PERMISSION_ASKED, asked) }
    }

    /** [THEME_SYSTEM] (default), [THEME_LIGHT] or [THEME_DARK]. */
    fun themeMode(ctx: Context): Int = prefs(ctx).getInt(KEY_THEME, THEME_SYSTEM)

    fun setThemeMode(ctx: Context, mode: Int) {
        prefs(ctx).edit { putInt(KEY_THEME, mode) }
    }

    /**
     * Language chosen in the app ([LANGUAGE_SL] or [LANGUAGE_EN]); null = never chosen (Slovenian is used).
     * Mirrors the per-app locale, so contexts without an Activity (widget, notifications) can use it as well.
     */
    fun language(ctx: Context): String? = prefs(ctx).getString(KEY_LANGUAGE, null)

    fun setLanguage(ctx: Context, tag: String) {
        prefs(ctx).edit { putString(KEY_LANGUAGE, tag) }
    }

    /** The privacy notice has been shown once (it opens by itself only at the first start). */
    fun privacyNoticeSeen(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PRIVACY_SEEN, false)

    fun setPrivacyNoticeSeen(ctx: Context, seen: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_PRIVACY_SEEN, seen) }
    }

    /**
     * The user's choice about usage analytics. Default: off. The app contains NO analytics today, so this choice sends
     * nothing anywhere; it only records the answer for a possible future version.
     */
    fun analyticsConsent(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ANALYTICS_CONSENT, false)

    fun setAnalyticsConsent(ctx: Context, allowed: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_ANALYTICS_CONSENT, allowed) }
    }

    /** The block-change alarm is needed if either feature wants to be told when a block ends. */
    fun alarmWanted(ctx: Context): Boolean = alarmWanted(blockNotificationsEnabled(ctx), statusIconEnabled(ctx))
}

/** Pure rule behind [AppSettings.alarmWanted] (kept separate so it can be unit tested). */
internal fun alarmWanted(blockNotifications: Boolean, statusIcon: Boolean): Boolean =
    blockNotifications || statusIcon

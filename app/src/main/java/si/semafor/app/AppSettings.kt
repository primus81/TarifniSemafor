package si.semafor.app

import android.content.Context

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

    /** Notify at every block change. */
    const val MODE_ALL = 0

    /** Notify only when block 1 or 2 begins. */
    const val MODE_BLOCKS_1_2 = 1

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
    fun permissionDenied(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PERMISSION_DENIED, false)

    fun setPermissionDenied(ctx: Context, denied: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PERMISSION_DENIED, denied).apply()
    }

    /** True once the app has asked for the notification permission by itself (first start with the defaults). */
    fun permissionAsked(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PERMISSION_ASKED, false)

    fun setPermissionAsked(ctx: Context, asked: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PERMISSION_ASKED, asked).apply()
    }

    /** The block-change alarm is needed if either feature wants to be told when a block ends. */
    fun alarmWanted(ctx: Context): Boolean = alarmWanted(blockNotificationsEnabled(ctx), statusIconEnabled(ctx))
}

/** Pure rule behind [AppSettings.alarmWanted] (kept separate so it can be unit tested). */
internal fun alarmWanted(blockNotifications: Boolean, statusIcon: Boolean): Boolean =
    blockNotifications || statusIcon

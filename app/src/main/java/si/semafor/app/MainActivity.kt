package si.semafor.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import si.semafor.app.mojelektro.MojElektroViewModel
import si.semafor.app.mojelektro.UsageViewModel
import si.semafor.app.ui.compose.AppShell
import si.semafor.app.ui.compose.LocalLang
import si.semafor.app.ui.compose.MainBottomBar
import si.semafor.app.ui.compose.MainPager
import si.semafor.app.ui.compose.MainTab
import si.semafor.app.ui.compose.MenuEntry
import si.semafor.app.ui.compose.components.AboutDialog
import si.semafor.app.ui.compose.components.PrivacySheet
import si.semafor.app.ui.compose.components.SourcesDialog
import si.semafor.app.ui.compose.screens.CalcScreen
import si.semafor.app.ui.compose.screens.HomeScreen
import si.semafor.app.ui.compose.screens.MojElektroScreen
import si.semafor.app.ui.compose.screens.NewsScreen
import si.semafor.app.ui.compose.screens.PricesScreen
import si.semafor.app.ui.compose.screens.SettingsActions
import si.semafor.app.ui.compose.screens.SettingsScreen
import si.semafor.app.ui.compose.screens.SettingsUiState
import si.semafor.app.ui.compose.screens.TipsScreen
import si.semafor.app.ui.compose.screens.UsageDashboardScreen
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.LocalAppPalette
import si.semafor.app.ui.compose.theme.isDarkTheme

/** MAIN = the three tabs (Domov, Semafor porabe, Nastavitve); the others are detail screens opened from them. */
enum class Screen { MAIN, CALC, NEWS, PRICES, TIPS, MOJELEKTRO }

/** Dialogs opened from the menu or from Settings. */
private enum class AppDialog { SOURCES, ABOUT }

/**
 * The only Activity. It hosts the Compose UI, navigates between the screens and does what needs an Activity
 * (permissions, intents, receivers, theme and language). The screens themselves are stateless composables; their data
 * lives in ViewModels ([HomeViewModel], [NewsViewModel], [MojElektroViewModel], [UsageViewModel]).
 *
 * AppCompatActivity: needed for the per-app language (Slovenian / English) on Android 12 and older.
 */
class MainActivity : AppCompatActivity() {
    private val homeVm: HomeViewModel by viewModels()
    private val newsVm: NewsViewModel by viewModels()

    // Optional Moj Elektro connection (screen state; the token never becomes part of it).
    private val mojElektroVm: MojElektroViewModel by viewModels()

    // "Semafor porabe": the user's own readings (second tab).
    private val usageVm: UsageViewModel by viewModels()

    private val screen = mutableStateOf(Screen.MAIN)
    private val dialog = mutableStateOf<AppDialog?>(null)

    // A tab the main pager should go to (menu, Moj Elektro screen, notification); null = none.
    private val tabRequest = mutableStateOf<Int?>(null)

    // What the Settings tab shows; refreshed whenever the tab is shown and when the app comes back.
    private val settingsState = mutableStateOf(SettingsUiState())

    // Light / dark / system, applied instantly (the neutral colours fade over; the block colours never change).
    private val themeMode = mutableIntStateOf(AppSettings.THEME_SYSTEM)

    // The privacy notice: opens by itself at the first start, later from Settings.
    private val showPrivacy = mutableStateOf(false)

    // Wall clock of the UI (top bar, countdowns); ticks once per second while the Activity is resumed.
    private val nowMillis = mutableLongStateOf(System.currentTimeMillis())

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            // Aligned to the next full second, so the clock and the countdowns change together.
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    // Device clock or time zone changed: drop throttles and re-validate the shown data.
    private val timeChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            homeVm.onClockChanged()
            tick()
        }
    }

    /** Why the notification permission is being asked: a switch the user turned on, or the first start. */
    private enum class PermissionRequest { BLOCK_NOTIFICATIONS, STATUS_ICON, FIRST_RUN }

    // Survives recreation (saved instance state), so the answer is applied to the switch that asked for it.
    private var pendingPermissionRequest: PermissionRequest? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingPermissionRequest ?: return@registerForActivityResult
            pendingPermissionRequest = null
            onNotificationPermissionResult(request, granted)
        }

    private companion object {
        // Saved instance state: the screen that was open (rotation, language change, process death) and a pending
        // permission request.
        const val KEY_SCREEN = "screen"
        const val KEY_PERMISSION_REQUEST = "permission_request"

        // Material "fast out, slow in" easing of the screen transitions.
        val MotionEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        // Only shown if PackageManager cannot report the version (keep in step with versionName).
        const val FALLBACK_VERSION = "2.2.3"

        const val PAGE_HOME = 0
        const val PAGE_USAGE = 1
        const val PAGE_SETTINGS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+: the user may have changed the app language in the phone's settings.
        AppLanguage.syncFromSystem(this)
        pendingPermissionRequest = savedInstanceState?.getString(KEY_PERMISSION_REQUEST)
            ?.let { name -> PermissionRequest.entries.firstOrNull { it.name == name } }
        themeMode.intValue = AppSettings.themeMode(this)
        showPrivacy.value = !AppSettings.privacyNoticeSeen(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val lang = AppLanguage.current(this)
        setContent {
            val dark = isDarkTheme(themeMode.intValue)
            AppTheme(dark = dark) {
                val bg = LocalAppPalette.current.bgScreen
                // System bar icons and the window behind Compose follow the theme.
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                    window.setBackgroundDrawable(bg.toArgb().toDrawable())
                }
                CompositionLocalProvider(LocalLang provides lang) { AppContent() }
            }
        }

        SemaforWidget.schedule(this)
        maybeAskNotificationPermissionOnFirstRun()

        // Back to the screen that was open before the Activity was recreated.
        savedInstanceState?.getString(KEY_SCREEN)
            ?.let { name -> Screen.entries.firstOrNull { it.name == name } }
            ?.let { showScreen(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN, screen.value.name)
        pendingPermissionRequest?.let { outState.putString(KEY_PERMISSION_REQUEST, it.name) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        // The user may have changed the notification settings of the phone while the app was in the background.
        refreshSettingsState()
        mojElektroVm.refreshConnection()
        // A catch-up after an offline period (SyncRecovery) refreshes the home screen straight away.
        SyncRecovery.uiListener = { info -> runOnUiThread { homeVm.onRecovered(info) } }
        ContextCompat.registerReceiver(
            this,
            timeChangeReceiver,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Stored data first (if it can still be trusted), then a refresh if the last one is older than a minute.
        homeVm.onScreenResumed()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
        SyncRecovery.uiListener = null
        try {
            unregisterReceiver(timeChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Not registered.
        }
    }

    private fun tick() {
        // Always derived from the real current instant, never from a manually incremented counter.
        val now = System.currentTimeMillis()
        nowMillis.longValue = now
        if (screen.value == Screen.MAIN) homeVm.onTick(now)
    }

    @Composable
    private fun AppContent() {
        val homeState by homeVm.state.collectAsStateWithLifecycle()
        val connection by mojElektroVm.state.collectAsStateWithLifecycle()
        val current by screen
        val connectedEimm = connection.connectedEimm
        val blockId = (homeState as? HomeUiState.Content)?.info?.now?.id
        LaunchedEffect(connection.loaded, connectedEimm) {
            if (connection.loaded) usageVm.onConnection(connectedEimm)
            refreshSettingsState()
        }

        // Detail screens go back to the tabs (the pager keeps the tab they were opened from).
        BackHandler(enabled = current != Screen.MAIN) { showScreen(Screen.MAIN) }

        val pager = rememberPagerState { MainTab.entries.size }
        val scope = rememberCoroutineScope()
        LaunchedEffect(tabRequest.value) {
            val target = tabRequest.value ?: return@LaunchedEffect
            pager.animateScrollToPage(target)
            tabRequest.value = null
        }

        AppShell(
            nowMillis = nowMillis.longValue,
            blockId = blockId,
            menuEntries = ::menuEntries,
            bottomBar = if (current == Screen.MAIN) {
                {
                    MainBottomBar(selected = pager.targetPage, blockId = blockId) { tab ->
                        scope.launch { pager.animateScrollToPage(tab) }
                    }
                }
            } else null
        ) {
            // Each screen keeps its saveable state (scroll position, typed values, the selected tab) while another
            // one is shown and when the Activity is recreated.
            val saveable = rememberSaveableStateHolder()
            val slide = with(LocalDensity.current) { 12.dp.roundToPx() }
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn(tween(220, easing = MotionEasing)) +
                        slideInVertically(tween(220, easing = MotionEasing)) { slide })
                        .togetherWith(fadeOut(tween(140, easing = MotionEasing)))
                },
                label = "screen"
            ) { s ->
                saveable.SaveableStateProvider(s.name) {
                    if (s == Screen.MAIN) {
                        MainPager(
                            state = pager,
                            onPageApproached = { page ->
                                when (page) {
                                    // Stored data first; Moj Elektro is asked only for what is missing or too old.
                                    PAGE_USAGE -> if (connectedEimm != null) usageVm.onScreenShown()
                                    PAGE_SETTINGS -> refreshSettingsState()
                                }
                            }
                        ) { page ->
                            when (page) {
                                PAGE_HOME -> HomePage(homeState)
                                PAGE_USAGE -> UsagePage(connectedEimm != null)
                                else -> SettingsPage()
                            }
                        }
                    } else {
                        DetailScreen(s, homeState)
                    }
                }
            }
        }

        when (dialog.value) {
            AppDialog.SOURCES -> SourcesDialog(onDismiss = { dialog.value = null }, onOpenUrl = ::openUrl)
            AppDialog.ABOUT -> AboutDialog(appVersion(), onDismiss = { dialog.value = null }, onOpenUrl = ::openUrl)
            null -> Unit
        }

        if (showPrivacy.value) {
            PrivacySheet(
                analyticsConsent = settingsState.value.analyticsConsent,
                onSave = { allowed ->
                    AppSettings.setAnalyticsConsent(this, allowed)
                    AppSettings.setPrivacyNoticeSeen(this, true)
                    showPrivacy.value = false
                    refreshSettingsState()
                },
                onOpenPolicy = { openUrl(Links.PRIVACY_POLICY) }
            )
        }
    }

    @Composable
    private fun HomePage(homeState: HomeUiState) {
        val pulling by homeVm.pullRefreshing.collectAsStateWithLifecycle()
        HomeScreen(
            state = homeState,
            nowMillis = nowMillis.longValue,
            pullRefreshing = pulling,
            onRefresh = { homeVm.refresh(force = true) },
            onPullRefresh = homeVm::pullRefresh,
            onOpenDonation = ::openDonationUrl
        )
    }

    @Composable
    private fun UsagePage(connected: Boolean) {
        val state by usageVm.state.collectAsStateWithLifecycle()
        val range by usageVm.rangeDays.collectAsStateWithLifecycle()
        val refreshing by usageVm.refreshing.collectAsStateWithLifecycle()
        UsageDashboardScreen(
            connected = connected,
            state = state,
            rangeDays = range,
            ranges = UsageViewModel.RANGES,
            refreshing = refreshing,
            onRefresh = usageVm::refresh,
            onRangeChange = usageVm::setRange,
            onManageConnection = { showScreen(Screen.MOJELEKTRO) }
        )
    }

    @Composable
    private fun SettingsPage() {
        SettingsScreen(
            state = settingsState.value,
            actions = SettingsActions(
                onThemeChange = ::onThemeChange,
                onLanguageChange = ::onLanguageChange,
                onBlockNotificationsChange = ::onBlockNotificationsChange,
                onNotificationModeChange = ::onNotificationModeChange,
                onStatusIconChange = ::onStatusIconChange,
                onOpenNotificationSettings = ::openNotificationSettings,
                onOpenMojElektro = { showScreen(Screen.MOJELEKTRO) },
                onWipeMojElektro = mojElektroVm::disconnect,
                onOpenTips = { showScreen(Screen.TIPS) },
                onOpenPrices = { showScreen(Screen.PRICES) },
                onOpenPrivacy = { showPrivacy.value = true },
                onOpenPrivacyPolicy = { openUrl(Links.PRIVACY_POLICY) },
                onOpenSources = { dialog.value = AppDialog.SOURCES },
                onOpenAbout = { dialog.value = AppDialog.ABOUT }
            )
        )
    }

    @Composable
    private fun DetailScreen(s: Screen, homeState: HomeUiState) {
        val back = { showScreen(Screen.MAIN) }
        when (s) {
            Screen.CALC -> CalcScreen(onNavigateHome = back, onOpenUrl = ::openUrl, onSendMail = ::openMail)
            Screen.NEWS -> {
                val news by newsVm.state.collectAsStateWithLifecycle()
                val pulling by newsVm.pullRefreshing.collectAsStateWithLifecycle()
                NewsScreen(
                    state = news,
                    pullRefreshing = pulling,
                    onBack = back,
                    onRefresh = { newsVm.load() },
                    onPullRefresh = { newsVm.load(fromPull = true) },
                    onOpenUrl = ::openUrl
                )
            }

            Screen.PRICES -> PricesScreen(
                list = PriceLists.current(),
                currentBlockId = (homeState as? HomeUiState.Content)?.info?.now?.id,
                onBack = back,
                onOpenUrl = ::openUrl
            )

            Screen.TIPS -> TipsScreen(onNavigateHome = back, onOpenUrl = ::openUrl)
            Screen.MOJELEKTRO -> {
                val state by mojElektroVm.state.collectAsStateWithLifecycle()
                MojElektroScreen(
                    state = state,
                    onBack = back,
                    onConnect = mojElektroVm::connect,
                    onVerify = mojElektroVm::verify,
                    onDisconnect = mojElektroVm::disconnect,
                    onOpenMojElektro = { openUrl(Links.MOJ_ELEKTRO) },
                    onOpenUsage = { openTab(PAGE_USAGE) },
                    backDescription = getString(R.string.common_back_home)
                )
            }

            Screen.MAIN -> Unit
        }
    }

    /** The main menu (tools and information); built each time it opens. The tabs are in the bottom bar. */
    private fun menuEntries(): List<MenuEntry> = buildList {
        add(MenuEntry(getString(R.string.menu_calculator), getString(R.string.menu_calculator_sub)) { showScreen(Screen.CALC) })
        add(MenuEntry(getString(R.string.menu_prices)) { showScreen(Screen.PRICES) })
        add(MenuEntry(getString(R.string.menu_tips)) { showScreen(Screen.TIPS) })
        add(MenuEntry(getString(R.string.menu_tariff_info)) { openUrl(Links.TARIFF_INFO) })
        add(MenuEntry(getString(R.string.menu_news)) { showScreen(Screen.NEWS) })
        add(MenuEntry(getString(R.string.menu_sources), dividerBefore = true) { dialog.value = AppDialog.SOURCES })
        add(MenuEntry(getString(R.string.menu_about)) { dialog.value = AppDialog.ABOUT })
    }

    /** Shows the tabs at [page] (Domov, Semafor porabe, Nastavitve). */
    private fun openTab(page: Int) {
        showScreen(Screen.MAIN)
        tabRequest.value = page
    }

    /** Switches to [s] and runs what that screen needs on every visit. */
    private fun showScreen(s: Screen) {
        screen.value = s
        if (s == Screen.NEWS) newsVm.load()
        if (s == Screen.MAIN) tick()
    }

    private fun onThemeChange(mode: Int) {
        AppSettings.setThemeMode(this, mode)
        themeMode.intValue = mode
        refreshSettingsState()
    }

    private fun onLanguageChange(tag: String) {
        if (tag == AppLanguage.current(this)) return
        // The Activity is recreated in the new language; the open tab and screen are kept (saved state).
        AppLanguage.set(this, tag)
        // Channels and the indicator get the new language at once (no Activity needed for them).
        applyNotificationSettings()
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: FALLBACK_VERSION
    } catch (e: Exception) {
        FALLBACK_VERSION
    }

    private fun onNotificationPermissionResult(request: PermissionRequest, granted: Boolean) {
        if (granted) {
            AppSettings.setPermissionDenied(this, false)
            // The switch the user turned on is on now.
            if (request == PermissionRequest.BLOCK_NOTIFICATIONS) AppSettings.setBlockNotificationsEnabled(this, true)
            if (request == PermissionRequest.STATUS_ICON) AppSettings.setStatusIconEnabled(this, true)
        } else {
            // Remembered so the Settings screen can explain it; the system dialog is never shown again on its own.
            AppSettings.setPermissionDenied(this, true)
            // A switch the user tried to turn on goes back to off. At the first start the defaults stay as they are.
            if (request == PermissionRequest.BLOCK_NOTIFICATIONS) AppSettings.setBlockNotificationsEnabled(this, false)
            if (request == PermissionRequest.STATUS_ICON) AppSettings.setStatusIconEnabled(this, false)
            toast(R.string.toast_allow_notifications)
        }
        applyNotificationSettings()
        refreshSettingsState()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    // Only called after needsNotificationPermission(), which is false below Android 13.
    @SuppressLint("InlinedApi")
    private fun requestNotificationPermission(request: PermissionRequest) {
        pendingPermissionRequest = request
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Notifications are on by default, so the first start asks for the permission once (Android 13+). If the user
     * refuses, nothing is switched off: the Settings screen explains why no notification appears.
     */
    private fun maybeAskNotificationPermissionOnFirstRun() {
        val wanted = AppSettings.blockNotificationsEnabled(this) || AppSettings.statusIconEnabled(this)
        if (wanted && needsNotificationPermission() &&
            !AppSettings.permissionAsked(this) && !AppSettings.permissionDenied(this)
        ) {
            AppSettings.setPermissionAsked(this, true)
            requestNotificationPermission(PermissionRequest.FIRST_RUN)
        }
    }

    /** Makes the alarm, the channels and the status bar icon follow the current settings. */
    private fun applyNotificationSettings() {
        val info = Repo.cached(this)
        Notifier.createChannel(this)
        Notifier.scheduleNext(this, info)
        StatusIndicator.update(this, info)
    }

    /** Reads the settings and the state of the system notifications into what the Settings screen shows. */
    private fun refreshSettingsState() {
        val blockOn = AppSettings.blockNotificationsEnabled(this)
        val statusOn = AppSettings.statusIconEnabled(this)
        val canPostAny = Notifier.canPost(this) || Notifier.canPost(this, StatusIndicator.CHANNEL_ID)
        settingsState.value = SettingsUiState(
            themeMode = AppSettings.themeMode(this),
            language = AppLanguage.current(this),
            blockNotifications = blockOn,
            notificationMode = AppSettings.notificationMode(this),
            statusIcon = statusOn,
            notificationsBlocked = !canPostAny && (blockOn || statusOn || AppSettings.permissionDenied(this)),
            // From the connection screen's state: no decryption on the main thread.
            mojElektroEimm = mojElektroVm.state.value.connectedEimm,
            analyticsConsent = AppSettings.analyticsConsent(this),
            version = appVersion()
        )
    }

    private fun onBlockNotificationsChange(enabled: Boolean) {
        if (enabled && needsNotificationPermission()) {
            // Switched on only once the user has allowed it (see onNotificationPermissionResult).
            requestNotificationPermission(PermissionRequest.BLOCK_NOTIFICATIONS)
            return
        }
        AppSettings.setBlockNotificationsEnabled(this, enabled)
        applyNotificationSettings()
        refreshSettingsState()
    }

    private fun onStatusIconChange(enabled: Boolean) {
        if (enabled && needsNotificationPermission()) {
            requestNotificationPermission(PermissionRequest.STATUS_ICON)
            return
        }
        AppSettings.setStatusIconEnabled(this, enabled)
        applyNotificationSettings()
        refreshSettingsState()
    }

    private fun onNotificationModeChange(mode: Int) {
        AppSettings.setNotificationMode(this, mode)
        applyNotificationSettings()
        refreshSettingsState()
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Exception) {
            toast(R.string.toast_notification_settings_failed)
        }
    }

    /** Opens the mail app for [address]. */
    private fun openMail(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()))
        } catch (e: Exception) {
            toast(R.string.toast_link_failed)
        }
    }

    private fun openUrl(url: String) {
        try {
            val uri = url.toUri()
            // Web pages only: a link coming from external content (news feed) must never start other schemes (intent:, file:, content:, ...).
            require(uri.scheme?.lowercase() in setOf("http", "https")) { "Unsupported link scheme" }
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: Exception) {
            toast(R.string.toast_link_failed)
        }
    }

    private fun openDonationUrl(url: String) {
        if (url.contains("PLACEHOLDER")) {
            toast(R.string.toast_donation_not_set)
            return
        }
        openUrl(url)
    }

    private fun toast(textRes: Int) {
        Toast.makeText(this, textRes, Toast.LENGTH_SHORT).show()
    }
}

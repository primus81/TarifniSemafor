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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.semafor.app.mojelektro.MojElektroRepository
import si.semafor.app.mojelektro.MojElektroViewModel
import si.semafor.app.mojelektro.UsageViewModel
import si.semafor.app.ui.compose.AppShell
import si.semafor.app.ui.compose.HomePager
import si.semafor.app.ui.compose.MenuEntry
import si.semafor.app.ui.compose.components.AboutDialog
import si.semafor.app.ui.compose.components.SourcesDialog
import si.semafor.app.ui.compose.screens.CalcScreen
import si.semafor.app.ui.compose.screens.HomeScreen
import si.semafor.app.ui.compose.screens.MojElektroScreen
import si.semafor.app.ui.compose.screens.NewsScreen
import si.semafor.app.ui.compose.screens.PricesScreen
import si.semafor.app.ui.compose.screens.SettingsScreen
import si.semafor.app.ui.compose.screens.SettingsUiState
import si.semafor.app.ui.compose.screens.TipsScreen
import si.semafor.app.ui.compose.screens.UsageDashboardScreen
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.AppTheme

enum class Screen { HOME, CALC, NEWS, PRICES, TIPS, SETTINGS, MOJELEKTRO }

/** Dialogs opened from the menu. */
private enum class AppDialog { SOURCES, ABOUT }

/**
 * The only Activity. It hosts the Compose UI, navigates between the screens and does what needs an Activity
 * (permissions, intents, receivers). The screens themselves are stateless composables; their data lives in
 * ViewModels ([HomeViewModel], [NewsViewModel], [MojElektroViewModel], [UsageViewModel]).
 */
class MainActivity : ComponentActivity() {
    private val homeVm: HomeViewModel by viewModels()
    private val newsVm: NewsViewModel by viewModels()

    // Optional Moj Elektro connection (screen state; the token never becomes part of it).
    private val mojElektroVm: MojElektroViewModel by viewModels()

    // "Moj semafor porabe": the user's own readings (second home page, only with a Moj Elektro connection).
    private val usageVm: UsageViewModel by viewModels()

    // A page the home pager should go to (menu entry "Moja poraba"); null = none.
    private val homePageRequest = mutableStateOf<Int?>(null)

    // Where back from the Moj Elektro screen leads: the screen it was opened from.
    private var mojElektroReturn = Screen.SETTINGS

    private val screen = mutableStateOf(Screen.HOME)
    private val dialog = mutableStateOf<AppDialog?>(null)

    // What the Settings screen shows; refreshed whenever the screen is opened and when the app comes back.
    private val settingsState = mutableStateOf(SettingsUiState())

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
        // Saved instance state: the screen that was open (rotation, process death) and a pending permission request.
        const val KEY_SCREEN = "screen"
        const val KEY_PERMISSION_REQUEST = "permission_request"

        // Material "fast out, slow in" easing of the screen transitions.
        val MotionEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        // Only shown if PackageManager cannot report the version (keep in step with versionName).
        const val FALLBACK_VERSION = "2.2.2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPermissionRequest = savedInstanceState?.getString(KEY_PERMISSION_REQUEST)
            ?.let { name -> PermissionRequest.entries.firstOrNull { it.name == name } }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(AppColors.BgScreen.toArgb().toDrawable())
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent { AppTheme { AppContent() } }

        SemaforWidget.schedule(this)
        maybeAskNotificationPermissionOnFirstRun()

        // Back to the screen that was open before the Activity was recreated.
        savedInstanceState?.getString(KEY_SCREEN)
            ?.let { name -> Screen.entries.firstOrNull { it.name == name } }
            ?.takeIf { it != Screen.HOME }
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
        if (screen.value == Screen.HOME) homeVm.onTick(now)
    }

    @Composable
    private fun AppContent() {
        val homeState by homeVm.state.collectAsStateWithLifecycle()
        val connection by mojElektroVm.state.collectAsStateWithLifecycle()
        val current by screen
        val connectedEimm = connection.connectedEimm
        LaunchedEffect(connection.loaded, connectedEimm) {
            if (connection.loaded) usageVm.onConnection(connectedEimm)
        }
        val blockId = (homeState as? HomeUiState.Content)?.info?.now?.id

        // Back goes to the home screen from every other screen (from Moj Elektro to Nastavitve, where it is opened);
        // on the home screen the system default applies (leave the app, with the predictive back animation).
        BackHandler(enabled = current != Screen.HOME) {
            showScreen(if (current == Screen.MOJELEKTRO) mojElektroReturn else Screen.HOME)
        }

        AppShell(nowMillis = nowMillis.longValue, blockId = blockId, menuEntries = ::menuEntries) {
            // Each screen keeps its saveable state (scroll position, typed values) while another one is shown and
            // when the Activity is recreated.
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
                saveable.SaveableStateProvider(s.name) { ScreenContent(s, homeState, connectedEimm != null) }
            }
        }

        when (dialog.value) {
            AppDialog.SOURCES -> SourcesDialog(onDismiss = { dialog.value = null }, onOpenUrl = ::openUrl)
            AppDialog.ABOUT -> AboutDialog(appVersion(), onDismiss = { dialog.value = null }, onOpenUrl = ::openUrl)
            null -> Unit
        }
    }

    @Composable
    private fun ScreenContent(s: Screen, homeState: HomeUiState, usageEnabled: Boolean) {
        val goHome = { showScreen(Screen.HOME) }
        when (s) {
            Screen.HOME -> HomePager(
                usageEnabled = usageEnabled,
                requestedPage = homePageRequest.value,
                onRequestHandled = { homePageRequest.value = null },
                // Stored data first; Moj Elektro is asked only for what is missing or too old.
                onPageApproached = { if (it == 1) usageVm.onScreenShown() }
            ) { page ->
                if (page == 0) HomePage(homeState) else UsagePage()
            }

            Screen.CALC -> CalcScreen(onNavigateHome = goHome, onOpenUrl = ::openUrl, onSendMail = ::openMail)
            Screen.NEWS -> {
                val news by newsVm.state.collectAsStateWithLifecycle()
                val pulling by newsVm.pullRefreshing.collectAsStateWithLifecycle()
                NewsScreen(
                    state = news,
                    pullRefreshing = pulling,
                    onBack = goHome,
                    onRefresh = { newsVm.load() },
                    onPullRefresh = { newsVm.load(fromPull = true) },
                    onOpenUrl = ::openUrl
                )
            }

            Screen.PRICES -> PricesScreen(
                list = PriceLists.current(),
                currentBlockId = (homeState as? HomeUiState.Content)?.info?.now?.id,
                onBack = goHome,
                onOpenUrl = ::openUrl
            )

            Screen.TIPS -> TipsScreen(onNavigateHome = goHome, onOpenUrl = ::openUrl)
            Screen.SETTINGS -> SettingsScreen(
                state = settingsState.value,
                onNavigateHome = goHome,
                onBlockNotificationsChange = ::onBlockNotificationsChange,
                onNotificationModeChange = ::onNotificationModeChange,
                onStatusIconChange = ::onStatusIconChange,
                onOpenNotificationSettings = ::openNotificationSettings,
                onOpenMojElektro = { openMojElektro(from = Screen.SETTINGS) }
            )

            Screen.MOJELEKTRO -> {
                val state by mojElektroVm.state.collectAsStateWithLifecycle()
                MojElektroScreen(
                    state = state,
                    onBack = { showScreen(mojElektroReturn) },
                    onConnect = mojElektroVm::connect,
                    onVerify = mojElektroVm::verify,
                    onDisconnect = mojElektroVm::disconnect,
                    onOpenMojElektro = { openUrl(Links.MOJ_ELEKTRO) },
                    onOpenUsage = ::openUsage,
                    backDescription = if (mojElektroReturn == Screen.HOME) "Nazaj na Moj semafor porabe" else "Nazaj na nastavitve"
                )
            }

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
    private fun UsagePage() {
        val state by usageVm.state.collectAsStateWithLifecycle()
        val range by usageVm.rangeDays.collectAsStateWithLifecycle()
        val refreshing by usageVm.refreshing.collectAsStateWithLifecycle()
        UsageDashboardScreen(
            state = state,
            rangeDays = range,
            ranges = UsageViewModel.RANGES,
            refreshing = refreshing,
            onRefresh = usageVm::refresh,
            onRangeChange = usageVm::setRange,
            onManageConnection = { openMojElektro(from = Screen.HOME) }
        )
    }

    /** The main menu; built each time it opens. */
    private fun menuEntries(): List<MenuEntry> = buildList {
        add(MenuEntry("Kalkulator", "Primerjalnik stroškov oskrbe") { showScreen(Screen.CALC) })
        add(MenuEntry("Cenik omrežnine") { showScreen(Screen.PRICES) })
        add(MenuEntry("Nasveti") { showScreen(Screen.TIPS) })
        add(MenuEntry("Vse o tarifnem sistemu") { openUrl(Links.TARIFF_INFO) })
        add(MenuEntry("Novice") { showScreen(Screen.NEWS) })
        // Only with a Moj Elektro connection (optional feature); cheap check, no decryption.
        if (MojElektroRepository(this@MainActivity).isConnected()) {
            add(MenuEntry("Moja poraba", "Moj semafor porabe, podatki z zamikom") { openUsage() })
        }
        add(MenuEntry("Nastavitve", "Obvestila in druge nastavitve") { showScreen(Screen.SETTINGS) })
        add(MenuEntry("Viri podatkov", dividerBefore = true) { dialog.value = AppDialog.SOURCES })
        add(MenuEntry("O aplikaciji") { dialog.value = AppDialog.ABOUT })
    }

    /** The usage dashboard: the second page of the home screen. */
    private fun openUsage() {
        showScreen(Screen.HOME)
        homePageRequest.value = 1
        usageVm.onScreenShown()
    }

    private fun openMojElektro(from: Screen) {
        mojElektroReturn = from
        showScreen(Screen.MOJELEKTRO)
    }

    /** Switches to [s] and runs what that screen needs on every visit. */
    private fun showScreen(s: Screen) {
        screen.value = s
        when (s) {
            Screen.NEWS -> newsVm.load()
            // The state is read again on every visit, so the switches and the explanation match the real state.
            Screen.SETTINGS -> refreshSettingsState()
            else -> Unit
        }
        if (s == Screen.HOME) tick()
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
            toast("Za obvestila dovoli obvestila v nastavitvah telefona.")
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
            blockNotifications = blockOn,
            notificationMode = AppSettings.notificationMode(this),
            statusIcon = statusOn,
            notificationsBlocked = !canPostAny && (blockOn || statusOn || AppSettings.permissionDenied(this)),
            // Cheap: only checks that a connection is stored (no decryption, no network).
            mojElektroConnected = MojElektroRepository(this).isConnected()
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
            toast("Nastavitev obvestil ni mogoče odpreti.")
        }
    }

    /** Opens the mail app for [address]. */
    private fun openMail(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()))
        } catch (e: Exception) {
            toast("Povezave ni mogoče odpreti.")
        }
    }

    private fun openUrl(url: String) {
        try {
            val uri = url.toUri()
            // Web pages only: a link coming from external content (news feed) must never start other schemes (intent:, file:, content:, ...).
            require(uri.scheme?.lowercase() in setOf("http", "https")) { "Unsupported link scheme" }
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: Exception) {
            toast("Povezave ni mogoče odpreti.")
        }
    }

    private fun openDonationUrl(url: String) {
        if (url.contains("PLACEHOLDER")) {
            toast("Donacijska povezava še ni nastavljena.")
            return
        }
        openUrl(url)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

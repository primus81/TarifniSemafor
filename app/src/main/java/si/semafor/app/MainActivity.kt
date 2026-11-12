package si.semafor.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.core.view.doOnLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import si.semafor.app.ui.components.UiKit
import si.semafor.app.ui.components.asButton
import si.semafor.app.ui.components.asLink
import si.semafor.app.ui.compose.ScreenInsets
import si.semafor.app.ui.compose.composeScreen
import si.semafor.app.ui.compose.screens.CalcScreen
import si.semafor.app.ui.compose.screens.SettingsScreen
import si.semafor.app.ui.compose.screens.SettingsUiState
import si.semafor.app.ui.compose.screens.TipsScreen
import si.semafor.app.ui.theme.Palette

enum class Screen { HOME, CALC, NEWS, PRICES, TIPS, SETTINGS }

class MainActivity : ComponentActivity() {
    private lateinit var box: LinearLayout
    private lateinit var status: TextView
    private var statusSizeSp = 12f
    private var topIcon: android.graphics.drawable.LayerDrawable? = null
    private var topTitleTv: TextView? = null

    private lateinit var contentContainer: FrameLayout
    private lateinit var homeView: ScrollView

    // Pull-to-refresh wrappers around the scrollable home and news content.
    private lateinit var homeRefresh: SwipeRefreshLayout
    private var newsRefresh: SwipeRefreshLayout? = null
    private var newsScroll: ScrollView? = null

    // News loads in flight; the pull spinner stays on until the last one has finished (success or error).
    private var newsPending = 0

    // The view currently shown in contentContainer (used by the screen transitions).
    private var shownView: View? = null
    private val motionInterpolator by lazy { PathInterpolatorCompat.create(0.4f, 0f, 0.2f, 1f) }
    private var calcView: View? = null
    private var newsView: View? = null
    private var pricesView: View? = null
    private var pricesBox: LinearLayout? = null
    private var tipsView: View? = null
    private var settingsView: View? = null
    private var currentBlockId: Int? = null

    // Whether the two recommendation lists on the home screen are open (kept while the app runs, open at every start).
    private var nowAdviceExpanded = true
    private var nextAdviceExpanded = true
    private var newsContentBox: LinearLayout? = null

    private var currentScreen: Screen = Screen.HOME

    private var topDateTv: TextView? = null
    private var topTimeTv: TextView? = null
    private var countdownTv: TextView? = null
    private var currentNextHour: Int? = null
    private var nextChangeMillis: Long = 0L
    private var shownDate: java.time.LocalDate? = null

    // Device clock or time zone changed: drop throttles and re-validate the shown data.
    private val timeChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastFreshFetchTime = 0L
            lastReloadTime = 0L
            fetchFreshData(force = true)
        }
    }

    private var bestCardContainer: LinearLayout? = null
    private var bestCountdownTv: TextView? = null
    private var currentBestBlock: BestBlock? = null

    private var lastReloadTime: Long = 0L
    private var lastFreshFetchTime: Long = 0L
    @Volatile
    private var isFetchingData: Boolean = false

    // Back goes to the home screen from every other screen; on the home screen it is disabled, so the system
    // default (leave the app, with the predictive back animation) applies.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showScreen(Screen.HOME)
        }
    }

    // System bar insets for the Compose screens (the View screens get them as padding in the listener below).
    private val screenInsets = mutableStateOf(ScreenInsets.None)

    // What the Settings screen shows; refreshed whenever the screen is opened and when the app comes back.
    private val settingsState = mutableStateOf(SettingsUiState())

    private companion object {
        // Request codes of the notification permission: a switch the user turned on, and the first start.
        const val REQ_BLOCK_NOTIFICATIONS = 1001
        const val REQ_STATUS_ICON = 1002
        const val REQ_FIRST_RUN = 1003
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        onBackPressedDispatcher.addCallback(this, backCallback)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(Palette.BG_SCREEN))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG_SCREEN)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Horizontal bar: the title takes the remaining width and shrinks instead of overlapping the date/time (large fonts).
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Palette.BG_TOP_BAR)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = dp(56)
            setPadding(dp(12), 0, dp(12), 0)
        }

        val menuBtn = TextView(this).apply {
            text = "☰"
            // Icon glyph: dp, not sp, so it cannot outgrow its 48 dp box with a large system font.
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 28f)
            setTextColor(Palette.TEXT_PRIMARY)
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = "Meni"
            asButton()
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { v -> showMenu(v) }
        }
        topBar.addView(menuBtn)

        val titleTv = TextView(this).apply {
            text = "Tarifni semafor"
            textSize = 20f
            getDrawable(R.drawable.ic_topbar_semafor)?.mutate()?.let {
                it.setBounds(0, 0, dp(36), dp(36))
                setCompoundDrawables(it, null, null, null)
                compoundDrawablePadding = dp(-4)
                topIcon = it as? android.graphics.drawable.LayerDrawable
            }
            setTypeface(null, Typeface.BOLD)
            setTextColor(Palette.TEXT_PRIMARY)
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(
                14,
                20,
                1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topTitleTv = titleTv
        updateTopIcon(null)
        topBar.addView(titleTv)

        val rightLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }

        topDateTv = TextView(this).apply {
            textSize = 12f
            setTextColor(Palette.TEXT_SECONDARY)
            gravity = Gravity.END
        }
        topTimeTv = TextView(this).apply {
            textSize = 12f
            setTextColor(Palette.TEXT_SECONDARY)
            gravity = Gravity.END
        }
        rightLayout.addView(topDateTv)
        rightLayout.addView(topTimeTv)
        topBar.addView(rightLayout)

        root.addView(topBar)

        val topDivider = View(this).apply {
            setBackgroundColor(Palette.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        }
        root.addView(topDivider)

        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        homeView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }
        homeView.addView(box)
        capContentWidth(box)
        homeRefresh = createRefreshLayout(homeView) { fetchFreshData(force = true) }
        contentContainer.addView(homeRefresh)
        shownView = homeRefresh

        root.addView(contentContainer)

        setContentView(root)

        val insetsController = WindowCompat.getInsetsController(window, root)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarInset = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val sideInset = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInset = androidx.core.graphics.Insets.of(
                sideInset.left, 0, sideInset.right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            // Keyboard: the content ends above it, so a focused field (calculator) is scrolled into view.
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            contentContainer.setPadding(0, 0, 0, (imeBottom - navBarInset.bottom).coerceAtLeast(0))

            val topBarLp = topBar.layoutParams as LinearLayout.LayoutParams
            topBarLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
            topBar.minimumHeight = dp(56) + statusBarInset.top
            topBar.layoutParams = topBarLp
            topBar.setPadding(
                dp(12) + sideInset.left,
                statusBarInset.top,
                dp(12) + sideInset.right,
                0
            )

            homeView.clipToPadding = false
            box.setPadding(
                dp(24) + navBarInset.left,
                dp(16),
                dp(24) + navBarInset.right,
                dp(24) + navBarInset.bottom
            )

            newsScroll?.clipToPadding = false
            newsContentBox?.setPadding(
                dp(24) + navBarInset.left,
                dp(16),
                dp(24) + navBarInset.right,
                dp(24) + navBarInset.bottom
            )

            (pricesView as? ScrollView)?.clipToPadding = false
            pricesBox?.setPadding(
                dp(24) + navBarInset.left,
                dp(16),
                dp(24) + navBarInset.right,
                dp(24) + navBarInset.bottom
            )

            screenInsets.value = ScreenInsets(navBarInset.left, navBarInset.right, navBarInset.bottom)

            insets
        }

        status = TextView(this).apply {
            setTextColor(Palette.TEXT_SECONDARY)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        SemaforWidget.schedule(this)
        maybeAskNotificationPermissionOnFirstRun()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        // The user may have changed the notification settings of the phone while the app was in the background.
        refreshSettingsState()
        // A catch-up after an offline period (SyncRecovery) refreshes the home screen straight away.
        SyncRecovery.uiListener = { info ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) show(info, Repo.getMemoryCachedBest(), isOfflineFailure = false)
            }
        }
        ContextCompat.registerReceiver(
            this,
            timeChangeReceiver,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val cached = Repo.cached(this)
        val memBest = Repo.getMemoryCachedBest()

        if (cached != null && isInfoSuitable(cached)) {
            show(cached, memBest, isOfflineFailure = false)
        } else {
            showLoading()
        }

        val now = System.currentTimeMillis()
        if (now - lastFreshFetchTime >= 60_000L) {
            fetchFreshData(force = false)
        }
    }

    private fun formatStamp(millis: Long): String =
        SloTime.formatDateTime(millis)

    private fun isInfoSuitable(info: Info): Boolean = Repo.dataState(info) != DataState.UNVERIFIED

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

    private fun updateBackCallback() {
        backCallback.isEnabled = currentScreen != Screen.HOME
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_BLOCK_NOTIFICATIONS && requestCode != REQ_STATUS_ICON && requestCode != REQ_FIRST_RUN) {
            return
        }
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            AppSettings.setPermissionDenied(this, false)
            // The switch the user turned on is on now.
            if (requestCode == REQ_BLOCK_NOTIFICATIONS) AppSettings.setBlockNotificationsEnabled(this, true)
            if (requestCode == REQ_STATUS_ICON) AppSettings.setStatusIconEnabled(this, true)
        } else {
            // Remembered so the Settings screen can explain it; the system dialog is never shown again on its own.
            AppSettings.setPermissionDenied(this, true)
            // A switch the user tried to turn on goes back to off. At the first start the defaults stay as they are.
            if (requestCode == REQ_BLOCK_NOTIFICATIONS) AppSettings.setBlockNotificationsEnabled(this, false)
            if (requestCode == REQ_STATUS_ICON) AppSettings.setStatusIconEnabled(this, false)
            Toast.makeText(
                this,
                "Za obvestila dovoli obvestila v nastavitvah telefona.",
                Toast.LENGTH_SHORT
            ).show()
        }
        applyNotificationSettings()
        refreshSettingsState()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission(requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
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
            requestNotificationPermission(REQ_FIRST_RUN)
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
            notificationsBlocked = !canPostAny && (blockOn || statusOn || AppSettings.permissionDenied(this))
        )
    }

    private fun onBlockNotificationsChange(enabled: Boolean) {
        if (enabled && needsNotificationPermission()) {
            // Switched on only once the user has allowed it (see onRequestPermissionsResult).
            requestNotificationPermission(REQ_BLOCK_NOTIFICATIONS)
            return
        }
        AppSettings.setBlockNotificationsEnabled(this, enabled)
        applyNotificationSettings()
        refreshSettingsState()
    }

    private fun onStatusIconChange(enabled: Boolean) {
        if (enabled && needsNotificationPermission()) {
            requestNotificationPermission(REQ_STATUS_ICON)
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

    private fun showScreen(s: Screen) {
        val screenChanged = s != currentScreen
        currentScreen = s

        val next: View = when (s) {
            Screen.HOME -> homeRefresh
            Screen.CALC -> calcView ?: composeScreen(this, screenInsets) {
                CalcScreen(
                    onNavigateHome = { showScreen(Screen.HOME) },
                    onOpenUrl = ::openUrl,
                    onSendMail = ::openMail
                )
            }.also { calcView = it }
            Screen.NEWS -> newsView ?: createNewsView().also { newsView = it }
            Screen.PRICES -> pricesView ?: createPricesView().also { pricesView = it }
            Screen.TIPS -> tipsView ?: composeScreen(this, screenInsets) {
                TipsScreen(
                    onNavigateHome = { showScreen(Screen.HOME) },
                    onOpenUrl = ::openUrl
                )
            }.also { tipsView = it }
            Screen.SETTINGS -> {
                // The state is read again on every visit, so the switches and the explanation match the real state.
                refreshSettingsState()
                settingsView ?: composeScreen(this, screenInsets) {
                    SettingsScreen(
                        state = settingsState.value,
                        onNavigateHome = { showScreen(Screen.HOME) },
                        onBlockNotificationsChange = ::onBlockNotificationsChange,
                        onNotificationModeChange = ::onNotificationModeChange,
                        onStatusIconChange = ::onStatusIconChange,
                        onOpenNotificationSettings = ::openNotificationSettings
                    )
                }.also { settingsView = it }
            }
        }
        // Rebuilding the same screen in place (e.g. after the permission result) is not animated.
        swapContent(next, animate = screenChanged)

        when (s) {
            Screen.NEWS -> loadNews()
            Screen.PRICES -> renderPrices()
            else -> Unit
        }
        updateBackCallback()
        scrollContentOf(next)?.let { capContentWidth(it) }
        ViewCompat.requestApplyInsets(contentContainer.rootView)
    }

    /**
     * Puts [next] into contentContainer. With [animate] the new screen fades in with a small upward slide while the
     * old one fades out (about 140-220 ms, Material "fast out, slow in" easing).
     * Rapid consecutive switches are safe: at most the old and the new screen are ever attached, anything still
     * fading out from an earlier switch is dropped, and a screen that comes back while it is fading out is simply
     * faded in again from its current alpha (no re-adding, no flicker, no stacking).
     */
    private fun swapContent(next: View, animate: Boolean) {
        val prev = shownView
        shownView = next
        if (prev === next) return

        for (i in contentContainer.childCount - 1 downTo 0) {
            val c = contentContainer.getChildAt(i)
            if (c !== prev && c !== next) {
                c.animate().cancel()
                contentContainer.removeViewAt(i)
                c.alpha = 1f
                c.translationY = 0f
            }
        }

        val animated = animate && android.animation.ValueAnimator.areAnimatorsEnabled()
        if (!animated) {
            if (prev != null) {
                prev.animate().cancel()
                contentContainer.removeView(prev)
                prev.alpha = 1f
                prev.translationY = 0f
            }
            next.animate().cancel()
            next.alpha = 1f
            next.translationY = 0f
            if (next.parent == null) contentContainer.addView(next)
            return
        }

        next.animate().cancel()
        if (next.parent === contentContainer) {
            // Still fading out from a previous quick switch: bring it back from where it is.
            contentContainer.bringChildToFront(next)
        } else {
            next.alpha = 0f
            next.translationY = dp(12).toFloat()
            contentContainer.addView(next)
        }
        next.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(motionInterpolator)
            .withLayer()
            .start()

        if (prev != null && prev.parent === contentContainer) {
            prev.animate().cancel()
            prev.animate()
                .alpha(0f)
                .setDuration(140L)
                .setInterpolator(motionInterpolator)
                .withLayer()
                .withEndAction {
                    // Not if it has been brought back as the current screen in the meantime.
                    if (shownView !== prev && prev.parent === contentContainer) {
                        contentContainer.removeView(prev)
                    }
                    prev.alpha = 1f
                    prev.translationY = 0f
                }
                .start()
        }
    }

    /**
     * Text colour for content drawn on [background] (the block colour): the candidate with the higher WCAG contrast
     * ratio, dark #111111 or white. If even the best one stays below 4.5:1 (AA for small text), pure black is used
     * instead of #111111 because it gains a little more contrast.
     *
     * A fixed luminance threshold such as 0.5 is deliberately not used: with this palette it would pick white on
     * the red, orange and green blocks, where dark text has the higher contrast.
     */
    private fun readableTextColor(background: Int): Int {
        val dark = 0xFF111111.toInt()
        val light = 0xFFFFFFFF.toInt()
        val contrastDark = ColorUtils.calculateContrast(dark, background)
        val contrastLight = ColorUtils.calculateContrast(light, background)
        val darkWins = contrastDark >= contrastLight
        if (maxOf(contrastDark, contrastLight) >= 4.5) return if (darkWins) dark else light
        return if (darkWins) 0xFF000000.toInt() else light
    }

    /** Pull-to-refresh wrapper: the spinner uses the app accent colour on the card surface colour. */
    private fun createRefreshLayout(scroll: ScrollView, onRefresh: () -> Unit): SwipeRefreshLayout =
        SwipeRefreshLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setColorSchemeColors(ContextCompat.getColor(this@MainActivity, R.color.semafor_accent))
            setProgressBackgroundColorSchemeColor(Palette.SURFACE)
            setOnRefreshListener { onRefresh() }
            addView(
                scroll,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

    /** The content view inside a screen's scroll view (also when the scroll view sits in a SwipeRefreshLayout). */
    private fun scrollContentOf(screen: View): View? {
        val scroll = if (screen is SwipeRefreshLayout) screen.getChildAt(0) else screen
        return (scroll as? ScrollView)?.getChildAt(0)
    }

    private fun createPricesView(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val boxLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }
        scroll.addView(boxLayout)
        pricesBox = boxLayout
        return scroll
    }

    private fun cardBackground() = UiKit.cardBackground(this)

    private fun renderPrices() {
        val boxLayout = pricesBox ?: return
        boxLayout.removeAllViews()

        val list = PriceLists.current()

        boxLayout.addView(
            UiKit.screenHeader(
                this,
                "Cenik omrežnine",
                "Uporabniška skupina 0 (gospodinjstva in nizka napetost)",
                bottomMarginDp = if (list == null) 12 else 2
            ) { showScreen(Screen.HOME) }
        )

        if (list == null) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = cardBackground()
            }
            card.addView(
                text(
                    "Cenik za trenutno obdobje še ni vnesen.",
                    14f,
                    false,
                    Palette.TEXT_PRIMARY
                )
            )
            val btn = Button(this).apply {
                text = "Odpri cenike SODO"
                textSize = 14f
                setTextColor(Palette.TEXT_PRIMARY)
                isAllCaps = false
                minimumHeight = dp(48)
                elevation = 0f
                stateListAnimator = null
                background = GradientDrawable().apply {
                    setColor(Palette.BORDER)
                    cornerRadius = dp(20).toFloat()
                }
                setPadding(dp(24), dp(8), dp(24), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                setOnClickListener { openUrl(PriceLists.SOURCE_ALL) }
            }
            card.addView(btn)
            boxLayout.addView(card)
            return
        }

        val df = java.time.format.DateTimeFormatter.ofPattern("d. M. yyyy")
        boxLayout.addView(
            text(
                "Velja od ${list.validFrom.format(df)} do ${list.validTo.format(df)}",
                12f,
                false,
                Palette.TEXT_SECONDARY
            ).apply {
                setPadding(0, dp(2), 0, dp(12))
            })

        val tableCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val wBlock = 1.3f
        val wPower = 1.5f
        val wEnergy = 1.3f

        fun cell(t: String, w: Float, sizeSp: Float, bold: Boolean, color: Int, right: Boolean) =
            TextView(this).apply {
                text = t
                textSize = sizeSp
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
                gravity = (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
                // Numbers shrink instead of wrapping or clipping with a large system font.
                if (right && sizeSp == 14f) {
                    maxLines = 1
                    setAutoSizeTextTypeUniformWithConfiguration(
                        10,
                        14,
                        1,
                        android.util.TypedValue.COMPLEX_UNIT_SP
                    )
                }
            }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4) + dp(4), dp(6), dp(8), dp(8))
        }
        header.addView(cell("Blok", wBlock, 12f, true, Palette.TEXT_SECONDARY, false))
        header.addView(cell("Moč [€/kW/mesec]", wPower, 12f, true, Palette.TEXT_SECONDARY, true))
        header.addView(cell("Energija [€/kWh]", wEnergy, 12f, true, Palette.TEXT_SECONDARY, true))
        // One spoken item for the header; each data row below is one item with units (cells alone would be bare numbers).
        header.isFocusable = true
        header.contentDescription =
            "Stolpci: blok, moč v evrih na kilovat na mesec, energija v evrih na kilovatno uro."
        for (i in 0 until header.childCount) header.getChildAt(i).importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        tableCard.addView(header)

        val nowId = currentBlockId
        for (r in list.rows) {
            val isNow = nowId != null && nowId == r.block
            val blockColor = Repo.color(r.block)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(44)
                if (isNow) setBackgroundColor((blockColor and 0x00FFFFFF) or (0x40 shl 24))
            }
            row.addView(View(this).apply {
                if (isNow) setBackgroundColor(blockColor)
                layoutParams = LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT)
            })
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val label = if (isNow) "${r.block}. blok (zdaj)" else "${r.block}. blok"
            inner.addView(cell(label, wBlock, 14f, isNow, Palette.TEXT_PRIMARY, false))
            inner.addView(
                cell(
                    PriceLists.formatNumber(r.powerEurPerKw),
                    wPower,
                    14f,
                    isNow,
                    Palette.TEXT_PRIMARY,
                    true
                )
            )
            inner.addView(
                cell(
                    PriceLists.formatNumber(r.energyEurPerKwh),
                    wEnergy,
                    14f,
                    isNow,
                    Palette.TEXT_PRIMARY,
                    true
                )
            )
            row.addView(inner)
            inner.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            row.isFocusable = true
            row.contentDescription =
                "${r.block}. blok" + (if (isNow) ", trenutno veljavni časovni blok" else "") +
                        ". Moč " + PriceLists.formatNumber(r.powerEurPerKw) + " evra na kilovat na mesec. Energija " +
                        PriceLists.formatNumber(r.energyEurPerKwh) + " evra na kilovatno uro."
            tableCard.addView(row)
        }
        boxLayout.addView(tableCard)

        val notes = ArrayList<String>()
        notes.add("Cenik velja za uporabniško skupino 0 (gospodinjski odjemalci in drugi odjemalci na nizkonapetostnem omrežju). Uporabniki brez 15-minutnih meritev imajo drugačen obračun in tu niso prikazani.")
        notes.add("Zneski so brez davkov, taks, trošarin in drugih dajatev.")
        notes.add("Tarifna postavka za moč se obračunava glede na dogovorjeno (in morebitno presežno) obračunsko moč v posameznem časovnem bloku, ne glede na porabo kWh. Tarifna postavka za energijo se obračunava glede na prevzeto energijo v kWh.")
        notes.add("Blok 1 velja samo ob delavnikih v višji sezoni (november–februar).")
        list.note?.let { notes.add(it) }
        notes.add("Cenik je informativen. Veljavne postavke objavlja operater (SODO).")

        val notesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        notes.forEachIndexed { i, n ->
            notesCard.addView(text("• $n", 13f, false, Palette.TEXT_SECONDARY).apply {
                setLineSpacing(0f, 1.1f)
                if (i > 0) setPadding(0, dp(6), 0, 0)
            })
        }
        boxLayout.addView(notesCard)

        boxLayout.addView(text("Uradni cenik (SODO)", 13f, false, Palette.TEXT_PRIMARY).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            minimumHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { openUrl(list.sourceUrl) }
            asLink()
        })
    }

    private fun createNewsView(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val boxLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }
        scroll.addView(boxLayout)
        newsContentBox = boxLayout
        newsScroll = scroll
        // Pull down to reload the news; the Osveži button on the screen stays as the accessible fallback.
        return createRefreshLayout(scroll) { loadNews() }.also { newsRefresh = it }
    }

    private fun loadNews() {
        val boxLayout = newsContentBox ?: return
        boxLayout.removeAllViews()

        boxLayout.addView(UiKit.screenHeader(this, "Novice") { showScreen(Screen.HOME) })

        boxLayout.addView(stateCard("Nalagam novice …"))

        newsPending++
        Thread {
            var result: NewsResult? = null
            try {
                result = NewsRepo.fetchNews(this)
            } finally {
                // The pull spinner is dismissed on success and on error alike, once the last load has finished.
                runOnUiThread {
                    newsPending = (newsPending - 1).coerceAtLeast(0)
                    if (newsPending == 0) newsRefresh?.isRefreshing = false
                    val loaded = result
                    if (loaded != null && !isFinishing && !isDestroyed) {
                        renderNewsScreen(loaded)
                    }
                }
            }
        }.start()
    }

    private fun renderNewsScreen(result: NewsResult) {
        val boxLayout = newsContentBox ?: return
        boxLayout.removeAllViews()

        val subTitleText = if (result.isOffline) {
            "Agencija za energijo · Shranjene novice (brez povezave)"
        } else {
            "Agencija za energijo"
        }
        boxLayout.addView(UiKit.screenHeader(this, "Novice", subTitleText) { showScreen(Screen.HOME) })

        val items = result.items
        if (items.isNullOrEmpty()) {
            boxLayout.addView(
                stateCard(
                    "Novic ni mogoče naložiti.",
                    null,
                    "Preveri internetno povezavo."
                )
            )
        } else {
            for (item in items) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = cardBackground()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                    setOnClickListener {
                        if (item.link.isNotEmpty()) {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(item.link)
                                        .takeIf { it.scheme?.lowercase() in setOf("http", "https") }
                                        ?: throw IllegalArgumentException("Unsupported link scheme")
                                ).addCategory(Intent.CATEGORY_BROWSABLE)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Povezave ni mogoče odpreti.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                card.asLink()
                if (item.title.isNotEmpty()) {
                    val itemTitleTv = TextView(this).apply {
                        text = item.title
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Palette.TEXT_PRIMARY)
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    card.addView(itemTitleTv)
                }

                if (item.dateMillis > 0L) {
                    val itemDateTv = TextView(this).apply {
                        text = SloTime.formatDate(item.dateMillis)
                        textSize = 12f
                        setTextColor(Palette.TEXT_SECONDARY)
                        setPadding(0, dp(4), 0, 0)
                    }
                    card.addView(itemDateTv)
                }

                if (item.description.isNotEmpty()) {
                    val itemDescTv = TextView(this).apply {
                        text = item.description
                        textSize = 13f
                        setTextColor(Palette.TEXT_SECONDARY)
                        maxLines = 4
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(6), 0, 0)
                    }
                    card.addView(itemDescTv)
                }

                boxLayout.addView(card)
            }
        }

        val refreshBtn = Button(this).apply {
            text = "Osveži"
            textSize = 14f
            setTextColor(Palette.TEXT_PRIMARY)
            isAllCaps = false
            minimumHeight = dp(48)
            elevation = 0f
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(Palette.BORDER)
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(24), dp(8), dp(24), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            }
            setOnClickListener { loadNews() }
        }
        boxLayout.addView(refreshBtn)

        val sourceTv = TextView(this).apply {
            text = "Vir: Agencija za energijo (agen-rs.si)"
            textSize = 12f
            setTextColor(Palette.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(16)
            }
        }
        boxLayout.addView(sourceTv)
    }

    /**
     * Single place that updates the top bar logo. Called by every render path (show, showLoading, showErrorState),
     * so it follows the cached data at startup, every fresh load, the Refresh button, the countdown reload and onResume.
     * The colour is Repo.color (same as the cards); no data / unknown block = lamp off (the border colour).
     */
    private fun updateTopIcon(blockId: Int?) {
        val state = SemaforIconState.forBlock(blockId)
        val color = if (state.lit) Repo.color(state.blockId!!) else Palette.BORDER
        topIcon?.findDrawableByLayerId(R.id.bolt)?.mutate()?.setTint(color)
        topIcon?.invalidateSelf()
        topTitleTv?.contentDescription =
            "Tarifni semafor. " + if (state.lit) "Trenutni blok: ${state.blockId}" else "Ni podatkov"
    }

    private fun showLoading() {
        box.removeAllViews()
        box.touchDelegate = null
        updateTopIcon(null)
        currentNextHour = null
        countdownTv = null
        currentBestBlock = null
        bestCountdownTv = null
        bestCardContainer = null

        box.addView(
            stateCard(
                "Nalagam podatke …",
                "Pridobivam podatke o trenutnem časovnem bloku."
            )
        )

        val refreshBtn = Button(this).apply {
            text = "Osveži"
            textSize = 14f
            setTextColor(Palette.TEXT_PRIMARY)
            isAllCaps = false
            minimumHeight = dp(48)
            elevation = 0f
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(Palette.BORDER)
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(24), dp(8), dp(24), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            }
            setOnClickListener { fetchFreshData(force = true) }
        }
        box.addView(refreshBtn)

        tick()
    }

    private fun showErrorState(lastUpdate: Long? = null) {
        box.removeAllViews()
        box.touchDelegate = null
        updateTopIcon(null)
        currentNextHour = null
        countdownTv = null
        currentBestBlock = null
        bestCountdownTv = null
        bestCardContainer = null

        val detail = if (lastUpdate != null && lastUpdate > 0L) {
            "Zadnja posodobitev: " + formatStamp(lastUpdate) + "\nShranjeni podatki niso več veljavni, zato niso prikazani."
        } else null
        box.addView(
            stateCard(
                "Podatkov trenutno ni mogoče preveriti.",
                detail,
                "Posodobitev trenutno ni mogoča. Preveri internetno povezavo."
            )
        )
        val refreshBtn = Button(this).apply {
            text = "Osveži"
            textSize = 14f
            setTextColor(Palette.TEXT_PRIMARY)
            isAllCaps = false
            minimumHeight = dp(48)
            elevation = 0f
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(Palette.BORDER)
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(24), dp(8), dp(24), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            }
            setOnClickListener { fetchFreshData(force = true) }
        }
        box.addView(refreshBtn)
    }

    private fun fetchFreshData(force: Boolean = false) {
        // Never run two network refreshes at once (manual refresh included).
        if (isFetchingData) return
        isFetchingData = true

        Thread {
            try {
                val freshInfo = Repo.load(this)
                // Failed fetch: schedule the catch-up for when the network returns. Success (also a manual
                // refresh): cancel a pending one.
                val recovered = SyncRecovery.afterFetch(this, freshInfo)
                val memBest = Repo.getMemoryCachedBest()

                if (freshInfo != null && freshInfo.fresh) {
                    lastFreshFetchTime = System.currentTimeMillis()
                    runOnUiThread {
                        show(freshInfo, memBest, isOfflineFailure = false)
                    }
                } else {
                    val cached = Repo.cached(this)
                    runOnUiThread {
                        // Stored data is shown only while it can still be trusted; otherwise say so.
                        if (cached != null && Repo.dataState(cached) != DataState.UNVERIFIED) {
                            show(cached, memBest, isOfflineFailure = true)
                        } else {
                            showErrorState(cached?.time)
                        }
                    }
                }

                val targetInfo = freshInfo ?: Repo.cached(this)
                val best = if (targetInfo != null) Repo.findBestUpcoming(this, targetInfo) else null
                SemaforWidget.render(this, targetInfo, best)
                Notifier.onData(this, targetInfo, recovered)

                if (targetInfo != null) {
                    runOnUiThread {
                        updateBestCard(best)
                    }
                }
            } finally {
                isFetchingData = false
                // Dismiss the pull-to-refresh spinner on success and on error alike. A pull that arrives while another
                // fetch is running is covered too: it returns early above and this finally ends the spinner.
                runOnUiThread { homeRefresh.isRefreshing = false }
            }
        }.start()
    }

    private fun tick() {
        // Always derived from the real current instant, never from a manually incremented counter.
        val nowMs = System.currentTimeMillis()
        // Only touch the views when the text really changes (no needless accessibility events every second).
        val dateText = SloTime.formatTopDate(nowMs)
        if (topDateTv?.text?.toString() != dateText) topDateTv?.text = dateText
        val timeText = SloTime.formatHm(nowMs)
        if (topTimeTv?.text?.toString() != timeText) topTimeTv?.text = timeText

        if (currentScreen == Screen.HOME) {
            if (currentNextHour != null && countdownTv != null) {
                val diffMs = nextChangeMillis - nowMs
                if (diffMs <= 0) {
                    countdownTv?.text = "Sprememba časovnega bloka zdaj"
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastReloadTime >= 5000L) {
                        lastReloadTime = currentTime
                        fetchFreshData(force = true)
                    }
                } else {
                    val totalSec = diffMs / 1000
                    val h = totalSec / 3600
                    val m = (totalSec % 3600) / 60
                    val s = totalSec % 60
                    countdownTv?.text = "Sprememba časovnega bloka čez $h h $m min $s s"
                }
            }

            // New Slovenian calendar day (price list validity): refresh once, throttled.
            val shown = shownDate
            if (currentNextHour != null && shown != null && shown != SloTime.today() &&
                nowMs - lastReloadTime >= 5000L
            ) {
                lastReloadTime = nowMs
                fetchFreshData(force = true)
            }

            val best = currentBestBlock
            if (best != null && bestCountdownTv != null) {
                val diffMs = best.startMillis - nowMs
                if (diffMs <= 0) {
                    bestCountdownTv?.text = "(zdaj)"
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastReloadTime >= 5000L) {
                        lastReloadTime = currentTime
                        fetchFreshData(force = true)
                    }
                } else {
                    val totalSec = diffMs / 1000
                    val h = totalSec / 3600
                    val m = (totalSec % 3600) / 60
                    val s = totalSec % 60
                    bestCountdownTv?.text = "(čez $h h $m min $s s)"
                }
            }
        }
    }

    private fun show(info: Info, best: BestBlock?, isOfflineFailure: Boolean = false) {
        box.removeAllViews()
        box.touchDelegate = null

        currentNextHour = info.next.from
        nextChangeMillis = Repo.nextStartMillis(info.next.from)
        shownDate = SloTime.today()
        currentBlockId = info.now.id
        if (currentScreen == Screen.PRICES) renderPrices()
        val n = info.now
        updateTopIcon(n.id)
        // Text colour follows the block colour: whichever of dark / white reads better on it (WCAG contrast).
        val onCard = readableTextColor(Repo.color(n.id))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(Repo.color(n.id)); cornerRadius = dp(24).toFloat()
            }
        }
        // Text label as well as colour: the active block is recognisable without relying on colour.
        card.addView(text("Trenutni časovni blok", 11f, true, onCard))
        card.addView(text("${n.id}. blok", 43f, true, onCard).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(
                27,
                43,
                1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
        })
        card.addView(text(n.load, 19f, true, onCard))
        card.addView(text("${n.from}–${n.to} h · ${seasonLabel(n.season)}", 13f, false, onCard))

        // Must always stay on ONE line. The minimum is 10 sp; with a large system font it is lowered so that the
        // longest text ("čez 23 h 59 min 59 s") still fits instead of being cut off.
        val cdMinSp = (10f / maxOf(1f, resources.configuration.fontScale)).toInt().coerceAtLeast(1)
        val cdTv = text(countdown(info.next.from), 12f, false, onCard).apply {
            setTypeface(null, Typeface.ITALIC)
            setPadding(0, dp(10), 0, 0)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setAutoSizeTextTypeUniformWithConfiguration(
                cdMinSp,
                12,
                1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
        }
        countdownTv = cdTv
        card.addView(cdTv)
        // TalkBack reads the card as ONE item. The ticking countdown is hidden from it (it would be re-announced every
        // second); the fixed time of the next change is part of the description instead.
        val spokenPrice = PriceLists.energyPrice(PriceLists.current(), n.id)
            ?.let { " Omrežnina za energijo " + PriceLists.formatNumber(it) + " evra na kilovatno uro." }
            ?: ""
        card.isFocusable = true
        card.contentDescription =
            "Trenutni časovni blok: ${n.id}. blok, ${n.load}, od ${n.from} do ${n.to} ure, " +
                    "${seasonLabel(n.season)}.$spokenPrice Naslednja sprememba ob ${
                        SloTime.formatHm(
                            nextChangeMillis
                        )
                    }."
        for (i in 0 until card.childCount) card.getChildAt(i).importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        box.addView(card)

        val stateNow = Repo.dataState(info)
        status.text = "Zadnja posodobitev: " + formatStamp(info.time) + "\n" + when {
            stateNow == DataState.FRESH -> "Podatki so aktualni."
            isOfflineFailure -> "Posodobitev trenutno ni mogoča. Preveri internetno povezavo.\nPrikazani so zadnji shranjeni podatki."
            else -> "Prikazani so zadnji shranjeni podatki."
        }
        statusSizeSp = 12f
        status.textSize = statusSizeSp
        status.setTextColor(if (stateNow == DataState.FRESH) Palette.TEXT_SECONDARY else 0xFFFDD835.toInt())
        status.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        status.setPadding(0, 0, 0, 0)
        (status.parent as? ViewGroup)?.removeView(status)

        // Status text on the left, round refresh button on the right edge (same horizontal margins as the card).
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        statusRow.addView(
            status,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val refreshBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_refresh)
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Same colour as the card above: the colour of the current block.
            setColorFilter(Repo.color(n.id))
            contentDescription = "Osveži"
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = 0f
            stateListAnimator = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2A3037"))
                setStroke(dp(1), Color.parseColor("#3A424B"))
            }
            // Provisional size; the real size is set below once the text block has been measured.
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) }
            setOnClickListener { fetchFreshData(force = true) }
        }
        statusRow.addView(refreshBtn)
        box.addView(statusRow)
        fitRefreshButton(statusRow, refreshBtn)

        val bestCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        bestCardContainer = bestCard
        updateBestCard(best)
        box.addView(bestCard)

        box.addView(createDivider())
        section("Priporočila zdaj", n, nowAdviceExpanded) { nowAdviceExpanded = it }
        box.addView(createDivider())
        section("Priporočila za naslednji časovni blok", info.next, nextAdviceExpanded) { nextAdviceExpanded = it }

        val donationCard = createDonationSection()
        if (donationCard != null) {
            box.addView(createDivider(2))
            box.addView(donationCard)
        }

        tick()
    }

    /**
     * Makes the round refresh button as high as the status text block (at most 32 dp, never higher than the text).
     * If the text block is lower than 24 dp, the status text is reduced by 1 sp and measured again.
     * The touch area is extended by 8 dp on every side with a TouchDelegate. It sits on the row's parent (box),
     * because a delegate on the row itself would never receive touches outside the row's own bounds.
     */
    private fun fitRefreshButton(row: LinearLayout, btn: ImageButton, attempt: Int = 0) {
        row.doOnLayout {
            val h = status.height
            if (h in 1 until dp(24) && statusSizeSp > 9f && attempt < 3) {
                statusSizeSp -= 1f
                status.textSize = statusSizeSp
                fitRefreshButton(row, btn, attempt + 1)
                return@doOnLayout
            }
            val size = minOf(h, dp(32))
            if (size <= 0) return@doOnLayout
            val lp = btn.layoutParams as LinearLayout.LayoutParams
            if (lp.width != size || lp.height != size) {
                lp.width = size
                lp.height = size
                btn.layoutParams = lp
            }
            btn.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                val ext = dp(8)
                box.touchDelegate = TouchDelegate(
                    Rect(
                        row.left + l - ext,
                        row.top + t - ext,
                        row.left + r + ext,
                        row.top + b + ext
                    ),
                    btn
                )
            }
            btn.requestLayout()
        }
    }

    private fun createDonationSection(): View? {
        if (!DonationConfig.DONATIONS_ENABLED) return null

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // The divider above provides the 16 dp gap, like the dividers between the recommendation sections.
                bottomMargin = dp(20)
            }
        }

        val titleTv = TextView(this).apply {
            text = "☕ Podpri razvoj aplikacije"
            contentDescription = "Podpri razvoj aplikacije"
            ViewCompat.setAccessibilityHeading(this, true)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Palette.TEXT_PRIMARY)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        card.addView(titleTv)

        val descTv = TextView(this).apply {
            text =
                "Če ti je aplikacija všeč in ti koristi, lahko s prostovoljno donacijo podpreš njen nadaljnji razvoj. Hvala za tvojo podporo!"
            textSize = 12f
            setTextColor(Palette.TEXT_SECONDARY)
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(descTv)

        val row1Layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        val btn1 = createDonationButton("1 €", 13f, 48, 24).apply {
            contentDescription = "Podpri razvoj z donacijo v višini 1 evra"
            setOnClickListener { openDonationUrl(DonationConfig.URL_1) }
        }
        val btn3 = createDonationButton("3 €", 13f, 48, 24).apply {
            contentDescription = "Podpri razvoj z donacijo v višini 3 evrov"
            setOnClickListener { openDonationUrl(DonationConfig.URL_3) }
        }
        val btn5 = createDonationButton("5 €", 13f, 48, 24).apply {
            contentDescription = "Podpri razvoj z donacijo v višini 5 evrov"
            setOnClickListener { openDonationUrl(DonationConfig.URL_5) }
        }

        row1Layout.addView(
            btn1,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = dp(4) })
        row1Layout.addView(
            btn3,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(4); rightMargin = dp(4) })
        row1Layout.addView(
            btn5,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(4) })

        val row2Layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val btnCustom = createDonationButton("Poljuben znesek", 12f, 48, 24).apply {
            contentDescription = "Podpri razvoj z donacijo poljubnega zneska"
            setOnClickListener { openDonationUrl(DonationConfig.URL_CUSTOM) }
        }

        row2Layout.addView(
            btnCustom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(row1Layout)
        card.addView(row2Layout)

        return card
    }

    private fun createDonationButton(label: String, txtSize: Float, heightDp: Int, cornerDp: Int) =
        Button(this).apply {
            text = label
            textSize = txtSize
            setTextColor(Palette.TEXT_PRIMARY)
            isAllCaps = false
            minimumHeight = dp(heightDp)
            elevation = 0f
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(Palette.BORDER)
                cornerRadius = dp(cornerDp).toFloat()
            }
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }

    private fun openDonationUrl(url: String) {
        if (url.contains("PLACEHOLDER")) {
            Toast.makeText(this, "Donacijska povezava še ni nastavljena.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Povezave ni mogoče odpreti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBestCard(best: BestBlock?) {
        val container = bestCardContainer ?: return
        container.removeAllViews()

        if (best == null) {
            container.visibility = View.GONE
            currentBestBlock = null
            bestCountdownTv = null
        } else {
            currentBestBlock = best
            container.visibility = View.VISIBLE
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = cardBackground()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
            }

            val timeStr = SloTime.formatHm(best.startMillis)
            val prefix = "Naslednji najugodnejši časovni blok ob "
            val suffix = " uri"
            val ssb = SpannableStringBuilder()
            ssb.append(prefix)
            val startBold = ssb.length
            ssb.append(timeStr)
            val endBold = ssb.length
            ssb.setSpan(
                StyleSpan(Typeface.BOLD),
                startBold,
                endBold,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.append(suffix)

            val row1 = TextView(this).apply {
                text = ssb
                contentDescription = ssb.toString().replace("€/kWh", " evra na kilovatno uro")
                textSize = 14f
                setTextColor(Palette.TEXT_PRIMARY)
            }
            cardView.addView(row1)

            val row2 = TextView(this).apply {
                textSize = 14f
                setTextColor(Palette.TEXT_SECONDARY)
                setPadding(0, dp(4), 0, 0)
            }
            // The ticking countdown stays visual only; the row above already says when the block starts.
            row2.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            bestCountdownTv = row2
            cardView.addView(row2)

            container.addView(cardView)
            tick()
        }
    }

    private fun createDivider(heightDp: Int = 1): View {
        val v = View(this)
        v.setBackgroundColor(Palette.DIVIDER)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
        lp.setMargins(0, dp(16), 0, dp(16))
        v.layoutParams = lp
        return v
    }

    private fun showMenu(anchor: View) {
        val context = this
        val popupLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = GradientDrawable().apply {
                setColor(Palette.SURFACE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Palette.BORDER)
            }
        }

        val maxPopupWidth = minOf(dp(280), resources.displayMetrics.widthPixels - dp(32))

        val popup = PopupWindow(
            popupLayout,
            maxPopupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = dp(12).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        fun createMenuItem(title: String, subtitle: String? = null, onClick: () -> Unit): View {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = StateListDrawable().apply {
                    addState(
                        intArrayOf(android.R.attr.state_pressed),
                        ColorDrawable(Palette.BORDER)
                    )
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    popup.dismiss()
                    onClick()
                }
            }

            val titleTv = TextView(context).apply {
                text = title
                textSize = 15f
                setTextColor(Palette.TEXT_PRIMARY)
            }
            row.addView(titleTv)

            if (subtitle != null) {
                val subTv = TextView(context).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(Palette.TEXT_SECONDARY)
                }
                row.addView(subTv)
            }

            row.asButton()
            return row
        }

        fun createMenuDivider(): View {
            val v = View(context)
            v.setBackgroundColor(Palette.DIVIDER)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            lp.setMargins(0, dp(4), 0, dp(4))
            v.layoutParams = lp
            return v
        }

        popupLayout.addView(createMenuItem("Kalkulator", "Primerjalnik stroškov oskrbe") {
            showScreen(Screen.CALC)
        })

        popupLayout.addView(createMenuItem("Cenik omrežnine") {
            showScreen(Screen.PRICES)
        })

        popupLayout.addView(createMenuItem("Nasveti") {
            showScreen(Screen.TIPS)
        })

        popupLayout.addView(createMenuItem("Vse o tarifnem sistemu") {
            openUrl(Links.TARIFF_INFO)
        })

        popupLayout.addView(createMenuItem("Novice") {
            showScreen(Screen.NEWS)
        })

        popupLayout.addView(createMenuItem("Nastavitve", "Obvestila in druge nastavitve") {
            showScreen(Screen.SETTINGS)
        })

        popupLayout.addView(createMenuDivider())

        popupLayout.addView(createMenuItem("Viri podatkov") {
            showSourceDialog()
        })

        popupLayout.addView(createMenuItem("O aplikaciji") {
            showAboutDialog()
        })

        popup.showAsDropDown(anchor, 0, dp(4))
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Nastavitev obvestil ni mogoče odpreti.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /** Opens the mail app for [address]; same behaviour as the link used to have inside the Calculator screen. */
    private fun openMail(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
        } catch (e: Exception) {
            Toast.makeText(this, "Povezave ni mogoče odpreti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            val uri = Uri.parse(url)
            // Web pages only: a link coming from external content (news feed) must never start other schemes (intent:, file:, content:, ...).
            require(uri.scheme?.lowercase() in setOf("http", "https")) { "Unsupported link scheme" }
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Povezave ni mogoče odpreti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSourceDialog() {
        val ssb = SpannableStringBuilder()

        fun addSource(label: String, linkLabel: String, url: String) {
            if (ssb.isNotEmpty()) ssb.append("\n\n")
            ssb.append("• ").append(label).append(" ")
            val start = ssb.length
            ssb.append(linkLabel)
            val end = ssb.length
            ssb.setSpan(
                URLSpan(url),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        addSource(
            "Tarifni semafor in časovni bloki:",
            "Agencija za energijo, uro.si (API)",
            Links.URO_URL
        )
        addSource(
            "Novice:",
            "Agencija za energijo (RSS)",
            Links.NEWS_PAGE
        )
        addSource(
            "Kalkulator (Primerjalnik stroškov oskrbe), povzetek in povezava:",
            "Agencija za energijo",
            Links.CALC_URL
        )
        addSource(
            "Informacije o tarifnem sistemu:",
            "MojElektro",
            Links.TARIFF_INFO
        )

        val tv = TextView(this).apply {
            text = ssb
            movementMethod = LinkMovementMethod.getInstance()
            textSize = 14f
            setTextColor(Palette.TEXT_PRIMARY)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Viri podatkov")
            .setView(tv)
            .setPositiveButton("V redu", null)
            .create()

        dialog.show()

        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Palette.SURFACE)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Palette.BORDER)
        })
    }

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "2.1.1"
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val disclaimerTv = TextView(this).apply {
            text =
                "Tarifni semafor je neuradna aplikacija. Ni povezana z Agencijo za energijo, SODO ali drugimi operaterji. Podatki so povzeti iz javnih virov (glej Viri podatkov); njihova točnost ni zagotovljena, veljavne podatke objavljajo uradni viri."
            textSize = 13f
            setTextColor(Palette.TEXT_SECONDARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        layout.addView(disclaimerTv)

        val devSsb = SpannableStringBuilder()
        devSsb.append("Razvijalec: ")
        val startBold = devSsb.length
        devSsb.append("Primus")
        val endBold = devSsb.length
        devSsb.setSpan(
            URLSpan("https://bsky.app/profile/primush.bsky.social"),
            startBold,
            endBold,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val devTv = TextView(this).apply {
            text = devSsb
            movementMethod = LinkMovementMethod.getInstance()
            textSize = 15f
            setTextColor(Palette.TEXT_PRIMARY)
        }
        layout.addView(devTv)

        val versionTv = TextView(this).apply {
            text = "Verzija: $version"
            textSize = 15f
            setTextColor(Palette.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        }
        layout.addView(versionTv)

        if (!Links.PRIVACY_POLICY.contains("REPLACE_WITH")) {
            val privacyTv = TextView(this).apply {
                text = "Politika zasebnosti"
                textSize = 13f
                minimumHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Palette.TEXT_PRIMARY)
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(16)
                }
                setOnClickListener { openUrl(Links.PRIVACY_POLICY) }
                asLink()
            }
            layout.addView(privacyTv)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("O aplikaciji")
            .setView(layout)
            .setPositiveButton("V redu", null)
            .create()

        dialog.show()

        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Palette.SURFACE)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Palette.BORDER)
        })
    }

    /**
     * A list of recommendations that can be folded: the heading row (name + chevron) is one button; tapping it shows
     * or hides the list. [expanded] is the state to start with, [onExpandedChange] reports every change so the caller
     * can keep it when the screen is rebuilt. This is not a setting: both lists are open at every start.
     */
    private fun section(title: String, blk: Block, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_expand_more)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            rotation = if (expanded) 180f else 0f
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            isClickable = true
            addView(
                text(title, 15f, true, Palette.TEXT_PRIMARY).apply {
                    ViewCompat.setAccessibilityHeading(this, true)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(chevron, LinearLayout.LayoutParams(dp(24), dp(24)))
        }
        var open = expanded
        fun describe() {
            header.contentDescription = title + ", " + (if (open) "razširjeno" else "strnjeno") +
                ". Dotakni se za " + (if (open) "strnitev" else "razširitev") + "."
        }
        describe()
        header.asButton()
        header.setOnClickListener {
            open = !open
            onExpandedChange(open)
            android.transition.TransitionManager.beginDelayedTransition(box)
            rows.visibility = if (open) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (open) 180f else 0f).setDuration(150L).start()
            describe()
        }
        box.addView(header)
        box.addView(rows)
        for (a in blk.advice) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = cardBackground()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(6)
                }
            }
            // Symbol + description in addition to colour (colour alone must not carry the meaning).
            val (symbol, dotColor, description) = when (a.state) {
                1 -> Triple("✓", 0xFF43A047.toInt(), "Ugodno")
                2 -> Triple("!", 0xFFFDD835.toInt(), "Vmes")
                else -> Triple("✕", 0xFFE53935.toInt(), "Manj ugodno")
            }
            val dot = TextView(this).apply {
                text = symbol
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#111111"))
                gravity = Gravity.CENTER
                minWidth = dp(24)
                minHeight = dp(24)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColor)
                }
            }
            row.addView(
                dot, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(12) })
            row.addView(text("${a.device}: ${a.use}", 14f, false, Palette.TEXT_PRIMARY).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            // One spoken item per recommendation, with the state in words (the symbol/colour are not read separately).
            row.isFocusable = true
            row.contentDescription = "$description. ${a.device}: ${a.use}"
            rows.addView(row)
        }
    }

    private fun countdown(nextHour: Int): String {
        val diffMs = Repo.nextStartMillis(nextHour) - System.currentTimeMillis()
        if (diffMs <= 0) return "Sprememba časovnega bloka zdaj"
        val totalSec = diffMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "Sprememba časovnega bloka čez $h h $m min $s s"
    }

    /** On tablets and landscape the content keeps a readable width (max 640 dp) and is centred. */
    private fun capContentWidth(content: View) {
        val maxPx = dp(640)
        content.layoutParams = FrameLayout.LayoutParams(
            if (resources.displayMetrics.widthPixels > maxPx) maxPx else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    // Thin wrappers over the shared UI kit (ui/components/UiKit.kt). They disappear screen by screen as the
    // remaining screens move out of this file.
    private fun stateCard(title: String, detail: String? = null, warning: String? = null): View =
        UiKit.stateCard(this, title, detail, warning)

    /** The API names the seasons "visoka"/"nizka"; the app consistently says "višja"/"nižja" sezona. */
    private fun seasonLabel(raw: String): String =
        raw.replace(Regex("(?i)visoka sezona"), "višja sezona")
            .replace(Regex("(?i)nizka sezona"), "nižja sezona")

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int? = null) =
        UiKit.text(this, s, size, bold, color)

    private fun dp(v: Int) = UiKit.dp(this, v)
}

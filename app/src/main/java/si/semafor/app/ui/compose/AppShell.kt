package si.semafor.app.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.R
import si.semafor.app.Repo
import si.semafor.app.SemaforIconState
import si.semafor.app.SloTime
import si.semafor.app.ui.compose.components.SemaforLogo
import si.semafor.app.ui.compose.theme.AppColors
import androidx.compose.foundation.layout.Spacer

/** One entry of the main menu. A divider is drawn above an entry with [dividerBefore]. */
@Immutable
data class MenuEntry(
    val title: String,
    val subtitle: String? = null,
    val dividerBefore: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The frame around every screen: the top bar (menu, logo in the colour of the current block, date and time) and the
 * content area below it. The content area ends above the keyboard, so a focused field is scrolled into view.
 */
@Composable
fun AppShell(
    nowMillis: Long,
    blockId: Int?,
    menuEntries: () -> List<MenuEntry>,
    bottomBar: (@Composable () -> Unit)? = null,
    navigationRail: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgScreen)
    ) {
        TopBar(nowMillis, blockId, menuEntries)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.Divider)
        )
        // Keyboard: only the part that is higher than the navigation bar (the screens keep clear of that one).
        // Computed by hand instead of with windowInsetsPadding, so the screens still see the full navigation bar inset.
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val keyboard = with(density) { (imeBottom - navBottom).coerceAtLeast(0).toDp() }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Wide windows (tablet, unfolded foldable, landscape): the main destinations move to a rail on the left.
            navigationRail?.invoke()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(bottom = keyboard)
                    // With the bottom bar, the bar keeps clear of the navigation bar, so the screens must not add it again.
                    .then(if (bottomBar != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
                    // With the rail, the rail keeps clear of the left system bar and cutout.
                    .then(if (navigationRail != null) Modifier.consumeWindowInsets(SideStartInsets) else Modifier)
            ) { content() }
        }
        bottomBar?.invoke()
    }
}

@Composable
private fun TopBar(nowMillis: Long, blockId: Int?, menuEntries: () -> List<MenuEntry>) {
    val icon = SemaforIconState.forBlock(blockId)
    val titleDescription = if (icon.lit) stringResource(R.string.top_title_block, icon.blockId!!) else stringResource(R.string.top_title_no_data)
    val menuDescription = stringResource(R.string.top_menu)
    val lang = LocalLang.current
    // Saveable: an open menu stays open after rotation or folding / unfolding.
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.TopBar)
            .windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)
                    .union(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { menuOpen = true }
                    .semantics {
                        role = Role.Button
                        contentDescription = menuDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                // Icon glyph: dp, not sp, so it cannot outgrow its 48 dp box with a large system font.
                val glyphSize = with(LocalDensity.current) { 28.dp.toSp() }
                Text("☰", fontSize = glyphSize, color = AppColors.TextPrimary)
            }
            MainMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, entries = menuEntries)
        }

        // The title takes the remaining width and shrinks instead of overlapping the date and time (large fonts).
        Row(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = titleDescription },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SemaforLogo(blockId = blockId, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = stringResource(R.string.app_name),
                style = TextStyle(color = AppColors.TextPrimary, fontWeight = FontWeight.Bold),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 20.sp, stepSize = 1.sp),
                modifier = Modifier
                    .weight(1f, fill = false)
            )
        }

        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(SloTime.formatTopDate(nowMillis, lang), fontSize = 12.sp, color = AppColors.TextSecondary, textAlign = TextAlign.End)
            Text(SloTime.formatHm(nowMillis), fontSize = 12.sp, color = AppColors.TextSecondary, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun MainMenu(expanded: Boolean, onDismiss: () -> Unit, entries: () -> List<MenuEntry>) {
    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val ink = MaterialTheme.colorScheme.onSurface
    val inkSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 12.dp,
        modifier = Modifier.width(minOf(280.dp, screenWidth - 32.dp))
    ) {
        // The entries are built when the menu opens (e.g. "Moja poraba" appears only with a Moj Elektro connection).
        if (expanded) {
            for (entry in entries()) {
                if (entry.dividerBefore) {
                    Box(
                        Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppColors.Divider)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            onDismiss()
                            entry.onClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(entry.title, fontSize = 15.sp, color = ink)
                    if (entry.subtitle != null) {
                        Text(entry.subtitle, fontSize = 12.sp, color = inkSecondary)
                    }
                }
            }
        }
    }
}

/** The three main destinations of the bottom bar, in pager order. */
enum class MainTab(val labelRes: Int, val iconRes: Int) {
    HOME(R.string.nav_home, R.drawable.ic_nav_home),
    USAGE(R.string.nav_usage, R.drawable.ic_nav_usage),
    SETTINGS(R.string.nav_settings, R.drawable.ic_nav_settings)
}

/**
 * Material 3 bottom bar of the main destinations. [selected] follows the pager while it is being swiped. The home icon
 * is the app's lightning bolt in the colour of the current block. A light tick confirms a change of tab.
 */
@Composable
fun MainBottomBar(selected: Int, blockId: Int?, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    NavigationBar(containerColor = AppColors.TopBar, tonalElevation = 0.dp) {
        MainTab.entries.forEachIndexed { i, tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selected == i,
                onClick = {
                    if (selected != i) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(i)
                },
                icon = {
                    if (tab == MainTab.HOME) {
                        SemaforLogo(blockId = blockId, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(painter = painterResource(tab.iconRes), contentDescription = null)
                    }
                },
                label = { Text(label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/** The left system bar and display cutout (landscape), kept clear by the navigation rail. */
private val SideStartInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)

/**
 * The same destinations as [MainBottomBar], as a Material 3 navigation rail for wide windows. It keeps clear of the
 * left system bar, the cutout and the navigation bar; the top bar above it already covers the status bar.
 */
@Composable
fun MainNavigationRail(selected: Int, blockId: Int?, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    NavigationRail(
        containerColor = AppColors.TopBar,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)
    ) {
        Spacer(Modifier.height(8.dp))
        MainTab.entries.forEachIndexed { i, tab ->
            NavigationRailItem(
                selected = selected == i,
                onClick = {
                    if (selected != i) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(i)
                },
                icon = {
                    if (tab == MainTab.HOME) {
                        SemaforLogo(blockId = blockId, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(painter = painterResource(tab.iconRes), contentDescription = null)
                    }
                },
                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

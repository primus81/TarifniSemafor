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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
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
import si.semafor.app.ui.compose.theme.AppColors

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
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = keyboard)
        ) { content() }
    }
}

@Composable
private fun TopBar(nowMillis: Long, blockId: Int?, menuEntries: () -> List<MenuEntry>) {
    val icon = SemaforIconState.forBlock(blockId)
    val boltColor = if (icon.lit) Color(Repo.color(icon.blockId!!)) else AppColors.Border
    val titleDescription = "Tarifni semafor. " + if (icon.lit) "Trenutni blok: ${icon.blockId}" else "Ni podatkov"
    var menuOpen by remember { mutableStateOf(false) }

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
                        contentDescription = "Meni"
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
            Icon(
                painter = painterResource(R.drawable.ic_semafor_bolt),
                contentDescription = null,
                tint = boltColor,
                modifier = Modifier.size(36.dp)
            )
            BasicText(
                text = "Tarifni semafor",
                style = TextStyle(color = AppColors.TextPrimary, fontWeight = FontWeight.Bold),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 20.sp, stepSize = 1.sp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .offset(x = (-4).dp)
            )
        }

        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(SloTime.formatTopDate(nowMillis), fontSize = 12.sp, color = AppColors.TextSecondary, textAlign = TextAlign.End)
            Text(SloTime.formatHm(nowMillis), fontSize = 12.sp, color = AppColors.TextSecondary, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun MainMenu(expanded: Boolean, onDismiss: () -> Unit, entries: () -> List<MenuEntry>) {
    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
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
                    Text(entry.title, fontSize = 15.sp, color = AppColors.TextPrimary)
                    if (entry.subtitle != null) {
                        Text(entry.subtitle, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

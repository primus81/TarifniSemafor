package si.semafor.app.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.R
import si.semafor.app.ui.compose.theme.AppColors
import si.semafor.app.ui.compose.theme.BodyLineHeight

/**
 * A scrollable screen with the app's standard layout: 24 / 16 / 24 / 24 dp padding plus the system bars (left,
 * right and bottom; the content scrolls behind the navigation bar), content at most 640 dp wide and centred on
 * tablets and in landscape.
 */
@Composable
fun ScreenScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .windowInsetsPadding(ContentInsets)
                .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
            content = content
        )
    }
}

/** System bars the screen content keeps clear of: the sides (bars and display cutout) and the navigation bar. */
private val ContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))

/**
 * Header of every secondary screen: a small round back arrow (36 dp circle inside a 48 dp touch target, shifted so
 * the circle lines up with the content edge), the title (announced as a heading) and an optional grey line under it.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    backDescription: String = "Nazaj na domači zaslon",
    bottomPadding: Dp = 12.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-6).dp)
                .size(48.dp)
                .clickable(onClick = onBack)
                .semantics {
                    role = Role.Button
                    contentDescription = backDescription
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier
            .weight(1f)
            .offset(x = (-6).dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Heading of a group of cards (15 sp, bold), announced as a heading by TalkBack. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(top = 4.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** Secondary action button: rounded, at least 48 dp high. */
@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    horizontalPadding: Dp = 20.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The refresh button of the loading, error and news states (14 sp, centred in the column). */
@Composable
fun ColumnScope.RefreshButton(onClick: () -> Unit) {
    ActionButton(
        label = "Osveži",
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        horizontalPadding = 24.dp
    )
}

/**
 * One look for loading, offline and error states: a card with a title, an optional explanation and an optional
 * warning (yellow).
 */
@Composable
fun StateCard(title: String, detail: String? = null, warning: String? = null) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (warning != null) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight, fontWeight = FontWeight.Bold),
                color = AppColors.Warning,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Thin full-width line with 16 dp above and below, between the sections of the home screen. */
@Composable
fun SectionDivider(thickness: Dp = 1.dp) {
    Box(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth()
            .height(thickness)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Rounded surface with a thin border and an 8 dp gap below: the container of every card. */
@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        content = content
    )
}

/** A label with a switch; the whole row is one switch for touch and for TalkBack. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    fontSize: TextUnit = 15.sp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        // The row handles the click, so the switch itself is display only.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A label with a radio button; the whole row is one radio button for touch and for TalkBack. */
@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 14.sp
) {
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
            color = textColor,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Green call-to-action button, full width, 48 dp high (the main button of a screen). */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** Underlined text, at least 48 dp high, that opens a web page; announced as a link ("povezava"). */
@Composable
fun LinkText(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label, povezava" },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textDecoration = TextDecoration.Underline
        )
    }
}

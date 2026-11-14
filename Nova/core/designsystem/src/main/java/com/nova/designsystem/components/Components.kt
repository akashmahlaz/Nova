package com.nova.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaTheme

/** Small top bar: back arrow, optional title/subtitle and trailing actions. Handles status bar inset. */
@Composable
fun NovaTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    backIcon: ImageVector = NovaIcons.Back,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(backIcon, contentDescription = backLabel, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** Large expressive screen title with optional supporting copy. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier.padding(start = 4.dp, top = 4.dp))
}

/** Rounded square icon container (M3 tonal tile). */
@Composable
fun IconTile(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    radius: Dp = 14.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize)) }
}

/** Initials avatar on a Material cookie shape. */
@Composable
fun CookieAvatar(
    initials: String,
    container: Color,
    content: Color,
    size: Dp = 48.dp,
    shape: NovaShape = NovaShape.Cookie,
    modifier: Modifier = Modifier,
) {
    ShapeBadge(shape, size, container, modifier) {
        Text(initials, color = content, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.34f).sp)
    }
}

/**
 * One row of an M3 Expressive segmented list. Pass [index] and [count] so the first/last items
 * get the large outer corners and middle items the small inner corners (official
 * [ListItemDefaults.segmentedShapes]).
 */
@Composable
fun NovaListItem(
    index: Int,
    count: Int,
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    val colors = ListItemDefaults.segmentedColors(
        containerColor = containerColor,
        contentColor = contentColor,
        leadingContentColor = contentColor,
        trailingContentColor = supportingColor,
        supportingContentColor = supportingColor,
    )
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val headlineContent: @Composable () -> Unit = {
        Text(headline, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val supportingContent: (@Composable () -> Unit)? = supporting?.let {
        { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supportingContent,
            colors = colors,
            content = headlineContent,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supportingContent,
            colors = colors,
            content = headlineContent,
        )
    }
}

/** Vertical stack for segmented list items (2dp gap, as in M3 Expressive lists). */
@Composable
fun SegmentedColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
}

/** Rounded top sheet that sits over the map (static, non-modal) with the M3 drag handle. */
@Composable
fun MapSheet(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    showHandle: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = color,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(contentPadding)) {
            if (showHandle) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
            } else {
                Box(Modifier.height(24.dp))
            }
            content()
        }
    }
}

/** Pill/rounded label (status chips, plate, badges). */
@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = CircleShape,
    height: Dp = 32.dp,
    textSize: Int = 13,
) {
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(container)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, color = content, fontSize = textSize.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** NZ-style number plate. */
@Composable
fun PlateBadge(plate: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NovaTheme.colors.plate)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(plate, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** M3 Expressive wavy progress (determinate). */
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, track: Color = MaterialTheme.colorScheme.primaryContainer) {
    LinearWavyProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = track,
    )
}

/** Outlined text field with Nova's 16dp radius. */
@Composable
fun NovaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        leadingIcon = leading,
        trailingIcon = trailing,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** Row with a Material [Switch] (used across settings screens). */
@Composable
fun SwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    NovaListItem(
        index = index,
        count = count,
        headline = title,
        supporting = supporting,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(),
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

/** Horizontal rule with centred "or". */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text("or", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/** Width helper used in rows. */
@Composable
fun HSpace(width: Dp) = Box(Modifier.width(width))

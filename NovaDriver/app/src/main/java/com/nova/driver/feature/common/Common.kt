package com.nova.driver.feature.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.*
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.driver.data.*
import com.nova.driver.navigation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Floating square-ish map button (56dp, 18dp radius). */
@Composable
fun MapFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(56.dp), shape = RoundedCornerShape(18.dp), color = NovaTheme.colors.navBar, shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(24.dp)) }
    }
}

/** Standard secondary page: back bar + scrolling content + optional pinned bottom. */
@Composable
fun SubPage(
    title: String?,
    onBack: () -> Unit,
    background: Color = MaterialTheme.colorScheme.surface,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(background).imePadding()) {
        NovaTopBar(onBack = onBack, title = title)
        header?.invoke(this)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
        if (bottom != null) Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = bottom)
    }
}

/** Top-level tab page: large title, scrolling content, driver navigation bar. */
@Composable
fun TabPage(title: String?, tab: DriverTab, onTab: (DriverTab) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp), modifier = Modifier.padding(horizontal = 8.dp))
            content()
        }
        DriverNavigationBar(selected = tab, onSelect = onTab)
    }
}

/** Sign-up progress: Account · Vehicle · Documents · Payouts. */
@Composable
fun SignUpSteps(current: Int) {
    val c = MaterialTheme.colorScheme
    val steps = listOf("Account", "Vehicle", "Documents", "Payouts")
    Row(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp).semantics { contentDescription = "Step ${current + 1} of 4" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { i, s ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(if (i <= current) NovaTheme.colors.cta else c.surfaceContainerHighest))
                Text(s, fontSize = 12.sp, fontWeight = if (i == current) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (i == current) c.onSurface else c.onSurfaceVariant)
            }
        }
    }
}

/** Segmented "connected" radio row (pill when selected, 8dp inner corners otherwise). */
@Composable
fun ConnectedChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    selectedContainer: Color = NovaTheme.colors.cta,
    selectedContent: Color = NovaTheme.colors.onCta,
    /** Optional per-option selected colours (container to content), e.g. "Great rider" / "Had an issue". */
    perOption: List<Pair<Color, Color>>? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    fontSize: Int = 14,
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
            val full = height / 2
            val start by animateDpAsState(if (on || i == 0) full else 8.dp, spec)
            val end by animateDpAsState(if (on || i == options.lastIndex) full else 8.dp, spec)
            Box(
                Modifier.weight(1f).height(height).clip(RoundedCornerShape(start, end, end, start))
                    .background(if (on) perOption?.get(i)?.first ?: selectedContainer else container)
                    .selectable(on, onClick = { onSelect(i) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = if (on) perOption?.get(i)?.second ?: selectedContent else MaterialTheme.colorScheme.onSurface, maxLines = 1) }
        }
    }
}

/** One segment of a connected action row (leading / middle / trailing). */
@Composable
fun RowScope.ConnectedButton(
    text: String?,
    position: Int,
    count: Int,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    weight: Float = 1f,
    height: Dp = 56.dp,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    fixedWidth: Dp? = null,
) {
    val outer = height / 2
    val shape = when {
        count == 1 -> RoundedCornerShape(outer)
        position == 0 -> RoundedCornerShape(outer, 8.dp, 8.dp, outer)
        position == count - 1 -> RoundedCornerShape(8.dp, outer, outer, 8.dp)
        else -> RoundedCornerShape(8.dp)
    }
    val m = if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier.weight(weight)
    Surface(onClick = onClick, modifier = m.height(height), shape = shape, color = container, contentColor = content) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, iconDescription, Modifier.size(20.dp))
            if (icon != null && text != null) Spacer(Modifier.width(8.dp))
            if (text != null) Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Grouped row used across lists (first/last get 20dp outer corners). */
fun groupShape(index: Int, count: Int, outer: Dp = 20.dp, inner: Dp = 4.dp): RoundedCornerShape = when {
    count == 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
    index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
    else -> RoundedCornerShape(inner)
}

/** Numeric keypad (drivers use on-screen keys so the phone keyboard never covers the code). */
@Composable
fun NumberPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onClear: (() -> Unit)? = null, keyHeight: Dp = 60.dp) {
    val c = MaterialTheme.colorScheme
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (onClear != null) "clr" else "", "0", "del")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val special = k == "del" || k == "clr"
                    if (k.isEmpty()) Spacer(Modifier.weight(1f).height(keyHeight))
                    else Surface(
                        onClick = { when (k) { "del" -> onDelete(); "clr" -> onClear?.invoke(); else -> onDigit(k[0]) } },
                        shape = RoundedCornerShape(20.dp),
                        color = if (special) Color.Transparent else c.surfaceContainer,
                        modifier = Modifier.weight(1f).height(keyHeight),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (k) {
                                "del" -> Text("⌫", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Delete" })
                                "clr" -> Text("Clear", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                else -> Text(k, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Code box that morphs to a pill while active (M3 Expressive shape motion). */
@Composable
fun CodeBox(char: Char?, active: Boolean, modifier: Modifier = Modifier, height: Dp = 64.dp, error: Boolean = false, success: Boolean = false, fontSize: Int = 28) {
    val c = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (active || success) height / 2 else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    val bg = when {
        error -> c.errorContainer
        success -> NovaTheme.colors.cta
        char != null -> c.primaryContainer
        active -> Color.Transparent
        else -> c.surfaceContainerHigh
    }
    val fg = when { error -> c.onErrorContainer; success -> NovaTheme.colors.onCta; else -> c.onPrimaryContainer }
    Box(
        modifier.height(height).clip(RoundedCornerShape(radius)).background(bg)
            .then(if (active && char == null) Modifier.border(3.dp, c.primary, RoundedCornerShape(radius)) else Modifier),
        contentAlignment = Alignment.Center,
    ) { if (char != null) Text(char.toString(), fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = fg) }
}

/** "Online" toggle pill shown at the top of the map while driving. */
@Composable
fun OnlinePill(online: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = c.surfaceContainerHigh, modifier = modifier.height(56.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(
                checked = online, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = NovaTheme.colors.cta, checkedThumbColor = NovaTheme.colors.onCta),
            )
            Text(if (online) "Online" else "Offline", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** "Today $286.50" chip at the top right of the driving map. */
@Composable
fun TodayChip(amount: String = Driver.today, modifier: Modifier = Modifier) {
    Column(Modifier.then(modifier).height(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
        Text("Today", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Turn-by-turn card: volt main instruction + soft "then" row. */
@Composable
fun TurnCard(distance: String, instruction: String, then: String, modifier: Modifier = Modifier, turnRight: Boolean = false) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp, 28.dp, 6.dp, 6.dp)).background(NovaTheme.colors.cta).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(NovaIcons.Back, null, tint = NovaTheme.colors.onCta, modifier = Modifier.size(44.dp).rotate(if (turnRight) 135f else 45f))
            Column {
                Text(distance, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, color = NovaTheme.colors.onCta)
                Text(instruction, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NovaTheme.colors.onCta)
            }
        }
        Text(
            then,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp, 6.dp, 28.dp, 28.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 20.dp, vertical = 10.dp),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Vertical floating map toolbar (M3 Expressive VerticalFloatingToolbar). */
@Composable
fun MapTools(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.VerticalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = androidx.compose.material3.FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}

/** Bottom sheet that sits over the map (driver surfaces use the container tone). */
@Composable
fun DriverSheet(modifier: Modifier = Modifier, showHandle: Boolean = false, top: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    MapSheet(modifier, color = MaterialTheme.colorScheme.surfaceContainer, showHandle = showHandle, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (showHandle) 0.dp else top, bottom = 28.dp)) {
        Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Dashed circle drawn behind content. */
fun Modifier.dashedRing(color: Color, width: Dp = 2.dp): Modifier = drawBehind {
    drawCircle(color, style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
}

/** Circular countdown ring (used by request timer, free-wait timer and no-show). */
@Composable
fun CountdownRing(progress: Float, size: Dp, stroke: Dp, color: Color, track: Color, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), Size(this.size.width - s, this.size.height - s), style = Stroke(s))
            drawArc(color, -90f, 360f * progress, false, Offset(s / 2, s / 2), Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Opens the phone dialer with [number] pre-filled (the user places the call). */
fun dial(context: android.content.Context, number: String) {
    context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")))
}

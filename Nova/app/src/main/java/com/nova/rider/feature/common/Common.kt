package com.nova.rider.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.theme.NovaTheme

/** Floating square-ish map button (56dp, 18dp radius, tonal surface + shadow). */
@Composable
fun MapFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = NovaTheme.colors.navBar,
    content: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 56.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = content,
        shadowElevation = 3.dp,
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(24.dp)) } }
}

/** Dark rounded label on the map (ETA, hints). */
@Composable
fun MapLabel(text: String, modifier: Modifier = Modifier, radius: Dp = 10.dp, height: Dp = 32.dp) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.onSurface).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** Driver identity row used across trip screens. */
@Composable
fun DriverRow(modifier: Modifier = Modifier, driver: com.nova.rider.data.auth.RideDriver? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CookieAvatar(driver?.initials ?: "", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(driver?.firstName ?: "Your driver", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
            Text(driver?.let { "${it.vehicle.label} · ${it.vehicle.plate}" } ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

/** Route timeline dots (pickup ring → stop → destination square). */
@Composable
fun RouteDot(kind: DotKind, color: Color = MaterialTheme.colorScheme.onSurface) {
    when (kind) {
        DotKind.Pickup -> Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Transparent).then(Modifier.padding(0.dp))) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Box(Modifier.align(Alignment.Center).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
        }
        DotKind.Stop -> Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
        DotKind.Destination -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
    }
}

enum class DotKind { Pickup, Stop, Destination }

/** Thin vertical connector between route dots. */
@Composable
fun RouteLine(height: Dp) = Box(Modifier.width(2.dp).height(height).background(MaterialTheme.colorScheme.outlineVariant))

/** Top-level tab page: large title, scrolling content, Nova navigation bar. */
@Composable
fun TabPage(
    title: String,
    tab: com.nova.designsystem.components.RiderTab,
    onTab: (com.nova.designsystem.components.RiderTab) -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // Compact: bottom bar. Medium/expanded: side rail, and the page is a centred readable column.
    val compact = currentWidthClass() == WidthClass.Compact
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (!compact) {
            com.nova.designsystem.components.RiderNavigationRail(
                selected = tab, onSelect = onTab,
                modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).statusBarsPadding()
                    .then(if (compact) Modifier else Modifier.navigationBarsPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 8.dp))
                    content()
                }
            }
            if (compact) com.nova.designsystem.components.RiderNavigationBar(selected = tab, onSelect = onTab)
        }
    }
}

/** One segment of a connected (leading / middle / trailing) action row. */
@Composable
fun androidx.compose.foundation.layout.RowScope.ConnectedButton(
    text: String,
    position: Int,
    count: Int,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    weight: Float = 1f,
    height: Dp = 56.dp,
    icon: ImageVector? = null,
) {
    val outer = height / 2
    val shape = when {
        count == 1 -> RoundedCornerShape(outer)
        position == 0 -> RoundedCornerShape(outer, 8.dp, 8.dp, outer)
        position == count - 1 -> RoundedCornerShape(8.dp, outer, outer, 8.dp)
        else -> RoundedCornerShape(8.dp)
    }
    Surface(onClick = onClick, modifier = Modifier.weight(weight).height(height), shape = shape, color = container, contentColor = content) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Box(Modifier.width(6.dp)) }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Drawn route thumbnail: the receipt's stand-in when there is no Maps key or route line. */
@Composable
fun MiniRouteMap(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val land = NovaTheme.colors.mapLand
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(28.dp))) {
        val sx = size.width / 358f; val sy = size.height / 132f
        drawRect(land)
        scale(sx, sy, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val road = androidx.compose.ui.graphics.drawscope.Stroke(8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            listOf(listOf(-10f, 40f, 370f, 52f), listOf(90f, -10f, 100f, 140f), listOf(-10f, 100f, 370f, 92f), listOf(270f, -10f, 262f, 140f)).forEach { (a, b, c2, d) ->
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(a, b), androidx.compose.ui.geometry.Offset(c2, d), 8f, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(60f, 104f); lineTo(96f, 96f); lineTo(98f, 46f); lineTo(264f, 52f); lineTo(300f, 30f) }
            drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawCircle(Color.White, 7f, androidx.compose.ui.geometry.Offset(60f, 104f))
            drawCircle(Color(0xFF1B1B21), 7f, androidx.compose.ui.geometry.Offset(60f, 104f), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            drawRoundRect(Color(0xFF1B1B21), androidx.compose.ui.geometry.Offset(292f, 22f), androidx.compose.ui.geometry.Size(16f, 16f), androidx.compose.ui.geometry.CornerRadius(4f))
        }
    }
}

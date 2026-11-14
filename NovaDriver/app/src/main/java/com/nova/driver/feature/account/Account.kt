package com.nova.driver.feature.account

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
import androidx.compose.foundation.text.TextAutoSize
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
import com.nova.driver.feature.common.*
import com.nova.driver.navigation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EarningsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var period by rememberSaveable { mutableIntStateOf(1) }
    val cur = EarningsPeriods[period]
    val max = (cur.values.maxOrNull() ?: 1).coerceAtLeast(1)
    TabPage("Earnings", DriverTab.Earnings, nav::tab) {
        ConnectedChoice(EarningsPeriods.map { it.label }, period, { period = it }, fontSize = 15)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.surfaceContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(cur.range, fontSize = 14.sp, color = c.onSurfaceVariant)
                Text(cur.total, fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            }
            Row(
                Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Earnings by ${listOf("hour", "day", "week")[period]}, highest bar highlighted" },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
            ) {
                cur.values.forEachIndexed { i, v ->
                    val target = maxOf(8f, v / max.toFloat() * 118f).dp
                    val h by animateDpAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(when { i == cur.highlight -> NovaTheme.colors.cta; v > 0 -> c.onSurfaceVariant; else -> c.surfaceContainerHigh }))
                        Text(cur.days[i], fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (i == cur.highlight) c.primary else c.onSurfaceVariant)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(cur.trips, "Trips", RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp), c.surfaceContainerHigh, c.onSurface) { nav.go(Trips) }
            StatTile(cur.hours, "Online", RoundedCornerShape(8.dp), c.surfaceContainerHigh, c.onSurface, null)
            StatTile(cur.tips, "Tips", RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp), c.primaryContainer, c.onPrimaryContainer) { nav.go(Quests) }
        }
    }
}

@Composable
private fun RowScope.StatTile(value: String, label: String, shape: RoundedCornerShape, bg: Color, fg: Color, onClick: (() -> Unit)?) {
    Surface(onClick = onClick ?: {}, enabled = onClick != null, shape = shape, color = bg, contentColor = fg, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, fontSize = 13.sp, color = if (bg == MaterialTheme.colorScheme.primaryContainer) fg else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TripsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var day by rememberSaveable { mutableIntStateOf(0) }
    SubPage("Trips", onBack = { nav.back() }) {
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "Yesterday", "Pick a date").forEachIndexed { i, label ->
                val on = i == day
                FilterChip(
                    selected = on, onClick = { day = i },
                    label = { Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold) },
                    leadingIcon = if (on) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                    shape = if (on) CircleShape else RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NovaTheme.colors.cta, selectedLabelColor = NovaTheme.colors.onCta, selectedLeadingIconColor = NovaTheme.colors.onCta),
                    modifier = Modifier.height(40.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(Triple("$312.40", "Earned", true), Triple("11", "Trips", false), Triple("6.5h", "Online", false)).forEachIndexed { i, (v, l, hi) ->
                Column(
                    Modifier.weight(if (i == 0) 1.3f else 1f).clip(when (i) { 0 -> RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp); 2 -> RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp); else -> RoundedCornerShape(8.dp) })
                        .background(if (hi) NovaTheme.colors.cta else c.surfaceContainerHigh).padding(14.dp),
                ) {
                    Text(v, fontWeight = FontWeight.ExtraBold, color = if (hi) NovaTheme.colors.onCta else c.onSurface, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 26.sp))
                    Text(l, fontSize = 13.sp, fontWeight = if (hi) FontWeight.Bold else FontWeight.Normal, color = if (hi) NovaTheme.colors.onCta else c.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TodayTrips.forEachIndexed { i, t ->
                Surface(onClick = { nav.go(Complete) }, shape = groupShape(i, TodayTrips.size), color = c.surfaceContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(t.time, Modifier.width(48.dp), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(t.route, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(t.detail, fontSize = 13.sp, color = c.onSurfaceVariant)
                        }
                        Text(t.earned, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuestsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val volt = NovaTheme.colors.cta
    SubPage("Quests & bonuses", onBack = { nav.back() }) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(NovaTheme.colors.onCta)) {
            ShapeBadge(NovaShape.Burst, 200.dp, volt.copy(alpha = 0.12f), Modifier.align(Alignment.TopEnd).offset(60.dp, (-50).dp))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WEEKEND QUEST · ENDS SUN 11:59 PM", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = volt)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("18", fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("/ 30 trips", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                WavyProgress(18 / 30f, color = volt, track = Color.White.copy(alpha = 0.18f))
                Text(buildAnnotatedString { append("7 more trips unlocks "); withStyle(SpanStyle(color = volt)) { append("+$70") } }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp).clip(groupShape(0, 3)).background(c.primaryContainer).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ShapeBadge(NovaShape.Cookie, 40.dp, c.primary) { Icon(NovaIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("15 trips", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
                Text("+$40 earned", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
            }
            Row(Modifier.fillMaxWidth().height(64.dp).clip(groupShape(1, 3)).background(c.surfaceContainer).border(2.dp, c.primary, groupShape(1, 3)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ShapeBadge(NovaShape.Cookie, 40.dp, c.surfaceContainerHighest) { Text("25", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                Text("25 trips", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("+$70", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.fillMaxWidth().height(64.dp).clip(groupShape(2, 3)).background(c.surfaceContainer).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ShapeBadge(NovaShape.Cookie, 40.dp, c.surfaceContainerHighest) { Text("30", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant) }
                Text("30 trips", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                Text("+$90", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant)
            }
        }
        Text("Boost zones today", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoostTile("1.5×", "Viaduct Harbour", "5–9 PM", c.tertiaryContainer, c.onTertiaryContainer, RoundedCornerShape(28.dp))
            BoostTile("+$4", "Airport runs", "Per trip, all day", c.secondaryContainer, c.onSecondaryContainer, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp))
        }
    }
}

@Composable
private fun RowScope.BoostTile(big: String, title: String, sub: String, bg: Color, fg: Color, shape: RoundedCornerShape) {
    Column(Modifier.weight(1f).clip(shape).background(bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(big, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = fg)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg)
        Text(sub, fontSize = 13.sp, color = fg)
    }
}

@Composable
fun PayoutsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var state by rememberSaveable { mutableIntStateOf(0) } // 0 ready, 1 sending, 2 sent
    val ink = NovaTheme.colors.onCta
    val radius by animateDpAsState(if (state > 0) 20.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    TabPage("Wallet", DriverTab.Wallet, nav::tab) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(NovaTheme.colors.cta)) {
            ShapeBadge(NovaShape.Burst, 180.dp, ink.copy(alpha = 0.07f), Modifier.align(Alignment.TopEnd).offset(40.dp, (-40).dp))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Ready to cash out", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                    Text(if (state == 2) "$0.00" else "$312.40", fontSize = 56.sp, lineHeight = 58.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = ink)
                }
                Surface(
                    onClick = { if (state == 0) scope.launch { state = 1; delay(1600); state = 2 } },
                    shape = RoundedCornerShape(radius), color = if (state == 2) NovaPalette.Mint else ink,
                    contentColor = if (state == 2) NovaPalette.OnMint else NovaTheme.colors.cta, modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (state == 1) { LoadingIndicator(Modifier.size(28.dp), color = NovaTheme.colors.cta); Spacer(Modifier.width(10.dp)) }
                        if (state == 2) { Icon(NovaIcons.Check, null); Spacer(Modifier.width(8.dp)) }
                        Text(listOf("Cash out now", "Sending to ANZ…", "Sent · arrives in minutes")[state], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().clip(groupShape(0, 2)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(NovaIcons.Bank, c.surfaceContainerHigh, c.onSurface)
                Column { Text("ANZ ···· 2210", fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Auto-payout daily at 11 PM", fontSize = 13.sp, color = c.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth().clip(groupShape(1, 2)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.tertiaryContainer), contentAlignment = Alignment.Center) { Text("$", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer) }
                Column(Modifier.weight(1f)) { Text("Tips today", fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Included in your payout", fontSize = 13.sp, color = c.onSurfaceVariant) }
                Text("$18.50", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("Recent payouts", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val rows = listOf("Fri 13 Nov" to "$224.80", "Thu 12 Nov" to "$201.60", "Wed 11 Nov" to "$96.40")
            rows.forEachIndexed { i, (d, a) ->
                Row(Modifier.fillMaxWidth().clip(groupShape(i, rows.size)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(d, Modifier.weight(1f), fontSize = 15.sp); Text(a, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

private data class InboxItem(val title: String, val body: String, val time: String, val icon: ImageVector, val tone: Int, val unread: Boolean)

@Composable
fun InboxScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val tones = listOf(c.tertiaryContainer to c.onTertiaryContainer, c.primaryContainer to c.onPrimaryContainer, c.secondaryContainer to c.onSecondaryContainer, c.errorContainer to c.onErrorContainer)
    val today = listOf(
        InboxItem("Viaduct Harbour is busy", "1.5× fares until 9 PM · 6 km from you", "4:52 PM", NovaIcons.Flame, 0, true),
        InboxItem("Payout sent · $286.50", "Arrives in your ASB account today", "11:02 PM yesterday", NovaIcons.Bank, 1, true),
        InboxItem("New compliment", "\"Smooth driving\" from Priya", "10:44 AM", NovaIcons.Star, 2, false),
    )
    val week = listOf(
        InboxItem("Certificate of Fitness expires soon", "Renew within 18 days to keep driving", "Wed", NovaIcons.Warning, 3, false),
        InboxItem("Weekend quest is live", "Up to +$90 for 30 trips", "Mon", NovaIcons.Trophy, 1, false),
    )
    SubPage("Inbox", onBack = { nav.back() }) {
        listOf("Today" to today, "This week" to week).forEach { (label, items) ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            SegmentedColumn {
                items.forEachIndexed { i, n ->
                    NovaListItem(
                        i, items.size, n.title, supporting = n.body + "\n" + n.time,
                        leading = { IconTile(n.icon, tones[n.tone].first, tones[n.tone].second, size = 44.dp, radius = 16.dp) },
                        trailing = if (n.unread) { { Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary).semantics { contentDescription = "Unread" }) } } else null,
                        onClick = {
                            when (n.icon) {
                                NovaIcons.Flame, NovaIcons.Trophy -> nav.go(Quests)
                                NovaIcons.Bank -> nav.tab(DriverTab.Wallet)
                                NovaIcons.Warning -> nav.go(DocExpired)
                                else -> nav.tab(DriverTab.Profile)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    TabPage(null, DriverTab.Profile, nav::tab) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CookieAvatar(Driver.initials, c.secondaryContainer, NovaPalette.OnCobaltContainer, size = 84.dp, shape = NovaShape.Sunny)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Driver.fullName, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("${Driver.car} · ${Driver.plate}", fontSize = 14.sp, color = c.onSurfaceVariant)
                Text("Driving with Nova since 2025", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(Driver.rating, "Rating", true), Triple("91%", "Acceptance", false), Triple("1,284", "Trips", false)).forEach { (v, l, hi) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(if (hi) NovaTheme.colors.cta else c.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(v, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = if (hi) NovaTheme.colors.onCta else c.onSurface)
                    Text(l, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (hi) NovaTheme.colors.onCta else c.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Top compliments", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Smooth driving", "214", true), Triple("Clean car", "168", false), Triple("Knew the route", "97", false)).forEach { (t, n, hi) ->
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (hi) c.primaryContainer else c.surfaceContainerHigh).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (hi) c.onPrimaryContainer else c.onSurface)
                        Text(n, fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (hi) c.onPrimaryContainer else c.onSurface)
                    }
                }
            }
        }
        SegmentedColumn {
            NovaListItem(0, 3, "Documents", leading = { Icon(NovaIcons.Doc, null) }, trailing = {
                Box(Modifier.height(24.dp).clip(CircleShape).background(NovaTheme.colors.coralBright).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("1 expiring", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                }
            }, onClick = { nav.go(DocExpired) })
            NovaListItem(1, 3, "Vehicle", leading = { Icon(NovaIcons.Car, null) }, onClick = { nav.go(Vehicle) })
            NovaListItem(2, 3, "Trip preferences", leading = { Icon(NovaIcons.Sliders, null) }, onClick = { nav.go(Prefs) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Settings", NovaIcons.Sliders, Settings), Triple("Help", NovaIcons.Help, Help)).forEach { (t, icon, route) ->
                Surface(onClick = { nav.go(route) }, shape = RoundedCornerShape(18.dp), color = c.surfaceContainerHigh, modifier = Modifier.weight(1f).height(56.dp)) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrefsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val eligible = mapOf("Go" to true, "Comfort" to true, "Electric" to false, "XL" to false)
    val types = remember { mutableStateListOf("Go", "Comfort") }
    var km by rememberSaveable { mutableIntStateOf(1) }
    var pets by rememberSaveable { mutableStateOf(true) }
    var long by rememberSaveable { mutableStateOf(false) }
    SubPage("Trip preferences", onBack = { nav.back() }) {
        Surface(onClick = { nav.goOnline() }, shape = RoundedCornerShape(28.dp), color = NovaTheme.colors.onCta, contentColor = Color.White) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ShapeBadge(NovaShape.Cookie, 52.dp, NovaTheme.colors.cta) { Icon(NovaIcons.HomeFilled, null, tint = NovaTheme.colors.onCta) }
                Column(Modifier.weight(1f)) {
                    Text("Head home mode", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Only trips toward Mt Albert · 2 of 2 left today", fontSize = 14.sp, color = c.outlineVariant)
                }
                Icon(NovaIcons.ArrowRight, null)
            }
        }
        Text("Ride types", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            eligible.forEach { (k, ok) ->
                val on = k in types
                FilterChip(
                    selected = on && ok, enabled = ok,
                    onClick = { if (on) types.remove(k) else types.add(k) },
                    label = { Text(k, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) },
                    leadingIcon = if (on && ok) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                    shape = if (on && ok) CircleShape else RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.primaryContainer, selectedLabelColor = c.onPrimaryContainer, selectedLeadingIconColor = c.onPrimaryContainer),
                    modifier = Modifier.height(44.dp),
                )
            }
        }
        Text("Greyed types need a different vehicle.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-6).dp))
        Text("Max pickup distance", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(3, 5, 8, 12).forEachIndexed { i, v ->
                val on = i == km
                val r by animateDpAsState(if (on) 22.dp else 10.dp, MaterialTheme.motionScheme.fastSpatialSpec())
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(r)).background(if (on) NovaTheme.colors.cta else c.surfaceContainerHighest)
                        .selectable(on, onClick = { km = i }, role = Role.RadioButton),
                    contentAlignment = Alignment.Center,
                ) { Text("$v km", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) NovaTheme.colors.onCta else c.onSurface) }
            }
        }
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Paw, "Accept pets", "Riders can request pet-friendly cars", pets) { pets = it }
            SwitchRow(1, 2, NovaIcons.Route, "Long trips (45+ min)", "Show trips outside Auckland", long) { long = it }
        }
    }
}

@Composable
fun SettingsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var navApp by rememberSaveable { mutableIntStateOf(0) }
    var theme by rememberSaveable { mutableIntStateOf(0) }
    var voice by rememberSaveable { mutableStateOf(true) }
    var chime by rememberSaveable { mutableStateOf(true) }
    SubPage("Settings", onBack = { nav.back() }) {
        Group("Navigation app")
        ConnectedChoice(listOf("Nova", "Google Maps", "Waze"), navApp, { navApp = it })
        Group("Theme")
        ConnectedChoice(listOf("Auto", "Light", "Dark"), theme, { theme = it })
        Text("Auto switches to dark after sunset for night shifts.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp))
        Group("Sound")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Volume, "Voice guidance", "Turn-by-turn directions", voice) { voice = it }
            SwitchRow(1, 2, NovaIcons.Bell, "Request chime", "Plays even on silent", chime) { chime = it }
        }
        SegmentedColumn {
            NovaListItem(0, 2, "Trip preferences", supporting = "Ride types, distance, pets", leading = { Icon(NovaIcons.Sliders, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(Prefs) })
            NovaListItem(1, 2, "Language", supporting = "English (NZ)", leading = { Icon(NovaIcons.Globe, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = {})
        }
        Group("Design preview · edge cases")
        val edges: List<Pair<String, Route>> = listOf("Rider no-show" to NoShow, "Rider cancelled" to RiderCancelled, "Connection lost on trip" to ConnectionLost, "Document expired" to DocExpired, "SOS active" to SosActive)
        SegmentedColumn {
            edges.forEachIndexed { i, (label, route) ->
                NovaListItem(i, edges.size, label, leading = { Icon(NovaIcons.Warning, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(route) })
            }
        }
        NovaOutlinedButton("Log out", onClick = { nav.reset(Login) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), height = 52.dp, leadingIcon = NovaIcons.Logout)
    }
}

@Composable
private fun Group(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
fun HelpScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    SubPage("Driver support", onBack = { nav.back() }) {
        Surface(onClick = { nav.go(SosActive) }, shape = RoundedCornerShape(24.dp), color = c.errorContainer, contentColor = c.onErrorContainer) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(NovaIcons.Shield, c.error, Color.White, size = 48.dp, radius = 16.dp, iconSize = 22.dp)
                Column(Modifier.weight(1f)) {
                    Text("In danger? Call 111", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Shares your live location automatically", fontSize = 14.sp)
                }
                Icon(NovaIcons.Chev, null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { dial(context, "0800668200") }, shape = RoundedCornerShape(28.dp), color = NovaTheme.colors.cta, contentColor = NovaTheme.colors.onCta, modifier = Modifier.weight(1f).height(96.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(NovaIcons.Phone, null); Text("Call support · 24/7", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Surface(onClick = { nav.go(Chat) }, shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh, modifier = Modifier.weight(1f).height(96.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(NovaIcons.Msg, null); Text("Chat with us", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Row(Modifier.fillMaxWidth().border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(NovaIcons.Receipt, null)
            Column(Modifier.weight(1f)) {
                Text("Fare adjustment · #48213", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Toll on Northern Gateway · updated 2h ago", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Box(Modifier.height(28.dp).clip(CircleShape).background(c.tertiaryContainer).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text("In review", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer) }
        }
        Text("Topics", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        val topics = listOf(
            Triple("Earnings & payouts", NovaIcons.Wallet, c.primaryContainer to c.onPrimaryContainer),
            Triple("Account & documents", NovaIcons.Doc, c.secondaryContainer to c.onSecondaryContainer),
            Triple("Vehicle & insurance", NovaIcons.Car, c.tertiaryContainer to c.onTertiaryContainer),
            Triple("Report a rider or trip", NovaIcons.Flag, c.surfaceContainerHigh to c.onSurface),
        )
        SegmentedColumn {
            topics.forEachIndexed { i, (t, icon, tone) ->
                NovaListItem(i, topics.size, t, leading = { IconTile(icon, tone.first, tone.second) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(Chat) })
            }
        }
    }
}

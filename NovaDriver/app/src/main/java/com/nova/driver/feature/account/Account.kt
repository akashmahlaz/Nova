package com.nova.driver.feature.account

import com.nova.driver.data.DriverStore
import com.nova.driver.data.NavPrefs
import com.nova.driver.data.formatKm
import com.nova.driver.data.formatMoney
import com.nova.driver.data.api.AppNotification
import com.nova.driver.data.api.DriverApi
import com.nova.driver.data.api.DriverTrip
import com.nova.driver.data.api.Earnings
import com.nova.driver.data.api.errorMessage
import com.nova.driver.data.auth.AuthStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

// ---------- shared helpers ----------

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun local(iso: String?): ZonedDateTime? = iso?.let { runCatching { Instant.parse(it).atZone(zone) }.getOrNull() }
private fun hours(seconds: Long): String = if (seconds < 3600) "${seconds / 60}m" else "%.1fh".format(seconds / 3600.0)
private fun money(e: Earnings?): String = e?.currency?.let { formatMoney(e.totalCents, it) } ?: "—"

/** Day = 3-hour slots, Week = Mon–Sun, Month = weeks of the month. Times in the phone's zone. */
private data class Period(val label: String, val range: String, val from: ZonedDateTime, val to: ZonedDateTime, val labels: List<String>, val slot: (ZonedDateTime) -> Int)

private fun periods(now: ZonedDateTime): List<Period> {
    val day = now.toLocalDate().atStartOfDay(zone)
    val monday = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zone)
    val month = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
    val end = now.plusMinutes(1)
    return listOf(
        Period("Day", "Today, ${now.format(DateTimeFormatter.ofPattern("EEE d MMM"))}", day, end,
            listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p")) { it.hour / 3 },
        Period("Week", "${monday.format(DateTimeFormatter.ofPattern("d MMM"))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))}", monday, end,
            listOf("M", "T", "W", "T", "F", "S", "S")) { it.dayOfWeek.value - 1 },
        Period("Month", "${now.format(DateTimeFormatter.ofPattern("MMMM"))} so far", month, end,
            listOf("W1", "W2", "W3", "W4", "W5")) { ((it.dayOfMonth - 1) / 7).coerceAtMost(4) },
    )
}

@Composable
fun EarningsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var period by rememberSaveable { mutableIntStateOf(1) }
    val all = remember { periods(ZonedDateTime.now(zone)) }
    val cur = all[period]
    var data by remember(period) { mutableStateOf<Earnings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period) {
        runCatching { DriverApi.earnings(cur.from.toInstant().toString(), cur.to.toInstant().toString()) }
            .onSuccess { data = it; error = null }.onFailure { error = errorMessage(it) }
    }
    val values = remember(data) {
        val v = LongArray(cur.labels.size)
        data?.rides?.forEach { r -> local(r.completedAt)?.let { v[cur.slot(it).coerceIn(0, v.lastIndex)] += r.fareCents } }
        v.toList()
    }
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val highlight = values.indexOf(values.maxOrNull() ?: 0L).takeIf { (values.maxOrNull() ?: 0L) > 0 } ?: -1
    TabPage("Earnings", DriverTab.Earnings, nav::tab) {
        ConnectedChoice(all.map { it.label }, period, { period = it }, fontSize = 15)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.surfaceContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(cur.range, fontSize = 14.sp, color = c.onSurfaceVariant)
                Text(if (data == null && error == null) "…" else money(data).takeIf { data?.currency != null } ?: "No trips yet", fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, maxLines = 1)
                error?.let { Text(it, fontSize = 13.sp, color = c.error) }
            }
            Row(
                Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Earnings by ${listOf("time of day", "day", "week")[period]}" },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
            ) {
                values.forEachIndexed { i, v ->
                    val target = maxOf(8f, v / max.toFloat() * 118f).dp
                    val h by animateDpAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(when { i == highlight -> NovaTheme.colors.cta; v > 0 -> c.onSurfaceVariant; else -> c.surfaceContainerHigh }))
                        Text(cur.labels[i], fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (i == highlight) c.primary else c.onSurfaceVariant)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val avg = data?.takeIf { it.trips > 0 && it.currency != null }?.let { formatMoney(it.totalCents / it.trips, it.currency!!) } ?: "—"
            StatTile("${data?.trips ?: 0}", "Trips", RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp), c.surfaceContainerHigh, c.onSurface) { nav.go(Trips) }
            StatTile(hours(data?.onlineSeconds ?: 0), "Online", RoundedCornerShape(8.dp), c.surfaceContainerHigh, c.onSurface, null)
            StatTile(avg, "Per trip", RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp), c.primaryContainer, c.onPrimaryContainer, null)
        }
        Text("Fares of completed trips. Service fees and tips will show here once they apply.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun RowScope.StatTile(value: String, label: String, shape: RoundedCornerShape, bg: Color, fg: Color, onClick: (() -> Unit)?) {
    Surface(onClick = onClick ?: {}, enabled = onClick != null, shape = shape, color = bg, contentColor = fg, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontWeight = FontWeight.ExtraBold, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp))
            Text(label, fontSize = 13.sp, color = if (bg == MaterialTheme.colorScheme.primaryContainer) fg else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TripsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var day by rememberSaveable { mutableIntStateOf(0) }
    val now = remember { ZonedDateTime.now(zone) }
    val (from, to) = remember(day) {
        val today = now.toLocalDate().atStartOfDay(zone)
        when (day) {
            0 -> today to now.plusMinutes(1)
            1 -> today.minusDays(1) to today
            else -> now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zone) to now.plusMinutes(1)
        }
    }
    var trips by remember(day) { mutableStateOf<List<DriverTrip>?>(null) }
    var earn by remember(day) { mutableStateOf<Earnings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(day) {
        runCatching { DriverApi.trips(from.toInstant().toString(), to.toInstant().toString(), 200) }
            .onSuccess { trips = it; error = null }.onFailure { error = errorMessage(it) }
        runCatching { DriverApi.earnings(from.toInstant().toString(), to.toInstant().toString()) }.onSuccess { earn = it }
    }
    SubPage("Trips", onBack = { nav.back() }) {
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "Yesterday", "This week").forEachIndexed { i, label ->
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
            listOf(Triple(money(earn), "Earned", true), Triple("${earn?.trips ?: 0}", "Trips", false), Triple(hours(earn?.onlineSeconds ?: 0), "Online", false)).forEachIndexed { i, (v, l, hi) ->
                Column(
                    Modifier.weight(if (i == 0) 1.3f else 1f).clip(when (i) { 0 -> RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp); 2 -> RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp); else -> RoundedCornerShape(8.dp) })
                        .background(if (hi) NovaTheme.colors.cta else c.surfaceContainerHigh).padding(14.dp),
                ) {
                    Text(v, fontWeight = FontWeight.ExtraBold, color = if (hi) NovaTheme.colors.onCta else c.onSurface, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 26.sp))
                    Text(l, fontSize = 13.sp, fontWeight = if (hi) FontWeight.Bold else FontWeight.Normal, color = if (hi) NovaTheme.colors.onCta else c.onSurfaceVariant)
                }
            }
        }
        error?.let { Text(it, fontSize = 14.sp, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        val list = trips
        when {
            list == null && error == null -> LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).size(48.dp), color = c.primary)
            list != null && list.isEmpty() -> Text("No trips ${listOf("today", "yesterday", "this week")[day]} yet.", fontSize = 15.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
            list != null -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                list.forEachIndexed { i, t ->
                    val done = t.status == "completed"
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(groupShape(i, list.size)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(local(t.endedAt)?.format(DateTimeFormatter.ofPattern(if (day == 2) "EEE\nH:mm" else "H:mm")) ?: "", Modifier.width(48.dp), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text("${t.pickupName} → ${t.dropoffName}", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(listOfNotNull(t.productName, formatKm(t.distanceMeters), t.riderName, if (!done) (if (t.cancelReason == "rider_no_show") "No-show" else "Cancelled") else null).joinToString(" · "), fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1)
                        }
                        Text(if (done) formatMoney(t.fareCents, t.currency) else "—", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (done) c.onSurface else c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** No bonus programme exists yet; when one does, its quests list here. */
@Composable
fun QuestsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val volt = NovaTheme.colors.cta
    SubPage("Quests & bonuses", onBack = { nav.back() }) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(NovaTheme.colors.onCta)) {
            ShapeBadge(NovaShape.Burst, 200.dp, volt.copy(alpha = 0.12f), Modifier.align(Alignment.TopEnd).offset(60.dp, (-50).dp))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QUESTS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = volt)
                Text("No quests running right now", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("When Nova runs trip bonuses in your city, you'll see them here and get a notification.", fontSize = 15.sp, color = c.outlineVariant)
            }
        }
        val week = DriverStore.week
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoostTile("${week?.trips ?: 0}", "Trips this week", "Counts toward future quests", c.tertiaryContainer, c.onTertiaryContainer, RoundedCornerShape(28.dp))
            BoostTile(hours(week?.onlineSeconds ?: 0), "Online this week", "Since Monday", c.secondaryContainer, c.onSecondaryContainer, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp))
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
    val ink = NovaTheme.colors.onCta
    LaunchedEffect(Unit) { DriverStore.refreshEarnings() }
    val week = DriverStore.week
    val payout = DriverStore.me?.payout
    TabPage("Wallet", DriverTab.Wallet, nav::tab) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(NovaTheme.colors.cta)) {
            ShapeBadge(NovaShape.Burst, 180.dp, ink.copy(alpha = 0.07f), Modifier.align(Alignment.TopEnd).offset(40.dp, (-40).dp))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Earned this week", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                    Text(money(week).takeIf { week?.currency != null } ?: "No trips yet", fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = ink, maxLines = 1)
                    Text("${week?.trips ?: 0} trips · ${hours(week?.onlineSeconds ?: 0)} online", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ink.copy(alpha = 0.08f)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NovaIcons.Info, null, tint = ink)
                    Text("Automatic bank payouts are being set up. Every trip is recorded and will be paid out.", fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = ink)
                }
            }
        }
        Surface(onClick = { nav.go(Bank) }, shape = RoundedCornerShape(20.dp), color = c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(NovaIcons.Bank, c.surfaceContainerHigh, c.onSurface)
                Column(Modifier.weight(1f)) {
                    Text(payout?.let { "${it.accountName} ···· ${it.accountLast4}" } ?: "Add your bank account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(payout?.let { "Payouts ${it.payoutSchedule}" + if (it.gstRegistered) " · GST registered" else "" } ?: "Needed to get paid", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                Icon(NovaIcons.Chev, null)
            }
        }
        Text("Recent payouts", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        Text("No payouts yet.", fontSize = 15.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
fun InboxScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var items by remember { mutableStateOf<List<AppNotification>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { DriverApi.notifications() }.onSuccess { items = it.first; error = null }.onFailure { error = errorMessage(it) }
        runCatching { DriverApi.markAllRead() }.onSuccess { DriverStore.markRead() }
    }
    val today = ZonedDateTime.now(zone).toLocalDate()
    SubPage("Inbox", onBack = { nav.back() }) {
        error?.let { Text(it, fontSize = 14.sp, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        val list = items
        if (list == null && error == null) LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).size(48.dp), color = c.primary)
        if (list != null && list.isEmpty()) Text("Nothing here yet. Approvals, document reviews and trip updates show up here.", fontSize = 15.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        if (list != null) {
            list.groupBy { if (local(it.createdAt)?.toLocalDate() == today) "Today" else "Earlier" }.forEach { (label, group) ->
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                SegmentedColumn {
                    group.forEachIndexed { i, n ->
                        val (icon, tone) = when {
                            n.event == "driver_approved" -> NovaIcons.Check to (c.primaryContainer to c.onPrimaryContainer)
                            n.event == "document_rejected" || n.event == "driver_pending" -> NovaIcons.Warning to (c.errorContainer to c.onErrorContainer)
                            n.event == "driver_suspended" -> NovaIcons.Shield to (c.errorContainer to c.onErrorContainer)
                            n.kind == "trip" -> NovaIcons.Car to (c.secondaryContainer to c.onSecondaryContainer)
                            else -> NovaIcons.Bell to (c.tertiaryContainer to c.onTertiaryContainer)
                        }
                        val time = local(n.createdAt)?.format(DateTimeFormatter.ofPattern(if (label == "Today") "h:mm a" else "EEE d MMM, h:mm a")) ?: ""
                        NovaListItem(
                            i, group.size, n.title, supporting = listOf(n.body, time).filter { it.isNotBlank() }.joinToString("\n"),
                            leading = { IconTile(icon, tone.first, tone.second, size = 44.dp, radius = 16.dp) },
                            trailing = if (n.readAt == null) { { Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary).semantics { contentDescription = "Unread" }) } } else null,
                            onClick = {
                                when (n.event) {
                                    "document_rejected", "driver_pending" -> nav.go(DocExpired)
                                    "driver_approved" -> nav.reset(Home)
                                    else -> if (n.kind == "trip") nav.go(Trips)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { runCatching { DriverStore.refreshMe() }; DriverStore.refreshEarnings() }
    val me = DriverStore.me
    val week = DriverStore.week
    val since = local(me?.driver?.createdAt)?.year
    val attention = me?.documents.orEmpty().count { it.required && (it.status == null || it.status == "rejected") }
    TabPage(null, DriverTab.Profile, nav::tab) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CookieAvatar(me?.user?.initials ?: "", c.secondaryContainer, NovaPalette.OnCobaltContainer, size = 84.dp, shape = NovaShape.Sunny)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(me?.user?.let { "${it.firstName} ${it.lastName}".trim() } ?: "", fontWeight = FontWeight.ExtraBold, maxLines = 2, autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 28.sp))
                Text(me?.vehicle?.let { "${it.label} · ${it.plate}" } ?: "No car added", fontSize = 14.sp, color = c.onSurfaceVariant, maxLines = 1)
                since?.let { Text("Driving with Nova since $it", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(money(week).takeIf { week?.currency != null } ?: "—", "This week", true), Triple("${week?.trips ?: 0}", "Trips", false), Triple(hours(week?.onlineSeconds ?: 0), "Online", false)).forEach { (v, l, hi) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(if (hi) NovaTheme.colors.cta else c.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(v, fontWeight = FontWeight.ExtraBold, color = if (hi) NovaTheme.colors.onCta else c.onSurface, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp))
                    Text(l, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (hi) NovaTheme.colors.onCta else c.onSurfaceVariant)
                }
            }
        }
        SegmentedColumn {
            NovaListItem(0, 4, "Documents", leading = { Icon(NovaIcons.Doc, null) }, trailing = {
                if (attention > 0) Box(Modifier.height(24.dp).clip(CircleShape).background(NovaTheme.colors.coralBright).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("$attention to fix", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                } else Icon(NovaIcons.Chev, null)
            }, onClick = { nav.go(DocExpired) })
            NovaListItem(1, 4, "Vehicle", leading = { Icon(NovaIcons.Car, null) }, onClick = { nav.go(Vehicle) })
            NovaListItem(2, 4, "Ride types", leading = { Icon(NovaIcons.Sliders, null) }, onClick = { nav.go(Prefs) })
            NovaListItem(3, 4, "Inbox", leading = { Icon(NovaIcons.Bell, null) }, onClick = { nav.go(Inbox) })
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

/** Ride types the car qualifies for, switchable; types it doesn't qualify for are shown greyed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrefsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { DriverStore.loadRules() }
    val mine = DriverStore.me?.products.orEmpty().associateBy { it.productId }
    SubPage("Ride types", onBack = { nav.back() }) {
        Text("Choose which requests you get", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverStore.rules.forEach { r ->
                val p = mine[r.id]
                val on = p?.enabled == true
                FilterChip(
                    selected = on, enabled = p != null,
                    onClick = {
                        // At least one type must stay on, or no requests can arrive.
                        if (on && mine.values.count { it.enabled } <= 1) { error = "Keep at least one ride type on."; return@FilterChip }
                        scope.launch { runCatching { DriverStore.setProduct(r.id, !on) }.onFailure { error = errorMessage(it) } }
                    },
                    label = { Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) },
                    leadingIcon = if (on) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                    shape = if (on) CircleShape else RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.primaryContainer, selectedLabelColor = c.onPrimaryContainer, selectedLeadingIconColor = c.onPrimaryContainer),
                    modifier = Modifier.height(44.dp),
                )
            }
        }
        Text("Greyed types need a different vehicle. Change your car in Profile › Vehicle.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
fun SettingsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navApps = listOf("google", "waze")
    var navApp by rememberSaveable { mutableIntStateOf(navApps.indexOf(NavPrefs.app(context)).coerceAtLeast(0)) }
    var theme by rememberSaveable { mutableIntStateOf(0) }
    var chime by rememberSaveable { mutableStateOf(NavPrefs.chime(context)) }
    var loggingOut by remember { mutableStateOf(false) }
    SubPage("Settings", onBack = { nav.back() }) {
        Group("Navigation app")
        ConnectedChoice(listOf("Google Maps", "Waze"), navApp, { navApp = it; NavPrefs.setApp(context, navApps[it]) })
        Group("Theme")
        ConnectedChoice(listOf("Auto", "Light", "Dark"), theme, { theme = it })
        Text("Auto switches to dark after sunset for night shifts.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp))
        Group("Sound")
        SegmentedColumn {
            SwitchRow(0, 1, NovaIcons.Bell, "Request chime", "Plays when a new request arrives", chime) { chime = it; NavPrefs.setChime(context, it) }
        }
        SegmentedColumn {
            NovaListItem(0, 2, "Ride types", supporting = "Which requests you get", leading = { Icon(NovaIcons.Sliders, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(Prefs) })
            NovaListItem(1, 2, "Language", supporting = "Uses your phone's language", leading = { Icon(NovaIcons.Globe, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = {
                val intent = if (android.os.Build.VERSION.SDK_INT >= 33)
                    android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                else android.content.Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
                runCatching { context.startActivity(intent) }
            })
        }
        if (com.nova.driver.BuildConfig.DEBUG) {
            Group("Design preview · edge cases (debug builds)")
            val edges: List<Pair<String, Route>> = listOf("Rider no-show" to NoShow, "Rider cancelled" to RiderCancelled, "Connection lost on trip" to ConnectionLost, "Documents" to DocExpired, "SOS" to SosActive)
            SegmentedColumn {
                edges.forEachIndexed { i, (label, route) ->
                    NovaListItem(i, edges.size, label, leading = { Icon(NovaIcons.Warning, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(route) })
                }
            }
        }
        NovaOutlinedButton(
            if (loggingOut) "Logging out…" else "Log out", modifier = Modifier.fillMaxWidth().padding(top = 4.dp), height = 52.dp, leadingIcon = NovaIcons.Logout,
            onClick = {
                if (loggingOut) return@NovaOutlinedButton
                loggingOut = true
                scope.launch {
                    // Leave dispatch first, then revoke this phone's refresh token on the server.
                    runCatching { if (DriverStore.state.online) DriverStore.goOffline() }
                    AuthStore.session?.refreshToken?.let { token -> runCatching { DriverApi.logout(token) } }
                    AuthStore.clearSession()
                    nav.reset(Welcome)
                }
            },
        )
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
                    Text("Opens your dialer with 111", fontSize = 14.sp)
                }
                Icon(NovaIcons.Chev, null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { dial(context, "0800668200") }, shape = RoundedCornerShape(28.dp), color = NovaTheme.colors.cta, contentColor = NovaTheme.colors.onCta, modifier = Modifier.weight(1f).height(96.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(NovaIcons.Phone, null); Text("Call support · 24/7", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Surface(onClick = { nav.go(Inbox) }, shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh, modifier = Modifier.weight(1f).height(96.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(NovaIcons.Msg, null); Text("Your inbox", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Text("Topics", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        val topics = listOf(
            Triple("Earnings & payouts", NovaIcons.Wallet, c.primaryContainer to c.onPrimaryContainer) to { nav.tab(DriverTab.Earnings) },
            Triple("Account & documents", NovaIcons.Doc, c.secondaryContainer to c.onSecondaryContainer) to { nav.go(DocExpired) },
            Triple("Vehicle & insurance", NovaIcons.Car, c.tertiaryContainer to c.onTertiaryContainer) to { nav.go(Vehicle) },
            Triple("Trip history", NovaIcons.Flag, c.surfaceContainerHigh to c.onSurface) to { nav.go(Trips) },
        )
        SegmentedColumn {
            topics.forEachIndexed { i, (t, go) ->
                NovaListItem(i, topics.size, t.first, leading = { IconTile(t.second, t.third.first, t.third.second) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = go)
            }
        }
    }
}

package com.nova.rider.feature.trip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.rider.data.Sample
import com.nova.rider.navigation.Call
import com.nova.rider.navigation.Chat
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Message(val me: Boolean, val text: String)

@Composable
fun ChatScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val messages = remember {
        mutableStateListOf(
            Message(false, "Kia ora! I’m at the corner by the café, white Prius."),
            Message(true, "Great, coming down now"),
            Message(false, "No rush, take your time"),
        )
    }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    fun send(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) { messages.add(Message(true, t)); draft = "" }
    }
    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.size) }
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Back, "Back") }
            CookieAvatar(Sample.driverInitials, c.primaryContainer, c.onPrimaryContainer, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(Sample.driverName, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("${Sample.carShort} · ${Sample.plate} · 3 min away", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = { nav.go(Call) },
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(16.dp), RoundedCornerShape(24.dp)),
                modifier = Modifier.size(48.dp),
            ) { Icon(NovaIcons.Phone, "Call ${Sample.driverName}") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(CircleShape).background(c.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(NovaIcons.Lock, null, Modifier.size(14.dp), tint = c.onSecondaryContainer)
                        Text("Your number stays private · chats are kept for safety", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSecondaryContainer)
                    }
                }
            }
            itemsIndexed(messages) { i, m ->
                val prevSame = i > 0 && messages[i - 1].me == m.me
                val nextSame = i < messages.lastIndex && messages[i + 1].me == m.me
                val big = 22.dp; val sm = 6.dp
                val shape = if (m.me) RoundedCornerShape(big, if (prevSame) sm else big, if (nextSame) sm else big, big)
                else RoundedCornerShape(if (prevSame) sm else big, big, big, if (nextSame) sm else big)
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.text,
                        Modifier.widthIn(max = 270.dp).clip(shape).background(if (m.me) c.primary else c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 16.sp, lineHeight = 22.sp, color = if (m.me) Color.White else c.onSurface,
                    )
                }
            }
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { Text("Read 10:31", fontSize = 12.sp, color = c.outline) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("On my way", "Can you wait 2 min?", "I’m at the entrance").forEach { q ->
                Surface(onClick = { send(q) }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, c.outline), color = Color.Transparent, modifier = Modifier.height(40.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(q, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f).height(56.dp).clip(CircleShape).background(c.surfaceContainerHighest).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) Text("Message ${Sample.driverName}", fontSize = 16.sp, color = c.onSurfaceVariant)
                BasicTextField(
                    draft, { draft = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(draft) }),
                )
            }
            FilledIconButton(
                onClick = { send(draft) },
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(18.dp), RoundedCornerShape(28.dp)),
                modifier = Modifier.size(56.dp),
            ) { Icon(NovaIcons.Send, "Send") }
        }
    }
}

@Composable
fun CallScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var mute by rememberSaveable { mutableStateOf(false) }
    var speaker by rememberSaveable { mutableStateOf(true) }
    var seconds by rememberSaveable { mutableIntStateOf(42) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    val ring = rememberInfiniteTransition(label = "ring")
    val t by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val ink = c.onPrimaryContainer
    Column(
        Modifier.fillMaxSize().background(c.primaryContainer).statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.height(32.dp).clip(CircleShape).background(ink.copy(alpha = 0.1f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(NovaIcons.Lock, null, Modifier.size(14.dp), tint = ink)
            Text("Calling via Nova · numbers hidden", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
        }
        Box(Modifier.padding(top = 24.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).graphicsLayer { val s = 0.7f + 0.4f * t; scaleX = s; scaleY = s; alpha = 0.25f * (1f - t) }.clip(CircleShape).background(c.primary))
            ShapeBadge(NovaShape.Cookie, 160.dp, Color.White) { Text(Sample.driverInitials, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = ink) }
        }
        Text(Sample.driverName, style = MaterialTheme.typography.headlineLarge, color = ink, modifier = Modifier.padding(top = 12.dp))
        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CallToggle("Mute", if (mute) NovaIcons.Micoff else NovaIcons.Mic, mute) { mute = it }
            CallToggle("Speaker", NovaIcons.Speaker, speaker) { speaker = it }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconButton(
                    onClick = { nav.replaceTop(Chat) },
                    shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(28.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ink.copy(alpha = 0.1f), contentColor = ink),
                    modifier = Modifier.size(80.dp),
                ) { Icon(NovaIcons.Msg, "Message", Modifier.size(28.dp)) }
                Text("Message", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
            }
        }
        FilledIconButton(
            onClick = { nav.back() },
            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(28.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.error, contentColor = Color.White),
            modifier = Modifier.padding(top = 18.dp).size(width = 200.dp, height = 80.dp),
        ) { Icon(NovaIcons.Endcall, "End call", Modifier.size(32.dp)) }
    }
}

@Composable
private fun CallToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = onChange,
            shapes = IconButtonDefaults.toggleableShapes(CircleShape, RoundedCornerShape(20.dp), RoundedCornerShape(28.dp)),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = ink.copy(alpha = 0.1f), contentColor = ink,
                checkedContainerColor = ink, checkedContentColor = Color.White,
            ),
            modifier = Modifier.size(80.dp),
        ) { Icon(icon, label, Modifier.size(28.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

private data class Contact(val key: String, val name: String, val bg: Color, val fg: Color)

@Composable
fun ShareTripScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val people = listOf(
        Contact("M", "Mum", c.primaryContainer, c.onPrimaryContainer),
        Contact("R", "Rohan", NovaPalette.Mint, NovaPalette.OnMint),
        Contact("J", "Jess", c.tertiaryContainer, c.onTertiaryContainer),
    )
    val selected = remember { mutableStateListOf("M", "R") }
    var auto by rememberSaveable { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    fun close(after: () -> Unit = {}) = scope.launch { sheet.hide() }.invokeOnCompletion { after() }
    Box(Modifier.fillMaxSize()) {
        EnRouteMap()
        ModalBottomSheet(onDismissRequest = { nav.back() }, sheetState = sheet, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Share your trip", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    Text("They'll see your live location, car and ETA until you arrive.", fontSize = 15.sp, color = c.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    people.forEach { p ->
                        val on = p.key in selected
                        val radius by animateDpAsState(if (on) 22.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec())
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).toggleable(on, role = Role.Checkbox) { if (it) selected.add(p.key) else selected.remove(p.key) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(64.dp).then(if (on) Modifier.border(3.dp, c.primary, RoundedCornerShape(radius + 3.dp)).padding(0.dp) else Modifier)
                                    .clip(RoundedCornerShape(radius)).background(p.bg),
                                contentAlignment = Alignment.Center,
                            ) { Text(p.key, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = p.fg) }
                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(64.dp).border(2.dp, c.outline, CircleShape), contentAlignment = Alignment.Center) { Icon(NovaIcons.Plus, "Add contact") }
                        Text("Add", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(NovaIcons.Link, null)
                    Text("nova.nz/t/K7Q2-PNSB", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("Nova trip link", "https://nova.nz/t/K7Q2-PNSB")))
                                copied = true
                            }
                        },
                        shape = CircleShape, color = c.secondaryContainer, contentColor = c.onSecondaryContainer, modifier = Modifier.height(40.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (copied) NovaIcons.Check else NovaIcons.Copy, null, Modifier.size(16.dp))
                            Text(if (copied) "Copied" else "Copy", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 4.dp).toggleable(auto, role = Role.Switch) { auto = it }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-share trips after 9 PM", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("With the people selected above", fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                    Switch(checked = auto, onCheckedChange = null)
                }
                val n = selected.size
                NovaButton(
                    if (n > 0) "Share with $n ${if (n > 1) "people" else "person"}" else "Choose who to share with",
                    onClick = { close { nav.back() } }, enabled = n > 0,
                )
            }
        }
    }
}

@Composable
fun CancelRideScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reasons = listOf("Driver is taking too long", "I changed my plans", "Booked by mistake", "Driver asked me to cancel", "Something else")
    var reason by rememberSaveable { mutableIntStateOf(-1) }
    var freeSeconds by rememberSaveable { mutableIntStateOf(48) }
    LaunchedEffect(Unit) { while (freeSeconds > 0) { delay(1000); freeSeconds-- } }
    Box(Modifier.fillMaxSize()) {
        EnRouteMap()
        ModalBottomSheet(onDismissRequest = { nav.back() }, sheetState = sheet, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cancel this ride?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    val free = freeSeconds > 0
                    Row(
                        Modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).background(if (free) NovaPalette.Mint else c.errorContainer).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val fg = if (free) NovaPalette.OnMint else c.onErrorContainer
                        Icon(NovaIcons.Clock, null, Modifier.size(16.dp), tint = fg)
                        Text(if (free) "Free to cancel for 0:%02d more".format(freeSeconds) else "A $6.00 fee now applies", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                    }
                    Text("After that, a $6.00 fee goes to ${Sample.driverName} for his time.", fontSize = 14.sp, color = c.onSurfaceVariant)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    reasons.forEachIndexed { i, t ->
                        val on = i == reason
                        val shape = when (i) {
                            0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)
                            reasons.lastIndex -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)
                            else -> RoundedCornerShape(4.dp)
                        }
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clip(shape).background(if (on) c.errorContainer else c.surfaceContainer)
                                .selectable(on, onClick = { reason = i }, role = Role.RadioButton).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = on, onClick = null,
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = c.error, unselectedColor = c.onSurfaceVariant),
                            )
                            Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.onErrorContainer else c.onSurface)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaButton("Keep my ride", onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { nav.back() } }, modifier = Modifier.weight(1f), height = 56.dp)
                    NovaButton(
                        "Cancel ride", onClick = { nav.reset(Home) }, modifier = Modifier.weight(1f), height = 56.dp, enabled = reason >= 0,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.errorContainer, contentColor = c.onErrorContainer),
                    )
                }
            }
        }
    }
}

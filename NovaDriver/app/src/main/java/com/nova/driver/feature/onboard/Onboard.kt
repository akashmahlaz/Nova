package com.nova.driver.feature.onboard

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
import com.nova.driver.feature.common.*
import com.nova.driver.navigation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun SplashScreen(nav: Navigator) {
    LaunchedEffect(Unit) { delay(1800); nav.reset(Welcome) }
    val infinite = rememberInfiniteTransition(label = "splash")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "spin")
    val pulse by infinite.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    val ink = NovaTheme.colors.onCta
    Box(
        Modifier.fillMaxSize().background(NovaTheme.colors.cta).clickable { nav.reset(Welcome) }
            .semantics { contentDescription = "Nova Driver. Tap to continue" },
    ) {
        ShapeBadge(NovaShape.Burst, 620.dp, ink, Modifier.offset((-115).dp, (-240).dp).alpha(0.06f).rotate(spin))
        ShapeBadge(NovaShape.Cookie, 300.dp, ink, Modifier.align(Alignment.BottomEnd).offset(110.dp, 80.dp).alpha(0.05f))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeBadge(NovaShape.Burst, 132.dp, ink, Modifier.scale(pulse)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(NovaTheme.colors.cta))
            }
            Spacer(Modifier.height(28.dp))
            Text("nova", color = ink, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(32.dp).clip(CircleShape).background(ink).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("DRIVER", color = NovaTheme.colors.cta, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
        }
        Text("Drive on your terms.", color = ink, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp))
    }
}

@Composable
private fun DriverWordmark() {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("nova", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
        Box(Modifier.height(22.dp).clip(CircleShape).background(NovaTheme.colors.cta).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text("DRIVER", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, color = NovaTheme.colors.onCta)
        }
    }
}

@Composable
fun WelcomeScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DriverWordmark()
        Text(
            buildAnnotatedString { append("Your car.\nYour hours.\n"); withStyle(SpanStyle(color = c.primary)) { append("Your money.") } },
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 50.sp), modifier = Modifier.padding(horizontal = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(240.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(NovaTheme.colors.cta)) {
                ShapeBadge(NovaShape.Burst, 150.dp, NovaTheme.colors.onCta.copy(alpha = 0.08f), Modifier.align(Alignment.TopEnd).offset(40.dp, (-30).dp))
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("$", fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, color = NovaTheme.colors.onCta)
                    Text("Cash out every day", fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, color = NovaTheme.colors.onCta)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PerkTile("Go online anytime", NovaIcons.Power, c.surfaceContainerHigh, c.onSurface, RoundedCornerShape(28.dp))
                PerkTile("24×7 driver support", NovaIcons.Help, c.tertiaryContainer, c.onTertiaryContainer, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        NovaButton("Start driving", onClick = { nav.go(SignUp) }, height = 96.dp, trailingIcon = NovaIcons.ArrowRight, textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp))
        TextButton(onClick = { nav.go(Login) }, modifier = Modifier.fillMaxWidth().height(48.dp).offset(y = (-8).dp)) {
            Text("I already drive with Nova", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.primary)
        }
    }
}

@Composable
private fun ColumnScope.PerkTile(text: String, icon: ImageVector, bg: Color, fg: Color, shape: RoundedCornerShape) {
    Column(Modifier.weight(1f).fillMaxWidth().clip(shape).background(bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = fg)
        Text(text, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

@Composable
private fun PhonePrefix(text: String) {
    Box(
        Modifier.height(64.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SignUpScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var first by rememberSaveable { mutableStateOf(Driver.firstName) }
    var last by rememberSaveable { mutableStateOf("Kumar") }
    var phone by rememberSaveable { mutableStateOf(Driver.phone) }
    var email by rememberSaveable { mutableStateOf(Driver.email) }
    var city by rememberSaveable { mutableStateOf("Auckland") }
    var cityMenu by remember { mutableStateOf(false) }
    var endorsement by rememberSaveable { mutableIntStateOf(0) }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(0) }, bottom = {
        NovaButton("Continue", onClick = { nav.go(Otp) }, enabled = first.isNotBlank() && phone.count(Char::isDigit) >= 8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Already driving with Nova? ", fontSize = 15.sp, color = c.onSurfaceVariant)
            Text("Log in", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.primary, modifier = Modifier.clickable { nav.go(Login) })
        }
    }) {
        Text("Let's get you\non the road", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 38.sp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NovaTextField(first, { first = it }, "First name", Modifier.weight(1f), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            NovaTextField(last, { last = it }, "Last name", Modifier.weight(1f), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            PhonePrefix("+64")
            NovaTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(13) }, "Mobile", Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }
        NovaTextField(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Box {
            Surface(onClick = { cityMenu = true }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.outline), color = c.surface, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("City you'll drive in", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                        Text(city, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(NovaIcons.Down, null)
                }
            }
            androidx.compose.material3.DropdownMenu(expanded = cityMenu, onDismissRequest = { cityMenu = false }) {
                listOf("Auckland", "Wellington", "Christchurch", "Hamilton", "Tauranga", "Dunedin").forEach { name ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(name) }, onClick = { city = name; cityMenu = false })
                }
            }
        }
        Text("Do you have a P endorsement?", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        ConnectedChoice(listOf("Yes, I have one", "Applying"), endorsement, { endorsement = it })
        if (endorsement == 1) {
            Text(
                "No problem — you can finish sign-up now. We'll guide you through the NZTA application before your first trip.",
                fontSize = 13.sp, lineHeight = 19.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun LoginScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var phone by rememberSaveable { mutableStateOf(Driver.phone) }
    SubPage(null, onBack = { nav.back() }, bottom = {
        TextButton(onClick = { nav.go(Help) }, modifier = Modifier.align(Alignment.CenterHorizontally).height(48.dp)) {
            Text("Trouble logging in?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("New driver? ", fontSize = 15.sp, color = c.onSurfaceVariant)
            Text("Sign up to drive", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.primary, modifier = Modifier.clickable { nav.go(SignUp) })
        }
    }) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShapeBadge(NovaShape.Cookie, 64.dp, NovaTheme.colors.cta) { Icon(NovaIcons.Car, null, tint = NovaTheme.colors.onCta) }
                Box(Modifier.height(26.dp).clip(CircleShape).background(NovaTheme.colors.onCta).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("DRIVER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = NovaTheme.colors.cta)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome back,\nlet's drive", style = MaterialTheme.typography.headlineLarge)
                Text("Log in with your driver mobile number.", fontSize = 16.sp, lineHeight = 24.sp, color = c.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                PhonePrefix("NZ +64")
                NovaTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(13) }, "Mobile number", Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
            NovaButton("Send code", onClick = { nav.go(Otp) }, enabled = phone.count(Char::isDigit) >= 8)
            Surface(onClick = { nav.reset(Home) }, shape = RoundedCornerShape(20.dp), color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(NovaIcons.Finger, null)
                    Column(Modifier.weight(1f)) {
                        Text("Use a passkey", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Faster, safer sign-in on this phone", fontSize = 13.sp)
                    }
                    Icon(NovaIcons.Chev, null)
                }
            }
        }
    }
}

@Composable
fun OtpScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var code by rememberSaveable { mutableStateOf("5803") }
    var seconds by rememberSaveable { mutableIntStateOf(31) }
    LaunchedEffect(Unit) { while (seconds > 0) { delay(1000); seconds-- } }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        NovaTopBar(onBack = { nav.back() })
        Column(Modifier.weight(1f).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Enter the 6-digit code", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp))
                Row {
                    Text("Sent to +64 ${Driver.phone} · ", fontSize = 15.sp, color = c.onSurfaceVariant)
                    if (seconds > 0) Text("resend in 0:%02d".format(seconds), fontSize = 15.sp, color = c.onSurfaceVariant)
                    else Text("Resend", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.primary, modifier = Modifier.clickable { seconds = 31 })
                }
            }
            Row(Modifier.semantics { contentDescription = "Verification code, ${code.length} of 6 digits entered" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i -> CodeBox(code.getOrNull(i), active = i == code.length, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.weight(1f))
            NumberPad(onDigit = { if (code.length < 6) code += it }, onDelete = { code = code.dropLast(1) })
            NovaButton("Verify", onClick = { nav.go(Vehicle) }, enabled = code.length == 6)
        }
    }
}

private val CarColours = listOf("White" to Color(0xFFFFFFFF), "Silver" to Color(0xFFC9CCD3), "Black" to Color(0xFF1B1B21), "Blue" to Color(0xFF2F5BCB), "Red" to Color(0xFFC0392B))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var model by rememberSaveable { mutableStateOf("Toyota Prius") }
    var year by rememberSaveable { mutableStateOf("2021") }
    var colour by rememberSaveable { mutableIntStateOf(0) }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(1) }, bottom = { NovaButton("Continue to documents", onClick = { nav.go(Documents) }) }) {
        Text("Tell us about your car", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NovaTextField(model, { model = it }, "Make & model", Modifier.weight(1.3f))
            NovaTextField(year, { year = it.filter(Char::isDigit).take(4) }, "Year", Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Plate", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                Text("We'll check rego & WoF/CoF with NZTA", fontSize = 14.sp, color = c.onSurfaceVariant)
            }
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, c.onSurface, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(Driver.plate, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
        }
        Text("Colour · ${CarColours[colour].first}", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Row(Modifier.selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CarColours.forEachIndexed { i, (name, hex) ->
                val on = i == colour
                val radius by animateDpAsState(if (on) 16.dp else 24.dp, MaterialTheme.motionScheme.fastSpatialSpec())
                Box(
                    Modifier.size(48.dp)
                        .then(if (on) Modifier.border(3.dp, c.primary, RoundedCornerShape(radius + 6.dp)).padding(6.dp) else Modifier)
                        .clip(RoundedCornerShape(radius)).background(hex).border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(radius))
                        .selectable(on, onClick = { colour = i }, role = Role.RadioButton)
                        .semantics { contentDescription = name },
                )
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your $year ${model.substringAfter(' ', model)} can drive", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Go" to true, "Comfort" to true, "Electric" to false, "XL" to false).forEach { (t, ok) ->
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp))
                            .then(if (ok) Modifier.background(c.primaryContainer) else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp)))
                            .padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (ok) NovaIcons.Check else NovaIcons.Close, null, Modifier.size(18.dp), tint = if (ok) c.onPrimaryContainer else c.outline)
                        Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (ok) c.onPrimaryContainer else c.outline)
                    }
                }
            }
            Text("Electric needs a full EV. XL needs 6+ seats.", fontSize = 13.sp, color = c.onSurfaceVariant)
        }
    }
}

private data class Doc(val id: String, val name: String, val sub: String, val done: Boolean)

@Composable
fun DocumentsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val uploaded = remember { mutableStateListOf<String>() }
    val docs = listOf(
        Doc("dl", "Driving licence", "Verified", true),
        Doc("rc", "Vehicle rego", "${Driver.plate} · verified", true),
        Doc("pe", "P endorsement", "Verified", true),
        Doc("ph", "Profile photo", "Looks great", true),
        Doc("in", "Vehicle insurance", "Required to go online", false),
        Doc("bk", "Bank account", "For daily payouts", false),
    ).map { if (it.id in uploaded) it.copy(done = true, sub = "Uploaded · in review") else it }
    val done = docs.count { it.done }
    val ready = done == docs.size
    SubPage(null, onBack = { nav.back() }, bottom = {
        NovaButton(if (ready) "Submit for review" else "Finish ${docs.size - done} more to continue", onClick = { nav.go(Bank) }, enabled = ready)
    }) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Get road-ready", style = MaterialTheme.typography.headlineLarge)
            Text("$done of ${docs.size} done · takes about 5 minutes", fontSize = 15.sp, color = c.onSurfaceVariant)
            WavyProgress(done / docs.size.toFloat(), color = c.primary, track = c.primaryContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            docs.forEachIndexed { i, d ->
                Surface(
                    onClick = { if (!d.done) { if (d.id == "in") nav.go(Scan) else uploaded.add(d.id) } },
                    enabled = !d.done, shape = groupShape(i, docs.size),
                    color = if (d.done) c.surfaceContainer else c.surfaceContainerHigh, modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (d.done) Box(Modifier.size(40.dp).clip(CircleShape).background(NovaTheme.colors.cta), contentAlignment = Alignment.Center) { Icon(NovaIcons.Check, null, tint = NovaTheme.colors.onCta) }
                        else IconTile(NovaIcons.Doc, c.tertiaryContainer, c.onTertiaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(d.sub, fontSize = 13.sp, color = if (d.done) c.onSurfaceVariant else c.tertiary)
                        }
                        if (!d.done) Box(Modifier.height(36.dp).clip(CircleShape).background(NovaTheme.colors.coralBright).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Upload", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanScreen(nav: Navigator) {
    val volt = NovaTheme.colors.cta
    val ink = NovaTheme.colors.onCta
    var flash by rememberSaveable { mutableStateOf(false) }
    // Official Activity Result APIs: system camera for a quick photo, Photo Picker for gallery.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) nav.go(Bank) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) nav.go(Bank) }
    val sweep = rememberInfiniteTransition(label = "scan")
    val y by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "y")
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0F12)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = { nav.back() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), modifier = Modifier.size(48.dp)) { Icon(NovaIcons.Close, "Close") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Driver licence · Front", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Step 1 of 2", fontSize = 13.sp, color = Color(0xFFC6C5D0))
            }
            androidx.compose.material3.FilledIconToggleButton(
                checked = flash, onCheckedChange = { flash = it },
                colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White, checkedContainerColor = volt, checkedContentColor = ink),
                modifier = Modifier.size(48.dp),
            ) { Icon(NovaIcons.Flash, "Flash") }
        }
        Box(Modifier.padding(top = 80.dp).fillMaxWidth().padding(horizontal = 14.dp).height(234.dp)) {
            // Card placeholder inside the frame
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(10.dp).fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF2A2D33), Color(0xFF1E2025))))) {
                val travel = maxHeight - 3.dp
                Box(Modifier.offset(20.dp, 24.dp).size(80.dp, 100.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A3E46)))
                Column(Modifier.offset(116.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(160.dp, 12.dp).clip(CircleShape).background(Color(0xFF3A3E46)))
                    Box(Modifier.size(120.dp, 10.dp).clip(CircleShape).background(Color(0xFF33363D)))
                    Box(Modifier.size(140.dp, 10.dp).clip(CircleShape).background(Color(0xFF33363D)))
                }
                Box(Modifier.align(Alignment.BottomStart).offset(20.dp, (-26).dp).size(240.dp, 10.dp).clip(CircleShape).background(Color(0xFF33363D)))
                // Scan line sweeps the card top to bottom.
                Box(Modifier.offset(y = travel * y).fillMaxWidth().height(3.dp).shadow(8.dp, ambientColor = volt, spotColor = volt).background(volt))
            }
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx(); val l = 40.dp.toPx(); val r = 22.dp.toPx()
                val st = Stroke(w, cap = StrokeCap.Round)
                fun corner(x: Float, y0: Float, sx: Float, sy: Float) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, y0 + sy * l); lineTo(x, y0 + sy * r)
                        quadraticTo(x, y0, x + sx * r, y0); lineTo(x + sx * l, y0)
                    }
                    drawPath(p, volt, style = st)
                }
                corner(w / 2, w / 2, 1f, 1f); corner(size.width - w / 2, w / 2, -1f, 1f)
                corner(w / 2, size.height - w / 2, 1f, -1f); corner(size.width - w / 2, size.height - w / 2, -1f, -1f)
            }
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 54.dp).height(48.dp).clip(CircleShape).background(volt).padding(start = 8.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(ink), contentAlignment = Alignment.Center) { Icon(NovaIcons.Check, null, tint = volt, modifier = Modifier.size(18.dp)) }
            Text("Edges found · hold still", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ink)
        }
        Text("Avoid glare. All four corners should be inside the frame.", fontSize = 14.sp, color = Color(0xFFC6C5D0), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 24.dp, end = 24.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(18.dp), RoundedCornerShape(28.dp)),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                modifier = Modifier.size(56.dp),
            ) { Icon(NovaIcons.Image, "Upload from gallery") }
            Box(
                Modifier.size(88.dp).border(5.dp, Color.White, CircleShape).padding(11.dp).clip(CircleShape).background(Color.White)
                    .clickable(role = Role.Button) { camera.launch(null) }.semantics { contentDescription = "Take photo" },
            )
            Spacer(Modifier.size(56.dp))
        }
    }
}

@Composable
fun BankScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf("R Kumar") }
    var account by rememberSaveable { mutableStateOf("12-3456-0123456-00") }
    var ird by rememberSaveable { mutableStateOf("123-456-789") }
    var gst by rememberSaveable { mutableStateOf(false) }
    var schedule by rememberSaveable { mutableIntStateOf(0) }
    // NZ format: BB-bbbb-AAAAAAA-SS(S); bank 12 = ASB.
    val digits = account.filter(Char::isDigit)
    val valid = digits.length in 15..16
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(3) }, bottom = { NovaButton("Submit for review", onClick = { nav.reset(Pending) }, enabled = valid && name.isNotBlank()) }) {
        Text("Where should we pay you?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
        NovaTextField(name, { name = it }, "Account name")
        NovaTextField(account, { account = it.filter { ch -> ch.isDigit() || ch == '-' }.take(19) }, "NZ bank account number", placeholder = "BB-bbbb-AAAAAAA-SS", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (valid) NovaIcons.Check else NovaIcons.Info, null, Modifier.size(16.dp), tint = if (valid) c.primary else c.onSurfaceVariant)
            Text(if (valid) "${if (digits.startsWith("12")) "ASB Bank" else "Bank"} · account format valid" else "Enter all 15–16 digits", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (valid) c.primary else c.onSurfaceVariant)
        }
        NovaTextField(ird, { ird = it.filter { ch -> ch.isDigit() || ch == '-' }.take(11) }, "IRD number", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text("Needed so earnings are reported correctly to Inland Revenue. Never shown to riders.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp))
        Row(Modifier.toggleable(gst, role = Role.Switch) { gst = it }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("I'm GST registered", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("We'll add GST to your tax invoices", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Switch(gst, null, colors = SwitchDefaults.colors(checkedTrackColor = NovaTheme.colors.cta, checkedThumbColor = NovaTheme.colors.onCta))
        }
        Text("Payout schedule", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        ConnectedChoice(listOf("Daily · free", "Weekly", "Instant"), schedule, { schedule = it })
    }
}

@Composable
fun PendingScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(84.dp), color = c.primary)
            }
            Text("You're almost in", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 38.sp), textAlign = TextAlign.Center)
            Text("We're checking your documents. We'll notify you the moment you can go online.", fontSize = 16.sp, lineHeight = 23.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().clip(groupShape(0, 3)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(NovaTheme.colors.cta), contentAlignment = Alignment.Center) { Icon(NovaIcons.Check, null, Modifier.size(18.dp), tint = NovaTheme.colors.onCta) }
                Text("Documents submitted", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Today", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().clip(groupShape(1, 3)).background(c.surfaceContainerHigh).border(2.dp, c.primaryContainer, groupShape(1, 3)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) { Text("2", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.primary) }
                Text("Background check", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("In progress", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary)
            }
            Row(Modifier.fillMaxWidth().clip(groupShape(2, 3)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).border(2.dp, c.outline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text("3", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant) }
                Text("Ready to drive", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.tertiaryContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(NovaIcons.Play, NovaTheme.colors.coralBright, c.onTertiaryContainer, size = 56.dp, radius = 18.dp, iconSize = 24.dp)
            Column {
                Text("While you wait", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                Text("Watch the rider safety training", fontSize = 14.sp, color = c.onTertiaryContainer)
            }
        }
        Spacer(Modifier.weight(1f))
        NovaOutlinedButton("Preview the driver home", onClick = { nav.reset(Home) }, modifier = Modifier.fillMaxWidth())
    }
}

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
import com.nova.driver.data.api.ApiException
import com.nova.driver.data.api.DriverApi
import com.nova.driver.data.api.VerifyResult
import com.nova.driver.data.api.errorMessage
import com.nova.driver.data.auth.AuthStore
import com.nova.driver.data.auth.SignInDraft
import kotlinx.coroutines.async

@Composable
fun SplashScreen(nav: Navigator) {
    LaunchedEffect(Unit) {
        val brand = async { delay(900) }
        val route = if (AuthStore.session != null) DriverStore.entryRoute() else null
        brand.await()
        when {
            AuthStore.session == null -> nav.reset(Welcome)
            // Offline right now: Home works and retries when the driver acts.
            route == null -> nav.reset(Home)
            route == Home && DriverStore.state.online -> nav.goOnline()
            route == ToPickup || route == Arrived || route == OnTrip -> { nav.reset(Home); nav.go(route) }
            else -> nav.reset(route)
        }
    }
    val infinite = rememberInfiniteTransition(label = "splash")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "spin")
    val pulse by infinite.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    val ink = NovaTheme.colors.onCta
    Box(
        Modifier.fillMaxSize().background(NovaTheme.colors.cta).clickable(enabled = AuthStore.session == null) { nav.reset(Welcome) }
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
    val context = LocalContext.current
    val region = remember { Phones.region(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("Auckland") }
    var cityMenu by remember { mutableStateOf(false) }
    var endorsement by rememberSaveable { mutableIntStateOf(0) }
    val continueSignUp = {
        val e164 = Phones.toE164(region, phone)
        if (e164 == null) error = "Enter a valid mobile number."
        else scope.launch {
            busy = true; error = null
            try {
                val sent = DriverApi.sendOtp(e164, "signup")
                AuthStore.draft = SignInDraft(e164, "signup", first.trim(), last.trim(), sent.destinationHint, sent.resendIn)
                nav.go(Otp)
            } catch (e: ApiException) {
                if (e.code == "already_registered") {
                    // Already rides with Nova: same account drives too, so sign them in instead.
                    runCatching { DriverApi.sendOtp(e164, "login") }
                        .onSuccess { sent -> AuthStore.draft = SignInDraft(e164, "login", destinationHint = sent.destinationHint, resendIn = sent.resendIn); nav.go(Otp) }
                        .onFailure { error = errorMessage(it) }
                } else error = errorMessage(e)
            } finally { busy = false }
        }
        Unit
    }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(0) }, bottom = {
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        NovaButton(if (busy) "Sending code…" else "Continue", onClick = continueSignUp, enabled = !busy && first.isNotBlank() && phone.count(Char::isDigit) >= 6)
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
            PhonePrefix(Phones.prefix(region))
            NovaTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(15); error = null }, "Mobile", Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }
        NovaTextField(email, { email = it }, "Email (optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
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
    val context = LocalContext.current
    val region = remember { Phones.region(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }
    val sendCode = {
        val e164 = Phones.toE164(region, phone)
        if (e164 == null) error = "Enter a valid mobile number."
        else scope.launch {
            busy = true; error = null
            runCatching { DriverApi.sendOtp(e164, "login") }
                .onSuccess { sent -> AuthStore.draft = SignInDraft(e164, "login", destinationHint = sent.destinationHint, resendIn = sent.resendIn); nav.go(Otp) }
                .onFailure { error = errorMessage(it) }
            busy = false
        }
        Unit
    }
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
                PhonePrefix("$region ${Phones.prefix(region)}")
                NovaTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(15); error = null }, "Mobile number", Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
            error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error) }
            NovaButton(if (busy) "Sending code…" else "Send code", onClick = sendCode, enabled = !busy && phone.count(Char::isDigit) >= 6)
            // Passkeys need this app registered with the passkey server; until then, say so plainly.
            Surface(onClick = { error = "Passkey sign-in for drivers is coming soon. Use your mobile number for now." }, shape = RoundedCornerShape(20.dp), color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = Modifier.fillMaxWidth().height(64.dp)) {
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
    val draft = AuthStore.draft
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableIntStateOf(draft.resendIn.coerceAtLeast(0)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seconds > 0) { while (seconds > 0) { delay(1000); seconds-- } }
    val verify = {
        scope.launch {
            busy = true; error = null
            try {
                val session = when (val result = DriverApi.verifyOtp(draft.phone, draft.purpose, code)) {
                    is VerifyResult.SignedIn -> result.session
                    is VerifyResult.Proof -> DriverApi.signup(draft.firstName.ifBlank { "Driver" }, draft.lastName, draft.phone, result.token)
                }
                AuthStore.saveSession(session)
                when (val route = DriverStore.entryRoute() ?: Vehicle) {
                    Home -> if (DriverStore.state.online) nav.goOnline() else nav.reset(Home)
                    ToPickup, Arrived, OnTrip -> { nav.reset(Home); nav.go(route) }
                    else -> nav.reset(route)
                }
            } catch (e: Exception) {
                error = errorMessage(e)
                code = ""
            } finally { busy = false }
        }
        Unit
    }
    val resend = {
        scope.launch {
            runCatching { DriverApi.sendOtp(draft.phone, draft.purpose) }
                .onSuccess { seconds = it.resendIn; error = null }
                .onFailure { error = errorMessage(it) }
        }
        Unit
    }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        NovaTopBar(onBack = { nav.back() })
        Column(Modifier.weight(1f).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Enter the 6-digit code", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp))
                Row {
                    Text("Sent to ${draft.destinationHint.ifBlank { draft.phone }} · ", fontSize = 15.sp, color = c.onSurfaceVariant)
                    if (seconds > 0) Text("resend in %d:%02d".format(seconds / 60, seconds % 60), fontSize = 15.sp, color = c.onSurfaceVariant)
                    else Text("Resend", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.primary, modifier = Modifier.clickable { resend() })
                }
                error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error) }
            }
            Row(Modifier.semantics { contentDescription = "Verification code, ${code.length} of 6 digits entered" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i -> CodeBox(code.getOrNull(i), active = i == code.length, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.weight(1f))
            NumberPad(onDigit = { if (code.length < 6) { code += it; error = null } }, onDelete = { code = code.dropLast(1) })
            NovaButton(if (busy) "Verifying…" else "Verify", onClick = verify, enabled = !busy && code.length == 6)
        }
    }
}

private val CarColours = listOf("White" to Color(0xFFFFFFFF), "Silver" to Color(0xFFC9CCD3), "Black" to Color(0xFF1B1B21), "Blue" to Color(0xFF2F5BCB), "Red" to Color(0xFFC0392B))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    // Prefill from the car on file (changing car) or what was typed earlier this session.
    val saved = DriverStore.me?.vehicle
    val draft = DriverStore.vehicleDraft
    var model by rememberSaveable { mutableStateOf(draft?.let { "${it.make} ${it.model}" } ?: saved?.let { "${it.make} ${it.model}" } ?: "") }
    var year by rememberSaveable { mutableStateOf((draft?.year ?: saved?.year)?.toString() ?: "") }
    var plate by rememberSaveable { mutableStateOf(draft?.plate ?: saved?.plate ?: "") }
    var colour by rememberSaveable { mutableIntStateOf(CarColours.indexOfFirst { it.first == (draft?.color ?: saved?.color) }.coerceAtLeast(0)) }
    var seats by rememberSaveable { mutableIntStateOf((draft?.seats ?: saved?.seats ?: 4).coerceIn(4, 7)) }
    var electric by rememberSaveable { mutableStateOf(draft?.electric ?: saved?.isElectric ?: false) }
    var wheelchair by rememberSaveable { mutableStateOf(draft?.wheelchair ?: saved?.wheelchairAccessible ?: false) }
    LaunchedEffect(Unit) { DriverStore.loadRules() }
    val thisYear = remember { java.time.Year.now().value }
    val makeModel = model.trim().split(Regex("\\s+"), limit = 2)
    val yearNum = year.toIntOrNull()
    val plateClean = plate.trim().uppercase()
    val valid = makeModel.size == 2 && yearNum != null && yearNum in 1990..thisYear + 1 &&
        plateClean.length in 2..10 && plateClean.all { it.isLetterOrDigit() || it == ' ' || it == '-' }
    val proceed = {
        DriverStore.vehicleDraft = VehicleDraft(
            make = makeModel[0], model = makeModel[1], color = CarColours[colour].first, plate = plateClean,
            year = yearNum ?: thisYear, seats = seats, electric = electric, wheelchair = wheelchair,
        )
        nav.go(Documents)
    }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(1) }, bottom = { NovaButton("Continue to documents", onClick = proceed, enabled = valid) }) {
        Text("Tell us about your car", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NovaTextField(model, { model = it.take(60) }, "Make & model", Modifier.weight(1.3f), placeholder = "Toyota Prius", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
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
            Box(Modifier.height(48.dp).widthIn(min = 120.dp, max = 168.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, c.onSurface, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.text.BasicTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == ' ' || ch == '-' }.take(10) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Color(0xFF1B1B21), textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.semantics { contentDescription = "Number plate" },
                    decorationBox = { inner ->
                        if (plate.isEmpty()) Text("ABC123", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Color(0xFF9A9AA3), textAlign = TextAlign.Center)
                        inner()
                    },
                )
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
        Text("Passenger seats", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        ConnectedChoice(listOf("4", "5", "6", "7"), seats - 4, { seats = it + 4 })
        Row(Modifier.fillMaxWidth().toggleable(electric, role = Role.Switch) { electric = it }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Fully electric", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Battery EV only, not hybrid", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Switch(electric, null, colors = SwitchDefaults.colors(checkedTrackColor = NovaTheme.colors.cta, checkedThumbColor = NovaTheme.colors.onCta))
        }
        Row(Modifier.fillMaxWidth().toggleable(wheelchair, role = Role.Switch) { wheelchair = it }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Wheelchair accessible", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Ramp or lift, room for a wheelchair", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Switch(wheelchair, null, colors = SwitchDefaults.colors(checkedTrackColor = NovaTheme.colors.cta, checkedThumbColor = NovaTheme.colors.onCta))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (yearNum != null && makeModel.size == 2) "Your $year ${makeModel[1]} can drive" else "Ride types your car can drive", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DriverStore.rules.map { it.name to it.allows(seats, electric, yearNum ?: 0, thisYear) }.forEach { (t, ok) ->
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
            val notes = DriverStore.rules.mapNotNull { r ->
                when {
                    r.requiresElectric -> "${r.name} needs a full EV."
                    r.maxVehicleAgeYears != null -> "${r.name} needs a car from ${thisYear - r.maxVehicleAgeYears} or newer."
                    r.seats > 4 -> "${r.name} needs ${r.seats}+ seats."
                    else -> null
                }
            }
            if (notes.isNotEmpty()) Text(notes.joinToString(" "), fontSize = 13.sp, color = c.onSurfaceVariant)
        }
    }
}

private data class Doc(val id: String, val name: String, val sub: String, val done: Boolean)

@Composable
fun DocumentsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uploaded = remember { mutableStateListOf<String>() }
    var licence by rememberSaveable { mutableStateOf(DriverStore.licenceNumber.ifBlank { DriverStore.me?.driver?.licenceNumber ?: "" }) }
    var licenceSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val licenceOk = licence.trim().length in 4..20 && licence.trim().all { it.isLetterOrDigit() || it == '-' }
    val car = DriverStore.vehicleDraft ?: DriverStore.me?.vehicle?.let { VehicleDraft(it.make, it.model, it.color, it.plate, it.year, it.seats, it.isElectric, it.wheelchairAccessible) }
    val docs = listOf(
        Doc("dl", "Driving licence", if (licenceOk) "Licence ${licence.trim().uppercase()}" else "Enter your licence number", licenceOk),
        Doc("rc", "Vehicle rego", car?.let { "${it.plate} · ${it.year} ${it.make} ${it.model}" } ?: "Add your car first", car != null),
        // Uploads are recorded on this phone for now; document storage and review come next.
        Doc("pe", "P endorsement", "Photo of your endorsement", false),
        Doc("ph", "Profile photo", "Riders see this when you arrive", false),
        Doc("in", "Vehicle insurance", "Required to go online", false),
        Doc("bk", "Bank account", "For daily payouts", false),
    ).map { if (it.id in uploaded) it.copy(done = true, sub = "Uploaded · in review") else it }
    val done = docs.count { it.done }
    val ready = done == docs.size
    val submit = {
        scope.launch {
            busy = true; error = null
            DriverStore.licenceNumber = licence.trim().uppercase()
            runCatching { DriverStore.submitApplication() }
                .onSuccess { nav.go(Bank) }
                .onFailure { error = errorMessage(it) }
            busy = false
        }
        Unit
    }
    if (licenceSheet) {
        ModalBottomSheet(onDismissRequest = { licenceSheet = false }, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Driving licence", style = MaterialTheme.typography.headlineSmall)
                Text("The number on the front of your licence card (5a on NZ licences).", fontSize = 14.sp, color = c.onSurfaceVariant)
                NovaTextField(licence, { licence = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(20) }, "Licence number", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                NovaButton("Save", onClick = { licenceSheet = false }, enabled = licenceOk)
            }
        }
    }
    SubPage(null, onBack = { nav.back() }, bottom = {
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        NovaButton(
            when { busy -> "Submitting…"; ready -> "Submit for review"; else -> "Finish ${docs.size - done} more to continue" },
            onClick = submit, enabled = ready && !busy,
        )
    }) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Get road-ready", style = MaterialTheme.typography.headlineLarge)
            Text("$done of ${docs.size} done · takes about 5 minutes", fontSize = 15.sp, color = c.onSurfaceVariant)
            WavyProgress(done / docs.size.toFloat(), color = c.primary, track = c.primaryContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            docs.forEachIndexed { i, d ->
                Surface(
                    onClick = {
                        when {
                            d.id == "dl" -> licenceSheet = true
                            d.id == "rc" -> nav.back()
                            !d.done -> if (d.id == "in") nav.go(Scan) else uploaded.add(d.id)
                        }
                    },
                    enabled = !d.done || d.id == "dl" || d.id == "rc", shape = groupShape(i, docs.size),
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
    var name by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var ird by rememberSaveable { mutableStateOf("") }
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
    val scope = rememberCoroutineScope()
    val status = DriverStore.me?.driver?.status ?: "pending"
    val approved = status == "approved"
    val suspended = status == "suspended"
    // Review can finish any minute (an admin, or instantly in development): keep checking.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { DriverStore.refreshMe() }
            if (DriverStore.me?.driver?.status == "approved") break
            delay(10_000)
        }
    }
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(if (approved) NovaTheme.colors.cta else c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (approved) Icon(NovaIcons.Check, null, Modifier.size(64.dp), tint = NovaTheme.colors.onCta)
                else LoadingIndicator(Modifier.size(84.dp), color = c.primary)
            }
            Text(
                when { approved -> "You're approved"; suspended -> "Account paused"; else -> "You're almost in" },
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 38.sp), textAlign = TextAlign.Center,
            )
            Text(
                when {
                    approved -> "Welcome to Nova. Go online whenever you're ready to take trips."
                    suspended -> "Driver support will contact you about your account."
                    else -> "We're checking your documents. We'll notify you the moment you can go online."
                },
                fontSize = 16.sp, lineHeight = 23.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp),
            )
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
                Text(if (approved) "Done" else "In progress", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary)
            }
            Row(Modifier.fillMaxWidth().clip(groupShape(2, 3)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).border(2.dp, c.outline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text("3", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant) }
                Text("Ready to drive", fontSize = 16.sp, fontWeight = if (approved) FontWeight.Bold else FontWeight.SemiBold, color = if (approved) c.onSurface else c.onSurfaceVariant)
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
        if (approved) NovaButton("Start driving", onClick = { nav.reset(Home) }, height = 72.dp)
        else NovaOutlinedButton("Check again", onClick = { scope.launch { runCatching { DriverStore.refreshMe() } } }, modifier = Modifier.fillMaxWidth())
    }
}

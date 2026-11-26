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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.onSizeChanged

@Composable
fun SplashScreen(nav: Navigator) {
    LaunchedEffect(Unit) {
        val brand = async { delay(900) }
        val route = if (AuthStore.session != null) runCatching { DriverStore.entryRoute() }.getOrNull() else null
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
    AdaptiveScreen(
        Modifier.background(c.surface).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
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
private fun CountryCodeButton(country: com.nova.driver.data.auth.Country, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.outline),
        color = c.surface,
        modifier = Modifier.height(56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(country.flag, fontSize = 16.sp)
            Text("+${country.dial}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Icon(NovaIcons.Down, contentDescription = "Select country", modifier = Modifier.size(16.dp), tint = c.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CityPickerSheet(
    country: com.nova.driver.data.auth.Country,
    selectedCity: String,
    onChangeCountry: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<DriverApi.CityOption>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(country.iso, query) {
        if (query.isNotBlank()) delay(280)
        loading = true
        loadError = null
        runCatching { DriverApi.cities(country.iso, query.trim(), countryName = country.name) }
            .onSuccess { cities = it }
            .onFailure { loadError = errorMessage(it) }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Select your city", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Surface(onClick = onChangeCountry, shape = CircleShape, color = c.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(country.flag, fontSize = 15.sp)
                        Text(country.iso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Icon(NovaIcons.Down, contentDescription = "Change country", modifier = Modifier.size(14.dp))
                    }
                }
            }
            NovaTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                label = "Search city in ${country.name}",
                placeholder = "Type your city name",
            )
            when {
                loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                loadError != null -> Text(loadError!!, color = c.error, fontSize = 14.sp)
                cities.isEmpty() -> Text(
                    if (query.isBlank()) "Type your city name above to search in ${country.name}." else "No cities matched \"$query\" in ${country.name}.",
                    fontSize = 14.sp,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(cities.size) { i ->
                        val item = cities[i]
                        val chosen = item.name.equals(selectedCity, ignoreCase = true)
                        Surface(
                            onClick = { onPick(item.name) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (chosen) c.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontSize = 16.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium)
                                    if (item.region.isNotBlank()) {
                                        Text(item.region, fontSize = 13.sp, color = c.onSurfaceVariant)
                                    }
                                }
                                if (chosen) Icon(NovaIcons.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var countryIso by rememberSaveable { mutableStateOf(com.nova.driver.data.auth.PhoneRegions.defaultRegion(context).iso) }
    val country = remember(countryIso) { com.nova.driver.data.auth.PhoneRegions.find(countryIso) ?: com.nova.driver.data.auth.PhoneRegions.defaultRegion(context) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var cityMenu by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var nearbyCities by remember { mutableStateOf<List<DriverApi.CityOption>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var endorsement by rememberSaveable { mutableIntStateOf(0) }

    // Fetch dynamic nearby/country cities from Google Places (nearby if GPS is available, else country cities).
    LaunchedEffect(country.iso) {
        val loc = runCatching { DriverStore.currentLocation() }.getOrNull()
        runCatching { DriverApi.cities(country.iso, "", loc?.latitude, loc?.longitude, country.name) }
            .onSuccess { list ->
                nearbyCities = list
                if (city.isBlank()) list.firstOrNull()?.let { city = it.name }
            }
    }

    if (showCountryPicker) {
        CountryPickerSheet(
            selected = country,
            onPick = { picked ->
                if (picked.iso != countryIso) {
                    countryIso = picked.iso
                    city = ""
                }
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }
    if (showCityPicker) {
        CityPickerSheet(
            country = country,
            selectedCity = city,
            onChangeCountry = { showCityPicker = false; showCountryPicker = true },
            onPick = { picked -> city = picked; showCityPicker = false },
            onDismiss = { showCityPicker = false },
        )
    }

    val continueSignUp = {
        val e164 = com.nova.driver.data.auth.PhoneRegions.toE164(country.iso, phone)
        if (e164 == null) error = "Enter a valid mobile number for ${country.name}."
        else if (city.isBlank()) error = "Select the city you'll drive in."
        else scope.launch {
            busy = true; error = null
            try {
                val sent = DriverApi.sendOtp(e164, "signup")
                AuthStore.draft = SignInDraft(
                    phone = e164,
                    purpose = "signup",
                    firstName = first.trim(),
                    lastName = last.trim(),
                    city = city.trim(),
                    countryCode = country.iso,
                    hasPassengerLicence = endorsement == 0,
                    destinationHint = sent.destinationHint,
                    resendIn = sent.resendIn,
                )
                nav.go(Otp)
            } catch (e: Exception) {
                // One account, one role: an existing number is never turned into a driver here.
                error = errorMessage(e)
            } finally { busy = false }
        }
        Unit
    }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(0) }, bottom = {
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        NovaButton(if (busy) "Sending code…" else "Continue", onClick = continueSignUp, enabled = !busy && first.isNotBlank() && city.isNotBlank() && phone.count(Char::isDigit) >= 6)
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
            CountryCodeButton(country, onClick = { showCountryPicker = true })
            NovaTextField(
                phone,
                { phone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(15); error = null },
                "Mobile",
                Modifier.weight(1f),
                placeholder = com.nova.driver.data.auth.PhoneRegions.exampleNumber(country.iso) ?: "Mobile number",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
        NovaTextField(email, { email = it }, "Email (optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Box {
            Surface(onClick = { if (nearbyCities.isNotEmpty()) cityMenu = true else showCityPicker = true }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.outline), color = c.surface, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("City you'll drive in (${country.name})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                        Text(city.ifBlank { "Select city" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (city.isBlank()) c.onSurfaceVariant else c.onSurface)
                    }
                    Icon(NovaIcons.Down, null)
                }
            }
            androidx.compose.material3.DropdownMenu(expanded = cityMenu, onDismissRequest = { cityMenu = false }) {
                nearbyCities.forEach { item ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(item.name) },
                        onClick = { city = item.name; cityMenu = false },
                    )
                }
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Search other city…", color = c.primary, fontWeight = FontWeight.SemiBold) },
                    onClick = { cityMenu = false; showCityPicker = true },
                )
            }
        }
        Text("Do you have a commercial passenger licence?", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        ConnectedChoice(listOf("Yes, I have one", "Applying"), endorsement, { endorsement = it })
        if (endorsement == 1) {
            Text(
                "No problem — you can finish sign-up now. We'll guide you through your local licensing steps before your first trip.",
                fontSize = 13.sp, lineHeight = 19.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun LoginScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var countryIso by rememberSaveable { mutableStateOf(com.nova.driver.data.auth.PhoneRegions.defaultRegion(context).iso) }
    val country = remember(countryIso) { com.nova.driver.data.auth.PhoneRegions.find(countryIso) ?: com.nova.driver.data.auth.PhoneRegions.defaultRegion(context) }
    var showCountryPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }

    if (showCountryPicker) {
        CountryPickerSheet(
            selected = country,
            onPick = { picked -> countryIso = picked.iso; showCountryPicker = false },
            onDismiss = { showCountryPicker = false },
        )
    }

    val sendCode = {
        val e164 = com.nova.driver.data.auth.PhoneRegions.toE164(country.iso, phone)
        if (e164 == null) error = "Enter a valid mobile number for ${country.name}."
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
                CountryCodeButton(country, onClick = { showCountryPicker = true })
                NovaTextField(
                    phone,
                    { phone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(15); error = null },
                    "Mobile number",
                    Modifier.weight(1f),
                    placeholder = com.nova.driver.data.auth.PhoneRegions.exampleNumber(country.iso) ?: "Mobile number",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
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
                // A rider or admin account signed in: don't keep it on this phone's driver app.
                if ((e as? ApiException)?.code == "not_driver_account") AuthStore.clearSession()
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
        AdaptiveScreen(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                Text("We'll verify your registration & vehicle inspection", fontSize = 14.sp, color = c.onSurfaceVariant)
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

private data class Doc(val id: String, val name: String, val sub: String, val done: Boolean, val bad: Boolean = false, val locked: Boolean = false)

@Composable
fun DocumentsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val me = DriverStore.me
    var licence by rememberSaveable { mutableStateOf(DriverStore.licenceNumber.ifBlank { me?.driver?.licenceNumber ?: "" }) }
    var licenceSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The checklist (and each upload's review status) lives on the server.
    LaunchedEffect(Unit) { runCatching { DriverStore.refreshMe() } }
    val licenceOk = licence.trim().length in 4..20 && licence.trim().all { it.isLetterOrDigit() || it == '-' }
    val car = DriverStore.vehicleDraft ?: me?.vehicle?.let { VehicleDraft(it.make, it.model, it.color, it.plate, it.year, it.seats, it.isElectric, it.wheelchairAccessible) }
    val docs = buildList {
        add(Doc("licence_no", "Licence number", if (licenceOk) licence.trim().uppercase() else "The number on your licence card", licenceOk))
        add(Doc("car", "Your car", car?.let { "${it.plate} · ${it.year} ${it.make} ${it.model}" } ?: "Add your car first", car != null))
        me?.documents.orEmpty().forEach { d ->
            val sub = when (d.status) {
                "verified" -> "Verified"
                "uploaded" -> "Uploaded · in review"
                "rejected" -> "Sent back: ${d.rejectionReason ?: "upload a clearer photo"}"
                else -> d.description
            }
            add(Doc(d.category, d.name, sub, d.status == "uploaded" || d.status == "verified", bad = d.status == "rejected", locked = d.status == "verified"))
        }
        add(Doc("bank", "Bank account", me?.payout?.let { "${it.accountName} ···· ${it.accountLast4}" } ?: "For your payouts", me?.payout != null))
    }
    val done = docs.count { it.done }
    val ready = done == docs.size && me != null
    val submit = {
        scope.launch {
            busy = true; error = null
            DriverStore.licenceNumber = licence.trim().uppercase()
            runCatching { DriverStore.submitApplication() }
                .onSuccess { nav.reset(if (DriverStore.me?.driver?.status == "approved") Home else Pending) }
                .onFailure { error = errorMessage(it) }
            busy = false
        }
        Unit
    }
    if (licenceSheet) {
        ModalBottomSheet(onDismissRequest = { licenceSheet = false }, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Licence number", style = MaterialTheme.typography.headlineSmall)
                Text("The number printed on the front of your driver licence.", fontSize = 14.sp, color = c.onSurfaceVariant)
                NovaTextField(licence, { licence = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(20) }, "Licence number", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                NovaButton("Save", onClick = { DriverStore.licenceNumber = licence.trim().uppercase(); licenceSheet = false }, enabled = licenceOk)
            }
        }
    }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(2) }, bottom = {
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        NovaButton(
            when { busy -> "Submitting…"; ready -> "Submit for review"; else -> "Finish ${docs.size - done} more to continue" },
            onClick = submit, enabled = ready && !busy,
        )
    }) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Get road-ready", style = MaterialTheme.typography.headlineLarge)
            Text("$done of ${docs.size} done · photos go securely to our review team", fontSize = 15.sp, color = c.onSurfaceVariant)
            WavyProgress(done / docs.size.toFloat(), color = c.primary, track = c.primaryContainer)
        }
        if (me == null) LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).size(48.dp), color = c.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            docs.forEachIndexed { i, d ->
                Surface(
                    onClick = {
                        when (d.id) {
                            "licence_no" -> licenceSheet = true
                            "car" -> nav.back()
                            "bank" -> nav.go(Bank)
                            else -> nav.go(Scan(d.id, d.name))
                        }
                    },
                    enabled = !d.locked, shape = groupShape(i, docs.size),
                    color = if (d.done) c.surfaceContainer else c.surfaceContainerHigh, modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (d.done) Box(Modifier.size(40.dp).clip(CircleShape).background(NovaTheme.colors.cta), contentAlignment = Alignment.Center) { Icon(NovaIcons.Check, null, tint = NovaTheme.colors.onCta) }
                        else IconTile(if (d.bad) NovaIcons.Warning else NovaIcons.Doc, if (d.bad) c.errorContainer else c.tertiaryContainer, if (d.bad) c.onErrorContainer else c.onTertiaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(d.sub, fontSize = 13.sp, color = when { d.bad -> c.error; d.done -> c.onSurfaceVariant; else -> c.tertiary }, maxLines = 2)
                        }
                        if (!d.done) Box(Modifier.height(36.dp).clip(CircleShape).background(if (d.bad) c.errorContainer else NovaTheme.colors.coralBright).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when { d.id == "licence_no" || d.id == "bank" -> "Add"; d.id == "car" -> "Edit"; d.bad -> "Re-upload"; else -> "Upload" },
                                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (d.bad) c.onErrorContainer else c.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanScreen(nav: Navigator, route: Scan) {
    val volt = NovaTheme.colors.cta
    val ink = NovaTheme.colors.onCta
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flash by rememberSaveable { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val upload = { uri: android.net.Uri ->
        scope.launch {
            uploading = true; error = null
            runCatching { DriverStore.uploadDocument(route.category, uri) }
                .onSuccess { nav.back() }
                .onFailure { error = errorMessage(it) }
            uploading = false
        }
        Unit
    }
    // Live CameraX viewfinder inside the frame; Photo Picker for gallery; PDFs via the file picker.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) upload(uri) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) upload(uri) }
    fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var cameraAllowed by remember { mutableStateOf(granted()) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraAllowed = ok
        if (!ok) error = "Camera is off for Nova. Allow it in Settings, or upload from your gallery or a PDF."
    }
    LaunchedEffect(Unit) { if (!cameraAllowed) askCamera.launch(android.Manifest.permission.CAMERA) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<androidx.camera.core.SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var frameSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val imageCapture = remember {
        androidx.camera.core.ImageCapture.Builder().setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    LaunchedEffect(cameraAllowed, frameSize) {
        if (!cameraAllowed || frameSize.width == 0) return@LaunchedEffect
        val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        val provider = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            future.addListener({ cont.resumeWith(runCatching { future.get() }) }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
        val preview = androidx.camera.core.Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        // The photo is cropped to the frame the driver sees, not the whole sensor.
        val rotation = (context as? android.app.Activity)?.display?.rotation ?: android.view.Surface.ROTATION_0
        val viewPort = androidx.camera.core.ViewPort.Builder(android.util.Rational(frameSize.width, frameSize.height), rotation).build()
        val group = androidx.camera.core.UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(imageCapture).build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, group)
            kotlinx.coroutines.awaitCancellation()
        } catch (e: IllegalArgumentException) {
            error = "No back camera found. Upload from your gallery or a PDF."
        } finally {
            provider.unbind(preview, imageCapture)
            camera = null; surfaceRequest = null
        }
    }
    // The flash button is the torch, so glare can be checked before taking the photo.
    LaunchedEffect(flash, camera) { camera?.cameraControl?.enableTorch(flash) }
    val shoot = shoot@{
        if (!cameraAllowed) { askCamera.launch(android.Manifest.permission.CAMERA); return@shoot }
        if (camera == null) return@shoot
        val uri = DocumentImages.newCaptureUri(context)
        val out = context.contentResolver.openOutputStream(uri) ?: return@shoot
        uploading = true; error = null
        imageCapture.takePicture(
            androidx.camera.core.ImageCapture.OutputFileOptions.Builder(out).build(),
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: androidx.camera.core.ImageCapture.OutputFileResults) { out.close(); upload(uri) }
                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    out.close(); uploading = false; error = "Couldn't take the photo. Try again."
                }
            },
        )
    }
    val sweep = rememberInfiniteTransition(label = "scan")
    val y by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "y")
    AdaptiveScreen(Modifier.background(Color(0xFF0E0F12)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = { nav.back() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), modifier = Modifier.size(48.dp)) { Icon(NovaIcons.Close, "Close") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(route.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(if (uploading) "Uploading securely…" else "Photo or PDF", fontSize = 13.sp, color = Color(0xFFC6C5D0))
            }
            androidx.compose.material3.FilledIconToggleButton(
                checked = flash, onCheckedChange = { flash = it },
                colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White, checkedContainerColor = volt, checkedContentColor = ink),
                modifier = Modifier.size(48.dp),
            ) { Icon(NovaIcons.Flash, "Flash") }
        }
        // Card-shaped frame (ID-1 ratio): same size on phones, grows to a readable size on tablets,
        // and sits higher in landscape so the shutter stays on screen.
        Box(
            Modifier.padding(top = if (com.nova.designsystem.adaptive.isHeightCompact()) 16.dp else 80.dp).align(Alignment.CenterHorizontally)
                .widthIn(max = 520.dp).fillMaxWidth()
                .padding(horizontal = 14.dp).aspectRatio(1.62f),
        ) {
            // Live camera inside the frame; the card outline shows until the camera starts.
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.padding(10.dp).fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF2A2D33), Color(0xFF1E2025))))
                    .onSizeChanged { frameSize = it },
            ) {
                val travel = maxHeight - 3.dp
                val request = surfaceRequest
                if (request != null) {
                    androidx.camera.compose.CameraXViewfinder(
                        surfaceRequest = request,
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Camera view. Line up the ${route.title.lowercase()} inside the frame." }
                            .clickable(enabled = !uploading, role = Role.Button, onClickLabel = "Take photo") { shoot() },
                    )
                } else {
                    Box(Modifier.offset(20.dp, 24.dp).size(80.dp, 100.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A3E46)))
                    Column(Modifier.offset(116.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(160.dp, 12.dp).clip(CircleShape).background(Color(0xFF3A3E46)))
                        Box(Modifier.size(120.dp, 10.dp).clip(CircleShape).background(Color(0xFF33363D)))
                        Box(Modifier.size(140.dp, 10.dp).clip(CircleShape).background(Color(0xFF33363D)))
                    }
                    Box(Modifier.align(Alignment.BottomStart).offset(20.dp, (-26).dp).size(240.dp, 10.dp).clip(CircleShape).background(Color(0xFF33363D)))
                }
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
            Text(if (uploading) "Uploading…" else "Fill the frame · hold still", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ink)
        }
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB4AB), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 24.dp, end = 24.dp)) }
        Text("Avoid glare. All four corners should be inside the frame.", fontSize = 14.sp, color = Color(0xFFC6C5D0), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 24.dp, end = 24.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !uploading,
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(18.dp), RoundedCornerShape(28.dp)),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                modifier = Modifier.size(56.dp),
            ) { Icon(NovaIcons.Image, "Upload from gallery") }
            Box(
                Modifier.size(88.dp).border(5.dp, Color.White, CircleShape).padding(11.dp).clip(CircleShape).background(Color.White)
                    .clickable(enabled = !uploading, role = Role.Button) { shoot() }.semantics { contentDescription = "Take photo" },
            ) { if (uploading) LoadingIndicator(Modifier.align(Alignment.Center).size(40.dp), color = volt) }
            FilledIconButton(
                onClick = { pdf.launch(arrayOf("application/pdf")) }, enabled = !uploading,
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(18.dp), RoundedCornerShape(28.dp)),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                modifier = Modifier.size(56.dp),
            ) { Icon(NovaIcons.Doc, "Upload a PDF") }
        }
    }
}

@Composable
fun BankScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val saved = DriverStore.me?.payout
    val schedules = listOf("daily", "weekly", "instant")
    var name by rememberSaveable { mutableStateOf(saved?.accountName ?: "") }
    var account by rememberSaveable { mutableStateOf("") }
    var ird by rememberSaveable { mutableStateOf("") }
    var gst by rememberSaveable { mutableStateOf(saved?.gstRegistered ?: false) }
    var schedule by rememberSaveable { mutableIntStateOf(schedules.indexOf(saved?.payoutSchedule ?: "daily").coerceAtLeast(0)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val accountClean = account.trim()
    val valid = accountClean.length in 6..34 && accountClean.any(Char::isDigit)
    val save = {
        scope.launch {
            busy = true; error = null
            runCatching { DriverStore.savePayout(name.trim(), accountClean, ird.trim(), gst, schedules[schedule]) }
                .onSuccess { nav.back() }
                .onFailure { error = errorMessage(it) }
            busy = false
        }
        Unit
    }
    SubPage(null, onBack = { nav.back() }, header = { SignUpSteps(3) }, bottom = {
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        NovaButton(if (busy) "Saving…" else "Save bank details", onClick = save, enabled = valid && name.isNotBlank() && !busy)
    }) {
        if (saved != null) Text("On file: ${saved.accountName} ···· ${saved.accountLast4}. Enter the full number again to change it.", fontSize = 14.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        Text("Where should we pay you?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
        NovaTextField(name, { name = it }, "Account holder name")
        NovaTextField(account, { account = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == ' ' }.take(34) }, "Bank account number / IBAN", placeholder = "Account number or IBAN")
        Row(Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (valid) NovaIcons.Check else NovaIcons.Info, null, Modifier.size(16.dp), tint = if (valid) c.primary else c.onSurfaceVariant)
            Text(if (valid) "Account format valid" else "Enter your full account number or IBAN", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (valid) c.primary else c.onSurfaceVariant)
        }
        NovaTextField(ird, { ird = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(20) }, "Tax ID / TIN (optional)")
        Text("Needed where local tax reporting applies. Never shown to riders.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp))
        Row(Modifier.toggleable(gst, role = Role.Switch) { gst = it }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Registered for sales tax / GST", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("We'll include tax details on your invoices", fontSize = 13.sp, color = c.onSurfaceVariant)
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
    AdaptiveScreen(
        Modifier.background(c.surface).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 24.dp),
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
        val rejected = DriverStore.me?.documents.orEmpty().filter { it.status == "rejected" }
        val note = DriverStore.me?.driver?.reviewNote?.takeIf { !approved }
        if (!approved && (rejected.isNotEmpty() || note != null || DriverStore.me?.documentsReady == false)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.errorContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Action needed", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onErrorContainer)
                rejected.forEach { Text("${it.name}: ${it.rejectionReason ?: "upload a clearer photo"}", fontSize = 14.sp, color = c.onErrorContainer) }
                note?.let { Text(it, fontSize = 14.sp, color = c.onErrorContainer) }
                if (rejected.isEmpty() && note == null) Text("Some documents are still missing.", fontSize = 14.sp, color = c.onErrorContainer)
                NovaButton("Fix documents", onClick = { nav.go(Documents) }, height = 52.dp)
            }
        }
        if (approved) NovaButton("Start driving", onClick = { nav.reset(Home) }, height = 72.dp)
        else NovaOutlinedButton("Check again", onClick = { scope.launch { runCatching { DriverStore.refreshMe() } } }, modifier = Modifier.fillMaxWidth())
    }
}

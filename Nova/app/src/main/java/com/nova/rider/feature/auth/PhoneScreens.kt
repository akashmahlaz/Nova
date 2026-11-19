package com.nova.rider.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaOutlinedButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.components.NovaTopBar
import com.nova.designsystem.components.OrDivider
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.Country
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.PhoneRegions
import com.nova.rider.data.auth.authErrorMessage
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.Login
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Otp
import com.nova.rider.navigation.SignUpPhone
import kotlinx.coroutines.launch

/** Country code button. Clean, quiet, and perfectly matching the phone input height & styling. */
@Composable
internal fun CountryCodeButton(country: Country?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.outline),
        color = c.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(country?.flag ?: "🌐", fontSize = 20.sp)
            Text(
                country?.let { "+${it.dial}" } ?: "Code",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = c.onSurface,
            )
            Icon(
                NovaIcons.Down,
                contentDescription = "Change country code",
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun PhoneRow(country: Country?, value: String, onCountryClick: () -> Unit, onValue: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryCodeButton(country, onClick = onCountryClick)
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onValue(v.filter { it.isDigit() || it == ' ' }.take(13)) },
            modifier = Modifier.weight(1f).height(56.dp),
            placeholder = { Text("Mobile number", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = c.outline,
                focusedBorderColor = c.primary,
                unfocusedContainerColor = c.surface,
                focusedContainerColor = c.surface,
            ),
        )
    }
}

@Composable
internal fun TermsText() {
    val c = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            append("By continuing you agree to Nova's ")
            withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("Terms") }
            append(" and ")
            withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
            append(".")
        },
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
        color = c.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Sign up · step 1: mobile number or email. Sends a sign-up verification code. */
@Composable
fun SignUpPhoneScreen(nav: Navigator) {
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var useEmail by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCountries by remember { mutableStateOf(false) }
    val country = AuthStore.country
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        NovaTopBar(onBack = { nav.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShapeBadge(NovaShape.Cookie, 64.dp, c.primaryContainer) {
                    Icon(
                        if (useEmail) NovaIcons.Mail else NovaIcons.Smartphone,
                        null,
                        tint = c.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (useEmail) "What's your\nemail?" else "What's your\nnumber?",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    if (useEmail) "We'll email a 6-digit code to verify your address." else "We'll text a 6-digit code to verify it's you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                )
            }

            if (useEmail) {
                NovaTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email address",
                    placeholder = "you@example.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            } else {
                PhoneRow(country, phone, onCountryClick = { showCountries = true }) { phone = it }
            }

            OrDivider()

            NovaOutlinedButton(
                if (useEmail) "Use phone instead" else "Continue with email",
                onClick = {
                    useEmail = !useEmail
                    error = null
                },
                leadingIcon = if (useEmail) NovaIcons.Smartphone else NovaIcons.Mail,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
            TermsText()
            val e164 = country?.let { PhoneRegions.toE164(it.iso, phone) }
            val canSend = if (useEmail) email.contains('@') && !busy else (e164 != null && !busy)

            NovaButton(
                "Send code",
                enabled = canSend,
                onClick = {
                    val channel = if (useEmail) "email" else "phone"
                    val destination = if (useEmail) email.trim().lowercase() else (e164 ?: return@NovaButton)
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            NovaApi.sendOtp(channel, destination, "signup")
                            nav.go(Otp(channel, destination, "signup"))
                        } catch (e: Throwable) {
                            error = authErrorMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
    if (showCountries) {
        CountryPickerSheet(
            selected = country,
            onPick = { AuthStore.selectCountry(it); showCountries = false },
            onDismiss = { showCountries = false },
        )
    }
}

/** Returning rider log in. Phone by default; "Continue with email" switches to an email code. */
@Composable
fun LoginScreen(nav: Navigator) {
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var useEmail by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCountries by remember { mutableStateOf(false) }
    val country = AuthStore.country
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        NovaTopBar(onBack = { nav.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ShapeBadge(NovaShape.Cookie, 64.dp, c.primaryContainer) {
                Icon(NovaIcons.User, null, tint = c.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome back", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (useEmail) "Log in with the email on your Nova account." else "Log in with the mobile number on your Nova account.",
                    style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                )
            }
            if (useEmail) {
                NovaTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            } else {
                PhoneRow(country, phone, onCountryClick = { showCountries = true }) { phone = it }
            }
            val phoneE164 = country?.let { PhoneRegions.toE164(it.iso, phone) }
            NovaButton("Send code", enabled = !busy && (if (useEmail) email.contains('@') else phoneE164 != null), onClick = {
                scope.launch {
                    busy = true
                    error = null
                    val channel = if (useEmail) "email" else "phone"
                    val destination = if (useEmail) email.trim().lowercase() else phoneE164.orEmpty()
                    try {
                        NovaApi.sendOtp(channel, destination, "login")
                        nav.go(Otp(channel, destination, "login"))
                    } catch (e: Throwable) {
                        error = authErrorMessage(e)
                    } finally {
                        busy = false
                    }
                }
            })
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
            NovaOutlinedButton(
                if (useEmail) "Use phone instead" else "Continue with email",
                onClick = { useEmail = !useEmail; error = null },
                leadingIcon = NovaIcons.Mail,
                modifier = Modifier.fillMaxWidth(),
            )
            OrDivider()
            Surface(
                onClick = { nav.reset(Home) },
                shape = RoundedCornerShape(20.dp),
                color = c.secondaryContainer,
                contentColor = c.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(NovaIcons.Finger, null, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Use a passkey", style = MaterialTheme.typography.labelLarge)
                        Text("Fingerprint or screen lock — no code needed", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(NovaIcons.Chev, null, modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New to Nova?", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            TextButton(onClick = { nav.go(SignUpPhone) }) { Text("Create an account", style = MaterialTheme.typography.labelLarge) }
        }
    }
    if (showCountries) {
        CountryPickerSheet(
            selected = country,
            onPick = { AuthStore.selectCountry(it); showCountries = false },
            onDismiss = { showCountries = false },
        )
    }
}

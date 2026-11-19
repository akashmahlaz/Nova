package com.nova.rider.feature.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaTopBar
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.SignupDraft
import com.nova.rider.data.auth.VerifyResult
import com.nova.rider.data.auth.authErrorMessage
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.LocationPermission
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Otp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CodeLength = 6
private const val ResendSeconds = 30

/**
 * Responsive OTP entry for one channel (phone or email) and purpose (sign-up or login).
 * Sign-up order: phone code, then the profile step, then the email code, which creates the account.
 */
@Composable
fun OtpScreen(nav: Navigator, route: Otp) {
    val c = MaterialTheme.colorScheme
    val isEmail = route.channel == "email"
    var code by rememberSaveable { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(ResendSeconds) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current

    // SMS User Consent API: automatically listens for incoming OTP SMS and populates the 6 digits
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            val extracted = Regex("\\b\\d{6}\\b").find(message ?: "")?.value
            if (!extracted.isNullOrBlank()) {
                code = extracted
            }
        }
    }

    if (!isEmail) {
        DisposableEffect(context) {
            // Start SMS User Consent client (listens for 5 mins for incoming SMS with a code)
            SmsRetriever.getClient(context).startSmsUserConsent(null)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                        val extras = intent.extras ?: return
                        val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status
                        if (status?.statusCode == CommonStatusCodes.SUCCESS) {
                            val consentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT) as? Intent
                            }
                            if (consentIntent != null) {
                                try {
                                    consentLauncher.launch(consentIntent)
                                } catch (_: ActivityNotFoundException) {}
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                SmsRetriever.SEND_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {}
            }
        }
    }

    // Ensure dark system status bar icons on light OTP background
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as? Activity)?.window
            val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
            val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: true
            insetsController?.isAppearanceLightStatusBars = true
            onDispose {
                insetsController?.isAppearanceLightStatusBars = prevLightStatus
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(seconds) {
        if (seconds > 0) {
            delay(1000)
            seconds--
        }
    }

    fun verify(value: String) {
        if (busy || value.length != CodeLength) return
        scope.launch {
            busy = true
            error = null
            try {
                when (val result = NovaApi.verifyOtp(route.channel, route.destination, route.purpose, value)) {
                    is VerifyResult.SignedIn -> {
                        AuthStore.saveSession(result.session)
                        nav.reset(Home)
                    }
                    is VerifyResult.Proof -> {
                        if (route.channel == "phone") {
                            AuthStore.draft = AuthStore.draft.copy(phone = route.destination, phoneProof = result.token)
                            nav.go(LocationPermission)
                        } else {
                            val draft: SignupDraft = AuthStore.draft.copy(email = route.destination, emailProof = result.token)
                            AuthStore.draft = draft
                            val session = NovaApi.signup(draft.firstName, draft.lastName, draft.phoneProof, draft.emailProof)
                            AuthStore.saveSession(session)
                            AuthStore.draft = SignupDraft()
                            nav.reset(Home)
                        }
                    }
                }
            } catch (e: Throwable) {
                error = authErrorMessage(e)
                code = ""
            } finally {
                busy = false
            }
        }
    }

    fun resend() {
        if (!isEmail) {
            SmsRetriever.getClient(context).startSmsUserConsent(null)
        }
        scope.launch {
            error = null
            try {
                NovaApi.sendOtp(route.channel, route.destination, route.purpose)
                seconds = ResendSeconds
            } catch (e: Throwable) {
                error = authErrorMessage(e)
            }
        }
    }

    LaunchedEffect(code) { if (code.length == CodeLength) verify(code) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val availableHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            NovaTopBar(onBack = { nav.back() })

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .defaultMinSize(minHeight = (availableHeight - 64.dp).coerceAtLeast(0.dp))
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Content: Title, Destination & OTP Digit Inputs
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter the code", style = MaterialTheme.typography.headlineLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Sent to ${route.destination} · ",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { nav.back() },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            ) {
                                Text("Edit", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    // 6 OTP Digit Boxes
                    BasicTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter(Char::isDigit).take(CodeLength) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.focusRequester(focus).semantics { contentDescription = "6-digit code" },
                        decorationBox = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(CodeLength) { i ->
                                    OtpBox(
                                        code.getOrNull(i),
                                        active = i == code.length,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        },
                    )

                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }

                    // SMS / Inbox Detection Pill
                    Row(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.secondaryContainer)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LoadingIndicator(Modifier.size(20.dp), color = c.primary)
                        Text(
                            if (isEmail) "Check your inbox…" else "Reading SMS automatically…",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.onSecondaryContainer,
                        )
                    }
                }

                // Bottom Content: Compact Resend Action & Primary Verify Button
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = c.surfaceContainerLow,
                        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = 0.6f)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Didn't receive code?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (seconds > 0) "Resend in 0:%02d".format(seconds) else "Ready to resend",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { resend() },
                                enabled = seconds == 0 && !busy,
                            ) {
                                Text(
                                    if (isEmail) "Resend Email" else "Resend SMS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (seconds == 0 && !busy) c.primary else c.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }

                    NovaButton(
                        "Verify",
                        onClick = { verify(code) },
                        enabled = code.length == CodeLength && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun OtpBox(digit: Char?, active: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (active) 24.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "otpRadius")
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(58.dp)
            .clip(shape)
            .background(
                when {
                    digit != null -> c.primaryContainer
                    active -> c.surface
                    else -> c.surfaceContainerHigh
                },
            )
            .then(if (active) Modifier.border(2.5.dp, c.primary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> Text(digit.toString(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = c.onPrimaryContainer)
            active -> Box(Modifier.width(2.dp).height(24.dp).background(c.primary))
        }
    }
}

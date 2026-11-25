package com.nova.rider.feature.auth

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.nova.designsystem.components.IconTile
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.components.toShape
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.PhoneRegions
import com.nova.rider.data.auth.authErrorMessage
import com.nova.rider.navigation.LocationDenied
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Otp
import com.nova.rider.navigation.ProfileSetup
import kotlinx.coroutines.launch

/** Pre-permission explainer, then the real Android location permission request. */
@Composable
fun LocationPermissionScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current

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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it } || AuthStore.session == null) nav.go(ProfileSetup) else nav.go(LocationDenied)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .navigationBarsPadding(),
    ) {
        val availableHeight = maxHeight
        val scale = (availableHeight / 740.dp).coerceIn(0.70f, 1.0f)
        val illustrationHeight = (240.dp * scale).coerceAtLeast(170.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = availableHeight)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy((20.dp * scale).coerceAtLeast(12.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(illustrationHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(240.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin.Center
                                }
                                .clip(NovaShape.Sunny.toShape()),
                        ) {
                            MapBackdrop(Modifier.fillMaxSize()) { halo(195f, 420f, 120f, c.primary, 0.18f) }
                        }
                        Box(
                            Modifier
                                .offset(y = (-10).dp)
                                .size(56.dp)
                                .rotate(-45f)
                                .shadow(8.dp, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp))
                                .clip(RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp))
                                .background(c.primary),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White)) }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pickups that find you.", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Allow location so drivers reach the right door, not just the right street.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.onSurfaceVariant,
                        )
                    }

                    SegmentedColumn {
                        NovaListItem(0, 3, "Exact pickup point, auto-filled", leading = { IconTile(NovaIcons.Nav, c.primaryContainer, c.onPrimaryContainer) })
                        NovaListItem(1, 3, "Live sharing & SOS during trips", leading = { IconTile(NovaIcons.Shield, c.tertiaryContainer, c.onTertiaryContainer) })
                        NovaListItem(2, 3, "Used only while Nova is open", leading = { IconTile(NovaIcons.Clock, NovaTheme.colors.successContainer, NovaTheme.colors.onSuccessContainer) })
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    NovaButton("Allow location", onClick = {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    })
                    TextButton(
                        onClick = {
                            if (AuthStore.session == null) nav.go(ProfileSetup) else nav.go(LocationDenied)
                        },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Enter pickup manually", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

/** Sign-up final step: name, secondary contact info (phone or email), optional referral. */
@Composable
fun ProfileSetupScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current

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

    val draft = AuthStore.draft
    val isEmailFirst = draft.emailProof.isNotBlank() && draft.phoneProof.isBlank()

    var first by rememberSaveable { mutableStateOf(draft.firstName) }
    var last by rememberSaveable { mutableStateOf(draft.lastName) }
    var email by rememberSaveable { mutableStateOf(draft.email) }
    var phone by rememberSaveable { mutableStateOf(draft.phone) }
    var showCountries by remember { mutableStateOf(false) }
    val country = AuthStore.country
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            Column(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = availableHeight)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Step 3 of 3", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                        repeat(3) { Box(Modifier.padding(start = 4.dp).width(32.dp).height(6.dp).clip(CircleShape).background(c.primary)) }
                    }

                    Text("What should drivers call you?", style = MaterialTheme.typography.headlineLarge)

                    Box(Modifier.size(100.dp).align(Alignment.CenterHorizontally)) {
                        ShapeBadge(NovaShape.Cookie, 100.dp, c.tertiaryContainer) {
                            Text(initialsOf(first, last), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                        }
                        Surface(
                            onClick = { /* photo picker */ },
                            modifier = Modifier.align(Alignment.BottomEnd).offset(y = (-2).dp).size(40.dp).border(3.dp, c.surface, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            color = c.primary,
                            contentColor = c.onPrimary,
                        ) { Box(contentAlignment = Alignment.Center) { Icon(NovaIcons.Camera, "Add profile photo", Modifier.size(18.dp)) } }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NovaTextField(first, { first = it }, "First name", Modifier.weight(1f))
                        NovaTextField(last, { last = it }, "Last name", Modifier.weight(1f), placeholder = "Optional")
                    }

                    if (isEmailFirst) {
                        // User verified email on Step 1. Mobile number is optional and can be verified later in settings.
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Mobile number (optional)", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                            PhoneRow(country, phone, onCountryClick = { showCountries = true }) { phone = it }
                        }
                    } else {
                        // User verified phone on Step 1. Email is optional and can be verified later in settings.
                        NovaTextField(
                            email,
                            { email = it },
                            "Email for receipts (optional)",
                            placeholder = "you@example.com",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                    }

                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, c.outline),
                    ) {
                        Icon(NovaIcons.Plus, null, Modifier.size(18.dp), tint = c.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("Have a referral code?", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = c.onSurface)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }

                    val e164 = if (isEmailFirst && phone.isNotBlank()) country?.let { PhoneRegions.toE164(it.iso, phone) } else null
                    val optionalValid = if (isEmailFirst) {
                        phone.isBlank() || e164 != null
                    } else {
                        email.isBlank() || email.contains('@')
                    }
                    val canProceed = first.isNotBlank() && optionalValid && !busy

                    NovaButton(
                        if (busy) "Creating account…" else "Let's ride",
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    val session = NovaApi.signup(
                                        firstName = first.trim(),
                                        lastName = last.trim(),
                                        phoneProof = draft.phoneProof.takeIf { it.isNotBlank() },
                                        emailProof = draft.emailProof.takeIf { it.isNotBlank() },
                                        phone = if (isEmailFirst) e164 else null,
                                        email = if (!isEmailFirst) email.trim().lowercase().takeIf { it.isNotBlank() } else null,
                                    )
                                    AuthStore.saveSession(session)
                                    AuthStore.draft = com.nova.rider.data.auth.SignupDraft()
                                    nav.reset(com.nova.rider.navigation.Home)
                                } catch (e: Throwable) {
                                    error = authErrorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        trailingIcon = NovaIcons.ArrowRight,
                        enabled = canProceed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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

private fun initialsOf(first: String, last: String): String =
    listOf(first.trim(), last.trim()).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

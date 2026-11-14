package com.nova.rider.feature.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaTopBar
import com.nova.rider.data.Sample
import com.nova.rider.navigation.LocationPermission
import com.nova.rider.navigation.Navigator
import kotlinx.coroutines.delay

private const val CodeLength = 6

/** OTP entry: one hidden text field drives six expressive boxes; the active box morphs to a pill. */
@Composable
fun OtpScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var code by rememberSaveable { mutableStateOf("3917") }
    var seconds by remember { mutableIntStateOf(24) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        while (seconds > 0) { delay(1000); seconds-- }
    }
    LaunchedEffect(code) { if (code.length == CodeLength) nav.go(LocationPermission) }

    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        NovaTopBar(onBack = { nav.back() })
        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the code", style = MaterialTheme.typography.headlineLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sent to ${Sample.riderPhone} · ", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    TextButton(onClick = { nav.back() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                        Text("Edit", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            BasicTextField(
                value = code,
                onValueChange = { v -> code = v.filter(Char::isDigit).take(CodeLength) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.focusRequester(focus).semantics { contentDescription = "6-digit code" },
                decorationBox = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(CodeLength) { i -> OtpBox(code.getOrNull(i), active = i == code.length, modifier = Modifier.weight(1f)) }
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(c.secondaryContainer).padding(start = 8.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(Modifier.size(24.dp), color = c.primary)
                    Text("Reading SMS…", style = MaterialTheme.typography.labelMedium, color = c.onSecondaryContainer)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (seconds > 0) "Resend in 0:%02d".format(seconds) else "You can resend now",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = c.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Didn't get it?", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { seconds = 30 },
                            enabled = seconds == 0,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Resend SMS", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        OutlinedButton(onClick = { }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Call me instead", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.onSurface)
                        }
                    }
                }
            }
            NovaButton("Verify", onClick = { nav.go(LocationPermission) })
        }
    }
}

@Composable
private fun OtpBox(digit: Char?, active: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (active) 32.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "otpRadius")
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(
                when {
                    digit != null -> c.primaryContainer
                    active -> c.surface
                    else -> c.surfaceContainerHigh
                },
            )
            .then(if (active) Modifier.border(3.dp, c.primary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> Text(digit.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = c.onPrimaryContainer)
            active -> Box(Modifier.width(2.dp).height(28.dp).background(c.primary))
        }
    }
}

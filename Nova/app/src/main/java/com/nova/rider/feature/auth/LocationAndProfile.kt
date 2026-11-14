package com.nova.rider.feature.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nova.rider.data.Sample
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.LocationDenied
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.ProfileSetup

/** Pre-permission explainer, then the real Android location permission request. */
@Composable
fun LocationPermissionScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) nav.go(ProfileSetup) else nav.go(LocationDenied)
    }
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(260.dp).clip(NovaShape.Sunny.toShape())) {
                MapBackdrop(Modifier.fillMaxSize()) { halo(195f, 420f, 120f, c.primary, 0.18f) }
            }
            Box(
                Modifier.offset(y = (-10).dp).size(60.dp).rotate(-45f).shadow(8.dp, RoundedCornerShape(30.dp, 30.dp, 30.dp, 8.dp))
                    .clip(RoundedCornerShape(30.dp, 30.dp, 30.dp, 8.dp)).background(c.primary),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pickups that find you.", style = MaterialTheme.typography.headlineLarge)
            Text("Allow location so drivers reach the right door, not just the right street.", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
        }
        SegmentedColumn {
            NovaListItem(0, 3, "Exact pickup point, auto-filled", leading = { IconTile(NovaIcons.Nav, c.primaryContainer, c.onPrimaryContainer) })
            NovaListItem(1, 3, "Live sharing & SOS during trips", leading = { IconTile(NovaIcons.Shield, c.tertiaryContainer, c.onTertiaryContainer) })
            NovaListItem(2, 3, "Used only while Nova is open", leading = { IconTile(NovaIcons.Clock, NovaTheme.colors.successContainer, NovaTheme.colors.onSuccessContainer) })
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NovaButton("Allow location", onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
            TextButton(onClick = { nav.go(LocationDenied) }, modifier = Modifier.height(48.dp)) {
                Text("Enter pickup manually", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** Sign-up final step: name, email, optional referral. */
@Composable
fun ProfileSetupScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var first by rememberSaveable { mutableStateOf(Sample.riderName) }
    var last by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Step 3 of 3", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                repeat(3) { Box(Modifier.padding(start = 4.dp).width(32.dp).height(6.dp).clip(CircleShape).background(c.primary)) }
            }
            Text("What should drivers call you?", style = MaterialTheme.typography.headlineLarge)
            Box(Modifier.size(120.dp).align(Alignment.CenterHorizontally)) {
                ShapeBadge(NovaShape.Cookie, 120.dp, c.tertiaryContainer) {
                    Text(Sample.riderInitials, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                }
                Surface(
                    onClick = { /* photo picker */ },
                    modifier = Modifier.align(Alignment.BottomEnd).offset(y = (-4).dp).size(44.dp).border(3.dp, c.surface, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = c.primary,
                    contentColor = c.onPrimary,
                ) { Box(contentAlignment = Alignment.Center) { Icon(NovaIcons.Camera, "Add profile photo", Modifier.size(20.dp)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaTextField(first, { first = it }, "First name", Modifier.weight(1f))
                NovaTextField(last, { last = it }, "Last name", Modifier.weight(1f), placeholder = "Optional")
            }
            NovaTextField(email, { email = it }, "Email for receipts", placeholder = "you@example.co.nz", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, c.outline),
            ) {
                Icon(NovaIcons.Plus, null, Modifier.size(20.dp), tint = c.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("Have a referral code?", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = c.onSurface)
            }
        }
        NovaButton(
            "Let's ride", onClick = { nav.reset(Home) }, trailingIcon = NovaIcons.ArrowRight, enabled = first.isNotBlank(),
            modifier = Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 12.dp),
        )
    }
}

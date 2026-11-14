package com.nova.rider.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.navigation.Login
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Onboarding2
import com.nova.rider.navigation.Onboarding3
import com.nova.rider.navigation.Welcome

/** Shared onboarding layout: wordmark + skip, 400dp illustration, progress, headline, CTA row. */
@Composable
private fun OnboardingLayout(
    background: Color,
    accent: Color,
    step: Int,
    title: String,
    body: String,
    bodyColor: Color,
    inactiveDot: Color,
    onSkip: (() -> Unit)?,
    illustration: @Composable BoxScope.() -> Unit,
    bottom: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("nova", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            if (onSkip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.height(48.dp)) {
                    Text("Skip", color = accent, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        // The illustration is drawn on a 390 x 400 dp canvas and scaled down to whatever space is
        // left, so the text and the Next button always keep their full size on every phone.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            val scale = minOf(maxWidth / 390.dp, maxHeight / 400.dp, 1f)
            Box(
                Modifier.requiredSize(390.dp, 400.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                content = illustration,
            )
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.semantics { contentDescription = "Step ${step + 1} of 3" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val w by animateDpAsState(if (i == step) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
                    Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (i == step) accent else inactiveDot))
                }
            }
            Text(title, style = MaterialTheme.typography.displaySmall)
            Text(body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp), color = bodyColor)
            Spacer(Modifier.height(10.dp))
            bottom()
        }
    }
}

@Composable
private fun NextFab(onClick: () -> Unit, container: Color, round: Boolean) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(shape = if (round) CircleShape else RoundedCornerShape(32.dp), pressedShape = RoundedCornerShape(24.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
        modifier = Modifier.size(96.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(NovaIcons.ArrowRight, contentDescription = "Next", modifier = Modifier.size(34.dp))
    }
}

/** Floating info chip used inside illustrations. */
@Composable
private fun FloatChip(modifier: Modifier, height: Dp = 52.dp, content: @Composable () -> Unit) {
    Surface(modifier = modifier.height(height), shape = CircleShape, color = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
fun Onboarding1Screen(nav: Navigator) {
    val accent = MaterialTheme.colorScheme.primary
    OnboardingLayout(
        background = MaterialTheme.colorScheme.surface,
        accent = accent,
        step = 0,
        title = "Your ride,\ntwo taps away.",
        body = "Electric, Go, Comfort or XL — see the fare upfront and pick what fits your day.",
        bodyColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveDot = MaterialTheme.colorScheme.outlineVariant,
        onSkip = { nav.go(Welcome) },
        illustration = {
            Box(Modifier.offset(34.dp, 36.dp).size(322.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
            ShapeBadge(NovaShape.Cookie, 200.dp, accent, Modifier.offset(95.dp, 96.dp)) {
                Icon(NovaIcons.CarSide, null, tint = Color.White, modifier = Modifier.width(104.dp).height(76.dp))
            }
            FloatChip(Modifier.offset(22.dp, 66.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(NovaIcons.Clock, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                }
                Text("Pickup in 3 min", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(
                Modifier.align(Alignment.TopEnd).offset((-18).dp, 302.dp).height(52.dp).clip(RoundedCornerShape(18.dp)).background(NovaPalette.Ink).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(NovaTheme.colors.volt))
                Text("Comfort · $28.40", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            ShapeBadge(NovaShape.Burst, 56.dp, NovaTheme.colors.coralBright, Modifier.align(Alignment.TopEnd).offset((-44).dp, 50.dp))
        },
        bottom = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { nav.go(Login) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("I have an account", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), modifier = Modifier.fillMaxWidth())
                }
                NextFab({ nav.go(Onboarding2) }, accent, round = false)
            }
        },
    )
}

@Composable
fun Onboarding2Screen(nav: Navigator) {
    val accent = MaterialTheme.colorScheme.tertiary
    OnboardingLayout(
        background = Color(0xFFFFF8F6),
        accent = accent,
        step = 1,
        title = "Safe from\ndoor to door.",
        body = "A PIN for every pickup, live trip sharing and one-tap SOS that connects you to 111.",
        bodyColor = Color(0xFF53433F),
        inactiveDot = Color(0xFFD8C2BC),
        onSkip = { nav.go(Welcome) },
        illustration = {
            ShapeBadge(NovaShape.Sunny, 330.dp, MaterialTheme.colorScheme.tertiaryContainer, Modifier.offset(30.dp, 32.dp))
            Box(Modifier.offset(115.dp, 116.dp).size(160.dp).clip(RoundedCornerShape(56.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(NovaIcons.ShieldCheck, null, tint = Color.White, modifier = Modifier.size(84.dp))
            }
            Surface(Modifier.offset(20.dp, 60.dp), shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 6.dp) {
                Row(Modifier.padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(NovaPalette.Ink).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("4821", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    }
                    Text("Trip PIN", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            FloatChip(Modifier.align(Alignment.TopEnd).offset((-16).dp, 296.dp), height = 56.dp) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("M", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Text("Live trip shared with Mum", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottom = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { nav.back() }, modifier = Modifier.height(48.dp)) {
                    Text("Back", color = Color(0xFF53433F), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                }
                Spacer(Modifier.weight(1f))
                NextFab({ nav.go(Onboarding3) }, accent, round = true)
            }
        },
    )
}

@Composable
fun Onboarding3Screen(nav: Navigator) {
    val green = NovaTheme.colors.onboardingGreen
    OnboardingLayout(
        background = Color(0xFFF4FBF4),
        accent = green,
        step = 2,
        title = "Pay your way.\nNo surprises.",
        body = "Google Pay, cards or Nova Credit. The price you see is the price you pay.",
        bodyColor = Color(0xFF404943),
        inactiveDot = Color(0xFFBFC9C1),
        onSkip = null,
        illustration = {
            ShapeBadge(NovaShape.Burst, 320.dp, NovaTheme.colors.successContainer, Modifier.offset(35.dp, 40.dp))
            Column(Modifier.offset(60.dp, 110.dp).width(270.dp).rotate(-4f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PayCard(RoundedCornerShape(28.dp, 28.dp, 8.dp, 8.dp), Color.White) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(green), contentAlignment = Alignment.Center) {
                        Text("GPay", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Google Pay", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Icon(NovaIcons.Check, null, tint = green, modifier = Modifier.size(22.dp))
                }
                PayCard(RoundedCornerShape(8.dp), Color.White) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                        Text("$", color = MaterialTheme.colorScheme.tertiary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Visa ···· 4417", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                }
                PayCard(RoundedCornerShape(8.dp, 8.dp, 28.dp, 28.dp), NovaPalette.Ink) {
                    ShapeBadge(NovaShape.Burst, 40.dp, NovaTheme.colors.volt)
                    Text("Nova Credit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("$42.00", color = NovaTheme.colors.volt, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        bottom = {
            NovaButton(
                text = "Get started",
                onClick = { nav.go(Welcome) },
                height = 96.dp,
                trailingIcon = NovaIcons.ArrowRight,
                colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = Color.White),
                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 22.sp),
            )
        },
    )
}

@Composable
private fun PayCard(shape: RoundedCornerShape, color: Color, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().height(64.dp), shape = shape, color = color, shadowElevation = if (color == Color.White) 3.dp else 0.dp) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

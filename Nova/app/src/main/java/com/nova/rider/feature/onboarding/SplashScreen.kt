package com.nova.rider.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Onboarding1
import kotlinx.coroutines.delay

/** Branded splash shown right after the system SplashScreen. Auto-advances, or tap to skip. */
@Composable
fun SplashScreen(nav: Navigator) {
    LaunchedEffect(Unit) {
        delay(1800)
        nav.reset(Onboarding1)
    }
    val infinite = rememberInfiniteTransition(label = "splash")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "spin")
    val pulse by infinite.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .clickable { nav.reset(Onboarding1) }
            .semantics { contentDescription = "Nova. Tap to continue" },
    ) {
        ShapeBadge(NovaShape.Burst, 620.dp, Color.White, Modifier.offset((-115).dp, (-240).dp).alpha(0.12f).rotate(spin))
        ShapeBadge(NovaShape.Cookie, 300.dp, Color.White, Modifier.align(Alignment.BottomEnd).offset(110.dp, 80.dp).alpha(0.1f))

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeBadge(NovaShape.Burst, 132.dp, Color.White, Modifier.scale(pulse)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(NovaTheme.colors.coralBright))
            }
            Spacer(Modifier.height(28.dp))
            Text("nova", color = Color.White, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(10.dp))
            Text("Get there, brighter.", color = MaterialTheme.colorScheme.primaryContainer, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(1f, 0.5f, 0.25f).forEach { a -> Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = a))) }
        }
    }
}

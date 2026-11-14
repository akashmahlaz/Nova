package com.nova.rider.feature.auth

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaOutlinedButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.navigation.Login
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.SignUpPhone

/** Auth choice: create account (sign-up flow) or log in (returning rider). */
@Composable
fun WelcomeScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.surface)) {
        // Panel takes the space the text and buttons don't need (470 dp on the design phone).
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                .background(c.primary),
        ) {
            ShapeBadge(NovaShape.Burst, 300.dp, Color.White, Modifier.offset((-70).dp, (-60).dp).alpha(0.12f))
            ShapeBadge(NovaShape.Cookie, 240.dp, Color.White, Modifier.align(Alignment.TopEnd).offset(80.dp, 250.dp).alpha(0.1f))
            Text(
                "nova", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp,
                modifier = Modifier.statusBarsPadding().padding(start = 24.dp, top = 16.dp),
            )
            // 310 x 270 dp illustration, scaled down when the panel is shorter than designed.
            val art = minOf((maxHeight - 140.dp) / 270.dp, maxWidth / 330.dp, 1f).coerceAtLeast(0.4f)
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp * art).requiredSize(310.dp, 270.dp)
                    .graphicsLayer { scaleX = art; scaleY = art; transformOrigin = TransformOrigin(0.5f, 1f) },
            ) {
                Box(Modifier.offset(0.dp, 30.dp).size(170.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(NovaIcons.CarSide, null, tint = c.onPrimaryContainer, modifier = Modifier.width(96.dp).height(70.dp))
                }
                ShapeBadge(NovaShape.Sunny, 150.dp, NovaTheme.colors.coralBright, Modifier.offset(150.dp, 0.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.onTertiaryContainer), contentAlignment = Alignment.Center) {
                        Icon(NovaIcons.Pin, null, tint = NovaTheme.colors.coralBright, modifier = Modifier.size(28.dp))
                    }
                }
                Box(
                    Modifier.offset(120.dp, 150.dp).width(190.dp).height(92.dp).clip(RoundedCornerShape(46.dp)).background(NovaTheme.colors.volt),
                    contentAlignment = Alignment.Center,
                ) { Text("4 min away", color = NovaTheme.colors.onVolt, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Column(
            Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Kia ora.\nWhere to today?", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp, lineHeight = 42.sp))
            Text("Fair upfront fares across Auckland, with safety built into every ride.", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            NovaButton("Create account", onClick = { nav.go(SignUpPhone) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaOutlinedButton("Log in", onClick = { nav.go(Login) }, modifier = Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { nav.go(Login) },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
                ) { Icon(NovaIcons.Mail, contentDescription = "Continue with email") }
            }
            Text(
                buildAnnotatedString {
                    append("By continuing you agree to the ")
                    withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("Terms") }
                    append(" and ")
                    withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
                    append(".")
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package com.nova.rider.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
    val view = LocalView.current

    // Set white status bar icons on top of the blue banner, restore dark icons on exit
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as? Activity)?.window
            val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
            val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: true
            insetsController?.isAppearanceLightStatusBars = false
            onDispose {
                insetsController?.isAppearanceLightStatusBars = prevLightStatus
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .navigationBarsPadding(),
    ) {
        // Hero Blue Banner: Extends fully behind the status bar (no statusBarsPadding on root)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(c.primary),
        ) {
            val scale = (maxHeight / 380.dp).coerceIn(0.70f, 1.05f)

            ShapeBadge(NovaShape.Burst, 300.dp, Color.White, Modifier.offset((-70).dp, (-60).dp).alpha(0.12f))
            ShapeBadge(NovaShape.Cookie, 240.dp, Color.White, Modifier.align(Alignment.TopEnd).offset(80.dp, 200.dp).alpha(0.1f))

            // Logo sits safely below the system status bar icons
            Text(
                "nova",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 24.dp, top = 8.dp),
            )

            // Centered and auto-scaled illustration
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(310.dp)
                    .height(260.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                Box(
                    Modifier.offset(0.dp, 30.dp).size(160.dp).clip(CircleShape).background(c.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NovaIcons.CarSide, null, tint = c.onPrimaryContainer, modifier = Modifier.width(92.dp).height(66.dp))
                }
                ShapeBadge(NovaShape.Sunny, 140.dp, NovaTheme.colors.coralBright, Modifier.offset(145.dp, 0.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(c.onTertiaryContainer), contentAlignment = Alignment.Center) {
                        Icon(NovaIcons.Pin, null, tint = NovaTheme.colors.coralBright, modifier = Modifier.size(26.dp))
                    }
                }
                Box(
                    Modifier.offset(115.dp, 140.dp).width(185.dp).height(84.dp).clip(RoundedCornerShape(42.dp)).background(NovaTheme.colors.volt),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("4 min away", color = NovaTheme.colors.onVolt, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Bottom Actions & Value Prop (natural space guaranteed, never squeezed)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Kia ora.\nWhere to today?",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(
                "Fair upfront fares across Auckland, with safety built into every ride.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
                color = c.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            // Primary: Create account
            NovaButton(
                "Create account",
                onClick = { nav.go(SignUpPhone) },
                height = 56.dp,
            )

            // Secondary: Log in
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NovaOutlinedButton(
                    "Log in",
                    onClick = { nav.go(Login) },
                    height = 56.dp,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { nav.go(Login) },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = c.secondaryContainer,
                        contentColor = c.onSecondaryContainer,
                    ),
                ) {
                    Icon(NovaIcons.Mail, contentDescription = "Continue with email", modifier = Modifier.size(22.dp))
                }
            }

            // Legal text
            Text(
                buildAnnotatedString {
                    append("By continuing you agree to the ")
                    withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("Terms") }
                    append(" and ")
                    withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
                    append(".")
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

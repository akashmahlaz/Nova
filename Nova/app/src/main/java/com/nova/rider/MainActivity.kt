package com.nova.rider

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nova.designsystem.theme.NovaTheme

class MainActivity : ComponentActivity() {
    private var trackToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        trackToken = extractTrackToken(intent)
        setContent {
            NovaTheme {
                NovaRiderApp(trackToken = trackToken, onTrackTokenHandled = { trackToken = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractTrackToken(intent)?.let { trackToken = it }
    }

    private fun extractTrackToken(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0] == "track") {
            return segments[1].takeIf { it.isNotBlank() }
        }
        return null
    }
}

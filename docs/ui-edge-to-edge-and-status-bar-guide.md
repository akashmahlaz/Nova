# Nova UI: Edge-to-Edge & System Status Bar Architecture Guide

> **Audience:** Core Developers & AI Agents working on Nova (Rider & Driver apps).  
> **Target Platform:** Jetpack Compose, Material 3 Expressive, Android 15-17 (API 35-37), Samsung OneUI / Pixel / Stock Android.

---

## 1. Executive Summary & Root Cause Analysis

### The Bug Observed
When testing screens on physical devices (such as Samsung Galaxy running OneUI), users reported a **whitish, washed-out band** across the system status bar area at the top of the screen instead of the screen's actual background seamlessly bleeding to the top physical bezel.

### Root Causes
1. **Root-Level `statusBarsPadding()` Anti-Pattern:**
   - When `.statusBarsPadding()` is placed on the root container (`Column` or `Box`), the entire layout is pushed downward by `X.dp` (the status bar height: 24dp–36dp).
   - This leaves the top physical strip of the window empty. Depending on `themes.xml`, the window background (`android:windowBackground`, `@color/nova_surface`) or the decor view renders through, causing a jarring whitish band above colored headers (e.g. the royal blue Welcome banner or peach/mint onboarding surfaces).
2. **OEM Translucent Contrast Enforcement (Samsung OneUI / Android 10+):**
   - By default on API 29+, the Android framework sets `window.isStatusBarContrastEnforced = true`. If the system detects light content or transparency, it artificially draws a 20-30% translucent white or gray protective scrim over the status bar.
3. **Unmanaged Status Bar Icon Appearance (`isAppearanceLightStatusBars`):**
   - When navigating from a dark screen (e.g. royal blue Welcome banner where icons must be white) back to a light screen (e.g. Onboarding 1/2/3 or Login where icons must be dark), the system does not automatically invert icon colors unless explicitly controlled via `WindowCompat.getInsetsController`.

---

## 2. Canonical Edge-to-Edge Rule Set

Every screen in the Nova Rider (`Nova`) and Nova Driver (`NovaDriver`) apps **MUST** follow these 4 architectural rules:

### Rule 1: Root Containers NEVER Have `statusBarsPadding()`
The outermost container must extend from physical `(0, 0)` down to the screen bottom:
```kotlin
// ✅ CORRECT: Background bleeds behind the status bar
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(screenBackground)
        .navigationBarsPadding() // Navigation bar at the bottom is handled
) { ... }

// ❌ WRONG: Creates a whitish strip at the top
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(screenBackground)
        .statusBarsPadding() // DO NOT PUT THIS ON ROOT!
        .navigationBarsPadding()
) { ... }
```

### Rule 2: Inner Top Components Apply `statusBarsPadding()`
Only the specific child component that sits at the top (e.g., `NovaTopBar`, logo header, or banner text) takes `.statusBarsPadding()`:
```kotlin
// In Top Bar / Header Row:
Row(
    modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding() // Pushes logo/actions safely below clock & camera cutout
        .padding(start = 24.dp, end = 16.dp, top = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text("nova", ...)
}
```
*Note: `NovaTopBar` already has `.statusBarsPadding()` built into its internal implementation.*

### Rule 3: Explicit Dynamic Icon Tinting (`isAppearanceLightStatusBars`)
Whenever a screen has a specific surface color under the status bar, use `DisposableEffect` with `WindowCompat.getInsetsController`:
- **Light Surfaces** (white, peach `#FFF8F6`, mint `#F4FBF4`, gray): `isAppearanceLightStatusBars = true` (Dark icons: black clock, battery, wifi).
- **Dark Surfaces** (cobalt `#0B3DE0`, ink `#111827`, brand primary): `isAppearanceLightStatusBars = false` (White icons).

```kotlin
val view = LocalView.current
if (!view.isInEditMode) {
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: true
        // Set to true for light surfaces, false for dark surfaces:
        insetsController?.isAppearanceLightStatusBars = true
        onDispose {
            insetsController?.isAppearanceLightStatusBars = prevLightStatus
        }
    }
}
```

### Rule 4: Disable Contrast Enforcing in `MainActivity.kt`
In `MainActivity.kt`:
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        setContent {
            NovaTheme {
                NovaRiderApp()
            }
        }
    }
}
```

---

## 3. Screen Audit & Status Matrix

| Screen File | Screen Composable | Status Bar Treatment | Icon Tint (`isAppearanceLightStatusBars`) | Status |
| :--- | :--- | :--- | :--- | :--- |
| `OnboardingScreens.kt` | `Onboarding1Screen` | Edge-to-edge white, top row `.statusBarsPadding()` | `true` (Dark icons) | ✅ Fixed |
| `OnboardingScreens.kt` | `Onboarding2Screen` | Edge-to-edge peach (`#FFF8F6`), top row `.statusBarsPadding()` | `true` (Dark icons) | ✅ Fixed |
| `OnboardingScreens.kt` | `Onboarding3Screen` | Edge-to-edge mint (`#F4FBF4`), top row `.statusBarsPadding()` | `true` (Dark icons) | ✅ Fixed |
| `WelcomeScreen.kt` | `WelcomeScreen` | Edge-to-edge cobalt blue banner behind status bar | `false` (White icons) | ✅ Fixed |
| `PhoneScreens.kt` | `SignUpPhoneScreen` | Uses `NovaTopBar` (internal status bar padding) | Default `true` | ✅ Valid |
| `PhoneScreens.kt` | `LoginScreen` | Uses `NovaTopBar` (internal status bar padding) | Default `true` | ✅ Valid |
| `PhoneScreens.kt` | `OtpScreen` | Uses `NovaTopBar` (internal status bar padding) | Default `true` | ✅ Valid |
| `LocationAndProfile.kt` | `LocationPermissionScreen` | Uses `c.surface` edge-to-edge with inner padding | `true` | Reviewed |
| `LocationAndProfile.kt` | `ProfileSetupScreen` | Uses `c.surface` edge-to-edge with inner padding | `true` | Reviewed |

---

## 4. Instructions for Future AI Agents & Developers
When creating a new screen or modifying an existing layout:
1. **Never** slap `.statusBarsPadding()` on the outer `fillMaxSize()` container.
2. If the screen has a custom background color or hero graphic at the top, let the background cover the entire screen and place `.statusBarsPadding()` on the first visible interactive element (app bar, title, close button).
3. Always verify that status bar icons remain legible by matching `isAppearanceLightStatusBars` to the background luminance.

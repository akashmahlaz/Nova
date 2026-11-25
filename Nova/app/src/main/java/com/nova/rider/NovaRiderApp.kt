package com.nova.rider

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nova.rider.data.auth.AuthStore
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nova.rider.feature.account.*
import com.nova.rider.feature.auth.*
import com.nova.rider.feature.booking.*
import com.nova.rider.feature.edge.*
import com.nova.rider.feature.onboarding.*
import com.nova.rider.feature.support.*
import com.nova.rider.feature.trip.*
import com.nova.rider.navigation.*

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * Debug builds reach the dev API on the Mac's LAN address. On targetSdk 37 Android blocks that
 * traffic until the user grants ACCESS_LOCAL_NETWORK, so ask once at launch. Release builds skip this.
 */
@Composable
private fun EnsureLocalNetworkAccess() {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(LOCAL_NETWORK_PERMISSION)
    }
}

/**
 * Root of the rider app: one Navigation 3 back stack (saved across process death because every
 * [Route] is @Serializable) and one entry per screen.
 */
@Composable
fun NovaRiderApp() {
    val context = LocalContext.current
    remember(context) { AuthStore.init(context) }
    EnsureLocalNetworkAccess()
    val backStack = rememberNavBackStack(Splash)
    val nav = remember(backStack) { Navigator(backStack) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    NavDisplay(
        backStack = backStack,
        onBack = { nav.back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Shared-axis style: forward slides in from the end, back slides from the start.
        transitionSpec = {
            ContentTransform(
                slideInHorizontally(spatial) { it / 4 } + fadeIn(effects),
                slideOutHorizontally(spatial) { -it / 4 } + fadeOut(tween(90)),
            )
        },
        popTransitionSpec = {
            (slideInHorizontally(spatial) { -it / 4 } + fadeIn(effects)) togetherWith
                (slideOutHorizontally(spatial) { it / 4 } + fadeOut(tween(90)))
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(spatial) { -it / 4 } + fadeIn(effects)) togetherWith
                (slideOutHorizontally(spatial) { it / 4 } + fadeOut(tween(90)))
        },
        entryProvider = entryProvider {
            entry<Splash> { SplashScreen(nav) }
            entry<Onboarding1> { Onboarding1Screen(nav) }
            entry<Onboarding2> { Onboarding2Screen(nav) }
            entry<Onboarding3> { Onboarding3Screen(nav) }
            entry<Welcome> { WelcomeScreen(nav) }
            entry<SignUpPhone> { SignUpPhoneScreen(nav) }
            entry<Login> { LoginScreen(nav) }
            entry<Otp> { key -> OtpScreen(nav, key) }
            entry<LocationPermission> { LocationPermissionScreen(nav) }
            entry<ProfileSetup> { ProfileSetupScreen(nav) }
            entry<Home> { HomeScreen(nav) }
            entry<Search> { SearchScreen(nav) }
            entry<PinMap> { PinMapScreen(nav) }
            entry<AddStop> { AddStopScreen(nav) }
            entry<RideOptions> { RideOptionsScreen(nav) }
            entry<Schedule> { ScheduleScreen(nav) }
            entry<FindingDriver> { FindingDriverScreen(nav) }
            entry<DriverEnRoute> { DriverEnRouteScreen(nav) }
            entry<Chat> { ChatScreen(nav) }
            entry<Call> { CallScreen(nav) }
            entry<ShareTrip> { ShareTripScreen(nav) }
            entry<CancelRide> { CancelRideScreen(nav) }
            entry<OnTrip> { OnTripScreen(nav) }
            entry<TripComplete> { TripCompleteScreen(nav) }
            entry<Activity> { ActivityScreen(nav) }
            entry<Receipt> { ReceiptScreen(nav) }
            entry<Wallet> { WalletScreen(nav) }
            entry<AddCard> { AddCardScreen(nav) }
            entry<Account> { AccountScreen(nav) }
            entry<EditProfile> { EditProfileScreen(nav) }
            entry<SetUpPlace> { key -> SavedPlacesScreen(nav, setupKind = key.kind) }
            entry<SavedPlaces> { SavedPlacesScreen(nav) }
            entry<Notifications> { NotificationsScreen(nav) }
            entry<Offers> { OffersScreen(nav) }
            entry<Settings> { SettingsScreen(nav) }
            entry<Safety> { SafetyScreen(nav) }
            entry<Help> { HelpScreen(nav) }
            entry<SupportChat> { SupportChatScreen(nav) }
            entry<LostItem> { LostItemScreen(nav) }
            entry<NoDrivers> { NoDriversScreen(nav) }
            entry<Surge> { SurgeScreen(nav) }
            entry<DriverCancelled> { DriverCancelledScreen(nav) }
            entry<PaymentFailed> { PaymentFailedScreen(nav) }
            entry<Offline> { OfflineScreen(nav) }
            entry<LocationDenied> { LocationDeniedScreen(nav) }
            entry<NoService> { NoServiceScreen(nav) }
            entry<EmptyActivity> { ActivityScreen(nav, empty = true) }
            entry<SosActive> { SosActiveScreen(nav) }
        },
    )
}

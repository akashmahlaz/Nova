package com.nova.driver

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nova.driver.feature.account.*
import com.nova.driver.feature.edge.*
import com.nova.driver.feature.onboard.*
import com.nova.driver.feature.shift.*
import com.nova.driver.navigation.*

/** Root of the driver app: one Navigation 3 back stack and one entry per screen. */
@Composable
fun NovaDriverApp() {
    val backStack = rememberNavBackStack(Splash)
    val nav = remember(backStack) { Navigator(backStack) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    NavDisplay(
        backStack = backStack,
        onBack = { nav.back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
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
            entry<Welcome> { WelcomeScreen(nav) }
            entry<SignUp> { SignUpScreen(nav) }
            entry<Login> { LoginScreen(nav) }
            entry<Otp> { OtpScreen(nav) }
            entry<Vehicle> { VehicleScreen(nav) }
            entry<Documents> { DocumentsScreen(nav) }
            entry<Scan> { ScanScreen(nav) }
            entry<Bank> { BankScreen(nav) }
            entry<Pending> { PendingScreen(nav) }
            entry<Home> { HomeScreen(nav) }
            entry<Online> { OnlineScreen(nav) }
            entry<Request> { RequestScreen(nav) }
            entry<ToPickup> { ToPickupScreen(nav) }
            entry<Chat> { ChatScreen(nav) }
            entry<CancelTrip> { CancelTripScreen(nav) }
            entry<Arrived> { ArrivedScreen(nav) }
            entry<OnTrip> { OnTripScreen(nav) }
            entry<Complete> { CompleteScreen(nav) }
            entry<Earnings> { EarningsScreen(nav) }
            entry<Trips> { TripsScreen(nav) }
            entry<Quests> { QuestsScreen(nav) }
            entry<Payouts> { PayoutsScreen(nav) }
            entry<Inbox> { InboxScreen(nav) }
            entry<Profile> { ProfileScreen(nav) }
            entry<Prefs> { PrefsScreen(nav) }
            entry<Settings> { SettingsScreen(nav) }
            entry<Help> { HelpScreen(nav) }
            entry<NoShow> { NoShowScreen(nav) }
            entry<RiderCancelled> { RiderCancelledScreen(nav) }
            entry<ConnectionLost> { ConnectionLostScreen(nav) }
            entry<DocExpired> { DocExpiredScreen(nav) }
            entry<SosActive> { SosActiveScreen(nav) }
        },
    )
}

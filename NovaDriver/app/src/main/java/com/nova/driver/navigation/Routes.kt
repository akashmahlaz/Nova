package com.nova.driver.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every driver screen is a serializable Navigation 3 key, so the back stack survives process death. */
@Serializable sealed interface Route : NavKey

// Sign up, log in & verification
@Serializable data object Splash : Route
@Serializable data object Welcome : Route
@Serializable data object SignUp : Route
@Serializable data object Login : Route
@Serializable data object Otp : Route
@Serializable data object Vehicle : Route
@Serializable data object Documents : Route
@Serializable data object Scan : Route
@Serializable data object Bank : Route
@Serializable data object Pending : Route

// A shift
@Serializable data object Home : Route
@Serializable data object Online : Route
@Serializable data object Request : Route
@Serializable data object ToPickup : Route
@Serializable data object Chat : Route
@Serializable data object CancelTrip : Route
@Serializable data object Arrived : Route
@Serializable data object OnTrip : Route
@Serializable data object Complete : Route

// Earnings, account & support
@Serializable data object Earnings : Route
@Serializable data object Trips : Route
@Serializable data object Quests : Route
@Serializable data object Payouts : Route
@Serializable data object Inbox : Route
@Serializable data object Profile : Route
@Serializable data object Prefs : Route
@Serializable data object Settings : Route
@Serializable data object Help : Route

// Edge cases
@Serializable data object NoShow : Route
@Serializable data object RiderCancelled : Route
@Serializable data object ConnectionLost : Route
@Serializable data object DocExpired : Route
@Serializable data object SosActive : Route
